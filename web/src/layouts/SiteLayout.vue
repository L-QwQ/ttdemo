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
  if (command === 'picks') {
    router.push('/my/picks')
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
          <span class="brand-copy"><span class="brand-text">慧彩预测</span><span class="brand-caption">让每一份分析有迹可循</span></span>
        </router-link>

        <nav class="nav" aria-label="主导航">
          <router-link to="/" class="nav-link" :class="{ active: isHome }">首页</router-link>
          <router-link to="/predictions" class="nav-link" active-class="active">预测广场</router-link>
          <router-link to="/draws" class="nav-link" active-class="active">开奖</router-link>
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
              <button type="button" class="user-trigger" aria-label="打开用户菜单">
                <el-avatar :size="28" :src="auth.user?.avatar || undefined" class="avatar">
                  {{ auth.displayName.slice(0, 1) }}
                </el-avatar>
                <span class="user-name">{{ auth.displayName }}</span>
                <span v-if="auth.isVip" class="vip-tag">VIP</span>
              </button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                  <el-dropdown-item command="points">我的积分</el-dropdown-item>
                  <el-dropdown-item command="rewards">积分兑换</el-dropdown-item>
                  <el-dropdown-item command="notices">公告通知</el-dropdown-item>
                  <el-dropdown-item command="messages">消息中心</el-dropdown-item>
                  <el-dropdown-item command="earnings">我的收益</el-dropdown-item>
                  <el-dropdown-item command="purchased">我的解锁</el-dropdown-item>
                  <el-dropdown-item command="picks">我的号码</el-dropdown-item>
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

    <main id="main-content" class="site-main">
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
          <router-link to="/legal/disclaimer">免责声明</router-link>
          <router-link to="/feedback">意见反馈</router-link>
        </span>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.site-shell { display: flex; min-height: 100%; flex-direction: column; }
.site-header { position: sticky; top: 0; z-index: 100; background: #ffffffed; backdrop-filter: blur(16px); border-bottom: 1px solid var(--border-soft); }
.header-inner { display: flex; align-items: center; gap: 28px; max-width: 1248px; margin: 0 auto; padding: 0 32px; min-height: 80px; }
.brand { display: flex; flex-shrink: 0; align-items: center; gap: 11px; }
.brand-mark { display: grid; place-items: center; width: 38px; height: 38px; border-radius: 12px; background: var(--brand-grad); color: #fff; font-size: 20px; font-weight: 650; box-shadow: 0 4px 10px #3563e91c; }
.brand-copy { display: flex; flex-direction: column; gap: 5px; }
.brand-text { font-size: 18px; font-weight: 750; letter-spacing: .04em; }
.brand-caption { font-size: 9px; color: var(--text-sub); letter-spacing: .08em; }
.nav { display: flex; align-items: center; justify-content: center; gap: 5px; flex: 1; min-width: 0; }
.nav-link { position: relative; flex-shrink: 0; padding: 10px 12px; border-radius: 8px; font-size: 13px; white-space: nowrap; color: var(--text-sub); transition: background .2s, color .2s; }
.nav-link:hover { color: var(--brand); background: var(--surface-soft); }
.nav-link.active { color: var(--brand); background: var(--el-color-primary-light-9); font-weight: 600; }
.unread-dot { display: inline-block; min-width: 16px; padding: 0 5px; margin-left: 3px; border-radius: 8px; background: var(--brand); color: white; font-size: 10px; line-height: 16px; text-align: center; }
.actions { display: flex; align-items: center; flex-shrink: 0; }
.actions > .el-button { height: 38px; font-size: 12px; padding: 0 18px; }
.user-trigger { display: flex; align-items: center; gap: 8px; padding: 5px 8px; border: 1px solid var(--border-soft); border-radius: 24px; background: white; font: inherit; cursor: pointer; }
.avatar { background: var(--brand); color: #fff; font-size: 13px; }
.user-name { max-width: 88px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 12px; color: var(--text-main); }
.vip-tag { padding: 1px 5px; border-radius: 5px; color: #8b5b1b; background: #faedcf; font-size: 10px; }
.site-main { flex: 1; width: 100%; max-width: 1248px; margin: 0 auto; padding: 32px 32px 56px; }
.site-footer { border-top: 1px solid var(--border-soft); background: #ffffff; }
.footer-inner { display: flex; align-items: flex-start; justify-content: space-between; gap: 24px; max-width: 1248px; margin: 0 auto; padding: 28px 32px; }
.footer-left { max-width: 700px; }
.footer-brand { margin: 0 0 10px; font-size: 12px; font-weight: 600; }
.footer-disclaimer { margin: 0; color: var(--text-sub); font-size: 11px; line-height: 1.9; }
.footer-links { display: flex; flex-shrink: 0; gap: 20px; color: var(--text-sub); font-size: 11px; }
.footer-links a:hover { color: var(--brand); }
@media (max-width: 1180px) {
  .header-inner { flex-wrap: wrap; gap: 0; padding-top: 16px; }
  .brand { margin-bottom: 14px; }
  .actions { margin-left: auto; margin-bottom: 14px; }
  .nav { order: 3; flex-basis: 100%; justify-content: flex-start; overflow-x: auto; gap: 6px; padding-bottom: 10px; scrollbar-width: thin; }
}
@media (max-width: 640px) {
  .header-inner { padding: 14px 18px 0; }
  .brand-mark { width: 34px; height: 34px; font-size: 18px; }
  .brand-text { font-size: 16px; }
  .brand-caption { font-size: 8px; }
  .nav-link { padding: 8px 12px; font-size: 12px; }
  .site-main { padding: 20px 16px 36px; }
  .footer-inner { flex-direction: column; gap: 18px; padding: 24px 20px; }
  .user-name { max-width: 60px; }
}
</style>
