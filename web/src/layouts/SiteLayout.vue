<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useNoticeStore } from '@/stores/notice'
import { useMessageStore } from '@/stores/message'

const auth = useAuthStore()
const noticeStore = useNoticeStore()
const messageStore = useMessageStore()
const router = useRouter()
const route = useRoute()

const isHome = computed(() => route.name === 'home')

onMounted(() => {
  noticeStore.refresh()
  messageStore.refresh()
})

watch(
  () => auth.isLoggedIn,
  (loggedIn) => {
    if (loggedIn) {
      noticeStore.refresh()
      messageStore.refresh()
    } else {
      noticeStore.reset()
      messageStore.reset()
    }
  },
)

// 在消息/通知页操作后回到其他页面时刷新角标
watch(
  () => route.fullPath,
  () => {
    if (auth.isLoggedIn) {
      noticeStore.refresh()
      messageStore.refresh()
    }
  },
)

async function handleCommand(command: string) {
  if (command === 'logout') {
    await auth.logout()
    noticeStore.reset()
    ElMessage.success('已退出登录')
    router.push('/')
    return
  }
  if (command === 'profile') {
    router.push('/profile')
    return
  }
  if (command === 'notices') {
    router.push('/notices')
    return
  }
  if (command === 'feedback') {
    router.push('/feedback')
    return
  }
  if (command === 'admin') {
    router.push('/admin')
    return
  }
  if (command === 'points') {
    router.push('/my/points')
    return
  }
  if (command === 'rewards') {
    router.push('/rewards')
    return
  }
  if (command === 'messages') {
    router.push('/messages')
    return
  }
  if (command === 'earnings') {
    router.push('/creator/earnings')
    return
  }
  if (command === 'purchased') {
    router.push('/my/purchased')
    return
  }
  if (command === 'creator') {
    router.push('/creator/dashboard')
    return
  }
  if (command === 'publish') {
    router.push('/publish')
  }
}
</script>

<template>
  <div class="site-shell">
    <header class="site-header">
      <div class="header-inner">
        <router-link to="/" class="brand">
          <span class="brand-mark">彩</span>
          <span class="brand-text">慧彩预测</span>
        </router-link>

        <nav class="nav">
          <router-link to="/" class="nav-link" :class="{ active: isHome }">首页</router-link>
          <router-link to="/predictions" class="nav-link" active-class="active">预测广场</router-link>
          <router-link to="/rank" class="nav-link" active-class="active">榜单</router-link>
          <router-link to="/random" class="nav-link" active-class="active">随机预测</router-link>
          <router-link v-if="auth.isLoggedIn" to="/notices" class="nav-link" active-class="active">
            公告
            <span v-if="noticeStore.unread > 0" class="unread-dot">
              {{ noticeStore.unread > 99 ? '99+' : noticeStore.unread }}
            </span>
          </router-link>
          <router-link v-if="auth.isLoggedIn" to="/messages" class="nav-link" active-class="active">
            消息
            <span v-if="messageStore.unread > 0" class="unread-dot">
              {{ messageStore.unread > 99 ? '99+' : messageStore.unread }}
            </span>
          </router-link>
          <router-link v-if="auth.isLoggedIn" to="/profile" class="nav-link" active-class="active">
            个人中心
          </router-link>
          <router-link v-if="auth.isStaff" to="/admin" class="nav-link">管理后台</router-link>
        </nav>

        <div class="actions">
          <template v-if="auth.isLoggedIn">
            <el-dropdown trigger="click" @command="handleCommand">
              <span class="user-trigger">
                <el-avatar :size="28" :src="auth.user?.avatar || undefined" class="avatar">
                  {{ auth.displayName.slice(0, 1) }}
                </el-avatar>
                <span class="user-name">{{ auth.displayName }}</span>
                <span v-if="auth.isVip" class="vip-tag">VIP</span>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                  <el-dropdown-item command="points">我的积分</el-dropdown-item>
                  <el-dropdown-item command="rewards">积分兑换</el-dropdown-item>
                  <el-dropdown-item command="notices">公告通知</el-dropdown-item>
                  <el-dropdown-item command="messages">消息中心</el-dropdown-item>
                  <el-dropdown-item command="earnings">我的收益</el-dropdown-item>
                  <el-dropdown-item command="purchased">我的解锁</el-dropdown-item>
                  <el-dropdown-item command="creator">创作者中心</el-dropdown-item>
                  <el-dropdown-item command="publish">发布预测</el-dropdown-item>
                  <el-dropdown-item v-if="auth.isStaff" command="admin">管理后台</el-dropdown-item>
                  <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
          <template v-else>
            <el-button type="primary" @click="router.push('/login')">登录 / 注册</el-button>
          </template>
        </div>
      </div>
    </header>

    <main class="site-main">
      <router-view />
    </main>

    <footer class="site-footer">
      <div class="footer-inner">
        <div class="footer-left">
          <p class="footer-brand">慧彩预测 · 数据来源：中国福彩网 / 中国体彩网</p>
          <p class="footer-disclaimer">
            本平台内容为创作者个人分析观点，仅供交流参考，不构成投注建议；彩票有风险，未满 18 周岁请勿购买。
          </p>
        </div>
        <span class="footer-links">
          <router-link to="/feedback">意见反馈</router-link>
        </span>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.site-shell {
  display: flex;
  min-height: 100%;
  flex-direction: column;
}

