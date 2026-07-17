<template>
  <view class="breadcrumb-nav">
    <view class="back-btn" @click="$emit('back')">
      <u-icon name="arrow-left" size="36" color="#4F6EF7" />
    </view>
    <scroll-view scroll-x class="crumb-scroll" :show-scrollbar="false">
      <view class="crumb-list">
        <view class="crumb-item" @click="$emit('home')">
          <u-icon name="home" size="28" color="#4F6EF7" />
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
  padding: 14rpx 24rpx;
  margin: 12rpx 24rpx;
  background: $color-bg-card;
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
}

.back-btn {
  width: 56rpx;
  height: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border-radius: $radius-sm;
  transition: background $transition-fast;

  &:active {
    background: $color-bg-hover;
  }
}

.crumb-scroll {
  flex: 1;
  margin-left: 4rpx;
}

.crumb-list {
  display: flex;
  align-items: center;
  white-space: nowrap;
}

.crumb-item {
  display: flex;
  align-items: center;
  padding: 8rpx 12rpx;
  border-radius: $radius-sm;
  transition: background $transition-fast;

  &:active {
    background: $color-bg-hover;
  }
}

.crumb-active {
  background: $color-primary-lighter;
}

.crumb-text {
  font-size: $font-size-body-sm;
  color: $color-text-regular;
  max-width: 200rpx;
  @include text-ellipsis;
}

.crumb-active .crumb-text {
  color: $color-primary;
  font-weight: $font-weight-medium;
}

.crumb-root {
  .crumb-item:first-child & {
    color: $color-primary;
    font-weight: $font-weight-medium;
  }
}

.crumb-sep {
  font-size: $font-size-body-sm;
  color: $color-text-placeholder;
  margin: 0 2rpx;
}
</style>