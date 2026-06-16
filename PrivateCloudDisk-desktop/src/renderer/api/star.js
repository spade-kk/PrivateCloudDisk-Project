/**
 * api/star.js - 收藏功能 API
 *
 * 后端: Spring Boot FileStarController
 * 端点前缀: /business/stars
 */
import { get, post, del } from '@/utils/request'

/** 添加收藏
 *  POST /business/stars/{file_id}
 *  无请求体
 */
export function addStar(fileId) {
  return post(`/stars/${fileId}`)
}

/** 取消收藏
 *  DELETE /business/stars/{file_id}
 */
export function removeStar(fileId) {
  return del(`/stars/${fileId}`)
}

/** 获取收藏列表 (分页)
 *  GET /business/stars?page=1&size=20
 */
export function getStarList(params) {
  return get('/stars', params)
}

/** 查询收藏状态
 *  GET /business/stars/{file_id}/status
 */
export function getStarStatus(fileId) {
  return get(`/stars/${fileId}/status`)
}