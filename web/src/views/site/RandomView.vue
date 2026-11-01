<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { lotteryApi } from '@/api/lottery'
import { pickApi } from '@/api/pick'
import {
  randomApi,
  type BetSpec,
  type HistorySummary,
  type RandomMethodVO,
  type RandomQuota,
  type RandomResult,
} from '@/api/random'
import { useAuthStore } from '@/stores/auth'
import type { LotteryType } from '@/types/lottery'
import { formatDateTime } from '@/utils/format'

const router = useRouter()
const auth = useAuthStore()

const types = ref<LotteryType[]>([])
const methods = ref<RandomMethodVO[]>([])
const quota = ref<RandomQuota | null>(null)
const result = ref<RandomResult | null>(null)
const history = ref<HistorySummary | null>(null)
const loading = ref(false)
const showHistory = ref(false)
const typeCode = ref('SSQ')
const method = ref('PURE_RANDOM')
const count = ref(1)
const historySize = ref(30)
const sumMin = ref<number | undefined>(undefined)
const sumMax = ref<number | undefined>(undefined)

/** 投注方式：单式 / 复式。复式按各组号码个数算注数与金额 */
const betMode = ref<'SINGLE' | 'MULTI'>('SINGLE')
const betSpec = ref<BetSpec | null>(null)
/** 与 betSpec.groups 一一对应的复式号码个数 */
const multiPicks = ref<number[]>([])

const currentType = computed(() => types.value.find((t) => t.code === typeCode.value))
const currentMethod = computed(() => methods.value.find((m) => m.code === method.value))
const isSumTarget = computed(() => method.value === 'SUM_TARGET')
const multiSupported = computed(() => !!betSpec.value?.supported)

/** 按 BASIC / STATS / MYSTIC 分组展示，顺序固定 */
const methodGroups = computed(() => {
  const order = ['BASIC', 'STATS', 'MYSTIC']
  const map = new Map<string, RandomMethodVO[]>()
  for (const m of methods.value) {
    if (!map.has(m.group)) map.set(m.group, [])
    map.get(m.group)!.push(m)
  }
  const groups: Array<{ key: string; name: string; subtitle: string; items: RandomMethodVO[] }> = []
  for (const key of order) {
    const rows = map.get(key)
    const first = rows?.[0]
    if (!rows || !first) continue
    groups.push({ key, name: first.groupName, subtitle: first.groupSubtitle, items: rows })
  }
  return groups
})

const quotaText = computed(() => {
  const q = quota.value
  if (!q) return ''
  if (!q.gradeEnabled) return '全部方式已开放'
  if (q.vip) return '会员 · 全部方式不限次'
  return `非会员 · 会员专享方式今日剩余 ${q.remainToday} / ${q.freeQuota} 次`
})

/** 选中会员方法且当前是非会员时，提前告知会消耗免费次数 */
const willConsumeQuota = computed(() => {
  const q = quota.value
  return Boolean(q && q.gradeEnabled && !q.vip && currentMethod.value?.vip && q.remainToday > 0)
})

/** 组合数 C(n,k)，与后端注数算法一致 */
function comb(n: number, k: number): number {
  if (n <= 0 || k < 0 || k > n) return 1
  const r = Math.min(k, n - k)
  let value = 1
  for (let i = 0; i < r; i += 1) value = (value * (n - i)) / (i + 1)
  return Math.round(value)
}

/** 生成前的注数/金额预览：复式注数 = 各组组合数相乘 */
const betPreview = computed(() => {
  const spec = betSpec.value
  if (!spec) return null
  const picks = spec.groups.map((g, i) =>
    betMode.value === 'MULTI' ? Math.min(g.max, Math.max(g.min, multiPicks.value[i] ?? g.standard)) : g.standard,
  )
  const betCount = spec.groups.reduce((acc, g, i) => acc * comb(picks[i] ?? g.standard, g.standard), 1)
  const multi = picks.some((p, i) => p > (spec.groups[i]?.standard ?? p))
  return {
    shape: picks.join('+'),
    betCount,
    amount: betCount * spec.unitPrice,
    multi,
    unitPrice: spec.unitPrice,
  }
})

async function loadBetSpec() {
  if (!typeCode.value) return
  try {
    const spec = await randomApi.betOptions(typeCode.value)
    betSpec.value = spec
    multiPicks.value = spec.groups.map((g) => g.defaultValue)
    if (!spec.supported) betMode.value = 'SINGLE'
  } catch {
    betSpec.value = null
    multiPicks.value = []
    betMode.value = 'SINGLE'
  }
}

