<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { creatorApi } from '@/api/market'
import type { CreatorProfile } from '@/types/lottery'
import { formatDateTime } from '@/utils/format'

const realname = ref<Record<string, any>>({})
const profile = ref<CreatorProfile | null>(null)
const loading = ref(true)
const submittingRealname = ref(false)
const submittingApply = ref(false)

const realnameForm = reactive({ realName: '', idCardNo: '' })
const applyForm = reactive({ penName: '', intro: '', tags: '', applyReason: '' })

const realnameStatusText: Record<number, string> = { 0: '未实名', 1: '已实名', 2: '审核中', 3: '已驳回' }
const creatorStatusText: Record<number, string> = { 0: '普通用户', 1: '申请中', 2: '已认证', 3: '已驳回', 4: '已封禁' }

async function load() {
  loading.value = true
  try {
    realname.value = await creatorApi.myRealname()
    try {
      profile.value = await creatorApi.profile()
      applyForm.penName = profile.value.penName
      applyForm.intro = profile.value.intro || ''
      applyForm.tags = profile.value.tags || ''
    } catch {
      profile.value = null
    }
  } finally {
    loading.value = false
  }
}

async function submitRealname() {
  if (!/^[\u4e00-\u9fa5]{2,20}$/.test(realnameForm.realName)) {
    ElMessage.warning('请输入 2-20 位中文姓名')
    return
  }
  if (!/^\d{17}[\dXx]$/.test(realnameForm.idCardNo)) {
    ElMessage.warning('身份证号格式不正确')
    return
  }
  submittingRealname.value = true
  try {
    await creatorApi.submitRealname(realnameForm)
    ElMessage.success('实名资料已提交，等待审核')
    await load()
  } finally {
    submittingRealname.value = false
  }
}

