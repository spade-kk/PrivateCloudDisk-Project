<template>
  <view class="breadcrumb-nav">
    <view class="back-btn" @click="$emit('back')">
      <u-icon name="arrow-left" size="36" color="#1a73e8" />
    </view>
    <scroll-view scroll-x class="crumb-scroll" :show-scrollbar="false">
      <view class="crumb-list">
        <view class="crumb-item" @click="$emit('home')">
          <text class="crumb-text crumb-root">首页</text>
        </view>
        <template v-for="(item, idx) in pathStack" :key="item.node_id || idx">
          <text class="crumb-sep">/</text>
          <view
            class="crumb-item"
            :class="{ 'crumb-active': idx === pathStack.length - 1 }"
            @click="$emit('navigate', item)"
          >
            <text class="crumb-text">{{ item.node_name }}</text>
          </view>
        </template>
      </view>
    </scroll-view>
  </view>
</template>

<script>
export default {
  name: 'BreadcrumbNav',
  props: {
    pathStack: { type: Array, default: () => [] }
  },
  emits: ['back', 'home', 'navigate']
}
</script>

<style lang="scss" scoped>
.breadcrumb-nav {
  display: flex;
  align-items: center;
  padding: 16rpx 24rpx;
  background: #fff;
  border-bottom: 1rpx solid #f0f0f0;
}
.back-btn {
  width: 56rpx;
  height: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-right: 8rpx;
}
.crumb-scroll { flex: 1; white-space: nowrap; }
.crumb-list { display: flex; align-items: center; }
.crumb-item { padding: 8rpx 12rpx; border-radius: 8rpx; }
.crumb-item:active { background: #f0f4ff; }
.crumb-text { font-size: 28rpx; color: #5f6368; }
.crumb-root { color: #1a73e8; font-weight: 500; }
.crumb-active .crumb-text { color: #1a73e8; font-weight: 600; }
.crumb-sep { font-size: 28rpx; color: #c4c7cc; margin: 0 4rpx; }
</style>