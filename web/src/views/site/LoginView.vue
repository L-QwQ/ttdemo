<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { authApi } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import type { SmsScene } from '@/types'

type TabKey = 'sms' | 'password' | 'register'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const activeTab = ref<TabKey>('sms')
const loading = ref(false)
const countdown = ref(0)
const debugCode = ref('')
const smsFormRef = ref<FormInstance>()
const passwordFormRef = ref<FormInstance>()
const registerFormRef = ref<FormInstance>()

let timer: ReturnType<typeof setInterval> | null = null

const phoneRule = {
  pattern: /^1[3-9]\d{9}$/,
  message: '请输入正确的手机号',
  trigger: 'blur',
}

const smsForm = reactive({ phone: '', code: '' })
const passwordForm = reactive({ phone: '', password: '' })
const registerForm = reactive({ phone: '', code: '', password: '', nickname: '', inviteCode: '', agreed: false })

const smsRules: FormRules = {
  phone: [{ required: true, message: '请输入手机号', trigger: 'blur' }, phoneRule],
  code: [{ required: true, message: '请输入验证码', trigger: 'blur' }],
}

const passwordRules: FormRules = {
  phone: [{ required: true, message: '请输入手机号', trigger: 'blur' }, phoneRule],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

const registerRules: FormRules = {
  phone: [{ required: true, message: '请输入手机号', trigger: 'blur' }, phoneRule],
  code: [{ required: true, message: '请输入验证码', trigger: 'blur' }],
  password: [{ min: 6, max: 32, message: '密码长度 6-32 位', trigger: 'blur' }],
}

const redirectTarget = computed(() => {
  const redirect = route.query.redirect
  return typeof redirect === 'string' && redirect ? redirect : '/'
})

const currentPhone = computed(() => {
  if (activeTab.value === 'register') return registerForm.phone
  if (activeTab.value === 'sms') return smsForm.phone
  return ''
})

const canSendCode = computed(() => countdown.value === 0 && /^1[3-9]\d{9}$/.test(currentPhone.value))

function startCountdown(seconds: number) {
  countdown.value = seconds
  stopTimer()
  timer = setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0) stopTimer()
  }, 1000)
}

function stopTimer() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
  if (countdown.value < 0) countdown.value = 0
}

onBeforeUnmount(stopTimer)

async function handleSendCode() {
  const scene: SmsScene = activeTab.value === 'register' ? 'REGISTER' : 'LOGIN'
  if (!currentPhone.value) {
    ElMessage.warning('请先输入手机号')
    return
  }
  try {
    const result = await authApi.sendSmsCode({ phone: currentPhone.value, scene })
    startCountdown(result.resendInterval || 60)
    ElMessage.success('验证码已发送')
    if (result.debugCode) {
      debugCode.value = result.debugCode
      if (activeTab.value === 'register') {
        registerForm.code = result.debugCode
      } else {
        smsForm.code = result.debugCode
      }
    }
  } catch {
    // 错误提示已由拦截器处理
  }
}

function goNext(target?: string) {
  // 后台人员无指定跳转目标时，直接进管理后台
  if (!target && auth.isStaff && redirectTarget.value === '/') {
    router.push('/admin')
    return
  }
  const next = target ?? redirectTarget.value
  router.push(next)
}

async function handleSmsLogin() {
  await smsFormRef.value?.validate()
  loading.value = true
  try {
    await auth.loginBySms(smsForm.phone, smsForm.code)
    ElMessage.success('登录成功')
    goNext()
  } catch {
    // ignore
  } finally {
    loading.value = false
  }
}

async function handlePasswordLogin() {
  await passwordFormRef.value?.validate()
  loading.value = true
  try {
    await auth.loginByPassword(passwordForm.phone, passwordForm.password)
    ElMessage.success('登录成功')
    goNext()
  } catch {
    // ignore
  } finally {
    loading.value = false
  }
}

