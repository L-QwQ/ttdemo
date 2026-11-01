<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { rankApi } from '@/api/market'
import { lotteryApi } from '@/api/lottery'
import type { LotteryType, RankData } from '@/types/lottery'
import PageHeader from '@/components/common/PageHeader.vue'

const rankType = ref('ACCURACY')
const typeCode = ref('ALL')
const data = ref<RankData | null>(null)
const loading = ref(false)
const types = ref<LotteryType[]>([])

const tabs = [
  { value: 'ACCURACY', label: '准确率榜' },
  { value: 'HIT_STREAK', label: '连红榜' },
  { value: 'INCOME', label: '人气榜' },
  { value: 'NEWCOMER', label: '新锐榜' },
]

async function load() {
  loading.value = true
  try {
    data.value = await rankApi.list({ rankType: rankType.value, typeCode: typeCode.value, limit: 50 })
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  types.value = await lotteryApi.types()
  await load()
})
</script>

<template>
  <div class="page ui-page view-rank">
    <PageHeader title="创作者榜单" sub="榜单每日 01:00 生成快照，不实时变动">
      <el-select v-model="typeCode" style="width: 140px" @change="load">
        <el-option label="全站" value="ALL" />
        <el-option v-for="t in types" :key="t.code" :label="t.name" :value="t.code" />
      </el-select>
    </PageHeader>

    <el-radio-group v-model="rankType" class="tabs" @change="load">
      <el-radio-button v-for="t in tabs" :key="t.value" :value="t.value">{{ t.label }}</el-radio-button>
    </el-radio-group>

    <el-alert v-if="data" type="info" :closable="false" class="note" :title="data.calcNote" />

    <div v-loading="loading" class="list">
      <el-empty v-if="!loading && (data?.list?.length ?? 0) === 0" description="暂无上榜创作者（样本不足或今日快照未生成）" />
      <div v-for="row in data?.list || []" :key="row.creatorId" class="row card-hover" tabindex="0" role="link" @keydown.enter="$router.push(`/creator/${row.creatorId}`)" @click="$router.push(`/creator/${row.creatorId}`)">
        <div class="rank" :class="{ top: row.rankNo <= 3 }">{{ row.rankNo }}</div>
        <el-avatar :size="40" :src="row.avatar || undefined">{{ row.penName?.slice(0, 1) }}</el-avatar>
        <div class="info">
          <div class="name">{{ row.penName }}</div>
          <div class="meta">
            {{ ['', '普通', '优质', '金牌'][row.level] || '普通' }}创作者 ·
            {{ row.sampleSize }} 期样本 · 连红 {{ row.currentHitStreak }} · {{ row.totalBuyer }} 人解锁
          </div>
        </div>
        <div class="metric">{{ row.metricText }}</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 14px; }
.head h1 { margin: 0 0 6px; font-size: 22px; }
.sub { margin: 0; font-size: 13px; color: var(--text-sub); }
.tabs { margin-bottom: 12px; }
.note { margin-bottom: 14px; }
.list { min-height: 160px; display: flex; flex-direction: column; gap: 10px; }
.row {
  display: flex; align-items: center; gap: 14px; background: #fff;
  border: 1px solid var(--border-soft); border-radius: 12px; padding: 12px 16px;
  cursor: pointer;
}
.rank { width: 32px; text-align: center; font-weight: 700; font-size: 16px; color: var(--text-sub); }
.rank.top { color: var(--el-color-danger); font-size: 20px; }
.info { flex: 1; min-width: 0; }
.name { font-weight: 600; font-size: 15px; }
.meta { font-size: 12px; color: var(--text-sub); margin-top: 2px; }
.metric { font-weight: 700; color: var(--el-color-primary); font-size: 16px; white-space: nowrap; }
</style>
