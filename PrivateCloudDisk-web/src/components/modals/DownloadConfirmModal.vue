<template>
  <div v-if="visible" class="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4" @click.self="$emit('close')">
    <div class="bg-white rounded-xl shadow-lg w-full max-w-sm p-6 fade-in">
      <div class="flex justify-between items-center mb-4">
        <h2 class="text-lg font-bold text-neutral-700">确认下载</h2>
        <button @click="$emit('close')" class="text-neutral-400 hover:text-neutral-700"><i class="fa fa-times"></i></button>
      </div>
      <div class="mb-6">
        <p class="text-neutral-600">确定要下载文件 <span class="font-medium text-neutral-700">{{ fileName }}</span> 吗？</p>
      </div>
      <div class="flex justify-end space-x-3">
        <button @click="$emit('close')" class="px-4 py-2 border border-neutral-200 rounded-lg hover:bg-neutral-50">取消</button>
        <button @click="$emit('confirm')" class="px-4 py-2 bg-primary hover:bg-primary/90 text-white rounded-lg flex items-center space-x-1">
          <i class="fa fa-download"></i><span>确认下载</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps(['visible', 'fileName'])
defineEmits(['close', 'confirm'])
</script>