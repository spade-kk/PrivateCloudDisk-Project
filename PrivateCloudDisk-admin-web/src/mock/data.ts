// ============================================================
// Mock 数据 - 全部模拟数据定义
// 企业级管理后台完整模拟数据集
// ============================================================
import type {
  AdminUser, User, FileNode, AuditLog, SecurityEvent,
  SystemOverview, SystemResources, SystemConfig, OnlineUser,
  DashboardData, StorageStats,
} from '@/types/api'

// ── 管理员账号 ────────────────────────────────────────────

export const mockAdmin: AdminUser = {
  id: 'admin-001',
  userId: 'ADMIN-001',
  account: 'superadmin',
  name: '超级管理员',
  email: 'admin@privateclouddisk.com',
  phoneNumber: '13800000000',
  role: 'SUPER_ADMIN',
  status: 'ACTIVE',
  imagePath: '',
  lastLoginAt: new Date().toISOString(),
  createdAt: '2024-01-01T00:00:00.000Z',
}

// ── 用户列表 (50 条详细数据) ───────────────────────────────

const firstNames = ['张', '李', '王', '刘', '陈', '杨', '赵', '黄', '周', '吴', '徐', '孙', '胡', '朱', '高', '林', '何', '郭', '马', '罗']
const lastNames = ['伟', '芳', '娜', '敏', '静', '强', '磊', '洋', '勇', '军', '杰', '涛', '明', '超', '秀英', '华', '丽', '玲', '鑫', '鹏']
const statuses: User['status'][] = ['ACTIVE', 'ACTIVE', 'ACTIVE', 'ACTIVE', 'ACTIVE', 'ACTIVE', 'ACTIVE', 'DISABLED', 'SUSPENDED']
const roles = ['USER', 'USER', 'USER', 'USER', 'USER', 'VIP', 'VIP', 'ADMIN']
const emailDomains = ['@qq.com', '@163.com', '@gmail.com', '@outlook.com', '@icloud.com', '@foxmail.com', '@sina.com', '@hotmail.com']
const provices = ['北京', '上海', '广州', '深圳', '杭州', '成都', '南京', '武汉', '西安', '重庆']

function randomInt(min: number, max: number) { return Math.floor(Math.random() * (max - min + 1)) + min }
function randomPick<T>(arr: T[]) { return arr[Math.floor(Math.random() * arr.length)] }
function randomDate(start: string, end: string) {
  const s = new Date(start).getTime()
  const e = new Date(end).getTime()
  return new Date(s + Math.random() * (e - s)).toISOString()
}

export const mockUsers: User[] = Array.from({ length: 50 }, (_, i) => {
  const name = randomPick(firstNames) + randomPick(lastNames)
  const account = `user_${String(i + 1).padStart(3, '0')}`
  const status = randomPick(statuses)
  const role = randomPick(roles)
  const storageUsed = randomInt(100 * 1024 * 1024, 500 * 1024 * 1024 * 1024) // 100MB ~ 500GB
  const storageLimit = randomInt(10 * 1024 * 1024 * 1024, 2 * 1024 * 1024 * 1024 * 1024) // 10GB ~ 2TB
  const fileCount = randomInt(10, 50000)
  const location = randomPick(provices)
  return {
    id: `user-${String(i + 1).padStart(3, '0')}`,
    userId: `UID-${String(i + 1).padStart(6, '0')}`,
    account,
    name,
    email: `${account}${randomPick(emailDomains)}`,
    phoneNumber: `1${randomInt(30, 99)}${String(randomInt(10000000, 99999999))}`,
    role,
    status,
    imagePath: `https://api.dicebear.com/7.x/initials/svg?seed=${encodeURIComponent(name)}`,
    storageUsed,
    storageLimit,
    fileCount,
    lastLoginAt: randomDate('2026-06-01', '2026-06-28'),
    createdAt: randomDate('2024-01-01', '2026-05-31'),
    emailVerified: Math.random() > 0.3,
    phoneVerified: Math.random() > 0.4,
    twoFactorEnabled: Math.random() > 0.7,
    location,
    lastLoginIp: `${randomInt(10, 223)}.${randomInt(0, 255)}.${randomInt(0, 255)}.${randomInt(1, 254)}`,
    registerIp: `${randomInt(10, 223)}.${randomInt(0, 255)}.${randomInt(0, 255)}.${randomInt(1, 254)}`,
    totalDownloadBytes: randomInt(0, 10 * 1024 * 1024 * 1024 * 1024),
    totalUploadBytes: randomInt(0, 5 * 1024 * 1024 * 1024 * 1024),
    deviceCount: randomInt(1, 8),
    sharedFileCount: randomInt(0, 200),
  } as User & {
    location: string
    lastLoginIp: string
    registerIp: string
    totalDownloadBytes: number
    totalUploadBytes: number
    deviceCount: number
    sharedFileCount: number
  }
})

