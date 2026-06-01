<template>
  <div class="bg-primary/10 rounded-lg p-3 flex flex-wrap items-center justify-between gap-3">
    <div class="flex items-center space-x-2">
      <i class="fa fa-check-square-o text-primary"></i>
      <span class="text-sm font-medium">已选中 {{ count }} 项</span>
    </div>
    <div class="flex flex-wrap gap-2">
      <button @click="$emit('batch-delete')" class="px-3 py-1.5 bg-white text-danger border border-danger rounded-lg text-sm hover:bg-danger/10">
        <i class="fa fa-trash"></i> 删除
      </button>
      <button @click="$emit('batch-move')" class="px-3 py-1.5 bg-white text-primary border border-primary rounded-lg text-sm hover:bg-primary/10">
        <i class="fa fa-arrows"></i> 移动
      </button>
      <button @click="$emit('batch-copy')" class="px-3 py-1.5 bg-white text-primary border border-primary rounded-lg text-sm hover:bg-primary/10">
        <i class="fa fa-copy"></i> 复制
      </button>
      <button @click="$emit('batch-download')" class="px-3 py-1.5 bg-white text-primary border border-primary rounded-lg text-sm hover:bg-primary/10">
        <i class="fa fa-download"></i> 打包下载
      </button>
      <button @click="$emit('clear-selection')" class="px-3 py-1.5 text-neutral-500 text-sm hover:text-neutral-700">
        <i class="fa fa-times"></i> 取消
      </button>
    </div>
  </div>
</template>

<script setup>
defineProps({ count: Number })
defineEmits(['batch-delete', 'batch-move', 'batch-copy', 'batch-download', 'clear-selection'])
</script>