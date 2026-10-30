<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ArrowDown } from '@element-plus/icons-vue'
import { lotteryApi } from '@/api/lottery'
import { marketAdminApi, type AdminIssue } from '@/api/market'
import type { LotteryType } from '@/types/lottery'
import { formatDateTime } from '@/utils/format'

const types = ref<LotteryType[]>([])
const list = ref<AdminIssue[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const drawDialog = ref(false)
const current = ref<AdminIssue | null>(null)

const syncing = ref(false)
const syncCount = ref(100)
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)

// 过滤条件：期号是字符串且各彩种位数不同，不能靠“拉最近 N 条”来看数据，必须按条件分页查
const query = reactive({
  typeCode: '',
  drawStatus: undefined as number | undefined,
  dataSource: undefined as number | undefined,
  keyword: '',
  officialOnly: true,
})

const form = reactive({ typeCode: 'SSQ', issueNo: '', drawTime: '' })
const drawForm = reactive({ redNumbers: '', blueNumbers: '', reviewTicket: '' })

async function load() {
  loading.value = true
  try {
    const result = await marketAdminApi.issues({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      typeCode: query.typeCode || undefined,
      drawStatus: query.drawStatus,
      dataSource: query.dataSource,
      keyword: query.keyword.trim() || undefined,
      officialOnly: query.officialOnly,
    })
    list.value = result.list
    total.value = result.total
    // 删除/过滤后当前页可能越界，回退一页重取
    if (!result.list.length && result.total > 0 && pageNum.value > 1) {
      pageNum.value -= 1
      await load()
    }
  } finally {
    loading.value = false
  }
}

function search() {
  pageNum.value = 1
  load()
}

async function resetQuery() {
  query.typeCode = ''
  query.drawStatus = undefined
  query.dataSource = undefined
  query.keyword = ''
  query.officialOnly = true
  pageNum.value = 1
  await load()
}

async function createIssue() {
  if (!form.issueNo || !form.drawTime) {
    ElMessage.warning('期号与开奖时间必填')
    return
  }
  await marketAdminApi.createIssue({
    typeCode: form.typeCode,
    issueNo: form.issueNo,
    drawTime: form.drawTime.replace(' ', 'T'),
  })
  ElMessage.success('期次已创建')
  dialogVisible.value = false
  form.issueNo = ''
  await load()
}

function openDraw(row: AdminIssue) {
  current.value = row
  drawForm.redNumbers = row.redNumbers || ''
  drawForm.blueNumbers = row.blueNumbers || ''
  drawForm.reviewTicket = ''
  drawDialog.value = true
}

async function prepare() {
  if (!current.value) return
  const result = await marketAdminApi.prepareDraw(current.value.id, {
    redNumbers: drawForm.redNumbers,
    blueNumbers: drawForm.blueNumbers || undefined,
  })
  drawForm.reviewTicket = result.reviewTicket
  ElMessage.success(`已生成复核票据 ${result.reviewTicket}，请由另一账号确认`)
}

async function confirm() {
  if (!current.value) return
  if (!drawForm.reviewTicket) {
    ElMessage.warning('请先录入生成复核票据')
    return
  }
  await marketAdminApi.confirmDraw(current.value.id, {
    redNumbers: drawForm.redNumbers,
    blueNumbers: drawForm.blueNumbers || undefined,
    reviewTicket: drawForm.reviewTicket,
  })
  ElMessage.success('开奖号已写入，结算任务将自动执行')
  drawDialog.value = false
  await load()
}

async function settle(row: AdminIssue) {
  try {
    await ElMessageBox.confirm(
      `将对该期所有已锁定预测判定命中并分账，操作幂等可重跑。确认结算 ${row.typeCode} ${row.issueNo}？`,
      '手动结算',
      { type: 'warning', confirmButtonText: '确认结算' },
    )
  } catch {
    return
  }
  const result = await marketAdminApi.settle(row.id)
  ElMessage.success(`结算完成：判定 ${result.judged} 份，生成 ${result.settlements} 张结算单`)
  await load()
}

