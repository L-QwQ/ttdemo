<script setup lang="ts">
import { onMounted, ref } from 'vue'
import {
  deactivationAdminApi,
  reportAdminApi,
  type DeactivationRow,
  type ReportRow,
} from '@/api/growth'
import { formatDateTime, formatFen, maskPhone } from '@/utils/format'

const tab = ref('report')
const loading = ref(false)

const reports = ref<ReportRow[]>([])
const reportTotal = ref(0)
const reportStatus = ref<number | undefined>(1)
const reportPage = ref(1)
const pendingCount = ref(0)

const deactivations = ref<DeactivationRow[]>([])
const deactivationTotal = ref(0)
const deactivationStatus = ref<number | undefined>(1)
const deactivationPage = ref(1)

async function loadReports() {
  loading.value = true
  try {
    const result = await reportAdminApi.page({
      pageNum: reportPage.value,
      pageSize: 10,
      status: reportStatus.value,
    })
    reports.value = result.list
    reportTotal.value = result.total
    pendingCount.value = (await reportAdminApi.pendingCount()).count
  } finally {
    loading.value = false
  }
}

async function handleReport(row: ReportRow, pass: boolean) {
  try {
    const { value } = await ElMessageBox.prompt(
      pass ? '核实成立时填写处理说明，可同时下架该预测' : '请填写驳回理由',
      pass ? '处理举报' : '驳回举报',
      {
        confirmButtonText: '确认',
        cancelButtonText: '取消',
        inputPlaceholder: pass ? '内容违反平台规范，已下架' : '举报内容不成立',
        inputValidator: (v) => (v && v.trim().length >= 2 ? true : '请填写至少 2 个字'),
      },
    )
    const offline = pass
      ? (await ElMessageBox.confirm('是否同时下架该预测？下架会全额退款给已购买用户', '下架确认', {
          confirmButtonText: '下架',
          cancelButtonText: '仅处理举报',
          distinguishCancelAndClose: true,
        }).then(() => true).catch(() => false))
      : false
    await reportAdminApi.handle(row.id, {
      status: pass ? 2 : 3,
      handleResult: value,
      offline,
    })
    ElMessage.success('已处理')
    await loadReports()
  } catch {
    // 取消
  }
}

async function loadDeactivations() {
  loading.value = true
  try {
    const result = await deactivationAdminApi.page({
      pageNum: deactivationPage.value,
      pageSize: 10,
      status: deactivationStatus.value,
    })
    deactivations.value = result.list
    deactivationTotal.value = result.total
  } finally {
    loading.value = false
  }
}

async function handleDeactivation(row: DeactivationRow, approve: boolean) {
  try {
    const tip = approve
      ? `确认注销该账号？\n剩余 ${row.pointsLeft} 积分将清零，需按 ¥${formatFen(row.refundFen)} 原路退回。此操作不可撤销。`
      : '请填写驳回理由'
    const { value } = await ElMessageBox.prompt(tip, approve ? '注销确认' : '驳回注销', {
      confirmButtonText: approve ? '确认注销' : '确认驳回',
      cancelButtonText: '取消',
      inputPlaceholder: approve ? '如有退款流水号可备注' : '用户仍有未完成订单等',
    })
    await deactivationAdminApi.handle(row.id, approve, value)
    ElMessage.success(approve ? '已注销并登记退款' : '已驳回')
    await loadDeactivations()
  } catch {
    // 取消
  }
}

function onTabChange(name: string | number) {
  if (name === 'deactivation' && deactivations.value.length === 0) loadDeactivations()
}

onMounted(loadReports)
</script>

