<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { lotteryApi } from '@/api/lottery'
import { predictionApi, type PredictionItemPayload } from '@/api/prediction'
import type { Issue, LotteryType, PriceQuote } from '@/types/lottery'
import { useAuthStore } from '@/stores/auth'
import BallPicker from '@/components/lottery/BallPicker.vue'
import PickBalls from '@/components/lottery/PickBalls.vue'
import {
  groupForItem,
  isDigitType,
  isNumberItem,
  maxPicksForItem,
  parsePlayGroups,
  toDigits,
  toNumbers,
} from '@/utils/lottery'

const router = useRouter()
const auth = useAuthStore()

const types = ref<LotteryType[]>([])
const issues = ref<Issue[]>([])
const quote = ref<PriceQuote | null>(null)
const issuesLoading = ref(false)
const submitting = ref(false)
/** 从随机预测带入的复式投注信息（如 复式 8+2 · 56 注 · ¥112） */
const draftBetLabel = ref('')

const form = reactive({
  typeCode: '',
  issueNo: '',
  title: '',
  playType: 'SINGLE',
  summary: '',
  contentHtml: '',
  pricePoints: 0,
})

/**
 * 默认 3 项 1 免费（33%）：后端要求免费项 ≥1 且占比 ≤40%，
 * 2 项 1 免费正好 50% 属于必然违规，页面一进来就报错。
 */
const items = ref<PredictionItemPayload[]>([
  { itemType: 'RED_SINGLE', itemValue: '', itemLabel: '红球6码', isFree: true },
  { itemType: 'BLUE_SINGLE', itemValue: '', itemLabel: '蓝球', isFree: false },
  { itemType: 'SUM_RANGE', itemValue: '', itemLabel: '和值区间', isFree: false },
])

const playTypes = [
  { value: 'SINGLE', label: '单式推荐' },
  { value: 'MULTI', label: '复式推荐' },
  { value: 'DAN', label: '胆码推荐' },
  { value: 'KILL', label: '杀号推荐' },
  { value: 'FORM', label: '形态预测' },
  { value: 'AI_PACK', label: '综合方案包' },
]

const itemTypes = [
  { value: 'RED_SINGLE', label: '单式红球组', desc: '与开奖红球完全一致才命中' },
  { value: 'RED_MULTI', label: '复式红球池', desc: '命中率 ≥50% 算中' },
  { value: 'DAN', label: '胆码', desc: '交集 / 预测数 ≥50% 算中' },
  { value: 'KILL', label: '杀号', desc: '与开奖无交集算中' },
  { value: 'BLUE_SINGLE', label: '蓝球/后区', desc: '相等算中' },
  { value: 'SUM_RANGE', label: '和值区间', desc: '如 90-115' },
  { value: 'SPAN', label: '跨度', desc: '如 18' },
  { value: 'ODD_EVEN', label: '奇偶比', desc: '如 4:2' },
  { value: 'BIG_SMALL', label: '大小比', desc: '如 3:3' },
]

const isCreator = computed(() => auth.user?.creatorStatus === 2)

/** 当前彩种的号码池（来自 playConfig），选号器按它渲染球 */
const currentType = computed(() => types.value.find((t) => t.code === form.typeCode))
const groups = computed(() => parsePlayGroups(currentType.value?.playConfig))

function groupOf(itemType: string) {
  return groupForItem(groups.value, itemType)
}

function maxOf(itemType: string) {
  return maxPicksForItem(groupOf(itemType), itemType)
}

/** 展开中的选号器（同时只展开一个，避免三个 33 球的网格把页面撑爆） */
const expandedIndex = ref<number | null>(null)

function togglePicker(index: number) {
  expandedIndex.value = expandedIndex.value === index ? null : index
}

