// ============================================================
// useFilePreview.ts — 文件预览组合式函数 (Composable)
// ============================================================
// 提供文件预览的便捷方法，封装预览状态管理、预览 Store 交互、
// 路由导航等逻辑，减少组件中的重复代码。
//
// 用法：
//   const { openPreview, closePreview, navigateToPreview, canPreview } = useFilePreview()
//
// Office 文档（PDF/Word/Excel/PPT）会跳转至独立的预览页面，
// 与视频播放器 (VideoPlayerView.vue) 的跳转模式一致。
// ============================================================

import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { usePreviewStore } from '@/stores/previewStore'
import { isPreviewable } from '@/utils/previewHelper'

/** 根据文件扩展名获取 Office 文档预览路由名称 */
function getOfficePreviewRouteName(fileName: string): string | null {
  const ext = fileName?.split('.').pop()?.toLowerCase() || ''
  if (ext === 'pdf') return 'PDFPreview'
  if (['doc', 'docx'].includes(ext)) return 'WordPreview'
  if (['xls', 'xlsx'].includes(ext)) return 'ExcelPreview'
  if (['ppt', 'pptx', 'pptm'].includes(ext)) return 'PPTPreview'
  return null
}

/** 根据文件扩展名获取压缩包预览路由名称 */
function getArchivePreviewRouteName(fileName: string): string | null {
  const ext = fileName?.split('.').pop()?.toLowerCase() || ''
  if (['zip', 'rar', '7z', 'tar', 'gz', 'bz2', 'xz', 'tgz', 'iso'].includes(ext)) {
    return 'ArchivePreview'
  }
  return null
}

/** 检查是否为 Office 文档（需要跳转至独立预览页面） */
function isOfficeDocument(fileName: string): boolean {
  return getOfficePreviewRouteName(fileName) !== null
}

/** 检查是否为压缩包文件（需要跳转至独立预览页面） */
function isArchiveDocument(fileName: string): boolean {
  return getArchivePreviewRouteName(fileName) !== null
}

/**
 * 文件预览组合式函数
 *
 * 提供文件预览的统一入口，封装：
 * - 预览面板的显示/隐藏状态
 * - 调用 previewStore 打开/关闭预览
 * - 跳转到独立预览页面（PDF/Word/Excel/PPT 使用独立页面）
 * - 检查文件是否可预览
 *
 * @returns { isPreviewVisible, openPreview, closePreview, navigateToPreview, canPreview, isOfficeDocument }
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
   * Office 文档（PDF/Word/Excel/PPT）跳转至专属预览页面，
   * 其他文件类型跳转至通用的 FilePreview 页面。
   *
   * @param file - 文件对象，需包含 node_id、node_name、node_type
   */
  const navigateToPreview = (file: any) => {
    const officeRouteName = getOfficePreviewRouteName(file.node_name)
    const archiveRouteName = getArchivePreviewRouteName(file.node_name)
    if (officeRouteName) {
      // Office 文档：跳转至独立预览页面
      router.push({
        name: officeRouteName,
        params: { fileId: file.node_id },
        query: { name: encodeURIComponent(file.node_name) },
      })
    } else if (archiveRouteName) {
      // 压缩包文件：跳转至独立预览页面
      router.push({
        name: archiveRouteName,
        params: { fileId: file.node_id },
        query: { name: encodeURIComponent(file.node_name) },
      })
    } else {
      // 其他文件类型：跳转至通用预览页面
      router.push({
        name: 'FilePreview',
        params: { fileId: file.node_id },
        query: {
          name: encodeURIComponent(file.node_name),
          type: file.node_type,
        },
      })
    }
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
    isOfficeDocument,
    isArchiveDocument,
    getOfficePreviewRouteName,
    getArchivePreviewRouteName,
  }
}