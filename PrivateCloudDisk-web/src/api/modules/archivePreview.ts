// ============================================================
// archivePreview.ts — 压缩包预览 API 模块
// ============================================================
// 对接后端 FastAPI Storage 服务的压缩包目录结构解析流水线。
//
// 后端处理流水线（自动触发，无需前端干预）：
//   文件上传成功 → 存储服务自动检测压缩包类型 → 触发解析流水线
//   → 解析目录结构 → 生成 JSON 目录树 → 更新预览状态
//
// 前端只需要：
//   1. 调用 getArchivePreviewInfoApi 查询预览状态
//   2. 根据返回的 status 字段展示不同的 UI 状态
//   3. completed 状态时调用 getArchiveTreeApi 获取目录树数据
//
// 参考：Office 文档预览模块（officePreview.ts）的对接模式
// ============================================================

import { get } from '@/utils/request'

// ---- 类型定义 ----

/** 压缩包预览状态 */
export type ArchivePreviewStatus =
  | 'completed'   // 目录树解析已完成
  | 'processing'  // 后端流水线解析中
  | 'failed'      // 解析失败（文件损坏/格式不支持等）
  | 'not_found'   // 文件不存在或未找到预览资源
  | 'pending'     // 等待处理（文件已上传但流水线尚未启动）

/** 压缩包预览信息（getArchivePreviewInfoApi 返回的 data 字段） */
export interface ArchivePreviewInfo {
  /** 文件 ID */
  fileId: string
  /** 预览状态 */
  status: ArchivePreviewStatus
  /** 后端返回的友好提示信息 */
  message?: string
  /** 解析进度（0-100，仅在 processing 状态时有效） */
  progress?: number
  /** 错误详情（仅在 failed 状态时有效） */
  errorDetail?: string
  /** 解析完成时间 */
  completedAt?: string
}

/** 目录树节点 */
export interface ArchiveTreeNode {
  /** 文件/目录名称 */
  name: string
  /** 节点类型：directory 或 file */
  type: 'directory' | 'file'
  /** 子节点（仅 directory 类型有） */
  children?: ArchiveTreeNode[] | null
  /** 文件大小（字节，仅 file 类型有） */
  size?: number
  /** 修改时间（ISO 8601 格式，仅 file 类型有） */
  modified?: string
}

/** 目录树数据（getArchiveTreeApi 返回的 data 字段） */
export interface ArchiveTreeData {
  /** 文件 ID */
  fileId: string
  /** 文件名 */
  fileName: string
  /** 文件总数 */
  totalFiles: number
  /** 目录总数 */
  totalDirs: number
  /** 解压后总大小（字节） */
  totalSize: number
  /** 解析时间（ISO 8601 格式） */
  parsedAt: string
  /** 目录树根节点 */
  tree: ArchiveTreeNode
}

// ---- API 函数 ----

/**
 * 获取压缩包预览状态
 *
 * 查询后端压缩包解析流水线的处理状态。
 * 状态包括：pending（等待处理）、processing（处理中）、
 * completed（已完成）、failed（失败）、not_found（文件不存在）。
 *
 * @param fileId - 文件 ID
 * @returns Promise<ArchivePreviewInfo> 预览状态信息
 */
export function getArchivePreviewInfoApi(fileId: string): Promise<ArchivePreviewInfo> {
  return get(`business/files/${fileId}/archive-preview-status`)
}

/**
 * 获取压缩包目录树数据
 *
 * 仅在预览状态为 completed 时调用。
 * 返回完整的目录树 JSON 数据，包含文件夹层级结构、
 * 文件名、大小、修改时间等元数据。
 *
 * @param fileId - 文件 ID
 * @returns Promise<ArchiveTreeData> 目录树数据
 */
export function getArchiveTreeApi(fileId: string): Promise<ArchiveTreeData> {
  return get(`business/files/${fileId}/archive-tree`)
}