/**
 * 管理后台 API 模块
 * 提供用户管理、系统监控、审计日志等管理功能接口
 */
import request from '@/utils/request'

// ─── 用户管理 ────────────────────────────────────────

/** 获取用户列表（分页） */
export function getUsersApi(params) {
  return request({ url: '/api/admin/users', method: 'GET', params })
}

/** 获取用户详情 */
export function getUserDetailApi(userId) {
  return request({ url: `/api/admin/users/${userId}`, method: 'GET' })
}

/** 禁用/启用用户 */
export function toggleUserStatusApi(userId, status) {
  return request({ url: `/api/admin/users/${userId}/status`, method: 'PUT', data: { status } })
}

/** 修改用户角色 */
export function updateUserRoleApi(userId, role) {
  return request({ url: `/api/admin/users/${userId}/role`, method: 'PUT', data: { role } })
}

/** 删除用户 */
export function deleteUserApi(userId) {
  return request({ url: `/api/admin/users/${userId}`, method: 'DELETE' })
}

/** 批量操作用户 */
export function batchUserActionApi(action, userIds) {
  return request({ url: '/api/admin/users/batch', method: 'POST', data: { action, userIds } })
}

// ─── 系统监控 ────────────────────────────────────────

/** 获取系统概览统计 */
export function getSystemOverviewApi() {
  return request({ url: '/api/admin/system/overview', method: 'GET' })
}

/** 获取系统资源使用情况 */
export function getSystemResourcesApi() {
  return request({ url: '/api/admin/system/resources', method: 'GET' })
}

/** 获取在线用户列表 */
export function getOnlineUsersApi() {
  return request({ url: '/api/admin/system/online-users', method: 'GET' })
}

// ─── 审计日志 ────────────────────────────────────────

/** 获取审计日志 */
export function getAuditLogsApi(params) {
  return request({ url: '/api/admin/audit-logs', method: 'GET', params })
}

/** 导出审计日志 */
export function exportAuditLogsApi(params) {
  return request({ url: '/api/admin/audit-logs/export', method: 'GET', params, responseType: 'blob' })
}

// ─── 系统配置 ────────────────────────────────────────

/** 获取系统配置 */
export function getSystemConfigApi() {
  return request({ url: '/api/admin/system/config', method: 'GET' })
}

/** 更新系统配置 */
export function updateSystemConfigApi(config) {
  return request({ url: '/api/admin/system/config', method: 'PUT', data: config })
}

/** 获取存储统计 */
export function getStorageStatsApi() {
  return request({ url: '/api/admin/storage/stats', method: 'GET' })
}