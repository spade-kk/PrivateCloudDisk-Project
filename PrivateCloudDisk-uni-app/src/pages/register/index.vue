<template>
  <view class="register-page">
    <view class="register-card">
      <view class="title-section">
        <text class="title">创建账号</text>
        <text class="subtitle">加入私有云盘，开启安全存储之旅</text>
      </view>

      <view class="form-section">
        <view class="form-item">
          <u-input
            v-model="form.username"
            placeholder="请输入用户名"
            prefixIcon="account"
            prefixIconSize="40"
            clearable
          />
        </view>
        <view class="form-item">
          <u-input
            v-model="form.email"
            placeholder="请输入邮箱"
            prefixIcon="email"
            prefixIconSize="40"
            clearable
          />
        </view>
        <view class="form-item">
          <u-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码（至少6位）"
            prefixIcon="lock"
            prefixIconSize="40"
            clearable
          />
        </view>
        <view class="form-item">
          <u-input
            v-model="form.confirmPassword"
            type="password"
            placeholder="请确认密码"
            prefixIcon="lock"
            prefixIconSize="40"
            clearable
          />
        </view>

        <u-button
          type="primary"
          text="注 册"
          :loading="loading"
          @click="handleRegister"
          customStyle="height: 88rpx; font-size: 32rpx; background: #4F6EF7; border-radius: 16rpx;"
        />

        <view class="form-footer">
          <text class="link-text" @click="goLogin">已有账号？立即登录</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import { useUserStore } from '@/store/user'
import { evaluatePasswordStrength, PASSWORD_MIN_LENGTH } from '@/utils/crypto'

export default {
  data() {
    return {
      form: { username: '', email: '', password: '', confirmPassword: '' },
      loading: false,
      passwordStrength: null
    }
  },
  watch: {
    'form.password'(val) {
      if (val && val.length >= PASSWORD_MIN_LENGTH) {
        this.passwordStrength = evaluatePasswordStrength(val)
      } else {
        this.passwordStrength = null
      }
    }
  },
  methods: {
    validate() {
      if (!this.form.username.trim()) {
        uni.showToast({ title: '请输入用户名', icon: 'none' })
        return false
      }
      if (!this.form.email.trim()) {
        uni.showToast({ title: '请输入邮箱', icon: 'none' })
        return false
      }
      if (this.form.password.length < PASSWORD_MIN_LENGTH) {
        uni.showToast({ title: `密码至少${PASSWORD_MIN_LENGTH}位`, icon: 'none' })
        return false
      }
      if (this.form.password !== this.form.confirmPassword) {
        uni.showToast({ title: '两次密码不一致', icon: 'none' })
        return false
      }
      return true
    },
    /**
     * 注册处理 - 企业级注册流程
     *
     * 流程:
     * 1. 前端表单校验（含密码强度评估）
     * 2. 调用 Pinia Store doRegister（内部完成密码哈希 + API 调用）
     * 3. 密码明文永不离开客户端
     */
    async handleRegister() {
      if (!this.validate()) return
      this.loading = true
      try {
        const userStore = useUserStore()
        await userStore.doRegister({
          username: this.form.username.trim(),
          email: this.form.email.trim(),
          password: this.form.password
        })
        uni.showToast({ title: '注册成功，请登录', icon: 'success', duration: 2000 })
        setTimeout(() => {
          uni.navigateBack()
        }, 2000)
      } catch (e) {
        // 错误由 request.js 拦截器统一处理
        console.error('[Register] 注册失败:', e)
      } finally {
        this.loading = false
      }
    },
    goLogin() {
      uni.navigateBack()
    }
  }
}
</script>

<style lang="scss" scoped>
.register-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(180deg, #4F6EF7 0%, #6C5CE7 40%, #f5f5f5 40%);
}
.register-card {
  width: 86%;
  background: #fff;
  border-radius: 24rpx;
  overflow: hidden;
  box-shadow: 0 8rpx 40rpx rgba(0,0,0,0.12);
}
.title-section {
  padding: 48rpx 40rpx 24rpx;
  text-align: center;
}
.title { font-size: 40rpx; color: #202124; font-weight: 700; display: block; }
.subtitle { font-size: 24rpx; color: #9aa0a6; margin-top: 8rpx; display: block; }

.form-section { padding: 0 40rpx 48rpx; }
.form-item { margin-bottom: 28rpx; }
.form-footer { margin-top: 32rpx; text-align: center; }
.link-text { font-size: 26rpx; color: #4F6EF7; }
</style>