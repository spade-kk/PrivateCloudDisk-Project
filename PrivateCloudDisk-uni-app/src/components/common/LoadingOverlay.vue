<template>
  <view class="loading-overlay" v-if="visible">
    <u-loading-icon size="36" color="#1a73e8" :text="text" textSize="28" />
  </view>
</template>

<script>
export default {
  name: 'LoadingOverlay',
  props: {
    visible: { type: Boolean, default: false },
    text: { type: String, default: '加载中...' }
  }
}
</script>

<style lang="scss" scoped>
.loading-overlay {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 80rpx 0;
}
</style>