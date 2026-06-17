// ============================================================
// trash.ts — 回收站管理 API 模块
// ============================================================
// 封装回收站相关操作：获取回收站列表、恢复、永久删除、清空回收站、
// 文件/文件夹移入回收站，以及未读计数。
// 回收站数据保留 30 天后自动清理（由后端定时任务处理）。
// ============================================================

import { get, del, post } from '@/utils/request'

/** 回收站列表查询参数 */
export interface TrashListParams {
  /** 页码，从 1 开始 */
  page?: number
  /** 每页条目数，默认 20 */
  pageSize?: number
}

// ============================================================
// 回收站列表
// ============================================================

/**
 * 获取回收站条目列表（分页）
 *
 * 返回被删除的文件和文件夹列表，按删除时间倒序排列。
 * 每个条目包含原路径、删除时间、剩余恢复天数等信息。
 *
 * @param params - 分页参数
 * @returns Promise<{ items: TrashItem[], total: number, page: number }> 分页列表
 */
export function getTrashTargetsApi(params: TrashListParams = {}): Promise<any> {
  return get('business/trash/', {
    page: params.page ?? 1,
    pageSize: params.pageSize ?? 20,
  })
}

/**
 * 获取单个回收站条目详情
 *
 * @param trashId - 回收站条目 ID
 * @returns Promise<TrashItem> 条目详情
 */
export function getTrashTargetApi(trashId: string): Promise<any> {
  return get(`business/trash/${trashId}`)
}

// ============================================================
// 移入回收站
// ============================================================

/**
 * 将文件移入回收站
 *
 * 文件移入回收站后不会立即删除，可在 30 天内恢复。
 * 移入回收站的文件仍然占用存储空间。
 *
 * @param fileId - 文件 ID
 * @returns Promise 移入结果
 */
export function moveFileToTrashApi(fileId: string): Promise<any> {
  return post(`business/trash/files/${fileId}`)
}

/**
 * 将文件夹移入回收站
 *
 * 文件夹下的所有内容一并移入回收站。
 *
 * @param nodeId - 文件夹节点 ID
 * @returns Promise 移入结果
 */
export function moveFolderToTrashApi(nodeId: string): Promise<any> {
  return post(`business/trash/folders/${nodeId}`)
}

// ============================================================
// 恢复与删除
// ============================================================

/**
 * 从回收站恢复条目
 *
 * 恢复到原位置。如果原路径已不存在，则恢复到根目录。
 *
 * @param trashId - 回收站条目 ID
 * @returns Promise 恢复结果
 */
export function restoreTrashTargetApi(trashId: string): Promise<any> {
  return post(`business/trash/${trashId}/restore`)
}

/**
 * 从回收站永久删除条目
 *
 * 此操作不可逆，删除前应弹出二次确认对话框。
 *
 * @param trashId - 回收站条目 ID
 * @returns Promise 删除结果
 */
export function deleteTrashTargetApi(trashId: string): Promise<any> {
  return del(`business/trash/${trashId}`)
}

/**
 * 一键清空回收站
 *
 * 永久删除回收站中的所有条目，此操作不可逆。
 * 应在用户确认后调用。
 *
 * @returns Promise 清空结果
 */
export function emptyTrashApi(): Promise<any> {
  return del('business/trash/')
}

// ============================================================
// 统计
// ============================================================

/**
 * 获取回收站条目总数
 *
 * 用于侧边栏/导航栏显示回收站未读计数徽章。
 *
 * @returns Promise<{ count: number }> 条目总数
 */
export function countTrashTargetsApi(): Promise<any> {
  return get('business/trash/count')
}