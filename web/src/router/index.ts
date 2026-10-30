import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    requiresAuth?: boolean
    requiresAdmin?: boolean
  }
}

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    {
      path: '/',
      component: () => import('@/layouts/SiteLayout.vue'),
      children: [
        { path: '', name: 'home', component: () => import('@/views/site/HomeView.vue'), meta: { title: '首页' } },
        {
          path: 'profile',
          name: 'profile',
          component: () => import('@/views/site/ProfileView.vue'),
          meta: { title: '个人中心', requiresAuth: true },
        },
        { path: 'predictions', name: 'predictions', component: () => import('@/views/site/PredictionListView.vue'), meta: { title: '预测广场' } },
        { path: 'prediction/:id', name: 'prediction-detail', component: () => import('@/views/site/PredictionDetailView.vue'), meta: { title: '预测详情' } },
        { path: 'rank', name: 'rank', component: () => import('@/views/site/RankView.vue'), meta: { title: '创作者榜单' } },
        { path: 'random', name: 'random', component: () => import('@/views/site/RandomView.vue'), meta: { title: '随机预测' } },
        { path: 'creator/:id', name: 'creator-home', component: () => import('@/views/site/CreatorHomeView.vue'), meta: { title: '创作者主页' } },
        {
          path: 'publish',
          name: 'publish',
          component: () => import('@/views/site/PublishView.vue'),
          meta: { title: '发布预测', requiresAuth: true },
        },
        {
          path: 'creator/apply',
          name: 'creator-apply',
          component: () => import('@/views/site/CreatorApplyView.vue'),
          meta: { title: '创作者认证', requiresAuth: true },
        },
        {
          path: 'creator/dashboard',
          name: 'creator-dashboard',
          component: () => import('@/views/site/CreatorDashboardView.vue'),
          meta: { title: '创作者看板', requiresAuth: true },
        },
        {
          path: 'my/points',
          name: 'my-points',
          component: () => import('@/views/site/MyPointsView.vue'),
          meta: { title: '我的积分', requiresAuth: true },
        },
        {
          path: 'rewards',
          name: 'rewards',
          component: () => import('@/views/site/RewardView.vue'),
          meta: { title: '积分兑换', requiresAuth: true },
        },
        {
          path: 'messages',
          name: 'messages',
          component: () => import('@/views/site/MessageView.vue'),
          meta: { title: '消息中心', requiresAuth: true },
        },
        {
          path: 'creator/earnings',
          name: 'creator-earnings',
          component: () => import('@/views/site/EarningsView.vue'),
          meta: { title: '我的收益', requiresAuth: true },
        },
        {
          path: 'legal/:type',
          name: 'legal',
          component: () => import('@/views/site/LegalView.vue'),
          meta: { title: '协议与规则' },
        },
        {
          path: 'my/purchased',
          name: 'my-purchased',
          component: () => import('@/views/site/MyPurchasedView.vue'),
          meta: { title: '我的解锁', requiresAuth: true },
        },
        {
          path: 'notices',
          name: 'notices',
          component: () => import('@/views/site/NoticeView.vue'),
          meta: { title: '通知中心', requiresAuth: true },
        },
        {
          path: 'feedback',
          name: 'feedback',
          component: () => import('@/views/site/FeedbackView.vue'),
          meta: { title: '意见反馈', requiresAuth: true },
        },
      ],
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/site/LoginView.vue'),
      meta: { title: '登录 / 注册' },
    },
    {
      path: '/admin/login',
      name: 'admin-login',
      component: () => import('@/views/admin/AdminLoginView.vue'),
      meta: { title: '管理后台登录' },
    },
    {
      path: '/admin',
      component: () => import('@/layouts/AdminLayout.vue'),
      meta: { requiresAdmin: true },
      children: [
        {
          path: '',
          name: 'admin-dashboard',
          component: () => import('@/views/admin/DashboardView.vue'),
          meta: { title: '仪表盘', requiresAdmin: true },
        },
        {
          path: 'users',
          name: 'admin-users',
          component: () => import('@/views/admin/UserListView.vue'),
          meta: { title: '用户管理', requiresAdmin: true },
        },
        {
          path: 'logs',
          name: 'admin-logs',
          component: () => import('@/views/admin/LoginLogView.vue'),
          meta: { title: '登录日志', requiresAdmin: true },
        },
        { path: 'audit', name: 'admin-audit', component: () => import('@/views/admin/PredictionAuditView.vue'), meta: { title: '预测审核', requiresAdmin: true } },
        { path: 'issues', name: 'admin-issues', component: () => import('@/views/admin/IssueManageView.vue'), meta: { title: '期次管理', requiresAdmin: true } },
        { path: 'creators', name: 'admin-creators', component: () => import('@/views/admin/CreatorAuditView.vue'), meta: { title: '创作者审核', requiresAdmin: true } },
        { path: 'finance', name: 'admin-finance', component: () => import('@/views/admin/FinanceView.vue'), meta: { title: '财务与配置', requiresAdmin: true } },
        { path: 'rewards', name: 'admin-rewards', component: () => import('@/views/admin/AdminRewardView.vue'), meta: { title: '权益管理', requiresAdmin: true } },
        { path: 'reports', name: 'admin-reports', component: () => import('@/views/admin/ReportManageView.vue'), meta: { title: '举报与注销', requiresAdmin: true } },
        {
          path: 'notices',
          name: 'admin-notices',
          component: () => import('@/views/admin/NoticeManageView.vue'),
          meta: { title: '通知公告', requiresAdmin: true },
        },
        {
          path: 'feedback',
          name: 'admin-feedback',
          component: () => import('@/views/admin/FeedbackManageView.vue'),
          meta: { title: '用户反馈', requiresAdmin: true },
        },
      ],
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/error/NotFoundView.vue'),
      meta: { title: '页面不存在' },
    },
  ],
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  document.title = to.meta.title ? `${to.meta.title} · 慧彩预测` : '慧彩预测'

  if (to.meta.requiresAdmin) {
    if (!auth.isLoggedIn) {
      // 统一走前台登录页，登录后按角色自动进入后台
      return { name: 'login', query: { redirect: to.fullPath } }
    }
    if (!auth.isStaff) {
      ElMessage.error('该账号没有管理后台权限')
      return { name: 'home' }
    }
    return true
  }

  if (to.meta.requiresAuth && !auth.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  return true
})

export default router
