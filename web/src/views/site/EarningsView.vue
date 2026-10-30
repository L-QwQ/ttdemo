<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { request } from '@/api/http'
import { formatDateTime } from '@/utils/format'
import type { PageResult } from '@/types'

interface Settlement {
  id: number
  settleNo: string
  typeCode: string
  issueNo: string
  orderCount: number
  grossPoints: number
  platformFee: number
  creatorPoints: number
  status: number
  creditTime: string | null
  createdAt: string
}

const list = ref<Settlement[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const summary = ref({ total: 0, credited: 0, pending: 0, orders: 0 })

const statusName = (status: number) => ['', '待入账', '已入账', '已撤销'][status] ?? '-'

async function load() {
  loading.value = true
  try {
    const result = await request<PageResult<Settlement>>({
      url: '/v1/creator/settlements',
      method: 'get',
      params: { pageNum: page.value, pageSize: 10 },
    })
    list.value = result.list
    total.value = result.total
    summary.value = {
      total: result.list.reduce((s, r) => s + (r.status === 2 ? r.creatorPoints : 0), 0),
      credited: result.list.filter((r) => r.status === 2).length,
      pending: result.list.filter((r) => r.status === 1).length,
      orders: result.list.reduce((s, r) => s + r.orderCount, 0),
    }
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <header class="head">
      <div>
        <h1>我的收益</h1>
        <p class="sub">按结算单逐笔记录，仅已入账的结算会计入可用积分</p>
      </div>
      <router-link to="/creator/dashboard"><el-button>返回看板</el-button></router-link>
    </header>

    <div class="cards">
      <div class="card"><span>本页已入账</span><strong>{{ summary.total }}</strong><em>积分</em></div>
      <div class="card"><span>已入账单数</span><strong>{{ summary.credited }}</strong><em>单</em></div>
      <div class="card"><span>待入账单数</span><strong>{{ summary.pending }}</strong><em>单</em></div>
      <div class="card"><span>本页解锁订单</span><strong>{{ summary.orders }}</strong><em>单</em></div>
    </div>

    <el-empty v-if="!loading && list.length === 0" description="还没有结算记录，发布预测被解锁后会在这里结算" />

    <el-table v-loading="loading" :data="list" size="small" class="table">
      <el-table-column label="结算时间" width="165">
        <template #default="{ row }">{{ formatDateTime(row.creditTime || row.createdAt) }}</template>
      </el-table-column>
      <el-table-column prop="settleNo" label="结算单号" width="205" />
      <el-table-column prop="typeCode" label="彩种" width="80" />
      <el-table-column prop="issueNo" label="期号" width="110" />
      <el-table-column prop="orderCount" label="订单" width="70" align="right" />
      <el-table-column label="成交总额" width="100" align="right">
        <template #default="{ row }">{{ row.grossPoints }}</template>
      </el-table-column>
      <el-table-column label="平台抽成" width="100" align="right">
        <template #default="{ row }">{{ row.platformFee }}</template>
      </el-table-column>
      <el-table-column label="我的收益" width="110" align="right">
        <template #default="{ row }"><b class="income">{{ row.creatorPoints }}</b></template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="row.status === 2 ? 'success' : row.status === 3 ? 'info' : 'warning'">
            {{ statusName(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="page"
      :page-size="10"
      :total="total"
      layout="total, prev, pager, next"
      class="pager"
      @current-change="load"
    />

    <p class="tip">
      收益以积分形式入账，可用于解锁内容或兑换站内权益；平台不提供积分提现。
      <router-link to="/rewards">去兑换权益 →</router-link>
    </p>
  </div>
</template>

<style scoped>
.page { max-width: 1000px; margin: 0 auto; }
.head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 14px; }
h1 { margin: 0 0 4px; font-size: 22px; }
.sub { margin: 0; font-size: 13px; color: var(--text-sub); }
.cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 12px; margin-bottom: 16px; }
.card { background: #fff; border: 1px solid var(--border-soft); border-radius: 12px; padding: 14px 16px; display: flex; flex-direction: column; gap: 2px; }
.card span { font-size: 12px; color: var(--text-sub); }
.card strong { font-size: 20px; }
.card em { font-style: normal; font-size: 11px; color: var(--text-sub); }
.table { background: #fff; border-radius: 10px; width: 100%; }
.income { color: var(--brand); font-size: 14px; }
.pager { justify-content: flex-end; margin-top: 12px; }
.tip { margin-top: 14px; font-size: 12px; color: var(--text-sub); }
</style>
