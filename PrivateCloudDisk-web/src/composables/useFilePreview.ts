// ============================================================
// useFilePreview.ts — 文件预览组合式函数 (Composable)
// ============================================================
// 提供文件预览的便捷方法，封装预览状态管理、预览 Store 交互、
// 路由导航等逻辑，减少组件中的重复代码。
//
// 用法：
//   const { openPreview, closePreview, canPreview } = useFilePreview()
// ============================================================

import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { usePreviewStore } from '@/stores/previewStore'
import { isPreviewable } from '@/utils/previewHelper'

/**
 * 文件预览组合式函数
 *
 * 提供文件预览的统一入口，封装：
 * - 预览面板的显示/隐藏状态
 * - 调用 previewStore 打开/关闭预览
 * - 跳转到独立预览页面（FilePreview 路由）
 * - 检查文件是否可预览
 *
 * @returns { isPreviewVisible, openPreview, closePreview, navigateToPreview, canPreview }
 */
export function useFilePreview() {
  const previewStore = usePreviewStore()
  const router = useRouter()

  /** 预览面板是否可见 */
  const isPreviewVisible = ref(false)

  /**
   * 打开文件预览面板
   *
   * 设置预览面板可见并调用 previewStore 加载文件预览数据。
   *
   * @param file - 文件对象，需包含 node_id、node_name、node_type 等字段
   */
  const openPreview = async (file: any) => {
    isPreviewVisible.value = true
    await previewStore.openFile(file)
  }

  /**
   * 关闭文件预览面板
   *
   * 隐藏预览面板并清理 previewStore 中的预览状态。
   */
  const closePreview = () => {
    isPreviewVisible.value = false
    previewStore.closePreview()
  }

  /**
   * 跳转到独立预览页面
   *
   * 通过路由导航到 FilePreview 页面，传递文件 ID 和基本信息。
   * 适用于需要全屏预览的场景（如大图、视频播放）。
   *
   * @param file - 文件对象，需包含 node_id、node_name、node_type
   */
  const navigateToPreview = (file: any) => {
    router.push({
      name: 'FilePreview',
      params: { fileId: file.node_id },
      query: {
        name: encodeURIComponent(file.node_name),
        type: file.node_type,
      },
    })
  }

  /**
   * 检查文件是否可预览
   *
   * 委托给 previewHelper.isPreviewable 进行文件类型判断。
   *
   * @param fileName - 文件名
   * @returns 是否可预览
   */
  const canPreview = (fileName: string): boolean => {
    return isPreviewable(fileName)
  }

  return {
    isPreviewVisible,
    openPreview,
    closePreview,
    navigateToPreview,
    canPreview,
  }
}