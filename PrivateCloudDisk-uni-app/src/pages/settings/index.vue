<template>
  <view class="settings-page">
    <view class="section">
      <view class="section-title">通用</view>
      <view class="menu-list">
        <view class="menu-item" @click="handleClearCache">
          <text class="menu-text">清除缓存</text>
          <text class="menu-value">{{ cacheSize }}</text>
        </view>
        <view class="menu-item">
          <text class="menu-text">自动播放</text>
          <u-switch v-model="autoPlay" activeColor="#1a73e8" />
        </view>
      </view>
    </view>

    <view class="section">
      <view class="section-title">安全</view>
      <view class="menu-list">
        <view class="menu-item" @click="handleChangePassword">
          <text class="menu-text">修改密码</text>
          <u-icon name="arrow-right" size="28" color="#c4c7cc" />
        </view>
      </view>
    </view>

    <view class="section">
      <view class="section-title">关于</view>
      <view class="menu-list">
        <view class="menu-item">
          <text class="menu-text">版本</text>
          <text class="menu-value">v1.0.0</text>
        </view>
        <view class="menu-item" @click="handleFeedback">
          <text class="menu-text">意见反馈</text>
          <u-icon name="arrow-right" size="28" color="#c4c7cc" />
        </view>
      </view>
    </view>

    <!-- 修改密码弹窗 -->
    <u-modal
      :show="pwdModalShow"
      title="修改密码"
      :showCancelButton="true"
      confirmText="确认"
      @confirm="confirmChangePassword"
      @cancel="pwdModalShow = false"
    >
      <view class="modal-form">
        <u-input v-model="pwdForm.oldPassword" type="password" placeholder="原密码" />
        <u-input v-model="pwdForm.newPassword" type="password" placeholder="新密码" class="mt-16" />
        <u-input v-model="pwdForm.confirmPassword" type="password" placeholder="确认新密码" class="mt-16" />
      </view>
    </u-modal>
  </view>
</template>

<script>
import { changePassword } from '@/api/auth'

export default {
  data() {
    return {
      cacheSize: '0 KB',
      autoPlay: false,
      pwdModalShow: false,
      pwdForm: { oldPassword: '', newPassword: '', confirmPassword: '' }
    }
  },
  onShow() {
    this.loadCacheSize()
  },
  methods: {
    loadCacheSize() {
      try {
        const res = uni.getStorageInfoSync()
        const size = res.currentSize || 0
        this.cacheSize = size < 1024 ? `${size} KB` : `${(size / 1024).toFixed(1)} MB`
      } catch (e) {
        this.cacheSize = '0 KB'
      }
    },
    handleClearCache() {
      uni.showModal({
        title: '清除缓存',
        content: '确定要清除应用缓存吗？',
        success: (res) => {
          if (res.confirm) {
            uni.clearStorageSync()
            this.loadCacheSize()
            uni.showToast({ title: '已清除', icon: 'success' })
          }
        }
      })
    },
    handleChangePassword() {
      this.pwdForm = { oldPassword: '', newPassword: '', confirmPassword: '' }
      this.pwdModalShow = true
    },
    async confirmChangePassword() {
      if (!this.pwdForm.oldPassword || !this.pwdForm.newPassword) {
        return uni.showToast({ title: '请填写完整', icon: 'none' })
      }
      if (this.pwdForm.newPassword !== this.pwdForm.confirmPassword) {
        return uni.showToast({ title: '两次密码不一致', icon: 'none' })
      }
      try {
        await changePassword(this.pwdForm.oldPassword, this.pwdForm.newPassword)
        uni.showToast({ title: '修改成功', icon: 'success' })
        this.pwdModalShow = false
      } catch (e) { /* 已处理 */ }
    },
    handleFeedback() {
      uni.showToast({ title: '意见反馈功能开发中', icon: 'none' })
    }
  }
}
</script>

<style lang="scss" scoped>
.settings-page { min-height: 100vh; background: #f5f5f5; padding-top: 16rpx; }

.section { margin-bottom: 16rpx; }
.section-title { padding: 16rpx 32rpx 8rpx; font-size: 24rpx; color: #9aa0a6; }

.menu-list {
  margin: 0 24rpx;
  background: #fff;
  border-radius: 16rpx;
  overflow: hidden;
  box-shadow: 0 2rpx 12rpx rgba(0,0,0,0.06);
}
.menu-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 28rpx 32rpx;
  border-bottom: 1rpx solid #f0f0f0;
  &:last-child { border-bottom: none; }
  &:active { background: #f5f8ff; }
}
.menu-text { font-size: 30rpx; color: #202124; }
.menu-value { font-size: 26rpx; color: #9aa0a6; }

.modal-form { padding: 24rpx; }
.mt-16 { margin-top: 16rpx; }
</style>