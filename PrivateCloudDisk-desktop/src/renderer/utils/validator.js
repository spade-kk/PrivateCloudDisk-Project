/**
 * utils/validator.js - 表单验证规则
 * 与后端 DTO 校验保持一致
 */
/** 验证手机号 */
export function validatePhone(phone) {
  if (!phone) return '请输入手机号'
  if (!/^1[3-9]\d{9}$/.test(phone)) return '请输入正确的手机号'
  return null
}

/** 验证密码强度 */
export function validatePassword(password) {
  if (!password) return '请输入密码'
  if (password.length < 8) return '密码至少 8 位'
  if (password.length > 64) return '密码最多 64 位'
  if (!/[A-Z]/.test(password)) return '密码需包含大写字母'
  if (!/[a-z]/.test(password)) return '密码需包含小写字母'
  if (!/[0-9]/.test(password)) return '密码需包含数字'
  if (!/[!@#$%^&*(),.?":{}|<>]/.test(password)) return '密码需包含特殊字符'
  return null
}

/** 验证用户名 */
export function validateName(name) {
  if (!name) return '请输入用户名'
  if (name.length < 2) return '用户名至少 2 个字符'
  if (name.length > 30) return '用户名最多 30 个字符'
  if (!/^[\u4e00-\u9fa5a-zA-Z0-9_-]+$/.test(name)) return '用户名只能包含中文、字母、数字、下划线和连字符'
  return null
}

/** 验证邮箱 */
export function validateEmail(email) {
  if (!email) return '请输入邮箱'
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return '请输入正确的邮箱地址'
  return null
}

/** 验证确认密码 */
export function validateConfirmPassword(password, confirmPassword) {
  if (!confirmPassword) return '请再次输入密码'
  if (password !== confirmPassword) return '两次密码不一致'
  return null
}

/** 验证文件名 */
export function validateFileName(name) {
  if (!name || !name.trim()) return '请输入名称'
  if (name.length > 255) return '名称过长'
  const invalid = /[<>:"/\\|?*\x00-\x1f]/
  if (invalid.test(name)) return '名称包含非法字符'
  return null
}