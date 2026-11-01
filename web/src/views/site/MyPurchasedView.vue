<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { predictionApi } from '@/api/prediction'
import type { PredictionListItem } from '@/types/lottery'
import { formatDateTime } from '@/utils/format'

const router = useRouter()
const list = ref<PredictionListItem[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const result = await predictionApi.purchased({ pageNum: page.value, pageSize: 10 })
    list.value = result.list
    total.value = result.total
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page ui-page view-my-purchased">
    <h1>我的解锁</h1>
    <el-card shadow="never" class="block">
      <el-table v-loading="loading" :data="list" size="small">
        <el-table-column prop="title" label="标题" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">
            <router-link class="link" :to="`/prediction/${row.id}`">{{ row.title }}</router-link>
          </template>
        </el-table-column>
        <el-table-column prop="typeCode" label="彩种" width="80" />
        <el-table-column prop="issueNo" label="期号" width="90" />
        <el-table-column prop="pricePoints" label="花费" width="80">
          <template #default="{ row }">{{ row.pricePoints }} 积分</template>
        </el-table-column>
        <el-table-column label="命中结果" width="150">
          <template #default="{ row }">
            <template v-if="row.hitRate !== null">
              <el-tag :type="row.isHit === 1 ? 'danger' : 'info'" size="small">
                {{ (row.hitRate * 100).toFixed(1) }}% {{ row.isHit === 1 ? '命中' : '' }}
              </el-tag>
            </template>
            <span v-else class="muted">待开奖</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button text size="small" type="primary" @click="router.push(`/prediction/${row.id}`)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && list.length === 0" description="还没有解锁任何预测" />
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
h1 { font-size: 22px; margin: 0 0 16px; }
.block { border-radius: 12px; }
.link { color: var(--el-color-primary); cursor: pointer; }
.muted { color: var(--text-sub); font-size: 12px; }
.pager { margin-top: 12px; justify-content: center; }
</style>
