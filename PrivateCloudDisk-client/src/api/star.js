/**
 * api/star.js - 文件收藏 API
 *
 * 后端: FileStarController -> /business/stars
 */
import { get, post, del } from '@/utils/request'

const BASE = '/business/stars'

/** 添加收藏 */
export function addStar(fileId) {
  return post(`${BASE}/${fileId}`)
}

/** 取消收藏 */
export function removeStar(fileId) {
  return del(`${BASE}/${fileId}`)
}

/** 检查是否已收藏 */
export function checkStarStatus(fileId) {
  return get(`${BASE}/${fileId}/status`)
}

/** 获取收藏列表 (分页) */
export function getStarList(params = {}) {
  return get(`${BASE}/`, params)
}

/** 收藏数量统计 */
export function getStarCount() {
  return get(`${BASE}/count`)
}