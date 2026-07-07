/**
 * api/search.js - 搜索 API
 *
 * 后端: FileController -> /business/files
 */
import { get } from '@/utils/request'

const BASE = '/business/files'

/**
 * 搜索文件
 * @param {Object} params { keyword, page, pageSize }
 */
export function searchFiles(params = {}) {
  return get(`${BASE}/advanced-search`, params)
}