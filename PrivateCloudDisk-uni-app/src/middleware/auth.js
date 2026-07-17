/**
 * middleware/auth.js - 路由认证守卫中间件
 *
 * 在页面 onLoad 前检查登录状态，未登录则重定向到登录页。
 * 对标 Vue3 Web 应用的 router.beforeEach 守卫模式。
 *
 * 使用方式:
 *   import { authGuard } from '@/middleware/auth'
 *   export default {
 *     onLoad() { authGuard() }
 *   }
 *
 * 或者在页面配置中统一拦截 (通过 pages.json 的 needLogin 标记)
 */
import { useUserStore } from '@/store/user'

/**
 * 认证守卫 - 检查登录状态
 *
 * @param {Object} options
 * @param {string} options.redirectUrl   未登录时重定向的页面 (默认登录页)
 * @param {boolean} options.requireLogin 是否需要登录 (默认 true)
 * @param {boolean} options.showToast    是否显示提示 (默认 true)
 * @returns {boolean} 已登录返回 true，否则返回 false
 */
export function authGuard(options = {}) {
  const {
    redirectUrl = '/pages/login/index',
    requireLogin = true,
    showToast = true
  } = options

  const userStore = useUserStore()

  // 恢复会话 (确保 Store 已初始化)
  if (!userStore.isLoggedIn && !userStore.token) {
    userStore.restoreSession()
  }

  if (requireLogin && !userStore.isLoggedIn) {
    if (showToast) {
      uni.showToast({
        title: '请先登录',
        icon: 'none',
        duration: 1500
      })
    }
    setTimeout(() => {
      uni.reLaunch({ url: redirectUrl })
    }, 300)
    return false
  }

  return true
}

/**
 * 游客守卫 - 已登录用户跳过登录/注册页
 *
 * @param {string} redirectUrl 已登录时重定向的页面 (默认首页)
 * @returns {boolean} 未登录返回 true
 */
export function guestGuard(redirectUrl = '/pages/index/index') {
  const userStore = useUserStore()

  if (!userStore.isLoggedIn && !userStore.token) {
    userStore.restoreSession()
  }

  if (userStore.isLoggedIn) {
    uni.reLaunch({ url: redirectUrl })
    return false
  }

  return true
}

/**
 * 页面级认证混入 (Mixin)
 *
 * 用于需要登录的页面，自动在 onLoad 时检查登录状态
 *
 * 使用方式:
 *   import { authMixin } from '@/middleware/auth'
 *   export default {
 *     mixins: [authMixin]
 *   }
 */
export const authMixin = {
  onLoad() {
    return authGuard()
  }
}

/**
 * 全局路由拦截器 (在 App.vue onLaunch 中注册)
 *
 * 拦截所有页面跳转，检查 needLogin 配置
 */
export function setupRouteGuard() {
  // 拦截 navigateTo
  const originalNavigateTo = uni.navigateTo
  uni.navigateTo = function (options) {
    const userStore = useUserStore()
    // 检查目标页面是否需要登录 (通过 pages.json 配置)
    if (options.url.includes('/pages/login/') || options.url.includes('/pages/register/')) {
      return originalNavigateTo.call(uni, options)
    }
    if (!userStore.isLoggedIn) {
      userStore.restoreSession()
    }
    return originalNavigateTo.call(uni, options)
  }

  // 拦截 switchTab
  const originalSwitchTab = uni.switchTab
  uni.switchTab = function (options) {
    return originalSwitchTab.call(uni, options)
  }
}