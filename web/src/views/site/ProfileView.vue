<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { authApi } from '@/api/auth'
import { deactivationApi, growthApi, type SignInStatus } from '@/api/growth'
import { userApi } from '@/api/user'
import { useAuthStore } from '@/stores/auth'
import { formatDateTime } from '@/utils/format'

const auth = useAuthStore()
const saving = ref(false)
const changing = ref(false)
const uploading = ref(false)
const fileInput = ref<HTMLInputElement>()
const countdown = ref(0)

const profileForm = reactive({ nickname: '' })
const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '', code: '' })
const setPasswordFormRef = ref<FormInstance>()
const changePasswordFormRef = ref<FormInstance>()

let timer: ReturnType<typeof setInterval> | null = null

/** 验证码自动注册的账号没有密码，展示"设置密码"而不是"修改密码" */
const hasPassword = computed(() => auth.user?.hasPassword !== false)
const maskPhone = computed(() => {
  const phone = auth.user?.phone ?? ''
  return phone.length === 11 ? `${phone.slice(0, 3)}****${phone.slice(7)}` : phone
})

/** 会员状态文案：过期与未开通分开显示 */
const vipText = computed(() => {
  const raw = auth.user?.vipExpireAt
  if (!raw) return '未开通'
  const date = new Date(raw)
  if (Number.isNaN(date.getTime())) return '未开通'
  if (date.getTime() < Date.now()) return '已过期'
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${m}-${d} 到期`
})

const realnameText = computed(() => (auth.user?.realnameStatus === 1 ? '已认证' : '未认证'))

const passwordRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度 6-32 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== passwordForm.newPassword) callback(new Error('两次输入的密码不一致'))
        else callback()
      },
      trigger: 'blur',
    },
  ],
}

const initPasswordRules: FormRules = {
  code: [
    { required: true, message: '请输入短信验证码', trigger: 'blur' },
    { pattern: /^\d{4,8}$/, message: '验证码格式不正确', trigger: 'blur' },
  ],
  newPassword: passwordRules.newPassword,
  confirmPassword: passwordRules.confirmPassword,
}

const signIn = ref<SignInStatus | null>(null)
const signing = ref(false)

onMounted(async () => {
  try {
    await auth.refreshProfile()
    profileForm.nickname = auth.user?.nickname ?? ''
    signIn.value = await growthApi.signInStatus()
  } catch {
    // 拦截器已提示
  }
})

async function handleSignIn() {
  signing.value = true
  try {
    const result = await growthApi.signIn()
    ElMessage.success(`签到成功，获得 ${result.points} 积分${result.bonusPoints > 0 ? '（含连签奖励）' : ''}`)
    signIn.value = await growthApi.signInStatus()
    await auth.refreshProfile()
  } catch {
    // 拦截器已提示
  } finally {
    signing.value = false
  }
}

async function handleDeactivate() {
  try {
    await ElMessageBox.confirm(
      `注销后账号不可恢复，剩余积分将清零。\n如有未消费积分，客服会按「1 元 = 10 积分」折算后原路退回，已消费与已过期积分不退。`,
      '申请注销账号',
      { confirmButtonText: '确认申请', cancelButtonText: '再想想', type: 'warning' },
    )
    const { value } = await ElMessageBox.prompt('请填写注销原因（选填）', '注销原因', {
      confirmButtonText: '提交申请',
      cancelButtonText: '取消',
      inputPlaceholder: '如：不再使用',
    })
    await deactivationApi.apply(value)
    ElMessage.success('注销申请已提交，客服将在 3 个工作日内处理')
  } catch {
    // 取消
  }
}

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})

async function handleSaveProfile() {
  saving.value = true
  try {
    auth.user = await userApi.updateProfile({ nickname: profileForm.nickname })
    ElMessage.success('昵称已保存')
  } catch {
    // ignore
  } finally {
    saving.value = false
  }
}

function pickFile() {
  fileInput.value?.click()
}

/** 居中裁成正方形并压到 512px，避免几 MB 的原图直传 */
function compressToSquare(file: File): Promise<Blob> {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file)
    const img = new Image()
    img.onload = () => {
      const size = 512
      const side = Math.min(img.naturalWidth, img.naturalHeight)
      const canvas = document.createElement('canvas')
      canvas.width = size
      canvas.height = size
      const ctx = canvas.getContext('2d')
      if (!ctx) {
        URL.revokeObjectURL(url)
        reject(new Error('当前浏览器不支持图片处理'))
        return
      }
      // 透明 PNG 转 JPEG 会变黑底，先铺白
      ctx.fillStyle = '#ffffff'
      ctx.fillRect(0, 0, size, size)
      ctx.drawImage(img, (img.naturalWidth - side) / 2, (img.naturalHeight - side) / 2, side, side, 0, 0, size, size)
      URL.revokeObjectURL(url)
      canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new Error('图片处理失败'))), 'image/jpeg', 0.9)
    }
    img.onerror = () => {
      URL.revokeObjectURL(url)
      reject(new Error('图片读取失败'))
    }
    img.src = url
  })
}

async function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = '' // 允许连续选择同一文件
  if (!file) return
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
    ElMessage.warning('仅支持 JPG / PNG / WEBP 格式')
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('图片不能超过 5MB')
    return
  }
  uploading.value = true
  try {
    const blob = await compressToSquare(file)
    auth.user = await userApi.uploadAvatar(blob)
    ElMessage.success('头像已更新')
  } catch {
    // 拦截器已提示
  } finally {
    uploading.value = false
  }
}

function startCountdown(seconds: number) {
  countdown.value = seconds
  if (timer) clearInterval(timer)
  timer = setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0 && timer) {
      clearInterval(timer)
      timer = null
    }
  }, 1000)
}

async function handleSendCode() {
  if (!auth.user?.phone) return
  try {
    const result = await authApi.sendSmsCode({ phone: auth.user.phone, scene: 'RESET_PASSWORD' })
    startCountdown(result.resendInterval || 60)
    if (result.debugCode) passwordForm.code = result.debugCode
    ElMessage.success('验证码已发送')
  } catch {
    // ignore
  }
}

async function handleChangePassword() {
  await changePasswordFormRef.value?.validate()
  changing.value = true
  try {
    await userApi.changePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword,
    })
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
    passwordForm.confirmPassword = ''
    ElMessage.success('密码已更新')
  } catch {
    // ignore
  } finally {
    changing.value = false
  }
}

async function handleInitPassword() {
  await setPasswordFormRef.value?.validate()
  changing.value = true
  try {
    await userApi.initPassword({ code: passwordForm.code, newPassword: passwordForm.newPassword })
    passwordForm.newPassword = ''
    passwordForm.confirmPassword = ''
    passwordForm.code = ''
    await auth.refreshProfile()
    ElMessage.success('登录密码已设置，下次可用密码登录')
  } catch {
    // ignore
  } finally {
    changing.value = false
  }
}
</script>

<template>
  <div class="profile ui-page view-profile">
    <!-- 概览：头像 + 身份标签 + 三项关键数据 -->
    <section class="hero-card">
      <div
        class="avatar-wrap"
        role="button"
        tabindex="0"
        aria-label="更换头像"
        :class="{ uploading }"
        @keydown.enter="pickFile"
        @keydown.space.prevent="pickFile"
        @click="pickFile"
      >
        <el-avatar :size="68" :src="auth.user?.avatar || undefined" class="avatar">
          {{ auth.displayName.slice(0, 1) }}
        </el-avatar>
        <div class="avatar-mask">
          <span>{{ uploading ? '上传中…' : '更换' }}</span>
        </div>
      </div>
      <input ref="fileInput" type="file" accept="image/png,image/jpeg,image/webp" hidden @change="onFileChange" />

      <div class="hero-info">
        <div class="hero-line">
          <h2 class="hero-name">{{ auth.displayName }}</h2>
          <el-tag v-if="auth.isVip" size="small" type="warning" effect="dark">会员</el-tag>
          <el-tag v-if="auth.user?.realnameStatus === 1" size="small" type="success" effect="plain">已实名</el-tag>
          <el-tag v-if="auth.isStaff" size="small" type="danger" effect="plain">管理员</el-tag>
        </div>
        <p class="hero-phone">
          {{ maskPhone }}
          <span class="sep">·</span>
          {{ hasPassword ? '密码已设置' : '未设置密码' }}
          <span class="sep">·</span>
          点击头像更换（JPG / PNG / WEBP）
        </p>
      </div>

      <div class="hero-stats">
        <div class="hstat">
          <span>信用分</span>
          <b>{{ auth.user?.creditScore ?? '-' }}</b>
        </div>
        <div class="hstat">
          <span>会员</span>
          <b class="text">{{ vipText }}</b>
        </div>
        <div class="hstat">
          <span>连续签到</span>
          <b>{{ signIn?.streakDays ?? 0 }}<i>天</i></b>
        </div>
      </div>
    </section>

    <div class="columns">
      <!-- 左栏：资料与密码 -->
      <div class="col">
        <section class="page-card">
          <header class="card-head">
            <h3>基本资料</h3>
            <span>昵称可随时修改</span>
          </header>
          <el-form label-position="top" @submit.prevent>
            <el-form-item label="昵称">
              <el-input v-model="profileForm.nickname" maxlength="20" show-word-limit placeholder="请输入昵称" />
            </el-form-item>
            <el-form-item label="手机号">
              <el-input :model-value="auth.user?.phone" disabled />
              <p class="form-tip">手机号是账号唯一标识，如需更换请联系客服</p>
            </el-form-item>
            <el-button type="primary" :loading="saving" @click="handleSaveProfile">保存资料</el-button>
          </el-form>
        </section>

        <section class="page-card">
          <template v-if="hasPassword">
            <header class="card-head">
              <h3>修改密码</h3>
              <span>需验证原密码</span>
            </header>
            <el-form
              ref="changePasswordFormRef"
              :model="passwordForm"
              :rules="passwordRules"
              label-position="top"
              @submit.prevent
            >
              <el-form-item label="原密码" prop="oldPassword">
                <el-input v-model="passwordForm.oldPassword" type="password" show-password />
              </el-form-item>
              <el-form-item label="新密码" prop="newPassword">
                <el-input v-model="passwordForm.newPassword" type="password" show-password />
              </el-form-item>
              <el-form-item label="确认新密码" prop="confirmPassword">
                <el-input v-model="passwordForm.confirmPassword" type="password" show-password />
              </el-form-item>
              <el-button type="primary" :loading="changing" @click="handleChangePassword">更新密码</el-button>
            </el-form>
          </template>

          <template v-else>
            <header class="card-head">
              <h3>设置登录密码</h3>
              <span>需短信验证</span>
            </header>
            <p class="card-desc">账号由验证码自动创建，还没有密码。验证手机号后即可设置，之后可用密码或验证码登录。</p>
            <el-form
              ref="setPasswordFormRef"
              :model="passwordForm"
              :rules="initPasswordRules"
              label-position="top"
              @submit.prevent
            >
              <el-form-item label="短信验证码" prop="code">
                <div class="code-row">
                  <el-input v-model="passwordForm.code" maxlength="8" placeholder="请输入验证码" />
                  <el-button :disabled="countdown > 0" @click="handleSendCode">
                    {{ countdown > 0 ? `${countdown}s` : '获取验证码' }}
                  </el-button>
                </div>
              </el-form-item>
              <el-form-item label="新密码" prop="newPassword">
                <el-input v-model="passwordForm.newPassword" type="password" show-password />
              </el-form-item>
              <el-form-item label="确认新密码" prop="confirmPassword">
                <el-input v-model="passwordForm.confirmPassword" type="password" show-password />
              </el-form-item>
              <el-button type="primary" :loading="changing" @click="handleInitPassword">设置密码</el-button>
            </el-form>
          </template>
        </section>
      </div>

      <!-- 右栏：账号信息与安全 -->
      <div class="col">
        <section class="page-card">
          <header class="card-head">
            <h3>账号信息</h3>
          </header>
          <div class="info-grid">
            <div class="info-item">
              <span>注册时间</span>
              <b>{{ formatDateTime(auth.user?.createdAt) }}</b>
            </div>
            <div class="info-item">
              <span>最后登录</span>
              <b>{{ formatDateTime(auth.user?.lastLoginAt) }}</b>
            </div>
            <div class="info-item">
              <span>实名状态</span>
              <b>{{ realnameText }}</b>
            </div>
            <div class="info-item">
              <span>登录方式</span>
              <b>{{ hasPassword ? '密码 + 验证码' : '仅验证码' }}</b>
            </div>
          </div>
        </section>

        <section class="page-card">
          <header class="card-head">
            <h3>签到与账号安全</h3>
          </header>

          <div class="row">
            <div class="row-info">
              <span class="row-title">每日签到</span>
              <span class="row-sub">
                已连续签到 {{ signIn?.streakDays ?? 0 }} 天 · 每天 {{ signIn?.basePoints ?? 2 }} 积分，连签
                {{ signIn?.streakCycle ?? 7 }} 天额外 +{{ signIn?.streakBonus ?? 10 }}
              </span>
            </div>
            <el-button type="primary" :loading="signing" :disabled="signIn?.todaySigned" @click="handleSignIn">
              {{ signIn?.todaySigned ? `今日已签 +${signIn?.todayPoints ?? 0}` : '立即签到' }}
            </el-button>
          </div>

          <div class="divider" />

          <div class="row">
            <div class="row-info">
              <span class="row-title">协议与规则</span>
              <span class="row-sub links">
                <router-link to="/legal/terms">用户协议</router-link>
                <router-link to="/legal/points">积分规则</router-link>
                <router-link to="/legal/disclaimer">免责声明</router-link>
              </span>
            </div>
          </div>

          <div class="divider" />

          <div class="row">
            <div class="row-info">
              <span class="row-title">注销账号</span>
              <span class="row-sub">注销后不可恢复；未消费积分按 1 元 = 10 积分折算后原路退回</span>
            </div>
            <el-button type="danger" plain @click="handleDeactivate">申请注销</el-button>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

<style scoped>
.profile {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

/* ===== 概览 ===== */
.hero-card {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 18px 22px;
  border: 1px solid var(--border-soft);
  border-radius: var(--radius-lg);
  background: linear-gradient(120deg, #ffffff 0%, #fdf6f3 100%);
  box-shadow: var(--shadow-sm);
}

.avatar-wrap {
  position: relative;
  flex: none;
  overflow: hidden;
  border-radius: 50%;
  line-height: 0;
  cursor: pointer;
}

.avatar-wrap.uploading {
  cursor: progress;
}

.avatar {
  background: var(--brand);
  color: #fff;
  font-size: 26px;
  transition: transform 0.25s;
}

.avatar-wrap:hover .avatar {
  transform: scale(1.04);
}

.avatar-mask {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  background: rgba(0, 0, 0, 0.45);
  color: #fff;
  font-size: 12px;
  opacity: 0;
  transition: opacity 0.2s;
}

.avatar-wrap:hover .avatar-mask,
.avatar-wrap:focus-visible .avatar-mask {
  opacity: 1;
}

.hero-info {
  flex: 1;
  min-width: 0;
}

.hero-line {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 4px;
}

.hero-name {
  margin: 0;
  font-size: 19px;
  font-weight: 700;
  letter-spacing: -0.01em;
}

.hero-phone {
  margin: 0;
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--text-sub);
}

.hero-phone .sep {
  margin: 0 4px;
  color: #d5ccc4;
}

/* 右侧三项数据 */
.hero-stats {
  display: flex;
  flex: none;
  gap: 0;
}

.hstat {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 0 18px;
  border-left: 1px solid var(--border-soft);
  text-align: right;
}

.hstat:first-child {
  padding-left: 0;
  border-left: none;
}

.hstat span {
  font-size: 11.5px;
  color: var(--text-sub);
}

.hstat b {
  font-family: ui-monospace, monospace;
  font-size: 17px;
  font-weight: 600;
  color: var(--text-main);
  line-height: 1.2;
}

.hstat b i {
  margin-left: 2px;
  font-style: normal;
  font-size: 11px;
  font-weight: 400;
  color: var(--text-sub);
}

.hstat b.text {
  font-family: inherit;
  font-size: 12.5px;
  font-weight: 500;
}

/* ===== 两栏 ===== */
.columns {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 14px;
  align-items: start;
}

.col {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
}

/* ===== 卡片 ===== */
.card-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 14px;
}

.card-head h3 {
  margin: 0;
  font-size: 14.5px;
  font-weight: 600;
}

.card-head span {
  font-size: 11.5px;
  color: var(--text-sub);
}

.card-desc {
  margin: 0 0 14px;
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--text-sub);
}

.form-tip {
  margin: 6px 0 0;
  font-size: 11.5px;
  line-height: 1.6;
  color: var(--text-sub);
}

.code-row {
  display: flex;
  gap: 10px;
  width: 100%;
}

.code-row .el-button {
  flex: none;
  width: 108px;
}

/* ===== 账号信息 ===== */
.info-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 16px;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.info-item span {
  font-size: 11.5px;
  color: var(--text-sub);
}

.info-item b {
  overflow: hidden;
  font-family: ui-monospace, monospace;
  font-size: 12.5px;
  font-weight: 500;
  color: var(--text-main);
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ===== 行式条目 ===== */
.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.row-info {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}

.row-title {
  font-size: 13px;
  font-weight: 600;
}

.row-sub {
  font-size: 12px;
  line-height: 1.65;
  color: var(--text-sub);
}

.row-sub.links a {
  margin-right: 12px;
  color: var(--brand);
}

.row-sub.links a:hover {
  text-decoration: underline;
}

.divider {
  height: 1px;
  margin: 12px 0;
  background: var(--border-soft);
}

@media (max-width: 900px) {
  .columns {
    grid-template-columns: minmax(0, 1fr);
  }

  .hero-card {
    flex-wrap: wrap;
    gap: 14px;
  }

  .hero-stats {
    width: 100%;
    justify-content: space-between;
    padding-top: 12px;
    border-top: 1px solid var(--border-soft);
  }

  .hstat {
    flex: 1;
    padding: 0 12px;
    text-align: left;
  }

  .hstat:first-child {
    padding-left: 0;
  }
}
</style>
