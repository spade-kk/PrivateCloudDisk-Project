/**
 * api/quota.js - 配额查询 API
 *
 * 后端: Spring Boot QuotaController
 * 端点前缀: /business/quotas
 */
import { get } from '@/utils/request'

/** 获取当前用户配额信息 */
export function getQuota() {
  return get('/quotas')
}