<template>
  <Teleport to="body">
    <Transition name="fade">
      <div
        v-if="folderDownloaderStore.isActive"
        class="fixed bottom-4 right-4 z-50 w-96 rounded-xl border bg-white p-4 shadow-2xl dark:border-neutral-700 dark:bg-neutral-800"
      >
        <!-- 头部 -->
        <div class="mb-3 flex items-center justify-between">
          <div class="flex items-center gap-2">
            <i class="fa fa-folder-download text-primary"></i>
            <span class="font-semibold text-sm truncate max-w-[200px]">
              {{ folderDownloaderStore.folderName }}
            </span>
          </div>
          <button
            @click="handleCancel"
            class="rounded-lg p-1 text-neutral-500 hover:bg-neutral-100 hover:text-danger dark:hover:bg-neutral-700"
            title="取消下载"
          >
            <i class="fa fa-times"></i>
          </button>
        </div>

        <!-- 状态标签 -->
        <div class="mb-2 text-xs text-neutral-500">
          <span v-if="folderDownloaderStore.status === 'fetching'">正在获取文件列表...</span>
          <span v-else-if="folderDownloaderStore.status === 'downloading'">
            下载中 {{ folderDownloaderStore.currentFileIndex + 1 }} / {{ folderDownloaderStore.files.length }}
            · {{ folderDownloaderStore.getSpeed() }}
          </span>
        </div>

        <!-- 整体进度条 -->
        <div class="mb-3">
          <div class="mb-1 flex justify-between text-xs text-neutral-500">
            <span>总进度</span>
            <span>{{ folderDownloaderStore.folderProgress }}%</span>
          </div>
          <div class="h-2 w-full overflow-hidden rounded-full bg-neutral-200 dark:bg-neutral-600">
            <div
              class="h-full rounded-full bg-primary transition-all duration-300"
              :style="{ width: folderDownloaderStore.folderProgress + '%' }"
            ></div>
          </div>
        </div>

        <!-- 文件列表 -->
        <div class="max-h-48 overflow-y-auto">
          <div
            v-for="(file, idx) in folderDownloaderStore.files"
            :key="idx"
            class="flex items-center gap-2 py-1 text-xs"
            :class="{
              'text-neutral-400': file.status === 'pending',
              'text-primary': file.status === 'downloading',
              'text-success': file.status === 'completed',
              'text-danger': file.status === 'error',
            }"
          >
            <i
              class="fa w-4 text-center"
              :class="{
                'fa-circle-o': file.status === 'pending',
                'fa-spinner fa-spin': file.status === 'downloading',
                'fa-check-circle': file.status === 'completed',
                'fa-times-circle': file.status === 'error',
              }"
            ></i>
            <span class="truncate flex-1" :title="file.fileInfo.relativePath">{{ file.fileInfo.fileName }}</span>
            <span class="text-neutral-400">{{ file.progress }}%</span>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { useFolderDownloaderStore } from '@/stores/folderDownloaderStore'

const folderDownloaderStore = useFolderDownloaderStore()

function handleCancel(): void {
  folderDownloaderStore.cancelFolderDownload()
}
</script>

<style scoped>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>