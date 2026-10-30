<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { predictionApi, type PredictionQuery } from '@/api/prediction'
import { lotteryApi } from '@/api/lottery'
import type { LotteryType, PredictionListItem } from '@/types/lottery'
import { formatDateTime } from '@/utils/format'
import { typeName, remainText } from '@/utils/lottery'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const list = ref<PredictionListItem[]>([])
const total = ref(0)
const types = ref<LotteryType[]>([])

const query = ref<PredictionQuery>({
  typeCode: (route.query.typeCode as string) || undefined,
  playType: undefined,
  onlyFree: false,
  sortBy: 'hot',
  pageNum: 1,
  pageSize: 10,
})

const playTypes = [
  { value: '', label: '全部玩法' },
  { value: 'SINGLE', label: '单式推荐' },
  { value: 'MULTI', label: '复式推荐' },
  { value: 'DAN', label: '胆码推荐' },
  { value: 'KILL', label: '杀号推荐' },
  { value: 'FORM', label: '形态预测' },
  { value: 'AI_PACK', label: '综合方案包' },
]

const sortOptions = [
  { value: 'hot', label: '热度优先' },
  { value: 'new', label: '最新发布' },
  { value: 'priceAsc', label: '价格从低到高' },
  { value: 'priceDesc', label: '价格从高到低' },
]

async function load() {
  loading.value = true
  try {
    const page = await predictionApi.list(query.value)
    list.value = page.list
    total.value = page.total
  } finally {
    loading.value = false
  }
}

function reset() {
  query.value = { playType: undefined, onlyFree: false, sortBy: 'hot', pageNum: 1, pageSize: 10 }
  load()
}

function open(item: PredictionListItem) {
  router.push(`/prediction/${item.id}`)
}

const filteredTypes = computed(() => types.value.filter((t) => t.status === 1))

onMounted(async () => {
  types.value = await lotteryApi.types()
  await load()
})
</script>

<template>
  <div class="page">
    <header class="page-head">
      <div>
        <h1>预测广场</h1>
        <p class="sub">按创作者历史准确度定价，付费内容为个人分析观点，不构成投注建议</p>
      </div>
      <el-button type="primary" @click="router.push('/publish')">发布预测</el-button>
    </header>

    <el-card shadow="never" class="filter">
      <div class="filter-row">
        <el-radio-group v-model="query.typeCode" @change="(query.pageNum = 1); load()">
          <el-radio-button :value="undefined">全部彩种</el-radio-button>
          <el-radio-button v-for="t in filteredTypes" :key="t.code" :value="t.code">{{ t.name }}</el-radio-button>
        </el-radio-group>

        <el-select v-model="query.playType" placeholder="全部玩法" clearable style="width: 150px" @change="(query.pageNum = 1); load()">
          <el-option v-for="p in playTypes" :key="p.value" :label="p.label" :value="p.value || undefined" />
        </el-select>

        <el-select v-model="query.sortBy" style="width: 150px" @change="load">
          <el-option v-for="s in sortOptions" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>

        <el-checkbox v-model="query.onlyFree" @change="(query.pageNum = 1); load()">只看免费</el-checkbox>
        <el-button text @click="reset">重置</el-button>
      </div>
    </el-card>

    <div v-loading="loading" class="list">
      <el-empty v-if="!loading && list.length === 0" description="暂无符合条件的预测（仅展示关联官方期号的预测）">
        <el-button type="primary" plain @click="router.push('/publish')">发布一条预测</el-button>
      </el-empty>

      <article v-for="item in list" :key="item.id" class="card" @click="open(item)">
        <div class="card-top">
          <div class="tags">
            <span class="type-tag" :data-code="item.typeCode">{{ typeName(item.typeCode) }}</span>
            <el-tag size="small" type="info" effect="plain">{{ item.issueNo }} 期</el-tag>
            <el-tag size="small" effect="plain">{{ item.playTypeName }}</el-tag>
            <el-tag v-if="item.isFree" size="small" type="success">免费</el-tag>
          </div>
          <span class="countdown">{{ remainText(item.saleEndTime ? Math.floor((new Date(item.saleEndTime).getTime() - Date.now()) / 1000) : 0) }}</span>
        </div>

        <h3 class="title">{{ item.title }}</h3>
        <p class="summary">{{ item.summary }}</p>

        <div class="chips">
          <span v-for="(f, i) in item.freeItems" :key="i" class="chip">免费可见：{{ f.label }}</span>
        </div>

        <div class="card-foot">
          <div class="creator">
            <el-avatar :size="28" :src="item.creator.avatar || undefined">
              {{ item.creator.penName?.slice(0, 1) }}
            </el-avatar>
            <span class="name">{{ item.creator.penName }}</span>
            <span class="score">准确度 {{ item.creator.accuracyScore?.toFixed(1) }} · {{ item.creator.sampleSize }} 期样本</span>
          </div>

          <div class="price-box">
            <div class="price">
              <template v-if="item.isFree">免费</template>
              <template v-else>{{ item.pricePoints }} <small>积分</small></template>
            </div>
            <div v-if="!item.isFree" class="breakdown">{{ item.priceBreakdown?.text }}</div>
          </div>
        </div>

        <div class="meta">
          <span>{{ item.viewCount }} 浏览</span>
          <span>{{ item.buyerCount }} 人解锁</span>
          <span>{{ formatDateTime(item.saleEndTime) }} 停售</span>
          <el-tag v-if="item.isPurchased" size="small" type="success" effect="dark">已解锁</el-tag>
          <el-tag v-else-if="item.isHit === 1" size="small" type="danger" effect="plain">
            命中 {{ ((item.hitRate ?? 0) * 100).toFixed(0) }}%
          </el-tag>
        </div>
      </article>
    </div>

    <el-pagination
      v-if="total > 0"
      v-model:current-page="query.pageNum"
      v-model:page-size="query.pageSize"
      :total="total"
      layout="prev, pager, next, total"
      class="pager"
      @current-change="load"
    />
  </div>
