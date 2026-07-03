// ============================================================
// 通用 API 响应类型
// ============================================================

/** 统一 API 响应格式 */
export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
  success: boolean
}

/** 分页响应格式 */
export interface PageResult<T> {
  records: T[]
  list: T[]
  total: number
  page: number
  pageSize: number
}

/** 分页请求参数 */
export interface PageParams {
  page: number
  pageSize: number
  [key: string]: unknown
}

// ============================================================
// 管理员认证
// ============================================================

export interface AdminUser {
  id: string
  userId: string
  account: string
  name: string
  email: string
  phoneNumber: string
  role: 'SUPER_ADMIN' | 'ADMIN' | 'MODERATOR'
  status: 'ACTIVE' | 'DISABLED'
  imagePath: string
  lastLoginAt: string
  createdAt: string
}

export interface LoginRequest {
  account: string
  password: string
  captcha_token?: string
  captcha_action?: string
}

export interface LoginResponse {
  accessToken: string
  refreshToken: string
  adminInfo: AdminUser
}

/** 管理员注册请求 */
export interface RegisterRequest {
  account: string
  name: string
  email: string
  password: string
  code: string
  captcha_token: string
  captcha_action: string
}

// ============================================================
// 用户管理
// ============================================================

export interface User {
  id: string
  userId: string
  account: string
  name: string
  email: string
  phoneNumber: string
  role: string
  status: 'ACTIVE' | 'DISABLED' | 'SUSPENDED'
  imagePath: string
  storageUsed: number
  storageLimit: number
  fileCount: number
  lastLoginAt: string
  createdAt: string
  emailVerified: boolean
  phoneVerified: boolean
  twoFactorEnabled: boolean
  /** 以下为扩展字段，用于用户详情页 */
  location?: string
  lastLoginIp?: string
  registerIp?: string
  totalDownloadBytes?: number
  totalUploadBytes?: number
  deviceCount?: number
  sharedFileCount?: number
}

export interface UserStatusUpdate {
  userId: string
  status: 'ACTIVE' | 'DISABLED' | 'SUSPENDED'
}

export interface UserRoleUpdate {
  userId: string
  role: string
}

export interface BatchUserAction {
  action: 'enable' | 'disable' | 'delete' | 'export'
  userIds: string[]
}

// ============================================================
// 文件管理
// ============================================================

export interface FileNode {
  nodeId: string
  nodeName: string
  nodeType: 'FOLDER' | 'FILE'
  size: number
  mimeType: string
  extension: string
  ownerId: string
  ownerName: string
  parentId: string
  isPublic: boolean
  isEncrypted: boolean
  virusScanStatus: 'CLEAN' | 'INFECTED' | 'PENDING' | 'UNKNOWN'
  createdAt: string
  updatedAt: string
  downloadCount: number
}

export interface FileFilterParams extends PageParams {
  keyword?: string
  ownerId?: string
  nodeType?: 'FOLDER' | 'FILE'
  virusScanStatus?: string
  sortBy?: string
  sortOrder?: 'asc' | 'desc'
}

export interface StorageStats {
  totalStorageBytes: number
  usedStorageBytes: number
  fileCount: number
  folderCount: number
  userCount: number
  storageByType: { type: string; bytes: number; count: number }[]
  dailyGrowth: { date: string; bytes: number }[]
  topUsers: { userId: string; name: string; bytes: number; fileCount: number }[]
}

// ============================================================
// 系统监控
// ============================================================

export interface SystemOverview {
  totalUsers: number
  activeUsers24h: number
  totalFiles: number
  totalStorageBytes: number
  totalDownloads: number
  cpuUsage: number
  memoryUsage: number
  diskUsage: number
  uptime: string
  version: string
}

export interface SystemResources {
  cpu: { cores: number; usage: number; history: { time: string; value: number }[] }
  memory: { total: number; used: number; free: number; usage: number }
  disk: { total: number; used: number; free: number; usage: number }
  network: { in: number; out: number; history: { time: string; in: number; out: number }[] }
  jvm: { heapUsed: number; heapMax: number; nonHeapUsed: number }
}

export interface OnlineUser {
  userId: string
  name: string
  ip: string
  device: string
  browser: string
  loginAt: string
  lastActivity: string
  sessionId: string
}

// ============================================================
// 审计日志
// ============================================================

export interface AuditLog {
  id: string
  userId: string
  userName: string
  action: string
  resource: string
  resourceId: string
  detail: string
  ip: string
  userAgent: string
  status: 'SUCCESS' | 'FAILURE'
  createdAt: string
}

