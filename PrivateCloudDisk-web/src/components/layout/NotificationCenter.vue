<template>
  <div class="relative">
    <button @click="showDropdown = !showDropdown" class="relative text-neutral-600 hover:text-primary">
      <i class="fa fa-bell text-xl"></i>
      <span v-if="unreadCount" class="absolute -top-1 -right-2 bg-danger text-white text-xs rounded-full w-4 h-4 flex items-center justify-center">{{ unreadCount }}</span>
    </button>
    <div v-if="showDropdown" class="absolute right-0 mt-2 w-80 bg-white rounded-lg shadow-lg z-20 max-h-96 overflow-y-auto">
      <div class="p-3 border-b font-semibold">通知</div>
      <div v-if="notifications.length === 0" class="p-4 text-center text-neutral-400">暂无通知</div>
      <div v-for="notif in notifications" :key="notif.id" class="p-3 border-b hover:bg-neutral-50">
        <p class="text-sm">{{ notif.message }}</p>
        <p class="text-xs text-neutral-400 mt-1">{{ formatTime(notif.time) }}</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'

const showDropdown = ref(false)
const unreadCount = ref(0)
const notifications = ref([])

const formatTime = (timestamp) => {
  const diff = Date.now() - timestamp
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
  return new Date(timestamp).toLocaleDateString()
}

onMounted(() => {
  // 模拟通知
  notifications.value = [
    { id: 1, message: '文件 "report.pdf" 上传成功', time: Date.now() - 1000 * 60 * 5 },
    { id: 2, message: '分享链接 "项目资料" 被访问', time: Date.now() - 1000 * 60 * 60 },
  ]
  unreadCount.value = notifications.value.length
})
</script>