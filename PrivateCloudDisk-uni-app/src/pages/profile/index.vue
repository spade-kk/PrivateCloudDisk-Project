<template>
  <view class="profile-page">
    <!-- 用户信息卡片 -->
    <view class="user-card">
      <view class="avatar">
        <u-avatar :src="avatar" size="100" />
      </view>
      <view class="user-info">
        <text class="user-name">{{ userStore.userName || '未登录' }}</text>
        <text class="user-email">{{ userStore.userEmail || '' }}</text>
      </view>
      <view class="edit-btn" @click="goEdit">
        <u-icon name="edit-pen" size="32" color="#1a73e8" />
      </view>
    </view>

    <!-- 容量卡片 -->
    <view class="quota-card" v-if="quota">
      <view class="quota-header">
        <text class="quota-title">存储空间</text>
        <text class="quota-text">
          {{ formatFileSize(quota.used_capacity || 0) }} / {{ formatFileSize(quota.total_capacity || 0) }}
        </text>
      </view>
      <u-line-progress :percentage="usagePercent" activeColor="#1a73e8" height="8" :showText="false" />
    </view>

    <!-- 功能菜单 -->
    <view class="menu-list">
      <view class="menu-item" @click="goUpload">
        <view class="menu-icon menu-icon-green">
          <u-icon name="cloud-upload" size="40" color="#34a853" />
        </view>
        <text class="menu-text">上传管理</text>
        <u-icon name="arrow-right" size="28" color="#c4c7cc" />
      </view>
      <view class="menu-item" @click="goFavorites">
        <view class="menu-icon menu-icon-yellow">
          <u-icon name="star" size="40" color="#fbbc04" />
        </view>
        <text class="menu-text">我的收藏</text>
        <u-icon name="arrow-right" size="28" color="#c4c7cc" />
      </view>
      <view class="menu-item" @click="goTrash">
        <view class="menu-icon menu-icon-red">
          <u-icon name="trash" size="40" color="#ea4335" />
        </view>
        <text class="menu-text">回收站</text>
        <u-icon name="arrow-right" size="28" color="#c4c7cc" />
      </view>
      <view class="menu-item" @click="goSettings">
        <view class="menu-icon menu-icon-blue">
          <u-icon name="setting" size="40" color="#1a73e8" />
        </view>
        <text class="menu-text">设置</text>
        <u-icon name="arrow-right" size="28" color="#c4c7cc" />
      </view>
    </view>

    <!-- 退出登录 -->
    <view class="logout-section">
      <u-button type="error" text="退出登录" @click="handleLogout" plain />
    </view>
  </view>
</template>

<script>
import { useUserAuth } from '@/composables/useUserAuth'
import { getMyQuota } from '@/api/quota'
import { formatFileSize } from '@/utils/helper'

export default {
  setup() {
    const { userStore, requireAuth, logout } = useUserAuth()
    return { userStore, requireAuth, logout }
  },
  data() {
    return { quota: null }
  },
  computed: {
    avatar() {
      return this.userStore.userAvatar || ''
    },
    usagePercent() {
      if (!this.quota?.total_capacity) return 0
      return ((this.quota.used_capacity / this.quota.total_capacity) * 100).toFixed(1)
    }
  },
  onShow() {
    if (!this.requireAuth()) return
    this.loadQuota()
  },
  methods: {
    formatFileSize,
    async loadQuota() {
      try {
        const res = await getMyQuota()
        this.quota = res.data
      } catch (e) { /* 已处理 */ }
    },
    goEdit() {
      uni.navigateTo({ url: '/pages/profile/edit/index' })
    },
    goUpload() {
      uni.navigateTo({ url: '/pages/upload/index' })
    },
    goFavorites() {
      uni.switchTab({ url: '/pages/favorites/index' })
    },
    goTrash() {
      uni.switchTab({ url: '/pages/trash/index' })
    },
    goSettings() {
      uni.navigateTo({ url: '/pages/settings/index' })
    },
    handleLogout() {
      uni.showModal({
        title: '确认退出',
        content: '确定要退出登录吗？',
        success: (res) => {
          if (res.confirm) this.logout()
        }
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.profile-page { min-height: 100vh; background: #f5f5f5; padding-bottom: 32rpx; }

.user-card {
  display: flex;
  align-items: center;
  padding: 48rpx 32rpx;
  background: linear-gradient(135deg, #1a73e8, #4a90d9);
  margin-bottom: 16rpx;
}
.avatar { margin-right: 24rpx; }
.user-info { flex: 1; }
.user-name { font-size: 36rpx; color: #fff; font-weight: 600; display: block; }
.user-email { font-size: 24rpx; color: rgba(255,255,255,0.8); margin-top: 4rpx; }
.edit-btn { padding: 8rpx; }

.quota-card {
  margin: 16rpx 24rpx;
  padding: 24rpx;
  background: #fff;
  border-radius: 16rpx;
  box-shadow: 0 2rpx 12rpx rgba(0,0,0,0.06);
}
.quota-header { display: flex; justify-content: space-between; margin-bottom: 16rpx; }
.quota-title { font-size: 28rpx; color: #202124; font-weight: 500; }
.quota-text { font-size: 24rpx; color: #5f6368; }

.menu-list {
  margin: 16rpx 24rpx;
  background: #fff;
  border-radius: 16rpx;
  overflow: hidden;
  box-shadow: 0 2rpx 12rpx rgba(0,0,0,0.06);
}
.menu-item {
  display: flex;
  align-items: center;
  padding: 28rpx 32rpx;
  border-bottom: 1rpx solid #f0f0f0;
  &:last-child { border-bottom: none; }
  &:active { background: #f5f8ff; }
}
.menu-icon {
  width: 64rpx; height: 64rpx; border-radius: 16rpx;
  display: flex; align-items: center; justify-content: center;
  margin-right: 24rpx;
}
.menu-icon-green { background: #e6f4ea; }
.menu-icon-yellow { background: #fef7e0; }
.menu-icon-red { background: #fce8e6; }
.menu-icon-blue { background: #e8f0fe; }
.menu-text { flex: 1; font-size: 30rpx; color: #202124; }

.logout-section { padding: 48rpx 32rpx; }
</style>