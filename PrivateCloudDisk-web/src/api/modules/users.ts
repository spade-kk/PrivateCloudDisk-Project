// ============================================================
// users.ts — 用户管理 API 模块
// ============================================================
// 封装用户认证、用户信息管理、密码修改、头像上传、设备管理等接口。
// 所有接口安全传输：密码在调用前已通过 PBKDF2-SHA256 预哈希处理。
// ============================================================

import { get, post, patch, put, del } from '@/utils/request'

// ============================================================
// 认证相关
// ============================================================

/**
 * 用户登录
 *
 * 密码在调用前已由 authStore 通过 hashPasswordForTransport() 预哈希处理，
 * 确保密码明文永不离浏览器内存。后端应进行二次 bcrypt/scrypt 哈希验证。
 *
 * @param phone_number - 用户手机号
 * @param password - 已预哈希的密码（PBKDF2-SHA256 格式）
 * @param captchaToken - 人机验证码 Token（Turnstile/CAPTCHA）
 * @param captchaAction - 验证动作标识，用于区分登录/注册等场景
 * @returns Promise<{ token: string, user: object }> 登录成功返回 Token 和用户信息
 */
export function loginApi(
  phone_number: string,
  password: string,
  captchaToken: string = '',
  captchaAction: string = 'login',
): Promise<any> {
  const data = {
    phone_number,
    password,
    captcha_token: captchaToken,
    captcha_action: captchaAction,
  }
  return post('business/users/login', data)
}

/**
 * 用户注册
 *
 * 密码同样经过客户端预哈希。验证码由短信/邮件服务发送。
 * 人机验证码 Token 用于防止机器注册。
 *
 * @param phone_number - 手机号
 * @param password - 已预哈希的密码
 * @param code - 短信/邮件验证码
 * @param username - 用户名/昵称
 * @param captchaToken - 人机验证码 Token
 * @param captchaAction - 验证动作标识
 * @returns Promise 注册结果
 */
export function registerApi(
  phone_number: string,
  password: string,
  code: string,
  username: string,
  captchaToken: string = '',
  captchaAction: string = 'register',
): Promise<any> {
  const data = {
    phone_number,
    password,
    code,
    name: username,
    captcha_token: captchaToken,
    captcha_action: captchaAction,
  }
  return post('business/users/', data)
}

// ============================================================
// 用户信息管理
// ============================================================

/**
 * 获取当前登录用户的信息
 *
 * 用于个人中心页面、顶部导航栏用户信息展示等。
 * 返回数据包含用户名、手机号、邮箱、头像、会员等级等。
 *
 * @returns Promise<{ id, name, phone_number, email, avatar, ... }> 用户完整信息
 */
export function getMyUserInfoApi(): Promise<any> {
  return get('business/users/me')
}

/**
 * 更新当前用户信息
 *
 * 支持部分更新：只传需要修改的字段即可。
 * 邮箱和手机号修改可能需要验证码确认（取决于后端策略）。
 *
 * @param email - 新邮箱地址
 * @param username - 新用户名
 * @param phone_number - 新手机号
 * @returns Promise 更新后的用户信息
 */
export function updateMyUserInfoApi(
  email: string,
  username: string,
  phone_number: string,
): Promise<any> {
  const data = {
    new_email: email,
    new_username: username,
    new_phone_number: phone_number,
  }
  return patch('business/users/me', data)
}

// ============================================================
// 密码管理
// ============================================================

/**
 * 修改用户密码
 *
 * 新旧密码均需经过客户端预哈希后再发送。
 * 后端应验证旧密码正确性，再更新为新密码。
 *
 * @param old_password - 已预哈希的旧密码
 * @param new_password - 已预哈希的新密码
 * @returns Promise 修改结果
 */
export function changeMyUserPasswordApi(
  old_password: string,
  new_password: string,
): Promise<any> {
  const data = { old_password, new_password }
  return post('business/users/me/password', data)
}

// ============================================================
// 头像管理
// ============================================================

/**
 * 上传用户头像
 *
 * 使用 FormData 上传，支持 JPG/PNG/WebP 格式。
 * 建议前端做裁剪和压缩处理后再上传，减少带宽和存储消耗。
 *
 * @param file - 头像图片 File 对象
 * @returns Promise<{ avatar_url: string }> 上传后的头像 URL
 */
export function uploadUserAvatarApi(file: File): Promise<any> {
  const formData = new FormData()
  formData.append('avatar_file', file)
  return put('business/users/me/avatar', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

// ============================================================
// 设备管理
// ============================================================

/**
 * 获取当前账号的在线设备列表
 *
 * 用于安全中心的设备管理页面，展示登录设备信息
 * （设备名称、IP、登录时间、浏览器类型等）。
 *
 * @returns Promise<Array<{ device_id, device_name, ip, login_time, ... }>> 设备列表
 */
export function getMyUserOnlineDevicesApi(): Promise<any> {
  return get('business/users/me/online-devices')
}

// ============================================================
// 账号注销
// ============================================================

/**
 * 注销当前用户账号
 *
 * 此操作不可逆，调用前应弹出二次确认对话框。
 * 注销后 Token 自动失效，需清除本地状态并跳转首页。
 *
 * @returns Promise 注销结果
 */
export function deleteMyUserApi(): Promise<any> {
  return del('business/users/me')
}