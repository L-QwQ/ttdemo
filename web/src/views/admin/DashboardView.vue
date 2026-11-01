<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { adminApi, type AdminDistribution, type AdminTrend } from '@/api/admin'
import type { AdminStats } from '@/types'
import TrendChart from '@/components/admin/TrendChart.vue'
import { typeName } from '@/utils/lottery'

interface PlatformStats {
  onlinePredictions: number
  pendingAuditPredictions: number
  settledPredictions: number
  totalCreators: number
  pendingRealname: number
  paidOrders: number
  drawnIssues: number
  officialDrawnIssues: number
}

const loading = ref(false)
const trendLoading = ref(false)
const range = ref(14)

const stats = ref<AdminStats | null>(null)
const platform = ref<PlatformStats | null>(null)
const trend = ref<AdminTrend | null>(null)
const dist = ref<AdminDistribution | null>(null)

/** 彩种主色，和前台标签保持一致 */
const TYPE_COLORS: Record<string, string> = {
  SSQ: '#d92b2b',
  DLT: '#2b7cd9',
  FC3D: '#c2541a',
  PL3: '#5a34b0',
  KL8: '#b0891f',
}

const ORDER_STATUS: Record<string, string> = {
  '1': '待支付',
  '2': '已支付',
  '3': '已退款',
  '4': '已关闭',
  '5': '已结算',
}

