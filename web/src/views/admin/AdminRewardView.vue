<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { rewardAdminApi, type RewardItem, type RewardOrder } from '@/api/reward'
import { formatDateTime, formatFen, maskPhone } from '@/utils/format'

const tab = ref('items')
const loading = ref(false)
const items = ref<RewardItem[]>([])
const orders = ref<RewardOrder[]>([])
const total = ref(0)
const page = ref(1)
const status = ref<number | undefined>()
const keyword = ref('')

const dialogVisible = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const form = reactive({
  itemCode: '',
  name: '',
  category: 1,
  pointsCost: 100,
  valueFen: 0,
  durationDays: 30,
  stock: -1,
  description: '',
  status: 1,
  sortNo: 10,
})

const rules: FormRules = {
  itemCode: [{ required: true, message: '请填写商品编码', trigger: 'blur' }],
  name: [{ required: true, message: '请填写商品名称', trigger: 'blur' }],
  pointsCost: [{ required: true, message: '请填写所需积分', trigger: 'blur' }],
}

async function loadItems() {
  loading.value = true
  try {
    items.value = await rewardAdminApi.items()
  } finally {
    loading.value = false
  }
}

async function loadOrders() {
  loading.value = true
  try {
    const result = await rewardAdminApi.orders({
      pageNum: page.value,
      pageSize: 20,
      status: status.value,
      keyword: keyword.value || undefined,
    })
    orders.value = result.list
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, {
    itemCode: '',
    name: '',
    category: 1,
    pointsCost: 100,
    valueFen: 0,
    durationDays: 30,
    stock: -1,
    description: '',
    status: 1,
    sortNo: 10,
  })
  dialogVisible.value = true
}

function openEdit(row: RewardItem) {
  editingId.value = row.id
  Object.assign(form, {
    itemCode: row.itemCode,
    name: row.name,
    category: row.category,
    pointsCost: row.pointsCost,
    valueFen: row.valueFen,
    durationDays: row.durationDays,
    stock: row.stock,
    description: row.description ?? '',
    status: row.status,
    sortNo: 0,
  })
  dialogVisible.value = true
}

