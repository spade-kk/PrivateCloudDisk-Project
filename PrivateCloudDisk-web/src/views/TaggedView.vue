<template>
  <div class="space-y-4 sm:space-y-6">
    <!-- 标题栏 -->
    <div class="flex items-center justify-between">
      <h1 class="text-xl font-bold sm:text-2xl">
        <i class="fa fa-tags text-primary"></i> 标签管理
      </h1>
      <button class="btn btn-primary btn-sm" @click="showCreateDialog = true">
        <i class="fa fa-plus mr-1"></i> 新建标签
      </button>
    </div>

    <!-- 加载中 -->
    <div v-if="loading"><LoadingSpinner /></div>

    <!-- 空状态 -->
    <div v-else-if="tagStore.tags.length === 0" class="responsive-panel p-8 text-center text-neutral-400 sm:p-10">
      <i class="fa fa-tags text-4xl mb-2"></i>
      <p>暂无标签</p>
      <p class="text-xs mt-1">创建标签后可为文件/文件夹分类标记</p>
    </div>

    <!-- 标签列表 -->
    <div v-else class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
      <div
        v-for="tag in tagStore.tags"
        :key="tag.tag_id"
        class="responsive-panel p-4 cursor-pointer hover:shadow-md transition-shadow"
        @click="viewTag(tag)"
      >
        <div class="flex items-center justify-between mb-2">
          <span
            class="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium text-white"
            :style="{ backgroundColor: tag.tag_color }"
          >
            <i class="fa fa-tag"></i>
            {{ tag.tag_name }}
          </span>
          <div class="flex gap-1">
            <button class="text-neutral-400 hover:text-primary" @click.stop="editTag(tag)">
              <i class="fa fa-pencil"></i>
            </button>
            <button class="text-neutral-400 hover:text-error" @click.stop="confirmDelete(tag)">
              <i class="fa fa-trash"></i>
            </button>
          </div>
        </div>
        <div class="flex gap-4 text-xs text-neutral-400">
          <span><i class="fa fa-file mr-1"></i>{{ tag.file_count }} 文件</span>
          <span><i class="fa fa-folder mr-1"></i>{{ tag.folder_count }} 文件夹</span>
        </div>
      </div>
    </div>

    <!-- 按标签查看文件 -->
    <div v-if="viewingTag" class="space-y-4">
      <div class="flex items-center gap-3">
        <button class="btn btn-ghost btn-sm" @click="viewingTag = null">
          <i class="fa fa-arrow-left mr-1"></i> 返回
        </button>
        <span
          class="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-sm font-medium text-white"
          :style="{ backgroundColor: viewingTag.tag_color }"
        >
          <i class="fa fa-tag"></i>
          {{ viewingTag.tag_name }}
        </span>
      </div>

      <!-- 文件列表 -->
      <div v-if="taggedFiles.length > 0">
        <h3 class="text-sm font-semibold text-neutral-500 mb-2">文件</h3>
        <div class="space-y-2">
          <div
            v-for="item in taggedFiles"
            :key="item.target_id"
            class="flex items-center gap-3 p-3 rounded-lg bg-base-200 hover:bg-base-300 cursor-pointer"
            @click="openFile(item)"
          >
            <i :class="getFileIconClass(item.target_name)" class="text-lg"></i>
            <span class="flex-1 text-sm truncate">{{ item.target_name }}</span>
            <span class="text-xs text-neutral-400">{{ formatSize(item.target_size) }}</span>
          </div>
        </div>
      </div>

      <!-- 文件夹列表 -->
      <div v-if="taggedFolders.length > 0">
        <h3 class="text-sm font-semibold text-neutral-500 mb-2 mt-4">文件夹</h3>
        <div class="space-y-2">
          <div
            v-for="item in taggedFolders"
            :key="item.target_id"
            class="flex items-center gap-3 p-3 rounded-lg bg-base-200 hover:bg-base-300 cursor-pointer"
            @click="openFolder(item)"
          >
            <i class="fa fa-folder text-warning text-lg"></i>
            <span class="flex-1 text-sm truncate">{{ item.target_name }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 创建/编辑标签对话框 -->
    <Teleport to="body">
      <div v-if="showCreateDialog || editTarget" class="modal modal-open">
        <div class="modal-box max-w-sm">
          <h3 class="text-lg font-bold mb-4">{{ editTarget ? '编辑标签' : '新建标签' }}</h3>
          <div class="space-y-3">
            <div>
              <label class="label text-xs">标签名称</label>
              <input
                v-model="tagForm.name"
                type="text"
                class="input input-bordered w-full"
                placeholder="例如：合同、设计稿、项目A"
                maxlength="50"
              />
            </div>
            <div>
              <label class="label text-xs">标签颜色</label>
              <div class="flex gap-2 flex-wrap">
                <button
                  v-for="c in presetColors"
                  :key="c"
                  class="w-8 h-8 rounded-full border-2 transition-transform"
                  :class="tagForm.color === c ? 'scale-110 border-neutral-content' : 'border-transparent'"
                  :style="{ backgroundColor: c }"
                  @click="tagForm.color = c"
                ></button>
              </div>
            </div>
          </div>
          <div class="modal-action">
            <button class="btn btn-ghost btn-sm" @click="closeDialog">取消</button>
            <button class="btn btn-primary btn-sm" :disabled="!tagForm.name" @click="submitTag">
              {{ editTarget ? '保存' : '创建' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import { useTagStore } from '@/stores/tagStore'
import { useToastStore } from '@/stores/toastStore'
import { getFileIconClass } from '@/utils/fileIcon'
import type { TagVO, TaggedFileVO } from '@/api/modules/tags'

const tagStore = useTagStore()
const toastStore = useToastStore()
const router = useRouter()

const loading = ref(false)
const showCreateDialog = ref(false)
const editTarget = ref<TagVO | null>(null)
const viewingTag = ref<TagVO | null>(null)
const taggedFiles = ref<TaggedFileVO[]>([])
const taggedFolders = ref<TaggedFileVO[]>([])

const tagForm = ref({ name: '', color: '#3B82F6' })

const presetColors = [
  '#3B82F6', '#EF4444', '#10B981', '#F59E0B', '#8B5CF6',
  '#EC4899', '#06B6D4', '#F97316', '#6366F1', '#14B8A6',
]

/** 格式化文件大小 */
const formatSize = (bytes: number): string => {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
  return (bytes / (1024 * 1024 * 1024)).toFixed(2) + ' GB'
}

// ============================================================
// 加载
// ============================================================

onMounted(async () => {
  loading.value = true
  await tagStore.loadTags()
  loading.value = false
})

// ============================================================
// 标签 CRUD
// ============================================================

const editTag = (tag: TagVO) => {
  editTarget.value = tag
  tagForm.value = { name: tag.tag_name, color: tag.tag_color }
}

const closeDialog = () => {
  showCreateDialog.value = false
  editTarget.value = null
  tagForm.value = { name: '', color: '#3B82F6' }
}

const submitTag = async () => {
  if (editTarget.value) {
    await tagStore.editTag(editTarget.value.tag_id, tagForm.value.name, tagForm.value.color)
    toastStore.showToast('标签已更新', 'success')
  } else {
    await tagStore.createTag(tagForm.value.name, tagForm.value.color)
    toastStore.showToast('标签已创建', 'success')
  }
  closeDialog()
}

const confirmDelete = async (tag: TagVO) => {
  if (confirm(`确定要删除标签「${tag.tag_name}」吗？关联的文件标签也将被移除。`)) {
    await tagStore.removeTag(tag.tag_id)
    toastStore.showToast('标签已删除', 'success')
  }
}

// ============================================================
// 按标签查看
// ============================================================

const viewTag = async (tag: TagVO) => {
  viewingTag.value = tag
  const [files, folders] = await Promise.all([
    tagStore.loadFilesByTag(tag.tag_id),
    tagStore.loadFoldersByTag(tag.tag_id),
  ])
  taggedFiles.value = files
  taggedFolders.value = folders
}

const openFile = (item: TaggedFileVO) => {
  router.push(`/app/preview/${item.target_id}`)
}

const openFolder = (item: TaggedFileVO) => {
  router.push(`/app?folder=${item.target_id}`)
}
</script>