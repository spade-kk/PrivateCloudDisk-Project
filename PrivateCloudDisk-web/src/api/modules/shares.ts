// ============================================================
// shares.ts — 分享链接 API 模块（v2 — 多资源分享模型）
// ============================================================
// 封装分享链接相关的所有接口调用，包括管理端（创建、查看、
// 撤销分享）和公开访问端（获取分享信息、验证密码、浏览内容）。
//
// 参数格式约束（与后端校验一致）：
//   share_token        : 12 位字母数字（如 "aB3xK9mP2qR7"），类似 B 站 BV 号
//   share_resource_id  : UUID 格式（如 "550e8400-e29b-..."）或 Base64URL 格式
//   password（提取码）  : 4-20 位字母数字组合，禁止特殊字符
//
// 与后端 ShareController 对应：
//   管理端（需要登录）：
//     POST   /business/shares                      创建分享（多资源）
//     GET    /business/shares                      获取我的分享列表（不含资源）
//     GET    /business/shares/{share_id}           获取分享详情（含资源+提取码）
//     DELETE /business/shares/{share_id}           撤销分享
//   公开访问端（无需登录）：
//     GET    /business/public/shares/{token}/info                          获取分享公开信息
//     POST   /business/public/shares/{token}/verify                        验证密码获取访问令牌
//     GET    /business/public/shares/{token}/content                       获取分享内容（需 X-Share-Access-Token）
//     GET    /business/public/shares/{token}/resources/{res_id}/children   浏览文件夹（需 X-Share-Access-Token）
//     GET    /business/public/shares/{token}/resources/{res_id}/download   下载文件（需 X-Share-Access-Token）
// ============================================================

import { get, post, put, del } from '@/utils/request'

// ============================================================
// 类型定义（v2 — 多资源分享模型）
// ============================================================

/** 分享资源 VO */
export interface ShareResourceVO {
  share_resource_id: string
  resource_type: 'file' | 'folder'
  resource_name: string
  resource_size: number
  file_type: string | null
}

/** 分享链接 VO（管理端列表返回，不含资源列表和密码） */
export interface ShareLinkItem {
  share_id: string
  share_token: string
  share_url: string
  share_name: string
  share_description?: string | null
  share_has_password: boolean
  share_expires_at: string | null
  share_view_count: number
  share_status: 'active' | 'revoked' | 'expired'
  share_created_at: string
  resource_count: number
}

/** 分享链接详情 VO（管理端详情返回，含资源列表和明文提取码） */
export interface ShareDetailVO {
  share_id: string
  share_token: string
  share_url: string
  share_name: string
  share_description?: string | null
  owner_name: string
  share_has_password: boolean
  share_password: string | null
  share_expires_at: string | null
  share_view_count: number
  share_status: 'active' | 'revoked' | 'expired'
  share_created_at: string
  resource_count: number
  resources: ShareResourceVO[]
}

/** 分享公开信息 VO（访问分享链接时返回，不含资源列表） */
export interface ShareAccessInfo {
  share_token: string
  share_name: string
  share_description?: string | null
  owner_name: string
  has_password: boolean
  is_expired: boolean
  is_revoked: boolean
  expires_at: string | null
  created_at: string
  resource_count: number
}

/** 密码验证成功后返回的令牌 VO */
export interface ShareAccessTokenVO {
  access_token: string
  share_name: string
  resource_count: number
}

/** 创建分享时提交的资源项 */
export interface ShareResourceItem {
  type: 'file' | 'folder'
  id: string
}

/** 创建分享请求 */
export interface ShareCreateParams {
  resources: ShareResourceItem[]
  share_name: string
  share_description?: string
  password?: string
  expires_in_days: number
}

/** 分享文件夹内容项 */
export interface ShareContentItem {
  item_type: 'file' | 'folder'
  /** 分享资源ID（虚拟标识符，用于导航子节点、下载等操作，不暴露内部 file_id/node_id） */
  share_resource_id: string
  name: string
  size: number
  file_type?: string
}

// ============================================================
// 管理端 API（需要登录）
// ============================================================

/**
 * 创建分享链接（v2 — 多资源）
 * 密码为明文传入，服务端 AES 加密存储
 */
export function createShareApi(params: ShareCreateParams): Promise<{ code: number; message: string | null; data: ShareLinkItem }> {
  return post('business/shares', params)
}

/**
 * 获取我的分享列表（不含资源列表和提取码）
 */
export function getMySharesApi(): Promise<{ code: number; message: string | null; data: ShareLinkItem[] }> {
  return get('business/shares')
}

