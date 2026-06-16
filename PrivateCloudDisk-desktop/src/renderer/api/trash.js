/**
 * api/trash.js - 回收站 API
 *
 * 后端: Spring Boot TrashController
 * 端点前缀: /business/trash
 */
import { get, post, del } from '@/utils/request'

/** 获取回收站列表 (分页)
 *  Query: page, size
 */
export function getTrashList(params) {
  return get('/trash', params)
}

/** 获取回收站统计信息 (总数量 + 总大小) */
export function getTrashStats() {
  return get('/trash/count')
}

/** 移入回收站 - 文件
 *  POST /business/trash/files/{file_id}
 */
export function moveFileToTrash(fileId) {
  return post(`/trash/files/${fileId}`)
}

/** 移入回收站 - 文件夹
 *  POST /business/trash/folders/{node_id}
 */
export function moveFolderToTrash(nodeId) {
  return post(`/trash/folders/${nodeId}`)
}

/** 从回收站恢复
 *  POST /business/trash/{trash_id}/restore
 */
export function restoreFromTrash(trashId) {
  return post(`/trash/${trashId}/restore`)
}

/** 彻底删除回收站记录
 *  DELETE /business/trash/{trash_id}
 */
export function permanentDelete(trashId) {
  return del(`/trash/${trashId}`)
}