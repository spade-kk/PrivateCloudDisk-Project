<template>
  <div class="flex h-screen bg-neutral-100" :class="{ dark: isDarkMode }">
    <Sidebar />
    <div class="flex-1 flex flex-col overflow-hidden">
      <header class="bg-white shadow-sm z-10">
        <div class="px-6 py-3 flex items-center justify-between">
          <h2 class="text-lg font-semibold text-neutral-700">{{ currentRouteName }}</h2>
          <div class="flex items-center space-x-4">
            <button @click="toggleDarkMode" class="text-neutral-600 hover:text-primary">
              <i :class="isDarkMode ? 'fa fa-sun-o' : 'fa fa-moon-o'"></i>
            </button>
            <StorageInfo />
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
      <main class="flex-1 overflow-y-auto p-6">
        <router-view />
      </main>
      <footer class="bg-white border-t border-neutral-200 py-4">
      <div class="container mx-auto px-4 text-center text-neutral-500 text-sm">
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

const currentRouteName = computed(() => {
  const names = { Dashboard: '我的网盘', Shares: '分享管理', Trash: '回收站', Profile: '个人中心', Transfers: '传输记录' }
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
</script>