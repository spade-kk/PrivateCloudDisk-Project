// ============================================================
// 管理员认证 Store
// ============================================================
import { create } from 'zustand'
import type { AdminUser, RegisterRequest } from '@/types/api'
import { adminLoginApi, adminLogoutApi, getAdminInfoApi, adminRegisterApi } from '@/api/auth'
import {
  getAccessToken,
  setAccessToken,
  setRefreshToken,
  getRefreshToken,
  setAdminUser,
  getAdminUser,
  clearAuthStorage,
} from '@/utils/storage'

const isMockEnabled = import.meta.env.VITE_ENABLE_MOCK === 'true'

interface AuthState {
  /** JWT 令牌 */
  token: string | null
  /** 刷新令牌 */
  refreshToken: string | null
  /** 当前管理员信息 */
  admin: AdminUser | null
  /** 是否已登录 */
  isLoggedIn: boolean
  /** 加载状态 */
  loading: boolean

  /** 登录操作 */
  login: (account: string, password: string, captchaToken: string) => Promise<{ success: boolean; message?: string }>
  /** 注册操作 */
  register: (data: RegisterRequest) => Promise<{ success: boolean; message?: string }>
  /** 登出操作 */
  logout: () => Promise<void>
  /** 获取管理员信息 */
  fetchAdminInfo: () => Promise<void>
  /** 初始化 auth（从 localStorage 恢复） */
  initialize: () => Promise<void>
}

export const useAuthStore = create<AuthState>((set, get) => ({
  token: isMockEnabled ? 'mock-jwt-token-admin-2024' : getAccessToken(),
  refreshToken: isMockEnabled ? 'mock-refresh-token-admin-2024' : getRefreshToken(),
  admin: isMockEnabled ? {
    id: 'admin-001',
    userId: 'ADMIN-001',
    account: 'superadmin',
    name: '超级管理员',
    email: 'admin@privateclouddisk.com',
    phoneNumber: '13800000000',
    role: 'SUPER_ADMIN',
    status: 'ACTIVE',
    imagePath: '',
    lastLoginAt: new Date().toISOString(),
    createdAt: '2024-01-01T00:00:00.000Z',
  } : getAdminUser<AdminUser>(),
  isLoggedIn: isMockEnabled ? true : !!getAccessToken(),
  loading: false,

  login: async (account, password, captchaToken) => {
    set({ loading: true })
    try {
      const res = await adminLoginApi({
        account,
        password,
        captcha_token: captchaToken,
        captcha_action: 'login',
      })
      const { code, data, message } = res.data

      if (code === 200 && data) {
        setAccessToken(data.accessToken)
        setRefreshToken(data.refreshToken)
        setAdminUser(data.adminInfo)

        set({
          token: data.accessToken,
          refreshToken: data.refreshToken,
          admin: data.adminInfo,
          isLoggedIn: true,
          loading: false,
        })

        return { success: true }
      }

      set({ loading: false })
      return { success: false, message: message || '登录失败' }
    } catch (err: unknown) {
      set({ loading: false })
      const msg = err instanceof Error ? err.message : '网络错误，请稍后重试'
      return { success: false, message: msg }
    }
  },

  register: async (data) => {
    set({ loading: true })
    try {
      const res = await adminRegisterApi(data)
      const { code, message } = res.data

      if (code === 200) {
        set({ loading: false })
        return { success: true }
      }

      set({ loading: false })
      return { success: false, message: message || '注册失败' }
    } catch (err: unknown) {
      set({ loading: false })
      const msg = err instanceof Error ? err.message : '网络错误，请稍后重试'
      return { success: false, message: msg }
    }
  },

  logout: async () => {
    try {
      await adminLogoutApi()
    } catch {
      // 即便登出接口失败，也清除本地状态
    } finally {
      clearAuthStorage()
      set({
        token: null,
        refreshToken: null,
        admin: null,
        isLoggedIn: false,
      })
    }
  },

  fetchAdminInfo: async () => {
    const { token } = get()
    if (!token) return

    set({ loading: true })
    try {
      const res = await getAdminInfoApi()
      if (res.data.code === 200 && res.data.data) {
        const admin = res.data.data
        setAdminUser(admin)
        set({ admin })
      }
    } catch {
      // 静默失败
    } finally {
      set({ loading: false })
    }
  },

  initialize: async () => {
    // Mock 模式：直接已登录，无需初始化
    if (isMockEnabled) {
      set({ isLoggedIn: true, loading: false })
      return
    }

    const token = getAccessToken()
    if (!token) {
      set({ isLoggedIn: false, token: null, admin: null })
      return
    }

    const admin = getAdminUser<AdminUser>()

    // 尝试从服务器获取最新 admin 信息
    try {
      const res = await getAdminInfoApi()
      if (res.data.code === 200 && res.data.data) {
        const freshAdmin = res.data.data
        setAdminUser(freshAdmin)
        set({ admin: freshAdmin, isLoggedIn: true, token })
        return
      }
    } catch {
      // 服务器不可用时使用本地缓存
    }

    if (admin) {
      set({ admin, isLoggedIn: true, token })
    } else {
      set({ isLoggedIn: false, token: null, admin: null })
    }
  },
}))