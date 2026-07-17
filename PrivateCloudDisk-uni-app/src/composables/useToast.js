/**
 * composables/useToast.js - 全局 Toast 提示组合式函数
 *
 * 核心能力:
 * - 统一 Toast 样式 (企业级蓝色主题)
 * - 成功/失败/警告/信息四种类型
 * - 自动堆叠管理 (避免多个 Toast 同时显示)
 * - Loading 遮罩管理
 */
import { ref } from 'vue'

// 全局 Toast 队列, 避免多实例重复显示
let toastTimer = null
let loadingVisible = false

export function useToast() {
  const isShowing = ref(false)

  /**
   * 显示 Toast
   * @param {string} title 提示文字
   * @param {string} icon  none / success / error / loading
   * @param {number} duration 持续时间 (ms)
   */
  function show(title, icon = 'none', duration = 2000) {
    if (toastTimer) clearTimeout(toastTimer)

    isShowing.value = true
    uni.showToast({ title, icon, duration })

    toastTimer = setTimeout(() => {
      isShowing.value = false
    }, duration)
  }

  /** 成功提示 */
  function success(title = '操作成功') {
    show(title, 'success', 2000)
  }

  /** 错误提示 */
  function error(title = '操作失败') {
    show(title, 'error', 2500)
  }

  /** 警告提示 */
  function warning(title) {
    show(title, 'none', 2500)
  }

  /** 信息提示 */
  function info(title) {
    show(title, 'none', 2000)
  }

  /** 显示加载中 */
  function showLoading(title = '加载中...') {
    if (loadingVisible) return
    loadingVisible = true
    uni.showLoading({ title, mask: true })
  }

  /** 隐藏加载中 */
  function hideLoading() {
    if (!loadingVisible) return
    loadingVisible = false
    uni.hideLoading()
  }

  /** 显示 Modal 确认框 */
  function confirm(title, content, options = {}) {
    return new Promise((resolve) => {
      uni.showModal({
        title,
        content,
        confirmColor: '#4F6EF7',
        cancelColor: '#999',
        ...options,
        success(res) {
          resolve(res.confirm)
        },
        fail() {
          resolve(false)
        }
      })
    })
  }

  /** 显示 ActionSheet */
  function showActionSheet(itemList, options = {}) {
    return new Promise((resolve) => {
      uni.showActionSheet({
        itemList,
        itemColor: '#333',
        ...options,
        success(res) {
          resolve(res.tapIndex)
        },
        fail() {
          resolve(-1)
        }
      })
    })
  }

  return {
    isShowing,
    show,
    success,
    error,
    warning,
    info,
    showLoading,
    hideLoading,
    confirm,
    showActionSheet
  }
}