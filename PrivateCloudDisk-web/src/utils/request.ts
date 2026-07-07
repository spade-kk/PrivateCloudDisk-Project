// ============================================================
// request.ts — Axios 请求封装与服务层
// ============================================================
// 基于 Axios 实例的企业级 HTTP 请求封装，提供统一的请求/响应拦截、
// Token 自动注入、安全签名头附加、表单数据处理、错误统一处理等功能。
//
// 安全设计：
// - Token 从 Cookie 读取，不依赖 Pinia store 初始化顺序
// - 所有请求自动附加浏览器指纹头 X-Device-Fingerprint
// - 敏感接口（登录/注册/用户/密码/安全/Token/验证）自动附加 HMAC 签名头
// - 401 时自动清除 Token 并跳转登录页
// - 防止 FormData 请求被错误设置 Content-Type 导致 boundary 丢失
// ============================================================

import axios from 'axios'
import type { AxiosError, InternalAxiosRequestConfig } from 'axios'
import { useAuthStore } from '@/stores/authStore'
import { useToastStore } from '@/stores/toastStore'
import { useSpaceStore } from '@/stores/spaceStore'
import { cookie } from '@/utils/cookie'
import { getVisitorId } from '@/utils/fingerprint'
import { generateSecurityHeaders } from '@/utils/securityToken'

// ============================================================
// 常量定义
// ============================================================

/** Token 在 Cookie 中的存储键名 */
export const TOKEN_COOKIE_KEY = 'cloud_drive_token'

/** 敏感接口路径前缀：这些接口需要附带 HMAC 安全签名头 */
const SENSITIVE_API_PATTERNS = [
  '/login',
  '/register',
  '/users',
  '/password',
  '/security',
  '/token',
  '/verify',
]

// ============================================================
// 自定义错误类型
// ============================================================

/**
 * API 错误类
 *
 * 扩展标准 Error，携带 HTTP 状态码、原始响应、网络错误标识等
 * 业务层可根据此信息做差异化处理（如网络错误重试、权限错误跳转）。
 */
export class ApiError extends Error {
  /** HTTP 状态码，网络错误时可能为 undefined */
  status?: number
  /** 原始 Axios 响应对象 */
  response?: unknown
  /** 原始 Axios 错误对象 */
  originalError?: Error
  /** 是否为网络连接错误（Network Error） */
  isNetworkError: boolean
  /** 是否为请求超时 */
  isTimeout: boolean

  constructor(message: string, options: Partial<ApiError> = {}) {
    super(message)
    this.name = 'ApiError'
    Object.assign(this, options)
  }
}

// ============================================================
// 内部辅助函数
// ============================================================

/** 指纹缓存：避免重复调用 FingerprintJS 采集 */
let cachedVisitorId: string | null = null

/**
 * 确保 visitorId 已缓存，未缓存时触发采集
 * 设计为非阻塞：失败时返回空字符串，不影响请求流程
 */
async function ensureVisitorId(): Promise<string> {
  if (cachedVisitorId) return cachedVisitorId
  try {
    cachedVisitorId = await getVisitorId()
    return cachedVisitorId
  } catch {
    return ''
  }
}

/**
 * 判断接口是否为敏感接口（需要安全签名）
 *
 * 匹配 SENSITIVE_API_PATTERNS 中的任一前缀，
 * 这些接口涉及认证、密码、安全等敏感操作。
 */
function isSensitiveRequest(url: string | undefined): boolean {
  if (!url) return false
  return SENSITIVE_API_PATTERNS.some((pattern) => url.includes(pattern))
}

/**
 * 网络错误通知（非阻塞 Toast 提示）
 *
 * 使用 suppressToast / silent 配置项可跳过提示，
 * 适用于静默请求（如轮询心跳）场景。
 */
function notifyNetworkError(message: string, config?: InternalAxiosRequestConfig): void {
  if (config?.suppressToast || config?.silent) return
  try {
    const toastStore = useToastStore()
    toastStore.showToast(message, 'error')
  } catch {
    // Toast store 未初始化时静默失败
    console.error(message)
  }
}

/**
 * 从 Cookie 直接读取 Token
 *
 * 不依赖 Pinia store 的初始化顺序，确保在任何时机
 * （含 store 未初始化时）都能正确附加 Authorization header。
 * 这是企业级实践中最稳妥的做法。
 */
function getTokenFromCookie(): string | null {
  return cookie.get(TOKEN_COOKIE_KEY)
}

// ============================================================
// Axios 实例创建
// ============================================================

/**
 * 核心 Axios 实例
 *
 * 配置说明：
 * - baseURL: 从环境变量 VITE_API_BASE_URL 读取，默认 /api/v1
 * - timeout: 15 秒超时，避免长时间挂起
 * - Content-Type: 默认 JSON 请求
 */
const service = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json;charset=utf-8' },
})

// ============================================================
// 请求拦截器
// ============================================================

/**
 * 请求拦截器 — 在请求发出前统一处理
 *
 * 处理顺序：
 * 1. 从 Cookie 读取 Token 附加 Authorization header
 * 2. 自动附加浏览器指纹 X-Device-Fingerprint（所有请求）
 * 3. 对敏感接口附加 HMAC 安全签名头（X-Timestamp / X-Nonce / X-Request-Signature）
 * 4. 处理 FormData 请求：删除 Content-Type 让浏览器自动设置 boundary
 */
