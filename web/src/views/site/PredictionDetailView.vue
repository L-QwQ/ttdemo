<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Lock } from '@element-plus/icons-vue'
import { predictionApi, newIdempotentKey } from '@/api/prediction'
import { pointApi } from '@/api/point'
import { reportApi } from '@/api/growth'
import { useAuthStore } from '@/stores/auth'
import type { PredictionDetail } from '@/types/lottery'
import { formatDateTime } from '@/utils/format'
import { typeName } from '@/utils/lottery'
import { lotteryApi } from '@/api/lottery'
import type { Issue } from '@/types/lottery'
import BallGroup from '@/components/lottery/BallGroup.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const id = Number(route.params.id)
const detail = ref<PredictionDetail | null>(null)

/** 举报违规内容：提交后由后台核查，达到阈值的预测会自动下架 */
async function reportContent() {
  if (!auth.isLoggedIn) {
    ElMessage.warning('请先登录后再举报')
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  if (!detail.value) return
  try {
    const { value } = await ElMessageBox.prompt(
      '请填写举报原因（如：承诺中奖、诱导赌博、侵犯版权、辱骂）',
      '举报该内容',
      {
        confirmButtonText: '提交举报',
        cancelButtonText: '取消',
        inputPlaceholder: '承诺中奖 / 诱导赌博 / 侵权 / 辱骂',
        inputValidator: (v) => (v && v.trim().length >= 4 ? true : '请至少填写 4 个字'),
      },
    )
    await reportApi.submit({
      targetType: 1,
      targetId: detail.value.id,
      reasonType: 1,
      content: value,
    })
    ElMessage.success('举报已提交，平台会尽快核查处理')
  } catch {
    // 取消
  }
}
const drawnIssue = ref<Issue | null>(null)
const loading = ref(true)
const buying = ref(false)
const content = ref<string | null>(null)
const watermark = ref('')
const remain = ref(0)
const showPriceTip = ref(false)

let timer: number | undefined

const canRead = computed(() => !!content.value)
const price = computed(() => detail.value?.pricePoints ?? 0)

function startCountdown() {
  timer = window.setInterval(() => {
    if (!detail.value?.saleEndTime) return
    const diff = Math.floor((new Date(detail.value.saleEndTime).getTime() - Date.now()) / 1000)
    remain.value = Math.max(0, diff)
  }, 1000)
}

const countdownText = computed(() => {
  if (remain.value <= 0) return '已停售'
  const d = Math.floor(remain.value / 86400)
  const h = Math.floor((remain.value % 86400) / 3600)
  const m = Math.floor((remain.value % 3600) / 60)
  const s = remain.value % 60
  if (d > 0) return `${d} 天 ${h} 小时 ${m} 分`
  if (h > 0) return `${h} 小时 ${m} 分 ${s} 秒`
  return `${m} 分 ${s} 秒`
})

const urgent = computed(() => remain.value > 0 && remain.value < 600)

async function load() {
  loading.value = true
  try {
    detail.value = await predictionApi.detail(id)
    if (detail.value.contentHtml) {
      content.value = detail.value.contentHtml
    }
    if (detail.value.saleEndTime) {
      remain.value = Math.max(0, Math.floor((new Date(detail.value.saleEndTime).getTime() - Date.now()) / 1000))
    }
    // 已开奖：拉取该彩种最近开奖期次，匹配本期展示官方号码
    if (detail.value.hitInfo) {
      try {
        const list = await lotteryApi.issues({ typeCode: detail.value.typeCode, drawStatus: 1, limit: 30 })
        drawnIssue.value = list.find((i) => i.issueNo === detail.value!.issueNo) ?? null
      } catch {
        /* 开奖数据加载失败不影响主流程 */
      }
    }
  } finally {
    loading.value = false
  }
}

async function unlock() {
  if (!auth.isLoggedIn) {
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  if (detail.value?.isOwner) {
    ElMessage.info('这是你自己发布的预测')
    return
  }
  try {
    await ElMessageBox.confirm(
      `将消耗 ${price.value} 积分解锁本文，解锁后不可退（预测被撤回/下架时平台自动全额退款）。`,
      '确认解锁',
      { confirmButtonText: '确认解锁', cancelButtonText: '再想想' },
    )
  } catch {
    return
  }
  buying.value = true
  try {
    const key = newIdempotentKey()
    const result = await predictionApi.unlock(id, key)
    ElMessage.success(`解锁成功，余额 ${result.balanceAfter} 积分`)
    await loadContent()
    await load()
  } finally {
    buying.value = false
  }
}

async function loadContent() {
  const data = await predictionApi.content(id)
  content.value = data.contentHtml
  watermark.value = data.watermark
}

const watermarkTiles = computed(() => Array.from({ length: 36 }, (_, i) => i))

onMounted(async () => {
  await load()
  startCountdown()
  // 已解锁但详情没带正文时，单独拉一次（详情会把 contentHtml 置空用于列表缓存）
  if (detail.value?.isPurchased && !content.value) {
    try {
      await loadContent()
    } catch {
      /* 忽略：正文接口失败时保持锁定态 */
    }
  }
})

onBeforeUnmount(() => {
  if (timer) window.clearInterval(timer)
})
</script>

<template>
  <div v-loading="loading" class="page">
    <template v-if="detail">
      <div class="head">
        <div class="head-left">
          <div class="tags">
            <span class="type-tag" :data-code="detail.typeCode">{{ typeName(detail.typeCode) }}</span>
            <el-tag size="small" type="info" effect="plain">{{ detail.issueNo }} 期</el-tag>
            <el-tag size="small" effect="plain">{{ detail.playTypeName }}</el-tag>
            <el-tag size="small" type="info" effect="plain">{{ detail.statusName }}</el-tag>
          </div>
          <h1>{{ detail.title }}</h1>
          <p class="meta">
            {{ detail.viewCount }} 浏览 · {{ detail.buyerCount }} 人解锁 ·
            发布于 {{ formatDateTime(detail.createdAt) }}
          </p>
        </div>
        <div class="countdown" :class="{ urgent }">
          <span class="label">距停售</span>
          <strong>{{ countdownText }}</strong>
        </div>
      </div>

      <el-alert
        v-if="detail.hitInfo"
        :type="detail.hitInfo.isHit === 1 ? 'error' : 'info'"
        :closable="false"
        show-icon
        class="hit-bar"
      >
        <template #title>
          已开奖：命中 {{ detail.hitInfo.hitCount }}/{{ detail.hitInfo.itemCount }} 项，
          加权命中率 {{ (detail.hitInfo.hitRate * 100).toFixed(1) }}%
          <span v-if="detail.hitInfo.isHit === 1" class="hit-flag">综合判定：命中</span>
        </template>
      </el-alert>

      <div class="layout">
        <main class="main">
          <el-card shadow="never" class="block">
            <template #header><span class="block-title">摘要（免费）</span></template>
            <p class="summary">{{ detail.summary }}</p>
            <div class="chips">
              <span v-for="(f, i) in detail.freeItems" :key="i" class="chip">{{ f.label }}</span>
            </div>
          </el-card>

          <el-card v-if="!canRead" shadow="never" class="block locked">
            <div class="locked-inner">
              <div class="blur-lines">
                <div v-for="i in 6" :key="i" class="blur-line" />
              </div>
              <div class="lock-mask">
                <el-icon :size="26"><Lock /></el-icon>
                <p class="lock-title">付费正文已锁定</p>
                <p class="lock-hint">{{ detail.lockedHint }}</p>
                <el-button type="primary" size="large" :loading="buying" @click="unlock">
                  {{ detail.isFree ? '免费查看' : `${price} 积分解锁` }}
                </el-button>
                <el-button text size="small" @click="showPriceTip = !showPriceTip">查看定价依据</el-button>
                <div v-if="showPriceTip" class="price-tip">
                  {{ detail.priceBreakdown.text }} = {{ price }} 积分
                  <div class="tip-note">准确度越高，系统允许的最高定价越高；创作者只能在系统区间内下浮</div>
                </div>
              </div>
            </div>
          </el-card>

          <el-card v-else shadow="never" class="block content-block">
            <template #header>
              <div class="content-head">
                <span class="block-title">付费正文</span>
                <el-tag size="small" type="success" effect="plain">
                  {{ detail.isOwner ? '作者本人' : '已解锁' }}
                </el-tag>
              </div>
            </template>
            <div class="watermark-wrap">
              <div class="watermark">
                <span v-for="i in watermarkTiles" :key="i">{{ watermark }}</span>
              </div>
              <div class="content" v-html="content" />
            </div>
          </el-card>

          <el-card v-if="drawnIssue" shadow="never" class="block">
            <template #header>
              <div class="content-head">
                <span class="block-title">本期官方开奖号码</span>
                <span class="draw-source">来源：{{ drawnIssue.dataSource === 1 ? '官方接口' : '人工录入' }} · {{ formatDateTime(drawnIssue.drawTime) }}</span>
              </div>
            </template>
            <div class="draw-numbers">
              <BallGroup
                :type-code="detail.typeCode"
                :red="drawnIssue.redNumbers"
                :blue="drawnIssue.blueNumbers"
                size="lg"
              />
              <span v-if="drawnIssue.extra" class="draw-extra">{{ drawnIssue.extra }}</span>
            </div>
          </el-card>

          <el-card v-if="detail.hitInfo" shadow="never" class="block">
            <template #header><span class="block-title">逐项命中明细</span></template>
            <el-table :data="detail.hitInfo.items" size="small">
              <el-table-column prop="itemLabel" label="预测项" min-width="140" />
              <el-table-column prop="itemValue" label="预测值" min-width="160" />
              <el-table-column label="结果" width="90">
                <template #default="{ row }">
                  <el-tag v-if="row.hitResult === 1" type="danger" size="small">命中</el-tag>
                  <el-tag v-else type="info" size="small">未中</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="hitDetail" label="判定明细" min-width="240" show-overflow-tooltip />
            </el-table>
          </el-card>
        </main>

        <aside class="side">
          <el-card shadow="never" class="creator-card">
            <div class="creator-top">
              <el-avatar :size="52" :src="detail.creator.avatar || undefined">
                {{ detail.creator.penName?.slice(0, 1) }}
              </el-avatar>
              <div>
                <div class="pen-name">{{ detail.creator.penName }}</div>
                <div class="level">{{ ['', '普通', '优质', '金牌'][detail.creator.level] || '普通' }}创作者</div>
              </div>
            </div>
            <div class="accuracy">
              <div class="acc-score">{{ detail.creator.accuracyScore?.toFixed(1) }}</div>
              <div class="acc-label">
                准确度评分<br />
                <span>{{ detail.creator.sampleSize }} 期样本 · 连红 {{ detail.creator.currentHitStreak }}</span>
              </div>
            </div>
            <div class="price-line">
              <span>本文定价</span>
              <strong v-if="detail.isFree">免费</strong>
              <strong v-else>{{ price }} 积分</strong>
            </div>
            <div class="formula">{{ detail.priceBreakdown.text }}</div>
            <el-button class="follow" @click="router.push(`/creator/${detail.creator.id}`)">查看创作者主页</el-button>
          </el-card>

          <el-card shadow="never" class="notice-card">
            <p class="notice-title">合规提示</p>
            <p class="notice-text">
              本平台交易的是创作者个人的开奖结果分析内容，不销售彩票、不提供投注服务、不承诺任何结果。
              积分仅用于站内消费，不可提现、不可转让。
            </p>
            <p class="report-line">
              发现内容违规？
              <el-button link type="danger" size="small" @click="reportContent">举报该内容</el-button>
            </p>
          </el-card>
        </aside>
      </div>
    </template>
  </div>
</template>

<style scoped>
.page { max-width: 1100px; margin: 0 auto; padding: 24px 16px 64px; min-height: 60vh; }
.head { display: flex; justify-content: space-between; gap: 20px; align-items: flex-start; margin-bottom: 14px; }
.tags { display: flex; gap: 6px; margin-bottom: 8px; }
.head h1 { margin: 0 0 8px; font-size: 24px; line-height: 1.35; }
.meta { margin: 0; font-size: 12px; color: var(--el-text-color-secondary); }
.countdown {
  flex: 0 0 auto; text-align: center; padding: 10px 16px; border-radius: 10px;
  background: var(--el-color-primary-light-9); color: var(--el-color-primary);
}
.countdown .label { display: block; font-size: 11px; opacity: .8; }
.countdown strong { font-size: 16px; }
.countdown.urgent { background: #fef0f0; color: #f56c6c; }
.hit-bar { margin-bottom: 14px; }
.hit-flag { margin-left: 10px; font-weight: 600; }
.layout { display: grid; grid-template-columns: minmax(0, 1fr) 300px; gap: 16px; }
.block { margin-bottom: 14px; border-radius: 12px; }
.block-title { font-weight: 600; font-size: 14px; }
.summary { margin: 0 0 10px; line-height: 1.7; color: var(--el-text-color-regular); }
.chips { display: flex; flex-wrap: wrap; gap: 8px; }
.chip { font-size: 12px; color: var(--el-color-primary); background: var(--el-color-primary-light-9); padding: 3px 10px; border-radius: 10px; }
.draw-source { font-size: 12px; color: var(--el-text-color-secondary); }
.draw-numbers { display: flex; align-items: center; gap: 14px; flex-wrap: wrap; }
.draw-extra { font-size: 13px; color: var(--gold); font-weight: 600; }
.locked-inner { position: relative; min-height: 260px; }
.blur-lines { filter: blur(6px); opacity: .55; }
.blur-line { height: 14px; background: var(--el-border-color-lighter); border-radius: 6px; margin-bottom: 12px; }
.blur-line:nth-child(2n) { width: 78%; }
.lock-mask {
  position: absolute; inset: 0; display: flex; flex-direction: column;
  align-items: center; justify-content: center; gap: 8px; text-align: center;
}
.lock-title { margin: 4px 0 0; font-size: 16px; font-weight: 600; }
.lock-hint { margin: 0 0 8px; font-size: 12px; color: var(--el-text-color-secondary); }
.price-tip {
  margin-top: 8px; font-size: 12px; color: var(--el-text-color-regular);
  background: var(--el-fill-color-light); padding: 8px 12px; border-radius: 8px; max-width: 320px;
}
.tip-note { margin-top: 4px; color: var(--el-text-color-secondary); font-size: 11px; }
.watermark-wrap { position: relative; overflow: hidden; }
.watermark {
  position: absolute; inset: 0; display: flex; flex-wrap: wrap; align-content: flex-start;
  pointer-events: none; user-select: none; opacity: .06; font-size: 13px; color: #000;
}
.watermark span { width: 20%; padding: 22px 4px; transform: rotate(-22deg); white-space: nowrap; }
.content { line-height: 1.85; color: var(--el-text-color-primary); position: relative; z-index: 1; }
.content-head { display: flex; justify-content: space-between; align-items: center; }
.side { display: flex; flex-direction: column; gap: 14px; }
.creator-card { border-radius: 12px; }
.creator-top { display: flex; gap: 10px; align-items: center; margin-bottom: 14px; }
.pen-name { font-size: 15px; font-weight: 600; }
.level { font-size: 12px; color: var(--el-text-color-secondary); }
.accuracy { display: flex; align-items: center; gap: 10px; padding: 10px; background: var(--el-fill-color-lighter); border-radius: 10px; }
.acc-score { font-size: 26px; font-weight: 700; color: var(--el-color-primary); }
.acc-label { font-size: 12px; color: var(--el-text-color-regular); line-height: 1.5; }
.acc-label span { color: var(--el-text-color-secondary); }
.price-line { display: flex; justify-content: space-between; margin-top: 14px; font-size: 13px; }
.price-line strong { color: var(--el-color-danger); font-size: 16px; }
.formula { font-size: 11px; color: var(--el-text-color-placeholder); margin-top: 4px; }
.follow { width: 100%; margin-top: 12px; }
.notice-card { border-radius: 12px; background: var(--el-fill-color-lighter); }
.notice-title { margin: 0 0 6px; font-size: 12px; font-weight: 600; }
.notice-text { margin: 0; font-size: 12px; line-height: 1.7; color: var(--el-text-color-secondary); }
.report-line { margin: 10px 0 0; font-size: 12px; color: var(--el-text-color-secondary); }
@media (max-width: 900px) {
  .layout { grid-template-columns: 1fr; }
  .head { flex-direction: column; }
}
</style>
