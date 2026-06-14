/**
 * 活动日志 API 模块
 * 提供用户操作日志、文件操作记录、系统事件等追溯功能接口
 */
import request from '@/utils/request'

/** 获取活动日志列表 */
export function getActivityLogsApi(params) {
  return request({ url: '/api/activity/logs', method: 'GET', params })
}

/** 获取活动日志详情 */
export function getActivityLogDetailApi(logId) {
  return request({ url: `/api/activity/logs/${logId}`, method: 'GET' })
}

/** 获取活动统计摘要 */
export function getActivitySummaryApi(params) {
  return request({ url: '/api/activity/summary', method: 'GET', params })
}

/** 获取文件操作记录 */
export function getFileOperationsApi(params) {
  return request({ url: '/api/activity/file-operations', method: 'GET', params })
}

/** 获取登录活动记录 */
export function getLoginActivityApi(params) {
  return request({ url: '/api/activity/login', method: 'GET', params })
}

/** 导出活动日志 */
export function exportActivityLogsApi(params) {
  return request({ url: '/api/activity/logs/export', method: 'GET', params, responseType: 'blob' })
}

/** 清理旧日志 */
export function cleanOldLogsApi(days) {
  return request({ url: '/api/activity/logs/clean', method: 'POST', data: { days } })
}