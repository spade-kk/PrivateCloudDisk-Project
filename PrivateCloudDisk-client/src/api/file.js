/**
 * api/file.js - 文件操作 API
 *
 * 后端: FileController -> /business/files
 */
import { get, patch, del } from '@/utils/request'

const BASE = '/business'

/** 获取文件详情 */
export function getFileDetail(fileId) {
  return get(`${BASE}/files/${fileId}`)
}

/** 重命名文件 */
export function renameFile(fileId, data) {
  return patch(`${BASE}/files/${fileId}/name`, data)
}

/** 移动文件 */
export function moveFile(fileId, data) {
  return patch(`${BASE}/files/${fileId}/position`, data)
}

/** 删除文件 */
export function deleteFile(fileId) {
  return del(`${BASE}/files/${fileId}`)
}

/** 高级搜索 */
export function advancedSearch(params) {
  return get(`${BASE}/files/advanced-search`, params)
}