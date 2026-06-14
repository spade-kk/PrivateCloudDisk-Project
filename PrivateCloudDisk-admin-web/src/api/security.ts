// ============================================================
// 管理后台 - 安全事件 API
// ============================================================
import { request } from '@/utils/request'
import type { ApiResponse, PageResult, SecurityEvent, PageParams } from '@/types/api'

/** 获取安全事件列表 */
export function getSecurityEventsApi(params: PageParams) {
  return request.get<ApiResponse<PageResult<SecurityEvent>>>('/api/admin/security/events', { params })
}

/** 处理安全事件 */
export function handleSecurityEventApi(eventId: string, resolution: string) {
  return request.post<ApiResponse<null>>(`/api/admin/security/events/${eventId}/handle`, {
    resolution,
  })
}

/** 批量处理安全事件 */
export function batchHandleEventsApi(eventIds: string[]) {
  return request.post<ApiResponse<{ handled: number }>>('/api/admin/security/events/batch-handle', {
    eventIds,
  })
}

/** 获取安全统计 */
export function getSecurityStatsApi() {
  return request.get<ApiResponse<{
    totalEvents: number
    unhandledCount: number
    bySeverity: { severity: string; count: number }[]
    byType: { type: string; count: number }[]
    recentAttacks: { ip: string; count: number; lastSeen: string }[]
  }>>('/api/admin/security/stats')
}

/** 获取 IP 黑名单 */
export function getIPBlacklistApi() {
  return request.get<ApiResponse<{ ip: string; reason: string; addedAt: string }[]>>(
    '/api/admin/security/ip-blacklist'
  )
}

/** 添加 IP 到黑名单 */
export function addIPToBlacklistApi(ip: string, reason: string) {
  return request.post<ApiResponse<null>>('/api/admin/security/ip-blacklist', { ip, reason })
}

/** 从黑名单移除 IP */
export function removeIPFromBlacklistApi(ip: string) {
  return request.delete<ApiResponse<null>>(`/api/admin/security/ip-blacklist/${encodeURIComponent(ip)}`)
}