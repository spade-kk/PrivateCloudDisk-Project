/**
 * api/user.js - 用户模块 API
 *
 * 后端: UserController -> /business/users
 */
import { get, post, patch, del, upload } from '@/utils/request'

const BASE = '/business/users'

/** 用户登录 */
export function login(data) {
  return post(`${BASE}/login`, data)
}

/** 用户注册 */
export function register(data) {
  return post(`${BASE}/`, data)
}

/** 获取当前用户信息 (X-User-Id) */
export function getUserInfo() {
  return get(`${BASE}/me`)
}

/** 更新用户信息 */
export function updateUserInfo(data) {
  return patch(`${BASE}/me`, data)
}

/** 修改密码 */
export function changePassword(data) {
  return post(`${BASE}/me/password`, data)
}

/** 注销账号 */
export function deleteAccount() {
  return del(`${BASE}/me`)
}

/** 上传头像 */
export function uploadAvatar(filePath) {
  return upload(`${BASE}/me/avatar`, filePath, 'avator_file')
}

/** 查询在线设备列表 */
export function getOnlineDevices() {
  return get(`${BASE}/me/online-devices`)
}

/** 发送验证码 */
export function sendVerificationCode(data) {
  return post('/business/verification-code/send', data)
}

/** 确认换绑邮箱 */
export function confirmChangeEmail(newEmail, code) {
  return post(`${BASE}/me/email`, { new_email: newEmail, code })
}

/** 确认换绑手机号 */
export function confirmChangePhone(newPhone, code) {
  return post(`${BASE}/me/phone`, { new_phone_number: newPhone, code })
}