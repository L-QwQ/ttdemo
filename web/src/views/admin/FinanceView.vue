<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { financeApi, type MoneyFlow, type MoneyOverview } from '@/api/finance'
import { marketAdminApi } from '@/api/market'
import { formatDateTime, formatFen, maskPhone } from '@/utils/format'

const router = useRouter()

const tab = ref('money')
const loading = ref(false)
const range = ref<string[]>([])

const overview = ref<MoneyOverview | null>(null)
const flows = ref<MoneyFlow[]>([])
const flowTotal = ref(0)
const flowPage = ref(1)
const flowType = ref('')

const recon = ref<Record<string, any> | null>(null)
const settlements = ref<Record<string, any>[]>([])

const configs = ref<Record<string, any>[]>([])
const configKeyword = ref('')
const editing = reactive<Record<string, string>>({})

const GROUP_LABELS: Record<string, string> = {
  price: '定价规则',
  point: '积分规则',
  platform: '平台抽成',
  score: '评分与命中',
  hit: '评分与命中',
  reward: '权益兑换',
  finance: '财务口径',
  feature: '功能开关',
  risk: '风控限制',
  create: '发布限制',
}

const configGroups = computed(() => {
  const keyword = configKeyword.value.trim().toLowerCase()
  const groups = new Map<string, Record<string, any>[]>()
  for (const row of configs.value) {
    const key = String(row.configKey ?? '')
    if (keyword && !key.toLowerCase().includes(keyword) && !String(row.remark ?? '').includes(configKeyword.value.trim())) {
      continue
    }
    const prefix = key.split('.')[0] || 'other'
    const label = GROUP_LABELS[prefix] ?? '其他'
    if (!groups.has(label)) groups.set(label, [])
    groups.get(label)!.push(row)
  }
  return Array.from(groups.entries()).map(([label, rows]) => ({ label, rows }))
})

const exchangeRatio = computed(() => overview.value?.pointRate ?? 10)

/** 趋势图最大值，用于计算柱高 */
const trendMax = computed(() => {
  const list = overview.value?.trend ?? []
  const max = Math.max(1, ...list.map((d) => Math.max(d.rechargeFen, d.exchangeFen)))
  return max
})

async function loadOverview() {
  loading.value = true
  try {
    overview.value = await financeApi.overview({
      startDate: range.value?.[0],
      endDate: range.value?.[1],
    })
  } finally {
    loading.value = false
  }
}

async function loadFlows() {
  loading.value = true
  try {
    const result = await financeApi.flows({
      startDate: range.value?.[0],
      endDate: range.value?.[1],
      bizType: flowType.value || undefined,
      pageNum: flowPage.value,
      pageSize: 20,
    })
    flows.value = result.list
    flowTotal.value = result.total
  } finally {
    loading.value = false
  }
}

async function loadRecon() {
  recon.value = await marketAdminApi.reconciliation({
    startDate: range.value?.[0],
    endDate: range.value?.[1],
  })
}

async function loadSettlements() {
  const result = await marketAdminApi.settlements({ pageNum: 1, pageSize: 50 })
  settlements.value = result.list
}

async function loadConfigs() {
  configs.value = await marketAdminApi.configs()
}

async function refreshAll() {
  await Promise.all([loadOverview(), loadFlows()])
}

async function saveConfig(row: Record<string, any>) {
  const key = row.configKey as string
  const value = editing[key]
  if (value === undefined || value === '') {
    ElMessage.warning('请输入新的配置值')
    return
  }
  await marketAdminApi.updateConfig(key, value)
  ElMessage.success('已更新，配置热生效')
  delete editing[key]
  await loadConfigs()
}

