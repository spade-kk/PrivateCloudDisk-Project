// ============================================================
// 路由配置
// ============================================================
// 采用 Vue Router 4 + History 模式，路由分为三大区域：
//   1. 官网 (/)         — 公开访问，无需登录
//   2. 登录/注册         — 认证页面
//   3. 控制台 (/app)    — 需要登录认证，包含所有业务功能页面
//
// 路由守卫 (beforeEach)：
//   检查目标路由的 meta.requiresAuth 标记，未登录时跳转登录页。
//   使用 Pinia authStore 的 isLoggedIn 状态判断登录状态。
// ============================================================

import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { useSpaceStore } from '@/stores/spaceStore'

// ============================================================
// 路由表定义
// ============================================================

const routes: RouteRecordRaw[] = [
  // ============================================================
  // 官网 — 公开页面，无需登录
  // 所有页面使用 PublicLayout 统一布局（导航栏+页脚）
  // 包含首页、产品功能、下载、定价、关于、文档、博客等营销页面
  // ============================================================
  {
    path: '/',
    component: () => import('@/components/website/PublicLayout.vue'),
    children: [
      { path: '', name: 'Home', component: () => import('@/views/website/HomeView.vue') },
      { path: 'features', name: 'Features', component: () => import('@/views/website/FeaturesView.vue') },
      { path: 'download', name: 'Download', component: () => import('@/views/website/DownloadView.vue') },
      { path: 'download/thanks', name: 'DownloadThanks', component: () => import('@/views/website/DownloadThanksView.vue') },
      { path: 'pricing', name: 'Pricing', component: () => import('@/views/website/PricingView.vue') },
      { path: 'about', name: 'About', component: () => import('@/views/website/AboutView.vue') },
      { path: 'contact', name: 'Contact', component: () => import('@/views/website/ContactView.vue') },
      { path: 'docs', name: 'Docs', component: () => import('@/views/website/DocsView.vue') },
      { path: 'docs/guide', name: 'DocsGuide', component: () => import('@/views/website/DocsGuideView.vue') },
      { path: 'docs/architecture', name: 'DocsArchitecture', component: () => import('@/views/website/DocsArchitectureView.vue') },
      { path: 'docs/deployment', name: 'DocsDeployment', component: () => import('@/views/website/DocsDeploymentView.vue') },
      { path: 'docs/development', name: 'DocsDevelopment', component: () => import('@/views/website/DocsDevelopmentView.vue') },
      { path: 'docs/api', name: 'DocsApi', component: () => import('@/views/website/DocsApiView.vue') },
      { path: 'docs/security', name: 'DocsSecurity', component: () => import('@/views/website/DocsSecurityView.vue') },
      { path: 'docs/database', name: 'DocsDatabase', component: () => import('@/views/website/DocsDatabaseView.vue') },
      { path: 'docs/customize', name: 'DocsCustomize', component: () => import('@/views/website/DocsCustomizeView.vue') },
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
      // 官网 404 兜底：匹配所有未定义的路由
      { path: ':pathMatch(.*)*', name: 'PublicNotFound', component: () => import('@/views/website/NotFoundView.vue') },
    ],
  },

  // ============================================================
  // 登录 — 已登录用户显示已登录提示页，未登录用户显示登录表单
  // LoginWrapper 组件内部根据 authStore.isLoggedIn 决定渲染内容
  // ============================================================
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginWrapper.vue'),
  },

  // ============================================================
  // 注册 — 无论是否登录均可访问
  // ============================================================
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/RegisterView.vue'),
  },

  // ============================================================
  // 分享链接访问 — 公开访问，无需登录
  // 类似百度网盘的分享链接页面，支持密码保护
  // ============================================================
  {
    path: '/share/:token',
    name: 'ShareAccess',
    component: () => import('@/views/ShareAccessView.vue'),
  },

  // ============================================================
  // 控制台 — 需要登录认证
  // 所有子路由共享 Layout 布局（侧边栏+顶栏+内容区）
  // meta.requiresAuth: true 触发路由守卫的登录检查
  // ============================================================
  {
    path: '/app',
    component: () => import('@/components/layout/Layout.vue'),
    meta: { requiresAuth: true },
    children: [
      { path: '', name: 'Dashboard', component: () => import('@/views/DashboardView.vue') },
      { path: 'search', name: 'Search', component: () => import('@/views/SearchView.vue') },
      { path: 'starred', name: 'Starred', component: () => import('@/views/StarredView.vue') },
      { path: 'tagged', name: 'Tagged', component: () => import('@/views/TaggedView.vue') },
      { path: 'recent', name: 'Recent', component: () => import('@/views/RecentView.vue') },
      { path: 'versions', name: 'Versions', component: () => import('@/views/versions/VersionsView.vue') },
      { path: 'shares', name: 'Shares', component: () => import('@/views/SharesView.vue') },
      { path: 'notifications', name: 'Notifications', component: () => import('@/views/NotificationsView.vue') },
      { path: 'transfers', name: 'Transfers', component: () => import('@/views/TransfersView.vue') },
      { path: 'trash', name: 'Trash', component: () => import('@/views/TrashView.vue') },
      { path: 'storage', name: 'Storage', component: () => import('@/views/StorageView.vue') },
      { path: 'team', name: 'Team', component: () => import('@/views/team/TeamView.vue') },
      { path: 'security', name: 'Security', component: () => import('@/views/security/SecurityView.vue') },
      { path: 'security/api-keys', name: 'ApiKeys', component: () => import('@/views/security/ApiKeysView.vue') },
      { path: 'security/change-password', name: 'ChangePassword', component: () => import('@/views/security/ChangePasswordView.vue') },
      { path: 'security/change-email', name: 'ChangeEmail', component: () => import('@/views/security/ChangeEmailView.vue') },
      { path: 'security/change-phone', name: 'ChangePhone', component: () => import('@/views/security/ChangePhoneView.vue') },
      { path: 'devices', name: 'Devices', component: () => import('@/views/DevicesView.vue') },
      { path: 'settings', name: 'Settings', component: () => import('@/views/settings/SettingsView.vue') },
      { path: 'billing', name: 'Billing', component: () => import('@/views/billing/BillingView.vue') },
      { path: 'billing/orders', name: 'BillingOrders', component: () => import('@/views/billing/OrderManagementView.vue') },
      { path: 'billing/payment', name: 'BillingPayment', component: () => import('@/views/billing/OrderPaymentView.vue') },
      { path: 'billing/payment/success', name: 'BillingPaymentSuccess', component: () => import('@/views/billing/OrderSuccessView.vue') },
      { path: 'billing/refund', name: 'BillingRefund', component: () => import('@/views/billing/OrderRefundView.vue') },
      { path: 'billing/refund/success', name: 'BillingRefundSuccess', component: () => import('@/views/billing/RefundSuccessView.vue') },
      { path: 'help', name: 'Help', component: () => import('@/views/help/HelpView.vue') },
      { path: 'profile', name: 'Profile', component: () => import('@/views/ProfileView.vue') },
      // 空间管理
      { path: 'spaces', name: 'Spaces', component: () => import('@/pages/SpaceManagement.vue') },
      // 视频播放器：通过 fileId 参数指定播放文件
      { path: 'video/:fileId', name: 'VideoPlayer', component: () => import('@/views/VideoPlayerView.vue'), meta: { requiresAuth: true } },
      // 视频/语音通话页面
      { path: 'call', name: 'Call', component: () => import('@/views/CallView.vue'), meta: { requiresAuth: true } },
      // 控制台 404 兜底
      { path: ':pathMatch(.*)*', name: 'AppNotFound', component: () => import('@/views/website/NotFoundView.vue') },
    ],
  },
]

