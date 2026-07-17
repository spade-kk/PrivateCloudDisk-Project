/**
 * api/trash.js - 回收站 API
 *
 * 后端: TrashController -> /business/trash
 *
 * 对标 Vue3 Web 项目 api/modules/trash.ts，
 * 确保所有端点路径、请求参数、响应格式完全一致。
 *
 * 关键约定：
 *   - 移入回收站：使用原始的 file_id / node_id
 *   - 恢复/永久删除：使用回收站条目的 trash_id（非原始 node_id）
 *   - 回收站列表返回的条目包含 trash_id 字段
 */
import { get, post, del } from '@/utils/request'

const BASE = '/business/trash'

// ============================================================
// 回收站列表
// ============================================================

/**
 * 获取回收站条目列表（分页）
 *
 * 对标 Vue3: getTrashTargetsApi(params)
 *
 * @param {Object} [params] { page, pageSize }
 * @returns Promise<{ items: Array, total: number, page: number }>
 */
export function getTrashList(params = {}) {
  return get(`${BASE}/`, params)
}

/**
 * 获取回收站列表（分页，显式传参）
 *
 * @param {number} page 页码，从 1 开始
 * @param {number} pageSize 每页条目数，默认 20
 */
export function getTrashListPaged(page = 1, pageSize = 20) {
  return get(`${BASE}/`, { page, pageSize })
}

/**
 * 获取单个回收站条目详情
 *
 * 对标 Vue3: getTrashTargetApi(trashId)
 *
 * @param {string} trashId 回收站条目 ID
 */
export function getTrashDetail(trashId) {
  return get(`${BASE}/${trashId}`)
}

// ============================================================
// 移入回收站
// 对标 Vue3: moveFileToTrashApi / moveFolderToTrashApi
// ============================================================

/**
 * 将文件移入回收站
 *
 * @param {string} fileId 文件 ID
 */
export function moveFileToTrash(fileId) {
  return post(`${BASE}/files/${fileId}`)
}

/**
 * 将文件夹移入回收站
 *
 * @param {string} nodeId 文件夹节点 ID
 */
export function moveFolderToTrash(nodeId) {
  return post(`${BASE}/folders/${nodeId}`)
}

// ============================================================
// 恢复与删除
// 注意：恢复和永久删除都使用 trash_id（回收站条目 ID），
// 而非原始的 file_id / node_id。
// 对标 Vue3: restoreTrashTargetApi / deleteTrashTargetApi
// ============================================================

/**
 * 从回收站恢复条目
 *
 * 对标 Vue3: restoreTrashTargetApi(trashId)
 *
 * @param {string} trashId 回收站条目 ID（非原始节点 ID！）
 */
export function restoreFromTrash(trashId) {
  return post(`${BASE}/${trashId}/restore`)
}

/**
 * 从回收站永久删除条目
 *
 * 对标 Vue3: deleteTrashTargetApi(trashId)
 *
 * @param {string} trashId 回收站条目 ID（非原始节点 ID！）
 */
export function permanentDelete(trashId) {
  return del(`${BASE}/${trashId}`)
}

/**
 * 一键清空回收站
 *
 * 对标 Vue3: emptyTrashApi()
 */
export function emptyTrash() {
  return del(`${BASE}/`)
}

// ============================================================
// 统计
// ============================================================

/**
 * 获取回收站条目总数
 *
 * 对标 Vue3: countTrashTargetsApi()
 */
export function getTrashCount() {
  return get(`${BASE}/count`)
}

// ============================================================
// 工具函数
// ============================================================

/**
 * 将后端回收站条目转换为前端 FileItem 期望的节点格式
 *
 * 对标 Vue3 TrashItem 组件的字段使用，
 * 后端返回 target_* 前缀字段，需转换为 node_* 前缀。
 *
 * @param {Object} item 后端返回的回收站条目
 * @param {string} item.trash_id 回收站条目 ID
 * @param {string} item.target_type 'file' | 'folder'
 * @param {string} item.target_id 原始文件/文件夹 ID
 * @param {string} item.target_name 名称
 * @param {number} [item.target_size] 大小
 * @param {string} [item.file_type] 文件 MIME 类型
 * @param {string} [item.deleted_at] 删除时间
 * @returns {Object} 前端节点格式
 */
export function trashItemToNode(item) {
  return {
    node_id: item.target_id,
    node_name: item.target_name,
    node_type: item.target_type === 'folder' ? 'FOLDER' : 'FILE',
    node_size: item.target_size ?? 0,
    file_type: item.file_type ?? undefined,
    deleted_at: item.deleted_at,
    // 保留 trash_id 用于恢复/永久删除操作
    trash_id: item.trash_id
  }
}