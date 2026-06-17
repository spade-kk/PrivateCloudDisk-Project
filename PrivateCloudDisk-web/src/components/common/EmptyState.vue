<template>
  <div class="flex flex-col items-center justify-center py-20">
    <div class="w-24 h-24 bg-neutral-100 rounded-full flex items-center justify-center mb-4">
      <i class="fa fa-folder-open text-4xl text-neutral-300"></i>
    </div>
    <h3 class="text-lg font-medium text-neutral-600 mb-1">{{ message }}</h3>
    <p class="text-neutral-400 mb-4">点击下方按钮开始</p>
    <button @click="$emit('create')" class="px-4 py-2 bg-primary hover:bg-primary/90 text-white rounded-lg flex items-center space-x-1">
      <i class="fa fa-folder-plus"></i><span>新建文件夹</span>
    </button>
  </div>
</template>

<script setup lang="ts">
defineProps({ message: { type: String, default: '该文件夹为空' } })
defineEmits(['create'])
</script>