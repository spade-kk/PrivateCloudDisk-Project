<template>
  <view class="empty-state">
    <view class="empty-icon-wrap">
      <u-icon :name="icon" size="72" color="#C0C0D0" />
    </view>
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
  padding: 100rpx 48rpx;
}

.empty-icon-wrap {
  width: 120rpx;
  height: 120rpx;
  border-radius: 50%;
  background: $color-bg-page;
  @include flex-center;
  margin-bottom: 16rpx;
}

.empty-text {
  font-size: $font-size-body;
  color: $color-text-secondary;
  font-weight: $font-weight-medium;
}

.empty-sub {
  font-size: $font-size-body-sm;
  color: $color-text-placeholder;
  margin-top: 8rpx;
  text-align: center;
}
</style>