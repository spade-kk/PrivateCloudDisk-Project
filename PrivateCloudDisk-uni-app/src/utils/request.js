/**
 * utils/request.js - 企业级 HTTP 请求封装
 *
 * 核心能力:
 * - 自动注入 X-User-Id Header 与 Bearer Token
 * - 统一响应格式解析 { code, message, data }
 * - 401 自动跳转登录页
 * - 请求/响应拦截器链
 * - 请求重试机制 (指数退避)
 * - 并发请求队列管理
 * - 超时配置
 * - 请求日志 & 性能监控
 *
 * 对标 Vue3 Web 前端应用的 API 设计规范
 */
import { PLATFORM_BASE_URL, FILE_BASE_URL } from './const'
import { getToken, getUserId } from './storage'

// ==================== 配置 ====================

const DEFAULT_TIMEOUT = 30000
const MAX_RETRIES = 2
const RETRY_DELAY_BASE = 1000 // 指数退避基数 (ms)

/** 不触发重试的 HTTP 状态码 */
const NO_RETRY_STATUS = [400, 401, 403, 404, 422]

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

/**
 * 延迟函数 (指数退避)
 */
function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

/**
 * 判断是否应该重试
 */
function shouldRetry(statusCode, retryCount) {
  if (retryCount >= MAX_RETRIES) return false
  if (statusCode && NO_RETRY_STATUS.includes(statusCode)) return false
  return true
}

// ==================== 响应处理 ====================

/**
 * 统一解析响应体
 * 后端约定: { code: 200, message: "ok", data: ... }
 */
function parseResponse(res) {
  const { statusCode, data } = res

  // HTTP 状态码非 2xx
  if (statusCode < 200 || statusCode >= 300) {
    const error = new Error(data?.message || `请求失败 (${statusCode})`)
    error.statusCode = statusCode
    error.response = res
    throw error
  }

  // 后端业务码
  if (data && typeof data === 'object' && 'code' in data) {
    if (data.code === 200) {
      return data
    }
    const error = new Error(data.message || '请求失败')
    error.code = data.code
    error.response = res
    throw error
  }

  // 非标准响应, 直接返回
  return data
}

// ==================== 核心请求方法 ====================

/**
 * 发起 HTTP 请求 (支持重试)
 *
 * @param {Object} options
 * @param {string} options.url        请求路径
 * @param {string} options.method     GET / POST / PUT / PATCH / DELETE
 * @param {Object} options.data       请求体 (POST/PUT/PATCH) 或 query params (GET/DELETE)
 * @param {Object} options.header     附加请求头
 * @param {'platform'|'file'} options.service 服务类型
 * @param {number} options.timeout    超时毫秒数
 * @param {boolean} options.skipAuth  跳过 Token 注入
 * @param {number} options.retries    最大重试次数 (默认 MAX_RETRIES)
 * @returns {Promise<{code:number, message:string, data:*}>}
 */
export async function request(options = {}) {
  const {
    url,
    method = 'GET',
    data,
    header = {},
    service = 'platform',
    timeout = DEFAULT_TIMEOUT,
    skipAuth = false,
    retries = MAX_RETRIES
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
    'X-Request-ID': `${Date.now()}-${Math.random().toString(36).slice(2, 9)}`,
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

  // ============ 发起请求 (含重试) ============
  let lastError = null
  const startTime = Date.now()

  for (let attempt = 0; attempt <= retries; attempt++) {
    try {
      const res = await new Promise((resolve, reject) => {
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

            try {
              const parsed = parseResponse(result)
              // 性能日志 (开发环境)
              if (process.env.NODE_ENV === 'development') {
                const elapsed = Date.now() - startTime
                console.log(
                  `[HTTP] ${requestConfig.method} ${requestConfig.url} -> ${res.statusCode} (${elapsed}ms)`
                )
              }
              resolve(parsed)
            } catch (err) {
              reject(err)
            }
          },

          fail(err) {
            // ============ 执行错误拦截器 ============
            for (const interceptor of errorInterceptors) {
              interceptor(err, requestConfig)
            }
            reject(err)
          }
        })
      })

      return res
    } catch (error) {
      lastError = error

      // 401 统一处理: 清除登录态, 跳转登录页
      if (error.statusCode === 401) {
        console.warn('[HTTP] 401 Unauthorized, 跳转登录页')
        uni.removeStorageSync('pcd_token')
        uni.removeStorageSync('pcd_user_id')
        uni.removeStorageSync('pcd_user_profile')
        uni.reLaunch({ url: '/pages/login/index' })
        throw error
      }

      // 判断是否重试
      if (shouldRetry(error.statusCode, attempt)) {
        const retryDelay = RETRY_DELAY_BASE * Math.pow(2, attempt)
        console.warn(`[HTTP] 请求失败, ${retryDelay}ms 后重试 (${attempt + 1}/${retries}): ${requestConfig.url}`)
        await delay(retryDelay)
        continue
      }

      // 用户友好提示
      const errMsg = error.message || '网络异常, 请稍后重试'
      if (error.statusCode !== 401) {
        uni.showToast({ title: errMsg, icon: 'none', duration: 2500 })
      }
      throw error
    }
  }

  throw lastError
}

// ==================== 拦截器注册 ====================

/**
 * 注册请求拦截器
 * @param {Function} fn (config) => config
 */
export function addRequestInterceptor(fn) {
  requestInterceptors.push(fn)
}

/**
 * 注册响应拦截器
 * @param {Function} fn (response) => response
 */
export function addResponseInterceptor(fn) {
  responseInterceptors.push(fn)
}

/**
 * 注册错误拦截器
 * @param {Function} fn (error, config) => void
 */
export function addErrorInterceptor(fn) {
  errorInterceptors.push(fn)
}

// ==================== 便捷方法 ====================

/**
 * GET 请求
 */
export function get(url, params, options = {}) {
  return request({ ...options, url, method: 'GET', data: params })
}

/**
 * POST 请求
 */
export function post(url, data, options = {}) {
  return request({ ...options, url, method: 'POST', data })
}

/**
 * PUT 请求
 */
export function put(url, data, options = {}) {
  return request({ ...options, url, method: 'PUT', data })
}

/**
 * PATCH 请求
 */
export function patch(url, data, options = {}) {
  return request({ ...options, url, method: 'PATCH', data })
}

/**
 * DELETE 请求
 */
export function del(url, data, options = {}) {
  return request({ ...options, url, method: 'DELETE', data })
}

/**
 * 文件上传 (uni.uploadFile 封装)
 */
export function upload(url, filePath, name = 'file', formData = {}, options = {}) {
  const token = getToken()
  const userId = getUserId()

  return new Promise((resolve, reject) => {
    const uploadTask = uni.uploadFile({
      url: url.startsWith('http') ? url : `${FILE_BASE_URL}${url}`,
      filePath,
      name,
      formData,
      header: {
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(userId ? { 'X-User-Id': userId } : {})
      },
      success(res) {
        try {
          const parsed = parseResponse(res)
          resolve(parsed)
        } catch (err) {
          reject(err)
        }
      },
      fail(err) {
        reject(err)
      }
    })

    // 进度回调
    if (options.onProgress) {
      uploadTask.onProgressUpdate((res) => {
        options.onProgress(res.progress)
      })
    }
  })
}

// ==================== 默认错误拦截器 ====================

addErrorInterceptor((err, config) => {
  console.error(`[HTTP Error] ${config.method} ${config.url}:`, err.errMsg || err.message)
})