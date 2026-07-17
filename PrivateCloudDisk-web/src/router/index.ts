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
//
// 页面标题管理 (afterEach)：
//   所有路由通过 meta.title 配置页面标题，路由切换时自动更新
//   document.title，格式为 "页面标题 - 私有云盘"。
//   meta.title 为必填项，确保浏览器标签页和浏览历史记录清晰可辨。
// ============================================================

import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { useSpaceStore } from '@/stores/spaceStore'

/** 基础标题后缀 */
const TITLE_SUFFIX = '私有云盘'

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
      { path: '', name: 'Home', component: () => import('@/views/website/HomeView.vue'), meta: { title: '首页' } },
      { path: 'features', name: 'Features', component: () => import('@/views/website/FeaturesView.vue'), meta: { title: '产品功能' } },
      { path: 'download', name: 'Download', component: () => import('@/views/website/DownloadView.vue'), meta: { title: '下载' } },
      { path: 'download/thanks', name: 'DownloadThanks', component: () => import('@/views/website/DownloadThanksView.vue'), meta: { title: '下载 - 感谢' } },
      { path: 'pricing', name: 'Pricing', component: () => import('@/views/website/PricingView.vue'), meta: { title: '定价' } },
      { path: 'about', name: 'About', component: () => import('@/views/website/AboutView.vue'), meta: { title: '关于我们' } },
      { path: 'contact', name: 'Contact', component: () => import('@/views/website/ContactView.vue'), meta: { title: '联系我们' } },
      { path: 'docs', name: 'Docs', component: () => import('@/views/website/DocsView.vue'), meta: { title: '文档' } },
      { path: 'docs/guide', name: 'DocsGuide', component: () => import('@/views/website/DocsGuideView.vue'), meta: { title: '文档 - 快速入门' } },
      { path: 'docs/architecture', name: 'DocsArchitecture', component: () => import('@/views/website/DocsArchitectureView.vue'), meta: { title: '文档 - 系统架构' } },
      { path: 'docs/deployment', name: 'DocsDeployment', component: () => import('@/views/website/DocsDeploymentView.vue'), meta: { title: '文档 - 部署指南' } },
      { path: 'docs/development', name: 'DocsDevelopment', component: () => import('@/views/website/DocsDevelopmentView.vue'), meta: { title: '文档 - 开发指南' } },
      { path: 'docs/api', name: 'DocsApi', component: () => import('@/views/website/DocsApiView.vue'), meta: { title: '文档 - API 文档' } },
      { path: 'docs/security', name: 'DocsSecurity', component: () => import('@/views/website/DocsSecurityView.vue'), meta: { title: '文档 - 安全' } },
      { path: 'docs/database', name: 'DocsDatabase', component: () => import('@/views/website/DocsDatabaseView.vue'), meta: { title: '文档 - 数据库' } },
      { path: 'docs/customize', name: 'DocsCustomize', component: () => import('@/views/website/DocsCustomizeView.vue'), meta: { title: '文档 - 定制化' } },
      { path: 'solutions', name: 'Solutions', component: () => import('@/views/website/SolutionsView.vue'), meta: { title: '解决方案' } },
      { path: 'case-studies', name: 'CaseStudies', component: () => import('@/views/website/CaseStudiesView.vue'), meta: { title: '客户案例' } },
      { path: 'blog', name: 'Blog', component: () => import('@/views/website/BlogView.vue'), meta: { title: '博客' } },
      { path: 'careers', name: 'Careers', component: () => import('@/views/website/CareersView.vue'), meta: { title: '加入我们' } },
      { path: 'partners', name: 'Partners', component: () => import('@/views/website/PartnersView.vue'), meta: { title: '合作伙伴' } },
      { path: 'security-center', name: 'SecurityCenter', component: () => import('@/views/website/SecurityCenterView.vue'), meta: { title: '安全中心' } },
      { path: 'changelog', name: 'Changelog', component: () => import('@/views/website/ChangelogView.vue'), meta: { title: '更新日志' } },
      { path: 'status', name: 'Status', component: () => import('@/views/website/StatusView.vue'), meta: { title: '服务状态' } },
      { path: 'privacy', name: 'Privacy', component: () => import('@/views/website/PrivacyView.vue'), meta: { title: '隐私政策' } },
      { path: 'terms', name: 'Terms', component: () => import('@/views/website/TermsView.vue'), meta: { title: '服务条款' } },
      { path: 'press', name: 'Press', component: () => import('@/views/website/PressView.vue'), meta: { title: '新闻中心' } },
      // 官网 404 兜底：匹配所有未定义的路由
      { path: ':pathMatch(.*)*', name: 'PublicNotFound', component: () => import('@/views/website/NotFoundView.vue'), meta: { title: '404 - 页面未找到' } },
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
    meta: { title: '登录' },
  },

  // ============================================================
  // 注册 — 无论是否登录均可访问
  // ============================================================
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/RegisterView.vue'),
    meta: { title: '注册' },
  },

  // ============================================================
  // 分享链接访问 — 公开访问，无需登录
  // 类似百度网盘的分享链接页面，支持密码保护
  // ============================================================
  {
    path: '/share/:token',
    name: 'ShareAccess',
    component: () => import('@/views/ShareAccessView.vue'),
    meta: { title: '分享文件' },
  },

  // ============================================================
  // 控制台 — 需要登录认证
  // 所有子路由共享 Layout 布局（侧边栏+顶栏+内容区）
  // meta.requiresAuth: true 触发路由守卫的登录检查
  // meta.title 为每个子页面配置专属标题，切换时自动更新浏览器标签页
  // ============================================================
  {
    path: '/app',
    component: () => import('@/components/layout/Layout.vue'),
    meta: { requiresAuth: true },
    children: [
      { path: '', name: 'Dashboard', component: () => import('@/views/DashboardView.vue'), meta: { title: '控制面板', requiresAuth: true } },
      { path: 'search', name: 'Search', component: () => import('@/views/SearchView.vue'), meta: { title: '搜索', requiresAuth: true } },
      { path: 'starred', name: 'Starred', component: () => import('@/views/StarredView.vue'), meta: { title: '收藏', requiresAuth: true } },
      { path: 'tagged', name: 'Tagged', component: () => import('@/views/TaggedView.vue'), meta: { title: '标签管理', requiresAuth: true } },
      { path: 'recent', name: 'Recent', component: () => import('@/views/RecentView.vue'), meta: { title: '最近使用', requiresAuth: true } },
      { path: 'versions', name: 'Versions', component: () => import('@/views/versions/VersionsView.vue'), meta: { title: '版本历史', requiresAuth: true } },
      { path: 'shares', name: 'Shares', component: () => import('@/views/SharesView.vue'), meta: { title: '分享管理', requiresAuth: true } },
      { path: 'notifications', name: 'Notifications', component: () => import('@/views/NotificationsView.vue'), meta: { title: '通知中心', requiresAuth: true } },
      { path: 'transfers', name: 'Transfers', component: () => import('@/views/TransfersView.vue'), meta: { title: '传输管理', requiresAuth: true } },
      { path: 'trash', name: 'Trash', component: () => import('@/views/TrashView.vue'), meta: { title: '回收站', requiresAuth: true } },
      { path: 'storage', name: 'Storage', component: () => import('@/views/StorageView.vue'), meta: { title: '存储空间', requiresAuth: true } },
      { path: 'team', name: 'Team', component: () => import('@/views/team/TeamView.vue'), meta: { title: '团队管理', requiresAuth: true } },
      { path: 'security', name: 'Security', component: () => import('@/views/security/SecurityView.vue'), meta: { title: '安全设置', requiresAuth: true } },
      { path: 'security/api-keys', name: 'ApiKeys', component: () => import('@/views/security/ApiKeysView.vue'), meta: { title: 'API 密钥', requiresAuth: true } },
      { path: 'security/change-password', name: 'ChangePassword', component: () => import('@/views/security/ChangePasswordView.vue'), meta: { title: '修改密码', requiresAuth: true } },
      { path: 'security/change-email', name: 'ChangeEmail', component: () => import('@/views/security/ChangeEmailView.vue'), meta: { title: '修改邮箱', requiresAuth: true } },
      { path: 'security/change-phone', name: 'ChangePhone', component: () => import('@/views/security/ChangePhoneView.vue'), meta: { title: '修改手机号', requiresAuth: true } },
      { path: 'devices', name: 'Devices', component: () => import('@/views/DevicesView.vue'), meta: { title: '设备管理', requiresAuth: true } },
      { path: 'settings', name: 'Settings', component: () => import('@/views/settings/SettingsView.vue'), meta: { title: '设置', requiresAuth: true } },
      { path: 'billing', name: 'Billing', component: () => import('@/views/billing/BillingView.vue'), meta: { title: '计费中心', requiresAuth: true } },
      { path: 'billing/orders', name: 'BillingOrders', component: () => import('@/views/billing/OrderManagementView.vue'), meta: { title: '订单管理', requiresAuth: true } },
      { path: 'billing/payment', name: 'BillingPayment', component: () => import('@/views/billing/OrderPaymentView.vue'), meta: { title: '订单支付', requiresAuth: true } },
      { path: 'billing/payment/success', name: 'BillingPaymentSuccess', component: () => import('@/views/billing/OrderSuccessView.vue'), meta: { title: '支付成功', requiresAuth: true } },
      { path: 'billing/refund', name: 'BillingRefund', component: () => import('@/views/billing/OrderRefundView.vue'), meta: { title: '退款申请', requiresAuth: true } },
      { path: 'billing/refund/success', name: 'BillingRefundSuccess', component: () => import('@/views/billing/RefundSuccessView.vue'), meta: { title: '退款成功', requiresAuth: true } },
      { path: 'help', name: 'Help', component: () => import('@/views/help/HelpView.vue'), meta: { title: '帮助中心', requiresAuth: true } },
      { path: 'profile', name: 'Profile', component: () => import('@/views/ProfileView.vue'), meta: { title: '个人信息', requiresAuth: true } },
      // 空间管理
      { path: 'spaces', name: 'Spaces', component: () => import('@/views/SpaceManagementView.vue'), meta: { title: '空间管理', requiresAuth: true } },
      // 视频播放器：通过 fileId 参数指定播放文件
      { path: 'video/:fileId', name: 'VideoPlayer', component: () => import('@/views/VideoPlayerView.vue'), meta: { title: '视频播放', requiresAuth: true } },
      // 文档预览 — PDF 独立预览页面（缩放、旋转、导航、搜索、缩略图、打印）
      { path: 'preview/pdf/:fileId', name: 'PDFPreview', component: () => import('@/views/preview/PDFPreviewView.vue'), meta: { title: 'PDF 预览', requiresAuth: true } },
      // 文档预览 — Word 独立预览页面（文档布局、页面视图、大纲导航）
      { path: 'preview/word/:fileId', name: 'WordPreview', component: () => import('@/views/preview/WordPreviewView.vue'), meta: { title: 'Word 预览', requiresAuth: true } },
      // 文档预览 — PPT 独立预览页面（幻灯片布局、分页导航、全屏演示）
      { path: 'preview/ppt/:fileId', name: 'PPTPreview', component: () => import('@/views/preview/PPTPreviewView.vue'), meta: { title: 'PPT 预览', requiresAuth: true } },
      // 文档预览 — Excel 独立预览页面（网格布局、工作表切换、行列导航）
      { path: 'preview/excel/:fileId', name: 'ExcelPreview', component: () => import('@/views/preview/ExcelPreviewView.vue'), meta: { title: 'Excel 预览', requiresAuth: true } },
      // 代码预览 — 独立预览页面（语法高亮、IDE 悬停提示、代码结构导航、搜索、行跳转）
      { path: 'preview/code/:fileId', name: 'CodePreview', component: () => import('@/views/preview/CodePreviewView.vue'), meta: { title: '代码预览', requiresAuth: true } },
      // Markdown 预览 — 独立预览页面（代码高亮、目录导航、Mermaid 图表、KaTeX 公式、全文搜索）
      { path: 'preview/markdown/:fileId', name: 'MarkdownPreview', component: () => import('@/views/preview/MarkdownPreviewView.vue'), meta: { title: 'Markdown 预览', requiresAuth: true } },
      // 压缩包预览 — 独立预览页面（目录结构树、文件元数据、层级展开折叠）
      { path: 'preview/archive/:fileId', name: 'ArchivePreview', component: () => import('@/views/preview/ArchivePreviewView.vue'), meta: { title: '压缩包预览', requiresAuth: true } },
      // 视频/语音通话页面
      { path: 'call', name: 'Call', component: () => import('@/views/CallView.vue'), meta: { title: '通话', requiresAuth: true } },
      // 控制台 404 兜底
      { path: ':pathMatch(.*)*', name: 'AppNotFound', component: () => import('@/views/website/NotFoundView.vue'), meta: { title: '404 - 页面未找到', requiresAuth: true } },
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

/**
 * 后置守卫 — 页面标题动态更新
 *
 * 每次路由切换完成后，根据目标路由的 meta.title 更新浏览器标签页标题。
 * 标题格式: "页面标题 | 私有云盘"
 * 确保浏览器历史记录中各页面标题清晰可辨，符合企业级项目标准。
 */
router.afterEach((to) => {
  const pageTitle = to.meta.title as string | undefined
  if (pageTitle) {
    document.title = `${TITLE_SUFFIX} - ${pageTitle}`
  } else {
    document.title = TITLE_SUFFIX
  }
})

export default router