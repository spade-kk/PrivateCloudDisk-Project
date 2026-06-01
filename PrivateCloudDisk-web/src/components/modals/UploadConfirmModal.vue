<template>
  <div v-if="visible" class="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4" @click.self="$emit('close')">
    <div class="bg-white rounded-xl shadow-lg w-full max-w-sm p-6 fade-in">
      <div class="flex justify-between items-center mb-4">
        <h2 class="text-lg font-bold text-neutral-700">确认上传</h2>
        <button @click="$emit('close')" class="text-neutral-400 hover:text-neutral-700"><i class="fa fa-times"></i></button>
      </div>
      <div class="mb-4 space-y-2">
        <div class="flex items-center space-x-2">
          <i :class="['fa', iconClass, 'text-xl']"></i>
          <span class="font-medium text-neutral-700 truncate">{{ file?.name }}</span>
        </div>
        <div class="text-sm text-neutral-500">大小：{{ formatFileSize(file?.size || 0) }}</div>
      </div>
      <div class="flex justify-end space-x-3">
        <button @click="$emit('close')" class="px-4 py-2 border border-neutral-200 rounded-lg hover:bg-neutral-50">取消</button>
        <button @click="$emit('confirm')" class="px-4 py-2 bg-success hover:bg-success/90 text-white rounded-lg flex items-center space-x-1">
          <i class="fa fa-upload"></i><span>确认上传</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { formatFileSize } from '@/utils/helpers'
import { getFileIconClass } from '@/utils/fileIcon'

const props = defineProps(['visible', 'file'])
defineEmits(['close', 'confirm'])

const iconClass = computed(() => getFileIconClass(props.file?.name || ''))
</script>