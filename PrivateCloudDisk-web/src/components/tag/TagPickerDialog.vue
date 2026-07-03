<template>
  <Teleport to="body">
    <div v-if="visible" class="modal modal-open">
      <div class="modal-box max-w-sm">
        <h3 class="text-lg font-bold mb-4">
          <i class="fa fa-tags mr-2"></i> 标签管理
          <span class="text-sm font-normal text-neutral-400 ml-2">— {{ targetName }}</span>
        </h3>

        <!-- 已打标签 -->
        <div v-if="currentTags.length > 0" class="mb-3">
          <label class="label text-xs text-neutral-400">已添加标签</label>
          <div class="flex flex-wrap gap-1.5">
            <span
              v-for="tag in currentTags"
              :key="tag.tag_id"
              class="inline-flex items-center gap-1 px-2 py-1 rounded text-xs font-medium text-white cursor-pointer hover:opacity-80"
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
          <label class="label text-xs text-neutral-400">
            可选标签
            <span class="cursor-pointer text-primary hover:underline" @click="showCreate = !showCreate">
              <i class="fa fa-plus text-[10px]"></i> 新建
            </span>
          </label>

          <!-- 新建标签 -->
          <div v-if="showCreate" class="flex gap-2 mb-2">
            <input
              v-model="newTagName"
              type="text"
              class="input input-bordered input-sm flex-1"
              placeholder="标签名"
              maxlength="50"
              @keyup.enter="createNewTag"
            />
            <button class="btn btn-primary btn-sm" :disabled="!newTagName" @click="createNewTag">
              创建
            </button>
          </div>

          <div v-if="allTags.length === 0" class="text-xs text-neutral-400 py-2">
            暂无标签，请先创建标签
          </div>
          <div v-else class="flex flex-wrap gap-1.5 max-h-32 overflow-y-auto">
            <span
              v-for="tag in availableTags"
              :key="tag.tag_id"
              class="inline-flex items-center gap-1 px-2 py-1 rounded text-xs font-medium border cursor-pointer hover:opacity-80 transition-opacity"
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

        <div class="modal-action">
          <button class="btn btn-ghost btn-sm" @click="close">关闭</button>
        </div>
      </div>
    </div>
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