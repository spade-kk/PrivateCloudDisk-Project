<template>
  <view class="edit-profile-page">
    <view class="avatar-section">
      <u-avatar :src="avatar" size="140" />
      <text class="avatar-tip" @click="handleChangeAvatar">更换头像</text>
    </view>

    <view class="form-section">
      <view class="form-item">
        <text class="form-label">用户名</text>
        <u-input v-model="form.username" placeholder="请输入用户名" clearable />
      </view>
      <view class="form-item">
        <text class="form-label">邮箱</text>
        <u-input v-model="form.email" placeholder="请输入邮箱" clearable />
      </view>
      <view class="form-item">
        <text class="form-label">手机号</text>
        <u-input v-model="form.phone" type="number" placeholder="请输入手机号" clearable />
      </view>
    </view>

    <view class="save-section">
      <u-button
        type="primary"
        text="保存"
        :loading="saving"
        @click="handleSave"
        customStyle="height: 88rpx; font-size: 32rpx; border-radius: 16rpx;"
      />
    </view>
  </view>
</template>

<script>
import { useUserAuth } from '@/composables/useUserAuth'
import { updateProfile } from '@/api/auth'

export default {
  setup() {
    const { userStore } = useUserAuth()
    return { userStore }
  },
  data() {
    return {
      form: {
        username: '',
        email: '',
        phone: ''
      },
      avatar: '',
      saving: false
    }
  },
  onShow() {
    this.form.username = this.userStore.userName || ''
    this.form.email = this.userStore.userEmail || ''
    this.form.phone = this.userStore.userPhone || ''
    this.avatar = this.userStore.userAvatar || ''
  },
  methods: {
    handleChangeAvatar() {
      uni.chooseImage({
        count: 1,
        sizeType: ['compressed'],
        success: (res) => {
          this.avatar = res.tempFilePaths[0]
        }
      })
    },
    async handleSave() {
      if (!this.form.username.trim()) {
        return uni.showToast({ title: '请输入用户名', icon: 'none' })
      }
      this.saving = true
      try {
        await updateProfile({
          username: this.form.username.trim(),
          email: this.form.email.trim(),
          phone: this.form.phone.trim()
        })
        this.userStore.setUserInfo({
          user_name: this.form.username.trim(),
          email: this.form.email.trim(),
          phone: this.form.phone.trim()
        })
        uni.showToast({ title: '保存成功', icon: 'success' })
        setTimeout(() => uni.navigateBack(), 1500)
      } catch (e) { /* 已处理 */ } finally {
        this.saving = false
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.edit-profile-page { min-height: 100vh; background: #f5f5f5; padding-bottom: 32rpx; }

.avatar-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 48rpx 0;
  background: #fff;
  margin-bottom: 16rpx;
}
.avatar-tip { font-size: 26rpx; color: #1a73e8; margin-top: 16rpx; }

.form-section {
  margin: 0 24rpx;
  background: #fff;
  border-radius: 16rpx;
  overflow: hidden;
  box-shadow: 0 2rpx 12rpx rgba(0,0,0,0.06);
}
.form-item { padding: 24rpx 32rpx; border-bottom: 1rpx solid #f0f0f0; &:last-child { border-bottom: none; } }
.form-label { font-size: 28rpx; color: #5f6368; margin-bottom: 12rpx; display: block; }

.save-section { padding: 48rpx 32rpx; }
</style>