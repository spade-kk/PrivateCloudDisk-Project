<template>
  <Teleport to="body">
    <div v-if="visible" class="fixed inset-0 z-50 flex items-center justify-center bg-black/80 p-3 sm:p-4" @click.self="$emit('close')">
      <div class="relative max-h-[calc(100dvh-1.5rem)] w-full max-w-5xl overflow-auto rounded-lg bg-white">
        <div class="sticky top-0 flex items-center justify-between gap-3 border-b bg-white p-3">
          <span class="font-medium truncate">{{ node?.node_name }}</span>
          <button @click="$emit('close')" class="text-neutral-500 hover:text-neutral-700"><i class="fa fa-times text-xl"></i></button>
        </div>
        <div class="flex min-h-[240px] items-center justify-center p-3 sm:min-h-[300px] sm:p-4">
          <!-- 图片预览 -->
          <img v-if="isImage" :src="fileUrl" class="max-w-full max-h-[70vh] object-contain" />
          <!-- PDF 预览 -->
          <iframe v-else-if="isPdf" :src="fileUrl" class="h-[70dvh] w-full" />
          <!-- 文本预览 -->
          <pre v-else-if="isText" class="max-h-[70dvh] w-full overflow-auto whitespace-pre-wrap rounded bg-neutral-50 p-3 text-sm sm:p-4">{{ textContent }}</pre>
          <!-- 视频预览 -->
          <video v-else-if="isVideo" controls class="w-full max-h-[70vh]" :src="fileUrl" />
          <!-- 音频预览 -->
          <audio v-else-if="isAudio" controls class="w-full" :src="fileUrl" />
          <!-- 不支持的类型 -->
          <div v-else class="text-center text-neutral-500">
            <i class="fa fa-file-o text-6xl mb-4 block"></i>
            <p>暂不支持预览此文件类型</p>
            <button @click="download" class="mt-4 bg-primary text-white px-4 py-2 rounded-lg">下载文件</button>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { ref, computed, watch } from 'vue'

const props = defineProps({
  visible: Boolean,
  node: Object,
})
const emit = defineEmits(['close', 'download'])

const fileUrl = ref('')
const textContent = ref('')

const ext = computed(() => props.node?.node_name?.split('.').pop()?.toLowerCase() || '')
const isImage = computed(() => ['jpg','jpeg','png','gif','webp','bmp','svg'].includes(ext.value))
const isPdf = computed(() => ext.value === 'pdf')
const isText = computed(() => ['txt','md','js','css','html','json','xml','log'].includes(ext.value))
const isVideo = computed(() => ['mp4','webm','ogg','mov','avi','mkv'].includes(ext.value))
const isAudio = computed(() => ['mp3','wav','ogg','flac','m4a'].includes(ext.value))

const download = () => emit('download', props.node)

watch(() => props.node, async (node) => {
  if (!node || node.node_type === 'FOLDER') return
  // 获取预览URL（需后端支持返回文件流或公开URL）
  fileUrl.value = `/api/files/preview/${node.node_id}`
  if (isText.value) {
    try {
    //   const res = await client.get(`/v1/files/content/${node.node_id}`, { responseType: 'text' })
      textContent.value = res.data
    } catch (err) {
      textContent.value = '无法加载文本内容'
    }
  }
}, { immediate: true })
</script>
