<template>
  <div class="flex items-center space-x-3 px-3 py-1.5 bg-neutral-50 rounded-lg">
    <i class="fa fa-cloud-upload text-primary text-lg"></i>
    <div class="flex flex-col">
      <div class="flex items-center space-x-2">
        <span class="text-xs text-neutral-500">已用</span>
        <span class="text-xs font-medium text-neutral-700">{{ usedFormatted }}</span>
        <span class="text-xs text-neutral-400">/</span>
        <span class="text-xs font-medium text-neutral-700">{{ totalFormatted }}</span>
      </div>
      <div class="w-24 h-1.5 bg-neutral-200 rounded-full mt-1 overflow-hidden">
        <div class="h-full bg-primary transition-all" :style="{ width: percent + '%' }"></div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useStorageStore } from '@/stores/storageStore'

const storageStore = useStorageStore()
const usedFormatted = computed(() => storageStore.formatUsed())
const totalFormatted = computed(() => storageStore.formatTotal())
const percent = computed(() => storageStore.percent)
</script>