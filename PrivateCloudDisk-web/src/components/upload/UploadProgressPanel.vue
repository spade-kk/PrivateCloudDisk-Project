<template>
  <Teleport to="body">
    <!-- 展开面板 -->
    <div v-if="visible && !minimized" class="fixed bottom-6 right-6 z-50 bg-white rounded-xl shadow-lg w-80 p-5 fade-in border border-neutral-200">
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
      <div class="flex space-x-2">
        <button @click="$emit('togglePause')" class="flex-1 px-3 py-1.5 text-sm border border-neutral-200 rounded-lg hover:bg-neutral-50 transition">
          <i :class="paused ? 'fa fa-play' : 'fa fa-pause'" class="mr-1"></i>{{ paused ? '继续' : '暂停' }}
        </button>
        <button @click="$emit('cancel')" class="flex-1 px-3 py-1.5 text-sm border border-neutral-200 rounded-lg text-danger hover:bg-red-50 transition">
          取消上传
        </button>
      </div>
    </div>

    <!-- 最小化小球 -->
    <div v-if="visible && minimized" @click="$emit('restore')" class="fixed bottom-6 right-6 w-12 h-12 bg-primary text-white rounded-full flex items-center justify-center cursor-pointer shadow-lg hover:scale-110 transition-all duration-200 z-50">
      <i class="fa fa-cloud-upload text-xl"></i>
    </div>
  </Teleport>
</template>

<script setup>
defineProps(['visible', 'minimized', 'progress', 'speed', 'fileName', 'paused'])
defineEmits(['minimize', 'restore', 'togglePause', 'cancel'])
</script>