async function fetchAll() {
  loading.value = true
  try {
    const [s, p, t, d] = await Promise.all([
      adminApi.stats(),
      adminApi.platformStats().catch(() => null),
      adminApi.trend(range.value),
      adminApi.distribution().catch(() => null),
    ])
    stats.value = s
    platform.value = p
    trend.value = t
    dist.value = d
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

async function reloadTrend() {
  trendLoading.value = true
  try {
    trend.value = await adminApi.trend(range.value)
  } catch {
    // 拦截器已提示
  } finally {
    trendLoading.value = false
  }
}

watch(range, reloadTrend)

/** 趋势末位 = 今日，倒数第二位 = 昨日 */
function lastValue(values?: number[]) {
  return values && values.length ? (values[values.length - 1] ?? 0) : 0
}

function deltaOf(values?: number[]): { text: string; up: boolean; flat: boolean } {
  const today = lastValue(values)
  const yesterday = values && values.length > 1 ? (values[values.length - 2] ?? 0) : 0
  if (!yesterday) {
    return today > 0 ? { text: '昨日无数据', up: true, flat: true } : { text: '—', up: true, flat: true }
  }
  const pct = ((today - yesterday) / yesterday) * 100
  return {
    text: `${pct >= 0 ? '↑' : '↓'} ${Math.abs(pct).toFixed(0)}% 较昨日`,
    up: pct >= 0,
    flat: false,
  }
}

interface Kpi {
  label: string
  value: number
  tone: string
  sub?: string
  to?: string
  delta?: { text: string; up: boolean; flat: boolean }
}

const kpis = computed<Kpi[]>(() => {
  const s = stats.value
  return [
    {
      label: '用户总数',
      value: s?.totalUsers ?? 0,
      sub: `正常 ${s?.enabledUsers ?? 0} · 禁用 ${s?.disabledUsers ?? 0}`,
      tone: 'red',
    },
    {
      label: '今日新增',
      value: s?.todayNewUsers ?? 0,
      delta: deltaOf(trend.value?.newUsers),
      tone: 'green',
    },
    {
      label: '今日登录',
      value: s?.todayLogins ?? 0,
      delta: deltaOf(trend.value?.logins),
      tone: 'blue',
    },
    {
      label: '待审核预测',
      value: platform.value?.pendingAuditPredictions ?? 0,
      sub: `已上架 ${platform.value?.onlinePredictions ?? 0} 条`,
      tone: 'gold',
      to: '/admin/audit',
    },
  ]
})

const chartSeries = computed(() => [
  { key: 'newUsers', name: '新增用户', color: '#d92b2b', values: trend.value?.newUsers ?? [] },
  { key: 'logins', name: '登录次数', color: '#2b7cd9', values: trend.value?.logins ?? [] },
  { key: 'predictions', name: '发布预测', color: '#d9982b', values: trend.value?.predictions ?? [] },
  { key: 'orders', name: '解锁订单', color: '#22a06b', values: trend.value?.orders ?? [] },
])

const totalPredictions = computed(() =>
  (dist.value?.predictionsByType ?? []).reduce((sum, item) => sum + item.value, 0),
)

const typeBars = computed(() => {
  const items = dist.value?.predictionsByType ?? []
  const max = Math.max(1, ...items.map((i) => i.value))
  const total = totalPredictions.value
  return items.map((item) => ({
    name: typeName(item.key),
    value: item.value,
    color: TYPE_COLORS[item.key] ?? '#9aa0a6',
    width: Math.max(3, Math.round((item.value / max) * 100)),
    share: total ? Math.round((item.value / total) * 100) : 0,
  }))
})

const orderBars = computed(() => {
  const items = dist.value?.ordersByStatus ?? []
  const total = items.reduce((sum, i) => sum + i.value, 0)
  return items.map((item) => ({
    name: ORDER_STATUS[item.key] ?? `状态 ${item.key}`,
    value: item.value,
    color: item.key === '5' ? '#22a06b' : item.key === '2' ? '#2b7cd9' : item.key === '3' ? '#d92b2b' : '#9aa0a6',
    width: Math.max(3, Math.round((item.value / Math.max(1, ...items.map((i) => i.value))) * 100)),
    share: total ? Math.round((item.value / total) * 100) : 0,
  }))
})

const todos = computed(() => {
  const pendingDraw = (dist.value?.issuesByDrawStatus ?? []).find((i) => i.key === '0')?.value ?? 0
  const pendingPay = (dist.value?.ordersByStatus ?? []).find((i) => i.key === '1')?.value ?? 0
  return [
    {
      label: '待审核预测',
      value: platform.value?.pendingAuditPredictions ?? 0,
      to: '/admin/audit',
      hint: '审核通过后才对外展示',
    },
    {
      label: '待审实名申请',
      value: platform.value?.pendingRealname ?? 0,
      to: '/admin/creators',
      hint: '创作者提现前必须实名',
    },
    {
      label: '待开奖期次',
      value: pendingDraw,
      to: '/admin/issues',
      hint: '开奖后由同步任务回填',
    },
    {
      label: '待支付订单',
      value: pendingPay,
      to: '/admin/finance',
      hint: '超时未支付会自动关闭',
    },
  ]
})

const overview = computed(() => [
  { label: '在售预测', value: platform.value?.onlinePredictions ?? 0 },
  { label: '已结算预测', value: platform.value?.settledPredictions ?? 0 },
  { label: '创作者总数', value: platform.value?.totalCreators ?? 0 },
  { label: '已支付订单', value: platform.value?.paidOrders ?? 0 },
  { label: '已开奖期次', value: platform.value?.drawnIssues ?? 0 },
  { label: '官方同步开奖', value: platform.value?.officialDrawnIssues ?? 0 },
])

onMounted(fetchAll)
</script>

<template>
  <div v-loading="loading" class="dash ui-page view-dashboard">
    <!-- KPI -->
    <div class="kpi-grid">
      <template v-for="k in kpis" :key="k.label">
        <router-link v-if="k.to" :to="k.to" class="kpi link" :class="k.tone">
          <p class="kpi-label">{{ k.label }}</p>
          <p class="kpi-value">{{ k.value }}</p>
          <p class="kpi-sub">{{ k.sub }}</p>
        </router-link>
        <div v-else class="kpi" :class="k.tone">
          <p class="kpi-label">{{ k.label }}</p>
          <p class="kpi-value">{{ k.value }}</p>
          <p v-if="k.delta" class="kpi-delta" :class="[{ up: k.delta.up, down: !k.delta.up }, { flat: k.delta.flat }]">
            {{ k.delta.text }}
          </p>
          <p v-else class="kpi-sub">{{ k.sub }}</p>
        </div>
      </template>
    </div>

    <!-- 趋势 -->
    <section class="panel">
      <header class="panel-head">
        <div>
          <h2>数据趋势</h2>
          <p class="panel-sub">按天统计，灰色虚线表示当天为 0</p>
        </div>
        <el-radio-group v-model="range" size="small">
          <el-radio-button :value="7">7 天</el-radio-button>
          <el-radio-button :value="14">14 天</el-radio-button>
          <el-radio-button :value="30">30 天</el-radio-button>
        </el-radio-group>
      </header>
      <div v-loading="trendLoading" class="chart-wrap">
        <TrendChart
          v-if="trend && trend.dates.length"
          :labels="trend.dates"
          :series="chartSeries"
          :height="250"
        />
        <div v-else class="empty-block">暂无趋势数据</div>
      </div>
    </section>

    <!-- 分布 + 待办 -->
    <div class="two-col">
      <section class="panel">
        <header class="panel-head">
          <div>
            <h2>预测彩种分布</h2>
            <p class="panel-sub">共 {{ totalPredictions }} 条预测</p>
          </div>
          <router-link to="/admin/audit" class="panel-link">去审核 →</router-link>
        </header>

        <div v-if="typeBars.length" class="bar-list">
          <div v-for="bar in typeBars" :key="bar.name" class="bar-row">
            <span class="bar-name">{{ bar.name }}</span>
            <span class="bar-track">
              <i :style="{ width: bar.width + '%', background: bar.color }" />
            </span>
            <span class="bar-value">{{ bar.value }}<em>{{ bar.share }}%</em></span>
          </div>
        </div>
        <div v-else class="empty-block">暂无预测数据</div>

        <div v-if="orderBars.length" class="sub-block">
          <p class="sub-title">订单状态</p>
          <div v-for="bar in orderBars" :key="bar.name" class="bar-row">
            <span class="bar-name">{{ bar.name }}</span>
            <span class="bar-track">
              <i :style="{ width: bar.width + '%', background: bar.color }" />
            </span>
            <span class="bar-value">{{ bar.value }}<em>{{ bar.share }}%</em></span>
          </div>
        </div>
      </section>

      <section class="panel">
        <header class="panel-head">
          <div>
            <h2>待办事项</h2>
            <p class="panel-sub">需要人工处理的任务</p>
          </div>
        </header>
        <ul class="todo-list">
          <li v-for="todo in todos" :key="todo.label">
            <router-link :to="todo.to" class="todo-item">
              <span class="todo-dot" :class="{ active: todo.value > 0 }" />
              <span class="todo-text">
                <b>{{ todo.label }}</b>
                <em>{{ todo.hint }}</em>
              </span>
              <span class="todo-count" :class="{ active: todo.value > 0 }">{{ todo.value }}</span>
            </router-link>
          </li>
        </ul>
      </section>
    </div>

    <!-- 平台概览 -->
    <section class="panel">
      <header class="panel-head">
        <div>
          <h2>平台概览</h2>
          <p class="panel-sub">来自各业务模块的累计数据</p>
        </div>
        <el-button text type="primary" @click="fetchAll">刷新数据</el-button>
      </header>
      <div class="mini-grid">
        <div v-for="item in overview" :key="item.label" class="mini">
          <span>{{ item.label }}</span>
          <b>{{ item.value }}</b>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.dash {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

/* ===== KPI ===== */
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 200px), 1fr));
  gap: 12px;
}

