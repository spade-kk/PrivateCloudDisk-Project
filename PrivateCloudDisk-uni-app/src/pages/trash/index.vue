<template>
  <view class="trash-page">
    <view class="trash-header">
      <text class="trash-tip">回收站中的文件将在 30 天后自动清除</text>
      <u-button
        v-if="list.length > 0"
        type="error"
        size="small"
        text="清空回收站"
        @click="handleEmptyTrash"
      />
    </view>
    <view class="file-list">
      <FileItem
        v-for="node in list"
        :key="node.node_id"
        :node="node"
        @click="handleItemClick"
      >
        <template #action>
          <u-button type="primary" size="mini" text="恢复" @click.stop="handleRestore(node)" />
        </template>
      </FileItem>
      <EmptyState v-if="!loading && list.length === 0" icon="trash" text="回收站为空" />
      <LoadingOverlay :visible="loading" text="加载中..." />
    </view>
  </view>
</template>

<script>
import { useUserAuth } from '@/composables/useUserAuth'
import { getTrashListPaged, restoreFile, restoreFolder, emptyTrash } from '@/api/trash'
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
    if (!this.requireAuth()) return
    this.loadTrash()
  },
  methods: {
    async loadTrash() {
      this.loading = true
      try {
        const res = await getTrashListPaged(1, 50)
        this.list = res.data?.items || []
      } catch (e) { /* 已处理 */ } finally {
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
        if (node.node_type === 'FOLDER') {
          await restoreFolder(node.node_id)
        } else {
          await restoreFile(node.node_id)
        }
        uni.showToast({ title: '已恢复', icon: 'success' })
        this.loadTrash()
      } catch (e) { /* 已处理 */ }
    },
    async handleEmptyTrash() {
      const res = await uni.showModal({
        title: '确认清空',
        content: '清空后所有文件将永久删除，无法恢复。确定清空吗？'
      })
      if (res.confirm) {
        try {
          await emptyTrash()
          uni.showToast({ title: '已清空回收站', icon: 'success' })
          this.loadTrash()
        } catch (e) { /* 已处理 */ }
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.trash-page { min-height: 100vh; background: #f5f5f5; }
.trash-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20rpx 32rpx;
  background: #fff;
  border-bottom: 1rpx solid #f0f0f0;
}
.trash-tip { font-size: 24rpx; color: #9aa0a6; }
.file-list { margin: 16rpx 24rpx; background: #fff; border-radius: 16rpx; overflow: hidden; box-shadow: 0 2rpx 12rpx rgba(0,0,0,0.06); }
</style>