const freeCount = computed(() => items.value.filter((i) => i.isFree).length)
const freePercent = computed(() => (items.value.length ? Math.round((freeCount.value / items.value.length) * 100) : 0))
const freeRatioInvalid = computed(() => items.value.length > 0 && freeCount.value * 5 > items.value.length * 2)
const freeLimit = computed(() => Math.floor((items.value.length * 2) / 5))
const priceRangeText = computed(() => (quote.value ? `${quote.value.minPrice} - ${quote.value.maxPrice} 积分` : '—'))
const scoreText = computed(() =>
  quote.value ? `${quote.value.accuracyScore?.toFixed(1)}（${quote.value.sampleSize} 期样本）` : '—',
)

const titleCount = computed(() => form.title.trim().length)
const summaryCount = computed(() => form.summary.trim().length)
const contentCount = computed(() => form.contentHtml.replace(/<[^>]+>/g, '').trim().length)
const contentTooShort = computed(() => contentCount.value > 0 && contentCount.value < 100)

function remainText(seconds?: number) {
  if (!seconds || seconds <= 0) return '已停售'
  const day = Math.floor(seconds / 86400)
  const hour = Math.floor((seconds % 86400) / 3600)
  const minute = Math.floor((seconds % 3600) / 60)
  if (day > 0) return `${day} 天 ${hour} 小时`
  if (hour > 0) return minute > 0 ? `${hour} 小时 ${minute} 分` : `${hour} 小时`
  return `${minute} 分钟`
}

function issueLabel(issue: Issue) {
  return `${issue.issueNo} · 距停售 ${remainText(issue.remainSeconds)}`
}

function valueHint(type: string) {
  switch (type) {
    case 'SUM_RANGE':
      return '如 90-115'
    case 'SPAN':
      return '如 18'
    case 'ODD_EVEN':
    case 'BIG_SMALL':
      return '如 4:2'
    default:
      return '填写取值'
  }
}

/** 号码型预测项的当前号码，按彩种决定是「逐位」还是「号码集合」 */
function itemNumbers(item: PredictionItemPayload): number[] {
  if (!item.itemValue) return []
  return isDigitType(form.typeCode) ? toDigits(item.itemValue) : toNumbers(item.itemValue)
}

async function loadIssues() {
  if (!form.typeCode) {
    issues.value = []
    return
  }
  issuesLoading.value = true
  try {
    issues.value = await lotteryApi.current(form.typeCode)
    form.issueNo = issues.value[0]?.issueNo || ''
  } catch {
    issues.value = []
    form.issueNo = ''
  } finally {
    issuesLoading.value = false
  }
}

async function loadQuote() {
  if (!form.typeCode || !form.playType) {
    quote.value = null
    return
  }
  try {
    const result = await predictionApi.priceQuote(form.typeCode, form.playType)
    quote.value = result
    // 首次进入或当前价已越界时回到建议价，用户手动下浮过的值不覆盖
    if (!form.pricePoints || form.pricePoints < result.minPrice || form.pricePoints > result.maxPrice) {
      form.pricePoints = result.suggestPrice
    }
  } catch {
    quote.value = null
  }
}

watch(
  () => form.typeCode,
  async () => {
    sanitizeItems()
    expandedIndex.value = null
    await loadIssues()
    await loadQuote()
  },
)

watch(() => form.playType, loadQuote)

/**
 * 换彩种后号码池跟着变（33→35、双色球→3D），旧号码可能越界或数量超限，
 * 与其让用户提交后被后端打回，不如就地清掉。
 */
function sanitizeItems() {
  const singleGroup = groups.value.length < 2
  for (const item of items.value) {
    if (item.itemType === 'BLUE_SINGLE' && singleGroup) {
      item.itemType = 'RED_SINGLE'
      item.itemValue = ''
      // 默认文案是「蓝球」，换成红球组后留着会误导
      if (!item.itemLabel || item.itemLabel === '蓝球') item.itemLabel = ''
      continue
    }
    if (!isNumberItem(item.itemType) || !item.itemValue) continue
    const group = groupOf(item.itemType)
    const numbers = itemNumbers(item)
    const max = maxOf(item.itemType)
    const valid = numbers.length <= max && numbers.every((n) => n >= group.min && n <= group.max)
    if (!valid) item.itemValue = ''
  }
}

