<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { lotteryApi } from '@/api/lottery'
import { predictionApi } from '@/api/prediction'
import { rankApi } from '@/api/market'
import type { Issue, LotteryType, PredictionListItem, RankRow } from '@/types/lottery'
import { formatDateTime } from '@/utils/format'
import { typeName, remainText, isNumberItem, itemTypeName } from '@/utils/lottery'
import BallGroup from '@/components/lottery/BallGroup.vue'
import PickBalls from '@/components/lottery/PickBalls.vue'
import { useAuthStore } from '@/stores/auth'
import { ArrowRight, DataAnalysis, TrendCharts, Trophy } from '@element-plus/icons-vue'

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

interface DrawCard {
  code: string
  name: string
  issue?: Issue
  sum: string
  span: string
  sales: number
  pool: number
  topPrize: { level: string; count: number; amount: number } | null
}

/** 卡片数据一次算好，避免模板里反复 JSON.parse */
const drawCards = computed<DrawCard[]>(() =>
  enabledTypes.value.map((t) => {
    const issue = latestDrawn.value[t.code]
    const card: DrawCard = { code: t.code, name: t.name, issue, sum: '-', span: '-', sales: 0, pool: 0, topPrize: null }
    if (!issue?.extra) return card
    try {
      const o = JSON.parse(issue.extra) as Record<string, unknown>
      card.sum = o.sum == null ? '-' : String(o.sum)
      card.span = o.span == null ? '-' : String(o.span)
      card.sales = Number(o.sales ?? 0)
      card.pool = Number(o.pool ?? 0)
      const prizes = o.prizes
      if (Array.isArray(prizes)) {
        const first = prizes.find((p) => Number((p as Record<string, unknown>).count ?? 0) > 0)
        if (first) {
          const f = first as Record<string, unknown>
          card.topPrize = {
            level: String(f.level ?? ''),
            count: Number(f.count ?? 0),
            amount: Number(f.amount ?? 0),
          }
        }
      }
    } catch {
      // extra 非 JSON 时只展示号码
    }
    return card
  }),
)

/** 金额：亿/万，无数据用破折号 */
function money(v: number): string {
  if (!v || v <= 0) return '—'
  if (v >= 1e8) return (v / 1e8).toFixed(2) + ' 亿'
  if (v >= 1e4) return (v / 1e4).toFixed(1) + ' 万'
  return v.toLocaleString()
}

function prizeText(p: DrawCard['topPrize']): string {
  if (!p) return ''
  return p.amount > 0 ? `${p.level} ${p.count} 注 · ${money(p.amount)}` : `${p.level} ${p.count} 注`
}

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

