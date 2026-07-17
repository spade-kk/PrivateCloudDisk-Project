/**
 * api/star.js - 文件/文件夹收藏 API
 *
 * 后端: FileStarController -> /business/stars
 *
 * 对标 Vue3 Web 项目 api/modules/stars.ts，
 * 文件与文件夹使用不同的端点路径：
 *   - 文件:   /business/stars/files/{id}
 *   - 文件夹: /business/stars/folders/{id}
 */
import { get, post, del } from '@/utils/request'

const BASE = '/business/stars'

// ============================================================
// 文件收藏
// ============================================================

/** 添加文件收藏 */
export function addFileStar(fileId) {
  return post(`${BASE}/files/${fileId}`)
}

/** 取消文件收藏 */
export function removeFileStar(fileId) {
  return del(`${BASE}/files/${fileId}`)
}

/** 检查文件是否已收藏 */
export function checkFileStarred(fileId) {
  return get(`${BASE}/files/${fileId}/status`)
}

// ============================================================
// 文件夹收藏
// ============================================================

/** 添加文件夹收藏 */
export function addFolderStar(nodeId) {
  return post(`${BASE}/folders/${nodeId}`)
}

/** 取消文件夹收藏 */
export function removeFolderStar(nodeId) {
  return del(`${BASE}/folders/${nodeId}`)
}

/** 检查文件夹是否已收藏 */
export function checkFolderStarred(nodeId) {
  return get(`${BASE}/folders/${nodeId}/status`)
}

// ============================================================
// 收藏列表 & 统计
// ============================================================

/**
 * 获取收藏列表（分页，含文件/文件夹详情）
 *
 * 对标 Vue3: getStarredItemsApi(page, pageSize)
 */
export function getStarList(page = 1, pageSize = 50) {
  return get(`${BASE}/?page=${page}&pageSize=${pageSize}`)
}

/** 收藏数量统计 */
export function getStarCount() {
  return get(`${BASE}/count`)
}

/** 获取收藏的文件 ID 列表（用于批量判断收藏状态） */
export function getStarredFileIds() {
  return get(`${BASE}/file-ids`)
}

/** 获取收藏的文件夹 ID 列表（用于批量判断收藏状态） */
export function getStarredNodeIds() {
  return get(`${BASE}/folder-ids`)
}

// ============================================================
// 工具函数
// ============================================================

/**
 * 将后端收藏项转换为前端节点格式
 *
 * 对标 Vue3: starredItemToNode(item)
 *
 * @param {Object} item 后端返回的收藏项
 * @param {number} item.star_id
 * @param {string} item.target_type 'file' | 'folder'
 * @param {string} item.target_id 文件或文件夹 ID
 * @param {string} item.target_name 名称
 * @param {number} item.target_size 大小
 * @param {string} [item.file_type] 文件 MIME 类型
 * @param {string} [item.starred_at] 收藏时间
 * @returns {Object} 前端节点格式
 */
export function starredItemToNode(item) {
  return {
    node_id: item.target_id,
    node_name: item.target_name,
    node_type: item.target_type === 'folder' ? 'FOLDER' : 'FILE',
    node_size: item.target_size ?? 0,
    file_type: item.file_type ?? undefined,
    starred_at: item.starred_at,
    star_id: item.star_id
  }
}