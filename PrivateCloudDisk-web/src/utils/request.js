// src/utils/request.js
import axios from 'axios';
import { useAuthStore } from '@/stores/authStore'
import { useToastStore } from '@/stores/toastStore'
import { cookie } from '@/utils/cookie'

const TOKEN_COOKIE_KEY = 'cloud_drive_token'

export class ApiError extends Error {
  constructor(message, options = {}) {
    super(message)
    this.name = 'ApiError'
    Object.assign(this, options)
  }
}

function notifyNetworkError(message, config = {}) {
  if (config?.suppressToast || config?.silent) return
  try {
    const toastStore = useToastStore()
    toastStore.showToast(message, 'error')
  } catch (error) {
    console.error(message)
  }
}

/**
 * 从 cookie 直接读取 token，不依赖 Pinia store
 * 确保在任何时机（含 store 未初始化时）都能正确附加 Authorization header
 */
function getTokenFromCookie() {
  return cookie.get(TOKEN_COOKIE_KEY)
}

// 创建 Axios 实例
const service = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json;charset=utf-8' },
});

// 请求拦截器
service.interceptors.request.use(
  (config) => {
    // 优先从 cookie 读取 token（不依赖 Pinia store 初始化顺序）
    const token = getTokenFromCookie()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    if (typeof FormData !== 'undefined' && config.data instanceof FormData) {
      if (typeof config.headers?.delete === 'function') {
        config.headers.delete('Content-Type')
        config.headers.delete('content-type')
      } else {
        delete config.headers['Content-Type']
        delete config.headers['content-type']
      }
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// 响应拦截器
service.interceptors.response.use(
  (response) => {
    const res = response.data;
    return res;
  },
  (error) => {
    // HTTP 状态码错误处理
    let message = '网络异常，请稍后重试';
    let isNetworkError = false
    let isTimeout = false
    if (error.response) {
      const status = error.response.status;
      switch (status) {
        case 401:
          message = '登录已过期，请重新登录';
          // 401 时清除 cookie 中的 token，并跳转登录
          cookie.remove(TOKEN_COOKIE_KEY)
          // 兼容旧版：同时清除 localStorage 中的旧 token
          localStorage.removeItem('cloudDriveToken')
          // 非登录页面才跳转，避免死循环
          if (!window.location.pathname.startsWith('/login')) {
            window.location.href = '/login';
          }
          break;
        case 403:
          message = '没有权限访问';
          break;
        case 404:
          message = '请求资源不存在';
          break;
        case 500:
          message = '服务器内部错误';
          break;
        default:
          message = `连接错误 ${status}`;
      }
    } else if (error.message.includes('timeout')) {
      isTimeout = true
      isNetworkError = true
      message = '请求超时，请检查网络';
    } else if (error.message.includes('Network Error')) {
      isNetworkError = true
      message = '网络连接异常';
    } else {
      isNetworkError = true
    }
    if (isNetworkError || isTimeout) {
      notifyNetworkError(message, error.config)
    }
    return Promise.reject(new ApiError(message, {
      status: error.response?.status,
      response: error.response,
      originalError: error,
      isNetworkError,
      isTimeout,
    }));
  }
);

export default service;

// 二次封装常用请求
export function get(url, params, config = {}) {
  return service.get(url, { params, ...config });
}

export function post(url, data, config = {}) {
  return service.post(url, data, config);
}

export function put(url, data, config = {}) {
  return service.put(url, data, config);
}

export function del(url, params, config = {}) {
  return service.delete(url, { params, ...config });
}

export function patch(url, data, config = {}) {
  return service.patch(url, data, config);
}