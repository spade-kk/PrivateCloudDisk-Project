// ============================================================
// tags.ts — 标签 API 模块
// ============================================================
// 封装标签相关的所有接口调用，包括标签 CRUD、文件/文件夹打标签、
// 按标签查询文件/文件夹等。
//
// 后端统一返回格式：{ code: 200, message: null, data: ... }
// 所有 API 函数返回完整响应类型，调用方需自行校验 code 并解包 data。
//
// 与后端 TagController 对应：
//   POST   /business/tags                    — 创建标签
//   GET    /business/tags                    — 获取用户所有标签
//   PUT    /business/tags/{tag_id}           — 更新标签
//   DELETE /business/tags/{tag_id}           — 删除标签
//   POST   /business/tags/files             — 为文件/文件夹打标签
//   DELETE /business/tags/files             — 移除标签
//   GET    /business/tags/files/{target_id} — 获取文件/文件夹的标签
//   GET    /business/tags/{tag_id}/files    — 按标签查文件
//   GET    /business/tags/{tag_id}/folders  — 按标签查文件夹
// ============================================================

import { get, post, put, del } from '@/utils/request'

// ============================================================
// 类型定义
// ============================================================

/** 标签 VO */
export interface TagVO {
  tag_id: number
  tag_name: string
  tag_color: string
  file_count: number
  folder_count: number
  tag_created_at: string
}

/** 带标签的文件/文件夹 VO */
export interface TaggedFileVO {
  target_id: string
  target_type: 'file' | 'folder'
  target_name: string
  target_size: number
  file_type: string | null
  tagged_at: string
  tags: TagVO[]
}

/** 打标签请求 */
export interface FileTagRequest {
  tag_ids: number[]
  target_type: 'file' | 'folder'
  target_id: string
}

/** 后端统一响应包装 */
export interface ApiResponse<T> {
  code: number
  message: string | null
  data: T
}

// ============================================================
// 标签 CRUD
// ============================================================

/**
 * 创建标签
 * 后端返回：{ code: 200, message: null, data: TagVO }
 */
export function createTagApi(tag_name: string, tag_color: string = '#3B82F6'): Promise<ApiResponse<TagVO>> {
  return post<ApiResponse<TagVO>>('business/tags', { tag_name, tag_color })
}

/**
 * 获取用户所有标签
 * 后端返回：{ code: 200, message: null, data: TagVO[] }
 */
export function getUserTagsApi(): Promise<ApiResponse<TagVO[]>> {
  return get<ApiResponse<TagVO[]>>('business/tags')
}

/**
 * 更新标签
 * 后端返回：{ code: 200, message: null, data: TagVO }
 */
export function updateTagApi(tag_id: number, tag_name: string, tag_color: string): Promise<ApiResponse<TagVO>> {
  return put<ApiResponse<TagVO>>(`business/tags/${tag_id}`, { tag_name, tag_color })
}

/**
 * 删除标签
 * 后端返回：{ code: 200, message: null, data: null }
 */
export function deleteTagApi(tag_id: number): Promise<ApiResponse<null>> {
  return del<ApiResponse<null>>(`business/tags/${tag_id}`)
}

// ============================================================
// 文件标签关联
// ============================================================

/**
 * 为文件/文件夹打标签（批量）
 * 后端返回：{ code: 200, message: null, data: null }
 */
export function tagFileApi(data: FileTagRequest): Promise<ApiResponse<null>> {
  return post<ApiResponse<null>>('business/tags/files', data)
}

/**
 * 移除文件/文件夹的指定标签
 * 后端返回：{ code: 200, message: null, data: null }
 */
export function untagFileApi(tag_id: number, target_type: 'file' | 'folder', target_id: string): Promise<ApiResponse<null>> {
  return del<ApiResponse<null>>('business/tags/files', { tag_id, target_type, target_id })
}

/**
 * 获取文件/文件夹的所有标签
 * 后端返回：{ code: 200, message: null, data: TagVO[] }
 */
export function getFileTagsApi(target_id: string, target_type: 'file' | 'folder'): Promise<ApiResponse<TagVO[]>> {
  return get<ApiResponse<TagVO[]>>(`business/tags/files/${target_id}`, { target_type } as any)
}

/** 批量查询当前目录中文件与文件夹的标签，避免 N+1 请求。 */
export function getTagsBatchApi(file_ids: string[], folder_ids: string[]): Promise<ApiResponse<Record<string, TagVO[]>>> {
  return post<ApiResponse<Record<string, TagVO[]>>>('business/tags/files/batch', { file_ids, folder_ids })
}

// ============================================================
// 按标签查询
// ============================================================

/**
 * 按标签获取文件列表
 * 后端返回：{ code: 200, message: null, data: TaggedFileVO[] }
 */
export function getFilesByTagApi(tag_id: number, page: number = 1, pageSize: number = 50): Promise<ApiResponse<TaggedFileVO[]>> {
  return get<ApiResponse<TaggedFileVO[]>>(`business/tags/${tag_id}/files`, { page, pageSize } as any)
}

/**
 * 按标签获取文件夹列表
 * 后端返回：{ code: 200, message: null, data: TaggedFileVO[] }
 */
export function getFoldersByTagApi(tag_id: number, page: number = 1, pageSize: number = 50): Promise<ApiResponse<TaggedFileVO[]>> {
  return get<ApiResponse<TaggedFileVO[]>>(`business/tags/${tag_id}/folders`, { page, pageSize } as any)
}
