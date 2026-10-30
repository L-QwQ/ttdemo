<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { adminApi } from '@/api/admin'
import { formatDateTime } from '@/utils/format'
import type { Feedback, FeedbackStatus, FeedbackType } from '@/types'

const TYPE_META: Record<FeedbackType, string> = {
  BUG: '功能故障',
  FEATURE: '功能建议',
  EXPERIENCE: '体验问题',
  OTHER: '其他',
}

const STATUS_META: Record<FeedbackStatus, { label: string; tag: 'info' | 'warning' | 'success' }> = {
  0: { label: '待处理', tag: 'info' },
  1: { label: '处理中', tag: 'warning' },
  2: { label: '已解决', tag: 'success' },
  3: { label: '已关闭', tag: 'info' },
}

const loading = ref(false)
const rows = ref<Feedback[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
  type: '',
  status: null as number | null,
})

const dialogVisible = ref(false)
const submitting = ref(false)
const current = ref<Feedback | null>(null)
const replyForm = reactive({ reply: '', status: 1 as FeedbackStatus })

async function fetchList() {
  loading.value = true
  try {
    const result = await adminApi.feedbacks({
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

function openReply(row: Feedback) {
  current.value = row
  replyForm.reply = row.reply ?? ''
  replyForm.status = (row.status === 0 ? 1 : row.status) as FeedbackStatus
  dialogVisible.value = true
}

async function submitReply() {
  if (!current.value) return
  if (!replyForm.reply.trim()) {
    ElMessage.warning('请填写回复内容')
    return
  }
  submitting.value = true
  try {
    await adminApi.replyFeedback(current.value.id, {
      reply: replyForm.reply.trim(),
      status: replyForm.status,
    })
    ElMessage.success('已回复')
    dialogVisible.value = false
    fetchList()
  } catch {
    // 拦截器已提示
  } finally {
    submitting.value = false
  }
}

async function remove(row: Feedback) {
  try {
    await ElMessageBox.confirm(`确认删除「${row.title}」这条反馈？`, '删除反馈', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    await adminApi.removeFeedback(row.id)
    ElMessage.success('已删除')
    fetchList()
  } catch {
    // 取消或失败
  }
}

onMounted(fetchList)
</script>

<template>
  <div class="page-card">
    <h2 class="page-title">用户反馈</h2>
    <p class="page-desc">用户提交的问题与建议，回复后用户可在主站「意见反馈」中看到</p>

    <div class="toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="标题 / 用户手机号"
        style="width: 200px"
        clearable
        @keyup.enter="handleSearch"
      />
      <el-select v-model="query.type" placeholder="类型" style="width: 130px" clearable>
        <el-option v-for="(label, value) in TYPE_META" :key="value" :label="label" :value="value" />
      </el-select>
      <el-select v-model="query.status" placeholder="状态" style="width: 120px" clearable>
        <el-option label="待处理" :value="0" />
        <el-option label="处理中" :value="1" />
        <el-option label="已解决" :value="2" />
        <el-option label="已关闭" :value="3" />
      </el-select>
      <el-button type="primary" @click="handleSearch">查询</el-button>
      <el-button @click="handleReset">重置</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="userPhone" label="用户" width="130" />
      <el-table-column label="类型" width="110">
        <template #default="{ row }">{{ TYPE_META[row.type as FeedbackType] ?? row.type }}</template>
      </el-table-column>
      <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="STATUS_META[row.status as FeedbackStatus].tag">
            {{ STATUS_META[row.status as FeedbackStatus].label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="提交时间" width="170">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openReply(row)">
            {{ row.reply ? '查看 / 回复' : '回复' }}
          </el-button>
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

    <el-dialog v-model="dialogVisible" title="反馈详情与回复" width="600px" destroy-on-close>
      <template v-if="current">
        <el-descriptions :column="2" border size="small" class="meta">
          <el-descriptions-item label="提交人">{{ current.userPhone }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ TYPE_META[current.type] ?? current.type }}</el-descriptions-item>
          <el-descriptions-item label="联系方式">{{ current.contact || '-' }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ formatDateTime(current.createdAt) }}</el-descriptions-item>
        </el-descriptions>

        <h4 class="block-title">{{ current.title }}</h4>
        <div class="content-block">{{ current.content }}</div>

        <el-form label-position="top" class="reply-form">
          <el-form-item label="回复内容">
            <el-input
              v-model="replyForm.reply"
              type="textarea"
              :rows="5"
              maxlength="2000"
              show-word-limit
              placeholder="填写给用户的回复"
            />
          </el-form-item>
          <el-form-item label="处理后状态">
            <el-radio-group v-model="replyForm.status">
              <el-radio-button :value="1">处理中</el-radio-button>
              <el-radio-button :value="2">已解决</el-radio-button>
              <el-radio-button :value="3">已关闭</el-radio-button>
            </el-radio-group>
          </el-form-item>
        </el-form>
        <p v-if="current.repliedAt" class="replied-at">上次回复：{{ formatDateTime(current.repliedAt) }}</p>
      </template>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitReply">提交回复</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.meta {
  margin-bottom: 16px;
}

.block-title {
  margin: 0 0 8px;
  font-size: 14px;
}

.content-block {
  padding: 12px 14px;
  background: #f7f8fb;
  border-radius: 8px;
  font-size: 13px;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
  margin-bottom: 18px;
}

.reply-form :deep(.el-form-item) {
  margin-bottom: 12px;
}

.replied-at {
  margin: 0;
  font-size: 12px;
  color: var(--text-sub);
}
</style>
