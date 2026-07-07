<template>
  <view class="empty-state">
    <u-icon :name="icon" size="80" color="#c4c7cc" />
    <text class="empty-text">{{ text }}</text>
    <text class="empty-sub" v-if="subText">{{ subText }}</text>
    <slot />
  </view>
</template>

<script>
export default {
  name: 'EmptyState',
  props: {
    icon: { type: String, default: 'folder' },
    text: { type: String, default: '暂无数据' },
    subText: { type: String, default: '' }
  }
}
</script>

<style lang="scss" scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 120rpx 48rpx;
}
.empty-text { font-size: 30rpx; color: #9aa0a6; margin-top: 24rpx; }
.empty-sub { font-size: 24rpx; color: #c4c7cc; margin-top: 8rpx; }
</style>