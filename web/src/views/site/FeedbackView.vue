<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { feedbackApi } from '@/api/feedback'
import { formatDateTime } from '@/utils/format'
import type { Feedback, FeedbackStatus, FeedbackType } from '@/types'

const TYPE_OPTIONS: { label: string; value: FeedbackType }[] = [
  { label: '功能故障', value: 'BUG' },
  { label: '功能建议', value: 'FEATURE' },
  { label: '体验问题', value: 'EXPERIENCE' },
  { label: '其他', value: 'OTHER' },
]

const STATUS_META: Record<FeedbackStatus, { label: string; type: 'info' | 'warning' | 'success' }> = {
  0: { label: '待处理', type: 'info' },
  1: { label: '处理中', type: 'warning' },
  2: { label: '已解决', type: 'success' },
  3: { label: '已关闭', type: 'info' },
}

const formRef = ref<FormInstance>()
const submitting = ref(false)
const form = reactive({
  type: 'FEATURE' as FeedbackType,
  title: '',
  content: '',
  contact: '',
})

const rules: FormRules = {
  type: [{ required: true, message: '请选择反馈类型', trigger: 'change' }],
  title: [{ required: true, message: '请填写标题', trigger: 'blur' }],
  content: [
    { required: true, message: '请填写反馈内容', trigger: 'blur' },
    { min: 5, max: 2000, message: '反馈内容需在 5-2000 字之间', trigger: 'blur' },
  ],
}

const loading = ref(false)
const rows = ref<Feedback[]>([])
const total = ref(0)
const page = ref(1)
const size = 5

async function fetchMine() {
  loading.value = true
  try {
    const result = await feedbackApi.my({ page: page.value, size })
    rows.value = result.list
    total.value = result.total
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

async function handleSubmit() {
  await formRef.value?.validate()
  submitting.value = true
  try {
    await feedbackApi.create({
      type: form.type,
      title: form.title.trim(),
      content: form.content.trim(),
      contact: form.contact.trim() || undefined,
    })
    ElMessage.success('反馈已提交，我们会尽快处理')
    form.title = ''
    form.content = ''
    form.contact = ''
    page.value = 1
    fetchMine()
  } catch {
    // 拦截器已提示
  } finally {
    submitting.value = false
  }
}

function handlePageChange(value: number) {
  page.value = value
  fetchMine()
}

onMounted(fetchMine)
</script>

<template>
  <div class="feedback-page ui-page view-feedback">
    <div class="page-card">
      <h2 class="page-title">意见反馈</h2>
      <p class="page-desc">遇到问题或有功能建议都可以提，管理员回复后可在下方「我的反馈」中查看</p>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="反馈类型" prop="type">
          <el-radio-group v-model="form.type">
            <el-radio-button v-for="item in TYPE_OPTIONS" :key="item.value" :value="item.value">
              {{ item.label }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" maxlength="100" show-word-limit placeholder="一句话概括你的问题或建议" />
        </el-form-item>
        <el-form-item label="详细描述" prop="content">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="6"
            maxlength="2000"
            show-word-limit
            placeholder="请描述具体场景、操作步骤和期望结果，便于我们定位问题"
          />
        </el-form-item>
        <el-form-item label="联系方式（选填）">
          <el-input v-model="form.contact" maxlength="100" placeholder="手机号 / 微信 / 邮箱，便于进一步沟通" />
        </el-form-item>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">提交反馈</el-button>
      </el-form>
    </div>

    <div class="page-card">
      <h2 class="page-title">我的反馈</h2>
      <p class="page-desc">共 {{ total }} 条，展开可查看完整内容与管理员回复</p>

      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="expand">
              <p class="expand-label">反馈内容</p>
              <p class="expand-text">{{ row.content }}</p>
              <template v-if="row.reply">
                <p class="expand-label">管理员回复（{{ formatDateTime(row.repliedAt) }}）</p>
                <p class="expand-text reply">{{ row.reply }}</p>
              </template>
              <p v-else class="expand-hint">暂无回复，请耐心等待</p>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            {{ TYPE_OPTIONS.find((item) => item.value === row.type)?.label ?? row.type }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="STATUS_META[row.status as FeedbackStatus].type">
              {{ STATUS_META[row.status as FeedbackStatus].label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="提交时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && rows.length === 0" description="还没有提交过反馈" :image-size="80" />

      <div v-if="total > size" class="pager">
        <el-pagination
          :current-page="page"
          :page-size="size"
          :total="total"
          layout="total, prev, pager, next"
          background
          @current-change="handlePageChange"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.feedback-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.expand {
  padding: 4px 12px 12px 48px;
}

.expand-label {
  margin: 8px 0 4px;
  font-size: 12px;
  color: var(--text-sub);
}

.expand-text {
  margin: 0;
  font-size: 13px;
  line-height: 1.75;
  white-space: pre-wrap;
  word-break: break-word;
}

.expand-text.reply {
  padding: 10px 12px;
  background: var(--surface-soft);
  border-radius: 8px;
}

.expand-hint {
  margin: 8px 0 0;
  font-size: 12px;
  color: #98a1b0;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
