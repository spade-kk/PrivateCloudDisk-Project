import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { loginApi } from '@/api/index'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('cloudDriveToken') || '')
  const isLoggedIn = computed(() => !!token.value)

  async function login(phoneNumber, password) {
    try {
      const res = await loginApi(phoneNumber, password);

      if (res.code === 200) {
        token.value = res.data
        localStorage.setItem('cloudDriveToken', token.value)
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
    localStorage.removeItem('cloudDriveToken')
  }

  return { token, isLoggedIn, login, logout }
})
