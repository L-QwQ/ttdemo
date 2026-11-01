<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowDown, Fold, Expand, House } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import AdminNavigation from '@/components/admin/AdminNavigation.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const collapsed = ref(false)
const mobileOpen = ref(false)
const compactScreen = ref(false)
const pageTitle = computed(() => (route.meta.title as string | undefined) ?? '管理后台')
const roleNames: Record<string, string> = { ADMIN: '管理员', OPERATOR: '运营人员', AUDITOR: '审核人员', FINANCE: '财务人员' }
const roleLabel = computed(() => roleNames[auth.user?.role ?? ''] ?? '后台人员')
let media: MediaQueryList | undefined
function syncScreen() { compactScreen.value = media?.matches ?? false; mobileOpen.value = false }
onMounted(() => { media = window.matchMedia('(max-width: 768px)'); syncScreen(); media.addEventListener('change', syncScreen) })
onBeforeUnmount(() => media?.removeEventListener('change', syncScreen))
watch(() => route.fullPath, () => { mobileOpen.value = false })
function toggleNavigation() { if (compactScreen.value) mobileOpen.value = !mobileOpen.value; else collapsed.value = !collapsed.value }
async function handleCommand(command: string) {
  if (command === 'logout') { await auth.logout(); ElMessage.success('已退出登录'); router.push('/admin/login') }
  if (command === 'site') router.push('/')
}
</script>

<template>
  <div class="admin-shell" :class="{ 'nav-collapsed': collapsed }">
    <aside class="sidebar">
      <router-link to="/admin" class="brand" aria-label="慧彩管理中心">
        <span class="brand-mark">慧</span>
        <span class="brand-copy"><strong>慧彩管理中心</strong><small>ADMIN WORKSPACE</small></span>
      </router-link>
      <div class="sidebar-scroll"><AdminNavigation :collapsed="collapsed" /></div>
      <router-link to="/" class="sidebar-footer" title="返回主站"><el-icon><House /></el-icon><span>返回慧彩主站</span><span class="exit-arrow">↗</span></router-link>
    </aside>

    <div class="workspace">
      <header class="header">
        <div class="header-left">
          <button class="nav-toggle" type="button" :aria-label="compactScreen ? '打开导航菜单' : collapsed ? '展开导航' : '收起导航'"
            :aria-expanded="compactScreen ? mobileOpen : !collapsed" @click="toggleNavigation">
            <el-icon><Expand v-if="collapsed || compactScreen" /><Fold v-else /></el-icon>
          </button>
          <div class="breadcrumb"><span>工作台</span><i>/</i><strong>{{ pageTitle }}</strong></div>
        </div>
        <div class="header-right">
          <router-link to="/" class="site-link"><el-icon><House /></el-icon> 访问主站</router-link>
          <el-dropdown trigger="click" @command="handleCommand">
            <button type="button" class="user-trigger" aria-label="打开账户菜单">
              <el-avatar :size="34" :src="auth.user?.avatar || undefined" class="avatar">{{ auth.displayName.slice(0, 1) }}</el-avatar>
              <span class="user-copy"><strong>{{ auth.displayName }}</strong><small>{{ roleLabel }}</small></span>
              <el-icon class="user-chevron"><ArrowDown /></el-icon>
            </button>
            <template #dropdown><el-dropdown-menu>
              <el-dropdown-item command="site">返回主站</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu></template>
          </el-dropdown>
        </div>
      </header>
      <main class="main"><router-view /></main>
    </div>
    <el-drawer v-model="mobileOpen" direction="ltr" size="264px" title="慧彩管理中心" append-to-body class="admin-nav-drawer">
      <AdminNavigation @navigate="mobileOpen = false" />
      <router-link to="/" class="drawer-site-link">返回主站 ↗</router-link>
    </el-drawer>
  </div>
</template>

