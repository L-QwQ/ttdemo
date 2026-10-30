<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { lotteryApi } from '@/api/lottery'
import { predictionApi } from '@/api/prediction'
import { rankApi } from '@/api/market'
import type { Issue, LotteryType, PredictionListItem, RankRow } from '@/types/lottery'
import { formatDateTime } from '@/utils/format'
import { typeName, remainText, extraSummary } from '@/utils/lottery'
import BallGroup from '@/components/lottery/BallGroup.vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

const loading = ref(true)
const types = ref<LotteryType[]>([])
/** typeCode → 最新已开奖期次 */
const latestDrawn = ref<Record<string, Issue>>({})
/** typeCode → 当前可预测期次 */
const upcoming = ref<Record<string, Issue>>({})
const hotPredictions = ref<PredictionListItem[]>([])
const topCreators = ref<RankRow[]>([])

const enabledTypes = computed(() => types.value.filter((t) => t.status === 1))

/** 倒计时用本地时钟重算，避免后端快照越看越旧 */
const nowTick = ref(Date.now())
let clockTimer: number | undefined

/** 即将开奖按停售时间升序，已过停售的自动移出（页面停留跨过停售点时） */
const upcomingList = computed(() =>
  Object.values(upcoming.value)
    .filter((i) => new Date(i.saleEndTime).getTime() > nowTick.value)
    .sort((a, b) => new Date(a.saleEndTime).getTime() - new Date(b.saleEndTime).getTime()),
)

function remainUntil(saleEndTime: string | null | undefined): string {
  if (!saleEndTime) return '—'
  const diff = Math.floor((new Date(saleEndTime).getTime() - nowTick.value) / 1000)
  return remainText(diff)
}

const platformFeatures = [
  { icon: '🏆', title: '创作者榜单', desc: '准确率按样本量加权计算，每日快照可追溯', to: '/rank' },
  { icon: '🎲', title: '随机预测工具', desc: '15 种算法模型生成参考号码，含 6 种玄学玩法', to: '/random' },
  { icon: '📡', title: '官方开奖同步', desc: '开奖数据来自福彩/体彩官网，可核验', to: '/predictions' },
]

async function loadTypeData(code: string) {
  const [drawn, current] = await Promise.all([
    lotteryApi.issues({ typeCode: code, drawStatus: 1, limit: 1 }),
    lotteryApi.current(code).catch(() => [] as Issue[]),
  ])
  const drawn0 = drawn[0]
  if (drawn0) latestDrawn.value[code] = drawn0
  const next0 = current.find((i) => i.drawStatus !== 1)
  if (next0) upcoming.value[code] = next0
}

function latestOf(code: string): Issue | undefined {
  return latestDrawn.value[code]
}

/** 卡片位置有限，只取前两项（和值/跨度） */
function extraBrief(extra: string | null | undefined): string {
  const full = extraSummary(extra)
  return full ? full.split(' · ').slice(0, 2).join(' · ') : ''
}

onMounted(async () => {
  try {
    const [typeList] = await Promise.all([
      lotteryApi.types(),
      predictionApi
        .list({ sortBy: 'hot', pageNum: 1, pageSize: 4 })
        .then((p) => (hotPredictions.value = p.list))
        .catch(() => undefined),
      rankApi
        .list({ rankType: 'ACCURACY', typeCode: 'ALL', limit: 3 })
        .then((r) => (topCreators.value = r.list || []))
        .catch(() => undefined),
    ])
    types.value = typeList
    await Promise.all(enabledTypes.value.map((t) => loadTypeData(t.code).catch(() => undefined)))
  } finally {
    loading.value = false
  }
  clockTimer = window.setInterval(() => {
    nowTick.value = Date.now()
  }, 30000)
})

onUnmounted(() => {
  if (clockTimer) window.clearInterval(clockTimer)
})

function goPrediction(item: PredictionListItem) {
  router.push(`/prediction/${item.id}`)
}
</script>

