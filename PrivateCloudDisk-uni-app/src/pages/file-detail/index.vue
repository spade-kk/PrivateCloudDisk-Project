<template>
  <view class="file-detail-page">
    <!-- 文件基本信息 -->
    <view class="detail-card">
      <view class="detail-icon">
        <u-icon :name="iconName" size="80" :color="iconColor" />
      </view>
      <text class="detail-name">{{ fileName }}</text>
      <text class="detail-size" v-if="fileSize">{{ formatFileSize(fileSize) }}</text>
    </view>

    <!-- 操作按钮 -->
    <view class="action-grid">
      <view class="action-item" @click="handleDownload">
        <view class="action-icon action-icon-blue">
          <u-icon name="download" size="40" color="#1a73e8" />
        </view>
        <text class="action-text">下载</text>
      </view>
      <view class="action-item" @click="handleShare">
        <view class="action-icon action-icon-green">
          <u-icon name="share" size="40" color="#34a853" />
        </view>
        <text class="action-text">分享</text>
      </view>
      <view class="action-item" @click="handleFavorite">
        <view class="action-icon" :class="isFavorited ? 'action-icon-yellow' : 'action-icon-gray'">
          <u-icon name="star" size="40" :color="isFavorited ? '#fbbc04' : '#9aa0a6'" />
        </view>
        <text class="action-text">{{ isFavorited ? '已收藏' : '收藏' }}</text>
      </view>
      <view class="action-item" @click="handleTrash">
        <view class="action-icon action-icon-red">
          <u-icon name="trash" size="40" color="#ea4335" />
        </view>
        <text class="action-text">删除</text>
      </view>
    </view>

    <!-- 详细信息 -->
    <view class="info-section">
      <view class="section-title">文件信息</view>
      <view class="info-list">
        <view class="info-item">
          <text class="info-label">文件名</text>
          <text class="info-value">{{ fileName }}</text>
        </view>
        <view class="info-item" v-if="fileSize">
          <text class="info-label">大小</text>
          <text class="info-value">{{ formatFileSize(fileSize) }}</text>
        </view>
        <view class="info-item" v-if="fileType">
          <text class="info-label">类型</text>
          <text class="info-value">{{ fileType }}</text>
        </view>
        <view class="info-item" v-if="updateTime">
          <text class="info-label">更新日期</text>
          <text class="info-value">{{ updateTime }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import { getFileDetail } from '@/api/node'
import { addFavorite, removeFavorite } from '@/api/favorite'
import { moveFileToTrash } from '@/api/trash'
import { formatFileSize, getFileIcon, getFileIconColor } from '@/utils/helper'

export default {
  data() {
    return {
      fileId: '',
      fileName: '',
      fileSize: 0,
      fileType: '',
      updateTime: '',
      isFavorited: false
    }
  },
  computed: {
    iconName() {
      return getFileIcon(this.fileName)
    },
    iconColor() {
      return getFileIconColor(this.fileName)
    }
  },
  onLoad(options) {
    this.fileId = options.fileId || ''
    this.fileName = decodeURIComponent(options.fileName || '')
    this.loadDetail()
  },
  methods: {
    formatFileSize,

    async loadDetail() {
      if (!this.fileId) return
      try {
        const res = await getFileDetail(this.fileId)
        const data = res.data
        this.fileName = data.node_name || this.fileName
        this.fileSize = data.node_size || 0
        this.fileType = data.content_type || '-'
        this.updateTime = data.updated_at || ''
        this.isFavorited = data.is_favorite || false
      } catch (e) { /* 已处理 */ }
    },

    handleDownload() {
      uni.showLoading({ title: '准备下载...' })
      uni.downloadFile({
        url: this.fileId,
        success: (res) => {
          uni.hideLoading()
          uni.openDocument({ filePath: res.tempFilePath })
        },
        fail: () => {
          uni.hideLoading()
          uni.showToast({ title: '下载失败', icon: 'none' })
        }
      })
    },

    handleShare() {
      // 小程序分享由 onShareAppMessage 处理
      uni.showToast({ title: '点击右上角分享', icon: 'none' })
    },

    async handleFavorite() {
      try {
        if (this.isFavorited) {
          await removeFavorite(this.fileId)
          this.isFavorited = false
          uni.showToast({ title: '已取消收藏', icon: 'success' })
        } else {
          await addFavorite(this.fileId)
          this.isFavorited = true
          uni.showToast({ title: '已收藏', icon: 'success' })
        }
      } catch (e) { /* 已处理 */ }
    },

    async handleTrash() {
      const res = await uni.showModal({
        title: '确认删除',
        content: '删除后将移入回收站，可于30天内恢复。'
      })
      if (res.confirm) {
        try {
          await moveFileToTrash(this.fileId)
          uni.showToast({ title: '已移入回收站', icon: 'success' })
          setTimeout(() => uni.navigateBack(), 1500)
        } catch (e) { /* 已处理 */ }
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.file-detail-page { min-height: 100vh; background: #f5f5f5; padding-bottom: 32rpx; }

.detail-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 56rpx 32rpx;
  background: #fff;
  margin-bottom: 16rpx;
}
.detail-icon { margin-bottom: 16rpx; }
.detail-name { font-size: 32rpx; color: #202124; font-weight: 500; text-align: center; word-break: break-all; }
.detail-size { font-size: 24rpx; color: #9aa0a6; margin-top: 8rpx; }

.action-grid {
  display: flex;
  justify-content: space-around;
  padding: 32rpx 16rpx;
  margin: 0 24rpx;
  background: #fff;
  border-radius: 16rpx;
  box-shadow: 0 2rpx 12rpx rgba(0,0,0,0.06);
  margin-bottom: 16rpx;
}
.action-item { display: flex; flex-direction: column; align-items: center; }
.action-icon {
  width: 80rpx; height: 80rpx; border-radius: 20rpx;
  display: flex; align-items: center; justify-content: center;
  margin-bottom: 8rpx;
}
.action-icon-blue { background: #e8f0fe; }
.action-icon-green { background: #e6f4ea; }
.action-icon-yellow { background: #fef7e0; }
.action-icon-red { background: #fce8e6; }
.action-icon-gray { background: #f5f5f5; }
.action-text { font-size: 22rpx; color: #5f6368; }

.info-section { margin: 0 24rpx; }
.section-title { font-size: 26rpx; color: #9aa0a6; padding: 16rpx 0 8rpx; }
.info-list {
  background: #fff;
  border-radius: 16rpx;
  overflow: hidden;
  box-shadow: 0 2rpx 12rpx rgba(0,0,0,0.06);
}
.info-item {
  display: flex;
  justify-content: space-between;
  padding: 24rpx 32rpx;
  border-bottom: 1rpx solid #f0f0f0;
  &:last-child { border-bottom: none; }
}
.info-label { font-size: 28rpx; color: #5f6368; }
.info-value { font-size: 28rpx; color: #202124; max-width: 60%; text-align: right; word-break: break-all; }
</style>