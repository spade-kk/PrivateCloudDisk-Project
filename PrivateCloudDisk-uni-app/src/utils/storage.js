/**
 * utils/storage.js - 本地存储封装
 *
 * 特点:
 * - 自动序列化/反序列化 JSON
 * - 过期时间支持
 * - 同步/异步双模式
 */
import { STORAGE_KEYS } from './const'

/**
 * 设置存储
 * @param {string} key   存储键
 * @param {*}      value 值 (自动 JSON 序列化)
 * @param {number} [expireSeconds] 过期秒数 (可选)
 */
export function setStorage(key, value, expireSeconds) {
  const payload = {
    value,
    expireAt: expireSeconds ? Date.now() + expireSeconds * 1000 : null
  }
  try {
    uni.setStorageSync(key, JSON.stringify(payload))
  } catch (e) {
    console.error('[Storage] setStorage 失败:', e)
  }
}

/**
 * 获取存储
 * @param {string} key 存储键
 * @returns {*} 已过期返回 null
 */
export function getStorage(key) {
  try {
    const raw = uni.getStorageSync(key)
    if (!raw) return null
    const payload = JSON.parse(raw)
    if (payload.expireAt && Date.now() > payload.expireAt) {
      uni.removeStorageSync(key)
      return null
    }
    return payload.value
  } catch (e) {
    console.error('[Storage] getStorage 失败:', e)
    return null
  }
}

/**
 * 移除存储
 * @param {string} key
 */
export function removeStorage(key) {
  try {
    uni.removeStorageSync(key)
  } catch (e) {
    console.error('[Storage] removeStorage 失败:', e)
  }
}

/** 清空所有应用存储 */
export function clearStorage() {
  try {
    uni.clearStorageSync()
  } catch (e) {
    console.error('[Storage] clearStorage 失败:', e)
  }
}

// ========== 快捷存取方法 ==========

export function getToken() {
  return getStorage(STORAGE_KEYS.TOKEN)
}

export function setToken(token) {
  setStorage(STORAGE_KEYS.TOKEN, token)
}

export function removeToken() {
  removeStorage(STORAGE_KEYS.TOKEN)
}

export function getUserId() {
  return getStorage(STORAGE_KEYS.USER_ID)
}

export function setUserId(id) {
  setStorage(STORAGE_KEYS.USER_ID, id)
}

export function getUserProfile() {
  return getStorage(STORAGE_KEYS.USER_PROFILE)
}

export function setUserProfile(profile) {
  setStorage(STORAGE_KEYS.USER_PROFILE, profile)
}