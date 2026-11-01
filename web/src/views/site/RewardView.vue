<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { rewardApi, type RewardItem, type RewardOrder, type RewardSummary } from '@/api/reward'
import { formatDateTime } from '@/utils/format'

const router = useRouter()

const loading = ref(false)
const summary = ref<RewardSummary | null>(null)
const items = ref<RewardItem[]>([])
const orders = ref<RewardOrder[]>([])
const total = ref(0)
const page = ref(1)
const tab = ref('items')

const dialogVisible = ref(false)
const submitting = ref(false)
const current = ref<RewardItem | null>(null)
const formRef = ref<FormInstance>()
const form = reactive({ quantity: 1, contactName: '', contactPhone: '', address: '' })

const isGift = computed(() => current.value?.category === 2)

const rules: FormRules = {
  contactName: [{ required: true, message: '请填写收货人', trigger: 'blur' }],
  contactPhone: [
    { required: true, message: '请填写联系电话', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' },
  ],
  address: [{ required: true, message: '请填写详细收货地址', trigger: 'blur' }],
}

const vipItems = computed(() => items.value.filter((i) => i.category === 1))
const giftItems = computed(() => items.value.filter((i) => i.category === 2))
const totalCost = computed(() => (current.value ? current.value.pointsCost * form.quantity : 0))
const canPay = computed(() => (summary.value?.available ?? 0) >= totalCost.value)

async function loadAll() {
  loading.value = true
  try {
    const [s, list] = await Promise.all([rewardApi.summary(), rewardApi.items()])
    summary.value = s
    items.value = list
  } finally {
    loading.value = false
  }
}

async function loadOrders() {
  const result = await rewardApi.myOrders({ pageNum: page.value, pageSize: 10 })
  orders.value = result.list
  total.value = result.total
}

function openExchange(item: RewardItem) {
  if (!summary.value?.exchangeEnabled) {
    ElMessage.warning('积分兑换暂未开放')
    return
  }
  if (item.soldOut) {
    ElMessage.warning('该权益已兑完')
    return
  }
  if (!item.affordable) {
    ElMessage.warning(`积分不足，还差 ${item.pointsCost - (summary.value?.available ?? 0)} 积分`)
    return
  }
  current.value = item
  form.quantity = 1
  form.contactName = ''
  form.contactPhone = ''
  form.address = ''
  dialogVisible.value = true
}

async function submit() {
  if (!current.value) return
  if (isGift.value) {
    await formRef.value?.validate()
  }
  submitting.value = true
  try {
    await rewardApi.exchange({
      itemId: current.value.id,
      quantity: form.quantity,
      contactName: isGift.value ? form.contactName : undefined,
      contactPhone: isGift.value ? form.contactPhone : undefined,
      address: isGift.value ? form.address : undefined,
    })
    ElMessage.success(
      current.value.category === 1 ? '兑换成功，会员已生效' : '兑换成功，等待客服联系发货',
    )
    dialogVisible.value = false
    await loadAll()
    if (tab.value === 'orders') await loadOrders()
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadAll(), loadOrders()])
})
</script>

