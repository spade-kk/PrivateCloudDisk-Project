// ============================================================
// recent.ts — 最近访问 API 模块
// ============================================================
// 封装最近访问记录相关的接口调用，包括最近上传、最近下载、最近打开等。
// 与后端 RecentAccessController 对应：
//   GET /business/recent?type=upload    — 最近上传
//   GET /business/recent?type=download  — 最近下载
//   GET /business/recent?type=open      — 最近打开
//   GET /business/recent                 — 所有最近访问（混合）
// ============================================================

import { get } from '@/utils/request'

// ============================================================
// 类型定义
// ============================================================

/** 访问类型 */
export type AccessType = 'upload' | 'download' | 'open'

/** 最近访问记录 VO */
export interface RecentAccessVO {
  ra_id: number
  target_id: string
  target_type: 'file' | 'folder'
  access_type: AccessType
  target_name: string
  target_size: number
  file_type: string | null
  accessed_at: string
}

// ============================================================
// API 函数
// ============================================================

/**
 * 获取最近访问记录
 * 注意：后端返回统一格式 { code: 200, message: null, data: RecentAccessVO[] }
 * 调用方（store）需自行解包，遵循项目统一的响应处理规范。
 *
 * @param type 访问类型筛选（为空则返回所有类型混合）
 * @param page 页码
 * @param pageSize 每页数量
 */
export function getRecentAccessApi(
  type?: AccessType,
  page: number = 1,
  pageSize: number = 50,
): Promise<{ code: number; message: string | null; data: RecentAccessVO[] }> {
  const params: Record<string, any> = { page, pageSize }
  if (type) {
    params.type = type
  }
  // 直接传递 params 对象，避免 { params } 导致的双层嵌套
  return get('business/recent', params)
}

/**
 * 获取最近上传
 */
export function getRecentUploadsApi(page: number = 1, pageSize: number = 50): Promise<{ code: number; message: string | null; data: RecentAccessVO[] }> {
  return getRecentAccessApi('upload', page, pageSize)
}

/**
 * 获取最近下载
 */
export function getRecentDownloadsApi(page: number = 1, pageSize: number = 50): Promise<{ code: number; message: string | null; data: RecentAccessVO[] }> {
  return getRecentAccessApi('download', page, pageSize)
}

/**
 * 获取最近打开
 */
export function getRecentOpensApi(page: number = 1, pageSize: number = 50): Promise<{ code: number; message: string | null; data: RecentAccessVO[] }> {
  return getRecentAccessApi('open', page, pageSize)
}