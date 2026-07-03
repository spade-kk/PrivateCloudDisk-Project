// ============================================================
// space.ts — 空间系统 API 模块
// ============================================================

import { get, post, put, del } from '@/utils/request'

// ============================================================
// 类型定义
// ============================================================

export interface SpaceInfo {
  spaceId: string
  spaceName: string
  spaceType: 'personal' | 'enterprise' | 'public' | 'team'
  spaceOwnerId: string
  spaceQuota: number
  spaceUsed: number
  spaceFileCount: number
  spaceVisibility: 'private' | 'public' | 'whitelist' | 'blacklist'
  spaceDescription: string
  spaceAvatarPath: string
  spaceImGroupId: string
  spaceCreatedAt: string
  spaceUpdatedAt: string
  spaceStatus: 'active' | 'disabled' | 'deleted'
}

export interface SpaceMember {
  memberId: number
  spaceId: string
  userId: string
  role: 'owner' | 'admin' | 'editor' | 'viewer'
  joinedAt: string
  invitedBy: string | null
}

export interface SpacePermission {
  permissionId: number
  spaceId: string
  userId: string
  targetNodeId: string | null
  canRead: boolean
  canWrite: boolean
  canDelete: boolean
  canShare: boolean
  canInvite: boolean
  canManage: boolean
  grantedBy: string
  grantedAt: string
}

export interface SpaceJoinRequest {
  requestId: number
  spaceId: string
  userId: string
  requestMessage: string
  status: 'pending' | 'approved' | 'rejected'
  reviewedBy: string | null
  reviewedAt: string | null
  createdAt: string
}

export interface SpaceVisibility {
  visibilityId: number
  spaceId: string
  userId: string
  listType: 'whitelist' | 'blacklist'
  createdAt: string
}

export interface CreateSpaceParams {
  spaceName: string
  spaceType: string
  spaceDescription?: string
  spaceVisibility?: string
}

export interface UpdateSpaceParams {
  spaceName?: string
  spaceDescription?: string
  spaceVisibility?: string
  spaceQuota?: number
}

// ============================================================
// 空间 CRUD
// ============================================================

export function createSpaceApi(data: CreateSpaceParams): Promise<{ code: number; data: SpaceInfo }> {
  return post('business/spaces', data)
}

export function listSpacesApi(type?: string): Promise<{ code: number; data: SpaceInfo[] }> {
  return get('business/spaces', { type })
}

export function getSpaceApi(spaceId: string): Promise<{ code: number; data: SpaceInfo }> {
  return get(`business/spaces/${spaceId}`)
}

export function updateSpaceApi(spaceId: string, data: UpdateSpaceParams): Promise<{ code: number; data: SpaceInfo }> {
  return put(`business/spaces/${spaceId}`, data)
}

export function deleteSpaceApi(spaceId: string): Promise<{ code: number }> {
  return del(`business/spaces/${spaceId}`)
}

// ============================================================
// 成员管理
// ============================================================

export function addMemberApi(spaceId: string, userId: string, role: string): Promise<{ code: number }> {
  return post(`business/spaces/${spaceId}/members`, { userId, role })
}

export function listMembersApi(spaceId: string): Promise<{ code: number; data: SpaceMember[] }> {
  return get(`business/spaces/${spaceId}/members`)
}

export function updateMemberRoleApi(spaceId: string, targetUserId: string, role: string): Promise<{ code: number }> {
  return put(`business/spaces/${spaceId}/members/${targetUserId}/role`, { role })
}

export function removeMemberApi(spaceId: string, targetUserId: string): Promise<{ code: number }> {
  return del(`business/spaces/${spaceId}/members/${targetUserId}`)
}

// ============================================================
// 权限管理
// ============================================================

export function updatePermissionApi(spaceId: string, targetUserId: string, data: Partial<SpacePermission>): Promise<{ code: number }> {
  return put(`business/spaces/${spaceId}/permissions/${targetUserId}`, data)
}

export function getPermissionApi(spaceId: string, targetUserId: string): Promise<{ code: number; data: SpacePermission }> {
  return get(`business/spaces/${spaceId}/permissions/${targetUserId}`)
}

// ============================================================
// 加入申请
// ============================================================

export function requestJoinApi(spaceId: string, message?: string): Promise<{ code: number }> {
  return post(`business/spaces/${spaceId}/join-requests`, null, { params: { message } })
}

export function listJoinRequestsApi(spaceId: string, status?: string): Promise<{ code: number; data: SpaceJoinRequest[] }> {
  return get(`business/spaces/${spaceId}/join-requests`, { status })
}

export function reviewJoinRequestApi(spaceId: string, reqUserId: string, action: string): Promise<{ code: number }> {
  return put(`business/spaces/${spaceId}/join-requests/${reqUserId}`, null, { params: { action } })
}

// ============================================================
// 可见性管理
// ============================================================

export function updateVisibilityListApi(spaceId: string, listType: string, userIds: string[]): Promise<{ code: number }> {
  return put(`business/spaces/${spaceId}/visibility-list`, userIds, { params: { listType } })
}

export function getVisibilityListApi(spaceId: string): Promise<{ code: number; data: SpaceVisibility[] }> {
  return get(`business/spaces/${spaceId}/visibility-list`)
}

// ============================================================
// 公共空间发现
// ============================================================

export function discoverPublicSpacesApi(keyword?: string): Promise<{ code: number; data: SpaceInfo[] }> {
  return get('business/spaces/public/discover', { keyword })
}

export function getPublicSpaceByNameApi(spaceName: string): Promise<{ code: number; data: SpaceInfo }> {
  return get(`business/spaces/public/by-name/${encodeURIComponent(spaceName)}`)
}

// ============================================================
// 当前空间切换
// ============================================================

export function getCurrentSpaceApi(): Promise<{ code: number; data: string }> {
  return get('business/spaces/current')
}

export function setCurrentSpaceApi(spaceId: string): Promise<{ code: number }> {
  return put(`business/spaces/current/${spaceId}`)
}