<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { lotteryApi } from '@/api/lottery'
import { predictionApi, type PredictionItemPayload } from '@/api/prediction'
import type { Issue, LotteryType, PriceQuote } from '@/types/lottery'
import { formatDateTime } from '@/utils/format'

const router = useRouter()
const types = ref<LotteryType[]>([])
const issues = ref<Issue[]>([])
const quote = ref<PriceQuote | null>(null)
const loading = ref(false)
const submitting = ref(false)

const form = reactive({
  typeCode: '',
  issueNo: '',
  title: '',
  playType: 'SINGLE',
  summary: '',
  contentHtml: '',
  pricePoints: 0,
})

const items = ref<PredictionItemPayload[]>([
  { itemType: 'RED_SINGLE', itemValue: '', itemLabel: '红球6码', isFree: true },
  { itemType: 'BLUE_SINGLE', itemValue: '', itemLabel: '蓝球', isFree: false },
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
  { value: 'RED_SINGLE', label: '单式红球组（完全一致才命中）' },
  { value: 'RED_MULTI', label: '复式红球池（命中率≥50%算中）' },
  { value: 'DAN', label: '胆码（交集/预测数≥50%）' },
  { value: 'KILL', label: '杀号（与开奖无交集算中）' },
  { value: 'BLUE_SINGLE', label: '蓝球/后区（相等算中）' },
  { value: 'SUM_RANGE', label: '和值区间（如 90-115）' },
  { value: 'SPAN', label: '跨度（如 18）' },
  { value: 'ODD_EVEN', label: '奇偶比（如 4:2）' },
  { value: 'BIG_SMALL', label: '大小比（如 3:3）' },
]

const freeCount = computed(() => items.value.filter((i) => i.isFree).length)
const freeRatio = computed(() => (items.value.length ? freeCount.value / items.value.length : 0))
const freeRatioInvalid = computed(() => items.value.length > 0 && freeCount.value * 5 > items.value.length * 2)
const priceRangeText = computed(() =>
  quote.value ? `${quote.value.minPrice} - ${quote.value.maxPrice} 积分` : '—',
)

async function loadIssues() {
  if (!form.typeCode) return
  issues.value = await lotteryApi.current(form.typeCode)
  form.issueNo = issues.value[0]?.issueNo || ''
}

async function loadQuote() {
  if (!form.typeCode || !form.playType) return
  try {
    quote.value = await predictionApi.priceQuote(form.typeCode, form.playType)
    if (!form.pricePoints) form.pricePoints = quote.value.suggestPrice
    if (form.pricePoints > quote.value.maxPrice) form.pricePoints = quote.value.maxPrice
    if (form.pricePoints < quote.value.minPrice) form.pricePoints = quote.value.minPrice
  } catch {
    quote.value = null
  }
}

watch(() => form.typeCode, async () => {
  await loadIssues()
  await loadQuote()
})

watch(() => form.playType, loadQuote)

function addItem() {
  items.value.push({ itemType: 'RED_MULTI', itemValue: '', itemLabel: '', isFree: false })
}

function removeItem(index: number) {
  items.value.splice(index, 1)
}

function validate(): string | null {
  if (!form.typeCode) return '请选择彩种'
  if (!form.issueNo) return '请选择期次'
  if (form.title.trim().length < 8 || form.title.length > 40) return '标题需 8-40 字'
  if (form.summary.trim().length < 20 || form.summary.length > 100) return '摘要需 20-100 字'
  const plain = form.contentHtml.replace(/<[^>]+>/g, '')
  if (plain.length < 100) return `正文需至少 100 字（当前 ${plain.length} 字，不含标签）`
  if (items.value.length === 0) return '至少 1 个预测项'
  if (freeCount.value === 0) return '至少 1 个免费可见的预测项'
  if (freeRatioInvalid.value) return '免费项占比不能超过 40%'
  if (items.value.some((i) => !i.itemValue.trim())) return '存在未填写的预测项'
  if (quote.value && (form.pricePoints < quote.value.minPrice || form.pricePoints > quote.value.maxPrice)) {
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
      title: form.title,
      playType: form.playType,
      summary: form.summary,
      contentHtml: `<p>${form.contentHtml.replace(/\n/g, '</p><p>')}</p>`,
      pricePoints: form.pricePoints,
      items: items.value,
    }
    const created = await predictionApi.create(payload)
    if (submitAfter) {
      await predictionApi.submit(created.id)
      ElMessage.success('已提交审核，预计 10 分钟内完成')
    } else {
      ElMessage.success('草稿已保存')
    }
    router.push('/creator/dashboard')
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  const all = await lotteryApi.types()
  types.value = all.filter((t) => t.status === 1)

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
        items?: PredictionItemPayload[]
      }
      if (draft.typeCode && types.value.some((t) => t.code === draft.typeCode)) {
        form.typeCode = draft.typeCode
      }
      form.title = draft.title || form.title
      form.summary = draft.summary || form.summary
      if (Array.isArray(draft.items) && draft.items.length) {
        items.value = draft.items
      }
      ElMessage.info(`已带入「${draft.methodName || '随机'}」生成的号码，请补充付费正文后提交`)
      return
    } catch {
      // 解析失败则按普通流程初始化
    }
  }

  const first = types.value[0]
  if (first) {
    form.typeCode = first.code
  }
})
</script>

