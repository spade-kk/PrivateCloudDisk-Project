<template>
  <view class="favorites-page">
    <!-- 页面头部 -->
    <view class="page-header">
      <text class="page-title">我的收藏</text>
      <text class="page-count" v-if="list.length > 0">{{ list.length }} 项</text>
    </view>

    <view class="file-list">
      <FileItem
        v-for="(node, idx) in list"
        :key="node.node_id"
        :node="node"
        :style="{ '--item-index': idx }"
        @click="handleItemClick"
        @longpress="handleLongPress"
      />
      <EmptyState
        v-if="!loading && list.length === 0"
        icon="star"
        text="暂无收藏"
        subText="长按文件可将文件添加至收藏"
      />
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
import { getFavoritesPaged, removeFavorite, removeFolderFavorite } from '@/api/favorite'
import { starredItemToNode } from '@/api/star'
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
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({ currentIndex: 1 })
    }
    if (!this.requireAuth()) return
    this.loadFavorites()
  },
  methods: {
    async loadFavorites() {
      this.loading = true
      try {
        const res = await getFavoritesPaged(1, 50)
        const rawList = Array.isArray(res.data) ? res.data : (res.data?.items || [])
        this.list = rawList.map(starredItemToNode)
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
          if (this.selectedItem.node_type === 'FOLDER') {
            await removeFolderFavorite(this.selectedItem.node_id)
          } else {
            await removeFavorite(this.selectedItem.node_id)
          }
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
.favorites-page {
  min-height: 100vh;
  background: $color-bg-page;
  padding-bottom: 140rpx;
}

.page-header {
  @include flex-between;
  padding: 24rpx 32rpx 16rpx;
}

.page-title {
  font-size: $font-size-subtitle;
  font-weight: $font-weight-semibold;
  color: $color-text-primary;
}

.page-count {
  font-size: $font-size-body-sm;
  color: $color-text-secondary;
}

.file-list {
  margin: 0 24rpx;
  background: $color-bg-card;
  border-radius: $card-radius;
  overflow: hidden;
  box-shadow: $shadow-md;
}
</style>