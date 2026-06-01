<template>
  <div class="space-y-4">
    <h1 class="text-2xl font-bold">传输记录</h1>
    <div class="bg-white rounded-lg shadow-card overflow-hidden">
      <div class="grid grid-cols-12 bg-neutral-50 p-3 font-medium text-sm border-b">
        <div class="col-span-5">文件名</div>
        <div class="col-span-2">类型</div>
        <div class="col-span-2">大小</div>
        <div class="col-span-2">时间</div>
        <div class="col-span-1">状态</div>
      </div>
      <div v-if="loading" class="p-8 text-center">加载中...</div>
      <div v-else-if="records.length === 0" class="p-8 text-center text-neutral-400">暂无传输记录</div>
      <div v-else>
        <div v-for="record in records" :key="record.id" class="grid grid-cols-12 p-3 text-sm border-b items-center hover:bg-neutral-50">
          <div class="col-span-5 flex items-center space-x-2">
            <i :class="record.type === 'upload' ? 'fa fa-upload text-success' : 'fa fa-download text-primary'"></i>
            <span class="truncate">{{ record.fileName }}</span>
          </div>
          <div class="col-span-2">{{ record.type === 'upload' ? '上传' : '下载' }}</div>
          <div class="col-span-2">{{ formatFileSize(record.size) }}</div>
          <div class="col-span-2">{{ formatTime(record.time) }}</div>
          <div class="col-span-1">
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