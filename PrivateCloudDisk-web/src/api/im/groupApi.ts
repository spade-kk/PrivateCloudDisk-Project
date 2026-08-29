import { del, get, post, put } from '@/utils/request'
import type { GroupDTO, GroupMemberDTO, GroupRole, PageResult, Result } from './types'

const IM_GROUPS_BASE = 'im/groups'

/**
 * GROUP-CHAT-20260810 [6.1-6.15]：群组 API 由旧 imApi.ts 拆分，所有变更操作均显式
 * 携带操作者 ID。IM Platform 目前不会从网关注入主体，因此不能在浏览器端假定服务端已知用户。
 */
export interface CreateGroupInput { ownerId: string; groupName: string; avatarFileId?: string; memberIds: string[]; joinMode?: number }
export interface UpdateGroupInput { operatorId: string; name?: string; avatarFileId?: string; announcement?: string; description?: string; joinMode?: number }

export function createGroupApi(input: CreateGroupInput): Promise<Result<GroupDTO>> { return post(IM_GROUPS_BASE, input, { silent: true }) }
export function listGroupsApi(userId: string, page = 1, size = 50): Promise<Result<PageResult<GroupDTO>>> { return get(IM_GROUPS_BASE, { userId, page, size }, { silent: true }) }
export function getGroupApi(groupId: string, userId: string): Promise<Result<GroupDTO>> { return get(`${IM_GROUPS_BASE}/${groupId}`, { userId }, { silent: true }) }
export function updateGroupApi(groupId: string, input: UpdateGroupInput): Promise<Result<GroupDTO>> { return put(`${IM_GROUPS_BASE}/${groupId}`, input, { silent: true }) }
export function inviteGroupMembersApi(groupId: string, operatorId: string, userIds: string[]): Promise<Result<void>> { return post(`${IM_GROUPS_BASE}/${groupId}/members`, { operatorId, userIds }, { silent: true }) }
export function listGroupMembersApi(groupId: string, userId: string, page = 1, size = 100): Promise<Result<PageResult<GroupMemberDTO>>> { return get(`${IM_GROUPS_BASE}/${groupId}/members`, { userId, page, size }, { silent: true }) }
export function removeGroupMemberApi(groupId: string, operatorId: string, userId: string): Promise<Result<void>> { return del(`${IM_GROUPS_BASE}/${groupId}/members/${userId}`, { params: { operatorId }, silent: true }) }
export function leaveGroupApi(groupId: string, userId: string): Promise<Result<void>> { return del(`${IM_GROUPS_BASE}/${groupId}/members/self`, { params: { userId }, silent: true }) }
export function dissolveGroupApi(groupId: string, ownerId: string): Promise<Result<void>> { return del(`${IM_GROUPS_BASE}/${groupId}`, { params: { ownerId }, silent: true }) }
export function setGroupMemberRoleApi(groupId: string, userId: string, operatorId: string, role: GroupRole): Promise<Result<void>> { return put(`${IM_GROUPS_BASE}/${groupId}/members/${userId}/role`, { operatorId, role }, { silent: true }) }
export function muteGroupMemberApi(groupId: string, userId: string, operatorId: string, durationMinutes: number): Promise<Result<void>> { return post(`${IM_GROUPS_BASE}/${groupId}/members/${userId}/mute`, { operatorId, durationMinutes }, { silent: true }) }
export function unmuteGroupMemberApi(groupId: string, userId: string, operatorId: string): Promise<Result<void>> { return del(`${IM_GROUPS_BASE}/${groupId}/members/${userId}/mute`, { params: { operatorId }, silent: true }) }
