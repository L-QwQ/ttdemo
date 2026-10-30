<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({ phone: '', password: '' })

const rules: FormRules = {
  phone: [
    { required: true, message: '请输入管理员手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' },
  ],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleLogin() {
  await formRef.value?.validate()
  loading.value = true
  try {
    await auth.loginAsAdmin(form.phone, form.password)
    ElMessage.success('登录成功')
    const redirect = route.query.redirect
    router.push(typeof redirect === 'string' && redirect ? redirect : '/admin')
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="admin-login">
    <div class="panel">
      <div class="panel-head">
        <span class="brand-mark">彩</span>
        <div>
          <h1>管理后台</h1>
          <p>仅限管理员账号登录</p>
        </div>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="管理员手机号" maxlength="11" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            placeholder="登录密码"
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        <el-button type="primary" class="submit" :loading="loading" @click="handleLogin">登录后台</el-button>
      </el-form>

      <p class="hint">首次启动自动创建：13800000000 / admin123456（请尽快修改）</p>
      <router-link to="/" class="back">← 返回主站</router-link>
    </div>
  </div>
</template>

<style scoped>
.admin-login {
  display: grid;
  place-items: center;
  min-height: 100vh;
  padding: 40px 24px;
  background: linear-gradient(135deg, #1f1616 0%, #3a2320 55%, #d92b2b 160%);
}

.panel {
  width: 100%;
  max-width: 400px;
  background: #fff;
  border-radius: 16px;
  padding: 32px;
  box-shadow: 0 24px 60px rgba(15, 20, 30, 0.35);
}

.panel-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 22px;
}

.brand-mark {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: var(--brand-grad);
  color: #fff;
  font-size: 18px;
  font-weight: 700;
}

.panel-head h1 {
  margin: 0;
  font-size: 20px;
}

.panel-head p {
  margin: 2px 0 0;
  font-size: 13px;
  color: var(--text-sub);
}

.submit {
  width: 100%;
}

.hint {
  margin: 18px 0 0;
  padding: 9px 12px;
  font-size: 12px;
  color: var(--text-sub);
  background: #f5f7fb;
  border-radius: 8px;
}

.back {
  display: inline-block;
  margin-top: 16px;
  font-size: 13px;
  color: var(--text-sub);
}

.back:hover {
  color: var(--brand);
}
</style>
