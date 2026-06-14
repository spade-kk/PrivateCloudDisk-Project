import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'

const routes = [
  // ============================================================
  // 官网 - 公开页面，无需登录
  // ============================================================
  {
    path: '/',
    component: () => import('@/components/website/PublicLayout.vue'),
    children: [
      { path: '', name: 'Home', component: () => import('@/views/website/HomeView.vue') },
      { path: 'features', name: 'Features', component: () => import('@/views/website/FeaturesView.vue') },
      { path: 'download', name: 'Download', component: () => import('@/views/website/DownloadView.vue') },
      { path: 'pricing', name: 'Pricing', component: () => import('@/views/website/PricingView.vue') },
      { path: 'about', name: 'About', component: () => import('@/views/website/AboutView.vue') },
      { path: 'contact', name: 'Contact', component: () => import('@/views/website/ContactView.vue') },
      { path: 'docs', name: 'Docs', component: () => import('@/views/website/DocsView.vue') },
      { path: 'solutions', name: 'Solutions', component: () => import('@/views/website/SolutionsView.vue') },
      { path: 'case-studies', name: 'CaseStudies', component: () => import('@/views/website/CaseStudiesView.vue') },
      { path: 'blog', name: 'Blog', component: () => import('@/views/website/BlogView.vue') },
      { path: 'careers', name: 'Careers', component: () => import('@/views/website/CareersView.vue') },
      { path: 'partners', name: 'Partners', component: () => import('@/views/website/PartnersView.vue') },
      { path: 'security-center', name: 'SecurityCenter', component: () => import('@/views/website/SecurityCenterView.vue') },
      { path: 'changelog', name: 'Changelog', component: () => import('@/views/website/ChangelogView.vue') },
      { path: 'status', name: 'Status', component: () => import('@/views/website/StatusView.vue') },
      { path: 'privacy', name: 'Privacy', component: () => import('@/views/website/PrivacyView.vue') },
      { path: 'terms', name: 'Terms', component: () => import('@/views/website/TermsView.vue') },
      { path: 'press', name: 'Press', component: () => import('@/views/website/PressView.vue') },
      // 官网 404 兜底
      { path: ':pathMatch(.*)*', name: 'PublicNotFound', component: () => import('@/views/website/NotFoundView.vue') },
    ],
  },

  // ============================================================
  // 登录 - 已登录显示已登录提示页，未登录显示登录表单
  // ============================================================
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginWrapper.vue'),
  },

  // ============================================================
  // 注册 - 无论是否登录均可访问
  // ============================================================
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/RegisterView.vue'),
  },

  // ============================================================
  // 控制台 - 需要登录认证
  // ============================================================
  {
    path: '/app',
    component: () => import('@/components/layout/Layout.vue'),
    meta: { requiresAuth: true },
    children: [
      { path: '', name: 'Dashboard', component: () => import('@/views/DashboardView.vue') },
      { path: 'search', name: 'Search', component: () => import('@/views/SearchView.vue') },
      { path: 'starred', name: 'Starred', component: () => import('@/views/StarredView.vue') },
      { path: 'notifications', name: 'Notifications', component: () => import('@/views/NotificationsView.vue') },
      { path: 'shares', name: 'Shares', component: () => import('@/views/SharesView.vue') },
      { path: 'trash', name: 'Trash', component: () => import('@/views/TrashView.vue') },
      { path: 'transfers', name: 'Transfers', component: () => import('@/views/TransfersView.vue') },
      { path: 'versions', name: 'Versions', component: () => import('@/views/versions/VersionsView.vue') },
      { path: 'team', name: 'Team', component: () => import('@/views/team/TeamView.vue') },
      { path: 'admin', name: 'Admin', component: () => import('@/views/admin/AdminView.vue'), meta: { requiresAdmin: true } },
      { path: 'analytics', name: 'Analytics', component: () => import('@/views/analytics/AnalyticsView.vue') },
      { path: 'security', name: 'Security', component: () => import('@/views/security/SecurityView.vue') },
      { path: 'security/api-keys', name: 'ApiKeys', component: () => import('@/views/security/ApiKeysView.vue') },
      { path: 'activity', name: 'ActivityLog', component: () => import('@/views/ActivityLogView.vue') },
      { path: 'settings', name: 'Settings', component: () => import('@/views/settings/SettingsView.vue') },
      { path: 'billing', name: 'Billing', component: () => import('@/views/billing/BillingView.vue') },
      { path: 'help', name: 'Help', component: () => import('@/views/help/HelpView.vue') },
      { path: 'profile', name: 'Profile', component: () => import('@/views/ProfileView.vue') },
      // 控制台 404 兜底
      { path: ':pathMatch(.*)*', name: 'AppNotFound', component: () => import('@/views/website/NotFoundView.vue') },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to, from, next) => {
  const authStore = useAuthStore()
  // 需要认证但未登录 -> 跳转登录
  if (to.meta.requiresAuth && !authStore.isLoggedIn) {
    next('/login')
    return
  }
  next()
})

export default router
