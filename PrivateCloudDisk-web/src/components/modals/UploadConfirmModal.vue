<template>
  <div v-if="visible" class="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4" @click.self="$emit('close')">
    <div class="fade-in w-full max-w-sm rounded-xl bg-white p-5 shadow-lg sm:p-6">
      <div class="flex justify-between items-center mb-4">
        <h2 class="text-lg font-bold text-neutral-700">确认上传</h2>
        <button @click="$emit('close')" class="text-neutral-400 hover:text-neutral-700"><i class="fa fa-times"></i></button>
      </div>
      <div class="mb-4 space-y-2">
        <div class="flex min-w-0 items-center gap-2">
          <FileTypeIcon :file-name="file?.name || ''" class="text-xl shrink-0" />
          <span class="font-medium text-neutral-700 truncate">{{ file?.name }}</span>
        </div>
        <div class="text-sm text-neutral-500">大小：{{ formatFileSize(file?.size || 0) }}</div>
      </div>
      <div class="grid grid-cols-2 gap-3 sm:flex sm:justify-end">
        <button @click="$emit('close')" class="touch-button rounded-lg border border-neutral-200 px-4 py-2 hover:bg-neutral-50">取消</button>
        <button @click="$emit('confirm')" class="touch-button flex items-center justify-center gap-1 rounded-lg bg-success px-4 py-2 text-white hover:bg-success/90">
          <i class="fa fa-upload"></i><span>确认上传</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { formatFileSize } from '@/utils/helpers'
import FileTypeIcon from '@/components/file/FileTypeIcon.vue'

const props = defineProps(['visible', 'file'])
defineEmits(['close', 'confirm'])

</script>
