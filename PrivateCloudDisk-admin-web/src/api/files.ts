// ============================================================
// 管理后台 - 文件管理 API
// ============================================================
import { request } from '@/utils/request'
import type { ApiResponse, PageResult, FileNode, FileFilterParams, StorageStats } from '@/types/api'

/** 获取所有文件列表（管理员视角） */
export function getFilesApi(params: FileFilterParams) {
  return request.get<ApiResponse<PageResult<FileNode>>>('/api/admin/files', { params })
}

/** 获取文件详情 */
export function getFileDetailApi(fileId: string) {
  return request.get<ApiResponse<FileNode>>(`/api/admin/files/${fileId}`)
}

/** 删除文件（管理员强制删除） */
export function adminDeleteFileApi(fileId: string) {
  return request.delete<ApiResponse<null>>(`/api/admin/files/${fileId}`)
}

/** 批量删除文件 */
export function batchDeleteFilesApi(fileIds: string[]) {
  return request.post<ApiResponse<{ successCount: number; failCount: number }>>(
    '/api/admin/files/batch-delete',
    { fileIds }
  )
}

/** 获取被隔离的病毒文件列表 */
export function getQuarantinedFilesApi(params: FileFilterParams) {
  return request.get<ApiResponse<PageResult<FileNode>>>('/api/admin/files/quarantined', { params })
}

/** 恢复隔离文件 */
export function restoreQuarantinedFileApi(fileId: string) {
  return request.post<ApiResponse<null>>(`/api/admin/files/${fileId}/restore`)
}

/** 获取存储统计 */
export function getStorageStatsApi() {
  return request.get<ApiResponse<StorageStats>>('/api/admin/storage/stats')
}

/** 获取存储趋势 */
export function getStorageTrendApi(days: number = 30) {
  return request.get<ApiResponse<{ date: string; bytes: number }[]>>('/api/admin/storage/trend', {
    params: { days },
  })
}