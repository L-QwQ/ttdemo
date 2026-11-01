<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { adminApi } from '@/api/admin'
import { formatDateTime } from '@/utils/format'
import type {
  AdminNotice,
  AdminNoticeDetail,
  NoticeLevel,
  NoticeTargetType,
  NoticeType,
  UserVO,
} from '@/types'

const TYPE_META: Record<NoticeType, { label: string; tag: 'primary' | 'success' }> = {
  ANNOUNCEMENT: { label: '公告', tag: 'primary' },
  NOTICE: { label: '通知', tag: 'success' },
}

const LEVEL_META: Record<NoticeLevel, { label: string; tag: 'info' | 'warning' | 'danger' }> = {
  NORMAL: { label: '普通', tag: 'info' },
  IMPORTANT: { label: '重要', tag: 'warning' },
  URGENT: { label: '紧急', tag: 'danger' },
}

const loading = ref(false)
const rows = ref<AdminNotice[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
  type: '',
  status: null as number | null,
})

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const submitting = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  title: '',
  content: '',
  type: 'ANNOUNCEMENT' as NoticeType,
  level: 'NORMAL' as NoticeLevel,
  targetType: 'ALL' as NoticeTargetType,
  targetUserIds: [] as number[],
})

const rules: FormRules = {
  title: [{ required: true, message: '请填写标题', trigger: 'blur' }],
  content: [{ required: true, message: '请填写通知内容', trigger: 'blur' }],
}

const userOptions = ref<UserVO[]>([])
const userLoading = ref(false)

async function searchUsers(keyword: string) {
  userLoading.value = true
  try {
    const result = await adminApi.users({ page: 1, size: 30, keyword: keyword || undefined })
    userOptions.value = result.list
  } catch {
    // 拦截器已提示
  } finally {
    userLoading.value = false
  }
}

function mergeUserOptions(users: UserVO[]) {
  const map = new Map(userOptions.value.map((item) => [item.id, item]))
  users.forEach((user) => map.set(user.id, user))
  userOptions.value = [...map.values()]
}

