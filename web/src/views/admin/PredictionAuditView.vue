<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { marketAdminApi, type AuditRow } from '@/api/market'

const router = useRouter()
const list = ref<AuditRow[]>([])
const total = ref(0)
const page = ref(1)
const status = ref(1)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const result = await marketAdminApi.auditQueue({ pageNum: page.value, pageSize: 10, status: status.value })
    list.value = result.list
    total.value = result.total
  } finally {
    loading.value = false
  }
}

async function pass(row: AuditRow) {
  await marketAdminApi.audit(row.id, { result: 1, remark: '内容合规' })
  ElMessage.success('已通过并上架')
  await load()
}

async function reject(row: AuditRow) {
  try {
    const { value } = await ElMessageBox.prompt('请填写驳回理由（5-200 字，会原文展示给创作者）', '驳回', {
      inputType: 'textarea',
      inputValidator: (v: string) => (v && v.trim().length >= 5 ? true : '至少 5 个字'),
      confirmButtonText: '确认驳回',
    })
    await marketAdminApi.audit(row.id, { result: 2, remark: value })
    ElMessage.success('已驳回')
    await load()
  } catch {
    /* 取消 */
  }
}

async function offline(row: AuditRow) {
  try {
    const { value } = await ElMessageBox.prompt('下架原因（将全额退款给已购用户）', '下架', {
      inputValidator: (v: string) => (v && v.trim().length >= 2 ? true : '至少 2 个字'),
    })
    await marketAdminApi.offline(row.id, value)
    ElMessage.success('已下架并退款')
    await load()
  } catch {
    /* 取消 */
  }
}

function slaClass(minutes: number) {
  if (minutes <= 0) return 'expired'
  if (minutes < 15) return 'urgent'
  if (minutes < 60) return 'warn'
  return ''
}

onMounted(load)
</script>

<template>
  <div class="page">
    <header class="head">
      <div>
        <h1>预测审核</h1>
        <p class="sub">按提交时间排队；距停售不足 15 分钟的置顶标红，需优先处理</p>
      </div>
      <el-select v-model="status" style="width: 140px" @change="(page = 1); load()">
        <el-option label="待审核" :value="1" />
        <el-option label="已上架" :value="3" />
        <el-option label="已驳回" :value="2" />
        <el-option label="已下架" :value="7" />
      </el-select>
    </header>

    <el-table v-loading="loading" :data="list" size="small" class="table">
      <el-table-column label="SLA" width="110">
        <template #default="{ row }">
          <span class="sla" :class="slaClass(row.remainMinutes)">
            {{ row.remainMinutes <= 0 ? '已停售' : `剩 ${row.remainMinutes} 分` }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="title" label="标题" min-width="260" show-overflow-tooltip>
        <template #default="{ row }">
          <a class="link" @click="router.push(`/prediction/${row.id}`)">{{ row.title }}</a>
        </template>
      </el-table-column>
      <el-table-column prop="penName" label="创作者" width="120" />
      <el-table-column prop="typeCode" label="彩种" width="80" />
      <el-table-column prop="issueNo" label="期号" width="90" />
      <el-table-column prop="pricePoints" label="定价" width="80" />
      <el-table-column label="风控" width="110">
        <template #default="{ row }">
          <el-tag v-if="row.riskHit" type="danger" size="small">{{ row.riskHit }}</el-tag>
          <span v-else class="muted">正常</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <template v-if="row.status === 1">
            <el-button size="small" type="primary" @click="pass(row)">通过</el-button>
            <el-button size="small" type="danger" plain @click="reject(row)">驳回</el-button>
          </template>
          <el-button v-else-if="row.status === 3 || row.status === 4" size="small" type="warning" plain @click="offline(row)">
            下架
          </el-button>
          <span v-else class="muted">{{ row.status === 2 ? '已驳回' : '—' }}</span>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-if="total > 10"
      v-model:current-page="page"
      :total="total"
      :page-size="10"
      layout="prev, pager, next, total"
      class="pager"
      @current-change="load"
    />
  </div>
</template>

<style scoped>
.page { padding: 4px; }
.head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 16px; }
.head h1 { margin: 0 0 6px; font-size: 20px; }
.sub { margin: 0; font-size: 13px; color: var(--el-text-color-secondary); }
.table { background: #fff; border-radius: 10px; }
.sla { font-size: 12px; font-weight: 600; }
.sla.urgent { color: #f56c6c; }
.sla.warn { color: #e6a23c; }
.sla.expired { color: var(--el-text-color-placeholder); }
.link { color: var(--el-color-primary); cursor: pointer; }
.muted { color: var(--el-text-color-placeholder); font-size: 12px; }
.pager { margin-top: 14px; justify-content: flex-end; }
</style>
