import {get, post} from '@/utils/request'

/**
 * 获取我的账号配额信息
 * @returns {Promise}
 */
export function getMyUserQuotaInfoApi() {
  return get('business/quotas/me');
}