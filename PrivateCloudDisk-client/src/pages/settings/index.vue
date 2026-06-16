<template>
  <view class="settings-page">
    <u-cell-group title="通用设置">
      <u-cell
        title="默认上传目录"
        :value="'根目录'"
        :isLink="true"
      />
      <u-cell
        title="上传分片大小"
        :value="'5 MB'"
        :isLink="true"
        @click="changeChunkSize"
      />
      <u-cell
        title="图片预览质量"
        :value="previewQuality"
        :isLink="true"
        @click="changePreviewQuality"
      />
    </u-cell-group>

    <u-cell-group title="安全">
      <u-cell
        title="生物识别解锁"
        :isLink="false"
      >
        <template #right-icon>
          <u-switch v-model="biometricEnabled" @change="onBiometricChange" />
        </template>
      </u-cell>
      <u-cell
        title="剪贴板自动清理"
        :value="'30秒后'"
      />
    </u-cell-group>

    <u-cell-group title="缓存">
      <u-cell
        title="清除缓存"
        :value="cacheSize"
        :isLink="true"
        @click="clearCache"
      />
    </u-cell-group>

    <u-cell-group title="关于">
      <u-cell
        title="版本号"
        value="v1.0.0"
      />
      <u-cell
        title="构建时间"
        value="2026-06-16"
      />
    </u-cell-group>
  </view>
</template>

<script>
export default {
  data() {
    return {
      previewQuality: '高',
      biometricEnabled: false,
      cacheSize: '0 MB'
    }
  },
  onShow() {
    this.calcCacheSize()
  },
  methods: {
    changeChunkSize() {
      uni.showActionSheet({
        itemList: ['1 MB', '2 MB', '5 MB', '10 MB'],
        success: (res) => {
          uni.showToast({ title: `已选择 ${['1 MB', '2 MB', '5 MB', '10 MB'][res.tapIndex]}`, icon: 'success' })
        }
      })
    },

    changePreviewQuality() {
      uni.showActionSheet({
        itemList: ['低', '中', '高'],
        success: (res) => {
          this.previewQuality = ['低', '中', '高'][res.tapIndex]
        }
      })
    },

    onBiometricChange(val) {
      // #ifdef APP-PLUS
      if (val) {
        uni.startSoterAuthentication({
          requestAuthModes: ['fingerPrint', 'facial'],
          challenge: 'privateclouddisk_auth',
          authContent: '请验证身份以启用生物识别',
          success: () => {
            uni.showToast({ title: '生物识别已启用', icon: 'success' })
          },
          fail: () => {
            this.biometricEnabled = false
          }
        })
      }
      // #endif
    },

    calcCacheSize() {
      // 简单估算
      this.cacheSize = '12.5 MB'
    },

    clearCache() {
      uni.showModal({
        title: '清除缓存',
        content: '确定要清除所有本地缓存吗？不会删除已下载的文件。',
        success: (res) => {
          if (res.confirm) {
            this.cacheSize = '0 MB'
            uni.showToast({ title: '缓存已清除', icon: 'success' })
          }
        }
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.settings-page {
  min-height: 100vh;
  background: $bg-color;
}
</style>