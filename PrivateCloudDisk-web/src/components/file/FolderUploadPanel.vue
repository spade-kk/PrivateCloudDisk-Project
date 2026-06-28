<template>
  <div class="folder-upload-panel">
    <!-- 触发按钮 -->
    <button
      @click="triggerFolderSelect"
      :disabled="folderUploaderStore.isActive"
      class="touch-button flex items-center justify-center gap-2 rounded-lg bg-primary px-3 py-2 text-sm text-white sm:px-4"
      title="上传文件夹"
    >
      <i class="fa fa-folder-open"></i>
      <span>上传文件夹</span>
    </button>
    <input
      ref="folderInputRef"
      type="file"
      class="hidden"
      webkitdirectory
      directory
      multiple
      @change="onFolderSelected"
    />

    <!-- 进度面板 -->
    <Teleport to="body">
      <Transition name="fade">
        <div
          v-if="folderUploaderStore.isActive"
          class="fixed bottom-4 right-4 z-50 w-96 rounded-xl border bg-white p-4 shadow-2xl dark:border-neutral-700 dark:bg-neutral-800"
        >
          <!-- 头部 -->
          <div class="mb-3 flex items-center justify-between">
            <div class="flex items-center gap-2 min-w-0">
              <i class="fa fa-folder-open text-primary shrink-0"></i>
              <span class="font-semibold text-sm truncate">
                {{ folderUploaderStore.folderName }}
              </span>
            </div>
            <button
              @click="handleCancel"
              class="ml-2 rounded-lg p-1 text-neutral-500 hover:bg-neutral-100 hover:text-danger dark:hover:bg-neutral-700 shrink-0"
              title="取消上传"
            >
              <i class="fa fa-times"></i>
            </button>
          </div>

          <!-- 状态标签 -->
          <div class="mb-2 text-xs text-neutral-500">
            <span v-if="folderUploaderStore.status === 'scanning'">正在扫描文件列表...</span>
            <span v-else-if="folderUploaderStore.status === 'uploading'">
              上传中
              <span class="font-medium text-neutral-700">
                {{ folderUploaderStore.completedCount }} / {{ folderUploaderStore.files.length }}
              </span>
              个文件 · {{ folderUploaderStore.getSpeed() }}
            </span>
          </div>

          <!-- 文件计数统计 -->
          <div class="mb-2 flex items-center gap-3 text-[11px] text-neutral-400">
            <span>
              共 <span class="font-medium text-neutral-600">{{ folderUploaderStore.files.length }}</span> 个文件
            </span>
            <span class="text-success">
              已完成 <span class="font-medium">{{ folderUploaderStore.completedCount }}</span>
            </span>
            <span v-if="folderUploaderStore.errorCount > 0" class="text-danger">
              失败 <span class="font-medium">{{ folderUploaderStore.errorCount }}</span>
            </span>
            <span class="text-neutral-400">
              剩余 <span class="font-medium text-neutral-600">{{ folderUploaderStore.files.length - folderUploaderStore.completedCount - folderUploaderStore.errorCount }}</span>
            </span>
          </div>

          <!-- 整体进度条 -->
          <div class="mb-3">
            <div class="mb-1 flex justify-between text-xs text-neutral-500">
              <span>总进度</span>
              <span>{{ folderUploaderStore.folderProgress }}%</span>
            </div>
            <div class="h-2 w-full overflow-hidden rounded-full bg-neutral-200 dark:bg-neutral-600">
              <div
                class="h-full rounded-full bg-primary transition-all duration-300"
                :style="{ width: folderUploaderStore.folderProgress + '%' }"
              ></div>
            </div>
          </div>

          <!-- 文件列表 -->
          <div class="max-h-48 overflow-y-auto">
            <div
              v-for="(file, idx) in folderUploaderStore.files"
              :key="idx"
              class="flex items-center gap-2 py-1 text-xs"
              :class="{
                'text-neutral-400': file.status === 'pending',
                'text-primary': file.status === 'uploading',
                'text-success': file.status === 'completed',
                'text-danger': file.status === 'error',
              }"
            >
              <i
                class="fa w-4 text-center shrink-0"
                :class="{
                  'fa-circle-o': file.status === 'pending',
                  'fa-spinner fa-spin': file.status === 'uploading',
                  'fa-check-circle': file.status === 'completed',
                  'fa-times-circle': file.status === 'error',
                }"
              ></i>
              <span class="truncate flex-1" :title="file.relativePath">{{ file.relativePath || file.file.name }}</span>
              <span class="text-neutral-400 shrink-0">{{ file.progress }}%</span>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useFolderUploaderStore } from '@/stores/folderUploaderStore'
import { useFileBrowserStore } from '@/stores/fileBrowserStore'

const folderUploaderStore = useFolderUploaderStore()
const fileBrowserStore = useFileBrowserStore()

const folderInputRef = ref<HTMLInputElement | null>(null)

function triggerFolderSelect(): void {
  if (folderUploaderStore.isActive) return
  folderInputRef.value?.click()
}

async function onFolderSelected(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const fileList = input.files

  if (!fileList || fileList.length === 0) return

  const files = Array.from(fileList)

  try {
    await folderUploaderStore.startFolderUpload(
      fileBrowserStore.currentNodeId,
      files,
    )
  } finally {
    input.value = ''
  }
}

function handleCancel(): void {
  folderUploaderStore.cancelFolderUpload()
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