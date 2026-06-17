// ============================================================
// nodes.ts — 文件目录节点管理 API 模块
// ============================================================
// 封装目录树的 CRUD 操作：获取根节点、子节点列表、创建文件夹、
// 删除、重命名、移动节点等。节点是云盘文件系统的核心抽象。
// ============================================================

import { post, get, del, patch } from '@/utils/request'

// ============================================================
// 节点查询
// ============================================================

/**
 * 获取当前用户的根目录节点
 *
 * 根节点是用户文件系统的顶层目录，ID 由后端分配。
 * 所有文件和子目录都从此节点开始展开。
 *
 * @returns Promise<{ node_id: string, node_name: string, ... }> 根节点信息
 */
export function getMyUserRootNodeApi(): Promise<any> {
  return get('business/nodes/root')
}

/**
 * 获取指定节点的详细信息
 *
 * 返回节点元数据：名称、类型、大小、创建时间、修改时间等。
 *
 * @param node_id - 节点唯一标识符
 * @returns Promise<{ node_id, node_name, node_type, size, created_at, ... }> 节点详情
 */
export function getNodeInfoApi(node_id: string): Promise<any> {
  return get(`business/nodes/${node_id}`)
}

/**
 * 获取指定节点的子节点列表（文件和子目录）
 *
 * 用于目录展开、文件列表渲染等场景。
 * 返回直接子节点，不递归。
 *
 * @param node_id - 父节点 ID
 * @returns Promise<Array<{ node_id, node_name, node_type, ... }>> 子节点列表
 */
export function getNodeChildrenApi(node_id: string): Promise<any> {
  return get(`business/nodes/${node_id}/children`)
}

// ============================================================
// 节点操作
// ============================================================

/**
 * 在指定节点下创建新文件夹
 *
 * 文件夹名称在同一父目录下必须唯一，否则后端返回冲突错误。
 *
 * @param node_id - 父节点 ID（在该目录下创建）
 * @param folder_name - 新文件夹名称
 * @returns Promise<{ node_id: string }> 新创建的文件夹节点信息
 */
export function createFolderApi(node_id: string, folder_name: string): Promise<any> {
  const data = { node_id, folder_name }
  return post('business/nodes/', data)
}

/**
 * 删除指定节点（移到回收站或直接删除，取决于后端配置）
 *
 * 删除文件夹会递归删除其下所有内容，操作前应做二次确认。
 *
 * @param node_id - 要删除的节点 ID
 * @returns Promise 删除结果
 */
export function deleteNodeApi(node_id: string): Promise<any> {
  return del(`business/nodes/${node_id}`)
}

/**
 * 重命名指定节点
 *
 * 新名称在同一父目录下必须唯一。
 *
 * @param node_id - 要重命名的节点 ID
 * @param new_name - 新名称
 * @returns Promise 重命名结果
 */
export function renameNodeApi(node_id: string, new_name: string): Promise<any> {
  const data = { new_name }
  return patch(`business/nodes/${node_id}/name`, data)
}

/**
 * 移动节点到指定目标目录
 *
 * 支持跨目录移动，移动后节点路径自动更新。
 * 目标目录不能是源目录的子目录（防止循环引用）。
 *
 * @param node_id - 要移动的节点 ID
 * @param target_node_id - 目标父节点 ID
 * @returns Promise 移动结果
 */
export function moveNodeApi(node_id: string, target_node_id: string): Promise<any> {
  const data = { target_node_id }
  return patch(`business/nodes/${node_id}/position`, data)
}