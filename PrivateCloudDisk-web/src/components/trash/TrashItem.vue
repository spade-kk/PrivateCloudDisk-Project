<template>
  <div class="grid grid-cols-12 p-3 text-sm border-b items-center hover:bg-neutral-50">
    <div class="col-span-6 flex items-center space-x-2">
      <i :class="['fa', iconClass]"></i>
      <span class="truncate">{{ item.node_name }}</span>
    </div>
    <div class="col-span-3">{{ formatTime(item.deleted_at) }}</div>
    <div class="col-span-3 text-right space-x-2">
      <button @click="$emit('restore', item.id)" class="text-primary text-sm"><i class="fa fa-undo"></i> 还原</button>
      <button @click="$emit('delete', item.id)" class="text-danger text-sm"><i class="fa fa-trash"></i> 彻底删除</button>
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