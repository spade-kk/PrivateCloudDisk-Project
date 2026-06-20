/**
 * utils/storage.js - 本地存储封装
 *
 * 开发环境使用 localStorage
 * 生产环境使用 electron-store (通过 preload 暴露)
 */
import { STORAGE_KEYS } from './const'

// ==================== 基础操作 ====================

function getStorage() {
  return window.localStorage
}

export function getItem(key) {
  try {
    const raw = getStorage().getItem(key)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

export function setItem(key, value) {
  try {
    getStorage().setItem(key, JSON.stringify(value))
  } catch (e) {
    console.error('[Storage] 写入失败:', e)
  }
}

export function removeItem(key) {
  getStorage().removeItem(key)
}

export function clearAll() {
  getStorage().clear()
}

// ==================== 快捷方法 ====================

export function getToken() {
  return getStorage().getItem(STORAGE_KEYS.TOKEN) || null
}

export function setToken(token) {
  getStorage().setItem(STORAGE_KEYS.TOKEN, token)
}

export function removeToken() {
  getStorage().removeItem(STORAGE_KEYS.TOKEN)
}

export function getUserId() {
  return getItem(STORAGE_KEYS.USER_ID)
}

export function setUserId(id) {
  setItem(STORAGE_KEYS.USER_ID, id)
}

export function removeUserId() {
  removeItem(STORAGE_KEYS.USER_ID)
}

export function getUserProfile() {
  return getItem(STORAGE_KEYS.USER_PROFILE)
}

export function setUserProfile(profile) {
  setItem(STORAGE_KEYS.USER_PROFILE, profile)
}

export function getRememberLogin() {
  return getItem(STORAGE_KEYS.REMEMBER_LOGIN)
}

export function setRememberLogin(remember) {
  setItem(STORAGE_KEYS.REMEMBER_LOGIN, remember)
}

export function getSavedAccount() {
  return getStorage().getItem(STORAGE_KEYS.SAVED_ACCOUNT) || null
}

export function setSavedAccount(account) {
  getStorage().setItem(STORAGE_KEYS.SAVED_ACCOUNT, account)
}

export function getSettings() {
  return getItem(STORAGE_KEYS.SETTINGS) || {}
}

export function setSettings(settings) {
  setItem(STORAGE_KEYS.SETTINGS, settings)
}