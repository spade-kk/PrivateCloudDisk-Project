import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getMyUserQuotaInfoApi } from '@/api/index'
import { formatFileSize } from '@/utils/helpers'

export const useStorageStore = defineStore('storage', () => {
  const used = ref(0)
  const total = ref(100 * 1024 * 1024 * 1024) // 默认 100GB
  const percent = ref(0)

  async function fetchStorageInfo() {
    try {
      const res = await getMyUserQuotaInfoApi()
      if (res.code === 200 && res.data) {
        used.value = res.data.used_capacity || 0
        total.value = res.data.total_capacity || total.value
        percent.value = total.value ? Math.min(100, (used.value / total.value) * 100) : 0
      }
    } catch (error) {
      console.error('获取存储信息失败', error)
    }
  }

  return { used, total, percent, fetchStorageInfo, formatUsed: () => formatFileSize(used.value), formatTotal: () => formatFileSize(total.value) }
})
