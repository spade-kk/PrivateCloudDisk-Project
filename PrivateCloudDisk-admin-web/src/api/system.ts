// ============================================================
// 管理后台 - 系统监控与配置 API
// ============================================================
import { request } from '@/utils/request'
import type {
  ApiResponse,
  SystemOverview,
  SystemResources,
  OnlineUser,
  SystemConfig,
  DashboardData,
} from '@/types/api'

// ── 仪表盘 ─────────────────────────────────────────────

/** 获取仪表盘数据 */
export function getDashboardApi() {
  return request.get<ApiResponse<DashboardData>>('/api/admin/dashboard')
}

// ── 系统概览 ───────────────────────────────────────────

/** 获取系统概览统计 */
export function getSystemOverviewApi() {
  return request.get<ApiResponse<SystemOverview>>('/api/admin/system/overview')
}

/** 获取系统资源使用情况 */
export function getSystemResourcesApi() {
  return request.get<ApiResponse<SystemResources>>('/api/admin/system/resources')
}

/** 获取在线用户列表 */
export function getOnlineUsersApi() {
  return request.get<ApiResponse<OnlineUser[]>>('/api/admin/system/online-users')
}

/** 踢出在线用户 */
export function kickOnlineUserApi(sessionId: string) {
  return request.post<ApiResponse<null>>(`/api/admin/system/online-users/${sessionId}/kick`)
}

// ── 系统配置 ───────────────────────────────────────────

/** 获取系统配置 */
export function getSystemConfigApi() {
  return request.get<ApiResponse<SystemConfig>>('/api/admin/system/config')
}

/** 更新系统配置 */
export function updateSystemConfigApi(config: Partial<SystemConfig>) {
  return request.put<ApiResponse<null>>('/api/admin/system/config', config)
}

/** 切换维护模式 */
export function toggleMaintenanceModeApi(enabled: boolean) {
  return request.post<ApiResponse<null>>('/api/admin/system/maintenance', { enabled })
}

/** 清理系统缓存 */
export function clearSystemCacheApi() {
  return request.post<ApiResponse<null>>('/api/admin/system/cache/clear')
}