// ── 文件列表 (100 条) ──────────────────────────────────────

const fileNames = [
  '年度总结报告.docx', '项目计划书.xlsx', '产品设计稿.fig', '会议纪要.pdf',
  '系统架构图.png', '数据库设计.sql', '前端开发规范.md', '测试用例.xlsx',
  '用户手册.pdf', 'API接口文档.md', '部署方案.docx', '需求文档V3.pdf',
  '财务报表.xlsx', '竞品分析.pptx', '团队合影.jpg', '培训视频.mp4',
  '源代码备份.zip', '配置文件.json', '日志文件.log', '数据备份.tar.gz',
  '营销方案.pdf', '合同模板.docx', '发票扫描件.pdf', '产品图片.png',
  '操作手册.pdf', '安全审计报告.docx', '性能测试报告.xlsx', '开发计划.md',
  '设计规范.sketch', '音乐demo.mp3', '视频教程.mp4', '安装包.exe',
  'README.md', 'package.json', 'Dockerfile', 'docker-compose.yml',
  '.gitignore', 'tsconfig.json', 'index.html', 'App.tsx',
]

const mimeTypes = [
  'image/png', 'image/jpeg', 'image/svg+xml',
  'application/pdf', 'application/msword',
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  'application/vnd.openxmlformats-officedocument.presentationml.presentation',
  'video/mp4', 'audio/mpeg', 'application/zip',
  'text/markdown', 'application/json', 'text/plain',
  'application/x-msdownload', 'application/x-tar',
]

const extensions = ['png', 'jpg', 'pdf', 'docx', 'xlsx', 'pptx', 'mp4', 'mp3', 'zip', 'md', 'json', 'txt', 'exe', 'tar.gz']

const virusStatuses: FileNode['virusScanStatus'][] = ['CLEAN', 'CLEAN', 'CLEAN', 'CLEAN', 'CLEAN', 'CLEAN', 'CLEAN', 'PENDING', 'INFECTED', 'UNKNOWN']

export const mockFiles: FileNode[] = Array.from({ length: 100 }, (_, i) => {
  const isFolder = i < 10
  const fileName = isFolder ? `文件夹_${String(i + 1).padStart(2, '0')}` : randomPick(fileNames)
  const user = randomPick(mockUsers)
  const ext = isFolder ? '' : randomPick(extensions)
  return {
    nodeId: `file-${String(i + 1).padStart(4, '0')}`,
    nodeName: fileName,
    nodeType: isFolder ? 'FOLDER' : 'FILE',
    size: isFolder ? 0 : randomInt(1024, 10 * 1024 * 1024 * 1024), // 1KB ~ 10GB
    mimeType: isFolder ? '' : randomPick(mimeTypes),
    extension: ext,
    ownerId: user.userId,
    ownerName: user.name,
    parentId: i < 10 ? 'root' : `folder-${randomInt(1, 10)}`,
    isPublic: Math.random() > 0.7,
    isEncrypted: Math.random() > 0.85,
    virusScanStatus: isFolder ? 'CLEAN' : randomPick(virusStatuses),
    createdAt: randomDate('2024-06-01', '2026-06-28'),
    updatedAt: randomDate('2026-03-01', '2026-06-28'),
    downloadCount: randomInt(0, 5000),
  }
})

