<template>
  <view class="profile-page">
    <!-- 用户信息卡片 - 渐变头部 -->
    <view class="profile-header">
      <view class="header-bg" />
      <view class="header-content">
        <view class="avatar-wrap">
          <u-avatar :src="avatar" size="128" />
        </view>
        <text class="user-name">{{ displayName }}</text>
        <text class="user-email" v-if="userEmail">{{ userEmail }}</text>
        <view class="edit-btn" @click="goEdit">
          <text class="edit-text">编辑资料</text>
          <u-icon name="arrow-right" size="28" color="rgba(255,255,255,0.8)" />
        </view>
      </view>
    </view>

    <!-- 容量卡片 -->
    <view class="quota-card" v-if="quota">
      <view class="quota-header">
        <view class="quota-title-row">
          <u-icon name="server-man" size="36" color="#4F6EF7" />
          <text class="quota-title">存储空间</text>
        </view>
        <text class="quota-percent">{{ usagePercent }}%</text>
      </view>
      <view class="quota-bar-wrap">
        <view class="quota-bar">
          <view class="quota-bar-fill" :style="{ width: usagePercent + '%' }" />
        </view>
      </view>
      <view class="quota-detail">
        <text class="quota-size">{{ formatFileSize(quota.used_capacity || 0) }} / {{ formatFileSize(quota.total_capacity || 0) }}</text>
      </view>
    </view>

    <!-- 功能菜单 -->
    <view class="menu-section">
      <text class="section-title">常用功能</text>
      <view class="menu-list">
        <view class="menu-item" @click="goUpload">
          <view class="menu-icon menu-icon--success">
            <u-icon name="arrow-up" size="40" color="#00C48C" />
          </view>
          <text class="menu-text">上传管理</text>
          <u-icon name="arrow-right" size="28" color="#C0C0D0" />
        </view>
        <view class="menu-item" @click="goFavorites">
          <view class="menu-icon menu-icon--warning">
            <u-icon name="star" size="40" color="#FFB347" />
          </view>
          <text class="menu-text">我的收藏</text>
          <u-icon name="arrow-right" size="28" color="#C0C0D0" />
        </view>
        <view class="menu-item" @click="goTrash">
          <view class="menu-icon menu-icon--danger">
            <u-icon name="trash" size="40" color="#FF5C5C" />
          </view>
          <text class="menu-text">回收站</text>
          <u-icon name="arrow-right" size="28" color="#C0C0D0" />
        </view>
        <view class="menu-item" @click="goSettings">
          <view class="menu-icon menu-icon--primary">
            <u-icon name="setting" size="40" color="#4F6EF7" />
          </view>
          <text class="menu-text">设置</text>
          <u-icon name="arrow-right" size="28" color="#C0C0D0" />
        </view>
      </view>
    </view>

    <!-- 退出登录 -->
    <view class="logout-section">
      <view class="logout-btn" @click="handleLogout">
        <text class="logout-text">退出登录</text>
      </view>
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
    displayName() {
      return this.userStore.displayName || '未登录'
    },
    userEmail() {
      return this.userStore.profile?.email || ''
    },
    avatar() {
      return this.userStore.avatarUrl || ''
    },
    usagePercent() {
      if (!this.quota?.total_capacity) return 0
      return ((this.quota.used_capacity / this.quota.total_capacity) * 100).toFixed(1)
    }
  },
  onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({ currentIndex: 3 })
    }
    if (!this.requireAuth()) return
    this.loadQuota()
    this.loadUserInfo()
  },
  methods: {
    formatFileSize,
    async loadQuota() {
      try {
        const res = await getMyQuota()
        this.quota = res.data
      } catch (e) {
        console.error('[Profile] 加载容量失败:', e)
      }
    },
    async loadUserInfo() {
      try {
        await this.userStore.fetchProfile()
      } catch (e) {
        console.error('[Profile] 加载用户信息失败:', e)
      }
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
.profile-page {
  min-height: 100vh;
  background: $color-bg-page;
  padding-bottom: 140rpx;
}

/* ========== 头部 ========== */
.profile-header {
  position: relative;
  padding-bottom: 40rpx;
}

.header-bg {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 340rpx;
  background: $gradient-primary;
  border-radius: 0 0 40rpx 40rpx;
}

.header-content {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: calc(var(--status-bar-height, 44px) + 32rpx);
}

.avatar-wrap {
  width: 128rpx;
  height: 128rpx;
  border-radius: 50%;
  border: 4rpx solid rgba(255, 255, 255, 0.3);
  box-shadow: 0 8rpx 32rpx rgba(0, 0, 0, 0.15);
  margin-bottom: 20rpx;
}

.user-name {
  font-size: $font-size-title;
  font-weight: $font-weight-semibold;
  color: $color-text-inverse;
  margin-bottom: 6rpx;
}

.user-email {
  font-size: $font-size-body-sm;
  color: rgba(255, 255, 255, 0.75);
  margin-bottom: 16rpx;
}

.edit-btn {
  display: flex;
  align-items: center;
  padding: 8rpx 24rpx;
  background: rgba(255, 255, 255, 0.15);
  border-radius: $radius-full;
  border: 1rpx solid rgba(255, 255, 255, 0.2);
}

.edit-text {
  font-size: $font-size-body-sm;
  color: rgba(255, 255, 255, 0.9);
  margin-right: 4rpx;
}

/* ========== 容量卡片 ========== */
.quota-card {
  margin: -16rpx 24rpx 16rpx;
  padding: 24rpx;
  background: $color-bg-card;
  border-radius: $card-radius;
  box-shadow: $shadow-lg;
}

.quota-header {
  @include flex-between;
  margin-bottom: 16rpx;
}

.quota-title-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.quota-title {
  font-size: $font-size-body;
  font-weight: $font-weight-medium;
  color: $color-text-primary;
}

.quota-percent {
  font-size: $font-size-body-sm;
  font-weight: $font-weight-semibold;
  color: $color-primary;
}

.quota-bar-wrap {
  margin-bottom: 10rpx;
}

.quota-bar {
  height: 6rpx;
  background: $color-bg-divider;
  border-radius: 3rpx;
  overflow: hidden;
}

.quota-bar-fill {
  height: 100%;
  background: $gradient-primary;
  border-radius: 3rpx;
  transition: width 0.3s ease;
}

.quota-detail {
  display: flex;
  justify-content: flex-end;
}

.quota-size {
  font-size: $font-size-caption;
  color: $color-text-secondary;
}

/* ========== 功能菜单 ========== */
.menu-section {
  margin: 16rpx 24rpx;
}

.section-title {
  font-size: $font-size-body-sm;
  font-weight: $font-weight-medium;
  color: $color-text-secondary;
  margin-bottom: 12rpx;
  padding-left: 8rpx;
  display: block;
}

.menu-list {
  background: $color-bg-card;
  border-radius: $card-radius;
  overflow: hidden;
  box-shadow: $shadow-md;
}

.menu-item {
  display: flex;
  align-items: center;
  padding: 28rpx 32rpx;
  transition: background $transition-fast;

  &:not(:last-child) {
    @include hairline-bottom($color-bg-divider);
  }

  &:active {
    background: $color-bg-hover;
  }
}

.menu-icon {
  width: 64rpx;
  height: 64rpx;
  border-radius: 16rpx;
  @include flex-center;
  margin-right: 24rpx;
}

.menu-icon--success { background: $color-success-light; }
.menu-icon--warning { background: $color-warning-light; }
.menu-icon--danger  { background: $color-danger-light; }
.menu-icon--primary { background: $color-primary-lighter; }

.menu-text {
  flex: 1;
  font-size: $font-size-body-lg;
  color: $color-text-primary;
  font-weight: $font-weight-medium;
}

/* ========== 退出登录 ========== */
.logout-section {
  padding: 48rpx 32rpx;
}

.logout-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 88rpx;
  background: $color-bg-card;
  border-radius: $card-radius;
  border: 1rpx solid $color-border;

  &:active {
    background: $color-bg-hover;
  }
}

.logout-text {
  font-size: $font-size-body;
  color: $color-danger;
  font-weight: $font-weight-medium;
}
</style>