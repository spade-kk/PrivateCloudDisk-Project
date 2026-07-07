<template>
  <view class="login-page">
    <view class="login-card">
      <view class="logo-section">
        <view class="logo-icon">
          <u-icon name="cloud" size="80" color="#fff" />
        </view>
        <text class="app-name">私有云盘</text>
        <text class="app-desc">安全 · 高效 · 跨平台</text>
      </view>

      <view class="form-section">
        <view class="form-item">
          <u-input
            v-model="username"
            placeholder="请输入用户名/邮箱"
            prefixIcon="account"
            prefixIconSize="40"
            clearable
          />
        </view>
        <view class="form-item">
          <u-input
            v-model="password"
            type="password"
            placeholder="请输入密码"
            prefixIcon="lock"
            prefixIconSize="40"
            clearable
          />
        </view>

        <u-button
          type="primary"
          text="登 录"
          :loading="loading"
          @click="handleLogin"
          class="login-btn"
          customStyle="height: 88rpx; font-size: 32rpx; background: #1a73e8; border-radius: 16rpx;"
        />

        <view class="form-footer">
          <text class="link-text" @click="goRegister">还没有账号？立即注册</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import { login } from '@/api/auth'
import { useUserStore } from '@/store/user'

export default {
  data() {
    return { username: '', password: '', loading: false }
  },
  methods: {
    async handleLogin() {
      if (!this.username.trim()) {
        return uni.showToast({ title: '请输入用户名', icon: 'none' })
      }
      if (!this.password) {
        return uni.showToast({ title: '请输入密码', icon: 'none' })
      }
      this.loading = true
      try {
        const res = await login(this.username.trim(), this.password)
        const { token, user_info } = res.data
        const userStore = useUserStore()
        userStore.setToken(token)
        if (user_info) {
          userStore.setUserInfo(user_info)
        }
        uni.reLaunch({ url: '/pages/index/index' })
      } catch (e) {
        // 错误由 request.js 处理
      } finally {
        this.loading = false
      }
    },
    goRegister() {
      uni.navigateTo({ url: '/pages/register/index' })
    }
  }
}
</script>

<style lang="scss" scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(180deg, #1a73e8 0%, #4a90d9 50%, #f5f5f5 50%);
}
.login-card {
  width: 86%;
  background: #fff;
  border-radius: 24rpx;
  overflow: hidden;
  box-shadow: 0 8rpx 40rpx rgba(0,0,0,0.12);
}
.logo-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 64rpx 0 48rpx;
  background: linear-gradient(135deg, #1a73e8, #4a90d9);
}
.logo-icon {
  width: 120rpx; height: 120rpx; border-radius: 60rpx;
  background: rgba(255,255,255,0.2);
  display: flex; align-items: center; justify-content: center;
  margin-bottom: 16rpx;
}
.app-name { font-size: 40rpx; color: #fff; font-weight: 700; }
.app-desc { font-size: 24rpx; color: rgba(255,255,255,0.7); margin-top: 8rpx; }

.form-section { padding: 48rpx 40rpx; }
.form-item { margin-bottom: 32rpx; }
.login-btn { margin-top: 16rpx; }
.form-footer { margin-top: 32rpx; text-align: center; }
.link-text { font-size: 26rpx; color: #1a73e8; }
</style>