// 隔离文件 (20条)
export const mockQuarantinedFiles: FileNode[] = Array.from({ length: 20 }, (_, i) => {
  const user = randomPick(mockUsers)
  return {
    nodeId: `quar-${String(i + 1).padStart(4, '0')}`,
    nodeName: randomPick(fileNames),
    nodeType: 'FILE',
    size: randomInt(1024, 500 * 1024 * 1024),
    mimeType: randomPick(mimeTypes),
    extension: randomPick(extensions),
    ownerId: user.userId,
    ownerName: user.name,
    parentId: 'quarantine',
    isPublic: false,
    isEncrypted: false,
    virusScanStatus: 'INFECTED',
    createdAt: randomDate('2026-05-01', '2026-06-27'),
    updatedAt: randomDate('2026-06-01', '2026-06-28'),
    downloadCount: 0,
  }
})

// ── 审计日志 (200 条) ──────────────────────────────────────

const auditActions = [
  'LOGIN', 'LOGOUT', 'FILE_UPLOAD', 'FILE_DOWNLOAD', 'FILE_DELETE',
  'FILE_SHARE', 'FILE_RENAME', 'FOLDER_CREATE', 'USER_UPDATE', 'ROLE_CHANGE',
  'PASSWORD_CHANGE', 'CONFIG_UPDATE', 'SYSTEM_BACKUP', 'API_KEY_CREATE',
  'DEVICE_LOGIN', '2FA_ENABLE', 'ACCOUNT_DISABLE', 'DATA_EXPORT',
]

const auditResources = ['USER', 'FILE', 'FOLDER', 'SYSTEM', 'CONFIG', 'SECURITY', 'API_KEY']

export const mockAuditLogs: AuditLog[] = Array.from({ length: 200 }, (_, i) => {
  const user = randomPick(mockUsers)
  const action = randomPick(auditActions)
  const resource = randomPick(auditResources)
  const status: 'SUCCESS' | 'FAILURE' = Math.random() > 0.15 ? 'SUCCESS' : 'FAILURE'
  return {
    id: `audit-${String(i + 1).padStart(5, '0')}`,
    userId: user.userId,
    userName: user.name,
    action,
    resource,
    resourceId: `${resource}-${randomInt(1000, 9999)}`,
    detail: `${action === 'LOGIN' ? '用户登录系统' : action === 'FILE_UPLOAD' ? '上传文件' : action === 'FILE_DOWNLOAD' ? '下载文件' : action === 'FILE_DELETE' ? '删除文件' : action === 'CONFIG_UPDATE' ? '修改系统配置' : `执行 ${action} 操作`}`,
    ip: `${randomInt(10, 223)}.${randomInt(0, 255)}.${randomInt(0, 255)}.${randomInt(1, 254)}`,
    userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
    status,
    createdAt: randomDate('2026-06-01', '2026-06-28'),
  }
})

// ── 安全事件 (50 条) ───────────────────────────────────────

const securityTypes: SecurityEvent['type'][] = [
  'LOGIN_FAILURE', 'BRUTE_FORCE', 'SUSPICIOUS_IP', 'UNAUTHORIZED_ACCESS', 'VIRUS_DETECTED', 'CONFIG_CHANGE',
]
const severities: SecurityEvent['severity'][] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']

export const mockSecurityEvents: SecurityEvent[] = Array.from({ length: 50 }, (_, i) => {
  const user = randomPick(mockUsers)
  const type = randomPick(securityTypes)
  const severity = randomPick(severities)
  return {
    id: `sec-${String(i + 1).padStart(4, '0')}`,
    type,
    severity,
    userId: user.userId,
    userName: user.name,
    ip: `${randomInt(10, 223)}.${randomInt(0, 255)}.${randomInt(0, 255)}.${randomInt(1, 254)}`,
    detail: type === 'LOGIN_FAILURE' ? '连续3次登录失败' : type === 'BRUTE_FORCE' ? '检测到暴力破解攻击' : type === 'SUSPICIOUS_IP' ? '来自可疑IP的访问' : type === 'VIRUS_DETECTED' ? '检测到病毒文件' : '系统配置变更',
    handled: Math.random() > 0.4,
    handledBy: Math.random() > 0.4 ? 'superadmin' : '',
    createdAt: randomDate('2026-06-01', '2026-06-28'),
  }
})

// ── 系统概览 ────────────────────────────────────────────────