.site-header {
  position: sticky;
  top: 0;
  z-index: 10;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid var(--border-soft);
}

.header-inner {
  display: flex;
  align-items: center;
  gap: 32px;
  max-width: 1120px;
  margin: 0 auto;
  padding: 0 24px;
  height: 64px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  font-weight: 700;
  font-size: 17px;
}

.brand-mark {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 9px;
  background: var(--brand-grad);
  color: #fff;
  font-size: 15px;
}

.nav {
  display: flex;
  gap: 24px;
  flex: 1;
}

.nav-link {
  position: relative;
  font-size: 14px;
  color: var(--text-sub);
  padding: 6px 0;
  border-bottom: 2px solid transparent;
  transition: color 0.2s;
}

.unread-dot {
  display: inline-block;
  min-width: 16px;
  height: 16px;
  margin-left: 4px;
  padding: 0 5px;
  border-radius: 8px;
  background: #f56c6c;
  color: #fff;
  font-size: 11px;
  line-height: 16px;
  text-align: center;
  vertical-align: 1px;
}

.nav-link:hover {
  color: var(--brand);
}

.nav-link.active {
  color: var(--brand);
  border-bottom-color: var(--brand);
}

.actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  outline: none;
}

.avatar {
  background: var(--brand);
  color: #fff;
  font-size: 13px;
}

.user-name {
  font-size: 14px;
  color: var(--text-main);
}

.vip-tag {
  margin-left: 2px;
  padding: 0 5px;
  border-radius: 6px;
  background: linear-gradient(135deg, #d9982b, #e4b45c);
  color: #fff;
  font-size: 10px;
  line-height: 16px;
  letter-spacing: 0.5px;
}

.site-main {
  flex: 1;
  width: 100%;
  max-width: 1120px;
  margin: 0 auto;
  padding: 32px 24px 56px;
}

.site-footer {
  border-top: 1px solid var(--border-soft);
  background: #fff;
}

.footer-inner {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 24px;
  max-width: 1120px;
  margin: 0 auto;
  padding: 22px 24px;
  font-size: 13px;
  color: var(--text-sub);
}

.footer-left {
  max-width: 640px;
}

.footer-brand {
  margin: 0 0 6px;
  font-weight: 600;
  color: var(--text-main);
}

.footer-disclaimer {
  margin: 0;
  font-size: 12px;
  line-height: 1.7;
}

.footer-links {
  display: flex;
  gap: 18px;
}

.footer-links a:hover {
  color: var(--brand);
}
</style>
