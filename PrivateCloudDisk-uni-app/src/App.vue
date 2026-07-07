<script>
/**
 * App.vue - 应用根组件
 * 全局生命周期管理: 初始化 token、路由守卫、平台适配
 *
 * 专注多平台小程序开发，支持微信/支付宝/百度/字节小程序
 */
import { useUserStore } from '@/store/user'

export default {
  onLaunch() {
    const sysInfo = uni.getSystemInfoSync()
    console.log('[App] 应用启动, 平台:', sysInfo.platform, '版本:', sysInfo.SDKVersion)
    this.initApp()
  },
  onShow() {
    console.log('[App] 应用显示')
  },
  onHide() {
    console.log('[App] 应用隐藏')
  },
  methods: {
    initApp() {
      // 重新加载持久化的 token
      const userStore = useUserStore()
      userStore.restoreSession()
    }
  }
}
</script>

<style lang="scss">
/* 全局样式 */
@import 'uview-plus/index.scss';

page {
  background-color: $bg-color;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC',
    'Hiragino Sans GB', 'Microsoft YaHei', 'Helvetica Neue', Helvetica, Arial,
    sans-serif;
  font-size: 28rpx;
  color: $text-primary;
  line-height: 1.6;
  -webkit-font-smoothing: antialiased;
}

/* ========== 安全区域适配 ========== */
.safe-area-bottom {
  padding-bottom: constant(safe-area-inset-bottom);
  padding-bottom: env(safe-area-inset-bottom);
}

/* ========== 文本省略 ========== */
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

/* ========== 弹性布局 ========== */
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

/* ========== 按钮样式覆盖 ========== */
.u-button--primary {
  background-color: #1a73e8 !important;
  border-color: #1a73e8 !important;
}

/* ========== TabBar 图标修正 ========== */
/* 确保 tabBar 图标在小程序中正常显示尺寸 */
</style>