async function loadMethods() {
  methods.value = await randomApi.methods(typeCode.value)
}

async function loadQuota() {
  try {
    quota.value = await randomApi.quota()
  } catch {
    quota.value = null
  }
}

async function generate() {
  if (!typeCode.value) return
  loading.value = true
  try {
    result.value = await randomApi.generate({
      typeCode: typeCode.value,
      method: method.value,
      count: count.value,
      historySize: historySize.value,
      sumMin: isSumTarget.value ? sumMin.value : undefined,
      sumMax: isSumTarget.value ? sumMax.value : undefined,
      betMode: betMode.value,
      multiMain: multiPicks.value[0],
      multiSub: multiPicks.value[1],
    })
    // 新结果重新生成，清掉上一轮的「已保存」标记
    savedIndexes.value = []
    // 新结果重新生成，清掉上一轮的「已保存」标记
    savedIndexes.value = []
    // 会员方法会消耗免费次数，生成成功后同步最新额度
    if (result.value?.sets?.[0]) {
      await Promise.all([loadQuota(), loadMethods()])
    }
  } catch {
    // 配额不足时刷新展示，让用户看到剩余次数
    await Promise.all([loadQuota(), loadMethods()])
  } finally {
    loading.value = false
  }
}

async function loadHistory() {
  if (!typeCode.value) return
  try {
    history.value = await randomApi.history(typeCode.value, 50)
  } catch {
    history.value = null
  }
}

function pickMethod(item: RandomMethodVO) {
  if (!item.available) {
    ElMessage.info('该彩种历史开奖数据不足，暂不可用')
    return
  }
  if (item.locked) {
    ElMessage.warning(
      auth.isLoggedIn
        ? '今日会员专享方式的免费次数已用完，开通会员可不限次使用'
        : '该方式为会员专享，登录后可每日免费试用',
    )
    router.push(auth.isLoggedIn ? '/rewards' : { name: 'login', query: { redirect: '/random' } })
    return
  }
  // 只切换方法，实际生成交给「开始预测」按钮，避免误点消耗免费次数
  method.value = item.code
}

function ballClass(groupKey: string) {
  return groupKey === 'BLUE' ? 'ball blue' : 'ball red'
}

/** 预测项类型中文名，与发布页下拉保持一致 */
const ITEM_TYPE_NAMES: Record<string, string> = {
  RED_SINGLE: '单式号码',
  RED_MULTI: '复式号码',
  BLUE_SINGLE: '蓝球/后区',
  DAN: '胆码',
  KILL: '杀号',
  SUM_RANGE: '和值区间',
  SPAN: '跨度',
  ODD_EVEN: '奇偶比',
  BIG_SMALL: '大小比',
}
const NUMBER_ITEM_TYPES = new Set(['RED_SINGLE', 'RED_MULTI', 'BLUE_SINGLE', 'DAN', 'KILL'])

function itemTypeName(type: string) {
  return ITEM_TYPE_NAMES[type] ?? type
}

/** 号码串用空格分隔更好读；形态类（和值/跨度/奇偶比）原样展示 */
function itemValueText(item: { itemType: string; itemValue: string }) {
  return NUMBER_ITEM_TYPES.has(item.itemType) ? item.itemValue.replace(/,/g, ' ') : item.itemValue
}

/** label 只在有额外信息时展示，避免和值「387-427 和值 387-427」这样重复两遍 */
function itemNote(item: { itemType: string; itemLabel: string }) {
  const label = item.itemLabel?.trim()
  if (!label || label === itemTypeName(item.itemType)) return ''
  return label
}

/** 把当前结果带入发布页 */
function useForPublish() {
  const set = result.value?.sets[0]
  if (!set || !result.value) return
  if (!auth.isLoggedIn) {
    ElMessage.warning('请先登录，再带入发布页')
    router.push({ name: 'login', query: { redirect: '/random' } })
    return
  }
  const main = set.groups[0]?.numbers.join(' ') ?? ''
  const multi = set.bet?.mode === 'MULTI'
  const draft = {
    typeCode: result.value.typeCode,
    methodName: set.methodName,
    items: set.items,
    betLabel: set.bet?.label ?? '',
    title: `${result.value.typeName}${multi ? '复式' : ''}随机参考：${set.methodName}生成`,
    summary: `本注由「${set.methodName}」生成${set.bet?.label ?? ''}，主号 ${main}，和值 ${set.metrics.sum}、跨度 ${set.metrics.span}，仅供参考。`,
  }
  sessionStorage.setItem('ttdemo.randomDraft', JSON.stringify(draft))
  ElMessage.success('已带入发布页，请补充正文后提交')
  router.push('/publish')
}

