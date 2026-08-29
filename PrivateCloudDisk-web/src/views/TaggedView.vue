<template>
  <div class="space-y-4 sm:space-y-6">
    <!-- 标题栏 -->
    <div class="flex items-center justify-between">
      <div>
        <h1 class="flex items-center gap-2 text-xl font-bold text-neutral-700 sm:text-2xl">
          <span class="flex h-8 w-8 items-center justify-center rounded-lg bg-primary/10">
            <i class="fa fa-tags text-primary"></i>
          </span>
          标签管理
        </h1>
        <CurrentSpaceBadge class="mt-2" />
      </div>
      <button
        class="inline-flex items-center gap-1.5 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white transition hover:bg-primary/90 active:scale-95"
        @click="showCreateDialog = true"
      >
        <i class="fa fa-plus text-xs"></i> 新建标签
      </button>
    </div>

    <!-- 加载中 -->
    <div v-if="tagStore.loading" class="flex justify-center py-16">
      <LoadingSpinner />
    </div>

    <!-- 空状态 -->
    <div v-else-if="tagStore.tags.length === 0" class="rounded-xl bg-white p-8 text-center text-neutral-400 shadow-sm sm:p-10">
      <div class="mb-3 flex justify-center">
        <div class="flex h-16 w-16 items-center justify-center rounded-full bg-neutral-100">
          <i class="fa fa-tags text-3xl text-neutral-300"></i>
        </div>
      </div>
      <p class="text-base font-medium text-neutral-500">暂无标签</p>
      <p class="mt-1 text-sm text-neutral-400">创建标签后可为文件/文件夹分类标记</p>
    </div>

    <!-- 标签列表 -->
    <div v-else class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
      <div
        v-for="tag in tagStore.tags"
        :key="tag.tag_id"
        class="cursor-pointer rounded-xl bg-white p-4 shadow-sm transition-shadow hover:shadow-md"
        @click="viewTag(tag)"
      >
        <div class="mb-3 flex items-center justify-between">
          <span
            class="inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium text-white"
            :style="{ backgroundColor: tag.tag_color }"
          >
            <i class="fa fa-tag"></i>
            {{ tag.tag_name }}
          </span>
          <div class="flex gap-0.5">
            <button
              class="flex h-7 w-7 items-center justify-center rounded-lg text-neutral-400 transition hover:bg-neutral-100 hover:text-primary"
              @click.stop="editTag(tag)"
              title="编辑标签"
            >
              <i class="fa fa-pencil text-xs"></i>
            </button>
            <button
              class="flex h-7 w-7 items-center justify-center rounded-lg text-neutral-400 transition hover:bg-danger/10 hover:text-danger"
              @click.stop="confirmDelete(tag)"
              title="删除标签"
            >
              <i class="fa fa-trash text-xs"></i>
            </button>
          </div>
        </div>
        <div class="flex gap-4 text-xs text-neutral-400">
          <span class="inline-flex items-center gap-1">
            <i class="fa fa-file-o"></i>{{ tag.file_count }} 文件
          </span>
          <span class="inline-flex items-center gap-1">
            <i class="fa fa-folder-o"></i>{{ tag.folder_count }} 文件夹
          </span>
        </div>
      </div>
    </div>

    <!-- 按标签查看文件 -->
    <div v-if="viewingTag" class="space-y-4">
      <div class="flex items-center gap-3">
        <button
          class="inline-flex items-center gap-1 rounded-lg px-3 py-1.5 text-sm text-neutral-500 transition hover:bg-neutral-100 hover:text-neutral-700"
          @click="viewingTag = null"
        >
          <i class="fa fa-arrow-left text-xs"></i> 返回
        </button>
        <span
          class="inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-sm font-medium text-white"
          :style="{ backgroundColor: viewingTag.tag_color }"
        >
          <i class="fa fa-tag"></i>
          {{ viewingTag.tag_name }}
        </span>
      </div>

      <!-- 文件列表 -->
      <div v-if="taggedFiles.length > 0" class="rounded-xl bg-white p-4 shadow-sm">
        <h3 class="mb-3 text-sm font-semibold text-neutral-500">文件</h3>
        <div class="space-y-1">
          <div
            v-for="item in taggedFiles"
            :key="item.target_id"
            class="flex items-center gap-3 rounded-lg px-3 py-2.5 transition-colors hover:bg-neutral-50 cursor-pointer"
            @click="openFile(item)"
          >
            <div class="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-primary/5">
              <FileTypeIcon :file-name="item.target_name" class="text-base" />
            </div>
            <span class="min-w-0 flex-1 truncate text-sm font-medium text-neutral-700">{{ item.target_name }}</span>
            <span class="shrink-0 text-xs text-neutral-400">{{ formatSize(item.target_size) }}</span>
          </div>
        </div>
      </div>

      <!-- 文件夹列表 -->
      <div v-if="taggedFolders.length > 0" class="rounded-xl bg-white p-4 shadow-sm">
        <h3 class="mb-3 text-sm font-semibold text-neutral-500">文件夹</h3>
        <div class="space-y-1">
          <div
            v-for="item in taggedFolders"
            :key="item.target_id"
            class="flex items-center gap-3 rounded-lg px-3 py-2.5 transition-colors hover:bg-neutral-50 cursor-pointer"
            @click="openFolder(item)"
          >
            <div class="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-warning/10">
              <FileTypeIcon :file-name="item.target_name" is-directory class="text-base" />
            </div>
            <span class="min-w-0 flex-1 truncate text-sm font-medium text-neutral-700">{{ item.target_name }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 创建/编辑标签对话框 — 纯 Tailwind 弹出层 -->
    <Teleport to="body">
      <Transition name="modal-fade">
        <div
          v-if="showCreateDialog || editTarget"
          class="fixed inset-0 z-50 flex items-center justify-center p-4"
        >
          <!-- 半透明遮罩 -->
          <div class="absolute inset-0 bg-black/40 backdrop-blur-sm" @click="closeDialog"></div>
          <!-- 对话框卡片 -->
          <div class="relative z-10 w-full max-w-sm rounded-2xl bg-white shadow-2xl">
            <!-- 标题 -->
            <div class="border-b border-neutral-100 px-5 py-4">
              <h3 class="text-lg font-bold text-neutral-700">
                {{ editTarget ? '编辑标签' : '新建标签' }}
              </h3>
            </div>
            <!-- 表单 -->
            <div class="space-y-4 px-5 py-4">
              <div>
                <label class="mb-1.5 block text-xs font-medium text-neutral-500">标签名称</label>
                <input
                  v-model="tagForm.name"
                  type="text"
                  class="w-full rounded-lg border border-neutral-200 px-3 py-2 text-sm text-neutral-700 placeholder-neutral-300 outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/20"
                  placeholder="例如：合同、设计稿、项目A"
                  maxlength="50"
                  @keyup.enter="submitTag"
                />
              </div>
              <div>
                <label class="mb-1.5 block text-xs font-medium text-neutral-500">标签颜色</label>
                <div class="flex flex-wrap gap-2">
                  <button
                    v-for="c in presetColors"
                    :key="c"
                    class="h-8 w-8 rounded-full border-2 transition-all hover:scale-110"
                    :class="tagForm.color === c ? 'scale-110 border-neutral-700 shadow-md' : 'border-transparent'"
                    :style="{ backgroundColor: c }"
                    @click="tagForm.color = c"
                  ></button>
                </div>
                <!-- 【需求十二】保留预设色，同时开放浏览器原生调色盘和可校验的 HEX 输入。 -->
                <div class="mt-3 flex items-center gap-2">
                  <input
                    v-model="tagForm.color"
                    type="color"
                    class="h-10 w-12 cursor-pointer rounded-lg border border-neutral-200 bg-white p-1"
                    aria-label="打开自定义颜色调色盘"
                  />
                  <input
                    :value="tagForm.color"
                    type="text"
                    maxlength="7"
                    class="min-w-0 flex-1 rounded-lg border border-neutral-200 px-3 py-2 font-mono text-sm uppercase text-neutral-700 outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/20"
                    placeholder="#FF5733"
                    @input="updateCustomColor"
                  />
                </div>
                <p v-if="!colorValid" class="mt-1.5 text-xs text-danger">请输入六位 HEX 颜色，例如 #FF5733</p>
              </div>
            </div>
            <!-- 按钮 -->
            <div class="flex justify-end gap-2 border-t border-neutral-100 px-5 py-3">
              <button
                class="rounded-lg px-4 py-2 text-sm text-neutral-500 transition hover:bg-neutral-100 hover:text-neutral-700"
                @click="closeDialog"
              >
                取消
              </button>
              <button
                class="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white transition hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-50"
                :disabled="!tagForm.name || !colorValid"
                @click="submitTag"
              >
                {{ editTarget ? '保存' : '创建' }}
              </button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import { useTagStore } from '@/stores/tagStore'
import { useToastStore } from '@/stores/toastStore'
import FileTypeIcon from '@/components/file/FileTypeIcon.vue'
import type { TagVO, TaggedFileVO } from '@/api/modules/tags'
import { useSpaceStore } from '@/stores/spaceStore'
import CurrentSpaceBadge from '@/components/space/CurrentSpaceBadge.vue'

const tagStore = useTagStore()
const toastStore = useToastStore()
const router = useRouter()
const spaceStore = useSpaceStore()

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
const colorValid = computed(() => /^#[0-9A-Fa-f]{6}$/.test(tagForm.value.color))

function updateCustomColor(event: Event) {
  const value = (event.target as HTMLInputElement).value.trim().toUpperCase()
  tagForm.value.color = value.startsWith('#') ? value : `#${value}`
}

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

onMounted(() => {
  tagStore.loadTags()
})

watch(() => spaceStore.revision, () => {
  viewingTag.value = null
  taggedFiles.value = []
  taggedFolders.value = []
  void tagStore.loadTags()
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
  if (!colorValid.value) {
    toastStore.showToast('标签颜色必须是六位 HEX 格式', 'error')
    return
  }
  tagForm.value.color = tagForm.value.color.toUpperCase()
  if (editTarget.value) {
    const result = await tagStore.editTag(editTarget.value.tag_id, tagForm.value.name, tagForm.value.color)
    if (result) {
      toastStore.showToast('标签已更新', 'success')
      closeDialog()
    } else {
      toastStore.showToast('标签更新失败，请重试', 'error')
    }
  } else {
    const result = await tagStore.createTag(tagForm.value.name, tagForm.value.color)
    if (result) {
      toastStore.showToast('标签已创建', 'success')
      closeDialog()
    } else {
      toastStore.showToast('标签创建失败，请重试', 'error')
    }
  }
}

const confirmDelete = async (tag: TagVO) => {
  if (confirm(`确定要删除标签「${tag.tag_name}」吗？关联的文件标签也将被移除。`)) {
    const ok = await tagStore.removeTag(tag.tag_id)
    if (ok) {
      toastStore.showToast('标签已删除', 'success')
    } else {
      toastStore.showToast('标签删除失败，请重试', 'error')
    }
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

<style scoped>
.modal-fade-enter-active,
.modal-fade-leave-active {
  transition: opacity 0.2s ease;
}
.modal-fade-enter-active > div:last-child,
.modal-fade-leave-active > div:last-child {
  transition: transform 0.2s ease, opacity 0.2s ease;
}
.modal-fade-enter-from,
.modal-fade-leave-to {
  opacity: 0;
}
.modal-fade-enter-from > div:last-child {
  transform: scale(0.95) translateY(8px);
  opacity: 0;
}
.modal-fade-leave-to > div:last-child {
  transform: scale(0.95) translateY(8px);
  opacity: 0;
}
</style>