</template>

<style scoped>
.page { max-width: 1100px; margin: 0 auto; padding: 24px 16px 64px; }
.page-head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 16px; gap: 16px; }
.page-head h1 { margin: 0 0 6px; font-size: 22px; }
.sub { margin: 0; color: var(--el-text-color-secondary); font-size: 13px; }
.filter { margin-bottom: 16px; border-radius: 10px; }
.filter-row { display: flex; flex-wrap: wrap; gap: 12px; align-items: center; }
.list { display: flex; flex-direction: column; gap: 14px; min-height: 200px; }
.card {
  background: #fff; border: 1px solid var(--el-border-color-lighter); border-radius: 12px;
  padding: 16px 18px; cursor: pointer; transition: box-shadow .2s, transform .2s;
}
.card:hover { box-shadow: 0 6px 20px rgba(0,0,0,.07); transform: translateY(-1px); }
.card-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.tags { display: flex; gap: 6px; }
.countdown { font-size: 12px; color: var(--el-color-warning); }
.title { margin: 4px 0 8px; font-size: 17px; line-height: 1.4; }
.summary { margin: 0 0 10px; color: var(--el-text-color-regular); font-size: 13px; line-height: 1.6; }
.chips { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 12px; }
.chip { font-size: 12px; color: var(--el-color-primary); background: var(--el-color-primary-light-9); padding: 2px 8px; border-radius: 10px; }
.card-foot { display: flex; justify-content: space-between; align-items: center; border-top: 1px dashed var(--el-border-color-lighter); padding-top: 10px; }
.creator { display: flex; align-items: center; gap: 8px; }
.name { font-size: 13px; font-weight: 500; }
.score { font-size: 12px; color: var(--el-text-color-secondary); }
.price-box { text-align: right; }
.price { color: var(--el-color-danger); font-size: 18px; font-weight: 600; }
.price small { font-size: 12px; }
.breakdown { font-size: 11px; color: var(--el-text-color-placeholder); }
.meta { display: flex; gap: 14px; align-items: center; margin-top: 8px; font-size: 12px; color: var(--el-text-color-placeholder); }
.pager { margin-top: 20px; justify-content: center; }
</style>
