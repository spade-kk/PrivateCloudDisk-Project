/**
 * api/quota.js - 配额 API
 *
 * 后端: QuotaController -> /business/quotas
 */
import { get } from '@/utils/request'

/** 获取当前用户配额 */
export function getMyQuota() {
  return get('/business/quotas/me')
}