async function adjustPoints() {
  const value = editing['__adjust'] ?? ''
  const [userIdText, pointsText, reason] = value.split('|')
  if (!userIdText || !pointsText) {
    ElMessage.warning('格式：用户ID|积分（负数表示扣减）|理由')
    return
  }
  const points = Number(pointsText)
  if (!Number.isFinite(points) || points === 0) {
    ElMessage.warning('积分必须是非零数字')
    return
  }
  await marketAdminApi.adjustPoints(Number(userIdText), {
    points: Math.abs(points),
    increase: points > 0,
    reason: reason || '财务人工调账（至少10个字）',
  })
  ElMessage.success('已调整')
  delete editing['__adjust']
}

async function rebuild() {
  await marketAdminApi.rebuildRank('ACCURACY')
  ElMessage.success('榜单已重算')
}

function onTabChange(name: string | number) {
  if (name === 'flow' && flows.value.length === 0) loadFlows()
  if (name === 'recon' && !recon.value) loadRecon()
  if (name === 'settle' && settlements.value.length === 0) loadSettlements()
  if (name === 'config' && configs.value.length === 0) loadConfigs()
}

onMounted(async () => {
  await refreshAll()
  await Promise.all([loadRecon(), loadSettlements(), loadConfigs()])
})
</script>

