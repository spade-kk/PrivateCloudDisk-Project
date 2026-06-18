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
      shares.value = await getMySharesApi()
      return shares.value
    } catch (e) {
      console.error('获取分享列表失败:', e)
      return []
    } finally {
      loading.value = false
    }
  }

  /**
   * 创建分享
   */
  async function createShare(params: ShareCreateParams): Promise<ShareLinkItem | null> {
    try {
      const item = await createShareApi(params)
      shares.value.unshift(item)
      return item
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
      await revokeShareApi(share_id)
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