import axios, { AxiosError } from 'axios'
import { message } from 'antd'
import { clearAuthStorage, getAccessToken } from '@/utils/storage.ts'

export const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 30000
})

request.interceptors.request.use((config) => {
  const token = getAccessToken()

  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }

  config.headers['X-Trace-Id'] = crypto.randomUUID()
  config.headers['X-Client-Type'] = 'ADMIN_WEB'

  return config
})

request.interceptors.response.use(
  (response) => {
    const data = response.data

    if (data && data.success === false) {
      message.error(data.message || '请求失败')
      return Promise.reject(data)
    }

    return response
  },
  (error: AxiosError<any>) => {
    const status = error.response?.status

    if (status === 401) {
      clearAuthStorage()
      message.error('登录已过期，请重新登录')
      window.location.href = '/login'
      return Promise.reject(error)
    }

    if (status === 403) {
      message.error('无权限访问')
      return Promise.reject(error)
    }

    if (status === 429) {
      message.error('请求过于频繁，请稍后再试')
      return Promise.reject(error)
    }

    message.error(error.response?.data?.message || error.message || '网络异常')
    return Promise.reject(error)
  }
)