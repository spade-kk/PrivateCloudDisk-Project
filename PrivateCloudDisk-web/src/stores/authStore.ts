import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { loginApi } from '@/api/index'
import { getMyUserInfoApi } from '@/api/modules/users'
import { codeLoginApi, refreshTokenApi, thirdPartyCallbackApi } from '@/api/modules/auth'
import { cookie } from '@/utils/cookie'
import { hashPasswordForTransport } from '@/utils/crypto'

const TOKEN_COOKIE_KEY = 'cloud_drive_token'
const REFRESH_TOKEN_KEY = 'cloud_drive_refresh_token'

export interface UserProfile {
  /** 后端用户 UUID；公开仓库所有者判断优先使用该稳定标识。 */
  id?: string
  name: string
  account: string
  email: string
  phone_number: string
  image_path: string
}

export interface ApiErrorLike {
  isBusinessError?: boolean
  message?: string
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref(cookie.get(TOKEN_COOKIE_KEY) || '')
  const isLoggedIn = computed(() => !!token.value)

  const user = ref<UserProfile>({
    id: '',
    name: '',
    account: '',
    email: '',
    phone_number: '',
    image_path: '',
  })
  const userLoading = ref(false)

  const displayName = computed(() => user.value.name || user.value.account || '用户')
  const userInitial = computed(() => {
    const src = displayName.value.trim()
    return src ? src.slice(0, 1).toUpperCase() : 'U'
  })

  async function fetchUserInfo(): Promise<UserProfile> {
    if (!token.value || userLoading.value) return user.value
    userLoading.value = true
    try {
      const res = await getMyUserInfoApi()
      if (res.code === 200 && res.data) {
        user.value = {
          id: res.data.id || res.data.user_id || res.data.userId || '',
          name: res.data.name || '',
          account: res.data.account || '',
          email: res.data.email || '',
          phone_number: res.data.phone_number || '',
          image_path: res.data.image_path || '',
        }
      }
    } catch {
      // 静默失败
    } finally {
      userLoading.value = false
    }
    return user.value
  }

  async function login(identifier: string, password: string, captchaToken: string = ''): Promise<{ success: boolean; message?: string; scope?: string }> {
    try {
      // 客户端密码预哈希 - 密码明文永不离开浏览器
      const hashedPassword = await hashPasswordForTransport(password)
      // 【需求九】统一账号输入由 API 层根据格式映射为 account / phone_number / email。
      const res = await loginApi(identifier.trim(), hashedPassword, captchaToken, 'login')
      if (res.code === 200) {
        saveTokenFromResponse(res.data)
        return { success: true }
      }
      return { success: false, message: res.message || '登录失败' }
    } catch (error: unknown) {
      const err = error as ApiErrorLike
      if (err.isBusinessError) {
        return { success: false, message: err.message || '账号或密码错误', scope: 'form' }
      }
      return { success: false, message: err.message || '网络错误，请稍后重试', scope: 'network' }
    }
  }

  /** 验证码登录（短信/邮箱） */
  async function codeLogin(target: string, code: string, loginType: 'phone' | 'email', captchaToken?: string): Promise<{ success: boolean; message?: string; scope?: string }> {
    try {
      const res = await codeLoginApi(target, code, loginType, captchaToken)
      if (res.code === 200) {
        saveTokenFromResponse(res.data)
        return { success: true }
      }
      return { success: false, message: res.message || '验证码登录失败' }
    } catch (error: unknown) {
      const err = error as ApiErrorLike
      if (err.isBusinessError) {
        return { success: false, message: err.message || '验证码错误或已过期', scope: 'form' }
      }
      return { success: false, message: err.message || '网络错误，请稍后重试', scope: 'network' }
    }
  }

  /** 第三方 OAuth 登录 */
  async function thirdPartyLogin(provider: string, code: string, state: string): Promise<{ success: boolean; message?: string }> {
    try {
      const res = await thirdPartyCallbackApi(provider, code, state)
      if (res.code === 200 && res.data) {
        saveTokenFromResponse(res.data)
        return { success: true }
      }
      return { success: false, message: res.message || '第三方登录失败' }
    } catch (error: unknown) {
      const err = error as ApiErrorLike
      return { success: false, message: err.message || '第三方登录失败，请重试' }
    }
  }

  /** 设备扫码授权成功后的 Token 保存 */
  function saveDeviceToken(accessToken: string, refreshToken?: string): void {
    token.value = accessToken
    cookie.set(TOKEN_COOKIE_KEY, accessToken, { days: 7 })
    if (refreshToken) {
      localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
    }
    fetchUserInfo()
  }

  /** 刷新 Token */
  async function refreshAccessToken(): Promise<boolean> {
    const rt = localStorage.getItem(REFRESH_TOKEN_KEY)
    if (!rt) return false
    try {
      const res = await refreshTokenApi(rt)
      if (res.code === 200 && res.data) {
        token.value = res.data.accessToken || res.data.access_token
        cookie.set(TOKEN_COOKIE_KEY, token.value, { days: 7 })
        if (res.data.refreshToken || res.data.refresh_token) {
          localStorage.setItem(REFRESH_TOKEN_KEY, res.data.refreshToken || res.data.refresh_token)
        }
        return true
      }
      return false
    } catch {
      return false
    }
  }

  /** 统一保存 Token（兼容不同响应格式） */
  function saveTokenFromResponse(data: any): void {
    if (typeof data === 'string') {
      token.value = data
    } else if (data.accessToken || data.access_token) {
      token.value = data.accessToken || data.access_token
    } else if (data.token) {
      token.value = data.token
    } else {
      token.value = data
    }
    cookie.set(TOKEN_COOKIE_KEY, token.value, { days: 7 })
    if (data.refreshToken || data.refresh_token) {
      localStorage.setItem(REFRESH_TOKEN_KEY, data.refreshToken || data.refresh_token)
    }
    fetchUserInfo()
  }

  function logout(): void {
    token.value = ''
    user.value = { id: '', name: '', account: '', email: '', phone_number: '', image_path: '' }
    cookie.remove(TOKEN_COOKIE_KEY)
    localStorage.removeItem(REFRESH_TOKEN_KEY)
    localStorage.removeItem('cloudDriveToken')
  }

  return { token, isLoggedIn, user, userLoading, displayName, userInitial, login, codeLogin, thirdPartyLogin, saveDeviceToken, refreshAccessToken, logout, fetchUserInfo }
})
