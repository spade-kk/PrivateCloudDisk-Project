// ============================================================
// users.ts — 用户管理 API 模块
// ============================================================
// 封装用户认证、用户信息管理、密码修改、头像上传、设备管理等接口。
// 所有接口安全传输：密码在调用前已通过 PBKDF2-SHA256 预哈希处理。
//
// 验证码获取流程：
//   1. 首次获取 → 需 Turnstile 人机验证 → 后端返回 resend_token（UUID）
//   2. 重发验证码 → 不需人机验证，携带 resend_token 即可
//      限制：10 分钟内最多重发 8 次，超限需重新走首次流程
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
// 验证码 — 通用（注册、换绑等场景共用）
// ============================================================

/**
 * 首次发送验证码（需人机验证）
 *
 * 适用于注册、邮箱换绑、手机号换绑等所有需要验证码的场景。
 * 首次发送必须通过 Turnstile 人机验证，后端校验通过后返回 resend_token，
 * 后续重发可凭此 token 免人机验证。
 *
 * @param target - 目标地址（邮箱地址或手机号）
 * @param captchaToken - Turnstile 人机验证 Token
 * @param captchaAction - 验证动作标识（send_code / change_email / change_phone 等）
 * @returns Promise<{ code: number, message: string, data: { resend_token: string } }>
 */
export function sendVerificationCodeApi(
  target: string,
  captchaToken: string,
  captchaAction: string = 'send_code',
  purpose: string
): Promise<any> {
  return post('business/verification-code/send', {
      email: target,
      captcha_token: captchaToken,
      captcha_action: captchaAction,
      purpose: purpose
    })
}

/**
 * 重发验证码（免人机验证，凭 resend_token）
 *
 * 首次发送验证码后，后端返回 resend_token（UUID），
 * 10 分钟内最多可凭此 token 重发 8 次，无需再次完成 Turnstile。
 * 超限或超时后需重新走首次流程（重新通过人机验证）。
 *
 * @param resendToken - 首次发送时后端返回的 resend_token
 * @returns Promise<{ code: number, message: string }>
 */
export function resendVerificationCodeApi(target: string, purpose: string, resendToken: string): Promise<any> {
  return post('business/verification-code/resend', {
      email: target,
      purpose: purpose
  }, {
    header: { 'X-Resend-Token': resendToken },
  })
}

/**
 * 首次发送验证码（需人机验证）
 *
 * 适用于注册、邮箱换绑、手机号换绑等所有需要验证码的场景。
 * 首次发送必须通过 Turnstile 人机验证，后端校验通过后返回 resend_token，
 * 后续重发可凭此 token 免人机验证。
 *
 * @param target - 目标地址（邮箱地址或手机号）
 * @param captchaToken - Turnstile 人机验证 Token
 * @param captchaAction - 验证动作标识（send_code / change_email / change_phone 等）
 * @returns Promise<{ code: number, message: string, data: { resend_token: string } }>
 */
export function sendRegisterVerificationCodeApi(
  target: string,
  captchaToken: string,
  captchaAction: string = 'send_code'
): Promise<any> {
  return post('business/verification-code/register/send', {
      email: target,
      captcha_token: captchaToken,
      captcha_action: captchaAction
    })
}

/**
 * 重发验证码（免人机验证，凭 resend_token）
 *
 * 首次发送验证码后，后端返回 resend_token（UUID），
 * 10 分钟内最多可凭此 token 重发 8 次，无需再次完成 Turnstile。
 * 超限或超时后需重新走首次流程（重新通过人机验证）。
 *
 * @param resendToken - 首次发送时后端返回的 resend_token
 * @returns Promise<{ code: number, message: string }>
 */
