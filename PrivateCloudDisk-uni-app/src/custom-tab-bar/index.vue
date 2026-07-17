<template>
  <view class="custom-tabbar">
    <view class="tabbar-inner">
      <view
        v-for="(item, index) in tabList"
        :key="index"
        class="tabbar-item"
        :class="{ 'tabbar-item--active': currentIndex === index }"
        @click="handleTabClick(index)"
      >
        <u-icon
          :name="item.icon"
          :size="44"
          :color="currentIndex === index ? '#4F6EF7' : '#9A9AB0'"
          customStyle="display: flex; align-items: center; justify-content: center;"
        />
        <text class="tabbar-text">{{ item.text }}</text>
      </view>
    </view>
    <view class="tabbar-safe" />
  </view>
</template>

<script>
export default {
  name: 'CustomTabBar',
  data() {
    return {
      currentIndex: 0,
      tabList: [
        { text: '首页', icon: 'home' },
        { text: '收藏', icon: 'star' },
        { text: '回收站', icon: 'trash' },
        { text: '我的', icon: 'account' }
      ]
    }
  },
  methods: {
    handleTabClick(index) {
      if (this.currentIndex === index) return
      const pages = ['/pages/index/index', '/pages/favorites/index', '/pages/trash/index', '/pages/profile/index']
      this.currentIndex = index
      uni.switchTab({ url: pages[index] })
    }
  }
}
</script>

<style>
.custom-tabbar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 9999;
  background: #ffffff;
  border-top: 1rpx solid #f0f0f5;
}

.tabbar-inner {
  display: flex;
  align-items: center;
  height: 100rpx;
  padding: 0 8rpx;
}

.tabbar-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  padding: 6rpx 0;
  position: relative;
}

.tabbar-text {
  font-size: 20rpx;
  color: #9A9AB0;
  font-weight: 400;
  line-height: 1;
  margin-top: 2rpx;
}

.tabbar-item--active .tabbar-text {
  color: #4F6EF7;
  font-weight: 600;
}

.tabbar-safe {
  height: constant(safe-area-inset-bottom);
  height: env(safe-area-inset-bottom);
  background: #ffffff;
}
</style>