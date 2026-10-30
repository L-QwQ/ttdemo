<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { marketAdminApi } from '@/api/market'
import { formatDateTime } from '@/utils/format'

type Row = Record<string, any>

const tab = ref('realname')
const loading = ref(false)

const realnames = ref<Row[]>([])
const realnameTotal = ref(0)
const realnameStats = ref({ pending: 0, passed: 0, rejected: 0 })
// -1 表示「全部」，避免 el-radio-group 用 undefined 做值
const realnameQuery = reactive({
  auditStatus: -1,
  keyword: '',
  pageNum: 1,
  pageSize: 20,
})

const creators = ref<Row[]>([])
const creatorTotal = ref(0)
const creatorQuery = reactive({
  status: -1,
  keyword: '',
  pageNum: 1,
  pageSize: 20,
})

const REALNAME_STATUS = ['待审核', '已通过', '已驳回']
const REALNAME_TAG = ['warning', 'success', 'danger'] as const
const CREATOR_STATUS = ['', '待审核', '已认证', '已驳回', '已封禁']

async function loadRealnames() {
  loading.value = true
  try {
    const result = await marketAdminApi.realnames({
      pageNum: realnameQuery.pageNum,
      pageSize: realnameQuery.pageSize,
      auditStatus: realnameQuery.auditStatus < 0 ? undefined : realnameQuery.auditStatus,
      keyword: realnameQuery.keyword.trim() || undefined,
    })
    realnames.value = result.list
    realnameTotal.value = result.total
    // 审核完最后一条时当前页会变空，回退一页
    if (!result.list.length && result.total > 0 && realnameQuery.pageNum > 1) {
      realnameQuery.pageNum -= 1
      await loadRealnames()
      return
    }
    realnameStats.value = await marketAdminApi.realnameStats()
  } finally {
    loading.value = false
  }
}

async function loadCreators() {
  loading.value = true
  try {
    const result = await marketAdminApi.creators({
      pageNum: creatorQuery.pageNum,
      pageSize: creatorQuery.pageSize,
      status: creatorQuery.status < 0 ? undefined : creatorQuery.status,
      keyword: creatorQuery.keyword.trim() || undefined,
    })
    creators.value = result.list
    creatorTotal.value = result.total
    if (!result.list.length && result.total > 0 && creatorQuery.pageNum > 1) {
      creatorQuery.pageNum -= 1
      await loadCreators()
    }
  } finally {
    loading.value = false
  }
}

function searchRealnames() {
  realnameQuery.pageNum = 1
  loadRealnames()
}

function searchCreators() {
  creatorQuery.pageNum = 1
  loadCreators()
}

async function resetRealnames() {
  realnameQuery.auditStatus = -1
  realnameQuery.keyword = ''
  searchRealnames()
}

async function auditRealname(row: Row, pass: boolean) {
  let remark = '资料完整'
  if (!pass) {
    try {
      const { value } = await ElMessageBox.prompt('驳回原因（至少 2 字）', '驳回实名', {
        inputValidator: (v: string) => (v && v.trim().length >= 2 ? true : '至少 2 个字'),
      })
      remark = value
    } catch {
      return
    }
  }
  await marketAdminApi.auditRealname(Number(row.id), { result: pass ? 1 : 2, remark })
  ElMessage.success(pass ? '已通过' : '已驳回')
  await loadRealnames()
}

async function resetRealname(row: Row) {
  let remark = '实名信息有误，请重新提交'
  try {
    const { value } = await ElMessageBox.prompt(
      '撤销后该账号回到「已驳回」，用户可重新提交实名。请填写原因（至少 2 字）',
      `撤销 ${row.nickname || row.phone} 的实名`,
      {
        inputValue: remark,
        inputValidator: (v: string) => (v && v.trim().length >= 2 ? true : '至少 2 个字'),
      },
    )
    remark = value
  } catch {
    return
  }
  await marketAdminApi.resetRealname(Number(row.id), remark)
  ElMessage.success('已撤销，用户可重新提交')
  await loadRealnames()
}

async function auditCreator(row: Row, pass: boolean) {
  let remark = '通过'
  if (!pass) {
    try {
      const { value } = await ElMessageBox.prompt('驳回原因（至少 2 字）', '驳回申请', {
        inputValidator: (v: string) => (v && v.trim().length >= 2 ? true : '至少 2 个字'),
      })
      remark = value
    } catch {
      return
    }
  }
  await marketAdminApi.auditCreator(Number(row.id), { result: pass ? 1 : 2, remark })
  ElMessage.success(pass ? '已通过' : '已驳回')
  await loadCreators()
}

onMounted(async () => {
  await Promise.all([loadRealnames(), loadCreators()])
})
</script>

