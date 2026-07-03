// ============================================================
// folderDownload.ts — 文件夹下载 API 模块
// ============================================================
// 封装文件夹下载流程：
//   1. 调用 /nodes/{node_id}/files 获取文件夹下所有文件（含相对路径）
//   2. 客户端按文件清单逐个创建下载令牌 → 下载文件
//   3. 客户端根据 relative_path 在本地重建文件夹结构
//
// 设计要点：
//   - 不改变现有单文件下载逻辑，完全复用
//   - 每个文件单独走 downloadGrant → getFileContent 流程
//   - 文件夹下载取消只停止后续下载，已下载文件保留
// ============================================================

import { get } from '@/utils/request'

// ============================================================
// 类型定义
// ============================================================

/** 文件夹内文件信息（含相对路径） */
export interface FolderFileInfo {
  fileId: string
  fileName: string
  fileSize: number
  storagePath: string
  nodeId: string
  relativePath: string
}

/** 文件夹下载清单 */
export interface FolderDownloadManifest {
  nodeId: string
  folderName: string
  files: FolderFileInfo[]
  totalSize: number
}

// ============================================================
// API 接口
// ============================================================

/**
 * 获取文件夹下所有文件清单（含相对路径，用于文件夹下载）
 */
export function getFolderFilesRecursiveApi(
  node_id: string,
): Promise<any> {
  return get(`business/nodes/${node_id}/files`)
}