/**
 * utils/validator.js - 表单校验规则
 *
 * 与后端 DTO 校验注解保持一致
 */
const PHONE_REGEX = /^1[3-9]\d{9}$/
const PASSWORD_REGEX = /^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d]{8,15}$/
const ACCOUNT_REGEX = /^[a-zA-Z0-9_]{4,16}$/
const NAME_REGEX = /^[a-zA-Z0-9\u4e00-\u9fa5]{2,10}$/

export function validatePhone(phone) {
  if (!phone) return '手机号不能为空'
  if (!PHONE_REGEX.test(phone)) return '手机号格式不正确'
  return ''
}

export function validatePassword(password) {
  if (!password) return '密码不能为空'
  if (!PASSWORD_REGEX.test(password)) return '密码需8-15位，包含字母和数字'
  return ''
}

export function validateAccount(account) {
  if (!account) return '账号不能为空'
  if (!ACCOUNT_REGEX.test(account)) return '账号需4-16位字母、数字或下划线'
  return ''
}

export function validateName(name) {
  if (!name) return '用户名不能为空'
  if (!NAME_REGEX.test(name)) return '用户名需2-10位字母、数字或中文'
  return ''
}

export function validateEmail(email) {
  if (!email) return ''
  const regex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  if (!regex.test(email)) return '邮箱格式不正确'
  return ''
}

export function validateUUID(id) {
  const regex = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/
  return regex.test(id)
}