<script>
/**
 * App.vue - 应用根组件
 * 全局生命周期管理: 初始化 token、路由守卫等
 */
import { useUserStore } from '@/store/user'

export default {
  onLaunch() {
    console.log('[App] 应用启动, 平台:', uni.getSystemInfoSync().platform)
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
}

/* 安全区域适配 */
.safe-area-bottom {
  padding-bottom: constant(safe-area-inset-bottom);
  padding-bottom: env(safe-area-inset-bottom);
}

/* 单行省略 */
.ellipsis {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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
</style>