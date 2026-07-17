/**
 * api/file.js - 文件操作 API
 *
 * 后端: FileController -> /business/files
 *
 * 对标 Vue3 Web 项目 api/modules/files.ts，
 * 确保所有端点路径、请求参数、响应格式完全一致。
 *
 * 注意：节点级别的操作（创建文件夹、移动节点、删除节点）
 * 请使用 api/node.js，本模块仅处理文件级别的操作。
 */
import { get, patch, del } from '@/utils/request'

const BASE = '/business/files'

// ============================================================
// 文件信息查询
// ============================================================

/** 获取文件详情 */
export function getFileDetail(fileId) {
  return get(`${BASE}/${fileId}`)
}

/**
 * 根据路径和文件名获取文件信息
 *
 * 对标 Vue3: getFileInfoByPathAndNameApi(node_id, name)
 *
 * @param {string} nodeId 父节点 ID（目录）
 * @param {string} name 文件名
 */
export function getFileInfoByPathAndName(nodeId, name) {
  return get(`/business/nodes/${nodeId}/files/${name}`)
}

// ============================================================
// 文件操作
// ============================================================

/**
 * 重命名文件
 *
 * 对标 Vue3: renameFileApi(file_id, new_name)
 *
 * @param {string} fileId 文件 ID
 * @param {string} newName 新文件名（含扩展名）
 */
export function renameFile(fileId, newName) {
  return patch(`${BASE}/${fileId}/name`, { new_name: newName })
}

/**
 * 移动文件到指定目标目录
 *
 * 对标 Vue3: moveFileApi(file_id, target_node_id)
 *
 * @param {string} fileId 要移动的文件 ID
 * @param {string} targetNodeId 目标目录节点 ID
 */
export function moveFile(fileId, targetNodeId) {
  return patch(`${BASE}/${fileId}/position`, { target_node_id: targetNodeId })
}

/**
 * 删除文件
 *
 * 对标 Vue3: deleteFileApi(file_id)
 *
 * @param {string} fileId 文件 ID
 */
export function deleteFile(fileId) {
  return del(`${BASE}/${fileId}/`)
}

// ============================================================
// 高级搜索
// ============================================================

/**
 * 高级搜索
 *
 * 对标 Vue3: advancedFileSearchApi(options)
 *
 * @param {Object} params { keyword, page, size, status, sortField, asc, searchAfter, highlightFields, filters }
 */
export function advancedSearch(params = {}) {
  return get(`${BASE}/advanced-search`, params)
}