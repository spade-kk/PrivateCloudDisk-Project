import {get, post, del} from '@/utils/request'

/**
 * 
 * @param {*} file_id  
 * @param {*} operation_type
 * @returns 
 */
export function createOperationTokenApi(file_id, operation_type) {
    let data = {
        file_id: file_id,
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
 * @param {*} file_id 
 * @param {*} operation_token 
 * @param {*} onProgress
 * @returns 
 */
export function getFileContentApi(file_id, operation_token, onProgress) {
    return get(`files/files/${file_id}/content`, {}, {
        responseType: 'blob',
        headers: {
            'X-Operation-Token': operation_token
        },
        onDownloadProgress: onProgress
    });
}
/**
 * 
 * @param {*} file_id 
 * @param {*} operation_token 
 * @param {*} start 
 * @param {*} end 
 * @returns 
 */
export function getFileContentChunkApi(file_id, operation_token, start, end) {
    return get(`files/files/${file_id}/content`, {}, {
        responseType: 'blob',
        headers: {
            'X-Operation-Token': operation_token,
            'Range': `bytes=${start}-${end}`
        }
    });
}