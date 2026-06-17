<template>
  <div class="responsive-panel p-4">
    <div class="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
      <div class="min-w-0 flex-1">
        <div class="flex min-w-0 items-center gap-3">
          <div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
            <i class="fa fa-link"></i>
          </div>
          <div class="min-w-0">
            <h3 class="truncate font-medium text-neutral-700">{{ title }}</h3>
            <p class="mt-1 truncate text-sm text-neutral-500">{{ link }}</p>
          </div>
        </div>
        <div class="mt-3 flex flex-wrap gap-x-4 gap-y-2 text-xs text-neutral-500">
          <span><i class="fa fa-clock-o mr-1"></i>{{ expiresText }}</span>
          <span><i class="fa fa-eye mr-1"></i>{{ viewCount }} 次访问</span>
          <span v-if="shareCode"><i class="fa fa-key mr-1"></i>提取码 {{ shareCode }}</span>
        </div>
      </div>
      <div class="grid grid-cols-2 gap-2 sm:flex sm:items-center">
        <button @click="copyLink" class="touch-button rounded-lg border border-primary px-3 py-2 text-sm text-primary hover:bg-primary/10">
          <i class="fa fa-copy mr-1"></i>复制
        </button>
        <button @click="$emit('revoke', share.id)" class="touch-button rounded-lg border border-danger px-3 py-2 text-sm text-danger hover:bg-danger/10">
          <i class="fa fa-chain-broken mr-1"></i>取消
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { formatTime } from '@/utils/helpers'

const props = defineProps({
  share: { type: Object, required: true },
})
defineEmits(['revoke'])

const title = computed(() => props.share.node_name || props.share.fileName || props.share.title || '未命名分享')
const link = computed(() => props.share.url || props.share.link || props.share.share_url || `${window.location.origin}/share/${props.share.id || ''}`)
const shareCode = computed(() => props.share.code || props.share.share_code || props.share.password || '')
const viewCount = computed(() => props.share.view_count || props.share.views || 0)
const expiresText = computed(() => {
  const value = props.share.expires_at || props.share.expireTime || props.share.expired_at
  return value ? `有效期至 ${formatTime(value)}` : '永久有效'
})

const copyLink = async () => {
  await navigator.clipboard.writeText(link.value)
}
</script>
