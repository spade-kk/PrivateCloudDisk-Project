<template>
  <view class="login-page">
    <!-- Logo 区域 -->
    <view class="logo-section">
      <image class="logo" src="/static/logo.png" mode="aspectFit" />
      <text class="app-name">PrivateCloudDisk</text>
      <text class="app-desc">企业私有云盘</text>
    </view>

    <!-- 表单区域 -->
    <view class="form-section">
      <!-- 登录方式切换 -->
      <view class="login-type-tabs">
        <view
          class="tab-item"
          :class="{ active: loginType === 'account' }"
          @click="loginType = 'account'"
        >账号登录</view>
        <view
          class="tab-item"
          :class="{ active: loginType === 'phone' }"
          @click="loginType = 'phone'"
        >手机号登录</view>
      </view>

      <!-- 账号登录 -->
      <template v-if="loginType === 'account'">
        <u-form :model="form" :rules="rules" ref="formRef" labelPosition="top">
          <u-form-item label="账号" prop="account" required>
            <u-input
              v-model="form.account"
              placeholder="请输入账号"
              prefixIcon="account"
              clearable
            />
          </u-form-item>
          <u-form-item label="密码" prop="password" required>
            <u-input
              v-model="form.password"
              type="password"
              placeholder="请输入密码"
              prefixIcon="lock"
              clearable
            />
          </u-form-item>
        </u-form>
      </template>

      <!-- 手机号登录 -->
      <template v-if="loginType === 'phone'">
        <u-form :model="form" :rules="rules" ref="formRef2" labelPosition="top">
          <u-form-item label="手机号" prop="phone_number" required>
            <u-input
              v-model="form.phone_number"
              placeholder="请输入手机号"
              prefixIcon="phone"
              type="number"
              maxlength="11"
              clearable
            />
          </u-form-item>
          <u-form-item label="密码" prop="password" required>
            <u-input
              v-model="form.password"
              type="password"
              placeholder="请输入密码"
              prefixIcon="lock"
              clearable
            />
          </u-form-item>
        </u-form>
      </template>

      <!-- 登录按钮 -->
      <u-button
        type="primary"
        :loading="loading"
        @click="handleLogin"
        class="login-btn"
        text="登 录"
      />

      <!-- 注册入口 -->
      <view class="register-link">
        <text>还没有账号？</text>
        <text class="link" @click="goRegister">立即注册</text>
      </view>
    </view>
  </view>
</template>

<script>
import { useUserStore } from '@/store/user'
import { validateAccount, validatePhone, validatePassword } from '@/utils/validator'

export default {
  data() {
    return {
      loginType: 'phone',
      loading: false,
      form: {
        account: '',
        phone_number: '',
        password: ''
      },
      rules: {}
    }
  },
  computed: {
    currentRules() {
      if (this.loginType === 'account') {
        return {
          account: [
            { required: true, message: '请输入账号', trigger: 'blur' },
            { validator: (rule, value, cb) => {
              const msg = validateAccount(value)
              cb(msg ? new Error(msg) : undefined)
            }, trigger: 'blur' }
          ],
          password: [
            { required: true, message: '请输入密码', trigger: 'blur' }
          ]
        }
      }
      return {
        phone_number: [
          { required: true, message: '请输入手机号', trigger: 'blur' },
          { validator: (rule, value, cb) => {
            const msg = validatePhone(value)
            cb(msg ? new Error(msg) : undefined)
          }, trigger: 'blur' }
        ],
        password: [
          { required: true, message: '请输入密码', trigger: 'blur' }
        ]
      }
    }
  },
  methods: {
    async handleLogin() {
      // 表单校验
      const formRef = this.$refs[this.loginType === 'account' ? 'formRef' : 'formRef2']
      if (!formRef) return

      try {
        await formRef.validate()
      } catch {
        return
      }

      this.loading = true
      try {
        const userStore = useUserStore()
        const params = {
          password: this.form.password
        }
        if (this.loginType === 'account') {
          params.account = this.form.account
        } else {
          params.phone_number = this.form.phone_number
        }
        await userStore.doLogin(params)
        uni.showToast({ title: '登录成功', icon: 'success' })
        setTimeout(() => {
          uni.switchTab({ url: '/pages/index/index' })
        }, 500)
      } catch (e) {
        console.error('登录失败:', e)
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
  padding: 80rpx 48rpx;
  background: $bg-white;
}

.logo-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 80rpx;

  .logo {
    width: 120rpx;
    height: 120rpx;
    margin-bottom: 24rpx;
  }

  .app-name {
    font-size: 40rpx;
    font-weight: 700;
    color: $primary-color;
    margin-bottom: 8rpx;
  }

  .app-desc {
    font-size: 26rpx;
    color: $text-secondary;
  }
}

.login-type-tabs {
  display: flex;
  margin-bottom: 48rpx;
  border-bottom: 2rpx solid $border-color;

  .tab-item {
    flex: 1;
    text-align: center;
    padding: 20rpx 0;
    font-size: 30rpx;
    color: $text-secondary;
    border-bottom: 4rpx solid transparent;
    transition: all 0.3s;

    &.active {
      color: $primary-color;
      font-weight: 600;
      border-bottom-color: $primary-color;
    }
  }
}

.login-btn {
  margin-top: 48rpx;
  height: 88rpx;
  border-radius: $radius-md;
  font-size: 32rpx;
}

.register-link {
  text-align: center;
  margin-top: 32rpx;
  font-size: 26rpx;
  color: $text-secondary;

  .link {
    color: $primary-color;
    margin-left: 8rpx;
  }
}
</style>