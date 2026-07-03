// ============================================================
// Mock 请求处理 - 拦截 Axios 请求并返回模拟数据
// 所有接口全部架空，返回前端本地 mock 数据
// ============================================================
import type { AxiosInstance } from 'axios'
import {
  mockAdmin, mockUsers, mockFiles, mockQuarantinedFiles,
  mockAuditLogs, mockSecurityEvents, mockSystemOverview,
  mockSystemResources, mockOnlineUsers, mockSystemConfig,
  mockDashboardData, mockStorageStats, mockIPBlacklist,
  mockOrders, mockAvatarAudits, mockRegistrationStats,
  paginate, filterBy,
} from './data'

// 响应延迟 (ms)
const DELAY = 200

function delay(ms: number = DELAY): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function success<T>(data: T) {
  return { data: { code: 200, message: '操作成功', data, success: true } }
}

function error(message: string, code: number = 400) {
  return { data: { code, message, data: null, success: false } }
}

export function setupMock(axiosInstance: AxiosInstance) {
  const originalRequest = axiosInstance.request.bind(axiosInstance)

  axiosInstance.request = async function (config: any) {
    const url: string = config.url || ''
    const method = (config.method || 'get').toLowerCase()
    const params = config.params || {}
    const data = config.data ? (typeof config.data === 'string' ? JSON.parse(config.data) : config.data) : {}

    await delay()

    // ── 认证相关 ──────────────────────────────────────────
    if (method === 'post' && url.includes('/api/v1/business/users/login')) {
      return success({
        accessToken: 'mock-jwt-token-admin-2024',
        refreshToken: 'mock-refresh-token-admin-2024',
        adminInfo: mockAdmin,
      })
    }

    if (method === 'post' && url.includes('/api/v1/business/users/')) {
      return success(null)
    }

    if (method === 'post' && url.includes('/api/v1/business/users/email/verification-code')) {
      return success(null)
    }

    if (method === 'post' && url.includes('/api/admin/auth/logout')) {
      return success(null)
    }

    if (method === 'get' && url.includes('/api/admin/auth/me')) {
      return success(mockAdmin)
    }

    if (method === 'post' && url.includes('/api/admin/auth/refresh')) {
      return success({
        accessToken: 'mock-jwt-token-admin-refreshed',
        refreshToken: 'mock-refresh-token-admin-refreshed',
      })
    }

    if (method === 'post' && url.includes('/api/admin/auth/password')) {
      return success(null)
    }

    // ── 仪表盘 ────────────────────────────────────────────
    if (method === 'get' && url.includes('/api/admin/dashboard')) {
      return success(mockDashboardData)
    }

    // ── 系统概览 ──────────────────────────────────────────
    if (method === 'get' && url.includes('/api/admin/system/overview')) {
      return success(mockSystemOverview)
    }

    if (method === 'get' && url.includes('/api/admin/system/resources')) {
      return success(mockSystemResources)
    }

    if (method === 'get' && url.includes('/api/admin/system/online-users')) {
      return success(mockOnlineUsers)
    }

    if (method === 'post' && url.includes('/kick')) {
      return success(null)
    }

    // ── 系统配置 ──────────────────────────────────────────
    if (method === 'get' && url.includes('/api/admin/system/config')) {
      return success(mockSystemConfig)
    }

    if (method === 'put' && url.includes('/api/admin/system/config')) {
      return success(null)
    }

    if (method === 'post' && url.includes('/api/admin/system/maintenance')) {
      return success(null)
    }

    if (method === 'post' && url.includes('/api/admin/system/cache/clear')) {
      return success(null)
    }

    // ── 用户管理 ──────────────────────────────────────────
    if (method === 'get' && url.includes('/api/admin/users/export')) {
      // 模拟导出 - 返回空 blob
      return error('Mock 模式下不支持导出', 400)
    }

    if (method === 'get' && url.includes('/api/admin/users/') && url.includes('/files')) {
      // 获取用户文件列表
      const userId = url.match(/\/api\/admin\/users\/([^/]+)\/files/)?.[1]
      const userFiles = mockFiles.filter((f) => f.ownerId === userId)
      const { page = 1, pageSize = 20 } = params
      return success(paginate(userFiles, Number(page), Number(pageSize)))
    }

    if (method === 'get' && url.includes('/api/admin/users/')) {
      // 获取用户详情
      const userId = url.match(/\/api\/admin\/users\/([^/]+)$/)?.[1]
      const user = mockUsers.find((u) => u.userId === userId)
      if (user) return success(user)
      return error('用户不存在', 404)
    }

    if (method === 'put' && url.includes('/status')) {
      return success(null)
    }

    if (method === 'put' && url.includes('/role')) {
      return success(null)
    }

    if (method === 'put' && url.includes('/quota')) {
      return success(null)
    }

    if (method === 'delete' && url.includes('/api/admin/users/')) {
      return success(null)
    }

    if (method === 'post' && url.includes('/api/admin/users/batch')) {
      return success({ successCount: data.userIds?.length || 0, failCount: 0 })
    }

    if (method === 'get' && url.includes('/api/admin/users')) {
      // 用户列表 (分页 + 筛选)
      const { page = 1, pageSize = 20, keyword, status } = params
      let filtered = [...mockUsers]
      const filters: Record<string, any> = {}
      if (keyword) {
        filtered = filtered.filter((u) =>
          u.name.includes(keyword) ||
          u.account.includes(keyword) ||
          u.email.includes(keyword)
        )
      }
      if (status) {
        filtered = filtered.filter((u) => u.status === status)
      }
      return success(paginate(filtered, Number(page), Number(pageSize)))
    }

    // ── 文件管理 ──────────────────────────────────────────
    if (method === 'get' && url.includes('/api/admin/files/quarantined')) {
      const { page = 1, pageSize = 20, keyword } = params
      let filtered = [...mockQuarantinedFiles]
      if (keyword) {
        filtered = filtered.filter((f) => f.nodeName.toLowerCase().includes(keyword.toLowerCase()))
      }
      return success(paginate(filtered, Number(page), Number(pageSize)))
    }

    if (method === 'post' && url.includes('/restore')) {
      return success(null)
    }

    if (method === 'get' && url.includes('/api/admin/files/') && !url.includes('batch') && !url.includes('quarantined')) {
      const fileId = url.match(/\/api\/admin\/files\/([^/]+)$/)?.[1]
      const file = mockFiles.find((f) => f.nodeId === fileId)
      if (file) return success(file)
      return error('文件不存在', 404)
    }

    if (method === 'delete' && url.includes('/api/admin/files/')) {
      return success(null)
    }

    if (method === 'post' && url.includes('/api/admin/files/batch-delete')) {
      return success({ successCount: data.fileIds?.length || 0, failCount: 0 })
    }

    if (method === 'get' && url.includes('/api/admin/files')) {
      const { page = 1, pageSize = 20, keyword, nodeType, virusScanStatus } = params
      let filtered = [...mockFiles]
      const filters: Record<string, any> = {}
      if (keyword) {
        filtered = filtered.filter((f) => f.nodeName.toLowerCase().includes(keyword.toLowerCase()))
      }
      if (nodeType) {
        filtered = filtered.filter((f) => f.nodeType === nodeType)
      }
      if (virusScanStatus) {
        filtered = filtered.filter((f) => f.virusScanStatus === virusScanStatus)
      }
      return success(paginate(filtered, Number(page), Number(pageSize)))
    }

    // ── 存储统计 ──────────────────────────────────────────
    if (method === 'get' && url.includes('/api/admin/storage/stats')) {
      return success(mockStorageStats)
    }

    if (method === 'get' && url.includes('/api/admin/storage/trend')) {
      const { days = 30 } = params
      return success(mockStorageStats.dailyGrowth.slice(-Number(days)))
    }

    // ── 审计日志 ──────────────────────────────────────────
    if (method === 'get' && url.includes('/api/admin/audit-logs/export')) {
      return error('Mock 模式下不支持导出', 400)
    }

    if (method === 'get' && url.includes('/api/admin/audit-logs/stats')) {
      const today = new Date().toISOString().slice(0, 10)
      const todayLogs = mockAuditLogs.filter((l) => l.createdAt.startsWith(today))
      return success({
        totalToday: todayLogs.length,
        byAction: [
          { action: 'LOGIN', count: 45 },
          { action: 'FILE_UPLOAD', count: 120 },
          { action: 'FILE_DOWNLOAD', count: 230 },
          { action: 'FILE_DELETE', count: 18 },
          { action: 'FILE_SHARE', count: 35 },
          { action: 'CONFIG_UPDATE', count: 3 },
          { action: 'USER_UPDATE', count: 8 },
        ],
        byStatus: [
          { status: 'SUCCESS', count: 420 },
          { status: 'FAILURE', count: 39 },
        ],
        byHour: Array.from({ length: 24 }, (_, i) => ({
          hour: i,
          count: Math.floor(Math.random() * 30) + 5,
        })),
      })
    }

    if (method === 'get' && url.includes('/api/admin/audit-logs/') && !url.includes('stats') && !url.includes('export')) {
      const logId = url.match(/\/api\/admin\/audit-logs\/([^/]+)$/)?.[1]
      const log = mockAuditLogs.find((l) => l.id === logId)
      if (log) return success(log)
      return error('日志不存在', 404)
    }

    if (method === 'get' && url.includes('/api/admin/audit-logs')) {
      const { page = 1, pageSize = 20, userId, action, status, startDate, endDate } = params
      let filtered = [...mockAuditLogs]
      if (userId) filtered = filtered.filter((l) => l.userId === userId)
      if (action) filtered = filtered.filter((l) => l.action === action)
      if (status) filtered = filtered.filter((l) => l.status === status)
      if (startDate) filtered = filtered.filter((l) => l.createdAt >= startDate)
      if (endDate) filtered = filtered.filter((l) => l.createdAt <= endDate + 'T23:59:59.999Z')
      return success(paginate(filtered, Number(page), Number(pageSize)))
    }

    // ── 安全事件 ──────────────────────────────────────────
    if (method === 'get' && url.includes('/api/admin/security/stats')) {
      return success({
        totalEvents: 50,
        unhandledCount: mockSecurityEvents.filter((e) => !e.handled).length,
        bySeverity: [
          { severity: 'CRITICAL', count: 5 },
          { severity: 'HIGH', count: 12 },
          { severity: 'MEDIUM', count: 18 },
          { severity: 'LOW', count: 15 },
        ],
        byType: [
          { type: 'LOGIN_FAILURE', count: 20 },
          { type: 'BRUTE_FORCE', count: 8 },
          { type: 'SUSPICIOUS_IP', count: 10 },
          { type: 'UNAUTHORIZED_ACCESS', count: 5 },
          { type: 'VIRUS_DETECTED', count: 4 },
          { type: 'CONFIG_CHANGE', count: 3 },
        ],
        recentAttacks: [
          { ip: '192.168.1.100', count: 45, lastSeen: '2026-06-28T10:00:00.000Z' },
          { ip: '10.0.0.55', count: 32, lastSeen: '2026-06-28T08:30:00.000Z' },
          { ip: '172.16.0.200', count: 18, lastSeen: '2026-06-27T22:00:00.000Z' },
        ],
      })
    }

    if (method === 'post' && url.includes('/handle')) {
      return success(null)
    }

    if (method === 'post' && url.includes('/batch-handle')) {
      return success({ handled: data.eventIds?.length || 0 })
    }

    if (method === 'get' && url.includes('/api/admin/security/events')) {
      const { page = 1, pageSize = 20, severity, type } = params
      let filtered = [...mockSecurityEvents]
      if (severity) filtered = filtered.filter((e) => e.severity === severity)
      if (type) filtered = filtered.filter((e) => e.type === type)
      return success(paginate(filtered, Number(page), Number(pageSize)))
    }

    // ── IP 黑名单 ─────────────────────────────────────────
    if (method === 'get' && url.includes('/api/admin/security/ip-blacklist')) {
      return success(mockIPBlacklist)
    }

    if (method === 'post' && url.includes('/api/admin/security/ip-blacklist')) {
      return success(null)
    }

    if (method === 'delete' && url.includes('/api/admin/security/ip-blacklist/')) {
      return success(null)
    }

    // ── 订单管理 ──────────────────────────────────────────
    if (method === 'get' && url.includes('/api/admin/orders/stats')) {
      const totalRevenue = mockOrders
        .filter((o) => o.status === 'PAID')
        .reduce((sum, o) => sum + o.finalPrice, 0)
      return success({
        totalOrders: mockOrders.length,
        totalRevenue,
        pendingCount: mockOrders.filter((o) => o.status === 'PENDING').length,
        paidCount: mockOrders.filter((o) => o.status === 'PAID').length,
        refundedCount: mockOrders.filter((o) => o.status === 'REFUNDED').length,
        todayRevenue: mockOrders
          .filter((o) => o.status === 'PAID' && o.paymentTime?.startsWith(new Date().toISOString().slice(0, 10)))
          .reduce((sum, o) => sum + o.finalPrice, 0),
        monthlyRevenue: Array.from({ length: 12 }, (_, i) => {
          const d = new Date()
          d.setMonth(d.getMonth() - 11 + i)
          const month = d.toISOString().slice(0, 7)
          const monthOrders = mockOrders.filter((o) => o.status === 'PAID' && o.paymentTime?.startsWith(month))
          return {
            month,
            revenue: monthOrders.reduce((sum, o) => sum + o.finalPrice, 0),
            count: monthOrders.length,
          }
        }),
      })
    }

    if (method === 'get' && url.includes('/api/admin/orders/')) {
      const orderId = url.match(/\/api\/admin\/orders\/([^/]+)$/)?.[1]
      const order = mockOrders.find((o) => o.orderId === orderId)
      if (order) return success(order)
      return error('订单不存在', 404)
    }

    if (method === 'get' && url.includes('/api/admin/orders')) {
      const { page = 1, pageSize = 20, status, keyword, planCycle, startDate, endDate } = params
      let filtered = [...mockOrders]
      if (status) filtered = filtered.filter((o) => o.status === status)
      if (keyword) {
        filtered = filtered.filter(
          (o) => o.orderNo.includes(keyword) || o.userName.includes(keyword) || o.planName.includes(keyword)
        )
      }
      if (planCycle) filtered = filtered.filter((o) => o.planCycle === planCycle)
      if (startDate) filtered = filtered.filter((o) => o.createdAt >= startDate)
      if (endDate) filtered = filtered.filter((o) => o.createdAt <= endDate + 'T23:59:59.999Z')
      return success(paginate(filtered, Number(page), Number(pageSize)))
    }

    // ── 头像审核 ──────────────────────────────────────────
    if (method === 'get' && url.includes('/api/admin/avatar-audit/stats')) {
      return success({
        total: mockAvatarAudits.length,
        pending: mockAvatarAudits.filter((a) => a.auditStatus === 'PENDING').length,
        approved: mockAvatarAudits.filter((a) => a.auditStatus === 'APPROVED').length,
        rejected: mockAvatarAudits.filter((a) => a.auditStatus === 'REJECTED').length,
        highRisk: mockAvatarAudits.filter((a) => a.overallRisk === 'HIGH').length,
        mqProcessed: mockAvatarAudits.filter((a) => a.mqStatus === 'PROCESSED').length,
        mqFailed: mockAvatarAudits.filter((a) => a.mqStatus === 'FAILED').length,
      })
    }

    if (method === 'post' && url.includes('/api/admin/avatar-audit/') && url.includes('/approve')) {
      return success(null)
    }

    if (method === 'post' && url.includes('/api/admin/avatar-audit/') && url.includes('/reject')) {
      return success(null)
    }

    if (method === 'post' && url.includes('/api/admin/avatar-audit/batch')) {
      return success({ successCount: data.ids?.length || 0, failCount: 0 })
    }

    if (method === 'post' && url.includes('/api/admin/avatar-audit/retry-mq')) {
      return success(null)
    }

    if (method === 'get' && url.includes('/api/admin/avatar-audit/') && !url.includes('stats')) {
      const auditId = url.match(/\/api\/admin\/avatar-audit\/([^/]+)$/)?.[1]
      const audit = mockAvatarAudits.find((a) => a.id === auditId)
      if (audit) return success(audit)
      return error('审核记录不存在', 404)
    }

    if (method === 'get' && url.includes('/api/admin/avatar-audit')) {
      const { page = 1, pageSize = 20, status, risk, mqStatus, keyword } = params
      let filtered = [...mockAvatarAudits]
      if (status) filtered = filtered.filter((a) => a.auditStatus === status)
      if (risk) filtered = filtered.filter((a) => a.overallRisk === risk)
      if (mqStatus) filtered = filtered.filter((a) => a.mqStatus === mqStatus)
      if (keyword) {
        filtered = filtered.filter(
          (a) => a.userName.includes(keyword) || a.userEmail.includes(keyword) || a.id.includes(keyword)
        )
      }
      return success(paginate(filtered, Number(page), Number(pageSize)))
    }

    // ── 注册统计 ──────────────────────────────────────────
    if (method === 'get' && url.includes('/api/admin/stats/registrations')) {
      return success(mockRegistrationStats)
    }

    // ── 文件元数据统计 ────────────────────────────────────
    if (method === 'get' && url.includes('/api/admin/files/metadata-stats')) {
      return success({
        totalFiles: mockFiles.length,
        totalFolders: mockFiles.filter((f) => f.nodeType === 'FOLDER').length,
        totalSize: mockFiles.reduce((sum, f) => sum + f.size, 0),
        averageFileSize: Math.floor(mockFiles.filter((f) => f.nodeType === 'FILE').reduce((sum, f) => sum + f.size, 0) / mockFiles.filter((f) => f.nodeType === 'FILE').length),
        largestFile: Math.max(...mockFiles.map((f) => f.size)),
        byExtension: [
          { ext: '.jpg', count: 25000, size: 1.2 * 1024 * 1024 * 1024 * 1024 },
          { ext: '.png', count: 18000, size: 0.9 * 1024 * 1024 * 1024 * 1024 },
          { ext: '.pdf', count: 15000, size: 2.5 * 1024 * 1024 * 1024 * 1024 },
          { ext: '.docx', count: 12000, size: 1.8 * 1024 * 1024 * 1024 * 1024 },
          { ext: '.mp4', count: 8000, size: 5.2 * 1024 * 1024 * 1024 * 1024 },
          { ext: '.zip', count: 6000, size: 3.1 * 1024 * 1024 * 1024 * 1024 },
          { ext: '.md', count: 5000, size: 0.1 * 1024 * 1024 * 1024 * 1024 },
          { ext: '.json', count: 4500, size: 0.05 * 1024 * 1024 * 1024 * 1024 },
        ],
        byOwner: mockUsers.slice(0, 10).map((u) => {
          const userFiles = mockFiles.filter((f) => f.ownerId === u.userId)
          return {
            userId: u.userId,
            userName: u.name,
            fileCount: userFiles.length,
            totalSize: userFiles.reduce((sum, f) => sum + f.size, 0),
          }
        }),
        byMimeType: [
          { mimeType: 'image', count: 43000, size: 2.1 * 1024 * 1024 * 1024 * 1024 },
          { mimeType: 'document', count: 27000, size: 4.3 * 1024 * 1024 * 1024 * 1024 },
          { mimeType: 'video', count: 8000, size: 5.2 * 1024 * 1024 * 1024 * 1024 },
          { mimeType: 'archive', count: 6000, size: 3.1 * 1024 * 1024 * 1024 * 1024 },
          { mimeType: 'code', count: 9500, size: 0.15 * 1024 * 1024 * 1024 * 1024 },
        ],
        encryptionStats: {
          encrypted: mockFiles.filter((f) => f.isEncrypted).length,
          unencrypted: mockFiles.filter((f) => !f.isEncrypted).length,
        },
        publicStats: {
          public: mockFiles.filter((f) => f.isPublic).length,
          private: mockFiles.filter((f) => !f.isPublic).length,
        },
      })
    }

    // ── 默认处理 ──────────────────────────────────────────
    console.warn(`[Mock] 未匹配的请求: ${method.toUpperCase()} ${url}`)
    return error('Mock 接口未实现', 404)
  }
}