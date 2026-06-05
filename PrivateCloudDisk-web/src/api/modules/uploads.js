import { get, post } from '@/utils/request';

/**
 * 创建上传会话
 * @param {int} total_chunks -总分块数
 * @param {int} file_size -文件大小
 * @param {String} file_checksum -文件校验和
 * @param {int} chunks_max_size -分块最大大小
 * @param {String} file_type -文件类型
 * @param {String} file_name -文件名称
 * @param {String} node_id -目录节点ID
 * @returns {Promise}
 */
export function createUploadsSessionApi(total_chunks, file_size, file_checksum, chunks_max_size, file_type, file_name, node_id) {
    let data = {
        total_chunks: total_chunks,
        file_size: file_size,
        file_checksum: file_checksum,
        chunks_max_size: chunks_max_size,
        file_type: file_type,
        file_name: file_name,
        node_id: node_id
    };
  return post('business/uploads/', data);
}

/**
 * 上传文件切片
 * @param {String} uploads_id -上传ID
 * @param {int} chunk_index -上传文件的切片索引
 * @param {File} upload_file_chunk -上传文件切片
 * @param {AbortSignal} signal -可选的AbortSignal对象，用于取消请求
 * @returns {Promise}
 */
export function uploadFileChunkApi(uploads_id, chunk_index, upload_file_chunk, signal) {
  let data = {
    chunk_index: chunk_index,
    file: upload_file_chunk
  };
  return post(`files/uploads/${uploads_id}/chunks`, data, { 
    signal: signal, 
    headers: { 'Content-Type': 'multipart/form-data' } 
  });
}
/**
 * 完成上传会话，通知服务器合并文件切片
 * @param {*} uploads_id -上传ID
 * @returns {Promise}
 */
export function completeUploadSessionApi(uploads_id) {
  return post(`files/uploads/${uploads_id}/merge`);
}