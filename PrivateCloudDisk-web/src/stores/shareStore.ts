import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  getMySharesApi,
  createShareApi,
  revokeShareApi,
  type ShareLinkItem,
  type ShareCreateParams
} from '@/api/modules/shares'

export const useShareStore = defineStore('share', () => {
  const shares = ref<ShareLinkItem[]>([])
  const loading = ref(false)

  /**
   * 获取我的分享列表
   */
  async function fetchMyShares(): Promise<ShareLinkItem[]> {
    loading.value = true
    try {
      const res = await getMySharesApi()
      if (res.code == 200) {
        shares.value = res.data || []
        return shares.value
      }
      console.error('获取分享列表失败:', res.message || `未知错误 业务异常码${res.code}`)
      return []
    } catch (e) {
      console.error('获取分享列表失败:', e)
      return []
    } finally {
      loading.value = false
    }
  }

  /**
   * 创建分享（v2 — 多资源）
   */
  async function createShare(params: ShareCreateParams): Promise<ShareLinkItem | null> {
    try {
      const res = await createShareApi(params)
      if (res.code == 200) {
        const item = res.data
        shares.value.unshift(item)
        return item
      }
      console.error('创建分享失败:', res.message || `未知错误 业务异常码${res.code}`)
      return null
    } catch (e) {
      console.error('创建分享失败:', e)
      return null
    }
  }

  /**
   * 撤销分享
   */
  async function revokeShare(share_id: string): Promise<boolean> {
    try {
      const res = await revokeShareApi(share_id)
      if (res.code != 200) {
        console.error('撤销分享失败:', res.message || `未知异常 业务异常码${res.code}`)
        return false
      }
      const idx = shares.value.findIndex((s) => s.share_id === share_id)
      if (idx !== -1) {
        shares.value[idx].share_status = 'revoked'
      }
      return true
    } catch (e) {
      console.error('撤销分享失败:', e)
      return false
    }
  }

  return { shares, loading, fetchMyShares, createShare, revokeShare }
})