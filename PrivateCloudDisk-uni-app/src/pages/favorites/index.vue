<template>
  <view class="favorites-page">
    <view class="file-list">
      <FileItem
        v-for="node in list"
        :key="node.node_id"
        :node="node"
        @click="handleItemClick"
        @longpress="handleLongPress"
      />
      <EmptyState v-if="!loading && list.length === 0" icon="star" text="暂无收藏" subText="长按文件可将文件添加至收藏" />
      <LoadingOverlay :visible="loading" text="加载中..." />
    </view>

    <u-action-sheet
      :show="menuShow"
      :actions="[{ name: '取消收藏', value: 'unfavorite' }]"
      @select="handleMenuSelect"
      @close="menuShow = false"
    />
  </view>
</template>

<script>
import { useUserAuth } from '@/composables/useUserAuth'
import { getFavoritesPaged, removeFavorite } from '@/api/favorite'
import FileItem from '@/components/file/FileItem.vue'
import EmptyState from '@/components/file/EmptyState.vue'
import LoadingOverlay from '@/components/common/LoadingOverlay.vue'

export default {
  components: { FileItem, EmptyState, LoadingOverlay },
  setup() {
    const { requireAuth } = useUserAuth()
    return { requireAuth }
  },
  data() {
    return { list: [], loading: true, menuShow: false, selectedItem: null }
  },
  onShow() {
    if (!this.requireAuth()) return
    this.loadFavorites()
  },
  methods: {
    async loadFavorites() {
      this.loading = true
      try {
        const res = await getFavoritesPaged(1, 50)
        this.list = res.data?.items || []
      } catch (e) {
        // 错误已处理
      } finally {
        this.loading = false
      }
    },
    handleItemClick(node) {
      if (node.node_type === 'FOLDER') {
        uni.switchTab({ url: '/pages/index/index' })
      } else {
        uni.navigateTo({
          url: `/pages/file-detail/index?fileId=${node.node_id}&fileName=${encodeURIComponent(node.node_name)}`
        })
      }
    },
    handleLongPress(node) {
      this.selectedItem = node
      this.menuShow = true
    },
    async handleMenuSelect(action) {
      if (action.value === 'unfavorite' && this.selectedItem) {
        try {
          await removeFavorite(this.selectedItem.node_id)
          uni.showToast({ title: '已取消收藏', icon: 'success' })
          this.loadFavorites()
        } catch (e) { /* 已处理 */ }
      }
      this.menuShow = false
    }
  }
}
</script>

<style lang="scss" scoped>
.favorites-page { min-height: 100vh; background: #f5f5f5; }
.file-list { margin: 16rpx 24rpx; background: #fff; border-radius: 16rpx; overflow: hidden; box-shadow: 0 2rpx 12rpx rgba(0,0,0,0.06); }
</style>