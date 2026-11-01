<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { creatorApi } from '@/api/market'
import { predictionApi } from '@/api/prediction'
import { useAuthStore } from '@/stores/auth'
import type { PredictionListItem } from '@/types/lottery'
import { typeName } from '@/utils/lottery'

const route = useRoute()
const auth = useAuthStore()
const id = Number(route.params.id)

const home = ref<Record<string, any> | null>(null)
const list = ref<PredictionListItem[]>([])
const loading = ref(true)
const following = ref(false)

async function load() {
  loading.value = true
  try {
    const [h, p] = await Promise.all([
      creatorApi.home(id),
      predictionApi.list({ pageNum: 1, pageSize: 10 }),
    ])
    home.value = h
    following.value = Boolean(h.followed)
    list.value = p.list.filter((item) => item.creator.id === id)
  } finally {
    loading.value = false
  }
}

async function toggleFollow() {
  if (!auth.isLoggedIn) {
    ElMessage.warning('请先登录')
    return
  }
  if (following.value) {
    await creatorApi.unfollow(id)
    following.value = false
  } else {
    await creatorApi.follow(id)
    following.value = true
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="page ui-page view-creator-home">
    <template v-if="home">
      <el-card shadow="never" class="profile">
        <div class="top">
          <el-avatar :size="64" :src="home.avatar || undefined">{{ home.penName?.slice(0, 1) }}</el-avatar>
          <div class="info">
            <h1>{{ home.penName }}</h1>
            <p class="tags">{{ home.tags || '暂未填写擅长标签' }}</p>
            <p class="intro">{{ home.intro || '这个人很低调，什么都没写。' }}</p>
          </div>
          <el-button :type="following ? 'default' : 'primary'" @click="toggleFollow">
            {{ following ? '已关注' : '关注' }}
          </el-button>
        </div>
        <div class="stats">
          <div><strong>{{ home.accuracyScore?.toFixed(1) }}</strong><span>准确度</span></div>
          <div><strong>{{ home.sampleSize }}</strong><span>样本期数</span></div>
          <div><strong>{{ home.currentHitStreak }}</strong><span>当前连红</span></div>
          <div><strong>{{ home.favCount }}</strong><span>关注</span></div>
          <div><strong>{{ home.totalBuyer }}</strong><span>累计解锁</span></div>
        </div>
      </el-card>

      <h2 class="section">TA 的预测</h2>
      <el-empty v-if="list.length === 0" description="暂无在售预测" />
      <div v-for="item in list" :key="item.id" class="row card-hover" tabindex="0" role="link" @keydown.enter="$router.push(`/prediction/${item.id}`)" @click="$router.push(`/prediction/${item.id}`)">
        <div class="row-main">
          <div class="row-tags">
            <span class="type-tag" :data-code="item.typeCode">{{ typeName(item.typeCode) }}</span>
            <span class="row-issue">{{ item.issueNo }} 期 · {{ item.playTypeName }}</span>
            <span v-if="item.isHit === 1" class="hit-badge">命中 {{ ((item.hitRate ?? 0) * 100).toFixed(0) }}%</span>
          </div>
          <h3>{{ item.title }}</h3>
          <p>{{ item.summary }}</p>
        </div>
        <div class="row-side">
          <div class="price">{{ item.isFree ? '免费' : `${item.pricePoints} 积分` }}</div>
          <div class="meta">{{ item.buyerCount }} 人解锁 · {{ item.viewCount }} 浏览</div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.profile { border-radius: 12px; margin-bottom: 20px; }
.top { display: flex; gap: 16px; align-items: flex-start; }
.info { flex: 1; }
.info h1 { margin: 0 0 6px; font-size: 22px; }
.tags { margin: 0 0 6px; font-size: 12px; color: var(--el-color-primary); }
.intro { margin: 0; font-size: 13px; color: var(--text-sub); line-height: 1.6; }
.stats { display: grid; grid-template-columns: repeat(5, 1fr); gap: 10px; margin-top: 18px; }
.stats div { text-align: center; background: var(--el-fill-color-lighter); border-radius: 10px; padding: 10px 6px; }
.stats strong { display: block; font-size: 18px; color: var(--el-color-primary); }
.stats span { font-size: 12px; color: var(--text-sub); }
.section { font-size: 16px; margin: 8px 0 12px; }
.row {
  display: flex; justify-content: space-between; gap: 16px; background: #fff;
  border: 1px solid var(--border-soft); border-radius: 12px; padding: 14px 16px;
  margin-bottom: 10px; cursor: pointer; transition: transform .2s, box-shadow .2s;
}
.row:hover { transform: translateY(-1px); box-shadow: 0 8px 20px rgba(60,40,30,.07); }
.row-tags { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.row-issue { font-size: 12px; color: var(--text-sub); }
.row h3 { margin: 0 0 6px; font-size: 15px; }
.row p { margin: 0; font-size: 12px; color: var(--text-sub); }
.row-side { text-align: right; }
.price { color: var(--el-color-danger); font-weight: 600; }
.meta { font-size: 11px; color: var(--text-sub); }
</style>