export interface AuditLogFilterParams extends PageParams {
  userId?: string
  action?: string
  resource?: string
  status?: string
  startDate?: string
  endDate?: string
}

// ============================================================
// 系统配置
// ============================================================

export interface SystemConfig {
  siteName: string
  siteDescription: string
  logoUrl: string
  faviconUrl: string
  maxFileSize: number
  allowedFileTypes: string[]
  maxUploadConcurrency: number
  enableRegistration: boolean
  enableCaptcha: boolean
  enable2FA: boolean
  sessionTimeout: number
  maxLoginAttempts: number
  passwordMinLength: number
  passwordRequireSpecialChar: boolean
  virusScanEnabled: boolean
  autoScanOnUpload: boolean
  maintenanceMode: boolean
  contactEmail: string
  minioEndpoint: string
  minioBucket: string
  smtpHost: string
  smsProvider: string
}

// ============================================================
// 安全事件
// ============================================================

export interface SecurityEvent {
  id: string
  type: 'LOGIN_FAILURE' | 'BRUTE_FORCE' | 'SUSPICIOUS_IP' | 'UNAUTHORIZED_ACCESS' | 'VIRUS_DETECTED' | 'CONFIG_CHANGE'
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
  userId: string
  userName: string
  ip: string
  detail: string
  handled: boolean
  handledBy: string
  createdAt: string
}

// ============================================================
// 仪表盘
// ============================================================

export interface DashboardData {
  overview: SystemOverview
  storageTrend: { date: string; bytes: number }[]
  userGrowth: { date: string; count: number }[]
  fileTypeDistribution: { type: string; count: number; bytes: number }[]
  recentActivities: AuditLog[]
  topUsers: { userId: string; name: string; storageUsed: number; fileCount: number }[]
  alerts: { type: string; message: string; severity: string; createdAt: string }[]
}

// ============================================================
// 订单管理
// ============================================================

export interface Order {
  orderId: string
  orderNo: string
  userId: string
  userName: string
  planName: string
  planCycle: 'MONTHLY' | 'QUARTERLY' | 'YEARLY' | 'LIFETIME'
  originalPrice: number
  discountAmount: number
  finalPrice: number
  status: 'PENDING' | 'PAID' | 'CANCELLED' | 'REFUNDED' | 'EXPIRED'
  paymentChannel: 'ALIPAY' | 'WECHAT' | 'BANK' | 'STRIPE'
  paymentTime: string | null
  createdAt: string
  expiredAt: string | null
  storageQuota: number
  remark: string
}

export interface OrderStats {
  totalOrders: number
  totalRevenue: number
  pendingCount: number
  paidCount: number
  refundedCount: number
  todayRevenue: number
  monthlyRevenue: { month: string; revenue: number; count: number }[]
}

// ============================================================
// 头像审核
// ============================================================

export interface AvatarAuditItem {
  id: string
  userId: string
  userName: string
  userEmail: string
  currentAvatar: string
  proposedAvatar: string
  auditStatus: 'PENDING' | 'APPROVED' | 'REJECTED'
  submitTime: string
  auditTime: string | null
  auditor: string | null
  auditRemark: string | null
  reason: string | null
  imageSize: number
  imageFormat: string
  nsfwScore: number
  violenceScore: number
  politicalScore: number
  overallRisk: 'LOW' | 'MEDIUM' | 'HIGH'
  mqEventId: string
  mqStatus: 'SENT' | 'RECEIVED' | 'PROCESSED' | 'FAILED'
}

export interface AvatarAuditStats {
  total: number
  pending: number
  approved: number
  rejected: number
  highRisk: number
  mqProcessed: number
  mqFailed: number
}

// ============================================================
// 文件元数据统计
// ============================================================

export interface FileMetadataStats {
  totalFiles: number
  totalFolders: number
  totalSize: number
  averageFileSize: number
  largestFile: number
  byExtension: { ext: string; count: number; size: number }[]
  byOwner: { userId: string; userName: string; fileCount: number; totalSize: number }[]
  byMimeType: { mimeType: string; count: number; size: number }[]
  encryptionStats: { encrypted: number; unencrypted: number }
  publicStats: { public: number; private: number }
}

// ============================================================
// 注册统计
// ============================================================

export interface RegistrationStats {
  total: number
  today: number
  thisWeek: number
  thisMonth: number
  pendingVerification: number
  bySource: { source: string; count: number }[]
  byDay: { date: string; count: number }[]
}