async function handleRegister() {
  await registerFormRef.value?.validate()
  loading.value = true
  try {
    await auth.register({
      phone: registerForm.phone,
      code: registerForm.code,
      password: registerForm.password || undefined,
      nickname: registerForm.nickname || undefined,
      agreed: registerForm.agreed,
      inviteCode: registerForm.inviteCode || undefined,
    })
    ElMessage.success('注册成功，已自动登录')
    goNext()
  } catch {
    // ignore
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <aside class="login-intro">
      <router-link to="/" class="login-brand"><span>彩</span> 慧彩预测</router-link>
      <div class="intro-content">
        <p class="intro-eyebrow">DATA · INSIGHT · RECORD</p>
        <h2>每一份认真分析，<br />都值得被看见。</h2>
        <p class="intro-desc">连接创作者与数据，让历史记录成为交流的起点。</p>
        <div class="intro-points"><span>官方开奖数据</span><span>可追溯的分析记录</span><span>透明的创作者榜单</span></div>
      </div>
      <p class="intro-note">理性看待概率，享受交流的过程。</p>
    </aside>
    <div class="login-card">
      <div class="card-head">
        <h1>欢迎回来</h1>
        <p>慧彩预测 · 手机号验证码即可登录，未注册自动创建账号</p>
      </div>

      <el-tabs v-model="activeTab" class="tabs">
        <el-tab-pane label="验证码登录" name="sms">
          <el-form ref="smsFormRef" :model="smsForm" :rules="smsRules" label-position="top" @submit.prevent>
            <el-form-item label="手机号" prop="phone">
              <el-input v-model="smsForm.phone" placeholder="请输入手机号" maxlength="11" clearable />
            </el-form-item>
            <el-form-item label="验证码" prop="code">
              <div class="code-row">
                <el-input v-model="smsForm.code" placeholder="6 位验证码" maxlength="8" />
                <el-button :disabled="!canSendCode" @click="handleSendCode">
                  {{ countdown > 0 ? `${countdown}s 后重发` : '获取验证码' }}
                </el-button>
              </div>
            </el-form-item>
            <el-button type="primary" class="submit" :loading="loading" @click="handleSmsLogin">登录</el-button>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="密码登录" name="password">
          <el-form
            ref="passwordFormRef"
            :model="passwordForm"
            :rules="passwordRules"
            label-position="top"
            @submit.prevent
          >
            <el-form-item label="手机号" prop="phone">
              <el-input v-model="passwordForm.phone" placeholder="请输入手机号" maxlength="11" clearable />
            </el-form-item>
            <el-form-item label="密码" prop="password">
              <el-input
                v-model="passwordForm.password"
                type="password"
                show-password
                placeholder="请输入密码"
                @keyup.enter="handlePasswordLogin"
              />
            </el-form-item>
            <el-button type="primary" class="submit" :loading="loading" @click="handlePasswordLogin">登录</el-button>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="注册" name="register">
          <el-form
            ref="registerFormRef"
            :model="registerForm"
            :rules="registerRules"
            label-position="top"
            @submit.prevent
          >
            <el-form-item label="手机号" prop="phone">
              <el-input v-model="registerForm.phone" placeholder="请输入手机号" maxlength="11" clearable />
            </el-form-item>
            <el-form-item label="验证码" prop="code">
              <div class="code-row">
                <el-input v-model="registerForm.code" placeholder="6 位验证码" maxlength="8" />
                <el-button :disabled="!canSendCode" @click="handleSendCode">
                  {{ countdown > 0 ? `${countdown}s 后重发` : '获取验证码' }}
                </el-button>
              </div>
            </el-form-item>
            <el-form-item label="昵称（选填）" prop="nickname">
              <el-input v-model="registerForm.nickname" placeholder="不填则自动生成" maxlength="20" />
            </el-form-item>
            <el-form-item label="密码（选填）" prop="password">
              <el-input
                v-model="registerForm.password"
                type="password"
                show-password
                placeholder="设置后可用密码登录"
              />
            </el-form-item>
            <el-form-item label="邀请码（选填）">
              <el-input v-model="registerForm.inviteCode" placeholder="填写好友邀请码，双方都有积分奖励" maxlength="16" />
            </el-form-item>
            <el-checkbox v-model="registerForm.agreed" class="agree">
              我已阅读并同意
              <router-link to="/legal/terms" target="_blank">《用户协议》</router-link>
              <router-link to="/legal/points" target="_blank">《积分规则》</router-link>
              <router-link to="/legal/disclaimer" target="_blank">《免责声明》</router-link>
            </el-checkbox>
            <el-button
              type="primary"
              class="submit"
              :loading="loading"
              :disabled="!registerForm.agreed"
              @click="handleRegister"
            >
              注册并登录
            </el-button>
          </el-form>
        </el-tab-pane>
      </el-tabs>

      <p class="legal-tip">
        登录或注册即表示你已同意
        <router-link to="/legal/terms">《用户协议》</router-link>
        <router-link to="/legal/points">《积分规则》</router-link>
        <router-link to="/legal/disclaimer">《免责声明》</router-link>
        ，未满 18 周岁禁止充值。
      </p>

      <p v-if="debugCode" class="debug-tip">
        开发环境验证码：<span class="mono">{{ debugCode }}</span>（已自动填入）
      </p>

      <div class="card-foot">
        <router-link to="/">返回首页</router-link>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(360px, 440px);
  align-items: center;
  gap: 72px;
  max-width: 1160px;
  margin: 0 auto;
  min-height: 100vh;
  padding: 48px 40px;

}

.login-card {
  width: 100%;
  max-width: 440px;
  background: #fff;
  border: 1px solid var(--border-soft);
  border-radius: 22px;
  padding: 36px;
  box-shadow: 0 18px 44px rgba(31, 36, 48, 0.08);
}

.card-head h1 {
  margin: 0 0 6px;
  font-size: 24px;
}

.card-head p {
  margin: 0 0 18px;
  font-size: 13px;
  color: var(--text-sub);
}

.code-row {
  display: flex;
  gap: 10px;
  width: 100%;
}

.code-row .el-input {
  flex: 1;
}

.submit {
  width: 100%;
  height: 42px;
  margin-top: 8px;
}

.agree {
  display: block;
  margin: 2px 0 12px;
  font-size: 12px;
  line-height: 1.8;
  white-space: normal;
  height: auto;
}

.agree :deep(.el-checkbox__label),
.legal-tip {
  font-size: 12px;
  color: var(--text-sub);
}

.agree a,
.legal-tip a {
  color: var(--el-color-primary);
}

.legal-tip {
  margin: 14px 0 0;
  line-height: 1.8;
  text-align: center;
}

.debug-tip {
  margin: 14px 0 0;
  font-size: 12px;
  color: #c07b12;
  background: #fdf6e7;
  border: 1px solid #f6e3b8;
  border-radius: 8px;
  padding: 8px 10px;
}

.card-foot {
  display: flex;
  justify-content: space-between;
  margin-top: 22px;
  font-size: 13px;
  color: var(--text-sub);
}

.card-foot a:hover {
  color: var(--brand);
}

:deep(.el-tabs__item) {
  font-size: 14px;
}

.login-intro { align-self: stretch; display: flex; flex-direction: column; justify-content: space-between; padding: 24px 0; }
.login-brand { display: flex; align-items: center; gap: 12px; font-size: 22px; font-weight: 700; }
.login-brand span { display: grid; place-items: center; width: 42px; height: 42px; border-radius: 13px; color: white; background: var(--brand-grad); font-size: 23px; }
.intro-content { padding: 60px 0; }
.intro-eyebrow { color: #6883b1; font-size: 11px; letter-spacing: .2em; }
.intro-content h2 { margin: 22px 0; font-size: clamp(30px, 3.2vw, 42px); letter-spacing: -.04em; line-height: 1.65; }
.intro-desc { font-size: 14px; color: var(--text-sub); line-height: 1.9; }
.intro-points { display: flex; flex-direction: column; gap: 14px; margin-top: 32px; font-size: 13px; color: var(--text-sub); }
.intro-points span::before { content: '✓'; display: inline-grid; place-items: center; width: 22px; height: 22px; margin-right: 10px; border-radius: 50%; background: #e8effa; color: #6883b1; font-size: 11px; }
.intro-note { font-size: 11px; color: var(--text-sub); }
.card-head p { line-height: 1.8; }
.agree :deep(.el-checkbox__label) { white-space: normal; line-height: 1.8; }
@media (max-width: 820px) {
  .login-page { grid-template-columns: minmax(0, 440px); justify-content: center; align-content: center; gap: 26px; padding: 32px 20px; }
  .login-intro { padding: 0; }
  .intro-content, .intro-note { display: none; }
  .login-brand { font-size: 19px; }
}
@media (max-width: 440px) {
  .login-card { padding: 26px 22px; }
  .code-row { gap: 6px; }
  .code-row .el-button { padding: 0 10px; font-size: 12px; }
}
</style>
