/**
 * api/auth.js - 认证 API
 *
 * 后端: UserController -> /business/users
 */
import { post, patch } from '@/utils/request'

const BASE = '/business/users'

/** 用户登录 */
export function login(account, password) {
  return post(`${BASE}/login`, { account, password })
}

/** 用户注册 */
export function register(data) {
  return post(`${BASE}/`, data)
}

/** 修改密码 */
export function changePassword(oldPassword, newPassword) {
  return post(`${BASE}/me/password`, { old_password: oldPassword, new_password: newPassword })
}

/** 更新个人资料 */
export function updateProfile(data) {
  return patch(`${BASE}/me`, data)
}