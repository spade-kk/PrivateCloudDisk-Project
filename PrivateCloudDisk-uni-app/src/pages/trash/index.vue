<template>
  <view class="trash-page">
    <!-- 页面头部 -->
    <view class="page-header">
      <text class="page-title">回收站</text>
      <view class="header-actions">
        <view class="trash-tip-badge">
          <u-icon name="info-circle" size="28" color="#FFB347" />
          <text class="trash-tip-text">30天后自动清除</text>
        </view>
        <u-button
          v-if="list.length > 0"
          type="error"
          size="small"
          text="清空"
          @click="handleEmptyTrash"
          customStyle="height: 52rpx; font-size: 22rpx; border-radius: 26rpx;"
        />
      </view>
    </view>

    <view class="file-list">
      <FileItem
        v-for="(node, idx) in list"
        :key="node.node_id"
        :node="node"
        :style="{ '--item-index': idx }"
        @click="handleItemClick"
      >
        <template #action>
          <view class="trash-actions">
            <u-button
              type="primary"
              size="mini"
              text="恢复"
              @click.stop="handleRestore(node)"
              customStyle="height: 48rpx; font-size: 20rpx; border-radius: 24rpx; margin-right: 8rpx;"
            />
            <u-button
              type="error"
              size="mini"
              text="删除"
              @click.stop="handlePermanentDelete(node)"
              customStyle="height: 48rpx; font-size: 20rpx; border-radius: 24rpx;"
            />
          </view>
        </template>
      </FileItem>
      <EmptyState
        v-if="!loading && list.length === 0"
        icon="trash"
        text="回收站为空"
      />
      <LoadingOverlay :visible="loading" text="加载中..." />
    </view>
  </view>
</template>

<script>
import { useUserAuth } from '@/composables/useUserAuth'
import { getTrashListPaged, restoreFromTrash, permanentDelete, emptyTrash, trashItemToNode } from '@/api/trash'
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
    return { list: [], loading: true }
  },
  onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({ currentIndex: 2 })
    }
    if (!this.requireAuth()) return
    this.loadTrash()
  },
  methods: {
    async loadTrash() {
      this.loading = true
      try {
        const res = await getTrashListPaged(1, 50)
        const rawList = Array.isArray(res.data) ? res.data : (res.data?.items || [])
        this.list = rawList.map(trashItemToNode)
      } catch (e) {
        console.error('[Trash] 加载失败:', e)
      } finally {
        this.loading = false
      }
    },
    handleItemClick(node) {
      if (node.node_type === 'FOLDER') {
        uni.showModal({
          title: '提示',
          content: '回收站中的文件夹无法直接查看，请先恢复',
          showCancel: false
        })
      } else {
        uni.navigateTo({
          url: `/pages/file-detail/index?fileId=${node.node_id}&fileName=${encodeURIComponent(node.node_name)}`
        })
      }
    },
    async handleRestore(node) {
      try {
        await restoreFromTrash(node.trash_id)
        uni.showToast({ title: '已恢复', icon: 'success' })
        this.loadTrash()
      } catch (e) {
        console.error('[Trash] 恢复失败:', e)
        uni.showToast({ title: e.message || '恢复失败', icon: 'none' })
      }
    },
    async handlePermanentDelete(node) {
      const res = await uni.showModal({
        title: '永久删除',
        content: `确定要永久删除 "${node.node_name}" 吗？此操作无法恢复！`,
        confirmColor: '#ea4335'
      })
      if (res.confirm) {
        try {
          await permanentDelete(node.trash_id)
          uni.showToast({ title: '已永久删除', icon: 'success' })
          this.loadTrash()
        } catch (e) {
          console.error('[Trash] 永久删除失败:', e)
          uni.showToast({ title: e.message || '删除失败', icon: 'none' })
        }
      }
    },
    async handleEmptyTrash() {
      const res = await uni.showModal({
        title: '确认清空',
        content: '清空后所有文件将永久删除，无法恢复。确定清空吗？',
        confirmColor: '#ea4335'
      })
      if (res.confirm) {
        try {
          await emptyTrash()
          uni.showToast({ title: '已清空回收站', icon: 'success' })
          this.loadTrash()
        } catch (e) {
          console.error('[Trash] 清空失败:', e)
          uni.showToast({ title: e.message || '清空失败', icon: 'none' })
        }
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.trash-page {
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

.header-actions {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.trash-tip-badge {
  display: flex;
  align-items: center;
  padding: 6rpx 16rpx;
  background: $color-warning-light;
  border-radius: $radius-full;
  gap: 6rpx;
}

.trash-tip-text {
  font-size: 20rpx;
  color: #FFB347;
  font-weight: $font-weight-medium;
}

.file-list {
  margin: 0 24rpx;
  background: $color-bg-card;
  border-radius: $card-radius;
  overflow: hidden;
  box-shadow: $shadow-md;
}

.trash-actions {
  display: flex;
  align-items: center;
}
</style>