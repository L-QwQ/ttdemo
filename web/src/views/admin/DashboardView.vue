<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { adminApi } from '@/api/admin'
import type { AdminStats } from '@/types'

const loading = ref(false)
const stats = ref<AdminStats | null>(null)

interface PlatformStats {
  onlinePredictions: number
  pendingAuditPredictions: number
  settledPredictions: number
  totalCreators: number
  pendingRealname: number
  paidOrders: number
  drawnIssues: number
  officialDrawnIssues: number
}
const platform = ref<PlatformStats | null>(null)

const userCards = ref<{ label: string; key: keyof AdminStats; tone: string }[]>([
  { label: '用户总数', key: 'totalUsers', tone: 'red' },
  { label: '今日新增', key: 'todayNewUsers', tone: 'green' },
  { label: '今日登录', key: 'todayLogins', tone: 'gold' },
  { label: '已禁用账号', key: 'disabledUsers', tone: 'grey' },
])

const platformCards = ref<{ label: string; key: keyof PlatformStats; tone: string; hint?: string }[]>([
  { label: '在售预测', key: 'onlinePredictions', tone: 'red' },
  { label: '待审核预测', key: 'pendingAuditPredictions', tone: 'gold', hint: '前往「预测审核」处理' },
  { label: '已结算预测', key: 'settledPredictions', tone: 'green' },
  { label: '创作者总数', key: 'totalCreators', tone: 'red' },
  { label: '待审实名申请', key: 'pendingRealname', tone: 'gold', hint: '前往「创作者审核」处理' },
  { label: '已支付订单', key: 'paidOrders', tone: 'green' },
  { label: '已开奖期次', key: 'drawnIssues', tone: 'grey' },
  { label: '官方同步开奖', key: 'officialDrawnIssues', tone: 'grey', hint: '来自官方接口' },
])

async function fetchStats() {
  loading.value = true
  try {
    const [s, p] = await Promise.all([
      adminApi.stats(),
      adminApi.platformStats().catch(() => null),
    ])
    stats.value = s
    platform.value = p
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

function cardValue(key: keyof AdminStats | keyof PlatformStats): number {
  return (stats.value?.[key as keyof AdminStats] ?? platform.value?.[key as keyof PlatformStats] ?? 0) as number
}

onMounted(fetchStats)
</script>

<template>
  <div v-loading="loading" class="dashboard">
    <div class="section-label">用户数据</div>
    <div class="stats-grid">
      <div v-for="card in userCards" :key="card.key" class="stat-card" :class="card.tone">
        <p class="stat-label">{{ card.label }}</p>
        <p class="stat-value">{{ cardValue(card.key) }}</p>
      </div>
    </div>

    <div class="section-label">平台数据 · 彩票预测</div>
    <div class="stats-grid">
      <div v-for="card in platformCards" :key="card.key" class="stat-card" :class="card.tone">
        <p class="stat-label">
          {{ card.label }}
          <el-tooltip v-if="card.hint" :content="card.hint" placement="top">
            <span class="hint-dot">?</span>
          </el-tooltip>
        </p>
        <p class="stat-value">{{ cardValue(card.key) }}</p>
      </div>
    </div>

    <div class="page-card">
      <h2 class="page-title">系统概况</h2>
      <p class="page-desc">用户数据实时统计，平台数据来自各业务模块</p>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="正常账号">{{ stats?.enabledUsers ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="禁用账号">{{ stats?.disabledUsers ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="今日新增用户">{{ stats?.todayNewUsers ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="今日成功登录">{{ stats?.todayLogins ?? 0 }}</el-descriptions-item>
      </el-descriptions>
      <div class="actions">
        <el-button @click="fetchStats">刷新数据</el-button>
        <el-button type="primary" plain @click="$router.push('/admin/audit')">去预测审核</el-button>
        <el-button plain @click="$router.push('/admin/issues')">期次管理</el-button>
        <el-button plain @click="$router.push('/admin/creators')">创作者审核</el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.section-label {
  font-size: 13px;
  font-weight: 700;
  color: var(--text-sub);
  margin-top: 6px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 16px;
}

.stat-card {
  background: #fff;
  border: 1px solid var(--border-soft);
  border-radius: 12px;
  padding: 18px 20px;
  position: relative;
  overflow: hidden;
}

.stat-card::after {
  content: '';
  position: absolute;
  inset: auto -30px -40px auto;
  width: 110px;
  height: 110px;
  border-radius: 50%;
  opacity: 0.12;
}

.stat-card.red::after { background: #d92b2b; }
.stat-card.green::after { background: #22b07d; }
.stat-card.gold::after { background: #d9982b; }
.stat-card.grey::after { background: #8b929c; }

.stat-label {
  margin: 0 0 8px;
  font-size: 13px;
  color: var(--text-sub);
}

.hint-dot {
  display: inline-block;
  width: 14px;
  height: 14px;
  line-height: 14px;
  text-align: center;
  border-radius: 50%;
  background: var(--border-soft);
  color: var(--text-sub);
  font-size: 10px;
  cursor: help;
}

.stat-value {
  margin: 0;
  font-size: 30px;
  font-weight: 600;
  letter-spacing: -0.02em;
}

.actions {
  display: flex;
  gap: 12px;
  margin-top: 18px;
}
</style>
