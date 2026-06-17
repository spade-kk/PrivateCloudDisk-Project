<template>
  <div class="flex min-h-screen items-center justify-center bg-gradient-to-br from-neutral-50 via-white to-primary/5 px-4 py-12">
    <div class="w-full max-w-lg">
      <!-- Logo -->
      <div class="text-center mb-8">
        <router-link to="/" class="inline-flex items-center gap-2.5">
          <div class="flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br from-primary to-info">
            <i class="fa fa-cloud text-lg text-white"></i>
          </div>
          <span class="text-xl font-bold text-neutral-800">CloudDrive</span>
        </router-link>
      </div>

      <!-- Card -->
      <div class="rounded-2xl border border-neutral-200 bg-white shadow-xl shadow-neutral-900/5 p-8 sm:p-10">
        <!-- 已登录提示 -->
        <div class="text-center">
          <div class="mx-auto flex h-16 w-16 items-center justify-center rounded-2xl bg-success/10">
            <i class="fa fa-check-circle text-3xl text-success"></i>
          </div>
          <h1 class="mt-4 text-2xl font-bold text-neutral-900">你已登录</h1>
          <p class="mt-2 text-sm text-neutral-500">无需重复登录，你的账号已处于活跃状态</p>
        </div>

        <!-- 用户信息 -->
        <div class="mt-6 rounded-xl border border-neutral-100 bg-neutral-50/50 p-4">
          <div class="flex items-center gap-4">
            <div v-if="auth.user.image_path" class="h-12 w-12 rounded-full overflow-hidden shrink-0">
              <img :src="auth.user.image_path" alt="avatar" class="h-full w-full object-cover" />
            </div>
            <div v-else class="flex h-12 w-12 items-center justify-center rounded-full bg-primary/10 text-lg font-bold text-primary shrink-0">
              {{ auth.userInitial }}
            </div>
            <div class="min-w-0">
              <p class="text-base font-semibold text-neutral-800 truncate">{{ auth.displayName }}</p>
              <p class="text-xs text-neutral-400 mt-0.5 truncate">{{ auth.user.email || auth.user.phone_number || auth.user.account }}</p>
            </div>
          </div>
        </div>

        <!-- 你想去哪？ -->
        <div class="mt-6">
          <p class="text-sm font-semibold text-neutral-700 mb-3">你想去哪？</p>
          <div class="grid grid-cols-1 gap-2 sm:grid-cols-2">
            <router-link to="/app" class="flex items-center gap-3 rounded-xl border border-primary/20 bg-primary/5 px-4 py-3 text-sm font-medium text-primary hover:bg-primary/10 hover:border-primary/30 transition">
              <i class="fa fa-th-large w-4 text-center"></i>
              控制面板
              <span class="ml-auto text-xs text-primary/60">推荐</span>
            </router-link>
            <router-link to="/app/profile" class="flex items-center gap-3 rounded-xl border border-neutral-200 px-4 py-3 text-sm font-medium text-neutral-600 hover:border-primary/20 hover:text-primary hover:bg-neutral-50 transition">
              <i class="fa fa-user-circle w-4 text-center"></i>
              个人中心
            </router-link>
            <router-link to="/" class="flex items-center gap-3 rounded-xl border border-neutral-200 px-4 py-3 text-sm font-medium text-neutral-600 hover:border-primary/20 hover:text-primary hover:bg-neutral-50 transition">
              <i class="fa fa-home w-4 text-center"></i>
              返回官网首页
            </router-link>
            <router-link to="/docs" class="flex items-center gap-3 rounded-xl border border-neutral-200 px-4 py-3 text-sm font-medium text-neutral-600 hover:border-primary/20 hover:text-primary hover:bg-neutral-50 transition">
              <i class="fa fa-book w-4 text-center"></i>
              帮助文档
            </router-link>
          </div>
        </div>

        <!-- 自动跳转倒计时 -->
        <div class="mt-6 rounded-xl bg-primary/5 border border-primary/10 p-4 text-center">
          <p class="text-sm text-neutral-600">
            <span class="font-semibold text-primary">{{ countdown }}</span> 秒后自动跳转到控制面板
          </p>
          <div class="mt-2 h-1.5 rounded-full bg-neutral-200 overflow-hidden">
            <div class="h-full rounded-full bg-primary transition-all duration-1000 ease-linear" :style="{ width: ((60 - countdown) / 60 * 100) + '%' }"></div>
          </div>
          <div class="mt-3 flex items-center justify-center gap-4">
            <router-link to="/app" class="text-sm font-medium text-primary hover:underline">立即前往</router-link>
            <span class="text-neutral-300">|</span>
            <button @click="cancelRedirect" class="text-sm text-neutral-400 hover:text-neutral-600 transition">取消跳转</button>
          </div>
        </div>

        <!-- 退出登录 -->
        <div class="mt-6 text-center">
          <p class="text-xs text-neutral-400 mb-2">不是你的账号？</p>
          <button @click="showLogoutConfirm = true" class="text-sm font-medium text-danger hover:underline">
            <i class="fa fa-sign-out mr-1"></i> 退出登录
          </button>
        </div>
      </div>

      <!-- 底部链接 -->
      <div class="mt-6 text-center">
        <router-link to="/" class="text-xs text-neutral-400 hover:text-primary transition">返回首页</router-link>
      </div>
    </div>

    <!-- 退出登录确认弹窗 -->
    <Teleport to="body">
      <Transition name="modal-fade">
        <div v-if="showLogoutConfirm" class="fixed inset-0 z-50 flex items-center justify-center p-4" @click.self="showLogoutConfirm = false">
          <div class="absolute inset-0 bg-black/40 backdrop-blur-sm"></div>
          <div class="relative w-full max-w-sm rounded-2xl bg-white shadow-2xl border border-neutral-200 p-6">
            <div class="text-center">
              <div class="mx-auto flex h-12 w-12 items-center justify-center rounded-xl bg-warning/10">
                <i class="fa fa-exclamation-triangle text-xl text-warning"></i>
              </div>
              <h3 class="mt-3 text-lg font-bold text-neutral-900">确认退出登录？</h3>
              <p class="mt-1 text-sm text-neutral-500">退出后需要重新输入账号密码才能访问控制面板</p>
            </div>
            <div class="mt-6 flex gap-3">
              <button @click="showLogoutConfirm = false" class="flex-1 rounded-xl border border-neutral-200 py-2.5 text-sm font-semibold text-neutral-600 hover:bg-neutral-50 transition">取消</button>
              <button @click="doLogout" class="flex-1 rounded-xl bg-danger py-2.5 text-sm font-semibold text-white hover:bg-danger/90 transition">确认退出</button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'

const router = useRouter()
const auth = useAuthStore()

const countdown = ref(60)
const showLogoutConfirm = ref(false)
let timer = null
let cancelled = false

function cancelRedirect() {
  cancelled = true
  if (timer) clearInterval(timer)
}

function doLogout() {
  auth.logout()
  showLogoutConfirm.value = false
  router.push('/login')
}

onMounted(() => {
  auth.fetchUserInfo()
  timer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0) {
      clearInterval(timer)
      if (!cancelled) router.push('/app')
    }
  }, 1000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.modal-fade-enter-active,
.modal-fade-leave-active {
  transition: all 0.2s ease;
}
.modal-fade-enter-from,
.modal-fade-leave-to {
  opacity: 0;
}
.modal-fade-enter-from > div:nth-child(2),
.modal-fade-leave-to > div:nth-child(2) {
  transform: scale(0.95);
}
</style>