export const mockSystemOverview: SystemOverview = {
  totalUsers: 12538,
  activeUsers24h: 847,
  totalFiles: 1856234,
  totalStorageBytes: 42.8 * 1024 * 1024 * 1024 * 1024, // 42.8 TB
  totalDownloads: 9856234,
  cpuUsage: 34.7,
  memoryUsage: 62.3,
  diskUsage: 58.1,
  uptime: '45天 12小时 38分钟',
  version: 'v3.2.1',
}

// ── 系统资源 ────────────────────────────────────────────────

function generateHistory(points: number, base: number, variance: number) {
  return Array.from({ length: points }, (_, i) => {
    const d = new Date()
    d.setMinutes(d.getMinutes() - (points - i) * 5)
    return {
      time: d.toISOString(),
      value: Math.min(100, Math.max(0, base + (Math.random() - 0.5) * variance)),
    }
  })
}

export const mockSystemResources: SystemResources = {
  cpu: {
    cores: 16,
    usage: 34.7,
    history: generateHistory(24, 35, 30),
  },
  memory: {
    total: 64 * 1024 * 1024 * 1024,
    used: 40.1 * 1024 * 1024 * 1024,
    free: 23.9 * 1024 * 1024 * 1024,
    usage: 62.3,
  },
  disk: {
    total: 10 * 1024 * 1024 * 1024 * 1024,
    used: 5.81 * 1024 * 1024 * 1024 * 1024,
    free: 4.19 * 1024 * 1024 * 1024 * 1024,
    usage: 58.1,
  },
  network: {
    in: 1024 * 1024 * 15,
    out: 1024 * 1024 * 8,
    history: generateHistory(24, 50, 40).map((h) => ({
      time: h.time,
      in: h.value * 1024 * 1024,
      out: (h.value * 0.6) * 1024 * 1024,
    })),
  },
  jvm: {
    heapUsed: 2.1 * 1024 * 1024 * 1024,
    heapMax: 8 * 1024 * 1024 * 1024,
    nonHeapUsed: 512 * 1024 * 1024,
  },
}

// ── 在线用户 ────────────────────────────────────────────────

const browsers = ['Chrome 125', 'Firefox 127', 'Safari 17', 'Edge 125', 'Opera 110']
const devices = ['Windows 11', 'macOS 14', 'iOS 17', 'Android 14', 'Ubuntu 24.04']

export const mockOnlineUsers: OnlineUser[] = Array.from({ length: 35 }, (_, i) => {
  const user = randomPick(mockUsers)
  return {
    userId: user.userId,
    name: user.name,
    ip: `${randomInt(10, 223)}.${randomInt(0, 255)}.${randomInt(0, 255)}.${randomInt(1, 254)}`,
    device: randomPick(devices),
    browser: randomPick(browsers),
    loginAt: new Date(Date.now() - randomInt(0, 24 * 3600 * 1000)).toISOString(),
    lastActivity: new Date(Date.now() - randomInt(0, 30 * 60 * 1000)).toISOString(),
    sessionId: `session-${String(i + 1).padStart(4, '0')}`,
  }
})

// ── 系统配置 ────────────────────────────────────────────────

export const mockSystemConfig: SystemConfig = {
  siteName: 'PrivateCloudDisk',
  siteDescription: '企业级私有云存储解决方案',
  logoUrl: '/logo.svg',
  faviconUrl: '/favicon.svg',
  maxFileSize: 10 * 1024 * 1024 * 1024,
  allowedFileTypes: ['.jpg', '.png', '.pdf', '.docx', '.xlsx', '.pptx', '.zip', '.mp4', '.mp3', '.txt', '.md'],
  maxUploadConcurrency: 5,
  enableRegistration: true,
  enableCaptcha: true,
  enable2FA: false,
  sessionTimeout: 3600,
  maxLoginAttempts: 5,
  passwordMinLength: 8,
  passwordRequireSpecialChar: true,
  virusScanEnabled: true,
  autoScanOnUpload: true,
  maintenanceMode: false,
  contactEmail: 'support@privateclouddisk.com',
  minioEndpoint: 'https://minio.internal.example.com',
  minioBucket: 'pcd-storage-prod',
  smtpHost: 'smtp.internal.example.com',
  smsProvider: 'aliyun',
}

