/**
 * api/favorite.js - 收藏 API（star.js 的别名模块）
 *
 * 后端: FileStarController -> /business/stars
 *
 * 对标 Vue3 Web 项目 api/modules/stars.ts，
 * 文件与文件夹使用不同的端点路径：
 *   - 文件:   /business/stars/files/{id}
 *   - 文件夹: /business/stars/folders/{id}
 *
 * 本模块是 star.js 的语义别名，方便页面中按 "favorite" 语义调用。
 */
import { get, post, del } from '@/utils/request'

const BASE = '/business/stars'

// ============================================================
// 文件收藏
// ============================================================

/** 添加文件收藏 */
export function addFavorite(fileId) {
  return post(`${BASE}/files/${fileId}`)
}

/** 取消文件收藏 */
export function removeFavorite(fileId) {
  return del(`${BASE}/files/${fileId}`)
}

/** 检查文件是否已收藏 */
export function checkFavoriteStatus(fileId) {
  return get(`${BASE}/files/${fileId}/status`)
}

// ============================================================
// 文件夹收藏
// ============================================================

/** 添加文件夹收藏 */
export function addFolderFavorite(nodeId) {
  return post(`${BASE}/folders/${nodeId}`)
}

/** 取消文件夹收藏 */
export function removeFolderFavorite(nodeId) {
  return del(`${BASE}/folders/${nodeId}`)
}

/** 检查文件夹是否已收藏 */
export function checkFolderFavoriteStatus(nodeId) {
  return get(`${BASE}/folders/${nodeId}/status`)
}

// ============================================================
// 收藏列表 & 统计
// ============================================================

/** 获取收藏列表（分页） */
export function getFavoritesPaged(page = 1, pageSize = 50) {
  return get(`${BASE}/?page=${page}&pageSize=${pageSize}`)
}

/** 收藏数量统计 */
export function getFavoriteCount() {
  return get(`${BASE}/count`)
}

/** 获取收藏的文件 ID 列表 */
export function getFavoriteFileIds() {
  return get(`${BASE}/file-ids`)
}

/** 获取收藏的文件夹 ID 列表 */
export function getFavoriteNodeIds() {
  return get(`${BASE}/folder-ids`)
}