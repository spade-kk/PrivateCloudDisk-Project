<template>
  <div class="space-y-4 sm:space-y-6">
    <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
      <div>
        <h1 class="text-xl font-bold sm:text-2xl">分享管理</h1>
        <p class="mt-1 text-sm text-neutral-500">管理您创建的所有分享链接</p>
      </div>
      <button
        @click="showCreateDialog = true"
        class="touch-button flex items-center justify-center gap-2 rounded-lg bg-primary px-4 py-2 text-white hover:bg-primary/90"
      >
        <i class="fa fa-plus"></i><span>新建分享</span>
      </button>
    </div>

    <div v-if="shareStore.loading" class="flex justify-center py-10">
      <LoadingSpinner />
    </div>

    <div v-else-if="shareStore.shares.length === 0" class="responsive-panel p-8 text-center text-neutral-400 sm:p-10">
      <i class="fa fa-share-alt mb-2 text-4xl"></i>
      <p class="text-lg font-medium">暂无分享链接</p>
      <p class="mt-1 text-sm">点击"新建分享"创建您的第一个分享链接</p>
    </div>

    <div v-else class="space-y-3">
      <div class="mb-3 flex items-center gap-2 text-sm text-neutral-500">
        <i class="fa fa-filter"></i>
        <span>共 {{ shareStore.shares.length }} 个分享</span>
      </div>
      <ShareLinkItem
        v-for="share in shareStore.shares"
        :key="share.share_id"
        :share="share"
        @revoke="handleRevoke"
      />
    </div>

    <CreateShareDialog
      :visible="showCreateDialog"
      @close="showCreateDialog = false"
      @created="handleCreated"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import ShareLinkItem from '@/components/share/ShareLinkItem.vue'
import CreateShareDialog from '@/components/share/CreateShareDialog.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import { useShareStore } from '@/stores/shareStore'

const shareStore = useShareStore()
const showCreateDialog = ref(false)

const handleRevoke = async (share_id: string) => {
  await shareStore.revokeShare(share_id)
}

const handleCreated = () => {
  showCreateDialog.value = false
}

onMounted(() => {
  shareStore.fetchMyShares()
})
</script>