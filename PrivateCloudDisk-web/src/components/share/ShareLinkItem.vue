<template>
  <div class="responsive-panel p-4">
    <div class="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
      <div class="min-w-0 flex-1">
        <div class="flex min-w-0 items-center gap-3">
          <div
            class="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg"
            :class="statusIconClass"
          >
            <i :class="statusIcon"></i>
          </div>
          <div class="min-w-0">
            <h3 class="truncate font-medium text-neutral-700">{{ share.share_name }}</h3>
            <p class="mt-0.5 text-xs text-neutral-500">
              <span class="mr-2">
                <i :class="share.share_target_type === 'folder' ? 'fa fa-folder' : 'fa fa-file'" class="mr-1"></i>
                {{ share.target_name || (share.share_target_type === 'folder' ? '文件夹' : '文件') }}
              </span>
              <span
                class="inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium"
                :style="{ color: getShareStatusColor(share.share_status), backgroundColor: getShareStatusColor(share.share_status) + '15' }"
              >
                {{ getShareStatusText(share.share_status) }}
              </span>
            </p>
          </div>
        </div>
        <div class="mt-3 flex flex-wrap gap-x-4 gap-y-2 text-xs text-neutral-500">
          <span><i class="fa fa-clock-o mr-1"></i>{{ expiresText }}</span>
          <span><i class="fa fa-eye mr-1"></i>{{ share.share_view_count || 0 }} 次访问</span>
          <span v-if="share.share_has_password"><i class="fa fa-lock mr-1"></i>有密码</span>
          <span v-if="share.file_type"><i class="fa fa-file-o mr-1"></i>{{ share.file_type }}</span>
          <span v-if="share.target_size"><i class="fa fa-database mr-1"></i>{{ formatSize(share.target_size) }}</span>
        </div>
      </div>
      <div class="grid grid-cols-2 gap-2 sm:flex sm:items-center">
        <button
          @click="copyLink"
          class="touch-button rounded-lg border border-primary px-3 py-2 text-sm text-primary hover:bg-primary/10"
        >
          <i class="fa fa-copy mr-1"></i>复制链接
        </button>
        <button
          v-if="share.share_status === 'active'"
          @click="$emit('revoke', share.share_id)"
          class="touch-button rounded-lg border border-danger px-3 py-2 text-sm text-danger hover:bg-danger/10"
        >
          <i class="fa fa-chain-broken mr-1"></i>取消分享
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { formatFileSize, formatShareUrl, getShareStatusText, getShareStatusColor } from '@/api/modules/shares'
import type { ShareLinkItem } from '@/api/modules/shares'

const props = defineProps<{
  share: ShareLinkItem
}>()
defineEmits<{
  revoke: [share_id: string]
}>()

const statusIcon = computed(() => {
  if (props.share.share_status === 'revoked') return 'fa fa-ban'
  if (props.share.share_status === 'expired') return 'fa fa-clock-o'
  return 'fa fa-link'
})

const statusIconClass = computed(() => {
  if (props.share.share_status === 'revoked') return 'bg-danger/10 text-danger'
  if (props.share.share_status === 'expired') return 'bg-warning/10 text-warning'
  return 'bg-primary/10 text-primary'
})

const expiresText = computed(() => {
  if (props.share.share_status === 'revoked') return '已撤销'
  if (props.share.share_status === 'expired') return '已过期'
  if (!props.share.share_expires_at) return '永久有效'
  const d = new Date(props.share.share_expires_at)
  if (d.getTime() < Date.now()) return '已过期'
  return `有效期至 ${d.toLocaleDateString('zh-CN')}`
})

const formatSize = (bytes: number) => formatFileSize(bytes)

const copyLink = async () => {
  try {
    const url = formatShareUrl(props.share.share_token)
    await navigator.clipboard.writeText(url)
  } catch {
    // fallback
  }
}
</script>