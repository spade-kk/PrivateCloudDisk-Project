<template>
  <view class="login-page">
    <view class="login-card">
      <!-- Logo 区域 -->
      <view class="logo-section">
        <view class="logo-icon">
          <u-icon name="server-man" size="80" color="#fff" />
        </view>
        <text class="app-name">私有云盘</text>
        <text class="app-desc">安全 · 高效 · 跨平台</text>
      </view>

      <!-- 登录表单 -->
      <view class="form-section">
        <view class="form-item">
          <u-input
            v-model="form.account"
            placeholder="请输入用户名/邮箱/手机号"
            prefixIcon="account"
            prefixIconSize="40"
            clearable
            border="bottom"
          />
        </view>
        <view class="form-item">
          <u-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            prefixIcon="lock"
            prefixIconSize="40"
            clearable
            border="bottom"
          />
        </view>

        <u-button
          type="primary"
          text="登 录"
          :loading="loading"
          @click="handleLogin"
          class="login-btn"
          customStyle="height: 88rpx; font-size: 32rpx; background: #4F6EF7; border-radius: 16rpx;"
        />

        <view class="form-footer">
          <text class="link-text" @click="goRegister">还没有账号？立即注册</text>
        </view>
      </view>
    </view>

    <!-- 版本信息 -->
    <view class="version-info">
      <text class="version-text">v1.0.0 · PrivateCloudDisk</text>
    </view>
  </view>
</template>

<script>
import { useUserStore } from '@/store/user'

export default {
  data() {
    return {
      form: {
        account: '',
        password: ''
      },
      loading: false
    }
  },
  onLoad() {
    // 检查是否已登录，已登录则直接跳转首页
    const userStore = useUserStore()
    if (userStore.isLoggedIn) {
      uni.reLaunch({ url: '/pages/index/index' })
    }
  },
  methods: {
    /**
     * 登录处理 - 企业级登录流程
     *
     * 流程:
     * 1. 前端表单校验
     * 2. 调用 Pinia Store doLogin (内部完成密码哈希 + API 调用)
     * 3. 登录成功后自动拉取用户信息
     * 4. Token 自动持久化到本地存储
     * 5. 跳转至首页
     */
    async handleLogin() {
      // 表单校验
      const account = this.form.account.trim()
      if (!account) {
        return uni.showToast({ title: '请输入账号', icon: 'none' })
      }
      if (!this.form.password) {
        return uni.showToast({ title: '请输入密码', icon: 'none' })
      }

      this.loading = true
      try {
        const userStore = useUserStore()

        // 构建登录参数：自动判断账号类型
        const loginParams = {
          password: this.form.password
        }
        if (/^1[3-9]\d{9}$/.test(account)) {
          loginParams.phone_number = account
        } else if (/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(account)) {
          loginParams.account = account
        } else {
          loginParams.account = account
        }

        // 调用 Store 的 doLogin 方法（内部完成密码哈希、API调用、Token存储、用户信息拉取）
        await userStore.doLogin(loginParams)

        uni.showToast({ title: '登录成功', icon: 'success', duration: 1200 })
        setTimeout(() => {
          uni.reLaunch({ url: '/pages/index/index' })
        }, 1200)
      } catch (e) {
        // 错误信息由 request.js 拦截器统一处理
        console.error('[Login] 登录失败:', e)
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
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: linear-gradient(180deg, #4F6EF7 0%, #6C5CE7 50%, #F5F6FA 50%);
}

.login-card {
  width: 86%;
  background: #fff;
  border-radius: 24rpx;
  overflow: hidden;
  box-shadow: 0 8rpx 40rpx rgba(79, 110, 247, 0.2);
}

.logo-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 64rpx 0 48rpx;
  background: linear-gradient(135deg, #4F6EF7, #6C5CE7);
}

.logo-icon {
  width: 120rpx;
  height: 120rpx;
  border-radius: 60rpx;
  background: rgba(255, 255, 255, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16rpx;
}

.app-name {
  font-size: 40rpx;
  color: #fff;
  font-weight: 700;
}

.app-desc {
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.7);
  margin-top: 8rpx;
}

.form-section {
  padding: 48rpx 40rpx;
}

.form-item {
  margin-bottom: 32rpx;
}

.login-btn {
  margin-top: 16rpx;
}

.form-footer {
  margin-top: 32rpx;
  text-align: center;
}

.link-text {
  font-size: 26rpx;
  color: #4F6EF7;
}

.version-info {
  position: absolute;
  bottom: 48rpx;
  left: 0;
  right: 0;
  text-align: center;
}

.version-text {
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.6);
}
</style>