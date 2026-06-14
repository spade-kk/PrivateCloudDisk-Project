// ============================================================
// 管理后台 - 审计日志 API
// ============================================================
import { request } from '@/utils/request'
import type { ApiResponse, PageResult, AuditLog, AuditLogFilterParams } from '@/types/api'

/** 获取审计日志列表 */
export function getAuditLogsApi(params: AuditLogFilterParams) {
  return request.get<ApiResponse<PageResult<AuditLog>>>('/api/admin/audit-logs', { params })
}

/** 获取审计日志详情 */
export function getAuditLogDetailApi(logId: string) {
  return request.get<ApiResponse<AuditLog>>(`/api/admin/audit-logs/${logId}`)
}

/** 导出审计日志 */
export function exportAuditLogsApi(params: AuditLogFilterParams) {
  return request.get('/api/admin/audit-logs/export', { params, responseType: 'blob' })
}

/** 获取审计日志统计 */
export function getAuditLogStatsApi() {
  return request.get<ApiResponse<{
    totalToday: number
    byAction: { action: string; count: number }[]
    byStatus: { status: string; count: number }[]
    byHour: { hour: number; count: number }[]
  }>>('/api/admin/audit-logs/stats')
}