// ── 存储统计 ────────────────────────────────────────────────

export const mockStorageStats: StorageStats = {
  totalStorageBytes: 100 * 1024 * 1024 * 1024 * 1024, // 100TB
  usedStorageBytes: 42.8 * 1024 * 1024 * 1024 * 1024,
  fileCount: 1856234,
  folderCount: 234567,
  userCount: 12538,
  storageByType: [
    { type: '文档', bytes: 8.5 * 1024 * 1024 * 1024 * 1024, count: 450000 },
    { type: '图片', bytes: 12.3 * 1024 * 1024 * 1024 * 1024, count: 680000 },
    { type: '视频', bytes: 15.2 * 1024 * 1024 * 1024 * 1024, count: 120000 },
    { type: '音频', bytes: 2.1 * 1024 * 1024 * 1024 * 1024, count: 85000 },
    { type: '压缩包', bytes: 3.5 * 1024 * 1024 * 1024 * 1024, count: 95000 },
    { type: '代码', bytes: 1.2 * 1024 * 1024 * 1024 * 1024, count: 380000 },
    { type: '其他', bytes: 0.8 * 1024 * 1024 * 1024 * 1024, count: 46234 },
  ],
  dailyGrowth: Array.from({ length: 30 }, (_, i) => {
    const d = new Date()
    d.setDate(d.getDate() - 29 + i)
    return {
      date: d.toISOString().slice(0, 10),
      bytes: randomInt(50 * 1024 * 1024 * 1024, 500 * 1024 * 1024 * 1024),
    }
  }),
  topUsers: mockUsers.slice(0, 10).map((u) => ({
    userId: u.userId,
    name: u.name,
    bytes: u.storageUsed,
    fileCount: u.fileCount,
  })),
}

// ── 仪表盘数据 ──────────────────────────────────────────────

export const mockDashboardData: DashboardData = {
  overview: mockSystemOverview,
  storageTrend: Array.from({ length: 30 }, (_, i) => {
    const d = new Date()
    d.setDate(d.getDate() - 29 + i)
    return {
      date: d.toISOString().slice(0, 10),
      bytes: 40 * 1024 * 1024 * 1024 * 1024 + randomInt(0, 5 * 1024 * 1024 * 1024 * 1024),
    }
  }),
  userGrowth: Array.from({ length: 30 }, (_, i) => {
    const d = new Date()
    d.setDate(d.getDate() - 29 + i)
    return {
      date: d.toISOString().slice(0, 10),
      count: randomInt(50, 200),
    }
  }),
  fileTypeDistribution: [
    { type: '图片', count: 680000, bytes: 12.3 * 1024 * 1024 * 1024 * 1024 },
    { type: '文档', count: 450000, bytes: 8.5 * 1024 * 1024 * 1024 * 1024 },
    { type: '代码', count: 380000, bytes: 1.2 * 1024 * 1024 * 1024 * 1024 },
    { type: '视频', count: 120000, bytes: 15.2 * 1024 * 1024 * 1024 * 1024 },
    { type: '压缩包', count: 95000, bytes: 3.5 * 1024 * 1024 * 1024 * 1024 },
    { type: '音频', count: 85000, bytes: 2.1 * 1024 * 1024 * 1024 * 1024 },
    { type: '其他', count: 46234, bytes: 0.8 * 1024 * 1024 * 1024 * 1024 },
  ],
  recentActivities: mockAuditLogs.slice(0, 20),
  topUsers: mockUsers.slice(0, 10).map((u) => ({
    userId: u.userId,
    name: u.name,
    storageUsed: u.storageUsed,
    fileCount: u.fileCount,
  })),
  alerts: [
    { type: '安全', message: '检测到来自 IP 192.168.1.100 的异常登录尝试', severity: 'HIGH', createdAt: new Date(Date.now() - 3600000).toISOString() },
    { type: '存储', message: '磁盘使用率已达到 58%，建议扩容', severity: 'MEDIUM', createdAt: new Date(Date.now() - 7200000).toISOString() },
    { type: '系统', message: '发现 3 个病毒文件已被隔离', severity: 'HIGH', createdAt: new Date(Date.now() - 1800000).toISOString() },
    { type: '性能', message: 'CPU 使用率持续超过 80%', severity: 'MEDIUM', createdAt: new Date(Date.now() - 5400000).toISOString() },
    { type: '用户', message: '用户 user_045 存储空间即将用尽 (95%)', severity: 'LOW', createdAt: new Date(Date.now() - 9000000).toISOString() },
  ],
}