<template>
  <div v-loading="loading" class="home">
    <!-- Hero -->
    <section class="hero">
      <div class="hero-text">
        <p class="eyebrow">官方开奖数据 · 创作者分析 · 命中可查</p>
        <h1>看得见历史的<br />彩票预测社区</h1>
        <p class="lead">
          开奖号码实时同步自中国福彩、体彩官方接口，每一条预测都有真实期次与命中明细，
          好不好、准不准，数据说了算。
        </p>
        <div class="hero-actions">
          <el-button type="primary" size="large" @click="router.push('/predictions')">进入预测广场</el-button>
          <el-button size="large" @click="router.push('/random')">免费随机预测</el-button>
          <el-button v-if="!auth.isLoggedIn" text size="large" @click="router.push('/login')">手机号登录 / 注册</el-button>
        </div>
        <p v-if="auth.isLoggedIn" class="welcome">当前登录：{{ auth.displayName }}</p>
      </div>
      <div class="hero-panel">
        <p class="panel-title">🔥 创作者准确率榜 TOP3</p>
        <div v-if="topCreators.length === 0" class="panel-empty">暂无上榜创作者（需累计满 10 期已结算样本）</div>
        <div v-for="row in topCreators" :key="row.creatorId" class="rank-row" @click="router.push(`/creator/${row.creatorId}`)">
          <span class="rank-no" :class="{ top: row.rankNo <= 3 }">{{ row.rankNo }}</span>
          <el-avatar :size="30" :src="row.avatar || undefined">{{ row.penName?.slice(0, 1) }}</el-avatar>
          <span class="rank-name">{{ row.penName }}</span>
          <span class="rank-metric">{{ row.metricText }}</span>
        </div>
        <el-divider style="margin: 12px 0" />
        <p class="panel-title">📅 即将开奖</p>
        <div v-if="upcomingList.length === 0" class="panel-empty">暂无在售期次</div>
        <div v-for="issue in upcomingList" :key="issue.id" class="up-row">
          <span class="type-tag" :data-code="issue.typeCode">{{ typeName(issue.typeCode) }}</span>
          <span class="up-issue">{{ issue.issueNo }} 期</span>
          <span class="up-time">{{ remainUntil(issue.saleEndTime) }}</span>
        </div>
      </div>
    </section>

    <!-- 最新真实开奖 -->
    <section class="section">
      <div class="section-head">
        <h2>最新开奖</h2>
        <span class="section-sub">数据来源：中国福彩网 / 中国体彩网 官方接口</span>
      </div>
      <div class="draw-grid">
        <div v-for="t in enabledTypes" :key="t.code" class="draw-card card-hover">
          <div class="draw-head">
            <span class="type-tag" :data-code="t.code">{{ t.name }}</span>
            <span v-if="latestOf(t.code)" class="issue-no">{{ latestOf(t.code)!.issueNo }} 期</span>
          </div>
          <div class="draw-body">
            <BallGroup
              v-if="latestOf(t.code)"
              :type-code="t.code"
              :red="latestOf(t.code)!.redNumbers"
              :blue="latestOf(t.code)!.blueNumbers"
              :max="t.code === 'KL8' ? 10 : 0"
              size="md"
            />
            <span v-else class="no-draw">暂无开奖数据</span>
          </div>
          <div class="draw-foot">
            <template v-if="latestOf(t.code)">
              <span>{{ formatDateTime(latestOf(t.code)!.drawTime) }}</span>
              <span v-if="extraBrief(latestOf(t.code)!.extra)" class="extra-info">
                {{ extraBrief(latestOf(t.code)!.extra) }}
              </span>
            </template>
            <template v-else>
              <span>开奖后自动同步</span>
            </template>
          </div>
        </div>
      </div>
    </section>

    <!-- 热门预测 -->
    <section class="section">
      <div class="section-head">
        <h2>热门预测</h2>
        <el-button text type="primary" @click="router.push('/predictions')">查看全部 →</el-button>
      </div>
      <div v-if="hotPredictions.length === 0" class="empty-tip">
        <p class="empty-title">还没有可展示的预测</p>
        <p class="empty-desc">平台只展示关联官方开奖期号的内容，发布后可在这里获得曝光</p>
        <el-button type="primary" plain @click="router.push('/publish')">去发布预测</el-button>
      </div>
      <div class="pred-grid">
        <article v-for="item in hotPredictions" :key="item.id" class="pred-card card-hover" @click="goPrediction(item)">
          <div class="pred-top">
            <span class="type-tag" :data-code="item.typeCode">{{ typeName(item.typeCode) }}</span>
            <span class="pred-issue">{{ item.issueNo }} 期 · {{ item.playTypeName }}</span>
            <span v-if="item.isFree" class="free-badge">免费</span>
            <span v-else class="price">{{ item.pricePoints }} 积分</span>
          </div>
          <h3 class="pred-title">{{ item.title }}</h3>
          <p class="pred-summary">{{ item.summary }}</p>
          <div class="pred-foot">
            <el-avatar :size="22" :src="item.creator.avatar || undefined">{{ item.creator.penName?.slice(0, 1) }}</el-avatar>
            <span class="pred-creator">{{ item.creator.penName }}</span>
            <span class="pred-score">{{ item.creator.accuracyScore?.toFixed(1) }} 分</span>
            <span class="pred-views">{{ item.viewCount }} 浏览</span>
          </div>
        </article>
      </div>
    </section>

    <!-- 平台能力 -->
    <section class="section">
      <div class="feature-grid">
        <div v-for="f in platformFeatures" :key="f.title" class="feature-card card-hover" @click="router.push(f.to)">
          <span class="feature-icon">{{ f.icon }}</span>
          <div>
            <h3>{{ f.title }}</h3>
            <p>{{ f.desc }}</p>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.hero {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(0, 1fr);
  gap: 36px;
  align-items: center;
  padding: 36px 0 28px;
}

