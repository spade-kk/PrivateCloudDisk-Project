import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { loginApi } from '@/api/index'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('cloudDriveToken') || '')
  const isLoggedIn = computed(() => !!token.value)

  async function login(phoneNumber, password) {
    try {
      const res = await loginApi(phoneNumber, password);

      console.log('登录响应:', res); // 添加日志以检查响应内容

      if (res.code === 200) {
        token.value = res.data
        localStorage.setItem('cloudDriveToken', token.value)
        return { success: true }
      }
      return { success: false, message: res.message || '登录失败' }
    } catch (error) {
      return { success: false, message: '网络错误，请稍后重试' }
    }
  }

  function logout() {
    token.value = ''
    localStorage.removeItem('cloudDriveToken')
  }

  return { token, isLoggedIn, login, logout }
})