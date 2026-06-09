// src/utils/request.js
import axios from 'axios';
import { useAuthStore } from '@/stores/authStore'
import { useToastStore } from '@/stores/toastStore'

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

// 创建 Axios 实例
const service = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1', // 环境变量
  timeout: 15000,
  headers: { 'Content-Type': 'application/json;charset=utf-8' },
});

// 请求拦截器
service.interceptors.request.use(
  (config) => {
    const authStore = useAuthStore();
    if (authStore.token) {
      config.headers.Authorization = `Bearer ${authStore.token}`;
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
    // // 根据后端约定的结构判断业务状态码 暂时注释 不要错误拦截文件下载GET请求的 blob响应哦
    // if (res.code !== 200) {
    //   // 特定业务错误码处理，如 401 跳转登录
    //   if (res.code === 401) {
    //     // 清除 token 并跳转
    //     window.location.href = '/login';
    //   }
    //   return Promise.reject(new ApiError(res.message || '请求失败', {
    //     code: res.code,
    //     data: res.data,
    //     response,
    //     isBusinessError: true,
    //   }));
    // }
    // 正常返回数据（只返回有效 data）
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

// 二次封装常用请求，业务调用时直接使用这些函数
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
