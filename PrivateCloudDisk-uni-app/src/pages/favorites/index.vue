<template>
  <view class="favorites-page">
    <!-- 统计栏 -->
    <view class="count-bar flex-between">
      <text class="count-title">收藏的文件</text>
      <text class="count-num">{{ totalCount }} 个</text>
    </view>

    <!-- 文件列表 -->
    <view class="file-list">
      <view
        class="file-item flex-between"
        v-for="item in starList"
        :key="item.file_id"
        @click="handleItemClick(item)"
      >
        <view class="file-left">
          <u-icon
            :name="getFileIcon(item.file_name)"
            size="44"
            :color="getFileIconColor(item.file_name)"
          />
          <view class="file-info">
            <text class="file-name ellipsis">{{ item.file_name }}</text>
            <text class="file-meta">{{ formatTime(item.starred_at) }}</text>
          </view>
        </view>
        <u-button
          type="error"
          size="small"
          text="取消"
          @click.stop="handleRemoveStar(item)"
        />
      </view>

      <u-empty
        v-if="!loading && starList.length === 0"
        text="还没有收藏的文件"
        icon="star"
      />
    </view>

    <view class="load-more" v-if="hasMore">
      <u-loading-icon
        v-if="loadingMore"
        size="20"
        text="加载中..."
      />
      <text v-else class="load-more-text" @click="loadMore">点击加载更多</text>
    </view>
  </view>
</template>

<script>
import { getStarList, getStarCount, removeStar } from '@/api/star'
import { formatFileSize, formatTime, getFileIcon, getFileIconColor } from '@/utils/helper'
import { PAGE_SIZE } from '@/utils/const'

export default {
  data() {
    return {
      loading: false,
      loadingMore: false,
      starList: [],
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
          getStarList({ page: 1, pageSize: PAGE_SIZE }),
          getStarCount()
        ])
        this.starList = listRes.data || []
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
        const res = await getStarList({ page: this.page + 1, pageSize: PAGE_SIZE })
        const items = res.data || []
        this.starList.push(...items)
        this.hasMore = items.length >= PAGE_SIZE
        this.page++
      } catch (e) {
      } finally {
        this.loadingMore = false
      }
    },

    handleItemClick(item) {
      uni.navigateTo({
        url: `/pages/file-detail/index?fileId=${item.file_id}&fileName=${item.file_name}`
      })
    },

    async handleRemoveStar(item) {
      try {
        await removeStar(item.file_id)
        this.starList = this.starList.filter(s => s.file_id !== item.file_id)
        this.totalCount--
        uni.showToast({ title: '已取消收藏', icon: 'success' })
      } catch (e) {}
    }
  }
}
</script>

<style lang="scss" scoped>
.favorites-page {
  min-height: 100vh;
  padding-bottom: 40rpx;
}

.count-bar {
  padding: 24rpx;
  background: $bg-white;
  margin: 20rpx 24rpx;
  border-radius: $radius-md;

  .count-title {
    font-size: 28rpx;
    font-weight: 600;
  }

  .count-num {
    font-size: 24rpx;
    color: $text-secondary;
  }
}

.file-list {
  background: $bg-white;
  margin: 0 24rpx;
  border-radius: $radius-md;
  overflow: hidden;
}

.file-item {
  padding: 24rpx;
  border-bottom: 1rpx solid $border-color;

  &:last-child {
    border-bottom: none;
  }

  .file-left {
    display: flex;
    align-items: center;
    flex: 1;
    margin-right: 16rpx;

    .file-info {
      margin-left: 20rpx;
      flex: 1;

      .file-name {
        font-size: 28rpx;
        color: $text-primary;
      }

      .file-meta {
        font-size: 22rpx;
        color: $text-secondary;
        margin-top: 4rpx;
      }
    }
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