<template>
  <view class="change-email-page">
    <view class="page-header">
      <view class="back-btn" @click="goBack">
        <u-icon name="arrow-left" size="36" color="#202124" />
      </view>
      <text class="page-title">换绑邮箱</text>
      <view class="placeholder" />
    </view>

    <view class="form-section">
      <view class="form-item">
        <text class="form-label">当前邮箱</text>
        <text class="form-value">{{ currentEmail || '未绑定' }}</text>
      </view>

      <view class="form-item">
        <text class="form-label">新邮箱</text>
        <u-input
          v-model="form.newEmail"
          placeholder="请输入新邮箱"
          clearable
          :disabled="sending"
        />
      </view>

      <view class="form-item">
        <text class="form-label">验证码</text>
        <view class="code-input-wrap">
          <u-input
            v-model="form.code"
            placeholder="请输入验证码"
            clearable
            :disabled="sending"
          />
          <u-button
            type="primary"
            :text="codeButtonText"
            :disabled="sending || !canSendCode"
            @click="handleSendCode"
            customStyle="height: 64rpx; font-size: 24rpx; border-radius: 12rpx; min-width: 160rpx;"
          />
        </view>
      </view>
    </view>

    <view class="save-section">
      <u-button
        type="primary"
        text="确认换绑"
        :loading="submitting"
        :disabled="!canSubmit"
        @click="handleSubmit"
        customStyle="height: 88rpx; font-size: 32rpx; border-radius: 16rpx;"
      />
    </view>
  </view>
</template>

<script>
import { getUserInfo, sendVerificationCode, confirmChangeEmail } from '@/api/user'

export default {
  data() {
    return {
      currentEmail: '',
      form: {
        newEmail: '',
        code: ''
      },
      sending: false,
      submitting: false,
      codeCountdown: 0,
      timer: null,
      resendToken: ''
    }
  },
  computed: {
    canSendCode() {
      const email = this.form.newEmail.trim()
      return email && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)
    },
    canSubmit() {
      return this.form.newEmail.trim() && this.form.code.trim() && !this.submitting
    },
    codeButtonText() {
      if (this.codeCountdown > 0) {
        return `${this.codeCountdown}s`
      }
      return '发送验证码'
    }
  },
  onShow() {
    this.loadUserInfo()
  },
  onUnmounted() {
    if (this.timer) {
      clearInterval(this.timer)
    }
  },
  methods: {
    goBack() {
      uni.navigateBack()
    },
    async loadUserInfo() {
      try {
        const res = await getUserInfo()
        this.currentEmail = res.data.email || ''
      } catch (e) {
        console.error('[ChangeEmail] 加载用户信息失败:', e)
      }
    },
    async handleSendCode() {
      if (!this.canSendCode || this.sending) return

      this.sending = true
      try {
        const email = this.form.newEmail.trim()
        const res = await sendVerificationCode({
          email: email,
          captcha_token: '',
          captcha_action: 'change_email',
          purpose: 'BIND'
        })
        this.resendToken = res.data?.resend_token || ''
        uni.showToast({ title: '验证码已发送', icon: 'success' })
        this.startCountdown()
      } catch (e) {
        console.error('[ChangeEmail] 发送验证码失败:', e)
        uni.showToast({ title: e.message || '发送失败', icon: 'none' })
      } finally {
        this.sending = false
      }
    },
    startCountdown() {
      this.codeCountdown = 60
      this.timer = setInterval(() => {
        this.codeCountdown--
        if (this.codeCountdown <= 0) {
          clearInterval(this.timer)
          this.timer = null
        }
      }, 1000)
    },
    async handleSubmit() {
      if (!this.canSubmit) return

      this.submitting = true
      try {
        await confirmChangeEmail(this.form.newEmail.trim(), this.form.code.trim())
        uni.showToast({ title: '邮箱换绑成功', icon: 'success' })
        setTimeout(() => uni.navigateBack(), 1500)
      } catch (e) {
        console.error('[ChangeEmail] 换绑失败:', e)
        uni.showToast({ title: e.message || '换绑失败', icon: 'none' })
      } finally {
        this.submitting = false
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.change-email-page {
  min-height: 100vh;
  background: #f5f5f5;
  padding-bottom: 32rpx;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 32rpx;
  background: #fff;
}

.back-btn {
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.page-title {
  font-size: 32rpx;
  font-weight: 500;
  color: #202124;
}

.placeholder {
  width: 64rpx;
}

.form-section {
  margin: 16rpx 24rpx;
  background: #fff;
  border-radius: 16rpx;
  overflow: hidden;
  box-shadow: 0 2rpx 12rpx rgba(0,0,0,0.06);
}

.form-item {
  padding: 24rpx 32rpx;
  border-bottom: 1rpx solid #f0f0f0;
  &:last-child { border-bottom: none; }
}

.form-label {
  font-size: 26rpx;
  color: #5f6368;
  margin-bottom: 12rpx;
  display: block;
}

.form-value {
  font-size: 28rpx;
  color: #202124;
}

.code-input-wrap {
  display: flex;
  gap: 16rpx;
}

.code-input-wrap .u-input {
  flex: 1;
}

.save-section {
  padding: 48rpx 32rpx;
}
</style>