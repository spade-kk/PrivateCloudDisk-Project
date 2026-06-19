/**
 * api/node.js - 目录/节点 API
 *
 * 后端: NodeController -> /business/nodes
 */
import { get, post, patch, del } from '@/utils/request'

const BASE = '/business/nodes'

/** 获取用户根目录 */
export function getRootNode() {
  return get(`${BASE}/root`)
}

/** 获取节点详情 */
export function getNodeDetail(nodeId) {
  return get(`${BASE}/${nodeId}`)
}

/** 获取子节点列表 (全部) */
export function getChildren(nodeId) {
  return get(`${BASE}/${nodeId}/children`)
}

/**
 * 分页查询子节点 (支持搜索/过滤/排序)
 * @param {string} nodeId   父节点 ID
 * @param {Object} params   { keyword, fileType, sortBy, sortOrder, page, pageSize }
 */
export function getChildrenPaged(nodeId, params = {}) {
  return get(`${BASE}/${nodeId}/children/paged`, params)
}

/** 创建文件夹 */
export function createFolder(data) {
  return post(`${BASE}/`, data)
}

/** 删除节点 */
export function deleteNode(nodeId) {
  return del(`${BASE}/${nodeId}`)
}

/** 移动节点 */
export function moveNode(nodeId, data) {
  return patch(`${BASE}/${nodeId}/position`, data)
}

/** 重命名节点 */
export function renameNode(nodeId, data) {
  return patch(`${BASE}/${nodeId}/name`, data)
}