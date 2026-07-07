/**
 * api/trash.js - 回收站 API
 *
 * 后端: TrashController -> /business/trash
 */
import { get, post, del } from '@/utils/request'

const BASE = '/business/trash'

/** 获取回收站列表 (分页) */
export function getTrashList(params = {}) {
  return get(`${BASE}/`, params)
}

/** 回收站数量统计 */
export function getTrashCount() {
  return get(`${BASE}/count`)
}

/** 获取回收站项详情 */
export function getTrashDetail(trashId) {
  return get(`${BASE}/${trashId}`)
}

/** 文件移入回收站 */
export function moveFileToTrash(fileId) {
  return post(`${BASE}/files/${fileId}`)
}

/** 文件夹移入回收站 */
export function moveFolderToTrash(nodeId) {
  return post(`${BASE}/folders/${nodeId}`)
}

/** 从回收站恢复 */
export function restoreFromTrash(trashId) {
  return post(`${BASE}/${trashId}/restore`)
}

/** 彻底删除回收站项 */
export function permanentDelete(trashId) {
  return del(`${BASE}/${trashId}`)
}

/** 清空回收站 */
export function emptyTrash() {
  return del(`${BASE}/`)
}

/** 获取回收站列表 (分页) - 别名 */
export function getTrashListPaged(page = 1, pageSize = 20) {
  return get(`${BASE}/`, { page, pageSize })
}

/** 恢复文件 */
export function restoreFile(fileId) {
  return post(`${BASE}/files/${fileId}/restore`)
}

/** 恢复文件夹 */
export function restoreFolder(nodeId) {
  return post(`${BASE}/folders/${nodeId}/restore`)
}