/** 切换预测项类型：号码池或数量上限变了就清空，避免残留越界号码 */
function onTypeChange(index: number, previous: string) {
  const item = items.value[index]
  if (!item) return
  if (previous === item.itemType) return
  const wasNumber = isNumberItem(previous)
  const isNumber = isNumberItem(item.itemType)
  if (wasNumber !== isNumber) {
    item.itemValue = ''
  } else if (isNumber && groupOf(previous).key !== groupOf(item.itemType).key) {
    item.itemValue = ''
  } else if (isNumber && item.itemValue) {
    const max = maxOf(item.itemType)
    const numbers = itemNumbers(item)
    if (numbers.length > max) item.itemValue = numbers.slice(0, max).join(',')
  }
  if (isNumber) expandedIndex.value = index
}

function addItem() {
  const type = isDigitType(form.typeCode) ? 'RED_SINGLE' : 'RED_MULTI'
  items.value.push({ itemType: type, itemValue: '', itemLabel: '', isFree: false })
  expandedIndex.value = items.value.length - 1
}

function removeItem(index: number) {
  if (items.value.length <= 1) {
    ElMessage.warning('至少保留 1 个预测项')
    return
  }
  items.value.splice(index, 1)
  if (expandedIndex.value === index) expandedIndex.value = null
  else if (expandedIndex.value !== null && expandedIndex.value > index) expandedIndex.value -= 1
}

/** 号码型预测项本地校验：越界、数量、重复都先在前端拦下 */
function validateNumbers(): string | null {
  for (const [index, item] of items.value.entries()) {
    if (!isNumberItem(item.itemType)) continue
    const group = groupOf(item.itemType)
    const max = maxOf(item.itemType)
    const numbers = itemNumbers(item)
    const label = `第 ${index + 1} 项`
    if (!numbers.length) return `${label}还没选号`
    if (numbers.length > max) return `${label}最多 ${max} 个号（当前 ${numbers.length} 个）`
    if (isDigitType(form.typeCode) && numbers.length !== max) {
      return `${label}需要 ${max} 位号码（当前 ${numbers.length} 位）`
    }
    if (numbers.some((n) => n < group.min || n > group.max)) {
      return `${label}号码需在 ${group.min}-${group.max} 之间`
    }
    if (!isDigitType(form.typeCode) && new Set(numbers).size !== numbers.length) {
      return `${label}号码不可重复`
    }
  }
  return null
}

function validate(): string | null {
  if (!form.typeCode) return '请选择彩种'
  if (!form.issueNo) return '请选择期次'
  if (titleCount.value < 8 || titleCount.value > 40) return '标题需 8-40 字'
  if (summaryCount.value < 20 || summaryCount.value > 100) return '摘要需 20-100 字'
  if (contentCount.value < 100) return `正文需至少 100 字（当前 ${contentCount.value} 字，不含标签）`
  if (!quote.value) return '定价信息未就绪，请重新选择彩种与玩法'
  if (items.value.length === 0) return '至少 1 个预测项'
  if (items.value.length > 20) return '预测项最多 20 个'
  if (freeCount.value === 0) return '至少 1 个免费可见的预测项'
  if (freeRatioInvalid.value) return `免费项占比不能超过 40%（${items.value.length} 项最多 ${freeLimit.value} 项免费）`
  if (items.value.some((i) => !i.itemValue.trim())) return '存在未填写的预测项'
  const numberError = validateNumbers()
  if (numberError) return numberError
  if (form.pricePoints < quote.value.minPrice || form.pricePoints > quote.value.maxPrice) {
    return `售价需在 ${priceRangeText.value} 之间`
  }
  return null
}

