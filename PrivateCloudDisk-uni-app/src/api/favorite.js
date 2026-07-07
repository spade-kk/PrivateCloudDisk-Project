/**
 * api/favorite.js - 收藏 API
 *
 * 后端: FileStarController -> /business/stars
 */
import { get, post, del } from '@/utils/request'

const BASE = '/business/stars'

/** 添加收藏 */
export function addFavorite(fileId) {
  return post(`${BASE}/${fileId}`)
}

/** 取消收藏 */
export function removeFavorite(fileId) {
  return del(`${BASE}/${fileId}`)
}

/** 检查是否已收藏 */
export function checkFavoriteStatus(fileId) {
  return get(`${BASE}/${fileId}/status`)
}

/** 获取收藏列表 (分页) */
export function getFavoritesPaged(page = 1, pageSize = 20) {
  return get(`${BASE}/`, { page, pageSize })
}

/** 收藏数量统计 */
export function getFavoriteCount() {
  return get(`${BASE}/count`)
}