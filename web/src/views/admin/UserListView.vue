<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { adminApi } from '@/api/admin'
import { useAuthStore } from '@/stores/auth'
import { formatDateTime } from '@/utils/format'
import type { UserRole, UserVO } from '@/types'

const auth = useAuthStore()

const loading = ref(false)
const rows = ref<UserVO[]>([])
const total = ref(0)

const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
  status: null as number | null,
  role: '',
})

async function fetchList() {
  loading.value = true
  try {
    const result = await adminApi.users({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      status: query.status,
      role: query.role || undefined,
    })
    rows.value = result.list
    total.value = result.total
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.page = 1
  fetchList()
}

function handleReset() {
  query.page = 1
  query.keyword = ''
  query.status = null
  query.role = ''
  fetchList()
}

function handlePageChange(page: number) {
  query.page = page
  fetchList()
}

function handleSizeChange(size: number) {
  query.size = size
  query.page = 1
  fetchList()
}

async function toggleStatus(row: UserVO) {
  const target = row.status === 1 ? 0 : 1
  try {
    await adminApi.updateStatus(row.id, target)
    row.status = target
    ElMessage.success(target === 1 ? '已启用该账号' : '已禁用该账号')
  } catch {
    // 拦截器已提示
  }
}

async function changeRole(row: UserVO, role: string) {
  if (role === row.role) return
  try {
    await adminApi.updateRole(row.id, role as UserRole)
    row.role = role as UserRole
    ElMessage.success('角色已更新')
  } catch {
    // 拦截器已提示，保持原值
  }
}

async function resetPassword(row: UserVO) {
  try {
    const { value } = await ElMessageBox.prompt(`为 ${row.phone} 设置新密码（6-32 位）`, '重置密码', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputType: 'password',
      inputPattern: /^.{6,32}$/,
      inputErrorMessage: '密码长度需为 6-32 位',
    })
    await adminApi.resetPassword(row.id, value)
    ElMessage.success('密码已重置')
  } catch {
    // 取消或失败
  }
}

async function removeUser(row: UserVO) {
  try {
    await ElMessageBox.confirm(`确认删除账号 ${row.phone}（${row.nickname}）？删除后该手机号无法再登录。`, '删除账号', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    await adminApi.remove(row.id)
    ElMessage.success('已删除')
    fetchList()
  } catch {
    // 取消或失败
  }
}

onMounted(fetchList)
</script>

<template>
  <div class="page-card ui-page view-user-list">
    <h2 class="page-title">用户管理</h2>
    <p class="page-desc">支持按手机号/昵称搜索、状态与角色筛选；封禁后该用户令牌立即失效</p>

    <div class="toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="手机号 / 昵称"
        style="width: 220px"
        clearable
        @keyup.enter="handleSearch"
      />
      <el-select v-model="query.status" placeholder="状态" style="width: 130px" clearable>
        <el-option label="正常" :value="1" />
        <el-option label="禁用" :value="0" />
      </el-select>
      <el-select v-model="query.role" placeholder="角色" style="width: 130px" clearable>
        <el-option label="普通用户" value="USER" />
        <el-option label="管理员" value="ADMIN" />
      </el-select>
      <el-button type="primary" @click="handleSearch">查询</el-button>
      <el-button @click="handleReset">重置</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="phone" label="手机号" width="130" />
      <el-table-column prop="nickname" label="昵称" min-width="120" show-overflow-tooltip />
      <el-table-column label="角色" width="140">
        <template #default="{ row }">
          <el-select
            :model-value="row.role"
            size="small"
            :disabled="row.id === auth.user?.id"
            @change="(value: string) => changeRole(row, value)"
          >
            <el-option label="普通用户" value="USER" />
            <el-option label="管理员" value="ADMIN" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-switch
            :model-value="row.status === 1"
            :disabled="row.id === auth.user?.id"
            @change="toggleStatus(row)"
          />
        </template>
      </el-table-column>
      <el-table-column label="最后登录" width="170">
        <template #default="{ row }">{{ formatDateTime(row.lastLoginAt) }}</template>
      </el-table-column>
      <el-table-column label="注册时间" width="170">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="resetPassword(row)">重置密码</el-button>
          <el-button link type="danger" :disabled="row.id === auth.user?.id" @click="removeUser(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        :current-page="query.page"
        :page-size="query.size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        background
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>
  </div>
</template>

<style scoped>
</style>