<style scoped>
.admin-shell { display: flex; height: 100dvh; overflow: hidden; background: var(--page-bg); }
.sidebar { width: 244px; flex-shrink: 0; display: flex; flex-direction: column; background: #fff; border-right: 1px solid #e8edf5; transition: width .2s; }
.brand { height: 96px; display: flex; align-items: center; gap: 12px; padding: 0 28px; flex-shrink: 0; }
.brand-mark { display: grid; place-items: center; flex-shrink: 0; width: 38px; height: 38px; background: #3563e9; border-radius: 12px; color: white; font-size: 22px; font-weight: 650; box-shadow: 0 5px 12px #3563e925; }
.brand-copy { display: flex; flex-direction: column; gap: 4px; white-space: nowrap; }
.brand-copy strong { font-size: 15px; font-weight: 650; color: #24334e; }
.brand-copy small { font-size: 9px; color: #94a1b6; letter-spacing: .12em; }
.sidebar-scroll { flex: 1; min-height: 0; overflow-y: auto; scrollbar-width: thin; }
.sidebar-footer { display: flex; align-items: center; gap: 12px; margin: 12px 20px 20px; padding: 14px 12px; border-top: 1px solid #edf1f6; color: #78869e; font-size: 12px; white-space: nowrap; }
.sidebar-footer:hover { color: var(--brand); }
.sidebar-footer .el-icon { font-size: 18px; }
.exit-arrow { margin-left: auto; }
.workspace { display: flex; flex: 1; min-width: 0; flex-direction: column; }
.header { height: 76px; flex-shrink: 0; display: flex; align-items: center; justify-content: space-between; padding: 0 32px; background: #ffffffed; border-bottom: 1px solid #e8edf5; gap: 20px; }
.header-left, .header-right { display: flex; align-items: center; gap: 22px; min-width: 0; }
.nav-toggle { display: grid; place-items: center; width: 34px; height: 34px; border: 1px solid #e8edf5; background: white; color: #7a89a2; border-radius: 8px; cursor: pointer; font-size: 18px; flex-shrink: 0; }
.nav-toggle:hover { background: #f1f5fd; color: var(--brand); }
.breadcrumb { display: flex; align-items: center; gap: 12px; font-size: 13px; min-width: 0; }
.breadcrumb > span { color: #8b98ad; white-space: nowrap; }
.breadcrumb i { color: #c4cedd; font-style: normal; }
.breadcrumb strong { color: #374764; font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.site-link { display: flex; align-items: center; gap: 7px; font-size: 12px; color: #7d8ba2; white-space: nowrap; }
.site-link:hover { color: var(--brand); }
.user-trigger { display: flex; align-items: center; gap: 10px; border: 0; background: transparent; cursor: pointer; padding: 4px; font: inherit; text-align: left; }
.avatar { background: #eaf0ff; color: #4267bd; font-size: 14px; }
.user-copy { display: flex; flex-direction: column; gap: 2px; }
.user-copy strong { font-size: 12px; font-weight: 600; color: #344460; max-width: 110px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.user-copy small { color: #97a3b6; font-size: 10px; }
.user-chevron { font-size: 11px; color: #8d9ab0; margin-left: 8px; }
.main { flex: 1; overflow: auto; min-height: 0; padding: 30px 32px 48px; }
.nav-collapsed .sidebar { width: 76px; }
.nav-collapsed .brand { padding: 0; justify-content: center; }
.nav-collapsed .brand-copy, .nav-collapsed .sidebar-footer span { display: none; }
.nav-collapsed .sidebar-footer { margin: 12px; padding: 14px 0; justify-content: center; }
.drawer-site-link { display: block; margin: 12px 28px; padding-top: 16px; border-top: 1px solid var(--border-soft); color: var(--text-sub); font-size: 13px; }
@media (max-width: 1024px) { .header { padding: 0 22px; } .main { padding: 24px; } .site-link { display: none; } }
@media (max-width: 768px) {
  .sidebar { display: none; }
  .header { height: 64px; padding: 0 16px; gap: 12px; }
  .header-left { gap: 12px; }
  .breadcrumb > span, .breadcrumb i, .user-copy, .user-chevron { display: none; }
  .main { padding: 20px 14px 32px; }
}
</style>
<style>
.admin-nav-drawer .el-drawer__body { padding: 0; }
.admin-nav-drawer .el-drawer__header { margin-bottom: 20px; color: #263955; font-size: 16px; }
</style>