const platformFeatures = [
  { icon: Trophy, title: '创作者榜单', desc: '用历史记录，发现值得关注的创作者', to: '/rank' },
  { icon: TrendCharts, title: '随机预测工具', desc: '探索不同算法，生成你的参考号码', to: '/random' },
  { icon: DataAnalysis, title: '开奖数据中心', desc: '开奖、奖池与中奖明细，一站查询', to: '/draws' },
]

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
  <div class="home">
    <!-- 头部 -->
    <section class="hero">
      <div class="hero-main">
        <p class="eyebrow"><span class="live-dot" /> 数据有来源 · 分析有记录</p>
        <h1>让数据说话，<br /><span>让每份分析可追溯。</span></h1>
        <p class="lead">
          汇集官方开奖信息与创作者分析，按期记录、逐项核算。<br class="desktop-break" />
          从历史数据出发，发现更有依据的参考。
        </p>
        <div class="hero-actions">
          <el-button type="primary" @click="router.push('/predictions')">探索预测广场 <el-icon><ArrowRight /></el-icon></el-button>
          <el-button @click="router.push('/draws')">查看最新开奖</el-button>
        </div>
        <p v-if="auth.isLoggedIn" class="welcome">已登录：{{ auth.displayName }}</p>
        <div class="hero-trust"><span>官方数据来源</span><span>历史记录可查</span><span>理性交流参考</span></div>
      </div>

      <aside class="hero-side">
        <div class="side-block">
          <div class="side-head">
            <span class="side-title"><el-icon><Trophy /></el-icon> 创作者精选榜</span>
            <router-link to="/rank" class="side-more">查看榜单 ↗</router-link>
          </div>
          <p v-if="loading" class="side-empty">加载中…</p>
          <p v-else-if="topCreators.length === 0" class="side-empty">
            样本累计中，满 10 期已结算样本后自动上榜
          </p>
          <ul v-else class="rank-list">
            <li
              v-for="(row, index) in topCreators"
              :key="row.creatorId"
              class="rank-row"
            >
              <router-link :to="`/creator/${row.creatorId}`" class="rank-link">
                <span class="rank-position">0{{ index + 1 }}</span>
                <el-avatar :size="28" :src="row.avatar || undefined">{{ row.penName?.slice(0, 1) }}</el-avatar>
                <div class="rank-info">
                  <div class="rank-line">
                    <span class="rank-name">{{ row.penName }}</span>
                    <span class="rank-score">{{ row.metricText }}</span>
                  </div>
                  <div class="rank-bar">
                    <i :style="{ width: Math.min(100, Math.max(4, row.metricValue)) + '%' }" />
                  </div>
                </div>
              </router-link>
            </li>
          </ul>
        </div>

        <div class="side-block">
          <div class="side-head">
            <span class="side-title"><span class="live-dot" /> 即将开奖</span>
            <span class="side-note">停售倒计时</span>
          </div>
          <p v-if="loading" class="side-empty">加载中…</p>
          <p v-else-if="upcomingList.length === 0" class="side-empty">暂无在售期次</p>
          <ul v-else class="up-list">
            <li v-for="issue in upcomingList" :key="issue.id" class="up-row">
              <span class="type-dot" :data-code="issue.typeCode" />
              <div class="up-info">
                <span class="up-name">{{ typeName(issue.typeCode) }} <i>{{ issue.issueNo }}</i></span>
                <span class="up-sub">{{ formatDateTime(issue.drawTime) }} 开奖</span>
              </div>
              <span class="up-time">{{ remainUntil(issue.saleEndTime) }}</span>
            </li>
          </ul>
        </div>
      </aside>
    </section>

    <section class="feature-grid" aria-label="快捷入口">
      <router-link v-for="f in platformFeatures" :key="f.title" :to="f.to" class="feature-card">
        <span class="feature-icon"><el-icon><component :is="f.icon" /></el-icon></span>
        <div><h3>{{ f.title }}</h3><p>{{ f.desc }}</p></div>
        <el-icon class="feature-arrow"><ArrowRight /></el-icon>
      </router-link>
    </section>

    <div v-loading="loading">
      <!-- 最新开奖 -->
      <section class="section">
        <div class="section-head">
          <div class="head-left">
            <h2>最新开奖</h2>
            <span class="head-note">数据来源：中国福彩网 / 中国体彩网</span>
          </div>
          <router-link to="/draws" class="more-link">全部开奖 →</router-link>
        </div>

        <div class="draw-grid">
          <div v-if="!loading && drawCards.length === 0" class="empty-tip draw-placeholder">
            <el-icon class="empty-icon"><DataAnalysis /></el-icon>
            <p class="empty-title">开奖数据待同步</p>
            <p class="empty-desc">暂无可展示的开奖数据，可前往数据中心查看历史记录。</p>
            <router-link to="/draws" class="more-link">前往开奖数据中心 →</router-link>
          </div>
          <router-link
            v-for="c in drawCards"
            :key="c.code"
            :to="{ path: '/draws', query: { type: c.code } }"
            class="draw-card"
          >
            <div class="draw-head">
              <span class="draw-name">{{ c.name }}</span>
              <span class="draw-issue">{{ c.issue ? `${c.issue.issueNo} 期` : '—' }}</span>
            </div>

            <div class="draw-body">
              <BallGroup
                v-if="c.issue"
                :type-code="c.code"
                :red="c.issue.redNumbers"
                :blue="c.issue.blueNumbers"
                :max="c.code === 'KL8' ? 10 : 0"
                size="md"
              />
              <span v-else class="draw-empty">暂无数据</span>
            </div>

            <div class="draw-stats">
              <div class="stat"><span>和值</span><b>{{ c.sum }}</b></div>
              <div class="stat"><span>跨度</span><b>{{ c.span }}</b></div>
              <div class="stat"><span>销售额</span><b>{{ money(c.sales) }}</b></div>
              <div class="stat"><span>奖池</span><b>{{ money(c.pool) }}</b></div>
            </div>

            <div class="draw-foot">
              <span v-if="c.topPrize" class="prize-line">{{ prizeText(c.topPrize) }}</span>
              <span v-else class="prize-line muted">{{ c.issue ? '官方未公布奖等数据' : '开奖后自动同步' }}</span>
              <span v-if="c.issue" class="draw-time">{{ formatDateTime(c.issue.drawTime) }}</span>
            </div>
          </router-link>
        </div>
      </section>

      <!-- 热门预测 -->
      <section class="section">
        <div class="section-head">
          <div class="head-left">
            <h2>热门预测</h2>
            <span class="head-note">按解锁与浏览热度排序</span>
          </div>
          <router-link to="/predictions" class="more-link">全部预测 →</router-link>
        </div>

        <div v-if="hotPredictions.length === 0" class="empty-tip">
          <el-icon class="empty-icon"><TrendCharts /></el-icon>
          <p class="empty-title">还没有可展示的预测</p>
          <p class="empty-desc">平台只展示关联官方开奖期号的预测，发布后即可在这里获得曝光。</p>
          <div class="empty-actions">
            <el-button type="primary" plain size="small" @click="router.push('/publish')">去发布预测</el-button>
            <el-button size="small" @click="router.push('/random')">随机生成一注</el-button>
          </div>
        </div>

        <div v-else class="pred-grid">
          <article
            v-for="item in hotPredictions"
            :key="item.id"
            class="pred-card"
            tabindex="0"
            role="link"
            @click="goPrediction(item)"
            @keydown.enter="goPrediction(item)"
          >
            <div class="pred-top">
              <span class="pred-type">{{ typeName(item.typeCode) }}</span>
              <span class="pred-issue">{{ item.issueNo }} 期</span>
              <span v-if="item.isFree" class="pred-free">免费</span>
              <span v-else class="pred-price">{{ item.pricePoints }} 积分</span>
            </div>
            <h3 class="pred-title">{{ item.title }}</h3>
            <p class="pred-summary">{{ item.summary }}</p>
            <div class="pred-tags">
              <span v-for="f in item.freeItems.slice(0, 2)" :key="f.itemType" class="pred-tag">
                <em>{{ f.label || itemTypeName(f.itemType) }}</em>
                <PickBalls
                  v-if="isNumberItem(f.itemType) && f.value"
                  :type-code="item.typeCode"
                  :item-type="f.itemType"
                  :value="f.value"
                  size="xs"
                  :max="6"
                />
              </span>
              <span v-if="item.freeItems.length > 2" class="pred-tag muted">
                +{{ item.freeItems.length - 2 }}
              </span>
            </div>
            <div class="pred-foot">
              <span class="pred-creator">{{ item.creator.penName }}</span>
              <span class="sep">·</span>
              <span>{{ item.creator.accuracyScore?.toFixed(1) }} 分</span>
              <span class="pred-views">{{ item.buyerCount }} 人解锁 · {{ item.viewCount }} 浏览</span>
            </div>
          </article>
        </div>
      </section>

    </div>
  </div>
