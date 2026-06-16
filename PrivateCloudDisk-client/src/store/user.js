/**
 * store/user.js - 用户状态管理 (Pinia)
 *
 * 管理: JWT Token、用户信息、登录状态
 * 持久化: Token & userId 写入本地存储
 */
import { defineStore } from 'pinia'
import { getToken, setToken, removeToken, getUserId, setUserId, getUserProfile, setUserProfile } from '@/utils/storage'
import { login, register, getUserInfo, updateUserInfo, changePassword, deleteAccount } from '@/api/user'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: null,
    userId: null,
    profile: null,    // { id, account, phone_number, email, name, image_path }
    isLoggedIn: false
  }),

  getters: {
    /** 用户名 (优先展示 name, 无则展示 account) */
    displayName(state) {
      return state.profile?.name || state.profile?.account || '未登录'
    },

    /** 头像地址 */
    avatarUrl(state) {
      return state.profile?.image_path || ''
    }
  },

  actions: {
    // ==================== 会话恢复 ====================

    /** 从本地存储恢复登录状态 (App 启动时调用) */
    restoreSession() {
      const token = getToken()
      const userId = getUserId()
      const profile = getUserProfile()
      if (token && userId) {
        this.token = token
        this.userId = userId
        this.profile = profile
        this.isLoggedIn = true
      }
    },

    // ==================== 登录 ====================

    /**
     * 用户登录
     * @param {Object} params { account?, phone_number, password, captcha_token? }
     */
    async doLogin(params) {
      const res = await login(params)
      const token = res.data
      this.token = token
      setToken(token)

      // 登录成功后立即拉取用户信息以获取 userId
      await this.fetchProfile()
      return token
    },

    // ==================== 注册 ====================

    /**
     * 用户注册
     * @param {Object} params { phone_number, password, code, name, captcha_token? }
     */
    async doRegister(params) {
      const res = await register(params)
      return res.data // 返回 account
    },

    // ==================== 用户信息 ====================

    /** 拉取用户信息 */
    async fetchProfile() {
      const res = await getUserInfo()
      const profile = res.data
      this.profile = profile
      this.userId = profile.id
      this.isLoggedIn = true
      setUserId(profile.id)
      setUserProfile(profile)
      return profile
    },

    /** 更新用户信息 */
    async doUpdateProfile(params) {
      await updateUserInfo(params)
      // 更新本地缓存
      if (params.new_email !== undefined) this.profile.email = params.new_email
      if (params.new_phone_number !== undefined) this.profile.phone_number = params.new_phone_number
      if (params.new_name !== undefined) this.profile.name = params.new_name
      setUserProfile(this.profile)
    },

    /** 修改密码 */
    async doChangePassword(params) {
      await changePassword(params)
    },

    /** 注销账号 */
    async doDeleteAccount() {
      await deleteAccount()
      this.logout()
    },

    // ==================== 退出登录 ====================

    logout() {
      this.token = null
      this.userId = null
      this.profile = null
      this.isLoggedIn = false
      removeToken()
      uni.removeStorageSync('pcd_user_id')
      uni.removeStorageSync('pcd_user_profile')
      uni.reLaunch({ url: '/pages/login/index' })
    }
  }
})