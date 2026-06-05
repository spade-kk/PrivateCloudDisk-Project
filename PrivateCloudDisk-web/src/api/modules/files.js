import { get, post, patch } from '@/utils/request'

/**
 * 获取文件信息
 * @param {*} file_id 
 * @returns 
 */
export function getFileInfoApi(file_id) {
  return get(`business/files/${file_id}`);
}
/**
 * 获取文件信息 根据路径和文件名
 * @param {*} node_id 
 * @param {*} name 
 * @returns 
 */
export function getFileInfoByPathAndNameApi(node_id, name) {
    return get(`business/nodes/${node_id}/files/${name}`);
}
/**
 * 移动文件位置
 * @param {*} file_id 
 * @param {*} target_node_id 
 * @returns 
 */
export function moveFileApi(file_id, target_node_id) {
  return patch(`business/files/${file_id}/position`, {
    target_node_id: target_node_id
  });
}
/**
 * 重命名文件
 * @param {*} file_id 
 * @param {*} new_name 
 * @returns 
 */
export function renameFileApi(file_id, new_name) {
  return patch(`business/files/${file_id}/name`, {
    new_name: new_name
  });
}
/**
 * 删除文件
 * @param {*} file_id 
 * @returns 
 */
export function deleteFileApi(file_id) {
  return del(`business/files/${file_id}/`);
}