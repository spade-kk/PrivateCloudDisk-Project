import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  getUserTagsApi,
  createTagApi,
  updateTagApi,
  deleteTagApi,
  tagFileApi,
  untagFileApi,
  getFileTagsApi,
  getFilesByTagApi,
  getFoldersByTagApi,
  type TagVO,
  type TaggedFileVO,
  type FileTagRequest,
} from '@/api/modules/tags'

export const useTagStore = defineStore('tag', () => {
  // ============================================================
  // State
  // ============================================================

  /** 用户的所有标签 */
  const tags = ref<TagVO[]>([])

  /** 当前选中文件的标签（用于标签管理弹窗） */
  const currentFileTags = ref<TagVO[]>([])

  /** 按标签查询的文件列表 */
  const taggedFiles = ref<TaggedFileVO[]>([])

  /** 按标签查询的文件夹列表 */
  const taggedFolders = ref<TaggedFileVO[]>([])

  /** 加载状态 */
  const loading = ref(false)

  /** 是否已初始化 */
  const initialized = ref(false)

  // ============================================================
  // Getters
  // ============================================================

  const tagCount = computed(() => tags.value.length)

  /** 按名称查找标签 */
  const getTagByName = (name: string): TagVO | undefined =>
    tags.value.find(t => t.tag_name === name)

  /** 按ID查找标签 */
  const getTagById = (id: number): TagVO | undefined =>
    tags.value.find(t => t.tag_id === id)

  // ============================================================
  // Actions — 初始化
  // ============================================================

  /** 加载用户所有标签 */
  async function loadTags(): Promise<void> {
    if (loading.value) return
    loading.value = true
    try {
      tags.value = await getUserTagsApi()
      initialized.value = true
    } catch (err) {
      console.error('[TagStore] 加载标签失败:', err)
    } finally {
      loading.value = false
    }
  }

  /** 重置（登出时调用） */
  function reset(): void {
    tags.value = []
    currentFileTags.value = []
    taggedFiles.value = []
    taggedFolders.value = []
    initialized.value = false
  }

  // ============================================================
  // Actions — 标签 CRUD
  // ============================================================

  /** 创建标签 */
  async function createTag(name: string, color: string = '#3B82F6'): Promise<TagVO | null> {
    try {
      const tag = await createTagApi(name, color)
      tags.value.push(tag)
      return tag
    } catch (err) {
      console.error('[TagStore] 创建标签失败:', err)
      return null
    }
  }

  /** 更新标签 */
  async function editTag(tag_id: number, name: string, color: string): Promise<TagVO | null> {
    try {
      const tag = await updateTagApi(tag_id, name, color)
      const idx = tags.value.findIndex(t => t.tag_id === tag_id)
      if (idx !== -1) tags.value[idx] = tag
      return tag
    } catch (err) {
      console.error('[TagStore] 更新标签失败:', err)
      return null
    }
  }

  /** 删除标签 */
  async function removeTag(tag_id: number): Promise<boolean> {
    try {
      await deleteTagApi(tag_id)
      tags.value = tags.value.filter(t => t.tag_id !== tag_id)
      return true
    } catch (err) {
      console.error('[TagStore] 删除标签失败:', err)
      return false
    }
  }

  // ============================================================
  // Actions — 文件标签关联
  // ============================================================

  /** 为文件/文件夹打标签 */
  async function addTagsToFile(target_id: string, target_type: 'file' | 'folder', tag_ids: number[]): Promise<boolean> {
    try {
      await tagFileApi({ target_id, target_type, tag_ids })
      return true
    } catch (err) {
      console.error('[TagStore] 打标签失败:', err)
      return false
    }
  }

  /** 移除文件/文件夹的标签 */
  async function removeTagFromFile(target_id: string, target_type: 'file' | 'folder', tag_id: number): Promise<boolean> {
    try {
      await untagFileApi(tag_id, target_type, target_id)
      currentFileTags.value = currentFileTags.value.filter(t => t.tag_id !== tag_id)
      return true
    } catch (err) {
      console.error('[TagStore] 移除标签失败:', err)
      return false
    }
  }

  /** 加载文件/文件夹的标签 */
  async function loadFileTags(target_id: string, target_type: 'file' | 'folder'): Promise<TagVO[]> {
    try {
      currentFileTags.value = await getFileTagsApi(target_id, target_type)
      return currentFileTags.value
    } catch (err) {
      console.error('[TagStore] 加载文件标签失败:', err)
      return []
    }
  }

  // ============================================================
  // Actions — 按标签查询
  // ============================================================

  /** 按标签加载文件 */
  async function loadFilesByTag(tag_id: number, page: number = 1, pageSize: number = 50): Promise<TaggedFileVO[]> {
    loading.value = true
    try {
      taggedFiles.value = await getFilesByTagApi(tag_id, page, pageSize)
      return taggedFiles.value
    } catch (err) {
      console.error('[TagStore] 按标签查询文件失败:', err)
      return []
    } finally {
      loading.value = false
    }
  }

  /** 按标签加载文件夹 */
  async function loadFoldersByTag(tag_id: number, page: number = 1, pageSize: number = 50): Promise<TaggedFileVO[]> {
    loading.value = true
    try {
      taggedFolders.value = await getFoldersByTagApi(tag_id, page, pageSize)
      return taggedFolders.value
    } catch (err) {
      console.error('[TagStore] 按标签查询文件夹失败:', err)
      return []
    } finally {
      loading.value = false
    }
  }

  return {
    // state
    tags,
    currentFileTags,
    taggedFiles,
    taggedFolders,
    loading,
    initialized,
    // getters
    tagCount,
    getTagByName,
    getTagById,
    // actions
    loadTags,
    reset,
    createTag,
    editTag,
    removeTag,
    addTagsToFile,
    removeTagFromFile,
    loadFileTags,
    loadFilesByTag,
    loadFoldersByTag,
  }
})