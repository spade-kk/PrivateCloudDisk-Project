<template>
  <div class="space-y-4">
    <div class="flex justify-between items-center">
      <h1 class="text-2xl font-bold">分享管理</h1>
      <button @click="showCreateDialog = true" class="bg-primary text-white px-4 py-2 rounded-lg flex items-center space-x-1">
        <i class="fa fa-plus"></i><span>新建分享</span>
      </button>
    </div>
    <div v-if="loading" class="flex justify-center py-10"><LoadingSpinner /></div>
    <div v-else-if="shares.length === 0" class="bg-white rounded-lg shadow-card p-10 text-center text-neutral-400">
      <i class="fa fa-share-alt text-4xl mb-2"></i><p>暂无分享链接</p>
    </div>
    <div v-else class="space-y-3">
      <ShareLinkItem v-for="share in shares" :key="share.id" :share="share" @revoke="revokeShare" />
    </div>
    <CreateShareDialog :visible="showCreateDialog" @close="showCreateDialog = false" @created="loadShares" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import ShareLinkItem from '@/components/share/ShareLinkItem.vue'
import CreateShareDialog from '@/components/share/CreateShareDialog.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import { useShareStore } from '@/stores/share'

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