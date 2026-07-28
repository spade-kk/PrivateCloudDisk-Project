import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  getRecentUploadsApi,
  getRecentDownloadsApi,
  getRecentOpensApi,
  getRecentAccessApi,
  type RecentAccessVO,
  type AccessType,
} from '@/api/modules/recent'

export const useRecentStore = defineStore('recent', () => {
  // ============================================================
  // State
  // ============================================================

  /** 最近上传 */
  const recentUploads = ref<RecentAccessVO[]>([])

  /** 最近下载 */
  const recentDownloads = ref<RecentAccessVO[]>([])

  /** 最近打开 */
  const recentOpens = ref<RecentAccessVO[]>([])

  /** 所有最近访问（混合） */
  const allRecent = ref<RecentAccessVO[]>([])

  /** 当前激活的 tab 类型 */
  const activeTab = ref<AccessType>('upload')

  /** 加载状态 */
  const loading = ref(false)

  /** 是否已初始化 */
  const initialized = ref(false)

  // ============================================================
  // Getters
  // ============================================================

  /** 当前显示的数据 */
  const currentData = computed<RecentAccessVO[]>(() => {
    switch (activeTab.value) {
      case 'upload': return recentUploads.value
      case 'download': return recentDownloads.value
      case 'open': return recentOpens.value
      default: return allRecent.value
    }
  })

  const uploadCount = computed(() => recentUploads.value.length)
  const downloadCount = computed(() => recentDownloads.value.length)
  const openCount = computed(() => recentOpens.value.length)

  // ============================================================
  // Actions
  // ============================================================

  /** 加载指定类型 */
  async function loadByType(type: AccessType, page: number = 1, pageSize: number = 50): Promise<void> {
    loading.value = true
    try {
      let res: { code: number; message: string | null; data: RecentAccessVO[] }
      switch (type) {
        case 'upload':
          res = await getRecentUploadsApi(page, pageSize)
          if (res.code === 200) recentUploads.value = res.data || []
          break
        case 'download':
          res = await getRecentDownloadsApi(page, pageSize)
          if (res.code === 200) recentDownloads.value = res.data || []
          break
        case 'open':
          res = await getRecentOpensApi(page, pageSize)
          if (res.code === 200) recentOpens.value = res.data || []
          break
      }
    } catch (err) {
      console.error('[RecentStore] 加载失败:', err)
    } finally {
      loading.value = false
    }
  }

  /** 加载所有 */
  async function loadAll(page: number = 1, pageSize: number = 50): Promise<void> {
    loading.value = true
    try {
      const [uploadRes, downloadRes, openRes] = await Promise.all([
        getRecentUploadsApi(page, pageSize),
        getRecentDownloadsApi(page, pageSize),
        getRecentOpensApi(page, pageSize),
      ])
      // 解包响应：后端统一返回 { code: 200, message: null, data: [...] }
      if (uploadRes.code === 200) recentUploads.value = uploadRes.data || []
      if (downloadRes.code === 200) recentDownloads.value = downloadRes.data || []
      if (openRes.code === 200) recentOpens.value = openRes.data || []
      initialized.value = true
    } catch (err) {
      console.error('[RecentStore] 加载全部失败:', err)
    } finally {
      loading.value = false
    }
  }

  /** 切换 Tab */
  async function switchTab(type: AccessType): Promise<void> {
    activeTab.value = type
    await loadByType(type)
  }

  /** 重置 */
  function reset(): void {
    recentUploads.value = []
    recentDownloads.value = []
    recentOpens.value = []
    allRecent.value = []
    initialized.value = false
  }

  return {
    // state
    recentUploads,
    recentDownloads,
    recentOpens,
    allRecent,
    activeTab,
    loading,
    initialized,
    // getters
    currentData,
    uploadCount,
    downloadCount,
    openCount,
    // actions
    loadByType,
    loadAll,
    switchTab,
    reset,
  }
})