const savingIndex = ref(-1)
const savedIndexes = ref<number[]>([])
/** 与后端 PickServiceImpl.MAX_BETS 保持一致 */
const MAX_PICK_BETS = 5000

/** 把某一注号码存进「我的号码」，会员功能 */
async function saveToPicks(set: RandomResult['sets'][number], index: number) {
  if (!auth.isLoggedIn) {
    ElMessage.warning('请先登录')
    router.push({ name: 'login', query: { redirect: '/random' } })
    return
  }
  if (!quota.value?.vip) {
    ElMessage.warning('号码本与兑奖为会员专享，开通后即可保存')
    router.push('/my/picks')
    return
  }
  if (!result.value) return
  const main = set.groups[0]?.numbers ?? []
  const sub = set.groups[1]?.numbers ?? []
  // 复式号码池存进号码本后，兑奖会按 C(n,k) 拆注逐注判定，注数过多会被后端拒绝，这里先拦一道
  const bets = set.bet?.betCount ?? 1
  if (bets > MAX_PICK_BETS) {
    ElMessage.warning(`本注拆出 ${bets} 注，超过号码本上限 ${MAX_PICK_BETS} 注，请改选更少的号码`)
    return
  }
  savingIndex.value = index
  try {
    const saved = await pickApi.save({
      typeCode: result.value.typeCode,
      redNumbers: main.join(','),
      blueNumbers: sub.length ? sub.join(',') : undefined,
      remark: bets > 1 ? `${set.methodName}生成 · 复式 ${bets} 注` : `${set.methodName}生成`,
    })
    if (!savedIndexes.value.includes(index)) {
      savedIndexes.value = [...savedIndexes.value, index]
    }
    ElMessage.success(
      bets > 1
        ? `已保存复式 ${bets} 注到「我的号码」第 ${saved.issueNo ?? '—'} 期，开奖后自动兑奖`
        : `已保存到「我的号码」第 ${saved.issueNo ?? '—'} 期，开奖后自动兑奖`,
    )
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    savingIndex.value = -1
  }
}

watch(typeCode, async () => {
  sumMin.value = undefined
  sumMax.value = undefined
  showHistory.value = false
  result.value = null
  await Promise.all([loadMethods(), loadHistory(), loadBetSpec()])
  const available = methods.value.find((m) => m.code === method.value)?.available
  if (!available) {
    method.value = 'PURE_RANDOM'
  }
})

onMounted(async () => {
  const all = await lotteryApi.types()
  types.value = all.filter((t) => t.status === 1)
  const first = types.value[0]
  if (first) typeCode.value = first.code
  await Promise.all([loadMethods(), loadHistory(), loadQuota(), loadBetSpec()])
})
</script>