async function fetchList() {
  loading.value = true
  try {
    const result = await adminApi.notices({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      type: query.type || undefined,
      status: query.status,
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
  query.type = ''
  query.status = null
  fetchList()
}

function handlePageChange(page: number) {
  query.page = page
  fetchList()
}

function resetForm() {
  form.title = ''
  form.content = ''
  form.type = 'ANNOUNCEMENT'
  form.level = 'NORMAL'
  form.targetType = 'ALL'
  form.targetUserIds = []
}

function openCreate() {
  dialogMode.value = 'create'
  editingId.value = null
  resetForm()
  searchUsers('')
  dialogVisible.value = true
}

async function openEdit(row: AdminNotice) {
  try {
    const detail: AdminNoticeDetail = await adminApi.noticeDetail(row.id)
    dialogMode.value = 'edit'
    editingId.value = detail.id
    form.title = detail.title
    form.content = detail.content
    form.type = detail.type
    form.level = detail.level
    form.targetType = detail.targetType
    form.targetUserIds = detail.receivers.map((user) => user.id)
    mergeUserOptions(detail.receivers)
    dialogVisible.value = true
  } catch {
    // 拦截器已提示
  }
}

async function submit(publishNow: boolean) {
  await formRef.value?.validate()
  if (form.targetType === 'SPECIFIC' && form.targetUserIds.length === 0) {
    ElMessage.warning('请至少选择一位接收人')
    return
  }
  submitting.value = true
  try {
    const payload = {
      title: form.title.trim(),
      content: form.content.trim(),
      type: form.type,
      level: form.level,
      targetType: form.targetType,
      targetUserIds: form.targetType === 'SPECIFIC' ? form.targetUserIds : undefined,
      publishNow,
    }
    if (dialogMode.value === 'create') {
      await adminApi.createNotice(payload)
    } else if (editingId.value) {
      await adminApi.updateNotice(editingId.value, payload)
    }
    ElMessage.success(publishNow ? '已发布' : '已保存为草稿')
    dialogVisible.value = false
    fetchList()
  } catch {
    // 拦截器已提示
  } finally {
    submitting.value = false
  }
}

async function publish(row: AdminNotice) {
  try {
    await ElMessageBox.confirm(
      `发布后将立即推送给${row.targetType === 'ALL' ? '全部启用用户' : '已选接收人'}，且不可再编辑。确认发布？`,
      '发布确认',
      { confirmButtonText: '发布', cancelButtonText: '取消' },
    )
    await adminApi.publishNotice(row.id)
    ElMessage.success('已发布')
    fetchList()
  } catch {
    // 取消或失败
  }
}

async function remove(row: AdminNotice) {
  try {
    await ElMessageBox.confirm(`确认删除「${row.title}」？接收人的阅读记录也会一并删除。`, '删除通知', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    await adminApi.removeNotice(row.id)
    ElMessage.success('已删除')
    fetchList()
  } catch {
    // 取消或失败
  }
}

onMounted(fetchList)
</script>

<template>
  <div class="page-card ui-page view-notice-manage">
    <h2 class="page-title">通知公告</h2>
    <p class="page-desc">公告发布给全部启用用户，通知可定向发送给指定用户；草稿发布前可修改</p>

    <div class="toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="标题关键词"
        style="width: 200px"
        clearable
        @keyup.enter="handleSearch"
      />
      <el-select v-model="query.type" placeholder="类型" style="width: 120px" clearable>
        <el-option label="公告" value="ANNOUNCEMENT" />
        <el-option label="通知" value="NOTICE" />
      </el-select>
      <el-select v-model="query.status" placeholder="状态" style="width: 120px" clearable>
        <el-option label="草稿" :value="0" />
        <el-option label="已发布" :value="1" />
      </el-select>
      <el-button type="primary" @click="handleSearch">查询</el-button>
      <el-button @click="handleReset">重置</el-button>
      <el-button type="success" @click="openCreate">发布公告 / 通知</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
      <el-table-column label="类型" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="TYPE_META[row.type as NoticeType].tag" effect="plain">
            {{ TYPE_META[row.type as NoticeType].label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="级别" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="LEVEL_META[row.level as NoticeLevel].tag">
            {{ LEVEL_META[row.level as NoticeLevel].label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="接收范围" width="110">
        <template #default="{ row }">
          {{ row.targetType === 'ALL' ? '全部用户' : '指定用户' }}
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">
            {{ row.status === 1 ? '已发布' : '草稿' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="阅读情况" width="130">
        <template #default="{ row }">
          <span v-if="row.status === 1">{{ row.readCount }} / {{ row.receiverCount }}</span>
          <span v-else class="muted">-</span>
        </template>
      </el-table-column>
      <el-table-column label="发布时间" width="170">
        <template #default="{ row }">{{ formatDateTime(row.publishedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 0" link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-if="row.status === 0" link type="success" @click="publish(row)">发布</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
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
        @size-change="(size: number) => { query.size = size; query.page = 1; fetchList() }"
      />
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '发布公告 / 通知' : '编辑草稿'"
      width="620px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="92px">
        <el-form-item label="类型">
          <el-radio-group v-model="form.type">
            <el-radio-button value="ANNOUNCEMENT">公告</el-radio-button>
            <el-radio-button value="NOTICE">通知</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="级别">
          <el-radio-group v-model="form.level">
            <el-radio-button value="NORMAL">普通</el-radio-button>
            <el-radio-button value="IMPORTANT">重要</el-radio-button>
            <el-radio-button value="URGENT">紧急</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="接收范围">
          <el-radio-group v-model="form.targetType">
            <el-radio-button value="ALL">全部用户</el-radio-button>
            <el-radio-button value="SPECIFIC">指定用户</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.targetType === 'SPECIFIC'" label="接收人">
          <el-select
            v-model="form.targetUserIds"
            multiple
            filterable
            remote
            reserve-keyword
            :remote-method="searchUsers"
            :loading="userLoading"
            placeholder="输入手机号或昵称搜索"
            style="width: 100%"
          >
            <el-option
              v-for="user in userOptions"
              :key="user.id"
              :label="`${user.nickname}（${user.phone}）`"
              :value="user.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" maxlength="100" show-word-limit placeholder="公告标题" />
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="8"
            maxlength="20000"
            show-word-limit
            placeholder="支持换行，用户端按原格式展示"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button :loading="submitting" @click="submit(false)">
          {{ dialogMode === 'create' ? '保存草稿' : '保存' }}
        </el-button>
        <el-button
          v-if="dialogMode === 'create'"
          type="primary"
          :loading="submitting"
          @click="submit(true)"
        >
          立即发布
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.muted {
  color: #a8b0be;
}
</style>
