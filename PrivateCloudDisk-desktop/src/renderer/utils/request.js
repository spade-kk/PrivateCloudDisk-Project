/**
 * utils/request.js - 企业级 HTTP 请求封装
 *
 * 核心能力:
 * - 自动注入 X-User-Id Header
 * - JWT Token 自动附加 (Bearer)
 * - 统一响应格式解析 { code, message, data }
 * - 401 自动跳转登录页
 * - 请求/响应拦截器
 * - 超时配置
 * - 文件上传原生进度支持
 */
import axios from 'axios'
import { PLATFORM_BASE_URL, FILE_BASE_URL } from './const'
import { getToken, getUserId, removeToken, removeUserId } from './storage'

// ==================== Axios 实例 ====================

/** 平台服务 (Spring Boot) */
const platformClient = axios.create({
  baseURL: PLATFORM_BASE_URL,
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' }
})

/** 文件服务 (FastAPI) */
const fileClient = axios.create({
  baseURL: FILE_BASE_URL,
  timeout: 60000,
  headers: { 'Content-Type': 'application/json' }
})

// ==================== 请求拦截器 ====================

const requestInterceptor = (config) => {
  const token = getToken()
  const userId = getUserId()

  if (token) {
    config.headers['Authorization'] = `Bearer ${token}`
  }
  if (userId) {
    config.headers['X-User-Id'] = userId
  }

  return config
}

platformClient.interceptors.request.use(requestInterceptor, (e) => Promise.reject(e))
fileClient.interceptors.request.use(requestInterceptor, (e) => Promise.reject(e))

// ==================== 响应拦截器 ====================

const responseInterceptor = (response) => {
  const { data, status } = response

  // HTTP 2xx 成功
  if (status >= 200 && status < 300) {
    // 标准 JSON 响应 { code, message, data }
    if (data && typeof data === 'object' && 'code' in data) {
      if (data.code === 200) {
        return data
      }
      // 业务错误
      const error = new Error(data.message || `请求失败 [${data.code}]`)
      error.code = data.code
      return Promise.reject(error)
    }
    // 非标准响应 (如文件下载)
    return data
  }

  return Promise.reject(new Error(`HTTP ${status}`))
}

const errorInterceptor = (error) => {
  if (error.response) {
    const { status } = error.response

    if (status === 401 || status === 403) {
      removeToken()
      removeUserId()
      // 跳转到登录页
      if (window.location.hash !== '#/login') {
        window.location.hash = '#/login'
      }
      return Promise.reject(new Error('登录已过期，请重新登录'))
    }

    if (status >= 500) {
      return Promise.reject(new Error('服务器内部错误，请稍后重试'))
    }
  }

  if (error.code === 'ECONNABORTED') {
    return Promise.reject(new Error('请求超时，请检查网络连接'))
  }

  if (error.message === 'Network Error') {
    return Promise.reject(new Error('网络连接失败，请检查网络'))
  }

  return Promise.reject(error)
}

platformClient.interceptors.response.use(responseInterceptor, errorInterceptor)
fileClient.interceptors.response.use(responseInterceptor, errorInterceptor)

// ==================== 导出 ====================

/**
 * 获取指定的 HTTP 客户端
 * @param {'platform'|'file'} service
 */
export function getClient(service = 'platform') {
  return service === 'file' ? fileClient : platformClient
}

/** 通用 GET 请求 */
export function get(url, params, service = 'platform') {
  return getClient(service).get(url, { params })
}

/** 通用 POST 请求 */
export function post(url, data, service = 'platform') {
  return getClient(service).post(url, data)
}

/** 通用 PUT 请求 */
export function put(url, data, service = 'platform') {
  return getClient(service).put(url, data)
}

/** 通用 PATCH 请求 */
export function patch(url, data, service = 'platform') {
  return getClient(service).patch(url, data)
}

/** 通用 DELETE 请求 */
export function del(url, service = 'platform') {
  return getClient(service).delete(url)
}

/** 文件上传 (带进度回调) */
export function uploadFile(url, formData, onProgress, service = 'file') {
  return getClient(service).post(url, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: (progressEvent) => {
      if (onProgress && progressEvent.total) {
        const percent = Math.round((progressEvent.loaded / progressEvent.total) * 100)
        onProgress(percent, progressEvent.loaded, progressEvent.total)
      }
    }
  })
}

/** 文件下载 (带进度回调) */
export function downloadFile(url, savePath, onProgress) {
  return getClient('file').get(url, {
    responseType: 'blob',
    onDownloadProgress: (progressEvent) => {
      if (onProgress && progressEvent.total) {
        const percent = Math.round((progressEvent.loaded / progressEvent.total) * 100)
        onProgress(percent, progressEvent.loaded, progressEvent.total)
      }
    }
  })
}