async function syncAll() {
  syncing.value = true
  try {
    const results = await marketAdminApi.syncAllDraw(syncCount.value)
    const summary = results
      .map((r) => `${r.typeCode} 新增${r.inserted}/更正${r.updated}/失败${r.failed}`)
      .join('；')
    const failed = results.reduce((sum, r) => sum + r.failed, 0)
    if (failed > 0) {
      ElMessage.warning(`同步完成（有失败）：${summary}`)
    } else {
      ElMessage.success(`同步完成：${summary}`)
    }
    await load()
  } finally {
    syncing.value = false
  }
}

async function syncOne(code: string) {
  if (!code) return
  syncing.value = true
  try {
    const r = await marketAdminApi.syncDraw(code, syncCount.value)
    ElMessage.success(`${r.typeName}：${r.message}`)
    await load()
  } finally {
    syncing.value = false
  }
}

async function ensureUpcoming() {
  const created = await marketAdminApi.ensureUpcoming()
  if (created.length) {
    ElMessage.success(
      `已生成 ${created.length} 个期次：` + created.map((i) => `${i.typeCode} ${i.issueNo}`).join('、'),
    )
  } else {
    ElMessage.info('所有彩种都已有在售期次，无需生成')
  }
  await load()
}

async function cleanup() {
  try {
    await ElMessageBox.confirm(
      '将删除「期号长度大于 7 位且没有预测关联」的期次（历史测试数据）。有预测关联的会跳过，不会影响真实开奖数据。',
      '清理测试期次',
      { type: 'warning', confirmButtonText: '确认清理' },
    )
  } catch {
    return
  }
  const r = await marketAdminApi.cleanupIssues()
  ElMessage.success(`已删除 ${r.deleted} 个，跳过 ${r.skipped} 个（有预测关联）`)
  await load()
}

onMounted(async () => {
  types.value = await lotteryApi.types()
  await load()
})
</script>

