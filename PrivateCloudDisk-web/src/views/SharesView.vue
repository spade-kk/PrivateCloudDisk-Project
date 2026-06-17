<template>
  <div class="space-y-4 sm:space-y-6">
    <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
      <h1 class="text-xl font-bold sm:text-2xl">分享管理</h1>
      <button @click="showCreateDialog = true" class="touch-button flex items-center justify-center gap-2 rounded-lg bg-primary px-4 py-2 text-white">
        <i class="fa fa-plus"></i><span>新建分享</span>
      </button>
    </div>
    <div v-if="loading" class="flex justify-center py-10"><LoadingSpinner /></div>
    <div v-else-if="shares.length === 0" class="responsive-panel p-8 text-center text-neutral-400 sm:p-10">
      <i class="fa fa-share-alt text-4xl mb-2"></i><p>暂无分享链接</p>
    </div>
    <div v-else class="space-y-3">
      <ShareLinkItem v-for="share in shares" :key="share.id" :share="share" @revoke="revokeShare" />
    </div>
    <CreateShareDialog :visible="showCreateDialog" @close="showCreateDialog = false" @created="loadShares" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import ShareLinkItem from '@/components/share/ShareLinkItem.vue'
import CreateShareDialog from '@/components/share/CreateShareDialog.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import { useShareStore } from '@/stores/shareStore'

const shareStore = useShareStore()
const shares = ref([])
const loading = ref(false)
const showCreateDialog = ref(false)

const loadShares = async () => {
  loading.value = true
  shares.value = await shareStore.fetchMyShares()
  loading.value = false
}
const revokeShare = async (id) => {
  await shareStore.revokeShare(id)
  await loadShares()
}
onMounted(loadShares)
</script>