// ============================================================
// 路由实例创建
// ============================================================

/**
 * Vue Router 实例
 *
 * 使用 HTML5 History 模式（无需 # 号），
 * 生产环境需配置 Nginx/Apache 将所有路由 fallback 到 index.html。
 */
const router = createRouter({
  history: createWebHistory(),
  routes,
})

// ============================================================
// 全局路由守卫
// ============================================================

/**
 * 前置守卫 — 登录状态检查
 *
 * 每次路由跳转前检查目标路由是否需要认证。
 * 需要认证但未登录时，重定向到 /login 页面。
 * 不需要认证的路由（官网、登录页、注册页）直接放行。
 */
router.beforeEach((to, _from, next) => {
  const authStore = useAuthStore()

  // 目标路由需要认证但用户未登录 → 跳转登录页
  if (to.meta.requiresAuth && !authStore.isLoggedIn) {
    next('/login')
    return
  }

  // 同步 URL 参数 ?space=xxx 到 spaceStore
  if (to.meta.requiresAuth && authStore.isLoggedIn) {
    const spaceStore = useSpaceStore()
    const spaceParam = to.query.space as string | undefined
    if (spaceParam && spaceParam !== spaceStore.currentSpaceId) {
      spaceStore.setCurrentSpaceFromUrl(spaceParam)
    }
  }

  next()
})

export default router