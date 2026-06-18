// ============================================================
// shares.ts — 分享链接 API 模块
// ============================================================
// 封装分享链接相关的所有接口调用，包括管理端（创建、查看、
// 撤销分享）和公开访问端（获取分享信息、验证密码、浏览内容）。
//
// 与后端 ShareController 对应：
//   管理端（需要登录）：
//     POST   /business/shares                  创建分享
//     GET    /business/shares                  获取我的分享列表
//     DELETE /business/shares/{share_id}       撤销分享
//   公开访问端（无需登录）：
//     GET    /public/shares/{token}/info       获取分享公开信息
//     POST   /public/shares/{token}/verify     验证密码获取访问令牌
//     GET    /public/shares/{token}/content    获取分享内容（需 X-Share-Access-Token）
//     GET    /public/shares/{token}/files/{id}/download  下载分享文件
// ============================================================

import { get, post, del } from '@/utils/request'

// ============================================================
// 类型定义
// ============================================================

/** 分享链接 VO（管理端返回） */
export interface ShareLinkItem {
  share_id: string
  share_token: string
  share_url: string
  share_target_type: 'file' | 'folder'
  share_name: string
  target_name: string
  target_size: number
  file_type: string | null
  share_has_password: boolean
  share_expires_at: string | null
  share_view_count: number
  share_status: 'active' | 'revoked' | 'expired'
  share_created_at: string
}

/** 分享公开信息 VO（访问分享链接时返回） */
export interface ShareAccessInfo {
  share_token: string
  share_name: string
  share_target_type: 'file' | 'folder'
  target_name: string
  target_size: number
  file_type: string | null
  owner_name: string
  has_password: boolean
  is_expired: boolean
  is_revoked: boolean
  expires_at: string | null
  created_at: string
}

/** 分享内容实体（用于展示分享的文件/文件夹） */
export interface ShareContent {
  share_id: string
  share_token: string
  share_target_type: 'file' | 'folder'
  share_name: string
  share_file_id?: string
  share_node_id?: string
  target_name: string
  target_size: number
  file_type: string | null
  owner_name: string
}

/** 创建分享请求 */
export interface ShareCreateParams {
  target_type: 'file' | 'folder'
  file_id?: string
  node_id?: string
  share_name: string
  password?: string
  expires_in_days: number
}

// ============================================================
// 管理端 API（需要登录）
// ============================================================

/**
 * 创建分享链接
 */
export function createShareApi(params: ShareCreateParams): Promise<ShareLinkItem> {
  return post<ShareLinkItem>('business/shares', params)
}

/**
 * 获取我的分享列表
 */
export function getMySharesApi(): Promise<ShareLinkItem[]> {
  return get<ShareLinkItem[]>('business/shares')
}

/**
 * 撤销分享链接
 */
export function revokeShareApi(share_id: string): Promise<void> {
  return del<void>(`business/shares/${share_id}`)
}

// ============================================================
// 公开访问端 API（无需登录）
// ============================================================

/**
 * 获取分享公开信息（展示分享链接页面）
 */
export function getShareInfoApi(share_token: string): Promise<ShareAccessInfo> {
  return get<ShareAccessInfo>(`public/shares/${share_token}/info`)
}

/**
 * 验证提取码并获取访问令牌
 * @param share_token 分享令牌
 * @param password 客户端预哈希后的密码（PBKDF2-SHA256）
 * @returns 短期访问令牌（JWT，15 分钟有效）
 */
export function verifySharePasswordApi(share_token: string, password: string): Promise<string> {
  return post<string>(`public/shares/${share_token}/verify`, { password })
}

/**
 * 获取分享内容（需要携带访问令牌）
 */
export function getShareContentApi(share_token: string, access_token: string): Promise<ShareContent> {
  return get<ShareContent>(`public/shares/${share_token}/content`, undefined, {
    headers: { 'X-Share-Access-Token': access_token }
  })
}

/**
 * 获取分享文件下载信息
 */
export function getSharedFileDownloadApi(
  share_token: string,
  file_id: string,
  access_token: string
): Promise<any> {
  return get<any>(`public/shares/${share_token}/files/${file_id}/download`, undefined, {
    headers: { 'X-Share-Access-Token': access_token }
  })
}

/**
 * 获取分享文件夹子内容（浏览文件夹）
 * @param share_token 分享令牌
 * @param node_id 节点ID（null 表示浏览根目录）
 * @param access_token 访问令牌
 */
export function getSharedFolderChildrenApi(
  share_token: string,
  node_id: string | null,
  access_token: string
): Promise<ShareContentItem[]> {
  const path = node_id
    ? `public/shares/${share_token}/folders/${node_id}/children`
    : `public/shares/${share_token}/children`
  return get<ShareContentItem[]>(path, undefined, {
    headers: { 'X-Share-Access-Token': access_token }
  })
}

/** 分享文件夹内容项 */
export interface ShareContentItem {
  item_type: 'file' | 'folder'
  file_id?: string
  node_id?: string
  name: string
  size: number
  file_type?: string
}

// ============================================================
// 工具函数
// ============================================================

/**
 * 格式化分享链接为完整 URL
 */
export function formatShareUrl(share_token: string): string {
  const base = window.location.origin
  return `${base}/share/${share_token}`
}

/**
 * 格式化文件大小
 */
export function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const k = 1024
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + units[i]
}

/**
 * 获取分享状态文本
 */
export function getShareStatusText(status: string): string {
  const map: Record<string, string> = {
    active: '有效',
    revoked: '已撤销',
    expired: '已过期'
  }
  return map[status] || status
}

/**
 * 获取分享状态颜色
 */
export function getShareStatusColor(status: string): string {
  const map: Record<string, string> = {
    active: '#52c41a',
    revoked: '#ff4d4f',
    expired: '#faad14'
  }
  return map[status] || '#999'
}