<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { lotteryApi } from '@/api/lottery'
import { randomApi, type HistorySummary, type RandomMethodVO, type RandomQuota, type RandomResult } from '@/api/random'
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

const currentType = computed(() => types.value.find((t) => t.code === typeCode.value))
const currentMethod = computed(() => methods.value.find((m) => m.code === method.value))
const isSumTarget = computed(() => method.value === 'SUM_TARGET')

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
    })
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
  const draft = {
    typeCode: result.value.typeCode,
    methodName: set.methodName,
    items: set.items,
    title: `${result.value.typeName}随机参考：${set.methodName}策略生成`,
    summary: `本组号码由「${set.methodName}」算法随机生成，主号 ${main}，和值 ${set.metrics.sum}，跨度 ${set.metrics.span}，奇偶比 ${set.metrics.oddEven}，仅供参考。`,
  }
  sessionStorage.setItem('ttdemo.randomDraft', JSON.stringify(draft))
  ElMessage.success('已带入发布页，请补充正文后提交')
  router.push('/publish')
}

watch(typeCode, async () => {
  sumMin.value = undefined
  sumMax.value = undefined
  showHistory.value = false
  result.value = null
  await Promise.all([loadMethods(), loadHistory()])
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
  await Promise.all([loadMethods(), loadHistory(), loadQuota()])
})
</script>

