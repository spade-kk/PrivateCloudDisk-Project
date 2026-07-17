/**
 * composables/useClipboard.js - 剪贴板操作组合式函数
 *
 * 核心能力:
 * - 复制文本到剪贴板
 * - 获取剪贴板内容
 * - 复制结果反馈
 */
import { ref } from 'vue'

export function useClipboard() {
  const isCopying = ref(false)
  const lastCopiedText = ref('')

  /**
   * 复制文本到剪贴板
   * @param {string} text 要复制的文本
   * @param {string} successMsg 成功提示 (默认 "已复制")
   * @returns {Promise<boolean>}
   */
  async function copy(text, successMsg = '已复制') {
    if (!text) {
      uni.showToast({ title: '复制内容为空', icon: 'none' })
      return false
    }
    isCopying.value = true
    try {
      await uni.setClipboardData({ data: text })
      lastCopiedText.value = text
      uni.showToast({ title: successMsg, icon: 'success', duration: 1500 })
      return true
    } catch (e) {
      console.error('[Clipboard] 复制失败:', e)
      uni.showToast({ title: '复制失败', icon: 'none' })
      return false
    } finally {
      isCopying.value = false
    }
  }

  /**
   * 获取剪贴板内容
   * @returns {Promise<string>}
   */
  async function getClipboard() {
    try {
      const res = await uni.getClipboardData()
      return res.data
    } catch (e) {
      console.error('[Clipboard] 获取剪贴板失败:', e)
      return ''
    }
  }

  return {
    isCopying,
    lastCopiedText,
    copy,
    getClipboard
  }
}