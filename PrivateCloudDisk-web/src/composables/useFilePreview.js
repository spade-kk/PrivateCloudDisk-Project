import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { usePreviewStore } from '@/stores/previewStore'

/**
 * 文件预览组合式函数
 * 提供文件预览的便捷方法
 */
export function useFilePreview() {
  const previewStore = usePreviewStore()
  const router = useRouter()

  const isPreviewVisible = ref(false)

  /**
   * 打开文件预览
   * @param {Object} file - 文件对象
   */
  const openPreview = async (file) => {
    isPreviewVisible.value = true
    await previewStore.openFile(file)
  }

  /**
   * 关闭文件预览
   */
  const closePreview = () => {
    isPreviewVisible.value = false
    previewStore.closePreview()
  }

  /**
   * 跳转到预览页面
   * @param {Object} file - 文件对象
   */
  const navigateToPreview = (file) => {
    router.push({
      name: 'FilePreview',
      params: { fileId: file.node_id },
      query: {
        name: encodeURIComponent(file.node_name),
        type: file.node_type
      }
    })
  }

  /**
   * 检查文件是否可预览
   * @param {string} fileName - 文件名
   * @returns {boolean}
   */
  const canPreview = (fileName) => {
    const { isPreviewable } = previewHelper
    return isPreviewable(fileName)
  }

  return {
    isPreviewVisible,
    openPreview,
    closePreview,
    navigateToPreview,
    canPreview
  }
}
