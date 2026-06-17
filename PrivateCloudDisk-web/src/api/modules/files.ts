// ============================================================
// files.ts — 文件管理 API 模块
// ============================================================
// 封装文件信息查询、移动、重命名、删除等文件级别的操作接口。
// 与 nodes.ts 配合使用：节点管理目录结构，files 管理目录下的具体文件。
// ============================================================

import { del, get, post, patch } from '@/utils/request'

// ============================================================
// 文件信息查询
// ============================================================

/**
 * 获取文件信息
 *
 * 根据文件 ID 获取文件元数据：名称、大小、类型、创建时间、修改时间等。
 *
 * @param file_id - 文件唯一标识符
 * @returns Promise<{ file_id, file_name, file_size, file_type, created_at, ... }> 文件详情
 */
export function getFileInfoApi(file_id: string): Promise<any> {
  return get(`business/files/${file_id}`)
}

/**
 * 根据路径和文件名获取文件信息
 *
 * 在指定节点目录下按文件名查找文件，用于文件冲突检测和路径解析。
 *
 * @param node_id - 父节点 ID（目录）
 * @param name - 文件名
 * @returns Promise<{ file_id, file_name, ... }> 文件信息
 */
export function getFileInfoByPathAndNameApi(node_id: string, name: string): Promise<any> {
  return get(`business/nodes/${node_id}/files/${name}`)
}

// ============================================================
// 文件操作
// ============================================================

/**
 * 移动文件到指定目标目录
 *
 * 移动后文件路径自动更新，移动操作会保留文件的所有元数据。
 * 目标目录不能已有同名文件，否则返回冲突错误。
 *
 * @param file_id - 要移动的文件 ID
 * @param target_node_id - 目标目录节点 ID
 * @returns Promise 移动结果
 */
export function moveFileApi(file_id: string, target_node_id: string): Promise<any> {
  return patch(`business/files/${file_id}/position`, {
    target_node_id,
  })
}

/**
 * 重命名文件
 *
 * 新名称在同一目录下必须唯一。文件扩展名通常保持不变。
 *
 * @param file_id - 文件 ID
 * @param new_name - 新文件名（含扩展名）
 * @returns Promise 重命名结果
 */
export function renameFileApi(file_id: string, new_name: string): Promise<any> {
  return patch(`business/files/${file_id}/name`, {
    new_name,
  })
}

/**
 * 删除文件
 *
 * 删除操作可能直接删除或移到回收站（取决于后端配置）。
 * 删除文件不可恢复时，前端应弹出二次确认对话框。
 *
 * @param file_id - 文件 ID
 * @returns Promise 删除结果
 */
export function deleteFileApi(file_id: string): Promise<any> {
  return del(`business/files/${file_id}/`)
}