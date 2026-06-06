<template>
  <div class="space-y-4 sm:space-y-6">
    <h1 class="text-xl font-bold sm:text-2xl"><i class="fa fa-star text-warning"></i> 我的收藏</h1>
    <div v-if="loading"><LoadingSpinner /></div>
    <div v-else-if="starredNodes.length === 0" class="responsive-panel p-8 text-center text-neutral-400 sm:p-10">
      <i class="fa fa-star-o text-4xl mb-2"></i><p>暂无收藏的文件或文件夹</p>
    </div>
    <div v-else>
      <FileGridView :nodes="starredNodes" @itemClick="onNodeClick" />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import FileGridView from '@/components/file/FileGridView.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import { useStarredStore } from '@/stores/starred'
import { useRouter } from 'vue-router'

const starredStore = useStarredStore()
const router = useRouter()
const starredNodes = ref([])
const loading = ref(false)

const loadStarred = async () => {
  loading.value = true
  starredNodes.value = await starredStore.fetchStarredNodes()
  loading.value = false
}
const onNodeClick = (node) => {
  if (node.node_type === 'FOLDER') router.push(`/?folder=${node.node_id}`)
  else router.push(`/preview/${node.node_id}`)
}
onMounted(loadStarred)
</script>
