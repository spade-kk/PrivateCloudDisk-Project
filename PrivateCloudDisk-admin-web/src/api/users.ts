// ============================================================
// 管理后台 - 用户管理 API
// ============================================================
import { request } from '@/utils/request'
import type { ApiResponse, PageResult, User, PageParams, BatchUserAction } from '@/types/api'

/** 获取用户列表（分页 + 筛选） */
export function getUsersApi(params: PageParams) {
  return request.get<ApiResponse<PageResult<User>>>('/api/admin/users', { params })
}

/** 获取用户详情 */
export function getUserDetailApi(userId: string) {
  return request.get<ApiResponse<User>>(`/api/admin/users/${userId}`)
}

/** 禁用/启用用户 */
export function toggleUserStatusApi(userId: string, status: string) {
  return request.put<ApiResponse<null>>(`/api/admin/users/${userId}/status`, { status })
}

/** 修改用户角色 */
export function updateUserRoleApi(userId: string, role: string) {
  return request.put<ApiResponse<null>>(`/api/admin/users/${userId}/role`, { role })
}

/** 修改用户存储配额 */
export function updateUserQuotaApi(userId: string, quotaBytes: number) {
  return request.put<ApiResponse<null>>(`/api/admin/users/${userId}/quota`, { quotaBytes })
}

/** 删除用户 */
export function deleteUserApi(userId: string) {
  return request.delete<ApiResponse<null>>(`/api/admin/users/${userId}`)
}

/** 批量操作用户 */
export function batchUserActionApi(data: BatchUserAction) {
  return request.post<ApiResponse<{ successCount: number; failCount: number }>>(
    '/api/admin/users/batch',
    data
  )
}

/** 导出用户列表 */
export function exportUsersApi(params: PageParams) {
  return request.get('/api/admin/users/export', { params, responseType: 'blob' })
}

/** 获取用户文件列表 */
export function getUserFilesApi(userId: string, params: PageParams) {
  return request.get<ApiResponse<PageResult<unknown>>>(
    `/api/admin/users/${userId}/files`,
    { params }
  )
}