<template>
  <div class="page ui-page view-report-manage">
    <header class="head">
      <div>
        <h1>内容举报与注销</h1>
        <p class="sub">举报待处理 {{ pendingCount }} 条 · 处置结果会写入审计日志</p>
      </div>
    </header>

    <el-tabs v-model="tab" @tab-change="onTabChange">
      <el-tab-pane label="内容举报" name="report">
        <div class="toolbar">
          <el-select v-model="reportStatus" placeholder="全部状态" clearable style="width: 150px" @change="reportPage = 1; loadReports()">
            <el-option label="待处理" :value="1" />
            <el-option label="已处理" :value="2" />
            <el-option label="已驳回" :value="3" />
          </el-select>
          <el-button @click="loadReports">刷新</el-button>
        </div>

        <el-empty v-if="!loading && reports.length === 0" description="暂无举报" />

        <el-table v-loading="loading" :data="reports" size="small" class="table">
          <el-table-column label="时间" width="160">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="被举报对象" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">
              <el-tag size="small" effect="plain">{{ row.targetTypeName }}</el-tag>
              <span class="ml">{{ row.targetTitle || `#${row.targetId}` }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="reasonName" label="原因" width="160" />
          <el-table-column label="举报人" width="130">
            <template #default="{ row }">{{ maskPhone(row.reporterPhone) }}</template>
          </el-table-column>
          <el-table-column label="补充说明" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ row.content || '-' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 2 ? 'success' : row.status === 3 ? 'info' : 'warning'">
                {{ row.statusName }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="140">
            <template #default="{ row }">
              <template v-if="row.status === 1">
                <el-button size="small" text type="primary" @click="handleReport(row, true)">成立</el-button>
                <el-button size="small" text type="info" @click="handleReport(row, false)">驳回</el-button>
              </template>
              <span v-else class="muted">{{ row.handleResult || '已处理' }}</span>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination
          v-model:current-page="reportPage"
          :page-size="10"
          :total="reportTotal"
          layout="total, prev, pager, next"
          class="pager"
          @current-change="loadReports"
        />
      </el-tab-pane>

      <el-tab-pane label="注销申请" name="deactivation">
        <div class="toolbar">
          <el-select v-model="deactivationStatus" placeholder="全部状态" clearable style="width: 150px" @change="deactivationPage = 1; loadDeactivations()">
            <el-option label="待处理" :value="1" />
            <el-option label="已注销" :value="2" />
            <el-option label="已驳回" :value="3" />
          </el-select>
          <el-button @click="loadDeactivations">刷新</el-button>
        </div>

        <el-alert
          type="warning"
          :closable="false"
          show-icon
          class="notice"
          title="通过注销会立即清零剩余积分、停用账号并使已签发令牌失效；退款金额按 1 元 = 10 积分折算，需财务线下原路退回。"
        />

        <el-table v-loading="loading" :data="deactivations" size="small" class="table">
          <el-table-column label="申请时间" width="160">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="手机号" width="130">
            <template #default="{ row }">{{ maskPhone(row.phone) }}</template>
          </el-table-column>
          <el-table-column label="剩余积分" width="100" align="right">
            <template #default="{ row }">{{ row.pointsLeft }}</template>
          </el-table-column>
          <el-table-column label="应退金额" width="110" align="right">
            <template #default="{ row }"><b class="refund">¥{{ formatFen(row.refundFen) }}</b></template>
          </el-table-column>
          <el-table-column label="原因" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ row.reason || '-' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 2 ? 'success' : row.status === 3 ? 'info' : 'warning'">
                {{ row.statusName }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150">
            <template #default="{ row }">
              <template v-if="row.status === 1">
                <el-button size="small" text type="danger" @click="handleDeactivation(row, true)">通过注销</el-button>
                <el-button size="small" text type="info" @click="handleDeactivation(row, false)">驳回</el-button>
              </template>
              <span v-else class="muted">{{ row.handleRemark || '已处理' }}</span>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination
          v-model:current-page="deactivationPage"
          :page-size="10"
          :total="deactivationTotal"
          layout="total, prev, pager, next"
          class="pager"
          @current-change="loadDeactivations"
        />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.page { padding: 4px; }
.head { margin-bottom: 12px; }
h1 { margin: 0 0 4px; font-size: 20px; }
.sub { margin: 0; font-size: 12px; color: var(--text-sub); }
.toolbar { display: flex; gap: 10px; margin-bottom: 12px; }
.notice { margin-bottom: 12px; }
.table { background: #fff; border-radius: 10px; width: 100%; }
.ml { margin-left: 6px; }
.muted { color: var(--text-sub); font-size: 12px; }
.refund { color: var(--el-color-danger); }
</style>