service.interceptors.request.use(
  async (config: InternalAxiosRequestConfig) => {
    // 1. 认证 Token：优先从 Cookie 读取，不依赖 Pinia store 初始化顺序
    const token = getTokenFromCookie()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }

    // 1.5 空间上下文：附加当前空间 ID
    try {
      const spaceStore = useSpaceStore()
      if (spaceStore.currentSpaceId) {
        config.headers['X-Space-Id'] = spaceStore.currentSpaceId
      }
    } catch {
      // spaceStore 未初始化时静默失败
    }

    // 2. 设备指纹：所有请求都附加，便于后端在未登录态下进行风控和限流
    const visitorId = await ensureVisitorId()
    if (visitorId) {
      config.headers['X-Device-Fingerprint'] = visitorId
    }

    // 3. 安全签名：仅对敏感接口附加防重放签名头
    const url = config.url
    if (isSensitiveRequest(url) && config.headers) {
      const bodyStr = config.data ? JSON.stringify(config.data) : ''
      const securityHeaders = await generateSecurityHeaders(bodyStr)
      Object.assign(config.headers, securityHeaders)
    }

    // 4. FormData 处理：删除 Content-Type 让浏览器自动设置正确的 multipart boundary
    if (typeof FormData !== 'undefined' && config.data instanceof FormData) {
      if (typeof config.headers?.delete === 'function') {
        config.headers.delete('Content-Type')
        config.headers.delete('content-type')
      } else {
        delete config.headers['Content-Type']
        delete config.headers['content-type']
      }
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  },
)

// ============================================================
// 响应拦截器
// ============================================================

/**
 * 响应拦截器 — 统一处理返回数据和错误
 *
 * 成功响应：直接返回 response.data 业务数据
 * 错误响应：按 HTTP 状态码分类处理，401 自动清除 Token 并跳转登录页
 */
service.interceptors.response.use(
  (response) => {
    const res = response.data
    return res
  },
  (error: AxiosError) => {
    let message = '网络异常，请稍后重试'
    let isNetworkError = false
    let isTimeout = false
    console.warn(error)
    if (error.response) {
      const status = error.response.status
      switch (status) {
        case 401:
          // 未授权：清除 Token 并跳转登录页
          message = '登录已过期，请重新登录'
          cookie.remove(TOKEN_COOKIE_KEY)
          // 兼容旧版：同时清除 localStorage 中的旧 Token
          localStorage.removeItem('cloudDriveToken')
          // 非登录页面才跳转，避免死循环
          if (!window.location.pathname.startsWith('/login')) {
            window.location.href = '/login'
          }
          break
        case 403:
          message = '没有权限访问'
          break
        case 404:
          message = '请求资源不存在'
          break
        case 429:
          message = '请求过于频繁，请稍后再试'
          break
        case 500:
          message = '服务器内部错误'
          break
        default:
          message = `连接错误 ${status}`
      }
    } else if (error.message?.includes('timeout')) {
      isTimeout = true
      isNetworkError = true
      message = '请求超时，请检查网络'
    } else if (error.message?.includes('Network Error')) {
      isNetworkError = true
      message = '网络连接异常'
    } else {
      isNetworkError = true
    }

    if (isNetworkError || isTimeout) {
      notifyNetworkError(message, error.config as InternalAxiosRequestConfig | undefined)
    }

    return Promise.reject(
      new ApiError(message, {
        status: error.response?.status,
        response: error.response,
        originalError: error,
        isNetworkError,
        isTimeout,
      }),
    )
  },
)

export default service

// ============================================================
// 二次封装常用 HTTP 方法
// ============================================================
// 语义化封装，减少重复代码，统一参数风格。
// 所有方法均返回 Promise，与 Axios 实例共享拦截器配置。
// ============================================================

/**
 * GET 请求
 * @param url - 请求路径（相对于 baseURL）
 * @param params - 查询参数对象
 * @param config - 额外的 Axios 配置
 */
export function get<T = unknown>(url: string, params?: Record<string, unknown>, config?: Record<string, unknown>): Promise<T> {
  return service.get(url, { params, ...config })
}

/**
 * POST 请求
 * @param url - 请求路径
 * @param data - 请求体数据
 * @param config - 额外的 Axios 配置
 */
export function post<T = unknown>(url: string, data?: unknown, config?: Record<string, unknown>): Promise<T> {
  return service.post(url, data, config)
}

/**
 * PUT 请求
 * @param url - 请求路径
 * @param data - 请求体数据
 * @param config - 额外的 Axios 配置
 */
export function put<T = unknown>(url: string, data?: unknown, config?: Record<string, unknown>): Promise<T> {
  return service.put(url, data, config)
}

/**
 * DELETE 请求
 * @param url - 请求路径
 * @param params - 查询参数对象
 * @param config - 额外的 Axios 配置
 */
export function del<T = unknown>(url: string, params?: Record<string, unknown>, config?: Record<string, unknown>): Promise<T> {
  return service.delete(url, { params, ...config })
}

/**
 * PATCH 请求
 * @param url - 请求路径
 * @param data - 请求体数据
 * @param config - 额外的 Axios 配置
 */
export function patch<T = unknown>(url: string, data?: unknown, config?: Record<string, unknown>): Promise<T> {
  return service.patch(url, data, config)
}