<template>
  <div class="page ui-page view-random">
    <header class="head">
      <div>
        <h1>随机预测生成器</h1>
        <p class="sub">
          15 种算法生成号码：基础随机永久免费，统计策略与玄学占卜为会员专享，非会员每日有免费试用次数。
        </p>
      </div>
      <el-select v-model="typeCode" style="width: 150px">
        <el-option v-for="t in types" :key="t.code" :label="t.name" :value="t.code" />
      </el-select>
    </header>

    <div class="quota-bar">
      <span class="quota-text" :class="{ on: quota?.vip }">{{ quotaText }}</span>
      <el-button
        v-if="quota && quota.gradeEnabled && !quota.vip"
        link
        type="primary"
        size="small"
        @click="router.push('/rewards')"
      >
        用积分开通会员 →
      </el-button>
      <span v-if="quota?.almanac" class="almanac">今日 {{ quota.almanac }}</span>
    </div>

    <div class="layout">
      <aside class="methods">
        <div class="panel-title">预测方法</div>
        <div v-for="g in methodGroups" :key="g.key" class="method-group">
          <div class="group-head">
            <span class="group-label" :class="g.key.toLowerCase()">{{ g.name }}</span>
            <span class="group-sub">{{ g.subtitle }}</span>
          </div>
          <div class="chips">
            <button
              v-for="m in g.items"
              :key="m.code"
              class="chip"
              :class="{ active: method === m.code, locked: m.locked }"
              :title="m.description"
              @click="pickMethod(m)"
            >
              <span>{{ m.name }}</span>
              <span v-if="m.vip" class="chip-vip">V</span>
            </button>
          </div>
        </div>

        <p v-if="currentMethod" class="method-hint">{{ currentMethod.description }}</p>
        <p v-if="currentMethod?.dataNote" class="method-note">{{ currentMethod.dataNote }}</p>
      </aside>

      <main v-loading="loading" class="result">
        <div class="param-bar">
          <div class="param">
            <label>投注方式</label>
            <el-radio-group v-model="betMode" size="small">
              <el-radio-button value="SINGLE">单式</el-radio-button>
              <el-radio-button value="MULTI" :disabled="!multiSupported">复式</el-radio-button>
            </el-radio-group>
          </div>
          <div class="param">
            <label>生成注数</label>
            <el-input-number v-model="count" :min="1" :max="5" size="small" />
          </div>
          <div class="param">
            <label>参考期数</label>
            <el-input-number v-model="historySize" :min="5" :max="100" :step="5" size="small" />
          </div>
          <template v-if="isSumTarget">
            <div class="param">
              <label>和值</label>
              <el-input-number v-model="sumMin" :min="0" :max="400" size="small" placeholder="自动" controls-position="right" />
              <span class="param-dash">—</span>
              <el-input-number v-model="sumMax" :min="0" :max="400" size="small" placeholder="自动" controls-position="right" />
            </div>
          </template>
          <span class="param-hint" :class="{ warn: (history?.total ?? 0) === 0 }">
            库中可用 {{ history?.total ?? 0 }} 期
            <template v-if="(history?.total ?? 0) < historySize">（超出部分按实际期数计算）</template>
          </span>
          <el-button type="primary" class="run-btn" :loading="loading" @click="generate">
            {{ result ? '重新预测' : '开始预测' }}
          </el-button>
        </div>
        <p v-if="willConsumeQuota" class="run-hint">
          本次将消耗 1 次免费试用，今日还剩 {{ quota?.remainToday }} 次
        </p>

        <div v-if="betMode === 'MULTI' && betSpec?.supported" class="multi-bar">
          <div v-for="(g, i) in betSpec.groups" :key="g.key" class="param">
            <label>{{ g.name }}</label>
            <el-input-number v-model="multiPicks[i]" :min="g.min" :max="g.max" size="small" />
            <span class="param-hint">单注 {{ g.standard }} 个</span>
          </div>
          <span v-if="betPreview" class="bet-preview">
            {{ betPreview.multi ? '复式' : '单式' }} {{ betPreview.shape }}
            <b>{{ betPreview.betCount }}</b> 注 · <b>¥{{ betPreview.amount }}</b>
            <em>（{{ betPreview.unitPrice }} 元/注）</em>
          </span>
          <span class="multi-note">{{ betSpec.note }}</span>
        </div>
        <p v-else-if="betSpec && !betSpec.supported" class="multi-note blocked">{{ betSpec.note }}</p>

        <div v-if="!result" class="empty-state">
          <p class="empty-title">还没有生成结果</p>
          <p class="empty-sub">
            在左侧点选一种预测方法，设置好参数后点击「开始预测」<br />
            生成后会展示号码、和值跨度指标与可直接发布的预测项
          </p>
        </div>
        <template v-else>
          <div class="result-head">
            <div>
              <span class="type-name">{{ result.typeName }}</span>
              <el-tag size="small" effect="plain" class="ml">{{ result.sets[0]?.methodName }}</el-tag>
              <el-tag size="small" :type="result.historySize === 0 ? 'warning' : 'info'" effect="plain" class="ml">
                使用最近 {{ result.historySize }} 期
                <template v-if="result.requestedHistorySize > result.historySize">
                  （设置 {{ result.requestedHistorySize }} 期，仅 {{ result.historySize }} 期可用）
                </template>
              </el-tag>
            </div>
            <div class="actions">
              <el-button size="small" @click="generate">换一批</el-button>
              <el-button size="small" type="primary" @click="useForPublish">带入发布页</el-button>
            </div>
          </div>

          <div v-for="(set, index) in result.sets" :key="index" class="set-card">
            <div class="set-head">
              <span class="set-title">
                {{ result.sets.length > 1 ? `第 ${index + 1} 注` : '本注号码' }}
              </span>
              <el-button
                size="default"
                class="save-btn"
                :type="savedIndexes.includes(index) ? 'success' : 'primary'"
                :plain="!quota?.vip || savedIndexes.includes(index)"
                :loading="savingIndex === index"
                @click="saveToPicks(set, index)"
              >
                <template v-if="savedIndexes.includes(index)">✓ 已保存到号码本</template>
                <template v-else-if="quota?.vip">＋ 保存到我的号码</template>
                <template v-else>＋ 开通会员后保存</template>
              </el-button>
            </div>

            <div class="bet-bar" :class="{ multi: set.bet?.mode === 'MULTI' }">
              <span class="bet-mode">{{ set.bet?.modeName ?? '单式' }}</span>
              <span class="bet-shape">{{ set.groups.map((g) => g.numbers.length).join('+') }}</span>
              <span class="bet-count"><b>{{ set.bet?.betCount ?? 1 }}</b> 注</span>
              <span class="bet-amount">¥{{ set.bet?.amount ?? 0 }}</span>
              <span class="bet-formula">{{ set.bet?.formula }}</span>
            </div>

            <div v-for="g in set.groups" :key="g.key" class="group">
              <span class="group-name">{{ g.name }}</span>
              <div class="balls">
                <span v-for="n in g.numbers" :key="n" :class="ballClass(g.key)">{{ n }}</span>
              </div>
            </div>

            <div class="metrics">
              <span>和值 <b>{{ set.metrics.sum }}</b></span>
              <span>跨度 <b>{{ set.metrics.span }}</b></span>
              <span>奇偶 <b>{{ set.metrics.oddEven }}</b></span>
              <span>大小 <b>{{ set.metrics.bigSmall }}</b></span>
              <span v-if="set.bet?.mode === 'MULTI'" class="metrics-note">号码池整体指标，非单注口径</span>
            </div>

            <p class="reason">{{ set.reason }}</p>

            <details class="items">
              <summary>
                查看可发布的结构化预测项（{{ set.items.length }} 项）
                <span class="items-hint">可直接带入发布页</span>
              </summary>
              <div class="item-list">
                <div v-for="(item, i) in set.items" :key="i" class="item-row">
                  <span class="item-type">{{ itemTypeName(item.itemType) }}</span>
                  <span class="item-value">{{ itemValueText(item) }}</span>
                  <span v-if="itemNote(item)" class="item-label">{{ itemNote(item) }}</span>
                  <el-tag v-if="item.isFree" size="small" type="success" effect="plain">免费</el-tag>
                </div>
              </div>
            </details>
          </div>

          <el-alert
            :type="result.historySize === 0 ? 'warning' : 'info'"
            :closable="false"
            show-icon
            class="data-note"
            :title="result.dataNote"
          >
            <div class="source-line">
              <span>数据来源：平台开奖库</span>
              <el-tag size="small" type="info" effect="plain">自动同步 {{ result.autoSyncCount }} 期</el-tag>
              <el-tag size="small" type="warning" effect="plain">人工录入 {{ result.manualCount }} 期</el-tag>
              <el-button
                v-if="result.historyRows.length"
                text
                size="small"
                type="primary"
                @click="showHistory = !showHistory"
              >
                {{ showHistory ? '收起明细' : '核对期次明细' }}
              </el-button>
            </div>
            <el-table
              v-if="showHistory"
              :data="result.historyRows"
              size="small"
              max-height="240"
              class="his-table"
            >
              <el-table-column prop="issueNo" label="期号" width="110" />
              <el-table-column label="开奖时间" width="160">
                <template #default="scope">{{ formatDateTime(scope.row.drawTime) }}</template>
              </el-table-column>
              <el-table-column label="开奖号码" min-width="200">
                <template #default="scope">
                  {{ scope.row.redNumbers || '—' }}
                  <span v-if="scope.row.blueNumbers"> + {{ scope.row.blueNumbers }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="sourceName" label="来源" width="100" />
            </el-table>
          </el-alert>

          <p class="disclaimer">{{ result.disclaimer }}</p>
        </template>
      </main>
    </div>
  </div>
</template>

<style scoped>
.head { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; margin-bottom: 16px; }
.head h1 { margin: 0 0 6px; font-size: 22px; }
.sub { margin: 0; font-size: 13px; color: var(--text-sub); max-width: 640px; line-height: 1.6; }
.layout { display: grid; grid-template-columns: 320px minmax(0, 1fr); gap: 16px; align-items: start; }
.methods { background: #fff; border: 1px solid var(--border-soft); border-radius: 12px; padding: 14px; }
.panel-title { font-size: 13px; font-weight: 600; margin-bottom: 10px; }

.quota-bar { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-bottom: 14px; padding: 10px 14px; background: #fff; border: 1px solid var(--border-soft); border-radius: 10px; }
.quota-text { font-size: 12px; color: var(--text-main); }
.quota-text.on { color: var(--gold); font-weight: 600; }
.almanac { margin-left: auto; font-size: 11px; color: var(--text-sub); letter-spacing: .5px; }

.method-group { margin-bottom: 6px; }
.group-head { display: flex; align-items: baseline; gap: 8px; margin: 10px 0 6px; }
.group-label { font-size: 12px; font-weight: 600; padding-left: 7px; border-left: 3px solid var(--el-border-color); }
.group-label.basic { border-left-color: #22b07d; }
.group-label.stats { border-left-color: #3b6ef6; }
.group-label.mystic { border-left-color: #a05cf0; }
.group-sub { font-size: 10px; color: var(--text-sub); }

/* 方法胶囊：三列铺排，替代原先的长卡片 */
.chips { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 6px; }
.chip {
  position: relative; display: flex; align-items: center; justify-content: center; gap: 3px;
  padding: 7px 4px; font-size: 12px; line-height: 1.2; text-align: center;
  background: #fff; border: 1px solid var(--border-soft); border-radius: 8px;
  cursor: pointer; transition: all .16s; white-space: nowrap; overflow: hidden;
}
.chip:hover { border-color: var(--el-color-primary-light-5); }
.chip.active { border-color: var(--el-color-primary); background: var(--el-color-primary-light-9); color: var(--el-color-primary); font-weight: 600; }
.chip.locked { opacity: .5; }
.chip-vip { font-size: 9px; color: var(--gold); font-weight: 700; }
.chip.active .chip-vip { color: var(--el-color-primary); }

.method-hint { margin: 12px 0 0; padding: 8px 10px; font-size: 11px; line-height: 1.6; color: var(--text-sub); background: var(--el-fill-color-lighter); border-radius: 8px; }
.method-note { margin: 6px 0 0; font-size: 10px; color: var(--el-color-warning); }

/* 参数条：置于右侧结果区顶部，横向一排 */
.param-bar {
  display: flex; align-items: center; gap: 14px; flex-wrap: wrap;
  background: #fff; border: 1px solid var(--border-soft); border-radius: 10px;
  padding: 10px 14px; margin-bottom: 12px;
}
.param { display: flex; align-items: center; gap: 6px; }
.param label { font-size: 12px; color: var(--text-main); white-space: nowrap; }
.param :deep(.el-input-number) { width: 104px; }
.param-dash { font-size: 12px; color: var(--text-sub); }
.param-hint { font-size: 11px; color: var(--text-sub); }
.param-hint.warn { color: var(--el-color-danger); }
.run-btn { margin-left: auto; min-width: 118px; letter-spacing: 1px; }
.run-hint { margin: -4px 0 10px; font-size: 11px; color: var(--gold); }

/* 复式：每组号码个数 + 注数金额预览 */
.multi-bar {
  display: flex; align-items: center; gap: 14px; flex-wrap: wrap;
  background: #fffaf4; border: 1px solid #f6ddc3; border-radius: 10px;
  padding: 10px 14px; margin: -2px 0 12px;
}
.multi-bar .param :deep(.el-input-number) { width: 92px; }
.bet-preview { margin-left: auto; font-size: 12px; color: var(--text-main); }
.bet-preview b { color: #d92b2b; font-size: 14px; font-variant-numeric: tabular-nums; }
.bet-preview em { font-style: normal; font-size: 11px; color: var(--text-sub); }
.multi-note { flex-basis: 100%; font-size: 11px; line-height: 1.6; color: var(--text-sub); }
.multi-note.blocked { margin: -2px 0 12px; color: var(--el-color-warning); }

/* 每注的投注信息条 */
.bet-bar {
  display: flex; align-items: center; gap: 10px; flex-wrap: wrap;
  margin-bottom: 12px; padding: 8px 12px; border-radius: 9px;
  background: var(--el-fill-color-lighter); font-size: 12px; color: var(--text-sub);
}
.bet-bar.multi { background: #fff7ee; border: 1px solid #f7dfc6; }
.bet-mode {
  padding: 1px 7px; border-radius: 5px; background: #fff; border: 1px solid var(--border-soft);
  font-weight: 600; color: var(--text-main);
}
.bet-bar.multi .bet-mode { color: #c2410c; border-color: #f5c99b; }
.bet-shape { font-weight: 600; color: var(--text-main); font-variant-numeric: tabular-nums; }
.bet-count b { color: var(--text-main); }
.bet-amount { font-size: 14px; font-weight: 700; color: #d92b2b; font-variant-numeric: tabular-nums; }
.bet-formula { font-size: 11px; color: var(--text-sub); }

.result { min-height: 420px; }
.empty-state { display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 380px; gap: 10px; background: #fff; border: 1px dashed var(--el-border-color); border-radius: 12px; }
.empty-title { margin: 0; font-size: 15px; font-weight: 600; color: var(--text-main); }
.empty-sub { margin: 0; font-size: 12px; line-height: 1.9; color: var(--text-sub); text-align: center; }
.result-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; gap: 12px; flex-wrap: wrap; }
.type-name { font-size: 15px; font-weight: 600; }
.ml { margin-left: 6px; }
.actions { display: flex; gap: 8px; }
.set-card {
  background: #fff; border: 1px solid var(--border-soft); border-radius: 12px;
  padding: 16px 18px; margin-bottom: 12px;
}
.group { display: flex; align-items: center; gap: 12px; margin-bottom: 10px; flex-wrap: wrap; }
.group-name { font-size: 12px; color: var(--text-sub); width: 42px; flex: 0 0 auto; }
.balls { display: flex; gap: 8px; flex-wrap: wrap; }
.ball {
  width: 38px; height: 38px; border-radius: 50%; display: inline-flex;
  align-items: center; justify-content: center; font-size: 14px; font-weight: 600; color: #fff;
}
.ball.red { background: linear-gradient(135deg, #f56c6c, #e64a4a); }
.ball.blue { background: linear-gradient(135deg, #4a90e2, #2f6fd0); }
.metrics { display: flex; gap: 18px; flex-wrap: wrap; font-size: 12px; color: var(--text-sub); margin: 10px 0 8px; }
.metrics b { color: var(--el-text-color-primary); font-size: 13px; }
.metrics-note { color: var(--text-sub); opacity: .8; font-size: 11px; }
.reason {
  margin: 0; font-size: 12px; line-height: 1.7; color: var(--text-main);
  background: var(--el-fill-color-lighter); border-radius: 8px; padding: 8px 12px;
}
.items { margin-top: 10px; font-size: 12px; }
.items summary { cursor: pointer; color: var(--el-color-primary); }
.items-hint { margin-left: 8px; color: var(--text-sub); font-size: 11px; }
.item-list { margin-top: 8px; display: flex; flex-direction: column; gap: 6px; }
.item-row {
  display: flex; align-items: center; gap: 10px; flex-wrap: wrap;
  padding: 7px 10px; border-radius: 8px; background: var(--el-fill-color-lighter);
}
.item-type { flex: 0 0 68px; color: var(--text-sub); }
.item-value {
  flex: 1 1 auto; min-width: 0; font-family: ui-monospace, monospace; line-height: 1.6;
  color: var(--el-text-color-primary); word-break: break-all;
}
.item-label { color: var(--text-sub); }
.set-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 12px; }
.set-title { font-size: 13px; font-weight: 600; color: var(--text-main); }
.save-btn { font-weight: 600; letter-spacing: 0.5px; }
.save-btn.el-button--primary:not(.is-plain) { box-shadow: 0 4px 12px rgba(217, 43, 43, 0.25); }
.data-note { margin-top: 12px; }
.source-line { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; font-size: 12px; margin-top: 4px; }
.his-table { margin-top: 8px; }
.disclaimer { font-size: 11px; color: var(--text-sub); line-height: 1.7; margin-top: 8px; }
@media (max-width: 860px) { .layout { grid-template-columns: 1fr; } }
</style>
