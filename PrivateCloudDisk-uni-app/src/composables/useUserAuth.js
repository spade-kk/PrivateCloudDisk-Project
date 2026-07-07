/**
 * composables/useUserAuth.js - 用户认证组合式函数
 *
 * 封装登录状态检查、路由守卫逻辑
 * 适用于 uni-app 小程序页面
 */
import { useUserStore } from '@/store/user'

export function useUserAuth() {
  const userStore = useUserStore()

  /**
   * 检查登录状态，未登录则跳转登录页
   * @returns {boolean} 是否已登录
   */
  function requireAuth() {
    if (!userStore.isLoggedIn) {
      uni.reLaunch({ url: '/pages/login/index' })
      return false
    }
    return true
  }

  /**
   * 页面 onShow 时调用，确保登录状态
   * @returns {boolean} 是否已登录
   */
  function checkAuth() {
    return requireAuth()
  }

  /**
   * 退出登录
   */
  function logout() {
    userStore.logout()
    uni.reLaunch({ url: '/pages/login/index' })
  }

  return { userStore, requireAuth, checkAuth, logout }
}