<template>
  <div class="page">
    <div class="head">
      <h1>创作者审核</h1>
      <span class="hint">
        实名申请按「待审核 → 最近提交」排序；姓名/身份证为脱敏展示，可结合用户 ID、昵称、手机号核对
      </span>
    </div>

    <el-tabs v-model="tab">
      <el-tab-pane name="realname">
        <template #label>
          实名审核
          <el-badge v-if="realnameStats.pending" :value="realnameStats.pending" class="tab-badge" />
        </template>

        <div class="filters">
          <el-radio-group v-model="realnameQuery.auditStatus" size="default" @change="searchRealnames">
            <el-radio-button :value="-1">全部 {{ realnameStats.pending + realnameStats.passed + realnameStats.rejected }}</el-radio-button>
            <el-radio-button :value="0">待审核 {{ realnameStats.pending }}</el-radio-button>
            <el-radio-button :value="1">已通过 {{ realnameStats.passed }}</el-radio-button>
            <el-radio-button :value="2">已驳回 {{ realnameStats.rejected }}</el-radio-button>
          </el-radio-group>
          <el-input
            v-model="realnameQuery.keyword"
            placeholder="手机号 / 昵称 / 用户ID"
            clearable
            style="width: 200px"
            @keyup.enter="searchRealnames"
            @clear="searchRealnames"
          />
          <el-button type="primary" @click="searchRealnames">查询</el-button>
          <el-button @click="resetRealnames">重置</el-button>
          <el-button :loading="loading" @click="loadRealnames">刷新</el-button>
        </div>

        <el-table
          v-loading="loading"
          :data="realnames"
          size="small"
          class="table"
          empty-text="暂无实名申请"
          :row-class-name="({ row }: { row: Row }) => (row.idCardInvalid ? 'row-danger' : '')"
        >
          <el-table-column prop="userId" label="用户ID" width="80" />
          <el-table-column label="用户" min-width="160">
            <template #default="{ row }">
              <div class="user-cell">
                <span>{{ row.nickname || '未设置昵称' }}</span>
                <span class="sub">{{ row.phone }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="realName" label="姓名" width="110" />
          <el-table-column prop="idCard" label="身份证" width="180" />
          <el-table-column label="年龄" width="110">
            <template #default="{ row }">
              <el-tooltip v-if="row.idCardInvalid" content="身份证出生日期或校验位不合法，建议驳回要求重新提交" placement="top">
                <el-tag type="danger" size="small">证件异常</el-tag>
              </el-tooltip>
              <el-tag v-else-if="row.minor" type="danger" size="small">{{ row.age }}（未成年）</el-tag>
              <span v-else>{{ row.age }} 岁</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="REALNAME_TAG[row.auditStatus]" size="small" effect="light">
                {{ REALNAME_STATUS[row.auditStatus] }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="提交时间" width="165">
            <template #default="{ row }">{{ formatDateTime(row.submittedAt) }}</template>
          </el-table-column>
          <el-table-column label="审核备注" min-width="130" show-overflow-tooltip>
            <template #default="{ row }">
              <span :class="{ muted: !row.auditRemark }">{{ row.auditRemark || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <template v-if="row.auditStatus === 0">
                <el-button size="small" type="primary" @click="auditRealname(row, true)">通过</el-button>
                <el-button size="small" type="danger" plain @click="auditRealname(row, false)">驳回</el-button>
              </template>
              <el-button v-else-if="row.auditStatus === 1" size="small" text type="danger" @click="resetRealname(row)">
                撤销
              </el-button>
              <span v-else class="muted">已处理</span>
            </template>
          </el-table-column>
        </el-table>

        <div class="pager">
          <el-pagination
            v-model:current-page="realnameQuery.pageNum"
            v-model:page-size="realnameQuery.pageSize"
            :page-sizes="[10, 20, 50]"
            :total="realnameTotal"
            layout="total, sizes, prev, pager, next"
            background
            @current-change="loadRealnames"
            @size-change="searchRealnames"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane label="创作者申请" name="creator">
        <div class="filters">
          <el-radio-group v-model="creatorQuery.status" @change="searchCreators">
            <el-radio-button :value="-1">全部</el-radio-button>
            <el-radio-button :value="1">待审核</el-radio-button>
            <el-radio-button :value="2">已认证</el-radio-button>
            <el-radio-button :value="3">已驳回</el-radio-button>
          </el-radio-group>
          <el-input
            v-model="creatorQuery.keyword"
            placeholder="笔名搜索"
            clearable
            style="width: 180px"
            @keyup.enter="searchCreators"
            @clear="searchCreators"
          />
          <el-button type="primary" @click="searchCreators">查询</el-button>
          <el-button :loading="loading" @click="loadCreators">刷新</el-button>
        </div>

        <el-table v-loading="loading" :data="creators" size="small" class="table" empty-text="暂无创作者申请">
          <el-table-column prop="penName" label="笔名" width="130" />
          <el-table-column prop="tags" label="标签" width="150" show-overflow-tooltip />
          <el-table-column prop="intro" label="简介" min-width="200" show-overflow-tooltip />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">{{ CREATOR_STATUS[row.status] }}</template>
          </el-table-column>
          <el-table-column label="准确度" width="120">
            <template #default="{ row }">{{ (row.accuracyScore ?? 0).toFixed(1) }} / {{ row.sampleSize }} 期</template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <template v-if="row.status === 1">
                <el-button size="small" type="primary" @click="auditCreator(row, true)">通过</el-button>
                <el-button size="small" type="danger" plain @click="auditCreator(row, false)">驳回</el-button>
              </template>
              <span v-else class="muted">已处理</span>
            </template>
          </el-table-column>
        </el-table>

        <div class="pager">
          <el-pagination
            v-model:current-page="creatorQuery.pageNum"
            v-model:page-size="creatorQuery.pageSize"
            :page-sizes="[10, 20, 50]"
            :total="creatorTotal"
            layout="total, sizes, prev, pager, next"
            background
            @current-change="loadCreators"
            @size-change="searchCreators"
          />
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.page { padding: 4px; }
.head { display: flex; align-items: baseline; gap: 12px; flex-wrap: wrap; margin-bottom: 12px; }
h1 { margin: 0; font-size: 20px; }
.hint { font-size: 12px; color: var(--el-text-color-secondary); }
.tab-badge { margin-left: 6px; }
.filters { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 12px; }
.table { background: #fff; border-radius: 10px; }
.pager { display: flex; justify-content: flex-end; margin-top: 12px; }
.user-cell { display: flex; flex-direction: column; line-height: 1.35; }
.user-cell .sub { font-size: 11px; color: var(--el-text-color-secondary); }
.muted { color: var(--el-text-color-placeholder); font-size: 12px; }
:deep(.row-danger) { background: var(--el-color-danger-light-9); }
</style>
