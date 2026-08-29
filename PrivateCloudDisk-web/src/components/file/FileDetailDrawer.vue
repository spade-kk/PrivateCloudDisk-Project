<template>
  <Teleport to="body">
    <div v-if="visible" class="fixed inset-0 z-50 flex">
      <!-- 遮罩层 -->
      <div class="flex-1 bg-black/50" @click="$emit('close')"></div>
      <!-- 抽屉内容 -->
      <div class="animate-slide-in h-full w-[min(100vw,24rem)] overflow-y-auto bg-white shadow-xl">
        <div class="p-4 border-b flex justify-between items-center">
          <h3 class="text-lg font-semibold">文件详情</h3>
          <button @click="$emit('close')" class="text-neutral-400 hover:text-neutral-600">
            <i class="fa fa-times"></i>
          </button>
        </div>
        <div class="p-4 space-y-4">
          <div class="flex justify-center">
            <FileTypeIcon
              :file-name="node?.node_name || ''"
              :is-directory="node?.node_type === 'FOLDER'"
              class="text-6xl"
            />
          </div>
          <div class="space-y-2">
            <div><label class="text-neutral-500 text-sm">文件名</label><p class="font-medium break-all">{{ node?.node_name }}</p></div>
            <div><label class="text-neutral-500 text-sm">类型</label><p>{{ node?.node_type === 'FOLDER' ? '文件夹' : getFileExtension(node?.node_name) }}</p></div>
            <div v-if="node?.node_type !== 'FOLDER'"><label class="text-neutral-500 text-sm">大小</label><p>{{ formatFileSize(node?.node_size) }}</p></div>
            <div><label class="text-neutral-500 text-sm">创建时间</label><p>{{ formatTime(node?.created_at) }}</p></div>
            <div><label class="text-neutral-500 text-sm">修改时间</label><p>{{ formatTime(node?.updated_at) }}</p></div>
            <div><label class="text-neutral-500 text-sm">存储路径</label><p class="text-xs break-all">{{ fullPath }}</p></div>
            <div v-if="node?.md5"><label class="text-neutral-500 text-sm">MD5</label><p class="text-xs font-mono break-all">{{ node?.md5 }}</p></div>
            <div>
              <div class="flex items-center justify-between">
                <label class="text-neutral-500 text-sm">标签</label>
                <button class="text-xs text-primary hover:underline" type="button" @click="tagPickerVisible = true"><i class="fa fa-tags"></i> 管理</button>
              </div>
              <div v-if="currentTags.length" class="mt-2 flex flex-wrap gap-1.5">
                <TagBadge v-for="tag in currentTags" :key="tag.tag_id" :tag="tag" />
              </div>
              <p v-else class="mt-1 text-xs text-neutral-400">暂未添加标签</p>
            </div>
          </div>
          <div class="grid grid-cols-1 gap-2 pt-4 sm:grid-cols-3">
            <button v-if="node?.node_type === 'FILE'" @click="download" class="touch-button rounded-lg bg-primary py-2 text-white">下载</button>
            <button v-if="isVideoFile" @click="playVideo" class="touch-button rounded-lg bg-green-600 py-2 text-white">
              <i class="fa fa-play"></i> 播放
            </button>
            <button @click="copyPath" class="touch-button rounded-lg border border-primary py-2 text-primary">复制路径</button>
            <button v-if="node?.node_type !== 'FOLDER'" @click="showVersionHistory" class="touch-button rounded-lg border border-neutral-300 py-2">版本历史</button>
          </div>
        </div>
      </div>
    </div>
    <TagPickerDialog
      :visible="tagPickerVisible"
      :target-id="node?.node_id || ''"
      :target-type="node?.node_type === 'FOLDER' ? 'folder' : 'file'"
      :target-name="node?.node_name || ''"
      @close="tagPickerVisible = false"
      @updated="loadTags"
    />
  </Teleport>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { formatFileSize, getFileExtension, formatTime } from '@/utils/helpers'
import { useRouter } from 'vue-router'
import FileTypeIcon from './FileTypeIcon.vue'
import TagBadge from '@/components/tag/TagBadge.vue'
import TagPickerDialog from '@/components/tag/TagPickerDialog.vue'
import { useTagStore } from '@/stores/tagStore'
import type { TagVO } from '@/api/modules/tags'

const props = defineProps({
  visible: Boolean,
  node: Object,
  fullPath: String, // 例如 "/我的网盘/文档/report.pdf"
})
const emit = defineEmits(['close', 'download', 'versionHistory'])
const router = useRouter()
const tagStore = useTagStore()
const tagPickerVisible = ref(false)
const currentTags = ref<TagVO[]>([])

async function loadTags() {
  if (!props.node?.node_id) return
  currentTags.value = await tagStore.loadFileTags(
    props.node.node_id,
    props.node.node_type === 'FOLDER' ? 'folder' : 'file',
  )
}

watch(
  () => [props.visible, props.node?.node_id],
  ([visible]) => {
    if (visible) void loadTags()
    else tagPickerVisible.value = false
  },
  { immediate: true },
)

const videoExtensions = ['mp4', 'webm', 'ogg', 'mov', 'avi', 'mkv', 'flv', 'wmv', 'm4v', 'ts', 'm3u8']

const isVideoFile = computed(() => {
  if (props.node?.node_type !== 'FILE') return false
  const ext = getFileExtension(props.node?.node_name || '')?.toLowerCase()
  return videoExtensions.includes(ext)
})

const download = () => emit('download', props.node)
const copyPath = () => {
  navigator.clipboard.writeText(props.fullPath || '')
}

const playVideo = () => {
  if (props.node?.node_id) {
    router.push({ name: 'VideoPlayer', params: { fileId: props.node.node_id }, query: { name: props.node.node_name } })
  }
}

const showVersionHistory = () => router.push(`/version-history/${props.node?.node_id}`)
</script>

<style scoped>
.animate-slide-in {
  animation: slideIn 0.3s ease-out;
}
@keyframes slideIn {
  from { transform: translateX(100%); }
  to { transform: translateX(0); }
}
</style>