async function save(submitAfter: boolean) {
  const error = validate()
  if (error) {
    ElMessage.warning(error)
    return
  }
  if (submitAfter) {
    try {
      await ElMessageBox.confirm(
        '提交后进入审核，审核通过后不可修改；停售前可撤回但会扣减信誉分。确认提交？',
        '提交审核',
        { confirmButtonText: '确认提交', cancelButtonText: '取消' },
      )
    } catch {
      return
    }
  }
  submitting.value = true
  try {
    const payload = {
      typeCode: form.typeCode,
      issueNo: form.issueNo,
      title: form.title.trim(),
      playType: form.playType,
      summary: form.summary.trim(),
      contentHtml: `<p>${form.contentHtml.replace(/\n/g, '</p><p>')}</p>`,
      pricePoints: form.pricePoints,
      items: items.value.map((item) => ({ ...item, itemValue: item.itemValue.trim() })),
    }
    const created = await predictionApi.create(payload)
    if (submitAfter) {
      await predictionApi.submit(created.id)
      ElMessage.success('已提交审核，预计 10 分钟内完成')
    } else {
      ElMessage.success('草稿已保存，可在创作者看板继续编辑')
    }
    router.push('/creator/dashboard')
  } catch {
    // 具体错误由 http 拦截器统一提示
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  try {
    const all = await lotteryApi.types()
    types.value = all.filter((t) => t.status === 1)
  } catch {
    types.value = []
  }

  // 从「随机预测生成器」带入
  const raw = sessionStorage.getItem('ttdemo.randomDraft')
  if (raw) {
    sessionStorage.removeItem('ttdemo.randomDraft')
    try {
      const draft = JSON.parse(raw) as {
        typeCode?: string
        methodName?: string
        title?: string
        summary?: string
        betLabel?: string
        items?: PredictionItemPayload[]
      }
      if (draft.typeCode && types.value.some((t) => t.code === draft.typeCode)) {
        form.typeCode = draft.typeCode
      }
      form.title = draft.title || form.title
      form.summary = draft.summary || form.summary
      draftBetLabel.value = draft.betLabel || ''
      if (Array.isArray(draft.items) && draft.items.length) {
        items.value = draft.items
      }
      ElMessage.info(
        `已带入「${draft.methodName || '随机'}」生成的号码${draft.betLabel ? `（${draft.betLabel}）` : ''}，请补充付费正文后提交`,
      )
    } catch {
      // 解析失败则按普通流程初始化
    }
  }

  if (!form.typeCode) {
    const first = types.value[0]
    if (first) form.typeCode = first.code
  }
})
</script>

