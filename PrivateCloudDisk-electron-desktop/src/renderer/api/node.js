/**
 * api/node.js - 文件夹节点 API
 *
 * 后端: Spring Boot NodeController
 * 端点前缀: /business/nodes
 */
import { get, post, patch, del } from '@/utils/request'

/** 获取根目录节点 */
export function getRootNode() {
  return get('/nodes/root')
}

/** 分页获取子节点列表
 *  Query: keyword, fileType, sortBy, sortOrder, page, pageSize
 */
export function getChildNodesPaged(nodeId, params) {
  return get(`/nodes/${nodeId}/children/paged`, params)
}

/** 创建文件夹节点
 *  body: { folder_name: 'newFolder', node_id: 'parentNodeId' }
 */
export function createFolder(data) {
  return post('/nodes', data)
}

/** 移动节点
 *  body: { target_position: 'targetNodeId' }
 */
export function moveNode(nodeId, data) {
  return patch(`/nodes/${nodeId}/position`, data)
}

/** 重命名节点
 *  body: { new_node_name: 'newName' }
 */
export function renameNode(nodeId, data) {
  return patch(`/nodes/${nodeId}/name`, data)
}

/** 删除节点 (彻底删除文件夹) */
export function deleteNode(nodeId) {
  return del(`/nodes/${nodeId}`)
}

/** 递归获取文件夹下所有文件信息（用于文件夹下载）
 *  GET /business/nodes/{node_id}/files
 *  X-User-Id header
 *  返回: { code: 200, data: [{ fileId, fileName, fileSize, storagePath }] }
 */
export function getFolderFiles(nodeId) {
  return get(`/nodes/${nodeId}/files`)
}