<template>
  <div class="rewards ui-page view-reward" v-loading="loading">
    <header class="head">
      <div>
        <h1>积分兑换</h1>
        <p class="sub">
          积分可兑换会员时长与实物礼品，不可提现、不可转让、不可兑换现金。
        </p>
      </div>
      <el-button @click="router.push('/my/points')">积分明细</el-button>
    </header>

    <div class="status-card">
      <div class="status-item">
        <span>可用积分</span>
        <strong>{{ summary?.available ?? 0 }}</strong>
      </div>
      <div class="status-item">
        <span>会员状态</span>
        <strong class="vip">{{ summary?.vip ? '会员' : '未开通' }}</strong>
        <em v-if="summary?.vip">{{ formatDateTime(summary?.vipExpireAt) }} 到期</em>
      </div>
      <div class="status-item">
        <span>今日已兑换</span>
        <strong>{{ summary?.todayCount ?? 0 }} / {{ summary?.dailyLimit ?? 0 }}</strong>
      </div>
      <div class="status-item">
        <span>累计兑换</span>
        <strong>{{ summary?.exchangedCount ?? 0 }} 单</strong>
        <em>消耗 {{ summary?.exchangedPoints ?? 0 }} 积分</em>
      </div>
    </div>

    <el-alert
      v-if="summary && !summary.exchangeEnabled"
      type="warning"
      :closable="false"
      show-icon
      class="mb"
      title="积分兑换暂未开放，请稍后再来"
    />
    <el-alert
      v-else-if="summary"
      type="info"
      :closable="false"
      show-icon
      class="mb"
      :title="`单次兑换需满 ${summary.minPoints} 积分，每日最多兑换 ${summary.dailyLimit} 次`"
    />

    <el-tabs v-model="tab">
      <el-tab-pane label="可兑换权益" name="items">
        <h3 class="section">会员权益</h3>
        <div class="grid">
          <div v-for="item in vipItems" :key="item.id" class="item-card">
            <div class="item-top">
              <span class="badge vip">会员</span>
              <span class="days">{{ item.durationDays }} 天</span>
            </div>
            <h4>{{ item.name }}</h4>
            <p class="desc">{{ item.description }}</p>
            <div class="item-foot">
              <div class="price">
                <strong>{{ item.pointsCost }}</strong>
                <span>积分</span>
              </div>
              <el-button type="primary" size="small" :disabled="item.soldOut || !item.affordable" @click="openExchange(item)">
                {{ item.soldOut ? '已兑完' : item.affordable ? '立即兑换' : '积分不足' }}
              </el-button>
            </div>
            <p class="stock">
              {{ item.unlimited ? '不限量' : `剩余 ${item.stock} 份` }} · 已兑 {{ item.exchangedCount }} 次
            </p>
          </div>
        </div>

        <h3 class="section">实物礼品</h3>
        <div v-if="giftItems.length === 0" class="empty">暂无可兑换礼品</div>
        <div class="grid">
          <div v-for="item in giftItems" :key="item.id" class="item-card">
            <div class="item-top">
              <span class="badge gift">礼品</span>
              <span class="days">需填收货信息</span>
            </div>
            <h4>{{ item.name }}</h4>
            <p class="desc">{{ item.description }}</p>
            <div class="item-foot">
              <div class="price">
                <strong>{{ item.pointsCost }}</strong>
                <span>积分</span>
              </div>
              <el-button type="primary" size="small" :disabled="item.soldOut || !item.affordable" @click="openExchange(item)">
                {{ item.soldOut ? '已兑完' : item.affordable ? '立即兑换' : '积分不足' }}
              </el-button>
            </div>
            <p class="stock">
              {{ item.unlimited ? '不限量' : `剩余 ${item.stock} 份` }} · 已兑 {{ item.exchangedCount }} 次
            </p>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="我的兑换" name="orders">
        <el-table :data="orders" size="small" class="table">
          <el-table-column label="时间" width="165">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column prop="orderNo" label="兑换单号" width="205" />
          <el-table-column prop="itemName" label="权益" min-width="140" />
          <el-table-column label="数量" width="70">
            <template #default="{ row }">×{{ row.quantity }}</template>
          </el-table-column>
          <el-table-column label="消耗积分" width="100" align="right">
            <template #default="{ row }">-{{ row.pointsCost }}</template>
          </el-table-column>
          <el-table-column label="状态" width="95">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 2 ? 'success' : row.status === 3 ? 'info' : 'warning'">
                {{ row.statusName }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="备注" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ row.issueRemark || '-' }}</template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-model:current-page="page"
          :page-size="10"
          :total="total"
          layout="total, prev, pager, next"
          class="pager"
          @current-change="loadOrders"
        />
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="dialogVisible" :title="`兑换 ${current?.name ?? ''}`" width="440px">
      <div class="dialog-body">
        <p class="dialog-line">
          单价 <strong>{{ current?.pointsCost }}</strong> 积分，当前可用
          <strong>{{ summary?.available ?? 0 }}</strong> 积分
        </p>
        <el-form label-position="top">
          <el-form-item label="兑换数量">
            <el-input-number v-model="form.quantity" :min="1" :max="10" />
          </el-form-item>
        </el-form>
        <el-form v-if="isGift" ref="formRef" :model="form" :rules="rules" label-position="top">
          <el-form-item label="收货人" prop="contactName">
            <el-input v-model="form.contactName" maxlength="32" />
          </el-form-item>
          <el-form-item label="联系电话" prop="contactPhone">
            <el-input v-model="form.contactPhone" maxlength="11" />
          </el-form-item>
          <el-form-item label="收货地址" prop="address">
            <el-input v-model="form.address" type="textarea" :rows="2" maxlength="200" show-word-limit />
          </el-form-item>
        </el-form>
        <p class="dialog-total">
          合计扣减 <strong>{{ totalCost }}</strong> 积分
          <span v-if="!canPay" class="warn">（积分不足）</span>
        </p>
        <p class="dialog-tip">兑换成功后积分立即扣减；会员即时生效，实物礼品由客服在 3 个工作日内联系发货。</p>
      </div>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" :disabled="!canPay" @click="submit">确认兑换</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.rewards { display: flex; flex-direction: column; gap: 14px; }
