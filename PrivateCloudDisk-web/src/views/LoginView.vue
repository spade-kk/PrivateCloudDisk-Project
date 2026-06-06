<template>
  <div class="flex min-h-screen items-center justify-center bg-neutral-100 p-4">
    <div class="w-full max-w-md rounded-xl bg-white p-6 shadow-lg sm:p-8">
      <div class="mb-8 text-center">
        <i class="fa fa-cloud text-4xl text-primary sm:text-5xl"></i>
        <h1 class="mt-3 text-2xl font-bold">CloudDrive 私有云</h1>
        <p class="text-neutral-500 mt-1">登录以管理您的文件</p>
      </div>
      <form @submit.prevent="handleLogin">
        <div class="space-y-4">
          <div>
            <label class="block text-sm font-medium text-neutral-600 mb-1">手机号</label>
            <input v-model="phone" type="tel" class="w-full px-4 py-2 border border-neutral-200 rounded-lg focus:ring-2 focus:ring-primary/30" placeholder="请输入手机号" required>
          </div>
          <div>
            <label class="block text-sm font-medium text-neutral-600 mb-1">密码</label>
            <input v-model="password" type="password" class="w-full px-4 py-2 border border-neutral-200 rounded-lg focus:ring-2 focus:ring-primary/30" placeholder="请输入密码" required>
          </div>
          <button type="submit" :disabled="loading" class="touch-button flex w-full items-center justify-center gap-2 rounded-lg bg-primary py-2.5 font-medium text-white hover:bg-primary/90">
            <i v-if="loading" class="fa fa-spinner fa-spin"></i>
            <i v-else class="fa fa-sign-in"></i>
            <span>{{ loading ? '登录中...' : '登录' }}</span>
          </button>
        </div>
      </form>
    </div>
    
    <ToastNotification />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { useToastStore } from '@/stores/toastStore'

const router = useRouter()
const authStore = useAuthStore()
const toastStore = useToastStore()
const phone = ref('15777446691')
const password = ref('20070315mwz')
const loading = ref(false)

async function handleLogin() {
  toastStore.showToast('1...', 'info')
  loading.value = true
  const result = await authStore.login(phone.value, password.value)
  loading.value = false
  if (result.success) {
    toastStore.showToast('登录成功', 'success')
    router.push('/')
  } else {
    toastStore.showToast(result.message, 'error')
  }
}
</script>
