// ============================================================
// auth.ts — SSO 认证 API 模块
// ============================================================
// 对接 auth-service（SSO 中心）的认证端点：
// - OAuth2 Authorization Code Flow
// - 第三方登录（微信/QQ/GitHub）
// - 设备扫码授权（RFC 8628 Device Authorization Grant）
// - 令牌刷新与撤销
// - 验证码登录（短信/邮箱）
//
// 注意：密码登录仍走 platform-service 的 /business/users/login
// 令牌刷新走 auth-service 的 /oauth2/token/refresh
// 第三方登录走 auth-service 的 /oauth2/callback/{provider}
// ============================================================

import { get, post } from '@/utils/request'

// ============================================================
// Token 管理
// ============================================================

/** 刷新 Access Token */
export function refreshTokenApi(refreshToken: string): Promise<any> {
  return post('/auth/oauth2/token/refresh', null, {
    params: { refreshToken },
  })
}

/** 撤销所有 Token */
export function revokeTokensApi(): Promise<any> {
  return post('/auth/oauth2/token/revoke')
}

/** 令牌内省 */
export function introspectTokenApi(token: string): Promise<any> {
  return post('/auth/oauth2/introspect', null, {
    params: { token },
  })
}

// ============================================================
// 第三方 OAuth 登录
// ============================================================

/** 获取第三方授权 URL */
export function getThirdPartyAuthUrlApi(provider: string, state?: string): Promise<any> {
  return get(`/auth/oauth2/third-party/${provider}/authorize`, { state: state || '' } as any)
}

/** 第三方回调 */
export function thirdPartyCallbackApi(provider: string, code: string, state: string): Promise<any> {
  return get(`/auth/oauth2/callback/${provider}`, { code, state } as any)
}

// ============================================================
// 设备扫码授权
// ============================================================

/** 设备端：初始化授权 */
export function deviceAuthInitApi(clientId: string, scope?: string, deviceInfo?: string): Promise<any> {
  return post('/auth/oauth2/device/init', {
    clientId,
    scope: scope || 'openid profile offline_access',
    deviceInfo: deviceInfo || '',
  })
}

/** 设备端：轮询授权状态 */
export function deviceAuthPollApi(deviceCode: string): Promise<any> {
  return post('/auth/oauth2/device/token', null, {
    params: { deviceCode },
  })
}

/** Web 端：用户确认授权 */
export function deviceAuthConfirmApi(userCode: string): Promise<any> {
  return post('/auth/oauth2/device/confirm', null, {
    params: { userCode },
  })
}

/** Web 端：用户拒绝授权 */
export function deviceAuthDenyApi(userCode: string): Promise<any> {
  return post('/auth/oauth2/device/deny', null, {
    params: { userCode },
  })
}

// ============================================================
// 验证码登录（短信/邮箱）
// 验证码发送复用 platform-service 的 /business/verification-code/send
// 验证码登录调 platform-service 的验证码登录接口
// ============================================================

/** 验证码登录 */
export function codeLoginApi(
  target: string,
  code: string,
  loginType: 'phone' | 'email',
  captchaToken?: string,
): Promise<any> {
  return post('/business/users/code-login', {
    phone_number: loginType === 'phone' ? target : undefined,
    email: loginType === 'email' ? target : undefined,
    code,
    captcha_token: captchaToken || '',
    captcha_action: 'code_login',
  })
}