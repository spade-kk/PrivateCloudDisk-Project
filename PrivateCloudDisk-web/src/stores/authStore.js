import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { loginApi } from '@/api/index'
import { getMyUserInfoApi } from '@/api/modules/users'
import { cookie } from '@/utils/cookie'

const TOKEN_COOKIE_KEY = 'cloud_drive_token'

export const useAuthStore = defineStore('auth', () => {
  // 初始化时从 cookie 读取 token（而非 localStorage）
  const token = ref(cookie.get(TOKEN_COOKIE_KEY) || '')
  const isLoggedIn = computed(() => !!token.value)

  // --- 用户资料（控制台头部显示用）---
  const user = ref({
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

  async function fetchUserInfo() {
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
      // 静默失败，不影响渲染
    } finally {
      userLoading.value = false
    }
    return user.value
  }

  async function login(phoneNumber, password, captchaToken = '') {
    try {
      const res = await loginApi(phoneNumber, password, captchaToken, 'login')

      if (res.code === 200) {
        token.value = res.data
        // 将 token 存入 cookie（Secure + SameSite，HTTPS 下更安全）
        cookie.set(TOKEN_COOKIE_KEY, token.value, { days: 7 })
        // 登录成功后预加载用户信息
        fetchUserInfo()
        return { success: true }
      }
      return { success: false, message: res.message || '登录失败' }
    } catch (error) {
      if (error.isBusinessError) {
        return { success: false, message: error.message || '手机号或密码错误', scope: 'form' }
      }
      return { success: false, message: error.message || '网络错误，请稍后重试', scope: 'network' }
    }
  }

  function logout() {
    token.value = ''
    user.value = { name: '', account: '', email: '', phone_number: '', image_path: '' }
    // 清除 cookie 中的 token
    cookie.remove(TOKEN_COOKIE_KEY)
    // 兼容旧版：同时清除 localStorage 中的旧 token
    localStorage.removeItem('cloudDriveToken')
  }

  return { token, isLoggedIn, user, userLoading, displayName, userInitial, login, logout, fetchUserInfo }
})