<template>
  <div class="space-y-4 sm:space-y-6">
    <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
      <div>
        <h1 class="text-xl font-bold sm:text-2xl">消息中心</h1>
        <p class="mt-1 text-sm text-neutral-500">系统通知、账号安全、文件任务与分享动态</p>
      </div>
      <button
        @click="notificationStore.markAllAsRead"
        class="touch-button inline-flex items-center justify-center gap-2 rounded-lg border border-primary px-4 py-2 text-sm text-primary transition hover:bg-primary/10"
      >
        <i class="fa fa-check-circle-o"></i>
        <span>全部标为已读</span>
      </button>
    </div>

    <div class="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
      <div
        v-for="stat in stats"
        :key="stat.label"
        class="rounded-lg border border-neutral-200 bg-white p-4 shadow-card"
      >
        <div class="flex items-center justify-between gap-3">
          <div>
            <p class="text-xs text-neutral-500">{{ stat.label }}</p>
            <p class="mt-1 text-2xl font-semibold text-neutral-700">{{ stat.value }}</p>
          </div>
          <div class="flex h-10 w-10 items-center justify-center rounded-lg" :class="stat.bg">
            <i :class="[stat.icon, stat.color]"></i>
          </div>
        </div>
      </div>
    </div>

    <div class="overflow-hidden rounded-lg bg-white shadow-card">
      <div class="flex items-center justify-between border-b border-neutral-200 bg-neutral-50 px-4 py-3">
        <div class="font-medium text-neutral-700">全部消息</div>
        <div class="text-sm text-neutral-500">{{ unreadCount }} 条未读</div>
      </div>

      <div v-if="notifications.length === 0" class="p-10">
        <PageState title="暂无消息" description="系统通知和文件动态会显示在这里" />
      </div>

      <div v-else class="divide-y divide-neutral-200">
        <button
          v-for="item in notifications"
          :key="item.id"
          @click="notificationStore.markAsRead(item.id)"
          class="group flex w-full gap-3 px-4 py-4 text-left transition hover:bg-neutral-50"
        >
          <div class="relative mt-1 flex h-10 w-10 shrink-0 items-center justify-center rounded-lg" :class="typeMeta(item.type).bg">
            <i :class="[typeMeta(item.type).icon, typeMeta(item.type).color]"></i>
            <span v-if="!item.read" class="absolute -right-1 -top-1 h-2.5 w-2.5 rounded-full bg-danger ring-2 ring-white"></span>
          </div>
          <div class="min-w-0 flex-1">
            <div class="flex flex-col gap-1 sm:flex-row sm:items-center sm:justify-between">
              <div class="flex min-w-0 items-center gap-2">
                <h3 class="truncate font-medium text-neutral-700">{{ item.title }}</h3>
                <span class="shrink-0 rounded-full bg-neutral-100 px-2 py-0.5 text-xs text-neutral-500">{{ item.category }}</span>
              </div>
              <span class="shrink-0 text-xs text-neutral-400">{{ timeAgo(item.time) }}</span>
            </div>
            <p class="mt-2 text-sm leading-6 text-neutral-500">{{ item.message }}</p>
          </div>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import PageState from '@/components/common/PageState.vue'
import { useNotificationStore } from '@/stores/notificationStore'
import { timeAgo } from '@/utils/helpers'

const notificationStore = useNotificationStore()
const notifications = computed(() => notificationStore.notifications)
const unreadCount = computed(() => notificationStore.unreadCount)

const stats = computed(() => [
  { label: '全部消息', value: notifications.value.length, icon: 'fa fa-bell', bg: 'bg-primary/10', color: 'text-primary' },
  { label: '未读消息', value: unreadCount.value, icon: 'fa fa-circle', bg: 'bg-danger/10', color: 'text-danger' },
  { label: '安全提醒', value: notifications.value.filter(item => item.type === 'security').length, icon: 'fa fa-shield', bg: 'bg-warning/10', color: 'text-warning' },
  { label: '文件动态', value: notifications.value.filter(item => item.category === '文件').length, icon: 'fa fa-file-o', bg: 'bg-secondary/10', color: 'text-secondary' },
])

function typeMeta(type) {
  const map = {
    success: { icon: 'fa fa-check', bg: 'bg-success/10', color: 'text-success' },
    warning: { icon: 'fa fa-exclamation-triangle', bg: 'bg-warning/10', color: 'text-warning' },
    security: { icon: 'fa fa-shield', bg: 'bg-primary/10', color: 'text-primary' },
    info: { icon: 'fa fa-info', bg: 'bg-neutral-100', color: 'text-neutral-500' },
  }
  return map[type] || map.info
}
</script>
