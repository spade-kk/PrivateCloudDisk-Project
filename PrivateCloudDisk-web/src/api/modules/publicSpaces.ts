// ============================================================
// publicSpaces.ts — 公开空间（仓库）API
// ============================================================
// 公开仓库与 shares.ts 严格分离：仓库必须登录，资源归属于独立 space；
// 分享链接仍使用 share token、密码和有效期，不在本模块复用。

import { get, patch, post } from '@/utils/request'

export interface PublicSpaceDetail {
  spaceId: string
  spaceName: string
  description?: string | null
  ownerId: string
  ownerName: string
  ownerAvatar?: string | null
  allowPublicBrowse: boolean
  allowPublicDownload: boolean
  allowPublicUpload: boolean
  fileCount: number
  usedBytes: number
  createdAt?: string
  updatedAt?: string
}

export interface PublicSpaceNode {
  id: string
  name: string
  type: 'folder' | 'file'
  size?: number | null
  fileType?: string | null
  updatedAt?: string | null
}

export interface PublicUserProfile {
  username: string
  userId: string
  displayName: string
  avatar?: string | null
  repositories: PublicSpaceDetail[]
}

export interface PublicSpaceSettingsPayload {
  name?: string
  description?: string
  allowPublicBrowse?: boolean
  allowPublicDownload?: boolean
  allowPublicUpload?: boolean
}

export function getPublicSpaceApi(spaceId: string) {
  return get<{ code: number; data: PublicSpaceDetail }>(`business/public-spaces/${spaceId}`)
}

export function getPublicSpaceRootApi(spaceId: string) {
  return get<{ code: number; data: PublicSpaceNode }>(`business/public-spaces/${spaceId}/root`)
}

export function getPublicSpaceChildrenApi(spaceId: string, nodeId: string) {
  return get<{ code: number; data: PublicSpaceNode[] }>(`business/public-spaces/${spaceId}/nodes/${nodeId}/children`)
}

/** 返回 README 文件 ID，正文继续走既有 preview-content 授权链路。 */
export function getPublicSpaceReadmeApi(spaceId: string) {
  return get<{ code: number; data: string | null }>(`business/public-spaces/${spaceId}/readme`)
}

export function updatePublicSpaceApi(spaceId: string, payload: PublicSpaceSettingsPayload) {
  return patch<{ code: number; data: PublicSpaceDetail }>(`business/public-spaces/${spaceId}`, payload)
}

export function getPublicUserProfileApi(username: string) {
  return get<{ code: number; data: PublicUserProfile }>(`business/public-spaces/users/${encodeURIComponent(username)}`)
}

export function explorePublicSpacesApi(keyword = '') {
  return get<{ code: number; data: PublicSpaceDetail[] }>('business/public-spaces/explore', { keyword: keyword || undefined })
}

export function searchPublicSpacesApi(keyword = '') {
  return get<{ code: number; data: PublicSpaceDetail[] }>('business/public-spaces/search', { keyword: keyword || undefined })
}

export function createPublicUploadSessionApi(spaceId: string, payload: {
  total_chunks: number
  file_size: number
  file_checksum: string
  chunks_max_size: number
  file_type: string
  file_name: string
  node_id: string
}) {
  return post<{ code: number; data: string | { uploads_id: string; remaining_concurrent_sessions?: number } }>(`business/public-spaces/${spaceId}/uploads`, payload)
}
