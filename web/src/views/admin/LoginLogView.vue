<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { adminApi } from '@/api/admin'
import { formatDateTime } from '@/utils/format'
import type { LoginLog } from '@/types'

const loading = ref(false)
const rows = ref<LoginLog[]>([])
const total = ref(0)

const query = reactive({
  page: 1,
  size: 10,
  phone: '',
  success: null as number | null,
})

const typeLabels: Record<string, string> = {
  REGISTER: '注册',
  LOGIN_SMS: '验证码登录',
  LOGIN_PASSWORD: '密码登录',
  ADMIN_LOGIN: '后台登录',
}

async function fetchList() {
  loading.value = true
  try {
    const result = await adminApi.logs({
      page: query.page,
      size: query.size,
      phone: query.phone || undefined,
      success: query.success,
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
  query.phone = ''
  query.success = null
  fetchList()
}

onMounted(fetchList)
</script>

<template>
  <div class="page-card">
    <h2 class="page-title">登录日志</h2>
    <p class="page-desc">记录注册、验证码登录、密码登录与后台登录，含 IP 与 User-Agent</p>

    <div class="toolbar">
      <el-input
        v-model="query.phone"
        placeholder="手机号"
        style="width: 200px"
        clearable
        @keyup.enter="handleSearch"
      />
      <el-select v-model="query.success" placeholder="结果" style="width: 130px" clearable>
        <el-option label="成功" :value="1" />
        <el-option label="失败" :value="0" />
      </el-select>
      <el-button type="primary" @click="handleSearch">查询</el-button>
      <el-button @click="handleReset">重置</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="phone" label="手机号" width="130" />
      <el-table-column label="类型" width="120">
        <template #default="{ row }">{{ typeLabels[row.type] ?? row.type }}</template>
      </el-table-column>
      <el-table-column label="结果" width="90">
        <template #default="{ row }">
          <el-tag :type="row.success === 1 ? 'success' : 'danger'" size="small">
            {{ row.success === 1 ? '成功' : '失败' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="message" label="说明" min-width="150" show-overflow-tooltip />
      <el-table-column prop="ip" label="IP" width="130" />
      <el-table-column prop="userAgent" label="User-Agent" min-width="180" show-overflow-tooltip />
      <el-table-column label="时间" width="170">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        :current-page="query.page"
        :page-size="query.size"
        :total="total"
        layout="total, prev, pager, next"
        background
        @current-change="(page: number) => { query.page = page; fetchList() }"
      />
    </div>
  </div>
</template>

<style scoped>
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
