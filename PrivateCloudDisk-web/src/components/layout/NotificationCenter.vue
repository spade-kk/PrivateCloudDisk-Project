<template>
  <div
    ref="panelRef"
    class="relative"
    @mouseenter="handleMouseEnter"
    @mouseleave="handleMouseLeave"
  >
    <button
      @click="handleTriggerClick"
      class="icon-button relative"
      title="消息通知"
      aria-label="消息通知"
    >
      <i class="fa fa-bell text-lg"></i>
      <span
        v-if="unreadCount"
        class="absolute right-1 top-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-danger px-1 text-[10px] leading-none text-white ring-2 ring-white"
      >
        {{ unreadCount > 9 ? '9+' : unreadCount }}
      </span>
    </button>

    <transition name="dropdown-pop">
      <div
        v-if="open"
        class="absolute right-0 top-[calc(100%+0.75rem)] z-30 w-[min(22rem,calc(100vw-2rem))] overflow-hidden rounded-xl border border-neutral-200 bg-white shadow-lg"
      >
        <div class="flex items-center justify-between border-b border-neutral-200 px-4 py-3">
          <div>
            <div class="font-semibold text-neutral-700">消息通知</div>
            <div class="mt-0.5 text-xs text-neutral-400">{{ unreadCount }} 条未读消息</div>
          </div>
          <button
            @click="notificationStore.markAllAsRead"
            class="text-xs text-primary hover:text-primary/80"
          >
            全部已读
          </button>
        </div>

        <div v-if="recentNotifications.length === 0" class="px-4 py-8 text-center text-sm text-neutral-400">
          暂无通知
        </div>
        <div v-else class="max-h-80 overflow-y-auto">
          <button
            v-for="item in recentNotifications"
            :key="item.id"
            @click="openDetail(item.id)"
            class="flex w-full gap-3 border-b border-neutral-100 px-4 py-3 text-left transition last:border-b-0 hover:bg-neutral-50"
          >
            <div class="relative mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-lg" :class="typeMeta(item.type).bg">
              <i :class="[typeMeta(item.type).icon, typeMeta(item.type).color]"></i>
              <span v-if="!item.read" class="absolute -right-1 -top-1 h-2.5 w-2.5 rounded-full bg-danger ring-2 ring-white"></span>
            </div>
            <div class="min-w-0 flex-1">
              <div class="flex items-center justify-between gap-2">
                <p class="truncate text-sm font-medium text-neutral-700">{{ item.title }}</p>
                <span class="shrink-0 text-xs text-neutral-400">{{ timeAgo(item.time) }}</span>
              </div>
              <p class="mt-1 line-clamp-2 text-xs leading-5 text-neutral-500">{{ item.message }}</p>
            </div>
          </button>
        </div>

        <router-link
          to="/notifications"
          class="flex items-center justify-center gap-2 border-t border-neutral-200 px-4 py-3 text-sm text-primary transition hover:bg-primary/5"
          @click="open = false"
        >
          <span>查看全部消息</span>
          <i class="fa fa-angle-right"></i>
        </router-link>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useNotificationStore } from '@/stores/notificationStore'
import { timeAgo } from '@/utils/helpers'

const router = useRouter()
const notificationStore = useNotificationStore()
const panelRef = ref(null)
const open = ref(false)
const isHoverDevice = ref(false)

const unreadCount = computed(() => notificationStore.unreadCount)
const recentNotifications = computed(() => notificationStore.recentNotifications)

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

function openDetail(id) {
  notificationStore.markAsRead(id)
  open.value = false
  router.push('/notifications')
}

function typeMeta(type) {
  const map = {
    success: { icon: 'fa fa-check', bg: 'bg-success/10', color: 'text-success' },
    warning: { icon: 'fa fa-exclamation-triangle', bg: 'bg-warning/10', color: 'text-warning' },
    security: { icon: 'fa fa-shield', bg: 'bg-primary/10', color: 'text-primary' },
    info: { icon: 'fa fa-info', bg: 'bg-neutral-100', color: 'text-neutral-500' },
  }
  return map[type] || map.info
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
