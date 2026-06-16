<template>
  <view class="register-page">
    <view class="page-header">
      <text class="title">注册账号</text>
      <text class="subtitle">创建您的 PrivateCloudDisk 账号</text>
    </view>

    <view class="form-section">
      <u-form :model="form" :rules="rules" ref="formRef" labelPosition="top">
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

        <u-form-item label="验证码" prop="code" required>
          <u-input
            v-model="form.code"
            placeholder="请输入验证码"
            prefixIcon="tags"
            clearable
          >
            <template #suffix>
              <u-button
                type="primary"
                size="small"
                :text="codeBtnText"
                :disabled="codeBtnDisabled"
                @click="sendCode"
                style="height: 56rpx; font-size: 24rpx;"
              />
            </template>
          </u-input>
        </u-form-item>

        <u-form-item label="用户名" prop="name" required>
          <u-input
            v-model="form.name"
            placeholder="2-10位字母、数字或中文"
            prefixIcon="account"
            clearable
          />
        </u-form-item>

        <u-form-item label="密码" prop="password" required>
          <u-input
            v-model="form.password"
            type="password"
            placeholder="8-15位，包含字母和数字"
            prefixIcon="lock"
            clearable
          />
        </u-form-item>

        <u-form-item label="确认密码" prop="confirm_password" required>
          <u-input
            v-model="form.confirm_password"
            type="password"
            placeholder="请再次输入密码"
            prefixIcon="lock"
            clearable
          />
        </u-form-item>
      </u-form>

      <u-button
        type="primary"
        :loading="loading"
        @click="handleRegister"
        class="register-btn"
        text="注 册"
      />

      <view class="login-link">
        <text>已有账号？</text>
        <text class="link" @click="goLogin">立即登录</text>
      </view>
    </view>
  </view>
</template>

<script>
import { useUserStore } from '@/store/user'
import { validatePhone, validatePassword, validateName } from '@/utils/validator'

export default {
  data() {
    return {
      loading: false,
      codeBtnText: '获取验证码',
      codeBtnDisabled: false,
      codeCountdown: 0,
      form: {
        phone_number: '',
        code: '',
        name: '',
        password: '',
        confirm_password: ''
      },
      rules: {
        phone_number: [
          { required: true, message: '请输入手机号', trigger: 'blur' },
          { validator: (rule, value, cb) => {
            const msg = validatePhone(value)
            cb(msg ? new Error(msg) : undefined)
          }, trigger: 'blur' }
        ],
        code: [
          { required: true, message: '请输入验证码', trigger: 'blur' }
        ],
        name: [
          { required: true, message: '请输入用户名', trigger: 'blur' },
          { validator: (rule, value, cb) => {
            const msg = validateName(value)
            cb(msg ? new Error(msg) : undefined)
          }, trigger: 'blur' }
        ],
        password: [
          { required: true, message: '请输入密码', trigger: 'blur' },
          { validator: (rule, value, cb) => {
            const msg = validatePassword(value)
            cb(msg ? new Error(msg) : undefined)
          }, trigger: 'blur' }
        ],
        confirm_password: [
          { required: true, message: '请确认密码', trigger: 'blur' },
          { validator: (rule, value, cb) => {
            if (value !== this.form.password) cb(new Error('两次密码不一致'))
            else cb()
          }, trigger: 'blur' }
        ]
      }
    }
  },
  methods: {
    sendCode() {
      const msg = validatePhone(this.form.phone_number)
      if (msg) {
        uni.showToast({ title: msg, icon: 'none' })
        return
      }
      // TODO: 对接验证码发送接口
      uni.showToast({ title: '验证码已发送', icon: 'success' })
      this.codeBtnDisabled = true
      this.codeCountdown = 60
      const timer = setInterval(() => {
        this.codeCountdown--
        this.codeBtnText = `${this.codeCountdown}s`
        if (this.codeCountdown <= 0) {
          clearInterval(timer)
          this.codeBtnDisabled = false
          this.codeBtnText = '获取验证码'
        }
      }, 1000)
    },

    async handleRegister() {
      try {
        await this.$refs.formRef.validate()
      } catch {
        return
      }

      this.loading = true
      try {
        const userStore = useUserStore()
        const account = await userStore.doRegister({
          phone_number: this.form.phone_number,
          password: this.form.password,
          code: this.form.code,
          name: this.form.name
        })
        uni.showToast({ title: `注册成功, 账号: ${account}`, icon: 'success' })
        setTimeout(() => {
          uni.navigateBack()
        }, 1500)
      } catch (e) {
        console.error('注册失败:', e)
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
  padding: 48rpx;
  background: $bg-white;
}

.page-header {
  margin-bottom: 48rpx;

  .title {
    font-size: 40rpx;
    font-weight: 700;
    color: $text-primary;
    display: block;
  }

  .subtitle {
    font-size: 26rpx;
    color: $text-secondary;
    margin-top: 8rpx;
    display: block;
  }
}

.register-btn {
  margin-top: 48rpx;
  height: 88rpx;
  border-radius: $radius-md;
  font-size: 32rpx;
}

.login-link {
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