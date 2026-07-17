<template>
  <view class="app-root">
    <router-view />
  </view>
</template>

<script>
/**
 * App.vue - 应用根组件
 *
 * 全局职责:
 * - 生命周期管理 (初始化 Token、网络状态)
 * - 全局错误捕获
 * - 路由守卫
 * - 跨平台适配
 *
 * 支持平台：微信小程序、支付宝小程序、百度小程序、字节小程序
 */
import { useUserStore } from '@/store/user'
import { useAppStore } from '@/store/app'
import { getPlatformInfo, getPlatformStyleVars } from '@/utils/platform'

export default {
  globalData: {
    appVersion: '1.0.0',
    platform: '',
    launchTime: 0,
    platformInfo: null,
  },

  onLaunch() {
    const platformInfo = getPlatformInfo()
    this.globalData.platform = platformInfo.platform
    this.globalData.platformInfo = platformInfo
    this.globalData.launchTime = Date.now()

    console.log(
      `[App] 启动 | 平台: ${platformInfo.name} | ` +
      `${platformInfo.brand} ${platformInfo.model} | ` +
      `系统: ${platformInfo.system} | SDK: ${platformInfo.SDKVersion}`
    )

    this.initApp()
    this.setupGlobalErrorHandler()
    this.setupNetworkMonitor()
    this.applyPlatformStyles()
  },

  onShow() {
    console.log('[App] 前台显示')
  },

  onHide() {
    console.log('[App] 后台隐藏')
  },

  onError(err) {
    console.error('[App] 全局错误:', err)
  },

  methods: {
    /**
     * 初始化应用
     */
    initApp() {
      // 恢复持久化的登录状态
      const userStore = useUserStore()
      userStore.restoreSession()

      // 初始化应用状态
      const appStore = useAppStore()
      appStore.initNetworkStatus()
    },

    /**
     * 应用平台适配的 CSS 变量
     * 用于处理刘海屏、底部指示条等安全区域适配
     */
    applyPlatformStyles() {
      const vars = getPlatformStyleVars()
      // 将 CSS 变量注入到根元素，供全局使用
      const styleEl = (typeof document !== 'undefined') ? document.documentElement : null
      if (styleEl) {
        Object.entries(vars).forEach(([key, value]) => {
          styleEl.style.setProperty(key, value)
        })
      }
    },

    /**
     * 全局 Promise 错误捕获
     */
    setupGlobalErrorHandler() {
      if (typeof window !== 'undefined') {
        window.addEventListener('unhandledrejection', (event) => {
          console.error('[App] 未捕获的 Promise 错误:', event.reason)
          event.preventDefault()
        })

        window.addEventListener('error', (event) => {
          console.error('[App] 全局错误:', event.error)
        })
      }
    },

    /**
     * 网络状态监听
     */
    setupNetworkMonitor() {
      const appStore = useAppStore()

      uni.getNetworkType({
        success(res) {
          appStore.setNetworkConnected(res.networkType !== 'none')
        }
      })

      uni.onNetworkStatusChange((res) => {
        appStore.setNetworkConnected(res.isConnected)
        if (!res.isConnected) {
          uni.showToast({
            title: '网络连接已断开',
            icon: 'none',
            duration: 2000
          })
        }
      })
    }
  }
}
</script>

<style lang="scss">
/* ==================== 全局样式 ==================== */
@import 'uview-plus/index.scss';
@import "@/styles/design-system.scss";

/* 页面基础样式 */
page {
  background-color: $color-bg-page;
  font-family: $font-family-base;
  font-size: $font-size-body;
  color: $color-text-primary;
  line-height: $line-height-normal;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

/* 全局滚动条隐藏 */
::-webkit-scrollbar {
  display: none;
  width: 0;
  height: 0;
}

/* ==================== 工具类 ==================== */

/* 文本省略 */
.ellipsis {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ellipsis-2 {
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.ellipsis-3 {
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
}

/* 弹性布局 */
.flex-center {
  display: flex;
  align-items: center;
  justify-content: center;
}

.flex-between {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.flex-column {
  display: flex;
  flex-direction: column;
}

.flex-1 {
  flex: 1;
}

/* 间距 */
.p-16 { padding: 16rpx; }
.p-24 { padding: 24rpx; }
.p-32 { padding: 32rpx; }
.px-24 { padding-left: 24rpx; padding-right: 24rpx; }
.px-32 { padding-left: 32rpx; padding-right: 32rpx; }
.py-16 { padding-top: 16rpx; padding-bottom: 16rpx; }
.py-24 { padding-top: 24rpx; padding-bottom: 24rpx; }
.mt-8 { margin-top: 8rpx; }
.mt-16 { margin-top: 16rpx; }
.mt-24 { margin-top: 24rpx; }
.mt-32 { margin-top: 32rpx; }
.mb-16 { margin-bottom: 16rpx; }
.mb-24 { margin-bottom: 24rpx; }
.ml-8 { margin-left: 8rpx; }
.mr-8 { margin-right: 8rpx; }

/* 文字 */
.text-center { text-align: center; }
.text-left { text-align: left; }
.text-right { text-align: right; }
.text-xs { font-size: $font-size-caption; }
.text-sm { font-size: $font-size-body-sm; }
.text-md { font-size: $font-size-body; }
.text-lg { font-size: $font-size-subtitle; }
.text-xl { font-size: $font-size-title; }
.text-bold { font-weight: $font-weight-bold; }
.text-primary { color: $color-primary; }
.text-danger { color: $color-danger; }
.text-success { color: $color-success; }
.text-warning { color: $color-warning; }
.text-muted { color: $color-text-secondary; }

/* 卡片通用 */
.card {
  background: $color-bg-card;
  border-radius: $card-radius;
  padding: $card-padding;
  box-shadow: $card-shadow;
}

/* 毛玻璃效果 */
.glass {
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(20rpx);
  -webkit-backdrop-filter: blur(20rpx);
}

/* 安全区域 */
.safe-bottom {
  padding-bottom: calc(env(safe-area-inset-bottom) + 16rpx);
}

/* 按钮样式覆盖 */
.u-button--primary {
  background-color: $color-primary !important;
  border-color: $color-primary !important;
}

/* 动画 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.slide-up-enter-active,
.slide-up-leave-active {
  transition: transform 0.3s ease, opacity 0.3s ease;
}

.slide-up-enter-from {
  transform: translateY(20rpx);
  opacity: 0;
}

.slide-up-leave-to {
  transform: translateY(-20rpx);
  opacity: 0;
}
</style>