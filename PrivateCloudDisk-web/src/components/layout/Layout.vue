<template>
  <div class="app-shell flex overflow-hidden bg-neutral-100" :class="{ dark: isDarkMode }">
    <Sidebar :mobile-open="mobileSidebarOpen" @close="mobileSidebarOpen = false" />
    <div class="app-content flex min-h-0 flex-1 flex-col overflow-hidden">
      <header class="z-20 shrink-0 bg-white shadow-sm">
        <div class="flex min-h-[64px] items-center justify-between gap-3 px-4 py-3 sm:px-5 lg:px-6">
          <div class="flex min-w-0 items-center gap-3">
            <button
              @click="mobileSidebarOpen = true"
              class="icon-button mobile-only shrink-0"
              title="打开导航"
            >
              <i class="fa fa-bars"></i>
            </button>
            <h2 class="truncate text-base font-semibold text-neutral-700 sm:text-lg">{{ currentRouteName }}</h2>
          </div>
          <div class="flex min-w-0 items-center justify-end gap-2 sm:gap-3 lg:gap-4">
            <button @click="toggleDarkMode" class="icon-button shrink-0" title="切换深色模式">
              <i :class="isDarkMode ? 'fa fa-sun-o' : 'fa fa-moon-o'"></i>
            </button>
            <StorageInfo class="hidden sm:flex" />
            <NotificationCenter />
            <UserDropdown />
          </div>
        </div>
      </header>
      <!-- <header class="bg-white shadow-sm fixed top-0 left-0 right-0 z-50">
      <div class="container mx-auto px-4 py-3 flex items-center justify-between">
        <div class="flex items-center space-x-2 cursor-pointer" @click="goHome">
          <i class="fa fa-cloud text-primary text-2xl"></i>
          <h1 class="text-xl font-bold">CloudDrive <span class="text-primary">私有云</span></h1>
        </div>
        <div class="flex items-center space-x-4">
          <div class="flex items-center space-x-2">
            <span class="text-neutral-600">{{ '用户' }}</span>
            <div class="w-8 h-8 rounded-full bg-primary/10 flex items-center justify-center text-primary">
              <i class="fa fa-user"></i>
            </div>
          </div>
          <StorageInfo />
          <button @click="logout" class="bg-neutral-200 hover:bg-neutral-300 text-neutral-700 px-4 py-2 rounded-lg flex items-center space-x-1">
            <i class="fa fa-sign-out"></i><span>退出</span>
          </button>
        </div>
      </div>
    </header> -->
      <main class="min-h-0 flex-1 overflow-y-auto p-4 sm:p-5 lg:p-6">
        <div class="page-container">
          <router-view v-slot="{ Component, route: viewRoute }">
            <transition name="page-fade" mode="out-in">
              <component :is="Component" :key="viewRoute.fullPath" />
            </transition>
          </router-view>
        </div>
      </main>
      <footer class="shrink-0 border-t border-neutral-200 bg-white py-3 sm:py-4">
        <div class="px-4 text-center text-xs text-neutral-500 sm:text-sm">
          <p>© 2025 CloudDrive 私有云网盘管理系统</p>
        </div>
      </footer>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import Sidebar from './Sidebar.vue'
import UserDropdown from './UserDropdown.vue'
import NotificationCenter from './NotificationCenter.vue'
import StorageInfo from '../file/StorageInfo.vue'

const route = useRoute()
const isDarkMode = ref(localStorage.getItem('darkMode') === 'true')
const mobileSidebarOpen = ref(false)

const currentRouteName = computed(() => {
  const names = {
    Dashboard: '我的网盘', Search: '文件搜索', Starred: '收藏夹', Notifications: '消息中心',
    Shares: '分享管理', Trash: '回收站', Profile: '个人中心', Transfers: '传输记录',
    Versions: '版本管理', Team: '团队协作', Admin: '管理后台', Analytics: '数据分析',
    Security: '安全中心', ApiKeys: 'API 密钥管理', ActivityLog: '操作日志',
    Settings: '系统设置', Billing: '套餐管理', Help: '帮助中心',
  }
  return names[route.name] || route.name
})

const toggleDarkMode = () => {
  isDarkMode.value = !isDarkMode.value
  localStorage.setItem('darkMode', isDarkMode.value)
  if (isDarkMode.value) document.documentElement.classList.add('dark')
  else document.documentElement.classList.remove('dark')
}

onMounted(() => {
  if (isDarkMode.value) document.documentElement.classList.add('dark')
})

watch(() => route.fullPath, () => {
  mobileSidebarOpen.value = false
})
</script>
