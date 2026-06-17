<template>
  <div class="space-y-4">
    <div v-if="items.length === 0" class="py-8 text-center text-sm text-neutral-400">
      <i class="fa fa-clock-o text-2xl"></i>
      <p class="mt-2">暂无记录</p>
    </div>
    <div v-for="(item, index) in items" :key="item.id || index" class="flex gap-4">
      <!-- 时间线节点 -->
      <div class="flex flex-col items-center">
        <div :class="['flex h-8 w-8 shrink-0 items-center justify-center rounded-full', getDotClass(item.type)]">
          <i :class="getIcon(item.type)" class="text-sm"></i>
        </div>
        <div v-if="index < items.length - 1" class="mt-1 w-0.5 flex-1 bg-neutral-200"></div>
      </div>
      <!-- 内容 -->
      <div class="flex-1 pb-6">
        <div class="flex items-start justify-between gap-2">
          <div>
            <p class="text-sm font-medium text-neutral-700">
              <slot name="title" :item="item">{{ item.title }}</slot>
            </p>
            <p class="mt-1 text-xs text-neutral-400">
              <slot name="description" :item="item">{{ item.description }}</slot>
            </p>
          </div>
          <span class="shrink-0 text-xs text-neutral-400">
            <slot name="time" :item="item">{{ formatTime(item.time) }}</slot>
          </span>
        </div>
        <div v-if="$slots.extra" class="mt-2">
          <slot name="extra" :item="item"></slot>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps({
  items: { type: Array, default: () => [] },
})

function getDotClass(type) {
  const map = {
    success: 'bg-success/10 text-success',
    warning: 'bg-warning/10 text-warning',
    error: 'bg-danger/10 text-danger',
    danger: 'bg-danger/10 text-danger',
    info: 'bg-info/10 text-info',
    primary: 'bg-primary/10 text-primary',
    login: 'bg-success/10 text-success',
    logout: 'bg-neutral-100 text-neutral-500',
    upload: 'bg-primary/10 text-primary',
    download: 'bg-info/10 text-info',
    delete: 'bg-danger/10 text-danger',
    create: 'bg-success/10 text-success',
    update: 'bg-warning/10 text-warning',
    share: 'bg-purple-100 text-purple-600',
    security: 'bg-danger/10 text-danger',
  }
  return map[type] || 'bg-neutral-100 text-neutral-500'
}

function getIcon(type) {
  const map = {
    success: 'fa fa-check',
    warning: 'fa fa-exclamation',
    error: 'fa fa-times',
    danger: 'fa fa-exclamation-triangle',
    info: 'fa fa-info',
    login: 'fa fa-sign-in',
    logout: 'fa fa-sign-out',
    upload: 'fa fa-upload',
    download: 'fa fa-download',
    delete: 'fa fa-trash',
    create: 'fa fa-plus',
    update: 'fa fa-pencil',
    share: 'fa fa-share-alt',
    security: 'fa fa-shield',
  }
  return map[type] || 'fa fa-circle'
}

function formatTime(time) {
  if (!time) return ''
  try {
    const d = new Date(time)
    const now = new Date()
    const diff = now - d
    if (diff < 60000) return '刚刚'
    if (diff < 3600000) return Math.floor(diff / 60000) + ' 分钟前'
    if (diff < 86400000) return Math.floor(diff / 3600000) + ' 小时前'
    return d.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
  } catch { return time }
}
</script>