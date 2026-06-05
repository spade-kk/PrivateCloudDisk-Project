import {get, post, del} from '@/utils/request'

/**
 * 
 * @param {*} node_id 
 * @param {*} file_name 
 * @param {*} operation_type
 * @returns 
 */
export function createOperationTokenApi(node_id, file_name, operation_type) {
    let data = {
        node_id: node_id,
        file_name: file_name,
        operation_type: operation_type
    };
    return post('files/operation-tokens', data);
}
/**
 * 
 * @param {*} operation_token 
 * @returns 
 */
export function cancelOperationApi(operation_token) {
    let data = {
        operation_token: operation_token
    };
    return del('files/operation-tokens/', data);
}
/**
 * 
 * @param {*} node_id 
 * @param {*} file_name 
 * @param {*} operation_token 
 * @param {*} onProgress
 * @returns 
 */
export function getFileContentApi(node_id, file_name, operation_token, onProgress) {
    return get(`files/nodes/${node_id}/files/${file_name}/content`, {}, {
        responseType: 'blob',
        headers: {
            'X-Operation-Token': operation_token
        },
        onDownloadProgress: onProgress
    });
}
/**
 * 
 * @param {*} node_id 
 * @param {*} file_name 
 * @param {*} operation_token 
 * @param {*} start 
 * @param {*} end 
 * @returns 
 */
export function getFileContentChunkApi(node_id, file_name, operation_token, start, end) {
    return get(`files/nodes/${node_id}/files/${file_name}/content`, {}, {
        responseType: 'blob',
        headers: {
            'X-Operation-Token': operation_token,
            'Range': `bytes=${start}-${end}`
        }
    });
}