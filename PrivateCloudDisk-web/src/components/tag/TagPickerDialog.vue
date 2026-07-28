<template>
  <Teleport to="body">
    <Transition name="modal-fade">
      <div
        v-if="visible"
        class="fixed inset-0 z-50 flex items-center justify-center p-4"
      >
        <!-- 半透明遮罩 -->
        <div class="absolute inset-0 bg-black/40 backdrop-blur-sm" @click="close"></div>
        <!-- 对话框卡片 -->
        <div class="relative z-10 w-full max-w-sm rounded-2xl bg-white shadow-2xl">
          <!-- 标题 -->
          <div class="border-b border-neutral-100 px-5 py-4">
            <h3 class="flex items-center text-lg font-bold text-neutral-700">
              <i class="fa fa-tags mr-2 text-primary"></i> 标签管理
              <span class="ml-2 truncate text-sm font-normal text-neutral-400">— {{ targetName }}</span>
            </h3>
          </div>

          <!-- 内容 -->
          <div class="space-y-4 px-5 py-4">
            <!-- 已打标签 -->
            <div v-if="currentTags.length > 0">
              <label class="mb-1.5 block text-xs font-medium text-neutral-400">已添加标签</label>
              <div class="flex flex-wrap gap-1.5">
                <span
                  v-for="tag in currentTags"
                  :key="tag.tag_id"
                  class="inline-flex cursor-pointer items-center gap-1 rounded-full px-2.5 py-1 text-xs font-medium text-white transition hover:opacity-80"
                  :style="{ backgroundColor: tag.tag_color }"
                  @click="removeTag(tag)"
                >
                  {{ tag.tag_name }}
                  <i class="fa fa-times text-[10px]"></i>
                </span>
              </div>
            </div>

            <!-- 可选标签 -->
            <div>
              <div class="mb-1.5 flex items-center justify-between">
                <label class="text-xs font-medium text-neutral-400">可选标签</label>
                <span class="cursor-pointer text-xs text-primary hover:underline" @click="showCreate = !showCreate">
                  <i class="fa fa-plus text-[10px]"></i> 新建
                </span>
              </div>

              <!-- 新建标签 -->
              <div v-if="showCreate" class="mb-2 flex gap-2">
                <input
                  v-model="newTagName"
                  type="text"
                  class="flex-1 rounded-lg border border-neutral-200 px-3 py-1.5 text-sm text-neutral-700 placeholder-neutral-300 outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/20"
                  placeholder="标签名"
                  maxlength="50"
                  @keyup.enter="createNewTag"
                />
                <button
                  class="rounded-lg bg-primary px-3 py-1.5 text-sm font-medium text-white transition hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-50"
                  :disabled="!newTagName"
                  @click="createNewTag"
                >
                  创建
                </button>
              </div>

              <div v-if="allTags.length === 0" class="py-2 text-xs text-neutral-400">
                暂无标签，请先创建标签
              </div>
              <div v-else class="flex max-h-32 flex-wrap gap-1.5 overflow-y-auto">
                <span
                  v-for="tag in availableTags"
                  :key="tag.tag_id"
                  class="inline-flex cursor-pointer items-center gap-1 rounded-full border px-2.5 py-1 text-xs font-medium transition hover:opacity-80"
                  :style="{
                    borderColor: tag.tag_color,
                    color: tag.tag_color,
                  }"
                  @click="addTag(tag)"
                >
                  <i class="fa fa-plus text-[10px]"></i>
                  {{ tag.tag_name }}
                </span>
              </div>
            </div>
          </div>

          <!-- 底部按钮 -->
          <div class="flex justify-end border-t border-neutral-100 px-5 py-3">
            <button
              class="rounded-lg px-4 py-2 text-sm text-neutral-500 transition hover:bg-neutral-100 hover:text-neutral-700"
              @click="close"
            >
              关闭
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useTagStore } from '@/stores/tagStore'
import { useToastStore } from '@/stores/toastStore'
import type { TagVO } from '@/api/modules/tags'

const props = defineProps<{
  visible: boolean
  targetId: string
  targetType: 'file' | 'folder'
  targetName: string
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'updated'): void
}>()

const tagStore = useTagStore()
const toastStore = useToastStore()

const currentTags = ref<TagVO[]>([])
const showCreate = ref(false)
const newTagName = ref('')

const allTags = computed(() => tagStore.tags)
const currentTagIds = computed(() => new Set(currentTags.value.map(t => t.tag_id)))

/** 可添加的标签（排除已添加的） */
const availableTags = computed(() =>
  allTags.value.filter(t => !currentTagIds.value.has(t.tag_id))
)

// ============================================================
// 加载文件标签
// ============================================================

watch(
  () => [props.visible, props.targetId],
  async ([visible]) => {
    if (visible && props.targetId) {
      await tagStore.loadTags()
      currentTags.value = await tagStore.loadFileTags(props.targetId, props.targetType)
    }
  },
  { immediate: true }
)

// ============================================================
// 操作
// ============================================================

const addTag = async (tag: TagVO) => {
  const ok = await tagStore.addTagsToFile(props.targetId, props.targetType, [tag.tag_id])
  if (ok) {
    currentTags.value.push(tag)
    emit('updated')
  }
}

const removeTag = async (tag: TagVO) => {
  const ok = await tagStore.removeTagFromFile(props.targetId, props.targetType, tag.tag_id)
  if (ok) {
    currentTags.value = currentTags.value.filter(t => t.tag_id !== tag.tag_id)
    emit('updated')
  }
}

const createNewTag = async () => {
  if (!newTagName.value.trim()) return
  const tag = await tagStore.createTag(newTagName.value.trim())
  if (tag) {
    newTagName.value = ''
    showCreate.value = false
    await addTag(tag)
  }
}

const close = () => {
  showCreate.value = false
  newTagName.value = ''
  emit('close')
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