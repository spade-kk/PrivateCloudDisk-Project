<template>
  <div class="space-y-4 sm:space-y-6">
    <h1 class="text-xl font-bold sm:text-2xl">传输记录</h1>
    <div class="overflow-hidden rounded-lg bg-white shadow-card">
      <div class="hidden grid-cols-12 bg-neutral-50 p-3 font-medium text-sm border-b sm:grid">
        <div class="col-span-5">文件名</div>
        <div class="col-span-2">类型</div>
        <div class="col-span-2">大小</div>
        <div class="col-span-2">时间</div>
        <div class="col-span-1">状态</div>
      </div>
      <div v-if="loading" class="p-8 text-center">加载中...</div>
      <div v-else-if="records.length === 0" class="p-8 text-center text-neutral-400">暂无传输记录</div>
      <div v-else>
        <div v-for="record in records" :key="record.id" class="block border-b p-3 text-sm hover:bg-neutral-50 sm:grid sm:grid-cols-12 sm:items-center">
          <div class="flex min-w-0 items-center gap-2 sm:col-span-5">
            <i :class="record.type === 'upload' ? 'fa fa-upload text-success' : 'fa fa-download text-primary'"></i>
            <span class="truncate">{{ record.fileName }}</span>
          </div>
          <div class="mt-2 grid grid-cols-2 gap-2 text-xs text-neutral-500 sm:contents sm:text-sm">
            <div class="sm:col-span-2">{{ record.type === 'upload' ? '上传' : '下载' }}</div>
            <div class="sm:col-span-2">{{ formatFileSize(record.size) }}</div>
            <div class="col-span-2 sm:col-span-2">{{ formatTime(record.time) }}</div>
          </div>
          <div class="mt-2 sm:col-span-1 sm:mt-0">
            <span :class="record.status === 'success' ? 'text-success' : record.status === 'failed' ? 'text-danger' : 'text-warning'">
              {{ record.status === 'success' ? '成功' : record.status === 'failed' ? '失败' : '进行中' }}
            </span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useTransferHistoryStore } from '@/stores/transferHistoryStore'
import { formatFileSize, formatTime } from '@/utils/helpers'

const historyStore = useTransferHistoryStore()
const records = ref([])
const loading = ref(false)

onMounted(() => {
  loading.value = true
  records.value = historyStore.records
  loading.value = false
})
</script>
