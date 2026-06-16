<template>
  <view class="edit-profile-page">
    <view class="avatar-section" @click="changeAvatar">
      <u-avatar :src="userStore.avatarUrl" size="120" mode="aspectFill" />
      <text class="avatar-hint">点击更换头像</text>
    </view>

    <u-cell-group>
      <u-cell title="账号" :value="userStore.profile?.account || ''" :isLink="false" />
      <u-cell title="用户 ID" :value="userStore.userId || ''" :isLink="false" />
    </u-cell-group>

    <u-form :model="form" :rules="rules" ref="formRef" labelPosition="top">
      <view class="form-section">
        <u-form-item label="用户名" prop="name">
          <u-input
            v-model="form.name"
            :placeholder="userStore.profile?.name || '请输入用户名'"
            prefixIcon="account"
            clearable
          />
        </u-form-item>

        <u-form-item label="手机号" prop="phone_number">
          <u-input
            v-model="form.phone_number"
            :placeholder="userStore.profile?.phone_number || '请输入手机号'"
            prefixIcon="phone"
            type="number"
            maxlength="11"
            clearable
          />
        </u-form-item>

        <u-form-item label="邮箱" prop="email">
          <u-input
            v-model="form.email"
            :placeholder="userStore.profile?.email || '请输入邮箱'"
            prefixIcon="email"
            clearable
          />
        </u-form-item>
      </view>
    </u-form>

    <u-button
      type="primary"
      text="保存修改"
      :loading="saving"
      @click="handleSave"
      class="save-btn"
    />
  </view>
</template>

<script>
import { useUserStore } from '@/store/user'
import { uploadAvatar } from '@/api/user'
import { validatePhone, validateName, validateEmail } from '@/utils/validator'

export default {
  data() {
    return {
      saving: false,
      form: {
        name: '',
        phone_number: '',
        email: ''
      },
      rules: {
        name: [
          { validator: (rule, value, cb) => {
            if (!value) return cb()
            const msg = validateName(value)
            cb(msg ? new Error(msg) : undefined)
          }, trigger: 'blur' }
        ],
        phone_number: [
          { validator: (rule, value, cb) => {
            if (!value) return cb()
            const msg = validatePhone(value)
            cb(msg ? new Error(msg) : undefined)
          }, trigger: 'blur' }
        ],
        email: [
          { validator: (rule, value, cb) => {
            if (!value) return cb()
            const msg = validateEmail(value)
            cb(msg ? new Error(msg) : undefined)
          }, trigger: 'blur' }
        ]
      }
    }
  },
  computed: {
    userStore() { return useUserStore() }
  },
  onShow() {
    const profile = this.userStore.profile
    if (profile) {
      this.form.name = profile.name || ''
      this.form.phone_number = profile.phone_number || ''
      this.form.email = profile.email || ''
    }
  },
  methods: {
    changeAvatar() {
      uni.chooseImage({
        count: 1,
        sizeType: ['compressed'],
        sourceType: ['album', 'camera'],
        success: async (res) => {
          uni.showLoading({ title: '上传中...' })
          try {
            await uploadAvatar(res.tempFilePaths[0])
            uni.hideLoading()
            uni.showToast({ title: '头像上传成功', icon: 'success' })
            await this.userStore.fetchProfile()
          } catch (e) {
            uni.hideLoading()
          }
        }
      })
    },

    async handleSave() {
      const { name, phone_number, email } = this.form
      const profile = this.userStore.profile

      if (!name && !phone_number && !email) {
        uni.showToast({ title: '没有修改任何信息', icon: 'none' })
        return
      }

      try {
        await this.$refs.formRef.validate()
      } catch {
        return
      }

      this.saving = true
      try {
        const params = {}
        if (name && name !== profile?.name) params.new_name = name
        if (phone_number && phone_number !== profile?.phone_number) params.new_phone_number = phone_number
        if (email && email !== profile?.email) params.new_email = email

        if (Object.keys(params).length === 0) {
          uni.showToast({ title: '没有修改任何信息', icon: 'none' })
          return
        }

        await this.userStore.doUpdateProfile(params)
        uni.showToast({ title: '修改成功', icon: 'success' })
        setTimeout(() => uni.navigateBack(), 1000)
      } catch (e) {
      } finally {
        this.saving = false
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.edit-profile-page {
  min-height: 100vh;
  padding: 24rpx;
}

.avatar-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 48rpx 0;
  background: $bg-white;
  border-radius: $radius-md;
  margin-bottom: 24rpx;

  .avatar-hint {
    font-size: 24rpx;
    color: $primary-color;
    margin-top: 16rpx;
  }
}

.form-section {
  background: $bg-white;
  margin: 24rpx 0;
  padding: 24rpx;
  border-radius: $radius-md;
}

.save-btn {
  height: 88rpx;
  border-radius: $radius-md;
  font-size: 32rpx;
  margin-top: 48rpx;
}
</style>