<template>
  <div v-if="visible" class="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4" @click.self="$emit('close')">
    <div class="fade-in w-full max-w-sm rounded-xl bg-white p-5 shadow-lg sm:p-6">
      <div class="flex justify-between items-center mb-4">
        <h2 class="text-lg font-bold text-neutral-700">确认下载</h2>
        <button @click="$emit('close')" class="text-neutral-400 hover:text-neutral-700"><i class="fa fa-times"></i></button>
      </div>
      <div class="mb-6">
        <p class="text-neutral-600">确定要下载文件 <span class="break-all font-medium text-neutral-700">{{ fileName }}</span> 吗？</p>
      </div>
      <div class="grid grid-cols-2 gap-3 sm:flex sm:justify-end">
        <button @click="$emit('close')" class="touch-button rounded-lg border border-neutral-200 px-4 py-2 hover:bg-neutral-50">取消</button>
        <button @click="$emit('confirm')" class="touch-button flex items-center justify-center gap-1 rounded-lg bg-primary px-4 py-2 text-white hover:bg-primary/90">
          <i class="fa fa-download"></i><span>确认下载</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps(['visible', 'fileName'])
defineEmits(['close', 'confirm'])
</script>