<template>
  <div class="page">
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
            <div v-if="result.sets.length > 1" class="set-index">第 {{ index + 1 }} 注</div>

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
            </div>

            <p class="reason">{{ set.reason }}</p>

            <details class="items">
              <summary>查看可发布的结构化预测项（{{ set.items.length }} 项）</summary>
              <ul>
                <li v-for="(item, i) in set.items" :key="i">
                  <el-tag size="small" effect="plain">{{ item.itemType }}</el-tag>
                  <span class="item-value">{{ item.itemValue }}</span>
                  <span class="item-label">{{ item.itemLabel }}</span>
                  <el-tag v-if="item.isFree" size="small" type="success">免费</el-tag>
                </li>
              </ul>
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
.page { max-width: 1100px; margin: 0 auto; padding: 24px 16px 64px; }
.head { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; margin-bottom: 16px; }
.head h1 { margin: 0 0 6px; font-size: 22px; }
.sub { margin: 0; font-size: 13px; color: var(--el-text-color-secondary); max-width: 640px; line-height: 1.6; }
.layout { display: grid; grid-template-columns: 320px minmax(0, 1fr); gap: 16px; align-items: start; }
.methods { background: #fff; border: 1px solid var(--el-border-color-lighter); border-radius: 12px; padding: 14px; }
.panel-title { font-size: 13px; font-weight: 600; margin-bottom: 10px; }

.quota-bar { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-bottom: 14px; padding: 10px 14px; background: #fff; border: 1px solid var(--el-border-color-lighter); border-radius: 10px; }
.quota-text { font-size: 12px; color: var(--el-text-color-regular); }
.quota-text.on { color: #d9982b; font-weight: 600; }
.almanac { margin-left: auto; font-size: 11px; color: var(--el-text-color-placeholder); letter-spacing: .5px; }

.method-group { margin-bottom: 6px; }
.group-head { display: flex; align-items: baseline; gap: 8px; margin: 10px 0 6px; }
.group-label { font-size: 12px; font-weight: 600; padding-left: 7px; border-left: 3px solid var(--el-border-color); }
.group-label.basic { border-left-color: #22b07d; }
.group-label.stats { border-left-color: #3b6ef6; }
.group-label.mystic { border-left-color: #a05cf0; }
.group-sub { font-size: 10px; color: var(--el-text-color-placeholder); }

/* 方法胶囊：三列铺排，替代原先的长卡片 */
.chips { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 6px; }
.chip {
  position: relative; display: flex; align-items: center; justify-content: center; gap: 3px;
  padding: 7px 4px; font-size: 12px; line-height: 1.2; text-align: center;
  background: #fff; border: 1px solid var(--el-border-color-lighter); border-radius: 8px;
  cursor: pointer; transition: all .16s; white-space: nowrap; overflow: hidden;
}
.chip:hover { border-color: var(--el-color-primary-light-5); }
.chip.active { border-color: var(--el-color-primary); background: var(--el-color-primary-light-9); color: var(--el-color-primary); font-weight: 600; }
.chip.locked { opacity: .5; }
.chip-vip { font-size: 9px; color: #d9982b; font-weight: 700; }
.chip.active .chip-vip { color: var(--el-color-primary); }

.method-hint { margin: 12px 0 0; padding: 8px 10px; font-size: 11px; line-height: 1.6; color: var(--el-text-color-secondary); background: var(--el-fill-color-lighter); border-radius: 8px; }
.method-note { margin: 6px 0 0; font-size: 10px; color: var(--el-color-warning); }

/* 参数条：置于右侧结果区顶部，横向一排 */
.param-bar {
  display: flex; align-items: center; gap: 14px; flex-wrap: wrap;
  background: #fff; border: 1px solid var(--el-border-color-lighter); border-radius: 10px;
  padding: 10px 14px; margin-bottom: 12px;
}
.param { display: flex; align-items: center; gap: 6px; }
.param label { font-size: 12px; color: var(--el-text-color-regular); white-space: nowrap; }
.param :deep(.el-input-number) { width: 104px; }
.param-dash { font-size: 12px; color: var(--el-text-color-placeholder); }
.param-hint { font-size: 11px; color: var(--el-text-color-placeholder); }
.param-hint.warn { color: var(--el-color-danger); }
.run-btn { margin-left: auto; min-width: 118px; letter-spacing: 1px; }
.run-hint { margin: -4px 0 10px; font-size: 11px; color: #d9982b; }

.result { min-height: 420px; }
.empty-state { display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 380px; gap: 10px; background: #fff; border: 1px dashed var(--el-border-color); border-radius: 12px; }
.empty-title { margin: 0; font-size: 15px; font-weight: 600; color: var(--el-text-color-regular); }
.empty-sub { margin: 0; font-size: 12px; line-height: 1.9; color: var(--el-text-color-secondary); text-align: center; }
.result-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; gap: 12px; flex-wrap: wrap; }
.type-name { font-size: 15px; font-weight: 600; }
.ml { margin-left: 6px; }
.actions { display: flex; gap: 8px; }
.set-card {
  background: #fff; border: 1px solid var(--el-border-color-lighter); border-radius: 12px;
  padding: 16px 18px; margin-bottom: 12px;
}
.set-index { font-size: 12px; color: var(--el-text-color-secondary); margin-bottom: 8px; }
.group { display: flex; align-items: center; gap: 12px; margin-bottom: 10px; flex-wrap: wrap; }
.group-name { font-size: 12px; color: var(--el-text-color-secondary); width: 42px; flex: 0 0 auto; }
.balls { display: flex; gap: 8px; flex-wrap: wrap; }
.ball {
  width: 38px; height: 38px; border-radius: 50%; display: inline-flex;
  align-items: center; justify-content: center; font-size: 14px; font-weight: 600; color: #fff;
}
.ball.red { background: linear-gradient(135deg, #f56c6c, #e64a4a); }
.ball.blue { background: linear-gradient(135deg, #4a90e2, #2f6fd0); }
.metrics { display: flex; gap: 18px; flex-wrap: wrap; font-size: 12px; color: var(--el-text-color-secondary); margin: 10px 0 8px; }
.metrics b { color: var(--el-text-color-primary); font-size: 13px; }
.reason {
  margin: 0; font-size: 12px; line-height: 1.7; color: var(--el-text-color-regular);
  background: var(--el-fill-color-lighter); border-radius: 8px; padding: 8px 12px;
}
.items { margin-top: 10px; font-size: 12px; }
.items summary { cursor: pointer; color: var(--el-color-primary); }
.items ul { margin: 8px 0 0; padding-left: 16px; }
.items li { display: flex; gap: 8px; align-items: center; margin-bottom: 6px; flex-wrap: wrap; }
.item-value { font-family: ui-monospace, monospace; }
.item-label { color: var(--el-text-color-secondary); }
.data-note { margin-top: 12px; }
.source-line { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; font-size: 12px; margin-top: 4px; }
.his-table { margin-top: 8px; }
.disclaimer { font-size: 11px; color: var(--el-text-color-placeholder); line-height: 1.7; margin-top: 8px; }
@media (max-width: 860px) { .layout { grid-template-columns: 1fr; } }
</style>