export function resendRegisterVerificationCodeApi(target: string, resendToken: string): Promise<any> {
  return post('business/verification-code/register/resend', {
      email: target
  }, {
    header: { 'X-Resend-Token': resendToken },
  })
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
 * 修改用户密码（企业级：含人机验证）
 *
 * 新旧密码均需经过客户端预哈希后再发送。
 * 后端应验证旧密码正确性，再更新为新密码。
 * captchaToken 为 Turnstile 人机验证 Token。
 *
 * @param old_password - 已预哈希的旧密码
 * @param new_password - 已预哈希的新密码
 * @param captchaToken - Turnstile 验证 Token
 * @param captchaAction - 验证动作标识
 * @returns Promise 修改结果
 */
export function changeMyUserPasswordApi(
  old_password: string,
  new_password: string,
  captchaToken: string = '',
  captchaAction: string = 'change_password',
): Promise<any> {
  const data = { old_password, new_password, captcha_token: captchaToken, captcha_action: captchaAction }
  return post('business/users/me/password', data)
}

// ============================================================
// 邮箱换绑
// ============================================================

/**
 * 发送邮箱换绑验证码
 *
 * 向新邮箱发送验证码，用于确认邮箱归属。
 * 首次发送需通过 Turnstile 人机验证。
 *
 * @param email - 新邮箱地址
 * @param captchaToken - Turnstile 验证 Token
 * @param captchaAction - 验证动作标识
 * @returns Promise<{ code: number, message: string, data: { resend_token: string } }>
 */
export function sendChangeEmailCodeApi(
  email: string,
  captchaToken: string = '',
  captchaAction: string = 'change_email',
): Promise<any> {
  return post('business/verification-code/send', {
    email: email,
    captcha_token: captchaToken,
    captcha_action: captchaAction,
    purpose: 'BIND'
  })
}

/**
 * 重发邮箱换绑验证码（免人机验证，凭 resend_token）
 *
 * @param resendToken - 首次发送时后端返回的 resend_token
 * @returns Promise<{ code: number, message: string }>
 */
export function resendChangeEmailCodeApi(email: string, resendToken: string): Promise<any> {
  return post('business/verification-code/resend', {
      email: email,
      purpose: 'BIND'
  }, {
    header: { 'X-Resend-Token': resendToken },
  })
}

/**
 * 确认换绑邮箱
 *
 * 验证验证码后完成邮箱换绑。
 *
 * @param newEmail - 新邮箱地址
 * @param code - 邮箱验证码
 * @returns Promise 换绑结果
 */
export function confirmChangeEmailApi(
  newEmail: string,
  code: string,
): Promise<any> {
  const data = { new_email: newEmail, code }
  return post('business/users/me/email', data)
}

// ============================================================
// 手机号换绑
// ============================================================

/**
 * 发送手机号换绑验证码
 *
 * 向新手机号发送短信验证码，用于确认手机号归属。
 * 首次发送需通过 Turnstile 人机验证。
 *
 * @param phone - 新手机号
 * @param captchaToken - Turnstile 验证 Token
 * @param captchaAction - 验证动作标识
 * @returns Promise<{ code: number, message: string, data: { resend_token: string } }>
 */
export function sendChangePhoneCodeApi(
  phone: string,
  captchaToken: string = '',
  captchaAction: string = 'change_phone',
): Promise<any> {
    return post('business/verification-code/send', {
    phone_number: phone,
    captcha_token: captchaToken,
    captcha_action: captchaAction,
    purpose: 'BIND'
  })
}

/**
 * 重发手机号换绑验证码（免人机验证，凭 resend_token）
 *
 * @param resendToken - 首次发送时后端返回的 resend_token
 * @returns Promise<{ code: number, message: string }>
 */
export function resendChangePhoneCodeApi(phone: string, resendToken: string): Promise<any> {
  return post('business/verification-code/resend', {
      phone_number: phone,
      purpose: 'BIND'
  }, {
    header: { 'X-Resend-Token': resendToken },
  })
}

/**
 * 确认换绑手机号
 *
 * 验证验证码后完成手机号换绑。
 *
 * @param newPhone - 新手机号
 * @param code - 短信验证码
 * @returns Promise 换绑结果
 */
export function confirmChangePhoneApi(
  newPhone: string,
  code: string,
): Promise<any> {
  const data = { new_phone_number: newPhone, code }
  return post('business/users/me/phone', data)
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