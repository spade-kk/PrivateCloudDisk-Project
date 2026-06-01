<template>
  <Teleport to="body">
    <div v-if="visible" class="fixed inset-0 bg-black/80 z-50 flex items-center justify-center p-4" @click.self="$emit('close')">
      <div class="relative bg-white rounded-lg max-w-5xl w-full max-h-screen overflow-auto">
        <div class="sticky top-0 bg-white p-2 border-b flex justify-between items-center">
          <span class="font-medium truncate">{{ node?.node_name }}</span>
          <button @click="$emit('close')" class="text-neutral-500 hover:text-neutral-700"><i class="fa fa-times text-xl"></i></button>
        </div>
        <div class="p-4 flex justify-center items-center min-h-[300px]">
          <!-- 图片预览 -->
          <img v-if="isImage" :src="fileUrl" class="max-w-full max-h-[70vh] object-contain" />
          <!-- PDF 预览 -->
          <iframe v-else-if="isPdf" :src="fileUrl" class="w-full h-[70vh]" />
          <!-- 文本预览 -->
          <pre v-else-if="isText" class="whitespace-pre-wrap w-full overflow-auto max-h-[70vh] bg-neutral-50 p-4 rounded">{{ textContent }}</pre>
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