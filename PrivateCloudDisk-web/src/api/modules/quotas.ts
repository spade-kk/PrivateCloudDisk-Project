// ============================================================
// quotas.ts — 用户配额 API 模块
// ============================================================
// 封装用户存储配额查询接口，用于在界面上展示用户
// 已使用空间 vs 总空间的关系（进度条、饼图等）。
// ============================================================

import { get } from '@/utils/request'

/**
 * 获取当前用户的存储配额信息
 *
 * 返回用户已使用空间、总空间、套餐类型等信息。
 * 用于侧边栏存储空间进度条、"我的存储" 页面等场景。
 *
 * @returns Promise<{
 *   used_space: number,    // 已使用空间（字节）
 *   total_space: number,   // 总空间（字节）
 *   plan_name: string,     // 套餐名称
 *   usage_percent: number  // 使用百分比
 * }> 配额信息
 *
 * @example
 * const quota = await getMyUserQuotaInfoApi()
 * const percent = (quota.used_space / quota.total_space * 100).toFixed(1)
 * console.log(`已使用 ${percent}%`)
 */
export function getMyUserQuotaInfoApi(): Promise<any> {
  return get('business/quotas/me')
}