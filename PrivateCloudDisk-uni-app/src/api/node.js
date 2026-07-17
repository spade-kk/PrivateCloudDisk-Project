/**
 * api/node.js - 目录/节点 API
 *
 * 后端: NodeController -> /business/nodes
 *
 * 对标 Vue3 Web 项目 api/modules/nodes.ts，
 * 确保所有端点路径、请求参数、响应格式完全一致。
 */
import { get, post, patch, del } from '@/utils/request'

const BASE = '/business/nodes'

// ============================================================
// 节点查询
// ============================================================

/** 获取用户根目录 */
export function getRootNode() {
  return get(`${BASE}/root`)
}

/** 获取节点详情 */
export function getNodeDetail(nodeId) {
  return get(`${BASE}/${nodeId}`)
}

/**
 * 获取子节点列表（全部，不分页）
 *
 * 对标 Vue3: getNodeChildrenApi(node_id)
 * 用于文件列表全量渲染场景。
 *
 * @param {string} nodeId 父节点 ID
 * @returns Promise<Array> 子节点列表
 */
export function getChildren(nodeId) {
  return get(`${BASE}/${nodeId}/children`)
}

/**
 * 分页查询子节点 (支持搜索/过滤/排序)
 *
 * 保留用于需要分页的场景（如移动端触底加载更多）。
 *
 * @param {string} nodeId   父节点 ID
 * @param {Object} params   { keyword, fileType, sortBy, sortOrder, page, pageSize }
 */
export function getChildrenPaged(nodeId, params = {}) {
  return get(`${BASE}/${nodeId}/children/paged`, params)
}

// ============================================================
// 路径查询与解析（混合模型）
// 对标 Vue3: resolvePathToNodeIdApi / getChildrenByPathApi
// ============================================================

/**
 * 路径 → node_id 转换接口
 *
 * 将面包屑路径（绝对路径或 node_id+相对路径）解析为对应的 node_id。
 *
 * @param {Object} params { absolute_path?, relative_path?, node_id? }
 * @returns Promise<{ node_id: string }>
 */
export function resolvePathToNodeId(params = {}) {
  return get(`${BASE}/resolve-path`, params)
}

/**
 * 按路径查询子节点（混合查询模型）
 *
 * 返回 { node_id, children } 结构，其中 node_id 是解析后的目标节点 ID。
 *
 * @param {Object} params { absolute_path?, relative_path?, node_id? }
 * @returns Promise<{ node_id: string, children: Array }>
 */
export function getChildrenByPath(params = {}) {
  return get(`${BASE}/children-by-path`, params)
}

// ============================================================
// 节点操作
// ============================================================

/**
 * 创建文件夹
 *
 * 对标 Vue3: createFolderApi(node_id, folder_name)
 *
 * @param {string} nodeId 父节点 ID
 * @param {string} folderName 新文件夹名称
 */
export function createFolder(nodeId, folderName) {
  return post(`${BASE}/`, { node_id: nodeId, folder_name: folderName })
}

/**
 * 创建文件夹（支持相对路径 / 面包屑路径懒创建）
 *
 * 对标 Vue3: createFolderWithPathApi
 *
 * @param {string} nodeId 父节点 ID
 * @param {string} folderName 新文件夹名称
 * @param {string} [relativePath] 可选，相对路径（如 "subfolder1/subfolder2"）
 * @param {string} [breadcrumbPath] 可选，面包屑路径（如 "/root/folder1"）
 */
export function createFolderWithPath(nodeId, folderName, relativePath, breadcrumbPath) {
  const data = { node_id: nodeId, folder_name: folderName }
  if (relativePath) data.relative_path = relativePath
  if (breadcrumbPath) data.breadcrumb_path = breadcrumbPath
  return post(`${BASE}/`, data)
}

/**
 * 删除节点（移入回收站）
 *
 * 对标 Vue3: deleteNodeApi(node_id)
 */
export function deleteNode(nodeId) {
  return del(`${BASE}/${nodeId}`)
}

/**
 * 移动节点到指定目标目录
 *
 * 对标 Vue3: moveNodeApi(node_id, target_node_id)
 *
 * @param {string} nodeId 要移动的节点 ID
 * @param {string} targetNodeId 目标父节点 ID
 */
export function moveNode(nodeId, targetNodeId) {
  return patch(`${BASE}/${nodeId}/position`, { target_node_id: targetNodeId })
}

/**
 * 重命名节点
 *
 * 对标 Vue3: renameNodeApi(node_id, new_name)
 *
 * @param {string} nodeId 要重命名的节点 ID
 * @param {string} newName 新名称
 */
export function renameNode(nodeId, newName) {
  return patch(`${BASE}/${nodeId}/name`, { new_name: newName })
}

// ============================================================
// 文件信息查询（通过节点路径）
// 对标 Vue3: getFileInfoByPathAndNameApi
// ============================================================

/**
 * 根据路径和文件名获取文件信息
 *
 * @param {string} nodeId 父节点 ID（目录）
 * @param {string} name 文件名
 */
export function getFileInfoByPathAndName(nodeId, name) {
  return get(`${BASE}/${nodeId}/files/${name}`)
}

// ============================================================
// 懒上传会话创建（混合模型）
// 对标 Vue3: createLazyUploadSessionApi
// ============================================================

/**
 * 懒上传会话创建 — 上传即创建路径
 *
 * 自动创建不存在的目录，然后创建上传会话。
 *
 * @param {Object} params
 * @param {number} params.total_chunks 总分片数
 * @param {number} params.file_size 文件大小
 * @param {string} params.file_checksum 文件 SHA-256 校验和
 * @param {number} params.chunks_max_size 分片最大大小
 * @param {string} params.file_type 文件 MIME 类型
 * @param {string} params.file_name 文件名
 * @param {string} [params.parent_node_id] 父节点 ID
 * @param {string} [params.relative_path] 相对路径
 * @param {string} [params.breadcrumb_path] 面包屑路径
 */
export function createLazyUploadSession(params = {}) {
  return post(`${BASE}/uploads/lazy`, params)
}

/** 获取文件详情 (别名，兼容页面调用) */
export function getFileDetail(fileId) {
  return get(`${BASE}/${fileId}`)
}