<template>
  <div class="page">
    <header class="head">
      <div>
        <h1>期次管理</h1>
        <p class="sub">开奖号需双人复核：录入生成票据 → 另一账号凭票据确认</p>
      </div>
      <div class="head-actions">
        <el-select v-model="syncCount" size="default" style="width: 104px">
          <el-option :value="30" label="30 期" />
          <el-option :value="50" label="50 期" />
          <el-option :value="100" label="100 期" />
          <el-option :value="200" label="200 期" />
        </el-select>
        <el-button type="primary" :loading="syncing" @click="syncAll">一键同步全部彩种</el-button>
        <el-dropdown :disabled="syncing" @command="syncOne">
          <el-button :loading="syncing">
            同步单个彩种<el-icon class="el-icon--right"><ArrowDown /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item v-for="t in types" :key="t.code" :command="t.code">
                {{ t.name }}（{{ t.code }}）
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-button @click="ensureUpcoming">生成下一期</el-button>
        <el-button @click="cleanup">清理测试期次</el-button>
        <el-button @click="dialogVisible = true">新建期次</el-button>
      </div>
    </header>

    <div class="filters">
      <el-select v-model="query.typeCode" placeholder="全部彩种" clearable style="width: 140px" @change="search">
        <el-option v-for="t in types" :key="t.code" :label="t.name" :value="t.code" />
      </el-select>
      <el-select v-model="query.drawStatus" placeholder="全部状态" clearable style="width: 120px" @change="search">
        <el-option :value="0" label="待开奖" />
        <el-option :value="1" label="已开奖" />
      </el-select>
      <el-select v-model="query.dataSource" placeholder="全部来源" clearable style="width: 130px" @change="search">
        <el-option :value="1" label="自动同步" />
        <el-option :value="2" label="人工录入" />
      </el-select>
      <el-input
        v-model="query.keyword"
        placeholder="期号搜索"
        clearable
        style="width: 160px"
        @keyup.enter="search"
        @clear="search"
      />
      <el-checkbox v-model="query.officialOnly" @change="search">只看官方期号</el-checkbox>
      <el-button type="primary" @click="search">查询</el-button>
      <el-button @click="resetQuery">重置</el-button>
      <span class="count">共 {{ total }} 期</span>
    </div>

    <el-table v-loading="loading" :data="list" size="small" class="table">
      <el-table-column prop="typeCode" label="彩种" width="80" />
      <el-table-column label="期号" width="150">
        <template #default="{ row }">
          {{ row.issueNo }}
          <el-tag v-if="row.issueNo.length > 7" size="small" type="danger" effect="plain">测试</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="停售时间" width="160">
        <template #default="{ row }">{{ formatDateTime(row.saleEndTime) }}</template>
      </el-table-column>
      <el-table-column label="开奖时间" width="160">
        <template #default="{ row }">{{ formatDateTime(row.drawTime) }}</template>
      </el-table-column>
      <el-table-column label="开奖号" min-width="200">
        <template #default="{ row }">
          <span v-if="row.redNumbers">{{ row.redNumbers }}<span v-if="row.blueNumbers"> + {{ row.blueNumbers }}</span></span>
          <span v-else class="muted">未开奖</span>
        </template>
      </el-table-column>
      <el-table-column prop="drawStatusName" label="状态" width="90" />
      <el-table-column label="来源" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="row.dataSource === 1 ? 'success' : 'info'" effect="plain">
            {{ row.dataSource === 1 ? '自动同步' : '人工录入' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="结算" width="90">
        <template #default="{ row }">
          <el-tag :type="row.settleStatus === 2 ? 'success' : row.settleStatus === 3 ? 'danger' : 'info'" size="small">
            {{ ['待结算', '结算中', '已结算', '失败'][row.settleStatus] }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="190" fixed="right">
        <template #default="{ row }">
          <el-button size="small" text type="primary" @click="openDraw(row)">录入开奖</el-button>
          <el-button v-if="row.drawStatus === 1 && row.settleStatus !== 2" size="small" text type="warning" @click="settle(row)">
            结算
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :page-sizes="[20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @current-change="load"
        @size-change="search"
      />
    </div>

    <el-dialog v-model="dialogVisible" title="新建期次" width="460px">
      <el-form label-width="90px">
        <el-form-item label="彩种">
          <el-select v-model="form.typeCode" style="width: 100%">
            <el-option v-for="t in types" :key="t.code" :label="`${t.name}（${t.code}）`" :value="t.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="期号">
          <el-input v-model="form.issueNo" placeholder="如 2026102" />
        </el-form-item>
        <el-form-item label="开奖时间">
          <el-input v-model="form.drawTime" placeholder="2026-10-27 21:15:00" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="createIssue">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="drawDialog" title="录入开奖号（双人复核）" width="520px">
      <el-form label-width="90px">
        <el-form-item label="主号码">
          <el-input v-model="drawForm.redNumbers" placeholder="如 03,05,12,18,22,29" />
        </el-form-item>
        <el-form-item label="副号码">
          <el-input v-model="drawForm.blueNumbers" placeholder="如 07（无则留空）" />
        </el-form-item>
        <el-form-item label="复核票据">
          <el-input v-model="drawForm.reviewTicket" placeholder="先点“生成票据”，再由复核人填入" />
        </el-form-item>
      </el-form>
      <el-alert type="info" :closable="false" show-icon title="第一步生成票据不写入数据；第二步确认才生效，并触发结算" />
      <template #footer>
        <el-button @click="drawDialog = false">取消</el-button>
        <el-button @click="prepare">生成票据</el-button>
        <el-button type="primary" @click="confirm">复核确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page { padding: 4px; }
.head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 16px; }
.head h1 { margin: 0 0 6px; font-size: 20px; }
.head-actions { display: flex; gap: 8px; flex-wrap: wrap; justify-content: flex-end; }
.sub { margin: 0; font-size: 13px; color: var(--el-text-color-secondary); }
.filters { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 12px; }
.filters .count { margin-left: auto; font-size: 13px; color: var(--el-text-color-secondary); }
.table { background: #fff; border-radius: 10px; }
.pager { display: flex; justify-content: flex-end; margin-top: 12px; }
.muted { color: var(--el-text-color-placeholder); font-size: 12px; }
</style>
