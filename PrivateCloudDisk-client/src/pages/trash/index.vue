<template>
  <view class="trash-page">
    <!-- 操作栏 -->
    <view class="action-bar flex-between">
      <text class="count-text">{{ totalCount }} 个文件</text>
      <u-button
        type="error"
        size="small"
        text="清空回收站"
        @click="handleEmptyTrash"
        :disabled="totalCount === 0"
      />
    </view>

    <!-- 回收站列表 -->
    <view class="trash-list">
      <view
        class="trash-item"
        v-for="item in trashList"
        :key="item.id"
      >
        <view class="item-main flex-between" @click="handleItemClick(item)">
          <view class="item-left">
            <u-icon
              :name="item.target_type === 'folder' ? 'folder' : getFileIcon(item.target_name)"
              size="44"
              :color="item.target_type === 'folder' ? '#1a73e8' : getFileIconColor(item.target_name)"
            />
            <view class="item-info">
              <text class="item-name ellipsis">{{ item.target_name }}</text>
              <text class="item-meta">{{ formatTime(item.deleted_at) }} · {{ item.deleted_by }}</text>
            </view>
          </view>
        </view>

        <!-- 操作按钮组 -->
        <view class="item-actions">
          <u-button
            type="primary"
            size="small"
            text="恢复"
            plain
            @click="handleRestore(item)"
          />
          <u-button
            type="error"
            size="small"
            text="彻底删除"
            plain
            @click="handlePermanentDelete(item)"
          />
        </view>
      </view>

      <u-empty
        v-if="!loading && trashList.length === 0"
        text="回收站为空"
        icon="trash"
      />
    </view>

    <view class="load-more" v-if="hasMore">
      <u-loading-icon v-if="loadingMore" size="20" text="加载中..." />
      <text v-else class="load-more-text" @click="loadMore">点击加载更多</text>
    </view>
  </view>
</template>

<script>
import { getTrashList, getTrashCount, restoreFromTrash, permanentDelete, emptyTrash } from '@/api/trash'
import { formatTime, getFileIcon, getFileIconColor } from '@/utils/helper'
import { PAGE_SIZE } from '@/utils/const'

export default {
  data() {
    return {
      loading: false,
      loadingMore: false,
      trashList: [],
      totalCount: 0,
      page: 1,
      hasMore: false
    }
  },
  onShow() {
    this.loadData()
  },
  methods: {
    getFileIcon,
    getFileIconColor,
    formatTime,

    async loadData() {
      this.loading = true
      try {
        const [listRes, countRes] = await Promise.all([
          getTrashList({ page: 1, pageSize: PAGE_SIZE }),
          getTrashCount()
        ])
        this.trashList = listRes.data || []
        this.totalCount = countRes.data || 0
        this.hasMore = (listRes.data?.length || 0) >= PAGE_SIZE
        this.page = 1
      } catch (e) {
      } finally {
        this.loading = false
      }
    },

    async loadMore() {
      if (this.loadingMore || !this.hasMore) return
      this.loadingMore = true
      try {
        const res = await getTrashList({ page: this.page + 1, pageSize: PAGE_SIZE })
        this.trashList.push(...(res.data || []))
        this.hasMore = (res.data?.length || 0) >= PAGE_SIZE
        this.page++
      } catch (e) {
      } finally {
        this.loadingMore = false
      }
    },

    async handleRestore(item) {
      try {
        await restoreFromTrash(item.id)
        this.trashList = this.trashList.filter(t => t.id !== item.id)
        this.totalCount--
        uni.showToast({ title: '已恢复', icon: 'success' })
      } catch (e) {}
    },

    async handlePermanentDelete(item) {
      uni.showModal({
        title: '彻底删除',
        content: `确定要永久删除「${item.target_name}」吗？此操作不可撤销！`,
        confirmColor: '#ea4335',
        success: async (res) => {
          if (res.confirm) {
            try {
              await permanentDelete(item.id)
              this.trashList = this.trashList.filter(t => t.id !== item.id)
              this.totalCount--
              uni.showToast({ title: '已删除', icon: 'success' })
            } catch (e) {}
          }
        }
      })
    },

    async handleEmptyTrash() {
      uni.showModal({
        title: '清空回收站',
        content: '确定要清空回收站吗？所有文件将永久删除，不可恢复！',
        confirmColor: '#ea4335',
        success: async (res) => {
          if (res.confirm) {
            try {
              await emptyTrash()
              this.trashList = []
              this.totalCount = 0
              uni.showToast({ title: '回收站已清空', icon: 'success' })
            } catch (e) {}
          }
        }
      })
    },

    handleItemClick(item) {
      // 回收站中的项目仅展示信息, 不可点击进入
      uni.showToast({ title: '回收站中的文件无法预览', icon: 'none' })
    }
  }
}
</script>

<style lang="scss" scoped>
.trash-page {
  min-height: 100vh;
  padding-bottom: 40rpx;
}

.action-bar {
  padding: 24rpx;
  background: $bg-white;
  margin: 20rpx 24rpx;
  border-radius: $radius-md;

  .count-text {
    font-size: 28rpx;
    font-weight: 600;
  }
}

.trash-list {
  background: $bg-white;
  margin: 0 24rpx;
  border-radius: $radius-md;
  overflow: hidden;
}

.trash-item {
  padding: 24rpx;
  border-bottom: 1rpx solid $border-color;

  &:last-child {
    border-bottom: none;
  }

  .item-main {
    .item-left {
      display: flex;
      align-items: center;
      flex: 1;

      .item-info {
        margin-left: 20rpx;
        flex: 1;

        .item-name {
          font-size: 28rpx;
          color: $text-primary;
        }

        .item-meta {
          font-size: 22rpx;
          color: $text-secondary;
          margin-top: 4rpx;
        }
      }
    }
  }

  .item-actions {
    display: flex;
    justify-content: flex-end;
    gap: 16rpx;
    margin-top: 16rpx;
  }
}

.load-more {
  padding: 24rpx;
  text-align: center;

  .load-more-text {
    color: $primary-color;
    font-size: 26rpx;
  }
}
</style>