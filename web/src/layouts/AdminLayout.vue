<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Avatar, Bell, Calendar, ChatDotRound, DataLine, Document, Money, Present, Stamp, User, Warning } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const activeMenu = computed(() => route.path)

const pageTitle = computed(() => (route.meta.title as string | undefined) ?? '管理后台')

async function handleCommand(command: string) {
  if (command === 'logout') {
    await auth.logout()
    ElMessage.success('已退出登录')
    router.push('/admin/login')
    return
  }
  if (command === 'site') {
    router.push('/')
  }
}
</script>

<template>
  <el-container class="admin-shell">
    <el-aside width="220px" class="aside">
      <div class="brand">
        <span class="brand-mark">彩</span>
        <span class="brand-text">慧彩预测后台</span>
      </div>
      <el-menu :default-active="activeMenu" router class="menu">
        <el-menu-item v-if="auth.can('sys:dashboard:view')" index="/admin">
          <el-icon><DataLine /></el-icon>
          <span>仪表盘</span>
        </el-menu-item>
        <el-menu-item v-if="auth.can('prd:prediction:audit')" index="/admin/audit">
          <el-icon><Stamp /></el-icon>
          <span>预测审核</span>
        </el-menu-item>
        <el-menu-item v-if="auth.can('prd:issue:manage') || auth.can('prd:issue:settle')" index="/admin/issues">
          <el-icon><Calendar /></el-icon>
          <span>期次管理</span>
        </el-menu-item>
        <el-menu-item v-if="auth.can('prd:creator:audit')" index="/admin/creators">
          <el-icon><Avatar /></el-icon>
          <span>创作者审核</span>
        </el-menu-item>
        <el-menu-item v-if="auth.can('fin:point:view') || auth.can('sys:config:manage')" index="/admin/finance">
          <el-icon><Money /></el-icon>
          <span>财务与配置</span>
        </el-menu-item>
        <el-menu-item v-if="auth.can('sys:config:manage') || auth.can('fin:point:adjust')" index="/admin/rewards">
          <el-icon><Present /></el-icon>
          <span>权益管理</span>
        </el-menu-item>
        <el-menu-item v-if="auth.can('prd:prediction:audit')" index="/admin/reports">
          <el-icon><Warning /></el-icon>
          <span>举报与注销</span>
        </el-menu-item>
        <el-menu-item v-if="auth.can('sys:user:view')" index="/admin/users">
          <el-icon><User /></el-icon>
          <span>用户管理</span>
        </el-menu-item>
        <el-menu-item v-if="auth.can('sys:notice:manage')" index="/admin/notices">
          <el-icon><Bell /></el-icon>
          <span>通知公告</span>
        </el-menu-item>
        <el-menu-item v-if="auth.can('sys:feedback:manage')" index="/admin/feedback">
          <el-icon><ChatDotRound /></el-icon>
          <span>用户反馈</span>
        </el-menu-item>
        <el-menu-item v-if="auth.can('sys:dashboard:view')" index="/admin/logs">
          <el-icon><Document /></el-icon>
          <span>登录日志</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-left">
          <strong>{{ pageTitle }}</strong>
        </div>
        <div class="header-right">
          <el-dropdown trigger="click" @command="handleCommand">
            <span class="user-trigger">
              <el-avatar :size="28" :src="auth.user?.avatar || undefined" class="avatar">
                {{ auth.displayName.slice(0, 1) }}
              </el-avatar>
              <span>{{ auth.displayName }}</span>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="site">返回主站</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.admin-shell {
  height: 100%;
}

.aside {
  background: #1f2430;
  color: #dfe4ee;
  display: flex;
  flex-direction: column;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 60px;
  padding: 0 20px;
  font-weight: 600;
  color: #fff;
}

.brand-mark {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: var(--brand-grad);
  font-size: 15px;
}

.menu {
  border-right: none;
  background: transparent;
  --el-menu-text-color: #b9c2d4;
  --el-menu-hover-bg-color: rgba(255, 255, 255, 0.08);
  --el-menu-active-color: #ffffff;
  --el-menu-bg-color: transparent;
}

.menu :deep(.el-menu-item.is-active) {
  background: rgba(217, 43, 43, 0.35);
  border-radius: 8px;
  margin: 4px 10px;
}

.menu :deep(.el-menu-item) {
  margin: 4px 10px;
  border-radius: 8px;
  height: 44px;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid var(--border-soft);
  height: 60px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  font-size: 14px;
  outline: none;
}

.avatar {
  background: var(--brand);
  color: #fff;
  font-size: 13px;
}

.main {
  background: var(--page-bg);
  padding: 20px;
}
</style>