async function submitApply() {
  if (applyForm.penName.trim().length < 2) {
    ElMessage.warning('笔名至少 2 个字')
    return
  }
  submittingApply.value = true
  try {
    await creatorApi.apply(applyForm)
    ElMessage.success('创作者申请已提交')
    await load()
  } finally {
    submittingApply.value = false
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="page">
    <h1>创作者认证</h1>

    <el-steps :active="profile?.status === 2 ? 3 : realname.auditStatus === 1 ? 2 : 1" simple class="steps">
      <el-step title="实名认证" />
      <el-step title="提交申请" />
      <el-step title="平台审核" />
      <el-step title="发布预测" />
    </el-steps>

    <el-card shadow="never" class="block">
      <template #header>
        <div class="head">
          <span class="t">1. 实名认证</span>
          <el-tag :type="realname.auditStatus === 1 ? 'success' : realname.auditStatus === 2 ? 'warning' : 'info'" size="small">
            {{ realnameStatusText[realname.realnameStatus as number] || '未实名' }}
          </el-tag>
        </div>
      </template>

      <template v-if="realname.submitted">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="姓名">{{ realname.realNameMasked }}</el-descriptions-item>
          <el-descriptions-item label="身份证">{{ realname.idCardMasked }}</el-descriptions-item>
          <el-descriptions-item label="审核状态">{{ realname.auditStatus === 1 ? '已通过' : realname.auditStatus === 2 ? '已驳回' : '审核中' }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ formatDateTime(realname.submittedAt) }}</el-descriptions-item>
          <el-descriptions-item label="备注">{{ realname.auditRemark || '—' }}</el-descriptions-item>
        </el-descriptions>
        <p class="tip">实名信息加密存储，仅用于年龄校验与合规要求</p>
      </template>

      <el-form v-else label-width="90px" class="form">
        <el-form-item label="真实姓名">
          <el-input v-model="realnameForm.realName" placeholder="与身份证一致" maxlength="20" />
        </el-form-item>
        <el-form-item label="身份证号">
          <el-input v-model="realnameForm.idCardNo" placeholder="18 位身份证号" maxlength="18" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="submittingRealname" @click="submitRealname">提交实名</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="block">
      <template #header>
        <div class="head">
          <span class="t">2. 创作者申请</span>
          <el-tag v-if="profile" :type="profile.status === 2 ? 'success' : profile.status === 1 ? 'warning' : 'info'" size="small">
            {{ creatorStatusText[profile.status] }}
          </el-tag>
        </div>
      </template>

      <el-alert
        v-if="realname.auditStatus !== 1"
        type="info"
        :closable="false"
        show-icon
        title="请先完成实名认证并通过审核，才能申请成为创作者"
        class="mb"
      />

      <template v-if="profile?.status === 2">
        <el-alert type="success" :closable="false" title="你已是认证创作者，可以发布预测了" class="mb" />
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="笔名">{{ profile.penName }}</el-descriptions-item>
          <el-descriptions-item label="平台抽成">{{ (profile.platformFeeRate * 100).toFixed(0) }}%</el-descriptions-item>
          <el-descriptions-item label="准确度评分">{{ profile.accuracyScore?.toFixed(1) }}</el-descriptions-item>
          <el-descriptions-item label="样本数">{{ profile.sampleSize }} 期</el-descriptions-item>
          <el-descriptions-item label="关注人数">{{ profile.favCount }}</el-descriptions-item>
          <el-descriptions-item label="累计收益">{{ profile.totalIncome }} 积分</el-descriptions-item>
        </el-descriptions>
        <div class="actions">
          <el-button type="primary" @click="$router.push('/publish')">去发布预测</el-button>
          <el-button @click="$router.push('/creator/dashboard')">创作者看板</el-button>
        </div>
      </template>

      <template v-else>
        <el-form label-width="90px" class="form">
          <el-form-item label="笔名">
            <el-input v-model="applyForm.penName" placeholder="2-16 字，全站唯一" maxlength="16" />
          </el-form-item>
          <el-form-item label="擅长标签">
            <el-input v-model="applyForm.tags" placeholder="如：双色球,大小号分析" maxlength="60" />
          </el-form-item>
          <el-form-item label="简介">
            <el-input v-model="applyForm.intro" type="textarea" :rows="3" maxlength="500" show-word-limit />
          </el-form-item>
          <el-form-item label="申请理由">
            <el-input v-model="applyForm.applyReason" type="textarea" :rows="3" maxlength="500" show-word-limit
              placeholder="过往成绩、分析方法等（有助于审核通过）" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="submittingApply" :disabled="realname.auditStatus !== 1" @click="submitApply">
              {{ profile ? '重新提交申请' : '提交申请' }}
            </el-button>
          </el-form-item>
        </el-form>
        <el-alert v-if="profile?.statusName === '已驳回'" type="error" :closable="false" :title="`驳回原因：${profile.auditRemark || '内容不符合要求'}`" />
      </template>
    </el-card>

    <el-card shadow="never" class="block rule">
      <p class="t">创作者规则</p>
      <ul>
        <li>发布内容必须是结构化预测项，开奖后由系统自动判定</li>
        <li>禁止出现「必中」「包中」「稳赚」等承诺性表述</li>
        <li>撤回预测会扣减信誉分，信誉分低于 60 将暂停发布</li>
        <li>平台默认抽成 20%，认证首 90 天 10%</li>
      </ul>
    </el-card>
  </div>
</template>

<style scoped>
.page { max-width: 860px; margin: 0 auto; padding: 24px 16px 64px; }
h1 { font-size: 22px; margin: 0 0 16px; }
.steps { margin-bottom: 16px; }
.block { margin-bottom: 14px; border-radius: 12px; }
.head { display: flex; justify-content: space-between; align-items: center; }
.t { font-weight: 600; font-size: 14px; }
.form { max-width: 560px; margin-top: 12px; }
.mb { margin-bottom: 12px; }
.tip { font-size: 12px; color: var(--el-text-color-secondary); margin: 10px 0 0; }
.actions { margin-top: 14px; display: flex; gap: 10px; }
.rule ul { margin: 8px 0 0; padding-left: 18px; font-size: 12px; line-height: 1.9; color: var(--el-text-color-secondary); }
</style>
