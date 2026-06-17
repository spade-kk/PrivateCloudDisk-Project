import { defineStore } from 'pinia'
import { deleteTrashTargetApi, getTrashTargetsApi, restoreTrashTargetApi } from '@/api/index'
import { useToastStore } from './toastStore'

export const useTrashStore = defineStore('trash', () => {
  const toastStore = useToastStore()

  async function fetchTrash(params: Record<string, any> = {}): Promise<any[]> {
    try {
      const res = await getTrashTargetsApi(params)
      if (res.code === 200) {
        return res.data || []
      }
      toastStore.showToast(res.message || '回收站加载失败', 'error')
      return []
    } catch (error: any) {
      toastStore.showToast(error.message || '回收站加载失败', 'error')
      return []
    }
  }

  async function restore(trashId: string): Promise<void> {
    const res = await restoreTrashTargetApi(trashId)
    if (res.code !== 200) {
      throw new Error(res.message || '恢复失败')
    }
    toastStore.showToast('已恢复', 'success')
  }

  async function permanentDelete(trashId: string): Promise<void> {
    const res = await deleteTrashTargetApi(trashId)
    if (res.code !== 200) {
      throw new Error(res.message || '彻底删除失败')
    }
    toastStore.showToast('已彻底删除', 'success')
  }

  return { fetchTrash, restore, permanentDelete }
})