<template>
  <div class="page ui-page view-finance">
    <header class="head">
      <div>
        <h1>财务与系统配置</h1>
        <p class="sub">
          积分按 1 元 = {{ exchangeRatio }} 积分折算为金额口径；平台不提供积分兑现，现金流出恒为 0。
        </p>
      </div>
      <div class="head-actions">
        <el-date-picker
          v-model="range"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          size="default"
          @change="refreshAll"
        />
        <el-button type="primary" @click="refreshAll">刷新</el-button>
        <el-button @click="router.push('/admin/rewards')">权益管理</el-button>
      </div>
    </header>

    <el-alert
      v-if="overview"
      type="info"
      :closable="false"
      show-icon
      class="notice"
      :title="overview.note"
    />

    <el-tabs v-model="tab" @tab-change="onTabChange">
      <!-- ---------- 资金总览 ---------- -->
      <el-tab-pane label="资金总览" name="money">
        <div v-loading="loading" class="kpi-grid">
          <div class="kpi primary">
            <span class="kpi-label">充值实收（现金流入）</span>
            <strong class="kpi-value">¥{{ formatFen(overview?.cashInFen) }}</strong>
            <span class="kpi-foot">{{ overview?.rechargeCount ?? 0 }} 笔 · 发放 {{ overview?.rechargePoints ?? 0 }} 积分</span>
          </div>
          <div class="kpi success">
            <span class="kpi-label">平台净入</span>
            <strong class="kpi-value">¥{{ formatFen(overview?.netPlatformFen) }}</strong>
            <span class="kpi-foot">充值实收 − 权益成本</span>
          </div>
          <div class="kpi warning">
            <span class="kpi-label">权益兑换成本</span>
            <strong class="kpi-value">¥{{ formatFen(overview?.exchangeFen) }}</strong>
            <span class="kpi-foot">{{ overview?.exchangeCount ?? 0 }} 单 · 消耗 {{ overview?.exchangePoints ?? 0 }} 积分</span>
          </div>
          <div class="kpi">
            <span class="kpi-label">现金流出</span>
            <strong class="kpi-value">¥{{ formatFen(overview?.cashOutFen) }}</strong>
            <span class="kpi-foot">无提现通道，恒为 0</span>
          </div>
        </div>

        <div class="mini-grid">
          <div class="mini">
            <span>平台抽成</span>
            <strong>¥{{ formatFen(overview?.platformFeeFen) }}</strong>
            <em>{{ overview?.platformFeePoints ?? 0 }} 积分</em>
          </div>
          <div class="mini">
            <span>创作者收益</span>
            <strong>¥{{ formatFen(overview?.creatorIncomeFen) }}</strong>
            <em>{{ overview?.creatorIncomePoints ?? 0 }} 积分</em>
          </div>
          <div class="mini">
            <span>预测消费</span>
            <strong>¥{{ formatFen(overview?.consumeFen) }}</strong>
            <em>{{ overview?.consumeCount ?? 0 }} 单</em>
          </div>
          <div class="mini">
            <span>用户积分负债</span>
            <strong>¥{{ formatFen(overview?.pointLiabilityFen) }}</strong>
            <em>{{ overview?.pointLiabilityPoints ?? 0 }} 积分 · {{ overview?.accountCount ?? 0 }} 账户</em>
          </div>
          <div class="mini">
            <span>待发放权益</span>
            <strong>{{ overview?.pendingRewardCount ?? 0 }} 单</strong>
            <em>实物礼品待处理</em>
          </div>
          <div class="mini">
            <span>已入账结算</span>
            <strong>¥{{ formatFen(overview?.settleGrossFen) }}</strong>
            <em>{{ overview?.settleCount ?? 0 }} 单</em>
          </div>
        </div>

        <div class="page-card">
          <div class="card-head">
            <h3>每日资金趋势</h3>
            <span class="legend">
              <i class="dot in" />充值
              <i class="dot out" />权益成本
            </span>
          </div>
          <div v-if="(overview?.trend?.length ?? 0) === 0" class="empty">所选区间暂无资金流水</div>
          <div v-else class="bars">
            <div v-for="d in overview?.trend" :key="d.day" class="bar-col" :title="`${d.day} 充值 ¥${formatFen(d.rechargeFen)} / 权益 ¥${formatFen(d.exchangeFen)}`">
              <div class="bar-stack">
                <div class="bar in" :style="{ height: `${Math.max((d.rechargeFen / trendMax) * 100, d.rechargeFen > 0 ? 2 : 0)}%` }" />
                <div class="bar out" :style="{ height: `${Math.max((d.exchangeFen / trendMax) * 100, d.exchangeFen > 0 ? 2 : 0)}%` }" />
              </div>
              <span class="bar-day">{{ d.day.slice(5) }}</span>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- ---------- 资金流水 ---------- -->
      <el-tab-pane label="资金流水" name="flow">
        <div class="toolbar">
          <el-select v-model="flowType" placeholder="全部类型" clearable style="width: 160px" @change="flowPage = 1; loadFlows()">
            <el-option label="积分充值" value="RECHARGE" />
            <el-option label="权益兑换" value="REWARD" />
            <el-option label="创作者结算" value="SETTLE" />
          </el-select>
          <el-button @click="loadFlows">查询</el-button>
        </div>
        <el-table v-loading="loading" :data="flows" size="small" class="table">
          <el-table-column label="时间" width="165">
            <template #default="{ row }">{{ formatDateTime(row.bizTime) }}</template>
          </el-table-column>
          <el-table-column label="类型" width="110">
            <template #default="{ row }">
              <el-tag size="small" :type="row.bizType === 'RECHARGE' ? 'success' : row.bizType === 'REWARD' ? 'warning' : 'info'">
                {{ row.bizTypeName }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="bizNo" label="业务单号" width="215" show-overflow-tooltip />
          <el-table-column label="用户" width="150">
            <template #default="{ row }">
              <div class="user-cell">
                <span>{{ row.userNickname || '-' }}</span>
                <em>{{ maskPhone(row.userPhone) }}</em>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="积分变动" width="110" align="right">
            <template #default="{ row }">
              <span :class="row.points >= 0 ? 'plus' : 'minus'">{{ row.points > 0 ? '+' : '' }}{{ row.points }}</span>
            </template>
          </el-table-column>
          <el-table-column label="金额" width="120" align="right">
            <template #default="{ row }">
              <span v-if="row.amountFen === 0" class="muted">—</span>
              <span v-else :class="row.amountFen > 0 ? 'plus' : 'minus'">
                {{ row.amountFen > 0 ? '+' : '-' }}¥{{ formatFen(Math.abs(row.amountFen)) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="statusName" label="状态" width="100" />
        </el-table>
        <el-pagination
          v-model:current-page="flowPage"
          :page-size="20"
          :total="flowTotal"
          layout="total, prev, pager, next"
          class="pager"
          @current-change="loadFlows"
        />
      </el-tab-pane>

      <!-- ---------- 积分对账 ---------- -->
      <el-tab-pane label="积分对账" name="recon">
        <el-alert
          v-if="recon && !recon.accountCheck?.balanced"
          type="error"
          :closable="false"
          show-icon
          class="mb"
          :title="`账务不平衡！差异用户 ${recon.accountCheck?.diffUsers} 个，禁止日结`"
        />
        <el-alert
          v-else-if="recon"
          type="success"
          :closable="false"
          show-icon
          class="mb"
          :title="`账务平衡（校验于 ${formatDateTime(recon.accountCheck?.checkedAt)}）`"
        />

        <div v-if="recon" class="mini-grid">
          <div class="mini"><span>消费积分</span><strong>{{ recon.consume?.points ?? 0 }}</strong><em>{{ recon.consume?.orderCount ?? 0 }} 单</em></div>
          <div class="mini"><span>创作者所得</span><strong>{{ recon.settle?.creatorPoints ?? 0 }}</strong><em>积分</em></div>
          <div class="mini"><span>平台抽成</span><strong>{{ recon.settle?.platformFee ?? 0 }}</strong><em>积分</em></div>
          <div class="mini"><span>退款积分</span><strong>{{ recon.refund?.points ?? 0 }}</strong><em>{{ recon.refund?.orderCount ?? 0 }} 单</em></div>
        </div>

        <el-table v-if="recon?.issueBreakdown?.length" :data="recon.issueBreakdown" size="small" class="table">
          <el-table-column prop="typeCode" label="彩种" width="100" />
          <el-table-column prop="gross" label="成交总额（积分）" />
          <el-table-column prop="fee" label="平台抽成（积分）" />
          <el-table-column prop="income" label="创作者所得（积分）" />
        </el-table>
        <div class="toolbar mt">
          <el-button @click="rebuild">重算榜单</el-button>
        </div>
      </el-tab-pane>

      <!-- ---------- 结算单 ---------- -->
      <el-tab-pane label="结算单" name="settle">
        <el-table v-loading="loading" :data="settlements" size="small" class="table">
          <el-table-column prop="settleNo" label="结算单号" width="200" />
          <el-table-column prop="typeCode" label="彩种" width="80" />
          <el-table-column prop="issueNo" label="期号" width="110" />
          <el-table-column prop="orderCount" label="订单数" width="90" />
          <el-table-column prop="grossPoints" label="总额" width="100" />
          <el-table-column prop="platformFee" label="抽成" width="90" />
          <el-table-column prop="creatorPoints" label="创作者所得" width="110" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">{{ ['', '待入账', '已入账', '已撤销'][row.status] }}</template>
          </el-table-column>
          <el-table-column label="入账时间" width="160">
            <template #default="{ row }">{{ formatDateTime(row.creditTime) }}</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ---------- 系统配置 ---------- -->
      <el-tab-pane label="系统配置" name="config">
        <div class="toolbar">
          <el-input v-model="configKeyword" placeholder="搜索配置键或说明" clearable style="width: 280px" />
          <el-button @click="loadConfigs">刷新</el-button>
          <el-input v-model="editing['__adjust']" placeholder="人工调账：用户ID|积分（负数扣减）|理由" style="width: 360px" />
          <el-button @click="adjustPoints">调账</el-button>
        </div>

        <div v-for="group in configGroups" :key="group.label" class="config-group">
          <h4 class="group-title">{{ group.label }}</h4>
          <el-table :data="group.rows" size="small" class="table">
            <el-table-column prop="configKey" label="配置键" width="250" />
            <el-table-column label="当前值" width="130">
              <template #default="{ row }">
                <el-tag size="small" effect="plain">{{ row.configValue }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="类型" width="90">
              <template #default="{ row }"><span class="muted">{{ row.valueType }}</span></template>
            </el-table-column>
            <el-table-column prop="remark" label="说明" min-width="220" show-overflow-tooltip />
            <el-table-column label="修改为" width="150">
              <template #default="{ row }">
                <el-input v-model="editing[row.configKey]" :placeholder="row.configValue" size="small" :disabled="row.editable === 0" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80">
              <template #default="{ row }">
                <el-button size="small" text type="primary" :disabled="row.editable === 0" @click="saveConfig(row)">保存</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
        <div v-if="configGroups.length === 0" class="empty">没有匹配的配置项</div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.page { padding: 4px; }
.head { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; margin-bottom: 12px; flex-wrap: wrap; }
h1 { margin: 0 0 4px; font-size: 20px; }
.sub { margin: 0; font-size: 12px; color: var(--text-sub); }
.head-actions { display: flex; gap: 8px; align-items: center; }
.notice { margin-bottom: 12px; }
.mb { margin-bottom: 14px; }
.mt { margin-top: 12px; }
.toolbar { display: flex; gap: 10px; margin-bottom: 14px; flex-wrap: wrap; }

.kpi-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 200px), 1fr)); gap: 12px; margin-bottom: 12px; }
.kpi { background: #fff; border: 1px solid var(--border-soft); border-radius: 12px; padding: 14px 16px; display: flex; flex-direction: column; gap: 4px; border-left: 3px solid var(--el-border-color); }
.kpi.primary { border-left-color: var(--brand); }
.kpi.success { border-left-color: #22b07d; }
.kpi.warning { border-left-color: var(--gold); }
.kpi-label { font-size: 12px; color: var(--text-sub); }
.kpi-value { font-size: 22px; letter-spacing: -0.5px; }
.kpi-foot { font-size: 11px; color: var(--text-sub); }

.mini-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 150px), 1fr)); gap: 10px; margin-bottom: 14px; }
.mini { background: #fff; border: 1px solid var(--border-soft); border-radius: 10px; padding: 12px 14px; display: flex; flex-direction: column; gap: 2px; }
.mini span { font-size: 12px; color: var(--text-sub); }
.mini strong { font-size: 17px; }
.mini em { font-style: normal; font-size: 11px; color: var(--text-sub); }

.card-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.card-head h3 { margin: 0; font-size: 14px; }
.legend { font-size: 12px; color: var(--text-sub); display: flex; align-items: center; gap: 6px; }
.dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; margin-left: 8px; }
.dot.in { background: var(--brand); }
.dot.out { background: var(--gold); }

.bars { display: flex; align-items: flex-end; gap: 6px; height: 140px; overflow-x: auto; padding-top: 8px; }
.bar-col { flex: 1 0 22px; display: flex; flex-direction: column; align-items: center; gap: 6px; }
.bar-stack { width: 100%; height: 110px; display: flex; align-items: flex-end; justify-content: center; gap: 2px; }
.bar { width: 8px; border-radius: 3px 3px 0 0; min-height: 0; }
.bar.in { background: var(--brand); }
.bar.out { background: var(--gold); }
.bar-day { font-size: 10px; color: var(--text-sub); }

.table { background: #fff; border-radius: 10px; width: 100%; }
.empty { color: var(--text-sub); font-size: 13px; padding: 24px; text-align: center; }
.user-cell { display: flex; flex-direction: column; line-height: 1.4; }
.user-cell em { font-style: normal; font-size: 11px; color: var(--text-sub); }
.plus { color: var(--brand); font-weight: 600; }
.minus { color: #22b07d; font-weight: 600; }
.muted { color: var(--text-sub); }

.config-group { margin-bottom: 18px; }
.group-title { margin: 0 0 8px; font-size: 13px; color: var(--text-main); padding-left: 8px; border-left: 3px solid var(--brand); }
</style>