<template>
  <div class="page ui-page view-publish">
    <header class="head">
      <div>
        <h1>发布预测</h1>
        <p class="sub">预测项结构化填写，系统按开奖结果自动判定，人工不可改判</p>
      </div>
      <el-button plain @click="router.back()">返回</el-button>
    </header>

    <el-alert
      v-if="!isCreator"
      class="notice"
      type="warning"
      :closable="false"
      show-icon
      title="当前账号还不是认证创作者"
      description="发布预测需完成实名认证并通过创作者审核，否则提交会被拒绝。可先保存草稿。"
    />

    <div class="layout">
      <main class="main">
        <section class="panel">
          <div class="panel-head">
            <div>
              <h2>基础信息</h2>
              <p>彩种与期次决定开奖口径，提交后不可更改</p>
            </div>
          </div>

          <el-form label-position="top" class="form">
            <div class="row-2">
              <el-form-item label="彩种">
                <el-select v-model="form.typeCode" placeholder="选择彩种">
                  <el-option v-for="t in types" :key="t.code" :label="t.name" :value="t.code" />
                </el-select>
              </el-form-item>
              <el-form-item label="期次">
                <el-select
                  v-model="form.issueNo"
                  :placeholder="issuesLoading ? '加载中…' : '选择期次'"
                  :disabled="!issues.length"
                  :loading="issuesLoading"
                >
                  <el-option v-for="i in issues" :key="i.id" :label="issueLabel(i)" :value="i.issueNo" />
                </el-select>
                <p v-if="!issuesLoading && !issues.length" class="field-hint">当前无在售期次，请等待运营开期</p>
              </el-form-item>
            </div>

            <el-form-item label="玩法">
              <div class="chips">
                <button
                  v-for="p in playTypes"
                  :key="p.value"
                  type="button"
                  class="chip"
                  :class="{ on: form.playType === p.value }"
                  @click="form.playType = p.value"
                >
                  {{ p.label }}
                </button>
              </div>
            </el-form-item>

            <el-form-item>
              <template #label>
                <span class="label-row">
                  <span>标题</span>
                  <em :class="{ bad: titleCount > 0 && titleCount < 8 }">{{ form.title.length }}/40</em>
                </span>
              </template>
              <el-input
                v-model="form.title"
                maxlength="40"
                placeholder="8-40 字，例如：125期双色球：红球三区走强，蓝球看好07"
              />
            </el-form-item>

            <el-form-item>
              <template #label>
                <span class="label-row">
                  <span>免费摘要</span>
                  <em :class="{ bad: summaryCount > 0 && summaryCount < 20 }">{{ form.summary.length }}/100</em>
                </span>
              </template>
              <el-input
                v-model="form.summary"
                type="textarea"
                :rows="2"
                maxlength="100"
                placeholder="20-100 字，未购买用户可见，写清结论与信心来源"
              />
            </el-form-item>

            <el-form-item>
              <template #label>
                <span class="label-row">
                  <span>付费正文</span>
                  <em :class="{ bad: contentTooShort }">正文 {{ contentCount }} / 100 字</em>
                </span>
              </template>
              <el-input
                v-model="form.contentHtml"
                type="textarea"
                :rows="6"
                maxlength="8000"
                placeholder="至少 100 字，写分析思路、数据依据与风险提示。禁止出现“必中/包中/稳赚”等表述"
              />
            </el-form-item>
          </el-form>
        </section>

        <section class="panel">
          <div class="panel-head">
            <div>
              <h2>预测项</h2>
              <p>每项一个独立判定口径，开奖后逐项计算是否命中</p>
            </div>
            <div class="ratio" :class="{ bad: freeRatioInvalid }">
              免费 <strong>{{ freeCount }}/{{ items.length }}</strong>
              <span class="ratio-pct">{{ freePercent }}%</span>
            </div>
          </div>

          <p class="ratio-tip" :class="{ bad: freeRatioInvalid }">
            <template v-if="freeRatioInvalid">
              免费项占比已超 40%，当前 {{ items.length }} 项最多 {{ freeLimit }} 项免费
            </template>
            <template v-else>
              免费项占比需 ≤ 40%（当前 {{ items.length }} 项最多 {{ freeLimit }} 项免费，且至少 1 项）
            </template>
          </p>

          <p v-if="draftBetLabel" class="draft-bet">
            已带入投注信息：<strong>{{ draftBetLabel }}</strong>
            <span>金额按 2 元/注折算，仅作参考，平台不提供代购</span>
          </p>

          <div class="items">
            <div v-for="(item, index) in items" :key="index" class="item-row" :class="{ open: expandedIndex === index }">
              <div class="item-line">
                <span class="idx">{{ String(index + 1).padStart(2, '0') }}</span>
                <el-select v-model="item.itemType" class="f-type" @change="onTypeChange(index, $event as string)">
                  <el-option v-for="t in itemTypes" :key="t.value" :label="t.label" :value="t.value">
                    <span class="opt-name">{{ t.label }}</span>
                    <span class="opt-desc">{{ t.desc }}</span>
                  </el-option>
                </el-select>

                <button
                  v-if="isNumberItem(item.itemType)"
                  type="button"
                  class="f-value value-btn"
                  :class="{ empty: !item.itemValue, active: expandedIndex === index }"
                  @click="togglePicker(index)"
                >
                  <PickBalls
                    v-if="item.itemValue"
                    :type-code="form.typeCode"
                    :item-type="item.itemType"
                    :value="item.itemValue"
                    size="xs"
                    :max="12"
                  />
                  <span v-else class="ph">点此选号，或粘贴号码</span>
                  <span class="pick-tag">{{ expandedIndex === index ? '收起' : '选号' }}</span>
                </button>
                <el-input v-else v-model="item.itemValue" class="f-value" :placeholder="valueHint(item.itemType)" />

                <el-input v-model="item.itemLabel" class="f-label" placeholder="可选文案" />
                <div class="f-free" :class="{ on: item.isFree }">
                  <el-switch v-model="item.isFree" size="small" />
                  <span>{{ item.isFree ? '免费可见' : '解锁可见' }}</span>
                </div>
                <el-button class="f-del" text type="danger" :disabled="items.length <= 1" @click="removeItem(index)">
                  删除
                </el-button>
              </div>

              <BallPicker
                v-if="isNumberItem(item.itemType) && expandedIndex === index"
                v-model="item.itemValue"
                :type-code="form.typeCode"
                :item-type="item.itemType"
                :group="groupOf(item.itemType)"
                :max="maxOf(item.itemType)"
                class="item-picker"
              />
            </div>
          </div>

          <el-button class="add-btn" plain @click="addItem">+ 添加一项</el-button>
        </section>
      </main>

      <aside class="side">
        <section class="panel price-card">
          <div class="panel-head">
            <div>
              <h2>定价</h2>
              <p>按准确度与样本量计算，可在区间内下浮</p>
            </div>
          </div>

          <template v-if="quote">
            <div class="price-main">
              <span class="price-num">{{ form.pricePoints }}</span>
              <span class="price-unit">积分</span>
              <span class="price-range">{{ quote.minPrice }}–{{ quote.maxPrice }}</span>
            </div>
            <div class="price-control">
              <el-slider
                v-model="form.pricePoints"
                :min="quote.minPrice"
                :max="quote.maxPrice"
                :step="1"
                :show-tooltip="false"
              />
              <el-input-number
                v-model="form.pricePoints"
                :min="quote.minPrice"
                :max="quote.maxPrice"
                :step="1"
                size="small"
                controls-position="right"
                class="price-input"
              />
            </div>
            <p class="price-suggest">建议价 {{ quote.suggestPrice }} 积分</p>

            <dl class="spec">
              <div><dt>基础价</dt><dd>{{ quote.base }}</dd></div>
              <div><dt>准确度系数</dt><dd>× {{ quote.coefficient }}</dd></div>
              <div><dt>样本因子</dt><dd>× {{ quote.sampleFactor }}</dd></div>
              <div><dt>你的评分</dt><dd>{{ scoreText }}</dd></div>
            </dl>
            <p class="formula">{{ quote.formula }}</p>
          </template>
          <p v-else class="price-empty">选择彩种与玩法后显示定价区间</p>

          <div class="submit-bar">
            <el-button :loading="submitting" @click="save(false)">保存草稿</el-button>
            <el-button type="primary" :loading="submitting" :disabled="!isCreator" @click="save(true)">
              提交审核
            </el-button>
          </div>
          <p class="submit-note">
            {{ isCreator ? '提交后进入审核，审核通过不可修改；停售前可撤回' : '认证通过后才可提交审核' }}
          </p>
        </section>

        <section class="panel rules-card">
          <h2>发布规则</h2>
          <ul>
            <li>需已实名并认证为创作者</li>
            <li>同一期次最多 5 篇有效预测</li>
            <li>免费项至少 1 个，占比 ≤ 40%</li>
            <li>售价只能在系统区间内下浮</li>
            <li>停售时间后不可修改</li>
          </ul>
        </section>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}
