<template>
  <div class="relative">
    <button @click="open = !open" class="flex items-center space-x-2 focus:outline-none">
      <div class="w-8 h-8 rounded-full bg-primary/10 flex items-center justify-center text-primary">
        <i class="fa fa-user"></i>
      </div>
      <span class="hidden md:inline text-sm">{{ username }}</span>
      <i class="fa fa-chevron-down text-xs"></i>
    </button>
    <div v-if="open" class="absolute right-0 mt-2 w-48 bg-white rounded-lg shadow-lg py-1 z-20">
      <router-link to="/profile" class="block px-4 py-2 text-sm hover:bg-neutral-100">个人中心</router-link>
      <router-link to="/transfers" class="block px-4 py-2 text-sm hover:bg-neutral-100">传输记录</router-link>
      <hr class="my-1">
      <button @click="logout" class="block w-full text-left px-4 py-2 text-sm text-red-500 hover:bg-neutral-100">退出登录</button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { useToastStore } from '@/stores/toastStore'

const router = useRouter()
const authStore = useAuthStore()
const toastStore = useToastStore()
const open = ref(false)

const username = computed(() => authStore.user?.name || '用户')

const logout = () => {
  authStore.logout()
  toastStore.showToast('已退出登录', 'success')
  router.push('/login')
}
</script>