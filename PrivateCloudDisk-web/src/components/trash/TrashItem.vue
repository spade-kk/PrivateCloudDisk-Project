<template>
  <div class="block border-b p-3 text-sm hover:bg-neutral-50 sm:grid sm:grid-cols-12 sm:items-center">
    <div class="flex min-w-0 items-center gap-2 sm:col-span-6">
      <i :class="['fa', iconClass]"></i>
      <span class="truncate">{{ item.node_name }}</span>
    </div>
    <div class="mt-2 text-xs text-neutral-500 sm:col-span-3 sm:mt-0 sm:text-sm">{{ formatTime(item.deleted_at) }}</div>
    <div class="mt-3 grid grid-cols-2 gap-2 sm:col-span-3 sm:mt-0 sm:flex sm:justify-end sm:gap-3 sm:text-right">
      <button @click="$emit('restore', item.id)" class="touch-button rounded-lg border border-primary px-3 py-1.5 text-sm text-primary sm:border-0 sm:px-0 sm:py-0"><i class="fa fa-undo"></i> 还原</button>
      <button @click="$emit('delete', item.id)" class="touch-button rounded-lg border border-danger px-3 py-1.5 text-sm text-danger sm:border-0 sm:px-0 sm:py-0"><i class="fa fa-trash"></i> 彻底删除</button>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { formatTime } from '@/utils/helpers'
import { getFileIconClass } from '@/utils/fileIcon'

const props = defineProps(['item'])
defineEmits(['restore', 'delete'])

const iconClass = computed(() => getFileIconClass(props.item.node_name))
</script>