/**
 * 获取分享链接详情（管理端，含资源列表和明文提取码）
 */
export function getShareDetailApi(share_id: string): Promise<{ code: number; message: string | null; data: ShareDetailVO }> {
  return get(`business/shares/${share_id}`)
}

/**
 * 撤销分享链接
 */
export function revokeShareApi(share_id: string): Promise<{ code: number; message: string | null; data: null }> {
  return del(`business/shares/${share_id}`)
}

/**
 * 修改分享链接提取码（管理端）
 * @param share_id 分享ID
 * @param password 新提取码明文，传空字符串表示移除密码
 */
export function updateSharePasswordApi(share_id: string, password: string): Promise<{ code: number; message: string | null; data: null }> {
  return put(`business/shares/${share_id}/password`, { password })
}

// ============================================================
// 公开访问端 API（无需登录）
// ============================================================

/**
 * 获取分享公开信息（不含资源列表）
 * 用于展示分享链接页面，返回分享名称、创建者、是否需要密码等
 */
export function getShareInfoApi(share_token: string): Promise<{ code: number; message: string | null; data: ShareAccessInfo }> {
  return get(`business/public/shares/${share_token}/info`)
}

/**
 * 验证提取码并获取访问令牌
 * @param share_token 分享令牌
 * @param password 明文提取码（不再需要客户端哈希）
 * @returns 访问令牌 VO（含 access_token、share_name、resource_count）
 */
export function verifySharePasswordApi(share_token: string, password: string): Promise<{ code: number; message: string | null; data: ShareAccessTokenVO }> {
  return post(`business/public/shares/${share_token}/verify`, { password })
}

/**
 * 获取分享内容（根资源列表，需要携带访问令牌）
 * 返回分享链接中的顶层资源列表，每个资源包含 share_resource_id
 */
export function getShareContentApi(share_token: string, access_token: string): Promise<{ code: number; message: string | null; data: ShareResourceVO[] }> {
  return get(`business/public/shares/${share_token}/content`, undefined, {
    headers: { 'X-Share-Access-Token': access_token }
  })
}

/**
 * 浏览分享文件夹的子内容（通过 share_resource_id）
 * @param share_token 分享令牌
 * @param share_resource_id 分享资源ID
 * @param access_token 访问令牌
 */
export function getSharedFolderChildrenApi(
  share_token: string,
  share_resource_id: string,
  access_token: string
): Promise<{ code: number; message: string | null; data: ShareContentItem[] }> {
  return get(
    `business/public/shares/${share_token}/resources/${share_resource_id}/children`,
    undefined,
    { headers: { 'X-Share-Access-Token': access_token } }
  )
}

/**
 * 下载分享文件（通过 share_resource_id）
 * @param share_token 分享令牌
 * @param share_resource_id 分享资源ID
 * @param access_token 访问令牌
 */
export function getSharedFileDownloadApi(
  share_token: string,
  share_resource_id: string,
  access_token: string
): Promise<any> {
  return get<any>(
    `business/public/shares/${share_token}/resources/${share_resource_id}/download`,
    undefined,
    { headers: { 'X-Share-Access-Token': access_token } }
  )
}

// ============================================================
// 工具函数
// ============================================================

/**
 * 格式化分享链接为完整 URL（含提取码）
 */
export function formatShareUrl(share_token: string, password?: string): string {
  const base = window.location.origin
  const pwd = password ? `?pwd=${encodeURIComponent(password)}` : ''
  return `${base}/share/${share_token}${pwd}`
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

// ============================================================
// 参数格式校验（与后端校验规则一致）
// ============================================================

/**
 * 校验提取码格式：4-20 位字母数字组合，禁止特殊字符
 * @returns 校验通过返回 null，失败返回错误信息
 */
export function validatePassword(password: string): string | null {
  if (!password || password.trim().length === 0) {
    return '提取码不能为空'
  }
  if (password.length < 4) {
    return '提取码最少为4位'
  }
  if (password.length > 20) {
    return '提取码最多为20位'
  }
  if (!/^[A-Za-z0-9]+$/.test(password)) {
    return '提取码只能包含字母和数字，不能包含特殊字符'
  }
  return null
}

/**
 * 校验分享令牌格式：12 位字母数字
 * @returns 校验通过返回 null，失败返回错误信息
 */
export function validateShareToken(token: string): string | null {
  if (!token || !/^[A-Za-z0-9]{12}$/.test(token)) {
    return '分享链接格式错误'
  }
  return null
}
