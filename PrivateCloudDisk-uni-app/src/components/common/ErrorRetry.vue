<template>
  <view class="error-retry" v-if="visible">
    <u-icon name="error-circle" size="64" color="#ea4335" />
    <text class="error-title">{{ title }}</text>
    <text class="error-msg">{{ message }}</text>
    <u-button type="primary" size="small" text="重试" @click="$emit('retry')" class="retry-btn" />
  </view>
</template>

<script>
export default {
  name: 'ErrorRetry',
  props: {
    visible: { type: Boolean, default: false },
    title: { type: String, default: '加载失败' },
    message: { type: String, default: '请检查网络后重试' }
  },
  emits: ['retry']
}
</script>

<style lang="scss" scoped>
.error-retry {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 100rpx 48rpx;
}
.error-title { font-size: 32rpx; color: #202124; margin-top: 24rpx; font-weight: 500; }
.error-msg { font-size: 26rpx; color: #9aa0a6; margin-top: 8rpx; }
.retry-btn { margin-top: 32rpx; }
</style>