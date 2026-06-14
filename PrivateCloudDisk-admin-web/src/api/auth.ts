// ============================================================
// 管理后台 - 认证 API
// ============================================================
import { request } from '@/utils/request'
import type { ApiResponse, LoginRequest, LoginResponse, AdminUser, RegisterRequest } from '@/types/api'

/** 管理员登录 */
export function adminLoginApi(data: LoginRequest) {
  return request.post<ApiResponse<LoginResponse>>('/api/v1/business/users/login', data)
}

/** 管理员注册 */
export function adminRegisterApi(data: RegisterRequest) {
  return request.post<ApiResponse<null>>('/api/v1/business/users/', data)
}

/** 发送邮箱验证码 */
export function sendVerificationCodeApi(email: string) {
  return request.post<ApiResponse<null>>('/api/v1/business/users/email/verification-code', null, {
    params: { email },
  })
}

/** 管理员登出 */
export function adminLogoutApi() {
  return request.post<ApiResponse<null>>('/api/admin/auth/logout')
}

/** 获取当前管理员信息 */
export function getAdminInfoApi() {
  return request.get<ApiResponse<AdminUser>>('/api/admin/auth/me')
}

/** 刷新 Token */
export function refreshAdminTokenApi(refreshToken: string) {
  return request.post<ApiResponse<{ accessToken: string; refreshToken: string }>>(
    '/api/admin/auth/refresh',
    { refreshToken }
  )
}

/** 修改管理员密码 */
export function changeAdminPasswordApi(oldPassword: string, newPassword: string) {
  return request.post<ApiResponse<null>>('/api/admin/auth/password', { oldPassword, newPassword })
}