.eyebrow {
  margin: 0 0 12px;
  font-size: 13px;
  letter-spacing: 0.06em;
  color: var(--brand);
  font-weight: 600;
}

.hero h1 {
  margin: 0 0 14px;
  font-size: 40px;
  line-height: 1.2;
  letter-spacing: -0.02em;
}

.lead {
  margin: 0 0 26px;
  font-size: 15px;
  line-height: 1.8;
  color: var(--text-sub);
  max-width: 480px;
}

.hero-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.welcome {
  margin: 14px 0 0;
  font-size: 13px;
  color: var(--text-sub);
}

.hero-panel {
  background: #fff;
  border: 1px solid var(--border-soft);
  border-radius: 14px;
  padding: 20px;
  box-shadow: 0 12px 30px rgba(90, 40, 20, 0.06);
}

.panel-title {
  margin: 0 0 10px;
  font-size: 14px;
  font-weight: 700;
}

.panel-empty {
  font-size: 13px;
  color: var(--text-sub);
  padding: 6px 0;
}

.rank-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 0;
  cursor: pointer;
  border-radius: 8px;
}

.rank-no {
  width: 22px;
  text-align: center;
  font-weight: 800;
  color: var(--text-sub);
  font-size: 14px;
}

.rank-no.top {
  color: var(--brand);
  font-size: 17px;
}

.rank-name {
  font-size: 13px;
  font-weight: 600;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rank-metric {
  font-size: 12px;
  color: var(--brand);
  font-weight: 600;
}

.up-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 0;
  font-size: 13px;
}

.up-issue {
  flex: 1;
  color: var(--text-main);
  font-weight: 500;
}

.up-time {
  color: var(--el-color-warning);
  font-size: 12px;
}

.section {
  margin: 26px 0;
}

.section-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 14px;
}

.section-head h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
}

.section-sub {
  font-size: 12px;
  color: var(--text-sub);
}

.draw-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 14px;
}

.draw-card {
  background: #fff;
  border: 1px solid var(--border-soft);
  border-radius: 12px;
  padding: 16px 18px;
}

.draw-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}

.issue-no {
  font-size: 13px;
  color: var(--text-sub);
  font-weight: 500;
}

.draw-body {
  min-height: 40px;
  margin-bottom: 12px;
}

.draw-foot {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 4px 10px;
  font-size: 12px;
  color: var(--text-sub);
  border-top: 1px dashed var(--border-soft);
  padding-top: 10px;
}

.extra-info {
  color: var(--gold);
  font-weight: 500;
  white-space: nowrap;
}

.empty-tip {
  background: #fff;
  border: 1px dashed var(--border-soft);
  border-radius: 12px;
  padding: 30px;
  text-align: center;
  color: var(--text-sub);
  font-size: 14px;
}

.empty-title {
  margin: 0 0 6px;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-main);
}

.empty-desc {
  margin: 0 0 14px;
  font-size: 13px;
  color: var(--text-sub);
}

.pred-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 14px;
}

.pred-card {
  background: #fff;
  border: 1px solid var(--border-soft);
  border-radius: 12px;
  padding: 16px 18px;
  cursor: pointer;
}

.pred-top {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.pred-issue {
  font-size: 12px;
  color: var(--text-sub);
  flex: 1;
}

.free-badge {
  font-size: 12px;
  color: var(--hit);
  font-weight: 700;
}

.price {
  font-size: 14px;
  color: var(--brand);
  font-weight: 700;
}

.pred-title {
  margin: 0 0 6px;
  font-size: 16px;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.pred-summary {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--text-sub);
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.pred-foot {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-sub);
  border-top: 1px dashed var(--border-soft);
  padding-top: 10px;
}

.pred-creator {
  font-weight: 600;
  color: var(--text-main);
}

.pred-score {
  color: var(--brand);
  font-weight: 600;
}

.pred-views {
  margin-left: auto;
}

.feature-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 14px;
}

.feature-card {
  display: flex;
  gap: 14px;
  background: #fff;
  border: 1px solid var(--border-soft);
  border-radius: 12px;
  padding: 20px;
  cursor: pointer;
}

.feature-icon {
  font-size: 26px;
  line-height: 1;
}

.feature-card h3 {
  margin: 0 0 6px;
  font-size: 15px;
}

.feature-card p {
  margin: 0;
  font-size: 13px;
  color: var(--text-sub);
  line-height: 1.6;
}

@media (max-width: 900px) {
  .hero {
    grid-template-columns: 1fr;
    padding-top: 16px;
  }

  .hero h1 {
    font-size: 28px;
  }
}
</style>
