/**
 * utils/request.js - 企业级 HTTP 请求封装
 *
 * 核心能力:
 * - 自动注入 X-User-Id Header
 * - JWT Token 自动附加 (Bearer)
 * - 统一响应格式解析 { code, message, data }
 * - 401 自动跳转登录页
 * - 请求/响应拦截器
 * - 请求重试 & 超时配置
 */
import { PLATFORM_BASE_URL, FILE_BASE_URL } from './const'
import { getToken, getUserId } from './storage'

// ==================== 拦截器队列 ====================
const requestInterceptors = []
const responseInterceptors = []
const errorInterceptors = []

// ==================== 工具函数 ====================

/**
 * 将对象转为 URL query string
 */
function buildQuery(params) {
  if (!params) return ''
  const parts = []
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') {
      parts.push(`${encodeURIComponent(k)}=${encodeURIComponent(v)}`)
    }
  })
  return parts.length ? `?${parts.join('&')}` : ''
}

/**
 * 判断是否为完整 URL
 */
function isAbsoluteURL(url) {
  return /^(https?:)?\/\//i.test(url)
}

// ==================== 核心请求方法 ====================

/**
 * 发起 HTTP 请求
 *
 * @param {Object} options
 * @param {string} options.url      请求路径 (相对路径或绝对 URL)
 * @param {string} options.method   GET / POST / PUT / PATCH / DELETE
 * @param {Object} options.data     请求体 (POST/PUT/PATCH) 或 query params (GET/DELETE)
 * @param {Object} options.header   附加请求头
 * @param {'platform'|'file'} options.service 服务类型 (默认 platform)
 * @param {number} options.timeout  超时毫秒数 (默认 30000)
 * @param {boolean} options.skipAuth 跳过 Token 注入 (默认 false)
 * @returns {Promise<{code:number, message:string, data:*}>}
 */
export async function request(options = {}) {
  const {
    url,
    method = 'GET',
    data,
    header = {},
    service = 'platform',
    timeout = 30000,
    skipAuth = false
  } = options

  // 拼接完整 URL
  let fullURL = url
  if (!isAbsoluteURL(url)) {
    const baseURL = service === 'platform' ? PLATFORM_BASE_URL : FILE_BASE_URL
    fullURL = `${baseURL}${url.startsWith('/') ? '' : '/'}${url}`
  }

  // ============ 默认 Header ============
  const headers = {
    'Content-Type': 'application/json',
    ...header
  }

  // 自动注入认证信息
  if (!skipAuth) {
    const token = getToken()
    const userId = getUserId()
    if (token) {
      headers['Authorization'] = `Bearer ${token}`
    }
    if (userId) {
      headers['X-User-Id'] = userId
    }
  }

  // ============ 执行请求拦截器 ============
  let requestConfig = { url: fullURL, method, data, header: headers, timeout }
  for (const interceptor of requestInterceptors) {
    requestConfig = interceptor(requestConfig) || requestConfig
  }

  // ============ GET/DELETE: data 拼接到 query string ============
  if (['GET', 'DELETE'].includes(method.toUpperCase()) && requestConfig.data) {
    requestConfig.url += buildQuery(requestConfig.data)
    delete requestConfig.data
  }

  return new Promise((resolve, reject) => {
    uni.request({
      url: requestConfig.url,
      method: requestConfig.method,
      data: requestConfig.data,
      header: requestConfig.header,
      timeout: requestConfig.timeout,

      success(res) {
        // ============ 执行响应拦截器 ============
        let result = res
        for (const interceptor of responseInterceptors) {
          result = interceptor(result) || result
        }

        const { statusCode, data: body } = result

        // HTTP 层面的非 2xx
        if (statusCode === 401 || statusCode === 403) {
          uni.showToast({ title: '登录已过期, 请重新登录', icon: 'none' })
          // 跳转到登录页
          uni.reLaunch({ url: '/pages/login/index' })
          reject(new Error('UNAUTHORIZED'))
          return
        }

        if (statusCode === 429) {
          uni.showToast({ title: '请求过于频繁, 请稍后再试', icon: 'none' })
          reject(new Error('RATE_LIMITED'))
          return
        }

        // 业务层响应解析
        if (body && typeof body === 'object' && 'code' in body) {
          if (body.code === 200) {
            resolve(body)
          } else {
            const msg = body.message || `请求失败 [${body.code}]`
            uni.showToast({ title: msg, icon: 'none' })
            reject(new Error(msg))
          }
        } else {
          // 非标准 JSON 响应 (如文件下载)
          resolve(res)
        }
      },

      fail(err) {
        // ============ 执行错误拦截器 ============
        for (const interceptor of errorInterceptors) {
          interceptor(err)
        }

        let msg = '网络请求失败'
        if (err.errMsg) {
          if (err.errMsg.includes('timeout')) msg = '请求超时, 请检查网络'
          else if (err.errMsg.includes('fail')) msg = '网络连接失败'
        }
        uni.showToast({ title: msg, icon: 'none' })
        reject(err)
      }
    })
  })
}

// ==================== 拦截器注册 ====================

export function addRequestInterceptor(fn) {
  requestInterceptors.push(fn)
}

export function addResponseInterceptor(fn) {
  responseInterceptors.push(fn)
}

export function addErrorInterceptor(fn) {
  errorInterceptors.push(fn)
}

// ==================== 语法糖方法 ====================

export function get(url, data, options = {}) {
  return request({ ...options, url, method: 'GET', data })
}

export function post(url, data, options = {}) {
  return request({ ...options, url, method: 'POST', data })
}

export function put(url, data, options = {}) {
  return request({ ...options, url, method: 'PUT', data })
}

export function patch(url, data, options = {}) {
  return request({ ...options, url, method: 'PATCH', data })
}

export function del(url, data, options = {}) {
  return request({ ...options, url, method: 'DELETE', data })
}

/**
 * 文件上传专用 (multipart/form-data)
 */
export function uploadFile({ url, filePath, name = 'file', formData = {}, header = {}, service = 'platform' }) {
  const fullURL = isAbsoluteURL(url)
    ? url
    : `${service === 'platform' ? PLATFORM_BASE_URL : FILE_BASE_URL}${url}`

  const headers = { ...header }
  const token = getToken()
  const userId = getUserId()
  if (token) headers['Authorization'] = `Bearer ${token}`
  if (userId) headers['X-User-Id'] = userId

  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: fullURL,
      filePath,
      name,
      formData,
      header: headers,
      success(res) {
        try {
          const body = JSON.parse(res.data)
          if (body.code === 200) resolve(body)
          else reject(new Error(body.message || '上传失败'))
        } catch (e) {
          reject(new Error('响应解析失败'))
        }
      },
      fail(err) {
        reject(err)
      }
    })
  })
}