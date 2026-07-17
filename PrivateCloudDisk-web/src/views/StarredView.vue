<template>
  <div class="space-y-4 sm:space-y-6">
    <!-- 标题栏 -->
    <div class="flex items-center justify-between">
      <h1 class="text-xl font-bold sm:text-2xl">
        <i class="fa fa-star text-warning"></i> 我的收藏
      </h1>
      <span class="text-sm text-neutral-400">共 {{ starredStore.starredCount }} 项</span>
    </div>

    <!-- 加载中 -->
    <div v-if="loading"><LoadingSpinner /></div>

    <!-- 空状态 -->
    <div v-else-if="starredNodes.length === 0" class="responsive-panel p-8 text-center text-neutral-400 sm:p-10">
      <i class="fa fa-star-o text-4xl mb-2"></i>
      <p>暂无收藏的文件或文件夹</p>
      <p class="text-xs mt-1">点击文件或文件夹旁的星标即可收藏</p>
    </div>

    <!-- 收藏列表 -->
    <div v-else>
      <FileGridView
        :nodes="starredNodes"
        :starredIds="starredStore.allStarredIds"
        @itemClick="onNodeClick"
        @star="onStarClick"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import FileGridView from '@/components/file/FileGridView.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import { useStarredStore } from '@/stores/starred'
import { useToastStore } from '@/stores/toastStore'
import { useRouter } from 'vue-router'
import { isVideo } from '@/utils/previewHelper'

const starredStore = useStarredStore()
const toastStore = useToastStore()
const router = useRouter()

const starredNodes = ref<any[]>([])
const loading = ref(false)

// ============================================================
// 加载收藏列表
// ============================================================

const loadStarred = async () => {
  loading.value = true
  try {
    starredNodes.value = await starredStore.fetchStarredNodes()
  } catch (err: any) {
    toastStore.showError('加载收藏列表失败')
  } finally {
    loading.value = false
  }
}

// ============================================================
// 事件处理
// ============================================================

/** 点击收藏项 → 根据类型跳转 */
const onNodeClick = (node: any) => {
  if (node.node_type === 'FOLDER') {
    router.push(`/?folder=${node.node_id}`)
    return
  }

  const fileName = node.node_name || node.name || ''

  // 视频文件：跳转至专属流媒体播放页面，携带 fileId 参数
  if (isVideo(fileName)) {
    router.push({
      name: 'VideoPlayer',
      params: { fileId: node.node_id },
      query: { name: encodeURIComponent(fileName) }
    })
    return
  }

  router.push(`/preview/${node.node_id}`)
}

/** 点击星标按钮 → 取消收藏 */
const onStarClick = async (node: any) => {
  try {
    await starredStore.toggleStar(node.node_id, node.node_type)
    // 从列表中移除
    starredNodes.value = starredNodes.value.filter(n => n.node_id !== node.node_id)
    toastStore.showSuccess('已取消收藏')
  } catch (err: any) {
    toastStore.showError('操作失败')
  }
}

onMounted(loadStarred)
</script>