<template>
  <div class="page">
    <header class="head">
      <div>
        <h1>发布预测</h1>
        <p class="sub">预测项必须结构化填写，系统按期开奖结果自动判定，人工不可改判</p>
      </div>
      <el-button text @click="router.back()">返回</el-button>
    </header>

    <div class="layout">
      <main>
        <el-card shadow="never" class="block">
          <template #header><span class="t">基础信息</span></template>
          <el-form label-width="88px" label-position="left">
            <el-form-item label="彩种期次">
              <el-select v-model="form.typeCode" style="width: 160px">
                <el-option v-for="t in types" :key="t.code" :label="t.name" :value="t.code" />
              </el-select>
              <el-select v-model="form.issueNo" placeholder="选择期次" style="width: 200px; margin-left: 10px">
                <el-option v-for="i in issues" :key="i.id" :label="`${i.issueNo}（${formatDateTime(i.saleEndTime)} 停售）`" :value="i.issueNo" />
              </el-select>
              <div v-if="issues.length === 0" class="hint">当前无在售期次，请等待运营开期</div>
            </el-form-item>
            <el-form-item label="玩法">
              <el-select v-model="form.playType" style="width: 200px">
                <el-option v-for="p in playTypes" :key="p.value" :label="p.label" :value="p.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="标题">
              <el-input v-model="form.title" maxlength="40" show-word-limit placeholder="8-40 字，例如：102期双色球：红球三区走强，蓝球看好07" />
            </el-form-item>
            <el-form-item label="免费摘要">
              <el-input v-model="form.summary" type="textarea" :rows="2" maxlength="100" show-word-limit placeholder="20-100 字，未购买用户可见" />
            </el-form-item>
            <el-form-item label="付费正文">
              <el-input v-model="form.contentHtml" type="textarea" :rows="8" maxlength="8000" show-word-limit placeholder="至少 100 字，可写分析思路、数据依据。禁止出现“必中/包中/稳赚”等表述" />
            </el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="never" class="block">
          <template #header>
            <div class="block-head">
              <span class="t">预测项（结构化，参与自动判定）</span>
              <el-button size="small" @click="addItem">添加一项</el-button>
            </div>
          </template>

          <el-alert
            v-if="freeRatioInvalid"
            type="warning"
            :closable="false"
            show-icon
            title="免费项占比不能超过 40%（当前已超出）"
            class="mb"
          />
          <div class="ratio">
            共 {{ items.length }} 项，免费 {{ freeCount }} 项（上限 40%）
          </div>

          <div v-for="(item, index) in items" :key="index" class="item-row">
            <el-select v-model="item.itemType" style="width: 240px">
              <el-option v-for="t in itemTypes" :key="t.value" :label="t.label" :value="t.value" />
            </el-select>
            <el-input v-model="item.itemValue" placeholder="值，如 03,05,12,18,22,29 或 90-115" style="flex: 1" />
            <el-input v-model="item.itemLabel" placeholder="展示文案（可选）" style="width: 160px" />
            <el-switch v-model="item.isFree" active-text="免费" />
            <el-button text type="danger" @click="removeItem(index)">删除</el-button>
          </div>
        </el-card>
      </main>

      <aside>
        <el-card shadow="never" class="price-card">
          <p class="price-title">定价</p>
          <template v-if="quote">
            <div class="price-big">{{ form.pricePoints }} <small>积分</small></div>
            <el-slider
              v-model="form.pricePoints"
              :min="quote.minPrice"
              :max="quote.maxPrice"
              :step="1"
              show-input
              size="small"
            />
            <div class="formula">{{ quote.formula }}</div>
            <el-descriptions :column="1" size="small" border class="desc">
              <el-descriptions-item label="基础价">{{ quote.base }}</el-descriptions-item>
              <el-descriptions-item label="准确度系数">{{ quote.coefficient }}</el-descriptions-item>
              <el-descriptions-item label="样本因子">{{ quote.sampleFactor }}</el-descriptions-item>
              <el-descriptions-item label="你的评分">{{ quote.accuracyScore?.toFixed(1) }}（{{ quote.sampleSize }} 期样本）</el-descriptions-item>
              <el-descriptions-item label="可售区间">{{ priceRangeText }}</el-descriptions-item>
            </el-descriptions>
            <p class="note">{{ quote.note }}</p>
          </template>
          <el-empty v-else description="选择彩种与玩法后显示定价" :image-size="60" />

          <div class="actions">
            <el-button :loading="submitting" @click="save(false)">保存草稿</el-button>
            <el-button type="primary" :loading="submitting" @click="save(true)">提交审核</el-button>
          </div>
        </el-card>

        <el-card shadow="never" class="rule-card">
          <p class="price-title">发布规则</p>
          <ul class="rules">
            <li>需已实名并认证为创作者</li>
            <li>同一期次最多 5 篇有效预测</li>
            <li>免费项至少 1 个，占比 ≤ 40%</li>
            <li>售价只能在系统区间内下浮</li>
            <li>停售时间后不可修改</li>
          </ul>
        </el-card>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.page { max-width: 1150px; margin: 0 auto; padding: 24px 16px 64px; }