.head h1 {
  margin: 0 0 6px;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.01em;
}
.sub {
  margin: 0;
  font-size: 13px;
  color: var(--text-sub);
}
.notice {
  margin-bottom: 14px;
  border-radius: var(--radius-md);
}

.layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 16px;
  align-items: start;
}
.main {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
}
.side {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
}

/* 卡片 */
.panel {
  padding: 18px 20px;
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
  margin-bottom: 16px;
}
.panel-head h2 {
  margin: 0 0 4px;
  font-size: 15px;
  font-weight: 650;
}
.panel-head p {
  margin: 0;
  font-size: 12px;
  line-height: 1.7;
  color: var(--text-sub);
}

/* 表单 */
.form :deep(.el-form-item__label) {
  width: 100%;
  padding-bottom: 6px;
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--text-sub);
}
.label-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
}
.label-row em {
  font-style: normal;
  font-size: 11.5px;
  color: #9aa6b8;
  font-variant-numeric: tabular-nums;
}
.label-row em.bad {
  color: var(--el-color-danger);
}
.row-2 {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 0 14px;
}
.field-hint {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--el-color-warning);
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.chip {
  padding: 7px 14px;
  border: 1px solid var(--border-soft);
  border-radius: 999px;
  background: var(--surface);
  font: inherit;
  font-size: 12.5px;
  color: var(--text-sub);
  cursor: pointer;
  transition: color 0.18s, border-color 0.18s, background 0.18s;
}
.chip:hover {
  color: var(--brand);
  border-color: var(--el-color-primary-light-7);
}
.chip.on {
  color: var(--brand);
  border-color: var(--brand);
  background: var(--el-color-primary-light-9);
  font-weight: 600;
}

