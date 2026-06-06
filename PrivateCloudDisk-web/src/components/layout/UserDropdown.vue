<template>
  <div
    ref="panelRef"
    class="relative"
    @mouseenter="handleMouseEnter"
    @mouseleave="handleMouseLeave"
  >
    <button
      @click="handleTriggerClick"
      class="flex min-h-10 items-center gap-2 rounded-lg px-1.5 transition hover:bg-neutral-100 focus:outline-none"
      title="用户菜单"
    >
      <div class="flex h-9 w-9 items-center justify-center rounded-full bg-primary/10 text-primary ring-1 ring-primary/10">
        <i class="fa fa-user"></i>
      </div>
      <span class="hidden max-w-24 truncate text-sm text-neutral-700 md:inline">{{ username }}</span>
      <i class="hidden text-xs text-neutral-400 transition md:inline" :class="open ? 'fa fa-chevron-up' : 'fa fa-chevron-down'"></i>
    </button>

    <transition name="dropdown-pop">
      <div
        v-if="open"
        class="absolute right-0 top-[calc(100%+0.75rem)] z-30 w-56 overflow-hidden rounded-xl border border-neutral-200 bg-white shadow-lg"
      >
        <div class="border-b border-neutral-200 px-4 py-3">
          <div class="flex items-center gap-3">
            <div class="flex h-10 w-10 items-center justify-center rounded-full bg-primary/10 text-primary">
              <i class="fa fa-user"></i>
            </div>
            <div class="min-w-0">
              <p class="truncate text-sm font-semibold text-neutral-700">{{ username }}</p>
              <p class="text-xs text-neutral-400">CloudDrive 用户</p>
            </div>
          </div>
        </div>

        <div class="py-1">
          <router-link
            v-for="item in menuItems"
            :key="item.path"
            :to="item.path"
            class="flex items-center gap-3 px-4 py-2.5 text-sm text-neutral-600 transition hover:bg-neutral-50 hover:text-primary"
            @click="open = false"
          >
            <i :class="item.icon" class="w-4 text-center"></i>
            <span>{{ item.name }}</span>
          </router-link>
        </div>

        <div class="border-t border-neutral-200 py-1">
          <button
            @click="logout"
            class="flex w-full items-center gap-3 px-4 py-2.5 text-left text-sm text-danger transition hover:bg-danger/10"
          >
            <i class="fa fa-sign-out w-4 text-center"></i>
            <span>退出登录</span>
          </button>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { useToastStore } from '@/stores/toastStore'

const router = useRouter()
const authStore = useAuthStore()
const toastStore = useToastStore()
const open = ref(false)
const panelRef = ref(null)
const isHoverDevice = ref(false)

const username = computed(() => authStore.user?.name || '用户')
const menuItems = [
  { path: '/profile', name: '个人中心', icon: 'fa fa-user-circle' },
  { path: '/transfers', name: '传输记录', icon: 'fa fa-exchange' },
  { path: '/notifications', name: '消息中心', icon: 'fa fa-bell' },
]

function syncPointerMode() {
  isHoverDevice.value = window.matchMedia('(hover: hover) and (pointer: fine)').matches
}

function handleMouseEnter() {
  if (isHoverDevice.value) open.value = true
}

function handleMouseLeave() {
  if (isHoverDevice.value) open.value = false
}

function handleTriggerClick() {
  if (!isHoverDevice.value) open.value = !open.value
}

function handlePointerDown(event) {
  if (!open.value || isHoverDevice.value) return
  if (panelRef.value && !panelRef.value.contains(event.target)) open.value = false
}

const logout = () => {
  open.value = false
  authStore.logout()
  toastStore.showToast('已退出登录', 'success')
  router.push('/login')
}

onMounted(() => {
  syncPointerMode()
  window.addEventListener('resize', syncPointerMode)
  document.addEventListener('pointerdown', handlePointerDown)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', syncPointerMode)
  document.removeEventListener('pointerdown', handlePointerDown)
})
</script>
