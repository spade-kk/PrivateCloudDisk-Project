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
// 路径查询与解析（混合模型）
// ============================================================

/**
 * 路径 → node_id 转换接口。
 *
 * 将面包屑路径（绝对路径或 node_id+相对路径）解析为对应的 node_id。
 * 支持两种模式：
 *   - absolute_path：绝对路径（如 "/my_disk/folder1/sub"）
 *   - node_id + relative_path：从指定节点出发的相对路径
 *
 * @param params.absolute_path - 绝对路径（可选）
 * @param params.relative_path - 相对路径（可选，需配合 node_id）
 * @param params.node_id - 父节点 ID（可选，需配合 relative_path）
 * @returns Promise<{ node_id: string }> 解析后的目标节点 ID
 */
export function resolvePathToNodeIdApi(params: {
  absolute_path?: string
  relative_path?: string
  node_id?: string
}): Promise<{ code: number; message: string | null; data: { node_id: string } }> {
  return get('business/nodes/resolve-path', { params })
}

/**
 * 按路径查询子节点（混合查询模型）。
 *
 * 返回 { node_id, children } 结构，其中 node_id 是解析后的目标节点 ID 供客户端保存。
 * 支持三种模式：
 *   - absolute_path：绝对路径
 *   - node_id + relative_path：从指定节点出发的相对路径
 *   - 仅 node_id：退化为普通 node_id 查询
 *
 * @param params.absolute_path - 绝对路径（可选）
 * @param params.relative_path - 相对路径（可选，需配合 node_id）
 * @param params.node_id - 父节点 ID（可选）
 * @returns Promise<{ node_id: string, children: Array }> 目标节点 ID 及子节点列表
 */
export function getChildrenByPathApi(params: {
  absolute_path?: string
  relative_path?: string
  node_id?: string
}): Promise<{ code: number; message: string | null; data: { node_id: string; children: any[] } }> {
  return get('business/nodes/children-by-path', { params })
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
 * 创建文件夹（支持相对路径 / 面包屑路径懒创建）
 *
 * 混合模型：
 *   - node_id + relative_path：在 node_id 下逐级创建 relative_path 路径，最终在此路径下创建 folder_name
 *   - breadcrumb_path：从根节点逐级创建 breadcrumb_path 路径，最终在此路径下创建 folder_name
 *   - 普通模式：仅 node_id + folder_name（原有行为）
 *
 * @param node_id - 父节点 ID
 * @param folder_name - 新文件夹名称
 * @param relativePath - 可选，相对路径（如 "subfolder1/subfolder2"）
 * @param breadcrumbPath - 可选，面包屑路径（如 "/root/folder1"）
 */
export function createFolderWithPathApi(
  node_id: string,
  folder_name: string,
  relative_path?: string,
  breadcrumb_path?: string,
): Promise<any> {
  const data: Record<string, any> = { node_id, folder_name }
  if (relative_path) data.relative_path = relative_path
  if (breadcrumb_path) data.breadcrumb_path = breadcrumb_path
  return post('business/nodes/', data)
}

/**
 * 懒上传会话创建 — 混合模型
 *
 * 上传即创建路径：自动创建不存在的目录，然后创建上传会话。
 * 支持两种模式：
 *   - parent_node_id + relative_path：在指定节点下按相对路径懒创建目录
 *   - breadcrumb_path：从根节点按面包屑路径懒创建目录
 *
 * 返回 { uploads_id, node_id }，node_id 是最终文件所在的目录节点 ID。
 *
 * @param total_chunks - 总分片数
 * @param file_size - 文件大小
 * @param file_checksum - 文件 SHA-256 校验和
 * @param chunks_max_size - 分片最大大小
 * @param file_type - 文件 MIME 类型
 * @param file_name - 文件名
 * @param parent_node_id - 父节点 ID（可选）
 * @param relative_path - 相对路径（可选）
 * @param breadcrumb_path - 面包屑路径（可选）
 */
export function createLazyUploadSessionApi(
  total_chunks: number,
  file_size: number,
  file_checksum: string,
  chunks_max_size: number,
  file_type: string,
  file_name: string,
  parent_node_id?: string,
  relative_path?: string,
  breadcrumb_path?: string,
): Promise<any> {
  const data: Record<string, any> = {
    total_chunks,
    file_size,
    file_checksum,
    chunks_max_size,
    file_type,
    file_name,
  }
  if (parent_node_id) data.parent_node_id = parent_node_id
  if (relative_path) data.relative_path = relative_path
  if (breadcrumb_path) data.breadcrumb_path = breadcrumb_path
  return post('business/nodes/uploads/lazy', data)
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