.head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 16px; }
.head h1 { margin: 0 0 6px; font-size: 22px; }
.sub { margin: 0; font-size: 13px; color: var(--el-text-color-secondary); }
.layout { display: grid; grid-template-columns: minmax(0, 1fr) 340px; gap: 16px; }
.block { margin-bottom: 14px; border-radius: 12px; }
.t { font-weight: 600; font-size: 14px; }
.block-head { display: flex; justify-content: space-between; align-items: center; }
.ratio { font-size: 12px; color: var(--el-text-color-secondary); margin-bottom: 10px; }
.mb { margin-bottom: 10px; }
.item-row { display: flex; gap: 10px; align-items: center; margin-bottom: 10px; }
.hint { font-size: 12px; color: var(--el-color-warning); margin-left: 10px; }
.price-card { border-radius: 12px; margin-bottom: 14px; position: sticky; top: 76px; }
.price-title { margin: 0 0 10px; font-weight: 600; font-size: 14px; }
.price-big { font-size: 28px; font-weight: 700; color: var(--el-color-danger); margin-bottom: 6px; }
.price-big small { font-size: 12px; color: var(--el-text-color-secondary); }
.formula { font-size: 12px; color: var(--el-text-color-secondary); margin: 8px 0; line-height: 1.5; }
.desc { margin-top: 6px; }
.note { font-size: 11px; color: var(--el-text-color-placeholder); line-height: 1.6; margin: 10px 0 0; }
.actions { display: flex; gap: 10px; margin-top: 16px; }
.actions .el-button { flex: 1; }
.rule-card { border-radius: 12px; background: var(--el-fill-color-lighter); }
.rules { margin: 0; padding-left: 18px; font-size: 12px; line-height: 1.9; color: var(--el-text-color-secondary); }
@media (max-width: 900px) { .layout { grid-template-columns: 1fr; } }
</style>
