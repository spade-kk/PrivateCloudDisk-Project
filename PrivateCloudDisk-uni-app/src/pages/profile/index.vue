<template>
  <view class="profile-page">
    <!-- 用户信息卡片 -->
    <view class="user-card">
      <u-avatar
        :src="userStore.avatarUrl"
        size="120"
        mode="aspectFill"
      />
      <view class="user-info">
        <text class="user-name">{{ userStore.displayName }}</text>
        <text class="user-account">{{ userStore.profile?.phone_number || '' }}</text>
      </view>
      <u-icon name="edit-pen" size="36" color="#fff" @click="goEditProfile" />
    </view>

    <!-- 存储空间 -->
    <view class="section-card" @click="goFileList">
      <view class="section-title flex-between">
        <text>存储空间</text>
        <text class="quota-text" v-if="quota">
          {{ formatFileSize(quota.used_capacity || 0) }} / {{ formatFileSize(quota.total_capacity || 0) }}
        </text>
      </view>
      <u-line-progress
        :percentage="usagePercent"
        activeColor="#1a73e8"
        height="10"
        :showText="false"
      />
      <view class="quota-detail flex-between">
        <text>文件 {{ quota?.file_count || 0 }} 个</text>
        <text>{{ usagePercent }}% 已使用</text>
      </view>
    </view>

    <!-- 菜单列表 -->
    <view class="menu-section">
      <u-cell-group>
        <u-cell
          title="个人信息"
          icon="account"
          :isLink="true"
          @click="goEditProfile"
        />
        <u-cell
          title="修改密码"
          icon="lock"
          :isLink="true"
          @click="showChangePassword"
        />
        <u-cell
          title="上传管理"
          icon="upload"
          :isLink="true"
          @click="goUpload"
        />
        <u-cell
          title="在线设备"
          icon="server-man"
          :isLink="true"
          @click="goDevices"
        />
        <u-cell
          title="设置"
          icon="setting"
          :isLink="true"
          @click="goSettings"
        />
        <u-cell
          title="关于"
          icon="info-circle"
          :isLink="true"
          @click="goAbout"
        />
      </u-cell-group>
    </view>

    <!-- 退出登录 -->
    <view class="logout-section">
      <u-button
        type="error"
        text="退出登录"
        @click="handleLogout"
        class="logout-btn"
      />
      <text class="delete-account" @click="handleDeleteAccount">注销账号</text>
    </view>

    <!-- 修改密码弹窗 -->
    <u-modal
      :show="passwordModal"
      title="修改密码"
      :showCancelButton="true"
      confirmText="确认修改"
      @confirm="confirmChangePassword"
      @cancel="passwordModal = false"
    >
      <view class="modal-form">
        <u-input
          v-model="pwdForm.user_password"
          type="password"
          placeholder="原密码"
          style="margin-bottom: 20rpx;"
        />
        <u-input
          v-model="pwdForm.new_password"
          type="password"
          placeholder="新密码"
        />
      </view>
    </u-modal>
  </view>
</template>

<script>
import { useUserStore } from '@/store/user'
import { useAppStore } from '@/store/app'
import { getMyQuota } from '@/api/quota'
import { formatFileSize } from '@/utils/helper'
import { validatePassword } from '@/utils/validator'

export default {
  data() {
    return {
      quota: null,
      passwordModal: false,
      pwdForm: { user_password: '', new_password: '' }
    }
  },
  computed: {
    userStore() { return useUserStore() },
    appStore() { return useAppStore() },
    usagePercent() {
      if (!this.quota || !this.quota.total_capacity) return 0
      return ((this.quota.used_capacity / this.quota.total_capacity) * 100).toFixed(1)
    }
  },
  onShow() {
    if (!this.userStore.isLoggedIn) {
      uni.reLaunch({ url: '/pages/login/index' })
      return
    }
    this.loadQuota()
  },
  methods: {
    formatFileSize,

    async loadQuota() {
      try {
        const res = await getMyQuota()
        this.quota = res.data
      } catch (e) {}
    },

    goEditProfile() {
      uni.navigateTo({ url: '/pages/profile/edit' })
    },

    goFileList() {
      uni.switchTab({ url: '/pages/index/index' })
    },

    goUpload() {
      uni.navigateTo({ url: '/pages/upload/index' })
    },

    goDevices() {
      uni.showToast({ title: '设备管理开发中', icon: 'none' })
    },

    goSettings() {
      uni.navigateTo({ url: '/pages/settings/index' })
    },

    goAbout() {
      uni.showModal({
        title: 'PrivateCloudDisk',
        content: '企业私有云盘 v1.0.0',
        showCancel: false
      })
    },

    showChangePassword() {
      this.pwdForm = { user_password: '', new_password: '' }
      this.passwordModal = true
    },

    async confirmChangePassword() {
      const msg = validatePassword(this.pwdForm.new_password)
      if (msg) {
        uni.showToast({ title: msg, icon: 'none' })
        return
      }
      try {
        await this.userStore.doChangePassword(this.pwdForm)
        uni.showToast({ title: '密码修改成功', icon: 'success' })
        this.passwordModal = false
      } catch (e) {}
    },

    handleLogout() {
      uni.showModal({
        title: '退出登录',
        content: '确定要退出登录吗？',
        success: (res) => {
          if (res.confirm) {
            this.userStore.logout()
          }
        }
      })
    },

    async handleDeleteAccount() {
      uni.showModal({
        title: '注销账号',
        content: '确定要注销账号吗？此操作不可撤销，所有数据将被永久删除。',
        confirmColor: '#ea4335',
        success: async (res) => {
          if (res.confirm) {
            try {
              await this.userStore.doDeleteAccount()
              uni.showToast({ title: '账号已注销', icon: 'success' })
            } catch (e) {}
          }
        }
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.profile-page {
  min-height: 100vh;
  padding-bottom: 40rpx;
}

.user-card {
  display: flex;
  align-items: center;
  padding: 48rpx 32rpx;
  background: linear-gradient(135deg, #1a73e8, #4a90e2);

  .user-info {
    flex: 1;
    margin-left: 28rpx;

    .user-name {
      font-size: 36rpx;
      font-weight: 600;
      color: #fff;
      display: block;
    }

    .user-account {
      font-size: 24rpx;
      color: rgba(255, 255, 255, 0.8);
      margin-top: 8rpx;
      display: block;
    }
  }
}

.section-card {
  background: $bg-white;
  margin: 20rpx 24rpx;
  padding: 24rpx;
  border-radius: $radius-md;
  box-shadow: $shadow-light;

  .section-title {
    margin-bottom: 16rpx;

    text:first-child {
      font-weight: 600;
      font-size: 28rpx;
    }

    .quota-text {
      font-size: 24rpx;
      color: $text-secondary;
    }
  }

  .quota-detail {
    margin-top: 12rpx;
    font-size: 22rpx;
    color: $text-secondary;
  }
}

.menu-section {
  margin: 20rpx 24rpx;
  border-radius: $radius-md;
  overflow: hidden;
  box-shadow: $shadow-light;
}

.logout-section {
  padding: 48rpx 24rpx;
  text-align: center;

  .logout-btn {
    width: 100%;
    height: 88rpx;
    border-radius: $radius-md;
    font-size: 30rpx;
  }

  .delete-account {
    display: block;
    margin-top: 32rpx;
    font-size: 24rpx;
    color: $text-secondary;
  }
}

.modal-form {
  padding: 20rpx 0;
}
</style>