import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  getStarredItemsApi,
  addFileStarApi,
  removeFileStarApi,
  addFolderStarApi,
  removeFolderStarApi,
  getStarredFileIdsApi,
  getStarredNodeIdsApi,
  checkFileStarredApi,
  checkFolderStarredApi,
  starredItemToNode,
  type StarredItem,
  type StarredNode,
} from '@/api/modules/stars'

export const useStarredStore = defineStore('starred', () => {
  // ============================================================
  // State
  // ============================================================

  /** 收藏节点列表（已转换为前端格式） */
  const starredNodes = ref<StarredNode[]>([])

  /** 收藏的文件ID集合（用于快速判断收藏状态） */
  const starredFileIds = ref<Set<string>>(new Set())

  /** 收藏的文件夹ID集合（用于快速判断收藏状态） */
  const starredNodeIds = ref<Set<string>>(new Set())

  /** 加载状态 */
  const loading = ref(false)

  /** 是否已初始化（加载过ID集合） */
  const initialized = ref(false)

  // ============================================================
  // Getters
  // ============================================================

  const starredCount = computed(() => starredNodes.value.length)

  /** 所有收藏的ID集合（文件+文件夹，用于 FileGridView 星标显示） */
  const allStarredIds = computed<Set<string>>(() => {
    const combined = new Set<string>(starredFileIds.value)
    for (const id of starredNodeIds.value) {
      combined.add(id)
    }
    return combined
  })

  const isFileStarred = (fileId: string): boolean => starredFileIds.value.has(fileId)

  const isFolderStarred = (nodeId: string): boolean => starredNodeIds.value.has(nodeId)

  /** 通用收藏状态检查（自动判断文件/文件夹） */
  const isStarred = (nodeId: string, nodeType: 'FILE' | 'FOLDER' | string): boolean => {
    if (nodeType === 'FOLDER' || nodeType === 'folder') {
      return starredNodeIds.value.has(nodeId)
    }
    return starredFileIds.value.has(nodeId)
  }

  // ============================================================
  // Actions — 初始化
  // ============================================================

  /**
   * 初始化收藏ID集合（在用户登录后调用一次）
   * 只加载 ID 列表，不加载详情，性能开销小。
   */
  async function initStarredIds(): Promise<void> {
    if (initialized.value) return
    try {
      const [fileIds, nodeIds] = await Promise.all([
        getStarredFileIdsApi(),
        getStarredNodeIdsApi(),
      ])
      starredFileIds.value = new Set(fileIds)
      starredNodeIds.value = new Set(nodeIds)
      initialized.value = true
    } catch (err) {
      console.error('[StarredStore] 初始化收藏ID失败:', err)
    }
  }

  /**
   * 重置收藏状态（登出时调用）
   */
  function reset(): void {
    starredNodes.value = []
    starredFileIds.value = new Set()
    starredNodeIds.value = new Set()
    initialized.value = false
  }

  // ============================================================
  // Actions — 收藏/取消收藏
  // ============================================================

  /**
   * 切换收藏状态（文件或文件夹）
   * @returns 新的收藏状态（true=已收藏, false=已取消）
   */
  async function toggleStar(nodeId: string, nodeType: 'FILE' | 'FOLDER' | string): Promise<boolean> {
    const isFolder = nodeType === 'FOLDER' || nodeType === 'folder'
    const currentlyStarred = isFolder
      ? starredNodeIds.value.has(nodeId)
      : starredFileIds.value.has(nodeId)

    try {
      if (currentlyStarred) {
        // 取消收藏
        if (isFolder) {
          await removeFolderStarApi(nodeId)
          starredNodeIds.value.delete(nodeId)
        } else {
          await removeFileStarApi(nodeId)
          starredFileIds.value.delete(nodeId)
        }
        // 从列表中移除
        starredNodes.value = starredNodes.value.filter(n => n.node_id !== nodeId)
        return false
      } else {
        // 添加收藏
        if (isFolder) {
          await addFolderStarApi(nodeId)
          starredNodeIds.value.add(nodeId)
        } else {
          await addFileStarApi(nodeId)
          starredFileIds.value.add(nodeId)
        }
        return true
      }
    } catch (err) {
      console.error('[StarredStore] 切换收藏失败:', err)
      throw err
    }
  }

  /**
   * 添加文件收藏
   */
  async function addFileStar(fileId: string): Promise<void> {
    await addFileStarApi(fileId)
    starredFileIds.value.add(fileId)
  }

  /**
   * 取消文件收藏
   */
  async function removeFileStar(fileId: string): Promise<void> {
    await removeFileStarApi(fileId)
    starredFileIds.value.delete(fileId)
    starredNodes.value = starredNodes.value.filter(n => n.node_id !== fileId)
  }

  /**
   * 添加文件夹收藏
   */
  async function addFolderStar(nodeId: string): Promise<void> {
    await addFolderStarApi(nodeId)
    starredNodeIds.value.add(nodeId)
  }

  /**
   * 取消文件夹收藏
   */
  async function removeFolderStar(nodeId: string): Promise<void> {
    await removeFolderStarApi(nodeId)
    starredNodeIds.value.delete(nodeId)
    starredNodes.value = starredNodes.value.filter(n => n.node_id !== nodeId)
  }

  // ============================================================
  // Actions — 加载收藏列表
  // ============================================================

  /**
   * 获取收藏列表（含文件/文件夹详情，转换为前端节点格式）
   */
  async function fetchStarredNodes(page: number = 1, pageSize: number = 50): Promise<StarredNode[]> {
    loading.value = true
    try {
      const items: StarredItem[] = await getStarredItemsApi(page, pageSize)
      const nodes = items.map(starredItemToNode)
      starredNodes.value = nodes
      // 同步更新ID集合
      for (const item of items) {
        if (item.target_type === 'folder') {
          starredNodeIds.value.add(item.target_id)
        } else {
          starredFileIds.value.add(item.target_id)
        }
      }
      return nodes
    } catch (err) {
      console.error('[StarredStore] 加载收藏列表失败:', err)
      throw err
    } finally {
      loading.value = false
    }
  }

  return {
    // state
    starredNodes,
    starredFileIds,
    starredNodeIds,
    loading,
    initialized,
    // getters
    starredCount,
    allStarredIds,
    isFileStarred,
    isFolderStarred,
    isStarred,
    // actions
    initStarredIds,
    reset,
    toggleStar,
    addFileStar,
    removeFileStar,
    addFolderStar,
    removeFolderStar,
    fetchStarredNodes,
  }
})