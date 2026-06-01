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
      // 实际接口地址请根据后端调整
      // const res = await getMyUserQuotaInfoApi()
      // if (res.code === 200) {
      //   used.value = res.used
      //   total.value = res.total
      //   percent.value = (used.value / total.value) * 100
      // }
      // 模拟数据
      used.value = 42 * 1024 * 1024 * 1024 // 42GB
      total.value = 100 * 1024 * 1024 * 1024
      percent.value = (used.value / total.value) * 100
    } catch (error) {
      console.error('获取存储信息失败', error)
    }
  }

  return { used, total, percent, fetchStorageInfo, formatUsed: () => formatFileSize(used.value), formatTotal: () => formatFileSize(total.value) }
})