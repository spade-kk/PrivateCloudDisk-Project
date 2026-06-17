import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { loginApi } from '@/api/index'
import { getMyUserInfoApi } from '@/api/modules/users'
import { cookie } from '@/utils/cookie'
import { hashPasswordForTransport } from '@/utils/crypto'

const TOKEN_COOKIE_KEY = 'cloud_drive_token'

export interface UserProfile {
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

  async function login(phoneNumber: string, password: string, captchaToken: string = ''): Promise<{ success: boolean; message?: string; scope?: string }> {
    try {
      // 客户端密码预哈希 - 密码明文永不离开浏览器
      const hashedPassword = await hashPasswordForTransport(password, phoneNumber)
      const res = await loginApi(phoneNumber, hashedPassword, captchaToken, 'login')
      if (res.code === 200) {
        token.value = res.data
        cookie.set(TOKEN_COOKIE_KEY, token.value, { days: 7 })
        fetchUserInfo()
        return { success: true }
      }
      return { success: false, message: res.message || '登录失败' }
    } catch (error: unknown) {
      const err = error as ApiErrorLike
      if (err.isBusinessError) {
        return { success: false, message: err.message || '手机号或密码错误', scope: 'form' }
      }
      return { success: false, message: err.message || '网络错误，请稍后重试', scope: 'network' }
    }
  }

  function logout(): void {
    token.value = ''
    user.value = { name: '', account: '', email: '', phone_number: '', image_path: '' }
    cookie.remove(TOKEN_COOKIE_KEY)
    localStorage.removeItem('cloudDriveToken')
  }

  return { token, isLoggedIn, user, userLoading, displayName, userInitial, login, logout, fetchUserInfo }
})