.head { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; }
h1 { margin: 0 0 4px; font-size: 22px; }
.sub { margin: 0; font-size: 13px; color: var(--text-sub); }
.mb { margin-bottom: 4px; }

.status-card { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 150px), 1fr)); gap: 12px; background: #fff; border: 1px solid var(--border-soft); border-radius: 14px; padding: 16px 18px; }
.status-item { display: flex; flex-direction: column; gap: 2px; }
.status-item span { font-size: 12px; color: var(--text-sub); }
.status-item strong { font-size: 20px; }
.status-item strong.vip { color: var(--gold); }
.status-item em { font-style: normal; font-size: 11px; color: var(--text-sub); }

.section { margin: 18px 0 10px; font-size: 15px; padding-left: 8px; border-left: 3px solid var(--brand); }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(min(100%, 240px), 1fr)); gap: 12px; }
.item-card { background: #fff; border: 1px solid var(--border-soft); border-radius: 14px; padding: 16px; display: flex; flex-direction: column; gap: 8px; transition: box-shadow 0.2s, transform 0.2s; }
.item-card:hover { box-shadow: 0 6px 20px rgba(0, 0, 0, 0.07); transform: translateY(-2px); }
.item-top { display: flex; justify-content: space-between; align-items: center; }
.badge { font-size: 11px; padding: 2px 8px; border-radius: 8px; }
.badge.vip { background: rgba(217, 152, 43, 0.14); color: var(--gold); }
.badge.gift { background: rgba(217, 43, 43, 0.1); color: var(--brand); }
.days { font-size: 11px; color: var(--text-sub); }
.item-card h4 { margin: 0; font-size: 15px; }
.desc { margin: 0; font-size: 12px; line-height: 1.6; color: var(--text-sub); min-height: 38px; }
.item-foot { display: flex; justify-content: space-between; align-items: center; margin-top: auto; }
.price strong { font-size: 20px; color: var(--brand); }
.price span { font-size: 12px; color: var(--text-sub); margin-left: 3px; }
.stock { margin: 0; font-size: 11px; color: var(--text-sub); }

.table { background: #fff; border-radius: 10px; width: 100%; }
.pager { margin-top: 12px; justify-content: flex-end; }
.empty { color: var(--text-sub); font-size: 13px; padding: 20px; text-align: center; }

.dialog-body { display: flex; flex-direction: column; gap: 8px; }
.dialog-line { margin: 0; font-size: 13px; color: var(--text-sub); }
.dialog-line strong { color: var(--text-main); }
.dialog-total { margin: 4px 0 0; font-size: 14px; }
.dialog-total strong { font-size: 20px; color: var(--brand); }
.warn { color: var(--brand); font-size: 12px; }
.dialog-tip { margin: 0; font-size: 12px; line-height: 1.6; color: var(--text-sub); }
</style>
