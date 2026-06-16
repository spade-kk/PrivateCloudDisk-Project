/**
 * api/user.js - 用户操作 API
 *
 * 后端: Spring Boot UserController
 * 端点前缀: /business/users
 */
import { get, post, patch, put, del } from '@/utils/request'
import { uploadFile } from '@/utils/request'
import { PLATFORM_BASE_URL } from '@/utils/const'

/** 用户登录
 *  body: { account, phone_number, password, captcha_token }
 *  返回: token 字符串
 */
export function loginUser(params) {
  return post('/users/login', params)
}

/** 用户注册
 *  body: { phone_number, password, code, name, captcha_token }
 */
export function registerUser(params) {
  return post('/users', params)
}

/** 获取用户信息 */
export function getUserInfo() {
  return get('/users/me')
}

/** 更新用户信息 (PATCH)
 *  body: { new_name, new_phone_number, new_email }
 */
export function updateUserInfo(params) {
  return patch('/users/me', params)
}

/** 修改密码
 *  body: { user_password, new_password }
 */
export function changePassword(params) {
  return post('/users/me/password', params)
}

/** 上传头像 (PUT multipart/form-data)
 *  后端: @PutMapping("/me/avatar") + @RequestParam MultipartFile avator_file
 *  字段名: avator_file
 */
export function uploadAvatar(filePath) {
  const formData = new FormData()
  formData.append('avator_file', filePath)
  return put('/users/me/avatar', formData)
}

/** 注销账号 */
export function deleteAccount() {
  return del('/users/me')
}

/** 获取在线设备列表 */
export function getOnlineDevices() {
  return get('/users/me/online-devices')
}