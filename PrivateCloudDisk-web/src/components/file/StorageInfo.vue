<template>
  <div class="flex items-center gap-3 rounded-lg bg-neutral-50 px-3 py-1.5">
    <i class="fa fa-cloud-upload text-primary text-lg"></i>
    <div class="flex min-w-0 flex-col">
      <div class="flex items-center gap-2">
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

<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { useStorageStore } from '@/stores/storageStore'
import { useSpaceStore } from '@/stores/spaceStore'

const storageStore = useStorageStore()
const spaceStore = useSpaceStore()
const usedFormatted = computed(() => storageStore.formatUsed())
const totalFormatted = computed(() => storageStore.formatTotal())
const percent = computed(() => storageStore.percent)

onMounted(() => {
  storageStore.fetchStorageInfo()
})

// 需求四-4：空间切换后按统一请求头重新获取当前空间配额。
watch(() => spaceStore.revision, () => {
  void storageStore.fetchStorageInfo()
})
</script>
