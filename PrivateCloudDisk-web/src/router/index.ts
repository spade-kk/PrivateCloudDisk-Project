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
      // 插件生态开发者文档中心：公开展示 SDK、生命周期、工作流 DSL 与安全边界。
      { path: 'docs/plugins', name: 'PluginDocs', component: () => import('@/views/website/PluginDocsView.vue'), meta: { title: '插件与自动化开发文档' } },
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

  // 公开空间（仓库）独立工作区：必须登录，且不复用控制台 Layout/文件浏览器。
  { path: '/repo/:spaceId', name: 'PublicSpaceRepository', component: () => import('@/views/public-space/PublicSpaceView.vue'), meta: { title: '公开仓库', requiresAuth: true, publicRepository: true } },
  { path: '/repo/:spaceId/settings', name: 'PublicSpaceRepositorySettings', component: () => import('@/views/public-space/PublicSpaceSettingsView.vue'), meta: { title: '仓库设置', requiresAuth: true, publicRepository: true } },
  { path: '/git/social', name: 'GitSocialRepositories', component: () => import('@/views/public-space/GitSocialRepositoriesView.vue'), meta: { title: '我的 Star 与 Fork', requiresAuth: true, publicRepository: true } },
  { path: '/user/:username', name: 'PublicUserProfile', component: () => import('@/views/public-space/UserProfileView.vue'), meta: { title: '用户主页', requiresAuth: true, publicRepository: true } },
  { path: '/explore', name: 'ExplorePublicSpaces', component: () => import('@/views/public-space/ExplorePublicSpacesView.vue'), meta: { title: '探索公开仓库', requiresAuth: true, publicRepository: true } },

  // 空间协作中心独立路由：与控制台文件浏览区隔离，所有页面仍需要登录。
  { path: '/teamwork', name: 'Teamwork', component: () => import('@/views/team/TeamworkView.vue'), meta: { title: '团队协作', requiresAuth: true, collaborationWorkspace: true } },
  { path: '/teamwork/space/:spaceId', name: 'TeamworkSpacePreview', component: () => import('@/views/team/TeamworkSpacePreviewView.vue'), meta: { title: '空间详情', requiresAuth: true, collaborationWorkspace: true } },
  { path: '/space/manage', name: 'SpaceManage', component: () => import('@/views/SpaceManagementView.vue'), meta: { title: '空间管理', requiresAuth: true, collaborationWorkspace: true } },
  { path: '/space/:spaceId/members', name: 'SpaceMembers', component: () => import('@/views/space/SpaceMembersView.vue'), meta: { title: '空间成员', requiresAuth: true, collaborationWorkspace: true } },
  { path: '/space/:spaceId/settings', name: 'SpaceSettings', component: () => import('@/views/space/SpaceSettingsView.vue'), meta: { title: '空间设置', requiresAuth: true, collaborationWorkspace: true } },
  { path: '/space/:spaceId/members/approvals', name: 'SpaceApprovals', component: () => import('@/views/space/SpaceApprovalsView.vue'), meta: { title: '加入审批', requiresAuth: true, collaborationWorkspace: true } },
  { path: '/space/:spaceId/plugins', name: 'SpacePlugins', component: () => import('@/views/plugins/SpacePluginManagementView.vue'), meta: { title: '空间插件管理', requiresAuth: true, collaborationWorkspace: true } },

  // 联系人管理使用独立工作区，消息中心仍保留在 /app Layout 内。
  { path: '/im/add-friend', name: 'ImAddFriend', component: () => import('@/views/im/AddFriendView.vue'), meta: { title: '添加好友', requiresAuth: true, imWorkspace: true } },
  { path: '/im/friend-requests', name: 'ImFriendRequests', component: () => import('@/views/im/FriendRequestsView.vue'), meta: { title: '好友申请', requiresAuth: true, imWorkspace: true } },
  // GROUP-CHAT-20260810 [4.1]：独立创建群聊工作区，避免在窄右侧面板中完成多成员选择流程。
  { path: '/im/create-group', name: 'ImCreateGroup', component: () => import('@/views/im/CreateGroupView.vue'), meta: { title: '创建群聊', requiresAuth: true, imWorkspace: true } },

  // 开发模式图标目录：用于验证文件类型映射与未知后缀 SVG，不进入普通导航。
  { path: '/dev/file-icons', name: 'FileIconPreview', component: () => import('@/views/dev/FileIconPreviewView.vue'), meta: { title: '文件图标预览' } },

  // ============================================================
  // 独立文件预览工作区
  // 预览路由位于 /app Layout 之外，不渲染控制台侧栏和菜单，为播放器、文档工具栏
  // 与后续协作能力保留完整视口。所有页面通过 fileId 路由参数识别资源。
  // ============================================================
  { path: '/preview/video/:fileId', name: 'VideoPlayer', component: () => import('@/views/VideoPlayerView.vue'), meta: { title: '视频播放', requiresAuth: true, previewWorkspace: true } },
  { path: '/preview/image/:fileId', name: 'ImagePreview', component: () => import('@/views/preview/ImagePreviewView.vue'), meta: { title: '图片预览', requiresAuth: true, previewWorkspace: true } },
  { path: '/preview/pdf/:fileId', name: 'PDFPreview', component: () => import('@/views/preview/PDFPreviewView.vue'), meta: { title: 'PDF 预览', requiresAuth: true, previewWorkspace: true } },
  { path: '/preview/word/:fileId', name: 'WordPreview', component: () => import('@/views/preview/WordPreviewView.vue'), meta: { title: 'Word 预览', requiresAuth: true, previewWorkspace: true } },
  { path: '/preview/ppt/:fileId', name: 'PPTPreview', component: () => import('@/views/preview/PPTPreviewView.vue'), meta: { title: 'PPT 预览', requiresAuth: true, previewWorkspace: true } },
  { path: '/preview/excel/:fileId', name: 'ExcelPreview', component: () => import('@/views/preview/ExcelPreviewView.vue'), meta: { title: 'Excel 预览', requiresAuth: true, previewWorkspace: true } },
  { path: '/preview/code/:fileId', name: 'CodePreview', component: () => import('@/views/preview/CodePreviewView.vue'), meta: { title: '代码预览', requiresAuth: true, previewWorkspace: true } },
  { path: '/preview/markdown/:fileId', name: 'MarkdownPreview', component: () => import('@/views/preview/MarkdownPreviewView.vue'), meta: { title: 'Markdown 预览', requiresAuth: true, previewWorkspace: true } },
  { path: '/preview/archive/:fileId', name: 'ArchivePreview', component: () => import('@/views/preview/ArchivePreviewView.vue'), meta: { title: '压缩包预览', requiresAuth: true, previewWorkspace: true } },

  // 插件/工作流独立开发工作区：不渲染控制台侧栏，适合桌面端多面板 IDE；旧 /app 路由继续保留兼容。
  { path: '/developer/plugins/new/:type(cloud|local)', name: 'DeveloperPluginCreate', component: () => import('@/views/plugins/PluginIdeView.vue'), meta: { title: '插件开发工作区', requiresAuth: true, ideWorkspace: true } },
  { path: '/developer/plugins/:pluginId/edit', name: 'DeveloperPluginEdit', component: () => import('@/views/plugins/PluginIdeView.vue'), meta: { title: '编辑插件', requiresAuth: true, ideWorkspace: true } },
  // [PLUGIN-EXEC-OBS-001] 独立详情工作区不复用控制台窄内容列，日志与审计可获得完整视口。
  { path: '/plugins/:pluginId/executions/:executionId', name: 'PluginExecutionDetail', component: () => import('@/views/plugins/PluginExecutionDetailView.vue'), meta: { title: '插件执行详情', requiresAuth: true } },
  { path: '/developer/marketplace/plugins/:pluginId', name: 'DeveloperPluginMarketplaceDetail', component: () => import('@/views/plugins/PluginMarketplaceDetailView.vue'), meta: { title: '插件详情', requiresAuth: true } },
  { path: '/developer/workflows/new', name: 'DeveloperWorkflowCreate', component: () => import('@/views/workflows/WorkflowEditorView.vue'), meta: { title: '工作流开发工作区', requiresAuth: true, ideWorkspace: true } },
  { path: '/developer/workflows/:workflowId/edit', name: 'DeveloperWorkflowEdit', component: () => import('@/views/workflows/WorkflowEditorView.vue'), meta: { title: '编辑工作流', requiresAuth: true, ideWorkspace: true } },

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
      // 原 NotificationsView 保留用于代码回溯；
      // 路由切换到独立消息中心视图，仍作为 Layout 子路由保留平台主菜单。
      { path: 'notifications', name: 'Notifications', component: () => import('@/views/MessageCenterView.vue'), meta: { title: '消息中心', requiresAuth: true } },
      { path: 'transfers', name: 'Transfers', component: () => import('@/views/TransfersView.vue'), meta: { title: '传输管理', requiresAuth: true } },
      { path: 'trash', name: 'Trash', component: () => import('@/views/TrashView.vue'), meta: { title: '回收站', requiresAuth: true } },
      { path: 'storage', name: 'Storage', component: () => import('@/views/StorageView.vue'), meta: { title: '存储空间', requiresAuth: true } },
      // 旧路径保留，页面主体迁移至独立团队协作中心。
      { path: 'team', name: 'Team', component: () => import('@/views/team/TeamworkView.vue'), meta: { title: '团队协作', requiresAuth: true } },
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
      // 插件生态与自动化工作流：均复用全局空间上下文和 X-Space-Id 请求头。
      { path: 'plugins', name: 'PluginManagement', component: () => import('@/views/plugins/PluginManagementView.vue'), meta: { title: '插件管理', requiresAuth: true } },
      // [IDE-CLEANUP-001] 旧 PluginEditorView 已移除，插件创建统一进入专业 IDE。
      { path: 'plugins/new/:type(cloud|local)', name: 'PluginCreate', component: () => import('@/views/plugins/PluginIdeView.vue'), meta: { title: '创建插件', requiresAuth: true } },
      { path: 'plugins/:pluginId/executions', name: 'PluginExecutions', component: () => import('@/views/plugins/PluginExecutionsView.vue'), meta: { title: '插件执行记录', requiresAuth: true } },
      { path: 'plugin-market', name: 'PluginMarketplace', component: () => import('@/views/plugins/PluginMarketplaceView.vue'), meta: { title: '插件市场', requiresAuth: true } },
      { path: 'space-tools', name: 'SpacePluginManagement', component: () => import('@/views/plugins/SpacePluginManagementView.vue'), meta: { title: '空间工具', requiresAuth: true } },
      { path: 'spaces/:spaceId/automation', name: 'SpaceAutomation', component: () => import('@/views/plugins/SpacePluginManagementView.vue'), meta: { title: '空间插件管理', requiresAuth: true } },
      // [AI-AGENT-FE-001] 企业 Agent 会话页；所有资产操作仍由后端 Capability Hub 代理，
      // 前端只消费会话 REST/SSE，不持有模型或服务间凭据。
      { path: 'ai', name: 'AiAssistant', component: () => import('@/views/AiAssistantView.vue'), meta: { title: 'AI 助手', requiresAuth: true } },
      { path: 'workflows', name: 'WorkflowManagement', component: () => import('@/views/workflows/WorkflowManagementView.vue'), meta: { title: '工作流管理', requiresAuth: true } },
      { path: 'workflows/new', name: 'WorkflowCreate', component: () => import('@/views/workflows/WorkflowEditorView.vue'), meta: { title: '创建工作流', requiresAuth: true } },
      { path: 'workflows/:workflowId/edit', name: 'WorkflowEdit', component: () => import('@/views/workflows/WorkflowEditorView.vue'), meta: { title: '编辑工作流', requiresAuth: true } },
      { path: 'workflow-market', name: 'WorkflowMarketplace', component: () => import('@/views/workflows/WorkflowMarketplaceView.vue'), meta: { title: '工作流市场', requiresAuth: true } },
      { path: 'workflow-market/:workflowId', name: 'WorkflowMarketplaceDetail', component: () => import('@/views/workflows/WorkflowMarketplaceDetailView.vue'), meta: { title: '工作流模板详情', requiresAuth: true } },
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
router.beforeEach(async (to) => {
  const authStore = useAuthStore()

  // 目标路由需要认证但用户未登录 → 跳转登录页
  if (to.meta.requiresAuth && !authStore.isLoggedIn) {
    return '/login'
  }

  // 统一同步 URL 参数 ?space=xxx 到 spaceStore。
  // 原行为：仅写入任意 space 参数，未校验、未回退、未规范化历史参数。
  // 新行为：初始化优先读取 URL，校验无效空间后回退个人空间并 replace URL，保证前进/后退可恢复。
  if (to.meta.requiresAuth && authStore.isLoggedIn) {
    const spaceStore = useSpaceStore()
    // 公开仓库/独立预览通过页面 API 显式携带 spaceId，不能污染控制台当前工作空间。
    if (!to.meta.publicRepository && !to.meta.previewWorkspace && !to.meta.collaborationWorkspace) {
      const requested = (to.query.space_id || to.query.space) as string | undefined
      if (!spaceStore.initialized) await spaceStore.initSpaces(requested)
      const resolved = spaceStore.setCurrentSpaceFromUrl(requested || null)
      const canonical = { ...to.query } as Record<string, string | string[] | undefined>
      delete canonical.space_id
      if (resolved) canonical.space = resolved
      const currentQuery = JSON.stringify(to.query)
      const canonicalQuery = JSON.stringify(canonical)
      if (currentQuery !== canonicalQuery) {
        return { path: to.path, query: canonical, hash: to.hash }
      }
    } else if (!to.meta.publicRepository && !to.meta.previewWorkspace && to.params.spaceId) {
      // 成员/设置/审批页以 path spaceId 作为请求上下文，
      // 先校验已加入空间，再让拦截器注入同一个 X-Space-Id，防止旧空间头污染写操作。
      const spaceStore = useSpaceStore()
      if (!spaceStore.initialized) await spaceStore.initSpaces()
      spaceStore.setCurrentSpaceFromUrl(String(to.params.spaceId))
    }
  }
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