/* 预测项 */
.ratio {
  flex-shrink: 0;
  padding: 5px 12px;
  border-radius: 999px;
  background: var(--surface-soft);
  font-size: 12px;
  color: var(--text-sub);
  font-variant-numeric: tabular-nums;
}
.ratio strong {
  margin: 0 2px;
  color: var(--text-main);
  font-weight: 650;
}
.ratio-pct {
  margin-left: 6px;
  color: var(--text-main);
}
.ratio.bad {
  background: #fdeceb;
  color: var(--el-color-danger);
}
.ratio.bad strong,
.ratio.bad .ratio-pct {
  color: var(--el-color-danger);
}

.ratio-tip {
  margin: 0 0 14px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--text-sub);
}
.ratio-tip.bad {
  color: var(--el-color-danger);
}

.draft-bet {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin: 0 0 12px;
  padding: 8px 12px;
  border-radius: 8px;
  background: #fff7ee;
  border: 1px solid #f7dfc6;
  font-size: 12px;
  color: var(--text-sub);
}
.draft-bet strong {
  color: #c2410c;
  font-variant-numeric: tabular-nums;
}

.items {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.item-row {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px 14px;
  border: 1px solid var(--border-soft);
  border-radius: var(--radius-md);
  background: var(--surface-soft);
  transition: border-color 0.18s, background 0.18s;
}
.item-row:hover {
  border-color: var(--el-color-primary-light-7);
  background: var(--surface);
}
.item-row.open {
  border-color: var(--brand);
  background: var(--surface);
}
.item-line {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}
.idx {
  flex: 0 0 20px;
  font-size: 11.5px;
  font-weight: 600;
  color: #a6b0c0;
  font-variant-numeric: tabular-nums;
}
.f-type {
  flex: 0 0 186px;
}
.f-value {
  flex: 1 1 150px;
  min-width: 0;
}
.value-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 36px;
  padding: 4px 8px;
  border: 1px solid var(--border-soft);
  border-radius: var(--radius-sm);
  background: #fff;
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.18s, background 0.18s;
}
.value-btn:hover,
.value-btn.active {
  border-color: var(--brand);
  background: var(--el-color-primary-light-9);
}
.value-btn.empty {
  border-style: dashed;
}
.value-btn .ph {
  font-size: 12.5px;
  color: #9aa6b8;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.pick-tag {
  flex-shrink: 0;
  margin-left: auto;
  padding: 1px 7px;
  border-radius: 6px;
  background: var(--el-color-primary-light-9);
  font-size: 11.5px;
  color: var(--brand);
}
.value-btn.active .pick-tag {
  background: #fff;
}
.item-picker {
  width: 100%;
}
.f-label {
  flex: 0 1 118px;
  min-width: 0;
}
.f-free {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-sub);
  white-space: nowrap;
}
.f-free.on {
  color: var(--el-color-primary);
}
.f-del {
  flex: 0 0 auto;
  padding: 0 4px;
}
.add-btn {
  width: 100%;
  margin-top: 12px;
  border-style: dashed;
}
.opt-name {
  font-size: 13px;
}
.opt-desc {
  margin-left: 8px;
  font-size: 11.5px;
  color: #9aa6b8;
}

