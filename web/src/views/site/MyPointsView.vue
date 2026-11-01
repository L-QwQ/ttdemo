<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { pointApi, type RechargeTier } from '@/api/point'
import type { PointAccount, PointTransaction } from '@/types/lottery'
import { formatDateTime } from '@/utils/format'

const account = ref<PointAccount | null>(null)
const tiers = ref<RechargeTier[]>([])
const txns = ref<PointTransaction[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const paying = ref(false)

const bizNames: Record<string, string> = {
  RECHARGE: '充值',
  BONUS: '赠送',
  SIGN_IN: '签到',
  INVITE: '邀请',
  UNLOCK: '解锁预测',
  REFUND: '退款',
  SETTLE_IN: '创作收益',
  PROMOTE: '推广',
  EXPIRE: '积分过期',
  ADMIN_ADJUST: '人工调账',
}

async function loadAccount() {
  account.value = await pointApi.account()
}

async function loadTxns() {
  loading.value = true
  try {
    const result = await pointApi.transactions({ pageNum: page.value, pageSize: 10 })
    txns.value = result.list
    total.value = result.total
  } finally {
    loading.value = false
  }
}

async function recharge(tier: RechargeTier) {
  try {
    await ElMessageBox.confirm(
      `充值 ${tier.amountYuan} 元，到账 ${tier.points} 积分${account.value?.totalRecharge === 0 ? '（首充额外赠送 10%）' : ''}。` +
        '积分仅可用于站内消费，不可提现、不可转让、不可兑换现金。',
      '确认充值',
      { confirmButtonText: '确认支付', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  paying.value = true
  try {
    const order = await pointApi.createRecharge(tier.amountFen)
    if (order.mockPayUrl) {
      await pointApi.mockPay(order.orderNo)
      ElMessage.success(`支付成功，到账 ${order.points + order.bonusPoints} 积分`)
    } else {
      ElMessage.info('已创建充值订单，请在新窗口完成支付')
    }
    await Promise.all([loadAccount(), loadTxns()])
  } finally {
    paying.value = false
  }
}

onMounted(async () => {
  const [t] = await Promise.all([pointApi.rechargeConfig(), loadAccount(), loadTxns()])
  tiers.value = t
})
</script>

<template>
  <div class="page ui-page view-my-points">
    <div class="page-head">
      <h1>我的积分</h1>
      <el-button type="primary" plain @click="$router.push('/rewards')">去兑换权益</el-button>
    </div>

    <div class="cards">
      <div class="stat">
        <span class="label">可用积分</span>
        <strong class="value primary">{{ account?.available ?? 0 }}</strong>
      </div>
      <div class="stat">
        <span class="label">累计获得</span>
        <strong class="value">{{ account?.totalIncome ?? 0 }}</strong>
      </div>
      <div class="stat">
        <span class="label">累计消费</span>
        <strong class="value">{{ account?.totalConsume ?? 0 }}</strong>
      </div>
      <div class="stat">
        <span class="label">累计充值</span>
        <strong class="value">{{ account?.totalRecharge ?? 0 }}</strong>
      </div>
    </div>

    <el-alert
      v-if="account?.expiringSoon?.points"
      type="warning"
      :closable="false"
      show-icon
      class="mb"
      :title="`有 ${account.expiringSoon.points} 积分将于 ${formatDateTime(account.expiringSoon.expireAt)} 到期`"
    />

    <el-card shadow="never" class="block">
      <template #header><span class="t">充值</span></template>
      <p class="rate">兑换比例：{{ account?.rechargeRate || '10 积分 / 元' }}，积分有效期 {{ account?.expireMonths || 24 }} 个月</p>
      <div class="tiers">
        <button v-for="t in tiers" :key="t.amountFen" class="tier" :disabled="paying" @click="recharge(t)">
          <span class="amount">￥{{ t.amountYuan }}</span>
          <span class="points">{{ t.points }} 积分</span>
        </button>
      </div>
    </el-card>

    <el-card shadow="never" class="block">
      <template #header><span class="t">积分流水</span></template>
      <el-table v-loading="loading" :data="txns" size="small">
        <el-table-column prop="txnNo" label="流水号" min-width="180" show-overflow-tooltip />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">{{ bizNames[row.bizType] || row.bizType }}</template>
        </el-table-column>
        <el-table-column label="变动" width="100">
          <template #default="{ row }">
            <span :class="row.direction === 1 ? 'in' : 'out'">
              {{ row.direction === 1 ? '+' : '-' }}{{ row.amount }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="balanceAfter" label="余额" width="90" />
        <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
        <el-table-column label="时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-if="total > 10"
        v-model:current-page="page"
        :total="total"
        :page-size="10"
        layout="prev, pager, next"
        class="pager"
        @current-change="loadTxns"
      />
    </el-card>

    <footer class="disclaimer">
      积分仅可用于平台内消费，不可提现、不可转让、不可兑换现金。
    </footer>
  </div>
</template>

<style scoped>
h1 { font-size: 22px; margin: 0 0 16px; }
.cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 150px), 1fr)); gap: 12px; margin-bottom: 16px; }
.stat { background: #fff; border: 1px solid var(--border-soft); border-radius: 12px; padding: 14px 16px; }
.label { display: block; font-size: 12px; color: var(--text-sub); margin-bottom: 6px; }
.value { font-size: 24px; font-weight: 700; }
.value.primary { color: var(--el-color-primary); }
.block { margin-bottom: 14px; border-radius: 12px; }
.t { font-weight: 600; font-size: 14px; }
.rate { margin: 0 0 12px; font-size: 12px; color: var(--text-sub); }
.tiers { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 120px), 1fr)); gap: 10px; }
.tier {
  border: 1px solid var(--el-border-color); background: #fff; border-radius: 10px;
  padding: 14px 10px; cursor: pointer; display: flex; flex-direction: column; gap: 4px; align-items: center;
  transition: all .2s;
}
.tier:hover { border-color: var(--el-color-primary); color: var(--el-color-primary); }
.tier:disabled { opacity: .6; cursor: not-allowed; }
.amount { font-size: 18px; font-weight: 600; }
.points { font-size: 12px; color: var(--text-sub); }
.in { color: var(--el-color-success); font-weight: 600; }
.out { color: var(--el-color-danger); font-weight: 600; }
.pager { margin-top: 12px; justify-content: center; }
.disclaimer {
  position: fixed; left: 0; right: 0; bottom: 0; z-index: 10;
  background: #fffdf5; border-top: 1px solid #f3e1b0; color: #a06a1b;
  font-size: 12px; text-align: center; padding: 10px 16px;
}
</style>
