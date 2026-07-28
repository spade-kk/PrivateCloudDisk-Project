// ============================================================
// stars.ts — 文件/文件夹收藏 API 模块
// ============================================================
// 封装收藏相关的所有接口调用，包括文件收藏、文件夹收藏、
// 收藏列表查询、收藏状态检查等。
// 与后端 FileStarController 对应：
//   POST   /business/stars/files/{id}       添加文件收藏
//   DELETE /business/stars/files/{id}       取消文件收藏
//   GET    /business/stars/files/{id}/status 检查文件收藏状态
//   POST   /business/stars/folders/{id}     添加文件夹收藏
//   DELETE /business/stars/folders/{id}     取消文件夹收藏
//   GET    /business/stars/folders/{id}/status 检查文件夹收藏状态
//   GET    /business/stars/                  获取收藏列表（分页）
//   GET    /business/stars/count             统计收藏总数
//   GET    /business/stars/file-ids           获取收藏文件ID列表
//   GET    /business/stars/folder-ids         获取收藏文件夹ID列表
// ============================================================

import { get, post, del } from '@/utils/request'

// ============================================================
// 类型定义
// ============================================================

/** 收藏项 VO（对应后端 FileStarVO） */
export interface StarredItem {
  star_id: number
  target_type: 'file' | 'folder'
  target_id: string
  target_name: string
  target_size: number
  file_type: string | null
  file_status: string | null
  starred_at: string
}

/** 前端节点格式（适配 FileGridView） */
export interface StarredNode {
  node_id: string
  node_name: string
  node_type: 'FILE' | 'FOLDER'
  node_size: number
  file_type?: string
  starred_at?: string
  star_id?: number
}

// ============================================================
// 文件收藏
// ============================================================

/**
 * 添加文件收藏
 */
export function addFileStarApi(file_id: string): Promise<any> {
  return post(`business/stars/files/${file_id}`)
}

/**
 * 取消文件收藏
 */
export function removeFileStarApi(file_id: string): Promise<any> {
  return del(`business/stars/files/${file_id}`)
}

/**
 * 检查文件是否已收藏
 */
export function checkFileStarredApi(file_id: string): Promise<boolean> {
  return get(`business/stars/files/${file_id}/status`)
}

// ============================================================
// 文件夹收藏
// ============================================================

/**
 * 添加文件夹收藏
 */
export function addFolderStarApi(node_id: string): Promise<any> {
  return post(`business/stars/folders/${node_id}`)
}

/**
 * 取消文件夹收藏
 */
export function removeFolderStarApi(node_id: string): Promise<any> {
  return del(`business/stars/folders/${node_id}`)
}

/**
 * 检查文件夹是否已收藏
 */
export function checkFolderStarredApi(node_id: string): Promise<boolean> {
  return get(`business/stars/folders/${node_id}/status`)
}

// ============================================================
// 收藏列表 & 统计
// ============================================================

/**
 * 获取收藏列表（含文件/文件夹详情，分页）
 * 后端返回统一格式 { code: 200, message: null, data: StarredItem[] }
 */
export function getStarredItemsApi(page: number = 1, pageSize: number = 50): Promise<{ code: number; message: string | null; data: StarredItem[] }> {
  return get(`business/stars/?page=${page}&pageSize=${pageSize}`)
}

/**
 * 统计收藏总数
 */
export function countStarredItemsApi(): Promise<number> {
  return get('business/stars/count')
}

/**
 * 获取收藏的文件ID列表（用于批量判断收藏状态）
 * 后端返回统一格式 { code: 200, message: null, data: string[] }
 */
export function getStarredFileIdsApi(): Promise<{ code: number; message: string | null; data: string[] }> {
  return get('business/stars/file-ids')
}

/**
 * 获取收藏的文件夹ID列表（用于批量判断收藏状态）
 * 后端返回统一格式 { code: 200, message: null, data: string[] }
 */
export function getStarredNodeIdsApi(): Promise<{ code: number; message: string | null; data: string[] }> {
  return get('business/stars/folder-ids')
}

// ============================================================
// 工具函数
// ============================================================

/**
 * 将后端 StarredItem 转换为前端 FileGridView 需要的节点格式
 */
export function starredItemToNode(item: StarredItem): StarredNode {
  return {
    node_id: item.target_id,
    node_name: item.target_name,
    node_type: item.target_type === 'folder' ? 'FOLDER' : 'FILE',
    node_size: item.target_size ?? 0,
    file_type: item.file_type ?? undefined,
    starred_at: item.starred_at,
    star_id: item.star_id,
  }
}