/* 定价 */
.price-card {
  position: sticky;
  top: 100px;
}
.price-main {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin-bottom: 4px;
}
.price-num {
  font-size: 30px;
  font-weight: 700;
  line-height: 1.1;
  color: var(--el-color-danger);
  font-variant-numeric: tabular-nums;
}
.price-unit {
  font-size: 12px;
  color: var(--text-sub);
}
.price-range {
  margin-left: auto;
  padding: 2px 8px;
  border-radius: 6px;
  background: var(--surface-soft);
  font-size: 11.5px;
  color: var(--text-sub);
  font-variant-numeric: tabular-nums;
}
.price-control {
  display: flex;
  align-items: center;
  gap: 10px;
}
.price-control :deep(.el-slider) {
  flex: 1;
  min-width: 0;
}
.price-input {
  flex: 0 0 84px;
  width: 84px;
}
.price-suggest {
  margin: 2px 0 12px;
  font-size: 11.5px;
  color: var(--text-sub);
}
.spec {
  margin: 0;
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--surface-soft);
  font-size: 12px;
}
.spec > div {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  padding: 4px 0;
}
.spec > div + div {
  border-top: 1px dashed #e7ecf3;
}
.spec dt {
  color: var(--text-sub);
}
.spec dd {
  margin: 0;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.formula {
  margin: 10px 0 0;
  font-size: 11.5px;
  line-height: 1.7;
  color: #9aa6b8;
}
.price-empty {
  margin: 0 0 4px;
  padding: 22px 12px;
  border: 1px dashed var(--border-soft);
  border-radius: 10px;
  text-align: center;
  font-size: 12px;
  color: var(--text-sub);
}
.submit-bar {
  display: flex;
  gap: 10px;
  margin-top: 16px;
}
.submit-bar .el-button {
  flex: 1;
  margin-left: 0;
}
.submit-note {
  margin: 8px 0 0;
  font-size: 11px;
  line-height: 1.7;
  color: #9aa6b8;
}

.rules-card h2 {
  margin: 0 0 10px;
  font-size: 13px;
  font-weight: 650;
}
.rules-card ul {
  margin: 0;
  padding-left: 16px;
  font-size: 12px;
  line-height: 2;
  color: var(--text-sub);
}

@media (max-width: 1080px) {
  .layout {
    grid-template-columns: minmax(0, 1fr);
  }
  /* 单列后定价与规则并排，避免整条拉伸 */
  .side {
    display: grid;
    grid-template-columns: minmax(0, 1.15fr) minmax(0, 1fr);
    align-items: start;
  }
  .price-card {
    position: static;
  }
}
@media (max-width: 780px) {
  .side {
    grid-template-columns: minmax(0, 1fr);
  }
}
@media (max-width: 640px) {
  .panel {
    padding: 16px;
  }
  .row-2 {
    grid-template-columns: minmax(0, 1fr);
  }
  .item-row {
    padding: 12px;
  }
  .f-type,
  .f-value {
    flex: 1 1 100%;
  }
  .f-label {
    flex: 1 1 40%;
  }
  .f-free {
    flex: 0 0 auto;
  }
  .f-del {
    margin-left: auto;
  }
  .head {
    flex-wrap: wrap;
  }
}
</style>
