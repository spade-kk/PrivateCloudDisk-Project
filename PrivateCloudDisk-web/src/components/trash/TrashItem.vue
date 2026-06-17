<template>
  <div class="block border-b p-3 text-sm hover:bg-neutral-50 sm:grid sm:grid-cols-12 sm:items-center">
    <div class="flex min-w-0 items-center gap-2 sm:col-span-5">
      <i :class="['fa', iconClass]"></i>
      <span class="truncate">{{ item.target_name }}</span>
    </div>
    <div class="mt-2 text-xs text-neutral-500 sm:col-span-2 sm:mt-0 sm:text-sm">
      {{ typeText }}
    </div>
    <div class="mt-2 text-xs text-neutral-500 sm:col-span-2 sm:mt-0 sm:text-sm">{{ formatDateTime(item.deleted_at) }}</div>
    <div class="mt-3 grid grid-cols-2 gap-2 sm:col-span-3 sm:mt-0 sm:flex sm:justify-end sm:gap-3 sm:text-right">
      <button @click="$emit('restore', item.trash_id)" class="touch-button rounded-lg border border-primary px-3 py-1.5 text-sm text-primary sm:border-0 sm:px-0 sm:py-0"><i class="fa fa-undo"></i> 还原</button>
      <button @click="$emit('delete', item.trash_id)" class="touch-button rounded-lg border border-danger px-3 py-1.5 text-sm text-danger sm:border-0 sm:px-0 sm:py-0"><i class="fa fa-trash"></i> 彻底删除</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { formatDateTime } from '@/utils/helpers'
import { getFileIconClass } from '@/utils/fileIcon'

const props = defineProps(['item'])
defineEmits(['restore', 'delete'])

const isFolder = computed(() => props.item.target_type === 'folder')
const iconClass = computed(() => isFolder.value ? 'fa-folder text-yellow-500' : getFileIconClass(props.item.target_name || ''))
const typeText = computed(() => isFolder.value ? '文件夹' : (props.item.file_type || '文件'))
</script>