// ── 订单数据 (80 条) ────────────────────────────────────────

export interface MockOrder {
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

const planNames = ['基础版', '专业版', '企业版', '旗舰版', '团队版', '入门版']
const planCycles: MockOrder['planCycle'][] = ['MONTHLY', 'QUARTERLY', 'YEARLY', 'LIFETIME']
const orderStatuses: MockOrder['status'][] = ['PENDING', 'PAID', 'PAID', 'PAID', 'PAID', 'PAID', 'CANCELLED', 'REFUNDED', 'EXPIRED']
const paymentChannels: MockOrder['paymentChannel'][] = ['ALIPAY', 'WECHAT', 'BANK', 'STRIPE']

export const mockOrders: MockOrder[] = Array.from({ length: 80 }, (_, i) => {
  const user = randomPick(mockUsers)
  const planName = randomPick(planNames)
  const cycle = randomPick(planCycles)
  const status = randomPick(orderStatuses)
  const originalPrice = cycle === 'MONTHLY' ? randomInt(9, 99) : cycle === 'QUARTERLY' ? randomInt(99, 299) : cycle === 'YEARLY' ? randomInt(299, 999) : randomInt(999, 4999)
  const discount = status === 'PAID' ? Math.floor(originalPrice * (Math.random() * 0.3)) : 0
  const createdAt = randomDate('2025-06-01', '2026-06-28')
  return {
    orderId: `order-${String(i + 1).padStart(5, '0')}`,
    orderNo: `PCD${new Date(createdAt).getFullYear()}${String(i + 1).padStart(8, '0')}`,
    userId: user.userId,
    userName: user.name,
    planName,
    planCycle: cycle,
    originalPrice,
    discountAmount: discount,
    finalPrice: originalPrice - discount,
    status,
    paymentChannel: status === 'PAID' ? randomPick(paymentChannels) : 'ALIPAY' as const,
    paymentTime: status === 'PAID' ? new Date(new Date(createdAt).getTime() + randomInt(60000, 86400000)).toISOString() : null,
    createdAt,
    expiredAt: status === 'PAID' ? new Date(new Date(createdAt).getTime() + 365 * 24 * 3600 * 1000).toISOString() : null,
    storageQuota: randomInt(100, 10240) * 1024 * 1024 * 1024,
    remark: status === 'REFUNDED' ? '用户申请退款' : status === 'CANCELLED' ? '用户取消订单' : '',
  }
})

// ── 头像审核数据 (30 条) ────────────────────────────────────

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

const avatarUrls = [
  'https://api.dicebear.com/7.x/avataaars/svg?seed=Felix',
  'https://api.dicebear.com/7.x/avataaars/svg?seed=Aneka',
  'https://api.dicebear.com/7.x/avataaars/svg?seed=Salem',
  'https://api.dicebear.com/7.x/avataaars/svg?seed=Mia',
  'https://api.dicebear.com/7.x/avataaars/svg?seed=Leo',
  'https://api.dicebear.com/7.x/avataaars/svg?seed=Zoe',
  'https://api.dicebear.com/7.x/avataaars/svg?seed=Max',
  'https://api.dicebear.com/7.x/avataaars/svg?seed=Luna',
]

const auditStatuses: AvatarAuditItem['auditStatus'][] = ['PENDING', 'PENDING', 'PENDING', 'APPROVED', 'APPROVED', 'APPROVED', 'REJECTED']
const riskLevels: AvatarAuditItem['overallRisk'][] = ['LOW', 'LOW', 'LOW', 'LOW', 'MEDIUM', 'MEDIUM', 'HIGH']
const mqStatuses: AvatarAuditItem['mqStatus'][] = ['SENT', 'RECEIVED', 'PROCESSED', 'PROCESSED', 'PROCESSED', 'FAILED']

export const mockAvatarAudits: AvatarAuditItem[] = Array.from({ length: 30 }, (_, i) => {
  const user = randomPick(mockUsers)
  const status = randomPick(auditStatuses)
  const risk = randomPick(riskLevels)
  const submitTime = randomDate('2026-06-20', '2026-06-28')
  return {
    id: `avatar-${String(i + 1).padStart(4, '0')}`,
    userId: user.userId,
    userName: user.name,
    userEmail: user.email,
    currentAvatar: randomPick(avatarUrls),
    proposedAvatar: randomPick(avatarUrls),
    auditStatus: status,
    submitTime,
    auditTime: status !== 'PENDING' ? new Date(new Date(submitTime).getTime() + randomInt(3600000, 86400000)).toISOString() : null,
    auditor: status !== 'PENDING' ? 'superadmin' : null,
    auditRemark: status === 'REJECTED' ? '头像包含不当内容，不符合社区规范' : status === 'APPROVED' ? '审核通过，头像已更新' : null,
    reason: status === 'REJECTED' ? '内容违规' : null,
    imageSize: randomInt(10240, 5242880),
    imageFormat: randomPick(['PNG', 'JPEG', 'WEBP', 'GIF']),
    nsfwScore: risk === 'HIGH' ? randomInt(70, 95) / 100 : risk === 'MEDIUM' ? randomInt(30, 70) / 100 : randomInt(0, 30) / 100,
    violenceScore: risk === 'HIGH' ? randomInt(50, 90) / 100 : randomInt(0, 30) / 100,
    politicalScore: randomInt(0, 20) / 100,
    overallRisk: risk,
    mqEventId: `mq-avatar-${String(i + 1).padStart(6, '0')}`,
    mqStatus: randomPick(mqStatuses),
  }
})

// ── IP 黑名单 ────────────────────────────────────────────────

export const mockIPBlacklist = [
  { ip: '192.168.1.100', reason: '暴力破解攻击', addedAt: '2026-06-15T08:00:00.000Z' },
  { ip: '10.0.0.55', reason: 'SQL注入尝试', addedAt: '2026-06-20T14:30:00.000Z' },
  { ip: '172.16.0.200', reason: '频繁扫描端口', addedAt: '2026-06-22T11:00:00.000Z' },
  { ip: '203.0.113.45', reason: 'DDoS攻击来源', addedAt: '2026-06-25T03:00:00.000Z' },
  { ip: '198.51.100.78', reason: '恶意爬虫', addedAt: '2026-06-26T16:00:00.000Z' },
  { ip: '185.220.101.34', reason: 'Tor出口节点', addedAt: '2026-06-27T09:00:00.000Z' },
  { ip: '45.33.32.156', reason: '已知恶意IP', addedAt: '2026-06-27T22:00:00.000Z' },
]

// ── 注册统计 ────────────────────────────────────────────────

export const mockRegistrationStats = {
  total: 12538,
  today: 47,
  thisWeek: 312,
  thisMonth: 1287,
  pendingVerification: 89,
  bySource: [
    { source: 'Web', count: 6500 },
    { source: 'Mobile', count: 4200 },
    { source: 'API', count: 1500 },
    { source: 'OAuth', count: 338 },
  ],
  byDay: Array.from({ length: 14 }, (_, i) => {
    const d = new Date()
    d.setDate(d.getDate() - 13 + i)
    return {
      date: d.toISOString().slice(0, 10),
      count: randomInt(20, 80),
    }
  }),
}

// ── 帮助函数 ────────────────────────────────────────────────

export function paginate<T>(list: T[], page: number, pageSize: number): { records: T[]; total: number; page: number; pageSize: number } {
  const start = (page - 1) * pageSize
  const records = list.slice(start, start + pageSize)
  return { records, total: list.length, page, pageSize }
}

export function filterBy<T extends Record<string, any>>(list: T[], filters: Record<string, any>): T[] {
  return list.filter((item) => {
    return Object.entries(filters).every(([key, value]) => {
      if (value === null || value === undefined || value === '') return true
      const itemVal = item[key]
      if (typeof itemVal === 'string' && typeof value === 'string') {
        return itemVal.toLowerCase().includes(value.toLowerCase())
      }
      return itemVal === value
    })
  })
}