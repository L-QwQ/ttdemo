<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { creatorApi } from '@/api/market'
import { predictionApi } from '@/api/prediction'
import type { AccuracyInfo, CreatorDashboard, PredictionListItem } from '@/types/lottery'
import { formatDateTime } from '@/utils/format'

const router = useRouter()
const dashboard = ref<CreatorDashboard | null>(null)
const accuracy = ref<AccuracyInfo | null>(null)
const list = ref<PredictionListItem[]>([])
const total = ref(0)
const page = ref(1)
const status = ref<number | undefined>(undefined)
const loading = ref(false)

const statusOptions = [
  { value: undefined, label: '全部' },
  { value: 0, label: '草稿' },
  { value: 1, label: '待审核' },
  { value: 2, label: '已驳回' },
  { value: 3, label: '已上架' },
  { value: 4, label: '已锁定' },
  { value: 5, label: '已结算' },
  { value: 6, label: '已撤回' },
  { value: 7, label: '已下架' },
]

async function load() {
  loading.value = true
  try {
    const [d, a, p] = await Promise.all([
      creatorApi.dashboard(),
      creatorApi.accuracy(),
      predictionApi.mine({ pageNum: page.value, pageSize: 10, status: status.value }),
    ])
    dashboard.value = d
    accuracy.value = a
    list.value = p.list
    total.value = p.total
  } catch (e) {
    ElMessage.error('请先完成创作者认证')
    router.push('/creator/apply')
  } finally {
    loading.value = false
  }
}

async function submit(row: PredictionListItem) {
  await predictionApi.submit(row.id)
  ElMessage.success('已提交审核')
  await load()
}

async function withdraw(row: PredictionListItem) {
  try {
    await ElMessageBox.confirm(
      '撤回后将自动全额退还所有已购用户积分，并扣减 5 点信誉分。确认撤回？',
      '撤回预测',
      { type: 'warning', confirmButtonText: '确认撤回', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  await predictionApi.withdraw(row.id)
  ElMessage.success('已撤回并退款')
  await load()
}

async function remove(row: PredictionListItem) {
  try {
    await ElMessageBox.confirm('确认删除该草稿？', '删除草稿', { type: 'warning' })
  } catch {
    return
  }
  await predictionApi.remove(row.id)
  ElMessage.success('已删除')
  await load()
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="page ui-page view-creator-dashboard">
    <header class="head">
      <div>
        <h1>创作者看板</h1>
        <p class="sub">数据每期结算后更新，准确度按近 90 天已结算预测计算</p>
      </div>
      <div>
        <el-button @click="router.push('/creator/apply')">创作者资料</el-button>
        <el-button type="primary" @click="router.push('/publish')">发布预测</el-button>
      </div>
    </header>

    <div class="cards">
      <div class="stat"><span class="label">累计发布</span><strong>{{ dashboard?.totalPredictions ?? 0 }}</strong></div>
      <div class="stat"><span class="label">已结算</span><strong>{{ dashboard?.settledCount ?? 0 }}</strong></div>
      <div class="stat"><span class="label">命中次数</span><strong>{{ dashboard?.hitCount ?? 0 }}</strong></div>
      <div class="stat"><span class="label">命中率</span><strong>{{ ((dashboard?.hitRate ?? 0) * 100).toFixed(1) }}%</strong></div>
      <div class="stat"><span class="label">累计收益</span><strong class="primary">{{ dashboard?.totalIncome ?? 0 }}</strong></div>
      <div class="stat"><span class="label">解锁人数</span><strong>{{ dashboard?.totalBuyer ?? 0 }}</strong></div>
    </div>

    <el-card v-if="accuracy" shadow="never" class="block">
      <template #header><span class="t">准确度评分</span></template>
      <div class="acc">
        <div class="score">{{ accuracy.score?.toFixed(1) }}</div>
        <div class="acc-detail">
          <div class="line">{{ accuracy.display }} · {{ accuracy.sampleSize }} 期样本</div>
          <div class="line">原始命中率 {{ (accuracy.rawHitRate * 100).toFixed(1) }}% ·
            加权命中 {{ (accuracy.weightedHitRate * 100).toFixed(1) }}% ·
            95% 置信下界 {{ (accuracy.wilsonLower * 100).toFixed(1) }}%</div>
          <div class="line">近 10 期命中率 {{ (accuracy.recent10Rate * 100).toFixed(0) }}% ·
            当前连红 {{ accuracy.currentHitStreak }} · 最长 {{ accuracy.maxHitStreak }}</div>
          <div class="calc">{{ accuracy.calcNote }}</div>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="block">
      <template #header>
        <div class="block-head">
          <span class="t">我的发布</span>
          <el-select v-model="status" size="small" style="width: 120px" @change="(page = 1); load()">
            <el-option v-for="(o, i) in statusOptions" :key="i" :label="o.label" :value="o.value" />
          </el-select>
        </div>
      </template>

      <el-table :data="list" size="small">
        <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <router-link class="link" :to="`/prediction/${row.id}`">{{ row.title }}</router-link>
          </template>
        </el-table-column>
        <el-table-column prop="issueNo" label="期号" width="90" />
        <el-table-column prop="pricePoints" label="定价" width="80">
          <template #default="{ row }">{{ row.pricePoints }} 分</template>
        </el-table-column>
        <el-table-column prop="statusName" label="状态" width="90" />
        <el-table-column prop="buyerCount" label="解锁" width="70" />
        <el-table-column label="命中" width="90">
          <template #default="{ row }">
            <span v-if="row.hitRate !== null">{{ (row.hitRate * 100).toFixed(0) }}%</span>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="拍售时间" width="150">
          <template #default="{ row }">{{ formatDateTime(row.saleEndTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 0 || row.status === 2" text size="small" type="primary" @click="submit(row)">提交</el-button>
            <el-button v-if="row.status === 0" text size="small" type="danger" @click="remove(row)">删除</el-button>
            <el-button v-if="row.status === 3" text size="small" type="warning" @click="withdraw(row)">撤回</el-button>
            <el-button text size="small" @click="router.push(`/prediction/${row.id}`)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-if="total > 10"
        v-model:current-page="page"
        :total="total"
        :page-size="10"
        layout="prev, pager, next"
        class="pager"
        @current-change="load"
      />
    </el-card>
  </div>
</template>

<style scoped>
.head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 16px; }
.head h1 { margin: 0 0 6px; font-size: 22px; }
.sub { margin: 0; font-size: 13px; color: var(--text-sub); }
.cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 140px), 1fr)); gap: 12px; margin-bottom: 14px; }
.stat { background: #fff; border: 1px solid var(--border-soft); border-radius: 12px; padding: 14px 16px; }
.label { display: block; font-size: 12px; color: var(--text-sub); margin-bottom: 6px; }
.stat strong { font-size: 22px; }
.primary { color: var(--el-color-primary); }
.block { margin-bottom: 14px; border-radius: 12px; }
.t { font-weight: 600; font-size: 14px; }
.block-head { display: flex; justify-content: space-between; align-items: center; }
.acc { display: flex; gap: 20px; align-items: center; }
.score { font-size: 44px; font-weight: 700; color: var(--el-color-primary); }
.acc-detail { font-size: 12px; line-height: 1.9; color: var(--text-main); }
.calc { color: var(--text-sub); }
.link { color: var(--el-color-primary); cursor: pointer; }
.pager { margin-top: 12px; justify-content: center; }
</style>
