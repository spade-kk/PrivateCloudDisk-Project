// ============================================================
// officePreview.ts — Office 文档预览 API 模块
// ============================================================
// 对接后端 FastAPI Storage 服务的文档自动转换流水线。
//
// 后端处理流水线（自动触发，无需前端干预）：
//   文件上传成功 → 存储服务自动检测 Office 类型 → 触发转换流水线
//   → LibreOffice 转换为 PDF → 生成预览资源 → 更新文档预览状态
//
// 流水线阶段（由后端 managed-processor 管理）：
//   1. validate_file     — 验证文件完整性
//   2. generate_preview  — 生成预览资源（PDF + 缩略图）
//   3. extract_metadata  — 提取文档元数据（页数、作者、大纲等）
//   4. cache_result      — 缓存预览结果
//
// 前端只需要：
//   1. 调用 getDocumentPreviewInfoApi 查询预览状态
//   2. 根据返回的 status 字段展示不同的 UI 状态
//
// 参考：视频流媒体模块（video.ts）的对接模式
// ============================================================

import { get } from '@/utils/request'

// ---- 类型定义 ----

/** 文档预览状态 */
export type DocumentPreviewStatus =
  | 'completed'   // 预览已就绪
  | 'processing'  // 后端流水线处理中
  | 'failed'      // 转换失败（文件损坏/格式不支持等）
  | 'not_found'   // 文件不存在或未找到预览资源
  | 'pending'     // 等待处理（文件已上传但流水线尚未启动）

/** 文档预览信息（getDocumentPreviewInfoApi 返回的 data 字段） */
export interface DocumentPreviewInfo {
  /** 文件 ID */
  fileId: string
  /** 预览状态 */
  status: DocumentPreviewStatus
  /** 预览 URL（status 为 completed 时可用） */
  previewUrl?: string
  /** 缩略图 URL */
  thumbnailUrl?: string
  /** 后端返回的友好提示信息 */
  message?: string
  /** 文档元数据 */
  metadata?: {
    /** 总页数/幻灯片数 */
    totalPages?: number
    /** 作者 */
    author?: string
    /** 文件大小（字节） */
    fileSize?: number
    /** 文档类型 */
    documentType?: 'word' | 'excel' | 'powerpoint' | 'pdf'
    // ---- Word 特有 ----
    /** 字数统计 */
    wordCount?: number
    /** 文档大纲 */
    outline?: DocumentOutlineItem[]
    // ---- Excel 特有 ----
    /** 工作表列表 */
    sheets?: SheetInfo[]
    // ---- PPT 特有 ----
    /** 幻灯片标题列表 */
    slideTitles?: string[]
  }
  /** 转换进度（0-100，仅在 processing 状态时有效） */
  progress?: number
  /** 转换完成时间 */
  completedAt?: string
  /** 错误详情（仅在 failed 状态时有效） */
  errorDetail?: string
}

/** 文档大纲节点 */
export interface DocumentOutlineItem {
  title: string
  level: number
  pageNumber: number
  children?: DocumentOutlineItem[]
}

/** 工作表信息 */
export interface SheetInfo {
  name: string
  index: number
  rowCount?: number
  columnCount?: number
}

// ---- API 函数 ----

/**
 * 获取文档预览信息
 *
 * 后端自动流水线处理 Office 文档，前端仅需查询预览状态。
 * 这是 Office 预览的唯一入口 API，无需前端触发转换或轮询。
 *
 * 后端端点：GET /api/v1/files/files/{fileId}/preview-info
 *
 * 返回数据示例：
 *   - 已完成: { status: "completed", previewUrl: "https://...", metadata: {...} }
 *   - 处理中: { status: "processing", progress: 45, message: "文档转换中..." }
 *   - 失败:   { status: "failed", errorDetail: "文件损坏", message: "..." }
 *   - 未找到: { status: "not_found", message: "预览资源不存在" }
 *
 * @param fileId - 文件 ID
 * @returns Promise<{ code: number, data: DocumentPreviewInfo, message?: string }>
 */
export function getDocumentPreviewInfoApi(
  fileId: string,
): Promise<{ code: number; data: DocumentPreviewInfo; message?: string }> {
  return get(`files/files/${fileId}/preview-info`)
}

/**
 * 获取文档缩略图 URL
 *
 * 后端端点：GET /api/v1/files/files/{fileId}/thumbnail?size=medium
 * 用于在预览页面加载前展示封面图。
 *
 * @param fileId - 文件 ID
 * @param size - 缩略图尺寸
 * @returns 完整的缩略图 URL 字符串
 */
export function getDocumentThumbnailUrl(
  fileId: string,
  size: 'small' | 'medium' | 'large' = 'medium',
): string {
  const baseUrl = import.meta.env.VITE_API_BASE_URL || '/api/v1'
  const params = new URLSearchParams({ size, type: 'document' })
  return `${baseUrl}/files/files/${encodeURIComponent(fileId)}/thumbnail?${params.toString()}`
}