</template>

<style scoped>
.home { --home-accent: #3563e9; }
.hero { position: relative; display: grid; grid-template-columns: minmax(0, 1.45fr) minmax(min(100%, 300px), 1fr); gap: 48px; align-items: center; padding: 48px; border: 1px solid #dfe7f5; border-radius: 24px; background: radial-gradient(ellipse at 40% 0%, #fff 0%, transparent 65%), #edf3ff; overflow: hidden; }
.hero::after { content: ''; position: absolute; width: 350px; height: 350px; border: 1px solid #cbd9f3; border-radius: 50%; bottom: -260px; left: 28%; pointer-events: none; box-shadow: 0 0 0 30px #cbd9f333, 0 0 0 60px #cbd9f322; }
.hero-main, .hero-side { position: relative; z-index: 1; min-width: 0; }
.eyebrow { display: inline-flex; align-items: center; gap: 8px; margin: 0 0 24px; color: #5373ad; font-size: 12px; font-weight: 600; letter-spacing: .08em; }
.live-dot { display: inline-block; width: 6px; height: 6px; flex-shrink: 0; border-radius: 50%; background: var(--brand); box-shadow: 0 0 0 4px #3563e90c; }
.hero h1 { margin: 0 0 20px; font-size: clamp(30px, 3.2vw, 44px); line-height: 1.5; letter-spacing: -.045em; font-weight: 750; }
.hero h1 span { color: var(--home-accent); }
.lead { margin: 0 0 28px; font-size: 14px; line-height: 1.95; color: var(--text-sub); }
.hero-actions { display: flex; gap: 12px; flex-wrap: wrap; }
.hero-actions :deep(.el-button) { height: 44px; padding: 0 20px; margin: 0; font-weight: 600; }
.hero-actions :deep(.el-icon) { margin-left: 10px; }
.hero-trust { display: flex; flex-wrap: wrap; gap: 18px; margin-top: 30px; font-size: 11px; color: #6e7e99; }
.hero-trust span::before { content: '✓'; margin-right: 6px; color: #5a7bb0; }
.welcome { font-size: 12px; color: var(--text-sub); margin-bottom: 0; }
.hero-side { display: grid; gap: 22px; padding: 24px; border: 1px solid #fff; border-radius: 18px; background: #ffffffd9; box-shadow: 0 12px 36px #284d8010; }
.side-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 16px; }
.side-title { display: inline-flex; align-items: center; gap: 8px; font-size: 13px; font-weight: 650; }
.side-title .el-icon { color: #b78b49; font-size: 17px; }
.side-more, .side-note { font-size: 11px; color: var(--text-sub); }
.side-more:hover { color: var(--brand); }
.side-empty { margin: 0; padding: 14px 12px; background: var(--surface-soft); border-radius: 8px; font-size: 12px; line-height: 1.8; color: var(--text-sub); }
.side-block + .side-block { padding-top: 20px; border-top: 1px solid var(--border-soft); }
.rank-list, .up-list { list-style: none; margin: 0; padding: 0; }
.rank-link { display: flex; align-items: center; gap: 10px; padding: 8px 0; border-radius: 6px; }
.rank-link:hover { background: var(--surface-soft); }
.rank-position { font-size: 12px; color: #b28b56; font-family: ui-monospace, monospace; font-weight: 700; }
.rank-info { flex: 1; min-width: 0; }
.rank-line { display: flex; align-items: baseline; justify-content: space-between; gap: 8px; }
.rank-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 13px; font-weight: 500; }
.rank-score { flex-shrink: 0; font-size: 12px; font-weight: 600; color: var(--brand); }
.rank-bar { height: 3px; margin-top: 6px; background: #edf1f8; border-radius: 3px; overflow: hidden; }
.rank-bar i { display: block; height: 100%; background: #91acf1; border-radius: inherit; }
.up-row { display: flex; align-items: center; gap: 9px; padding: 7px 0; }
.type-dot { width: 6px; height: 6px; flex-shrink: 0; border-radius: 50%; background: var(--brand); }
.type-dot[data-code='DLT'] { background: #467ab2; }
.type-dot[data-code='FC3D'] { background: #c47745; }
.type-dot[data-code='PL3'] { background: #8b68b0; }
.type-dot[data-code='KL8'] { background: #ad9041; }
.up-info { display: flex; flex: 1; min-width: 0; flex-direction: column; gap: 4px; }
.up-name { font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.up-name i { margin-left: 4px; font-style: normal; font-size: 11px; color: var(--text-sub); }
.up-sub { font-size: 10px; color: var(--text-sub); }
.up-time { flex-shrink: 0; font-size: 11px; color: var(--brand); font-variant-numeric: tabular-nums; }
.feature-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; margin-top: 20px; }
.feature-card { display: flex; align-items: center; gap: 14px; padding: 22px; border: 1px solid var(--border-soft); border-radius: 14px; background: var(--surface); transition: border-color .2s, transform .2s, box-shadow .2s; }
.feature-icon { display: grid; place-items: center; flex-shrink: 0; width: 44px; height: 44px; border-radius: 13px; background: #eef3ff; color: #5273c9; font-size: 22px; }
.feature-card:nth-child(2) .feature-icon { color: #5b7c77; background: #edf4f0; }
.feature-card:nth-child(3) .feature-icon { color: #6a7f9b; background: #eef2f8; }
.feature-card h3 { margin: 0 0 6px; font-size: 14px; font-weight: 650; }
.feature-card p { margin: 0; color: var(--text-sub); font-size: 11px; line-height: 1.7; }
.feature-arrow { margin-left: auto; flex-shrink: 0; color: #9aa9bf; font-size: 14px; }
.section { margin-top: 36px; }
.section-head { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 18px; }
.head-left { display: flex; align-items: center; gap: 14px; flex-wrap: wrap; }
.section-head h2 { margin: 0; font-size: 19px; font-weight: 700; letter-spacing: -.025em; }
.section-head h2::before { content: ''; display: inline-block; vertical-align: -1px; width: 3px; height: 16px; margin-right: 10px; border-radius: 2px; background: var(--brand); }
.head-note { color: var(--text-sub); font-size: 11px; }
.more-link { flex-shrink: 0; font-size: 12px; color: var(--text-sub); transition: color .2s; }
.more-link:hover { color: var(--brand); }
.draw-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 310px), 1fr)); gap: 16px; }
.draw-card, .pred-card { display: block; min-width: 0; padding: 22px; border: 1px solid var(--border-soft); border-radius: 14px; background: var(--surface); transition: border-color .2s, box-shadow .2s, transform .2s; }
.draw-card:hover, .pred-card:hover, .feature-card:hover { border-color: #b9cbee; box-shadow: 0 8px 24px #294b800a; transform: translateY(-3px); }
.draw-head { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 18px; }
.draw-name { font-size: 15px; font-weight: 650; }
.draw-issue, .draw-time { color: var(--text-sub); font-size: 11px; font-variant-numeric: tabular-nums; }
.draw-body { min-height: 36px; }
.draw-empty { color: var(--text-sub); font-size: 13px; }
.draw-stats { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 8px; margin-top: 18px; padding: 12px; background: var(--surface-soft); border-radius: 10px; }
.stat { display: flex; flex-direction: column; gap: 6px; min-width: 0; }
.stat span { color: var(--text-sub); font-size: 11px; }
.stat b { font-size: 13px; font-weight: 600; font-variant-numeric: tabular-nums; }
.draw-foot { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; margin-top: 14px; font-size: 11px; }
.prize-line { color: var(--gold); font-weight: 600; }
.prize-line.muted { font-weight: 400; color: var(--text-sub); }
.pred-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.pred-card { display: flex; flex-direction: column; cursor: pointer; }
.pred-top { display: flex; align-items: center; gap: 10px; margin-bottom: 14px; color: var(--text-sub); font-size: 12px; }
.pred-type { color: var(--brand); font-weight: 600; }
.pred-free, .pred-price { margin-left: auto; padding: 3px 8px; background: var(--surface-soft); border-radius: 5px; font-weight: 600; color: var(--brand); }
.pred-free { color: var(--hit); background: #edf6f0; }
.pred-title { margin: 0 0 10px; font-size: 16px; line-height: 1.6; font-weight: 650; }
.pred-summary { flex: 1; margin: 0 0 16px; color: var(--text-sub); font-size: 13px; line-height: 1.8; }
.pred-title, .pred-summary { display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.pred-tags { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 16px; }
.pred-tag { display: inline-flex; align-items: center; gap: 6px; padding: 3px 8px; border-radius: 5px; background: var(--surface-soft); color: var(--text-sub); font-size: 11px; }
.pred-tag em { font-style: normal; white-space: nowrap; }
.pred-foot { display: flex; align-items: center; flex-wrap: wrap; gap: 6px; padding-top: 14px; border-top: 1px solid var(--border-soft); font-size: 11px; color: var(--text-sub); }
.pred-creator { font-weight: 600; color: var(--text-main); }
.sep { color: #ccd6e5; }
.pred-views { margin-left: auto; }
.empty-tip { padding: 32px 24px; border: 1px solid var(--border-soft); border-radius: 14px; background: var(--surface); text-align: center; }
.draw-placeholder { grid-column: 1 / -1; }
.empty-icon { font-size: 26px; color: #819ac1; padding: 12px; box-sizing: content-box; background: var(--surface-soft); border-radius: 16px; margin-bottom: 14px; }
.empty-title { margin: 0 0 8px; font-size: 14px; font-weight: 600; }
.empty-desc { margin: 0 0 18px; color: var(--text-sub); font-size: 12px; line-height: 1.8; }
.empty-actions { display: flex; justify-content: center; flex-wrap: wrap; gap: 10px; }
.empty-actions :deep(.el-button) { margin: 0; }
@media (max-width: 1100px) {
  .hero { gap: 28px; padding: 32px; }
  .hero h1 { font-size: 34px; }
  .feature-card { padding: 18px; gap: 10px; }
  .feature-arrow { display: none; }
}
@media (max-width: 800px) {
  .hero { grid-template-columns: 1fr; }
  .hero-side { grid-template-columns: 1fr 1fr; gap: 20px; }
  .side-block + .side-block { padding: 0 0 0 20px; border-top: 0; border-left: 1px solid var(--border-soft); }
  .feature-grid { gap: 10px; }
  .feature-card { align-items: flex-start; flex-direction: column; }
  .head-note { display: none; }
}
@media (max-width: 540px) {
  .hero { padding: 28px 22px; border-radius: 18px; gap: 28px; }
  .hero h1 { font-size: 29px; }
  .eyebrow { font-size: 11px; margin-bottom: 18px; }
  .lead { font-size: 13px; }
  .desktop-break { display: none; }
  .hero-trust { gap: 12px; font-size: 10px; margin-top: 22px; }
  .hero-side { grid-template-columns: 1fr; padding: 20px; }
  .side-block + .side-block { border-left: 0; border-top: 1px solid var(--border-soft); padding: 18px 0 0; }
  .hero-actions { gap: 8px; }
  .hero-actions :deep(.el-button) { padding: 0 13px; font-size: 12px; }
  .feature-grid { grid-template-columns: 1fr; }
  .feature-card { flex-direction: row; align-items: center; padding: 16px; }
  .feature-arrow { display: block; }
  .section { margin-top: 28px; }
  .section-head h2 { font-size: 17px; }
  .pred-grid { grid-template-columns: 1fr; }
  .draw-card, .pred-card { padding: 18px; }
}
</style>
