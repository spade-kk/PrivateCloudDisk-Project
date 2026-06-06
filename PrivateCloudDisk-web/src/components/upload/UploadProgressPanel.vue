<template>
  <Teleport to="body">
    <!-- 展开面板 -->
    <div v-if="visible && !minimized" class="safe-bottom fixed inset-x-4 bottom-4 z-50 rounded-xl border border-neutral-200 bg-white p-4 shadow-lg fade-in sm:inset-x-auto sm:bottom-6 sm:right-6 sm:w-80 sm:p-5">
      <div class="flex items-center justify-between mb-3">
        <h3 class="font-bold text-neutral-700 text-sm flex items-center"><i class="fa fa-cloud-upload text-primary mr-2"></i>正在上传</h3>
        <div class="flex items-center space-x-2">
          <button @click="$emit('minimize')" class="text-neutral-400 hover:text-neutral-600 transition"><i class="fa fa-minus"></i></button>
          <button @click="$emit('cancel')" class="text-neutral-400 hover:text-neutral-600 transition"><i class="fa fa-times"></i></button>
        </div>
      </div>
      <div class="mb-2 text-sm text-neutral-600 truncate" :title="fileName">{{ fileName }}</div>
      <div class="w-full bg-neutral-200 rounded-full h-2 mb-2">
        <div class="bg-primary h-2 rounded-full transition-all duration-300" :style="{ width: progress + '%' }"></div>
      </div>
      <div class="flex justify-between text-xs text-neutral-500 mb-3">
        <span>{{ progress.toFixed(1) }}%</span>
        <span>{{ speed }}</span>
      </div>
      <div class="grid grid-cols-2 gap-2">
        <button @click="$emit('togglePause')" class="touch-button rounded-lg border border-neutral-200 px-3 py-1.5 text-sm transition hover:bg-neutral-50">
          <i :class="paused ? 'fa fa-play' : 'fa fa-pause'" class="mr-1"></i>{{ paused ? '继续' : '暂停' }}
        </button>
        <button @click="$emit('cancel')" class="touch-button rounded-lg border border-neutral-200 px-3 py-1.5 text-sm text-danger transition hover:bg-red-50">
          取消上传
        </button>
      </div>
    </div>

    <!-- 最小化小球 -->
    <div v-if="visible && minimized" @click="$emit('restore')" class="fixed bottom-4 right-4 z-50 flex h-12 w-12 cursor-pointer items-center justify-center rounded-full bg-primary text-white shadow-lg transition-all duration-200 hover:scale-110 sm:bottom-6 sm:right-6">
      <i class="fa fa-cloud-upload text-xl"></i>
    </div>
  </Teleport>
</template>

<script setup>
defineProps(['visible', 'minimized', 'progress', 'speed', 'fileName', 'paused'])
defineEmits(['minimize', 'restore', 'togglePause', 'cancel'])
</script>
