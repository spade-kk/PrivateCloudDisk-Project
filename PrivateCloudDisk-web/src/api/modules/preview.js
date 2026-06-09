import { get, del } from '@/utils/request'

/**
 * 获取文件预览Token
 * 用于获取文件预览所需的临时访问令牌
 * @param {string} file_id - 文件ID
 * @returns {Promise<Object>} 包含预览URL和Token
 */
export function getFilePreviewTokenApi(file_id) {
  return get(`files/files/${file_id}/preview-token`);
}

/**
 * 获取文件预览URL
 * 根据文件类型和预览模式获取预览URL
 * @param {string} file_id - 文件ID
 * @param {Object} options - 预览选项
 * @param {string} options.mode - 预览模式: 'inline', 'attachment'
 * @param {string} options.thumbnail - 是否为缩略图: 'true', 'false'
 * @returns {Promise<Object>} 预览URL和相关信息
 */
export function getFilePreviewUrlApi(file_id, options = {}) {
  const params = new URLSearchParams(options).toString()
  return get(`files/files/${file_id}/preview-url${params ? '?' + params : ''}`);
}

/**
 * 获取文件元数据
 * 获取文件详细信息用于预览
 * @param {string} file_id - 文件ID
 * @returns {Promise<Object>} 文件元数据
 */
export function getFileMetadataApi(file_id) {
  return get(`files/files/${file_id}/metadata`);
}

/**
 * 生成文件缩略图
 * 请求后端生成指定尺寸的缩略图
 * @param {string} file_id - 文件ID
 * @param {Object} options - 缩略图选项
 * @param {number} options.width - 缩略图宽度
 * @param {number} options.height - 缩略图高度
 * @param {string} options.type - 缩略图类型: 'cover', 'contain'
 * @returns {Promise<Object>} 缩略图URL
 */
export function generateThumbnailApi(file_id, options = {}) {
  return get(`files/files/${file_id}/thumbnail`, options);
}

/**
 * 清除文件预览缓存
 * 当文件更新后清除预览缓存
 * @param {string} file_id - 文件ID
 * @returns {Promise<Object>} 操作结果
 */
export function clearPreviewCacheApi(file_id) {
  return del(`files/files/${file_id}/preview-cache`);
}

/**
 * 获取支持的预览格式列表
 * 获取当前系统支持预览的文件格式
 * @returns {Promise<Object>} 支持的文件格式列表
 */
export function getSupportedFormatsApi() {
  return get('files/supported-formats');
}

/**
 * 预览文件内容（文本/代码）
 * 直接获取文本或代码文件内容用于预览
 * @param {string} file_id - 文件ID
 * @param {Object} options - 获取选项
 * @param {number} options.maxSize - 最大读取字节数（默认1MB）
 * @param {string} options.encoding - 文本编码（默认utf-8）
 * @returns {Promise<Object>} 文件内容
 */
export function getFileContentApi(file_id, options = {}) {
  return get(`files/files/${file_id}/content`, options, {
    responseType: 'text'
  });
}

/**
 * 获取Office文档转换状态
 * 检查Office文档是否已转换为可预览格式
 * @param {string} file_id - 文件ID
 * @returns {Promise<Object>} 转换状态
 */
export function getDocumentConversionStatusApi(file_id) {
  return get(`files/files/${file_id}/conversion-status`);
}

/**
 * 请求Office文档转换
 * 触发后端将Office文档转换为可预览的PDF/图片格式
 * @param {string} file_id - 文件ID
 * @param {Object} options - 转换选项
 * @param {string} options.format - 目标格式: 'pdf', 'images'
 * @param {number} options.dpi - 转换DPI（默认150）
 * @returns {Promise<Object>} 转换任务信息
 */
export function requestDocumentConversionApi(file_id, options = {}) {
  return post(`files/files/${file_id}/convert`, options);
}

/**
 * 记录文件预览历史
 * 记录用户的预览行为用于分析
 * @param {string} file_id - 文件ID
 * @param {Object} metadata - 预览元数据
 * @returns {Promise<Object>} 操作结果
 */
export function recordPreviewHistoryApi(file_id, metadata = {}) {
  return post(`files/files/${file_id}/preview-history`, metadata);
}