async function save() {
  await formRef.value?.validate()
  saving.value = true
  try {
    const payload = { ...form }
    if (editingId.value) {
      await rewardAdminApi.updateItem(editingId.value, payload)
      ElMessage.success('已保存')
    } else {
      await rewardAdminApi.createItem(payload as never)
      ElMessage.success('已新增')
    }
    dialogVisible.value = false
    await loadItems()
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row: RewardItem) {
  const next = row.status === 1 ? 0 : 1
  await rewardAdminApi.updateItem(row.id, {
    itemCode: row.itemCode,
    name: row.name,
    category: row.category,
    pointsCost: row.pointsCost,
    valueFen: row.valueFen,
    durationDays: row.durationDays,
    stock: row.stock,
    description: row.description ?? '',
    status: next,
    sortNo: 0,
  })
  ElMessage.success(next === 1 ? '已上架' : '已下架')
  await loadItems()
}

async function issue(row: RewardOrder) {
  try {
    const { value } = await ElMessageBox.prompt('填写发放备注（如快递单号）', '标记已发放', {
      confirmButtonText: '确认发放',
      cancelButtonText: '取消',
      inputPlaceholder: '顺丰 SF1234567890',
    })
    await rewardAdminApi.issue(row.id, value)
    ElMessage.success('已标记发放')
    await loadOrders()
  } catch {
    // 取消
  }
}

async function cancel(row: RewardOrder) {
  try {
    const { value } = await ElMessageBox.prompt('取消后积分将退回用户，请填写原因', '取消兑换', {
      confirmButtonText: '确认取消',
      cancelButtonText: '返回',
      inputPlaceholder: '用户申请取消 / 库存异常等',
      inputValidator: (v) => (v && v.trim().length >= 2 ? true : '请填写至少 2 个字的原因'),
    })
    await rewardAdminApi.cancel(row.id, value)
    ElMessage.success('已取消并退回积分')
    await loadOrders()
  } catch {
    // 取消
  }
}

onMounted(async () => {
  await Promise.all([loadItems(), loadOrders()])
})
</script>

<template>
  <div class="page">
    <header class="head">
      <div>
        <h1>积分权益管理</h1>
        <p class="sub">积分仅可兑换会员时长与实物礼品，不提供提现或兑换现金。</p>
      </div>
      <el-button type="primary" @click="openCreate">新增权益</el-button>
    </header>

    <el-tabs v-model="tab">
      <el-tab-pane label="权益商品" name="items">
        <el-table v-loading="loading" :data="items" size="small" class="table">
          <el-table-column prop="itemCode" label="编码" width="120" />
          <el-table-column prop="name" label="名称" min-width="140" />
          <el-table-column label="类型" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="row.category === 1 ? 'warning' : 'danger'" effect="plain">
                {{ row.category === 1 ? '会员' : '礼品' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="pointsCost" label="所需积分" width="100" align="right" />
          <el-table-column label="折算价值" width="100" align="right">
            <template #default="{ row }">¥{{ formatFen(row.valueFen) }}</template>
          </el-table-column>
          <el-table-column label="会员天数" width="95" align="right">
            <template #default="{ row }">{{ row.durationDays || '-' }}</template>
          </el-table-column>
          <el-table-column label="库存" width="90" align="right">
            <template #default="{ row }">{{ row.stock === -1 ? '不限' : row.stock }}</template>
          </el-table-column>
          <el-table-column prop="exchangedCount" label="已兑" width="80" align="right" />
          <el-table-column label="状态" width="85">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">
                {{ row.status === 1 ? '上架' : '下架' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="130">
            <template #default="{ row }">
              <el-button size="small" text type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button size="small" text :type="row.status === 1 ? 'info' : 'success'" @click="toggleStatus(row)">
                {{ row.status === 1 ? '下架' : '上架' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="兑换单" name="orders">
        <div class="toolbar">
          <el-select v-model="status" placeholder="全部状态" clearable style="width: 140px" @change="page = 1; loadOrders()">
            <el-option label="待发放" :value="1" />
            <el-option label="已发放" :value="2" />
            <el-option label="已取消" :value="3" />
          </el-select>
          <el-input v-model="keyword" placeholder="兑换单号" clearable style="width: 220px" @keyup.enter="page = 1; loadOrders()" />
          <el-button @click="page = 1; loadOrders()">查询</el-button>
        </div>

        <el-table v-loading="loading" :data="orders" size="small" class="table">
          <el-table-column label="时间" width="160">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column prop="orderNo" label="兑换单号" width="200" />
          <el-table-column label="用户" width="150">
            <template #default="{ row }">
              <div class="user-cell">
                <span>{{ row.userNickname || '-' }}</span>
                <em>{{ maskPhone(row.userPhone) }}</em>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="itemName" label="权益" min-width="130" />
          <el-table-column label="数量" width="65">
            <template #default="{ row }">×{{ row.quantity }}</template>
          </el-table-column>
          <el-table-column label="积分" width="90" align="right">
            <template #default="{ row }">{{ row.pointsCost }}</template>
          </el-table-column>
          <el-table-column label="收货信息" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="row.category === 2">{{ row.contactName }} {{ maskPhone(row.contactPhone) }} {{ row.address }}</span>
              <span v-else class="muted">会员自动发放</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 2 ? 'success' : row.status === 3 ? 'info' : 'warning'">
                {{ row.statusName }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="130">
            <template #default="{ row }">
              <el-button v-if="row.status === 1" size="small" text type="primary" @click="issue(row)">发放</el-button>
              <el-button v-if="row.status !== 3" size="small" text type="danger" @click="cancel(row)">取消</el-button>
              <span v-if="row.status === 3" class="muted">已关闭</span>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-model:current-page="page"
          :page-size="20"
          :total="total"
          layout="total, prev, pager, next"
          class="pager"
          @current-change="loadOrders"
        />
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑权益' : '新增权益'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item label="商品编码" prop="itemCode">
          <el-input v-model="form.itemCode" placeholder="VIP_30 / GIFT_XXX" :disabled="!!editingId" />
        </el-form-item>
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" maxlength="64" />
        </el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="form.category">
            <el-radio :value="1">会员时长</el-radio>
            <el-radio :value="2">实物礼品</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="所需积分" prop="pointsCost">
          <el-input-number v-model="form.pointsCost" :min="1" :step="50" />
        </el-form-item>
        <el-form-item label="折算价值(分)">
          <el-input-number v-model="form.valueFen" :min="0" :step="100" />
          <span class="hint">用于财务成本统计，¥{{ formatFen(form.valueFen) }}</span>
        </el-form-item>
        <el-form-item v-if="form.category === 1" label="会员天数">
          <el-input-number v-model="form.durationDays" :min="1" :max="3650" />
        </el-form-item>
        <el-form-item label="库存">
          <el-input-number v-model="form.stock" :min="-1" />
          <span class="hint">-1 表示不限量</span>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortNo" :min="0" />
          <span class="hint">数字越小越靠前</span>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="上架" inactive-text="下架" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page { padding: 4px; }
.head { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; margin-bottom: 12px; }
h1 { margin: 0 0 4px; font-size: 20px; }
.sub { margin: 0; font-size: 12px; color: var(--el-text-color-secondary); }
.toolbar { display: flex; gap: 10px; margin-bottom: 12px; flex-wrap: wrap; }
.table { background: #fff; border-radius: 10px; width: 100%; }
.pager { margin-top: 12px; justify-content: flex-end; }
.user-cell { display: flex; flex-direction: column; line-height: 1.4; }
.user-cell em { font-style: normal; font-size: 11px; color: var(--el-text-color-secondary); }
.muted { color: var(--el-text-color-placeholder); font-size: 12px; }
.hint { margin-left: 8px; font-size: 11px; color: var(--el-text-color-secondary); }
</style>
