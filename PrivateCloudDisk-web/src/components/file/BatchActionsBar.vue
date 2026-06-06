<template>
  <div class="flex flex-col gap-3 rounded-lg bg-primary/10 p-3 sm:flex-row sm:flex-wrap sm:items-center sm:justify-between">
    <div class="flex items-center gap-2">
      <i class="fa fa-check-square-o text-primary"></i>
      <span class="text-sm font-medium">已选中 {{ count }} 项</span>
    </div>
    <div class="grid grid-cols-2 gap-2 sm:flex sm:flex-wrap">
      <button @click="$emit('batch-delete')" class="touch-button rounded-lg border border-danger bg-white px-3 py-1.5 text-sm text-danger hover:bg-danger/10">
        <i class="fa fa-trash"></i> 删除
      </button>
      <button @click="$emit('batch-move')" class="touch-button rounded-lg border border-primary bg-white px-3 py-1.5 text-sm text-primary hover:bg-primary/10">
        <i class="fa fa-arrows"></i> 移动
      </button>
      <button @click="$emit('batch-copy')" class="touch-button rounded-lg border border-primary bg-white px-3 py-1.5 text-sm text-primary hover:bg-primary/10">
        <i class="fa fa-copy"></i> 复制
      </button>
      <button @click="$emit('batch-download')" class="touch-button rounded-lg border border-primary bg-white px-3 py-1.5 text-sm text-primary hover:bg-primary/10">
        <i class="fa fa-download"></i> 打包下载
      </button>
      <button @click="$emit('clear-selection')" class="touch-button col-span-2 px-3 py-1.5 text-sm text-neutral-500 hover:text-neutral-700 sm:col-span-1">
        <i class="fa fa-times"></i> 取消
      </button>
    </div>
  </div>
</template>

<script setup>
defineProps({ count: Number })
defineEmits(['batch-delete', 'batch-move', 'batch-copy', 'batch-download', 'clear-selection'])
</script>
