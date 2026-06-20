/**
 * api/file.js - 文件操作 API
 *
 * 后端: Spring Boot FileController
 * 端点前缀: /business/files
 */
import { get, post, patch, del } from '@/utils/request'

/** 获取文件详情 */
export function getFileDetail(fileId) {
  return get(`/files/${fileId}`)
}

/** 重命名文件
 *  body: { file_new_name: 'newName.txt' }
 *  后端 RenameFileRequest: file_new_name (也接受 @JsonAlias new_name / name)
 */
export function renameFile(fileId, data) {
  return patch(`/files/${fileId}/name`, data)
}

/** 移动文件
 *  body: { target_node_id: 'uuid' }
 *  后端 MoveFileRequest: target_node_id
 */
export function moveFile(fileId, data) {
  return patch(`/files/${fileId}/position`, data)
}

/** 删除文件 (彻底删除, 业务层删除FileEntity记录) */
export function deleteFile(fileId) {
  return del(`/files/${fileId}`)
}

/** 高级搜索 (GET 请求, 参数走 query string) */
export function advancedSearch(params) {
  return get('/files/advanced-search', params)
}