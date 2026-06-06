import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

const now = Date.now()

const seedNotifications = [
  {
    id: 1,
    type: 'success',
    title: '文件上传成功',
    message: '文件 "report.pdf" 已成功上传到 我的网盘 / 项目资料。',
    time: now - 1000 * 60 * 5,
    read: false,
    category: '文件',
  },
  {
    id: 2,
    type: 'security',
    title: '账号登录提醒',
    message: '你的账号刚刚在 浙江杭州 的 Chrome 浏览器登录。',
    time: now - 1000 * 60 * 32,
    read: false,
    category: '安全',
  },
  {
    id: 3,
    type: 'info',
    title: '密码修改成功',
    message: '你的账号密码已成功修改，如非本人操作请立即联系管理员。',
    time: now - 1000 * 60 * 60 * 4,
    read: true,
    category: '账号',
  },
  {
    id: 4,
    type: 'warning',
    title: '分享链接即将过期',
    message: '分享 "项目资料" 将在 24 小时后过期。',
    time: now - 1000 * 60 * 60 * 12,
    read: true,
    category: '分享',
  },
]

export const useNotificationStore = defineStore('notification', () => {
  const notifications = ref(seedNotifications)

  const unreadCount = computed(() => notifications.value.filter(item => !item.read).length)
  const recentNotifications = computed(() => notifications.value.slice(0, 5))

  function markAsRead(id) {
    const item = notifications.value.find(notification => notification.id === id)
    if (item) item.read = true
  }

  function markAllAsRead() {
    notifications.value.forEach(item => {
      item.read = true
    })
  }

  return {
    notifications,
    recentNotifications,
    unreadCount,
    markAsRead,
    markAllAsRead,
  }
})
