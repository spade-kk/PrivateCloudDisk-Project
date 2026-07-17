<template>
  <view class="edit-profile-page">
    <view class="page-header">
      <view class="back-btn" @click="goBack">
        <u-icon name="arrow-left" size="36" color="#202124" />
      </view>
      <text class="page-title">编辑资料</text>
      <view class="placeholder" />
    </view>

    <view class="avatar-section">
      <u-avatar :src="avatar" size="140" />
      <text class="avatar-tip" @click="handleChangeAvatar">更换头像</text>
    </view>

    <view class="form-section">
      <view class="form-item">
        <text class="form-label">用户名</text>
        <u-input v-model="form.name" placeholder="请输入用户名" clearable />
      </view>

      <view class="form-item link-item" @click="goChangeEmail">
        <text class="form-label">邮箱</text>
        <view class="link-content">
          <text class="form-value">{{ currentEmail || '未绑定' }}</text>
          <u-icon name="arrow-right" size="28" color="#C0C0D0" />
        </view>
      </view>

      <view class="form-item link-item" @click="goChangePhone">
        <text class="form-label">手机号</text>
        <view class="link-content">
          <text class="form-value">{{ currentPhone || '未绑定' }}</text>
          <u-icon name="arrow-right" size="28" color="#C0C0D0" />
        </view>
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
import { useUserStore } from '@/store/user'
import { getUserInfo } from '@/api/user'

export default {
  data() {
    return {
      form: {
        name: ''
      },
      avatar: '',
      currentEmail: '',
      currentPhone: '',
      saving: false
    }
  },
  onShow() {
    this.loadUserInfo()
  },
  methods: {
    goBack() {
      uni.navigateBack()
    },
    async loadUserInfo() {
      try {
        const res = await getUserInfo()
        const profile = res.data
        this.form.name = profile.name || ''
        this.currentEmail = profile.email || ''
        this.currentPhone = profile.phone_number || ''
        this.avatar = profile.image_path || ''
      } catch (e) {
        console.error('[EditProfile] 加载用户信息失败:', e)
      }
    },
    handleChangeAvatar() {
      uni.chooseImage({
        count: 1,
        sizeType: ['compressed'],
        success: (res) => {
          this.avatar = res.tempFilePaths[0]
        }
      })
    },
    goChangeEmail() {
      uni.navigateTo({ url: '/pages/profile/change-email/index' })
    },
    goChangePhone() {
      uni.navigateTo({ url: '/pages/profile/change-phone/index' })
    },
    async handleSave() {
      if (!this.form.name.trim()) {
        return uni.showToast({ title: '请输入用户名', icon: 'none' })
      }
      this.saving = true
      try {
        const userStore = useUserStore()
        await userStore.doUpdateProfile({
          new_name: this.form.name.trim()
        })
        uni.showToast({ title: '保存成功', icon: 'success' })
        setTimeout(() => uni.navigateBack(), 1500)
      } catch (e) {
        console.error('[EditProfile] 保存失败:', e)
        uni.showToast({ title: e.message || '保存失败', icon: 'none' })
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

.avatar-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 48rpx 0;
  background: #fff;
  margin-bottom: 16rpx;
}
.avatar-tip { font-size: 26rpx; color: #4F6EF7; margin-top: 16rpx; }

.form-section {
  margin: 0 24rpx;
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
  font-size: 28rpx;
  color: #5f6368;
  margin-bottom: 12rpx;
  display: block;
}

.form-value {
  font-size: 28rpx;
  color: #202124;
}

.link-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 28rpx 32rpx;
}

.link-item .form-label {
  margin-bottom: 0;
}

.link-content {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.save-section { padding: 48rpx 32rpx; }
</style>