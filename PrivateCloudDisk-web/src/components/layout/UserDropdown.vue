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
      <div class="user-avatar h-9 w-9">
        <img v-if="profile.image_path" :src="profile.image_path" alt="avatar" />
        <span v-else>{{ userStore.initials }}</span>
      </div>
      <span class="hidden max-w-28 truncate text-sm text-neutral-700 md:inline">{{ userStore.displayName }}</span>
      <i class="hidden text-xs text-neutral-400 transition md:inline" :class="open ? 'fa fa-chevron-up' : 'fa fa-chevron-down'"></i>
    </button>

    <transition name="dropdown-pop">
      <div
        v-if="open"
        class="absolute right-0 top-[calc(100%+0.75rem)] z-30 w-56 overflow-hidden rounded-xl border border-neutral-200 bg-white shadow-lg"
      >
        <div class="border-b border-neutral-200 px-4 py-3">
          <div class="flex items-center gap-3">
            <div class="user-avatar h-11 w-11">
              <img v-if="profile.image_path" :src="profile.image_path" alt="avatar" />
              <span v-else>{{ userStore.initials }}</span>
            </div>
            <div class="min-w-0">
              <p class="truncate text-sm font-semibold text-neutral-700">{{ userStore.displayName }}</p>
              <p class="truncate text-xs text-neutral-400">{{ userStore.subtitle }}</p>
            </div>
          </div>
          <div class="mt-3 grid gap-1.5 rounded-lg bg-neutral-50 p-2 text-xs text-neutral-500">
            <p class="flex min-w-0 items-center gap-2">
              <i class="fa fa-at w-3 text-center text-neutral-400"></i>
              <span class="truncate">{{ profile.account || '账号加载中' }}</span>
            </p>
            <p class="flex min-w-0 items-center gap-2">
              <i class="fa fa-envelope-o w-3 text-center text-neutral-400"></i>
              <span class="truncate">{{ profile.email || '邮箱未绑定' }}</span>
            </p>
            <p class="flex min-w-0 items-center gap-2">
              <i class="fa fa-mobile w-3 text-center text-neutral-400"></i>
              <span class="truncate">{{ maskedPhone }}</span>
            </p>
          </div>
          <p v-if="userStore.error" class="mt-2 text-[11px] text-warning">资料暂未同步，已使用占位信息</p>
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
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { useToastStore } from '@/stores/toastStore'
import { useUserStore } from '@/stores/userStore'

const router = useRouter()
const authStore = useAuthStore()
const toastStore = useToastStore()
const userStore = useUserStore()
const open = ref(false)
const panelRef = ref(null)
const isHoverDevice = ref(false)

const profile = computed(() => userStore.profile)
const maskedPhone = computed(() => {
  const phone = profile.value.phone_number
  if (!phone) return '手机号未绑定'
  return phone.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2')
})
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
  userStore.clearProfile()
  authStore.logout()
  toastStore.showToast('已退出登录', 'success')
  router.push('/login')
}

onMounted(() => {
  syncPointerMode()
  if (authStore.isLoggedIn) userStore.fetchProfile()
  window.addEventListener('resize', syncPointerMode)
  document.addEventListener('pointerdown', handlePointerDown)
})

watch(() => authStore.isLoggedIn, (loggedIn) => {
  if (loggedIn) userStore.fetchProfile({ force: true })
  else userStore.clearProfile()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', syncPointerMode)
  document.removeEventListener('pointerdown', handlePointerDown)
})
</script>

<style scoped>
.user-avatar {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border-radius: 9999px;
  background: rgba(22, 93, 255, 0.1);
  color: #165dff;
  font-weight: 700;
  box-shadow: inset 0 0 0 1px rgba(22, 93, 255, 0.12);
}

.user-avatar img {
  height: 100%;
  width: 100%;
  object-fit: cover;
}
</style>