.kpi {
  position: relative;
  overflow: hidden;
  padding: 16px 18px 14px 20px;
  background: var(--surface);
  border: 1px solid var(--border-soft);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

.kpi::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
  background: var(--brand);
}

.kpi.green::before { background: var(--hit); }
.kpi.blue::before { background: #2b7cd9; }
.kpi.gold::before { background: var(--gold); }

.kpi.link {
  display: block;
  transition: border-color 0.18s;
}

.kpi.link:hover {
  border-color: #dcd0c7;
}

.kpi-label {
  margin: 0 0 10px;
  font-size: 12.5px;
  color: var(--text-sub);
}

.kpi-value {
  margin: 0;
  font-family: ui-monospace, monospace;
  font-size: 26px;
  font-weight: 600;
  letter-spacing: -0.02em;
  line-height: 1.2;
}

.kpi-sub,
.kpi-delta {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--text-sub);
}

.kpi-delta.up {
  color: var(--hit);
}

.kpi-delta.down {
  color: var(--brand);
}

.kpi-delta.flat {
  color: var(--text-sub);
}

/* ===== 面板 ===== */
.panel {
  padding: 16px 20px 18px;
  background: var(--surface);
  border: 1px solid var(--border-soft);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.panel-head h2 {
  margin: 0 0 3px;
  font-size: 15px;
  font-weight: 600;
}

.panel-sub {
  margin: 0;
  font-size: 12px;
  color: var(--text-sub);
}

.panel-link {
  font-size: 12px;
  color: var(--text-sub);
}

.panel-link:hover {
  color: var(--brand);
}

.chart-wrap {
  min-height: 250px;
}

/* ===== 两栏 ===== */
.two-col {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(0, 1fr);
  gap: 14px;
}

/* ===== 条形 ===== */
.bar-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.bar-row {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12.5px;
}

.bar-name {
  flex: 0 0 62px;
  color: var(--text-sub);
}

.bar-track {
  flex: 1;
  height: 8px;
  border-radius: 4px;
  background: var(--surface-soft);
  overflow: hidden;
}

.bar-track i {
  display: block;
  height: 100%;
  border-radius: 4px;
}

.bar-value {
  flex: 0 0 74px;
  text-align: right;
  font-family: ui-monospace, monospace;
  color: var(--text-main);
}

.bar-value em {
  margin-left: 6px;
  font-style: normal;
  font-size: 11px;
  color: var(--text-sub);
}

.sub-block {
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px solid var(--border-soft);
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.sub-title {
  margin: 0 0 2px;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--text-sub);
}

/* ===== 待办 ===== */
.todo-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.todo-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid var(--border-soft);
  border-radius: 10px;
  transition: border-color 0.18s, background 0.18s;
}

.todo-item:hover {
  border-color: #dcd0c7;
  background: var(--surface-soft);
}

.todo-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #d5ccc4;
}

.todo-dot.active {
  background: var(--brand);
}

.todo-text {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.todo-text b {
  font-size: 13px;
  font-weight: 500;
}

.todo-text em {
  font-style: normal;
  font-size: 11px;
  color: var(--text-sub);
}

.todo-count {
  font-family: ui-monospace, monospace;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-sub);
}

.todo-count.active {
  color: var(--brand);
}

/* ===== 概览 ===== */
.mini-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 140px), 1fr));
  gap: 10px;
}

.mini {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 14px;
  border-radius: 10px;
  background: var(--surface-soft);
}

.mini span {
  font-size: 12px;
  color: var(--text-sub);
}

.mini b {
  font-family: ui-monospace, monospace;
  font-size: 17px;
  font-weight: 600;
}

@media (max-width: 1100px) {
  .two-col {
    grid-template-columns: 1fr;
  }
}
</style>
