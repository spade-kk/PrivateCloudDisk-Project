import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { loginApi } from '@/api/index'
import { getMyUserInfoApi } from '@/api/modules/users'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('cloudDriveToken') || '')
  const isLoggedIn = computed(() => !!token.value)

  // --- 用户资料（官网头部显示用）---
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
      const res = await loginApi(phoneNumber, password, captchaToken, 'login');

      if (res.code === 200) {
        token.value = res.data
        localStorage.setItem('cloudDriveToken', token.value)
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
    localStorage.removeItem('cloudDriveToken')
  }

  return { token, isLoggedIn, user, userLoading, displayName, userInitial, login, logout, fetchUserInfo }
})
