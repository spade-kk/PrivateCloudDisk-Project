/**
 * store/userStore.js - 用户状态管理 (Zustand)
 *
 * 管理: JWT Token、用户信息、登录状态、会话恢复
 */
import { create } from 'zustand'
import { getToken, setToken, removeToken, getUserId, setUserId, removeUserId, getUserProfile, setUserProfile } from '@/utils/storage'
import { loginUser, registerUser, getUserInfo, updateUserInfo, changePassword, deleteAccount, uploadAvatar } from '@/api/user'

export const useUserStore = create((set, get) => ({
  // ==================== 状态 ====================
  token: null,
  userId: null,
  profile: null,       // { id, account, phone_number, email, name, image_path }
  isLoggedIn: false,
  loading: false,

  // ==================== 计算属性 (getter 风格) ====================

  /** 展示名称 */
  displayName: () => {
    const { profile } = get()
    return profile?.name || profile?.account || '未登录'
  },

  /** 头像地址 */
  avatarUrl: () => {
    const { profile } = get()
    return profile?.image_path || ''
  },

  // ==================== 会话管理 ====================

  /** 从本地存储恢复登录状态 (App 启动时调用) */
  restoreSession: () => {
    const token = getToken()
    const userId = getUserId()
    const profile = getUserProfile()
    if (token && userId) {
      set({ token, userId, profile, isLoggedIn: true })
      return true
    }
    return false
  },

  // ==================== 登录 ====================

  /** 用户登录 */
  doLogin: async (params) => {
    set({ loading: true })
    try {
      const res = await loginUser(params)
      const token = res.data
      setToken(token)
      set({ token })

      // 拉取用户信息
      const infoRes = await getUserInfo()
      const profile = infoRes.data
      setUserId(profile.id)
      setUserProfile(profile)
      set({ profile, userId: profile.id, isLoggedIn: true, loading: false })
      return profile
    } catch (e) {
      set({ loading: false })
      throw e
    }
  },

  /** 刷新用户信息 */
  fetchProfile: async () => {
    try {
      const res = await getUserInfo()
      const profile = res.data
      setUserId(profile.id)
      setUserProfile(profile)
      set({ profile, userId: profile.id, isLoggedIn: true })
      return profile
    } catch (e) {
      throw e
    }
  },

  // ==================== 注册 ====================

  /** 用户注册 */
  doRegister: async (params) => {
    set({ loading: true })
    try {
      const res = await registerUser(params)
      set({ loading: false })
      return res.data
    } catch (e) {
      set({ loading: false })
      throw e
    }
  },

  // ==================== 更新信息 ====================

  /** 更新用户信息 */
  doUpdateProfile: async (params) => {
    set({ loading: true })
    try {
      await updateUserInfo(params)
      await get().fetchProfile()
      set({ loading: false })
    } catch (e) {
      set({ loading: false })
      throw e
    }
  },

  /** 修改密码 */
  doChangePassword: async (params) => {
    set({ loading: true })
    try {
      await changePassword(params)
      set({ loading: false })
    } catch (e) {
      set({ loading: false })
      throw e
    }
  },

  /** 上传头像 */
  doUploadAvatar: async (filePath) => {
    set({ loading: true })
    try {
      await uploadAvatar(filePath)
      await get().fetchProfile()
      set({ loading: false })
    } catch (e) {
      set({ loading: false })
      throw e
    }
  },

  // ==================== 退出 ====================

  /** 退出登录 */
  logout: () => {
    removeToken()
    removeUserId()
    set({ token: null, userId: null, profile: null, isLoggedIn: false })
  },

  // ==================== 注销 ====================

  /** 注销账号 */
  doDeleteAccount: async () => {
    set({ loading: true })
    try {
      await deleteAccount()
      get().logout()
      set({ loading: false })
    } catch (e) {
      set({ loading: false })
      throw e
    }
  }
}))