import {post, get, del, patch} from '@/utils/request'

/**
 * 
 * @returns 
 */
export function getMyUserRootNodeApi() {
    return get('business/nodes/root');
}
/**
 * 
 * @param {*} node_id 
 * @returns 
 */
export function getNodeInfoApi(node_id) {
    return get(`business/nodes/${node_id}`);
}
/**
 * 
 * @param {*} node_id 
 * @param {*} folder_name 
 * @returns 
 */
export function createFolderApi(node_id, folder_name) {
    let data = {
        node_id: node_id,
        folder_name: folder_name
    };
    return post('business/nodes/', data);
}
/**
 * 
 * @param {*} node_id 
 * @returns 
 */
export function deleteNodeApi(node_id) {
    return del(`business/nodes/${node_id}`);
}
/**
 * 
 * @param {*} node_id 
 * @param {*} new_name 
 * @returns 
 */
export function renameNodeApi(node_id, new_name) {
    let data = {
        new_name: new_name
    };
    return patch(`business/nodes/${node_id}/name`, data);
}
/**
 * 
 * @param {*} node_id 
 * @param {*} target_node_id 
 * @returns 
 */
export function moveNodeApi(node_id, target_node_id) {
    let data = {
        target_node_id: target_node_id
    };
    return patch(`business/nodes/${node_id}/position`, data);
}
/**
 * 
 * @param {*} node_id 
 * @returns 
 */
export function getNodeChildrenApi(node_id) {
    return get(`business/nodes/${node_id}/children`);
}