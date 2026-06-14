/**
 * 数据分析 API 模块
 * 提供使用统计、存储趋势、流量分析等数据可视化功能接口
 */
import request from '@/utils/request'

/** 获取概览统计 */
export function getAnalyticsOverviewApi(params) {
  return request({ url: '/api/analytics/overview', method: 'GET', params })
}

/** 获取存储使用趋势 */
export function getStorageTrendApi(params) {
  return request({ url: '/api/analytics/storage-trend', method: 'GET', params })
}

/** 获取流量趋势 */
export function getTrafficTrendApi(params) {
  return request({ url: '/api/analytics/traffic-trend', method: 'GET', params })
}

/** 获取文件类型分布 */
export function getFileTypeDistributionApi() {
  return request({ url: '/api/analytics/file-types', method: 'GET' })
}

/** 获取用户活跃度 */
export function getUserActivityApi(params) {
  return request({ url: '/api/analytics/user-activity', method: 'GET', params })
}

/** 获取上传/下载统计 */
export function getTransferStatsApi(params) {
  return request({ url: '/api/analytics/transfer-stats', method: 'GET', params })
}

/** 获取热门文件 */
export function getHotFilesApi(params) {
  return request({ url: '/api/analytics/hot-files', method: 'GET', params })
}

/** 导出分析报告 */
export function exportAnalyticsReportApi(params) {
  return request({ url: '/api/analytics/export', method: 'GET', params, responseType: 'blob' })
}