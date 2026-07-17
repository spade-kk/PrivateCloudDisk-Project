<template>
  <view class="image-preview-page">
    <!-- 图片预览区域 -->
    <swiper
      class="preview-swiper"
      :current="currentIndex"
      :indicator-dots="images.length > 1"
      indicator-color="rgba(255,255,255,0.3)"
      indicator-active-color="#4F6EF7"
      @change="onSwiperChange"
      circular
      duration="300"
    >
      <swiper-item v-for="(img, idx) in images" :key="idx">
        <view class="image-wrapper">
          <image
            class="preview-image"
            :src="img.url || img"
            mode="aspectFit"
            :show-menu-by-longpress="true"
            @load="onImageLoad(idx)"
            @error="onImageError(idx)"
          />
          <text class="image-index" v-if="images.length > 1">{{ idx + 1 }} / {{ images.length }}</text>
        </view>
      </swiper-item>
    </swiper>

    <!-- 底部操作栏 -->
    <view class="bottom-bar">
      <view class="bar-item" @click="handleDownload">
        <u-icon name="download" size="44" color="#fff" />
        <text class="bar-label">下载</text>
      </view>
      <view class="bar-item" @click="handleShare">
        <u-icon name="share" size="44" color="#fff" />
        <text class="bar-label">分享</text>
      </view>
      <view class="bar-item" @click="handleDelete">
        <u-icon name="trash" size="44" color="#fff" />
        <text class="bar-label">删除</text>
      </view>
      <view class="bar-item" @click="handleMore">
        <u-icon name="more-dot-fill" size="44" color="#fff" />
        <text class="bar-label">更多</text>
      </view>
    </view>

    <!-- 顶部信息栏 -->
    <view class="top-bar" v-if="showTopBar">
      <view class="back-btn" @click="goBack">
        <u-icon name="arrow-left" size="44" color="#fff" />
      </view>
      <text class="top-title">{{ currentImageName }}</text>
    </view>

    <!-- 更多操作弹窗 -->
    <u-action-sheet
      :show="moreSheetShow"
      :actions="moreActions"
      @select="onMoreSelect"
      @close="moreSheetShow = false"
    />
  </view>
</template>

<script>
import { downloadFile } from '@/api/download'
import { moveFileToTrash } from '@/api/trash'
import { useClipboard } from '@/composables/useClipboard'

export default {
  setup() {
    const { copy } = useClipboard()
    return { copy }
  },
  data() {
    return {
      images: [],
      currentIndex: 0,
      showTopBar: true,
      topBarTimer: null,
      moreSheetShow: false,
      fileId: '',
      currentImageName: '',
      moreActions: [
        { name: '复制链接', value: 'copy_link' },
        { name: '保存到相册', value: 'save_album' },
        { name: '设为封面', value: 'set_cover' },
        { name: '详细信息', value: 'info' }
      ]
    }
  },
  onLoad(options) {
    const urls = decodeURIComponent(options.urls || '[]')
    try {
      this.images = JSON.parse(urls)
    } catch (e) {
      this.images = []
    }
    this.currentIndex = Number(options.index || 0)
    this.fileId = options.fileId || ''
    this.currentImageName = decodeURIComponent(options.name || '')
  },
  methods: {
    onSwiperChange(e) {
      this.currentIndex = e.detail.current
    },

    onImageLoad(idx) {
      console.log('[ImagePreview] 图片加载成功:', idx)
    },

    onImageError(idx) {
      console.error('[ImagePreview] 图片加载失败:', idx)
      uni.showToast({ title: '图片加载失败', icon: 'none' })
    },

    handleDownload() {
      if (!this.fileId) {
        uni.showToast({ title: '缺少文件信息', icon: 'none' })
        return
      }
      uni.showLoading({ title: '准备下载...' })
      downloadFile(this.fileId).then(() => {
        uni.hideLoading()
        uni.showToast({ title: '已加入下载队列', icon: 'success' })
      }).catch(() => {
        uni.hideLoading()
      })
    },

    handleShare() {
      uni.showToast({ title: '分享功能开发中', icon: 'none' })
    },

    async handleDelete() {
      const res = await new Promise((resolve) => {
        uni.showModal({
          title: '确认删除',
          content: '删除后可从回收站恢复',
          confirmColor: '#ea4335',
          success: (r) => resolve(r.confirm)
        })
      })
      if (!res) return

      try {
        await moveFileToTrash(this.fileId)
        uni.showToast({ title: '已移入回收站', icon: 'success' })
        setTimeout(() => uni.navigateBack(), 1000)
      } catch (e) {
        // 错误已处理
      }
    },

    handleMore() {
      this.moreSheetShow = true
    },

    onMoreSelect(action) {
      this.moreSheetShow = false
      switch (action.value) {
        case 'copy_link':
          this.copy(this.images[this.currentIndex]?.url || '', '链接已复制')
          break
        case 'save_album':
          this.saveToAlbum()
          break
        case 'set_cover':
          uni.showToast({ title: '功能开发中', icon: 'none' })
          break
        case 'info':
          uni.showToast({ title: `文件ID: ${this.fileId}`, icon: 'none', duration: 3000 })
          break
      }
    },

    saveToAlbum() {
      const url = this.images[this.currentIndex]?.url
      if (!url) return
      uni.showLoading({ title: '保存中...' })
      uni.downloadFile({
        url,
        success(res) {
          uni.saveImageToPhotosAlbum({
            filePath: res.tempFilePath,
            success() {
              uni.hideLoading()
              uni.showToast({ title: '已保存到相册', icon: 'success' })
            },
            fail() {
              uni.hideLoading()
              uni.showToast({ title: '保存失败', icon: 'none' })
            }
          })
        },
        fail() {
          uni.hideLoading()
          uni.showToast({ title: '下载失败', icon: 'none' })
        }
      })
    },

    goBack() {
      uni.navigateBack()
    }
  }
}
</script>

<style lang="scss" scoped>
.image-preview-page {
  position: relative;
  width: 100%;
  height: 100vh;
  background: #000;
}

.preview-swiper {
  width: 100%;
  height: 100%;
}

.image-wrapper {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
}

.preview-image {
  width: 100%;
  height: 100%;
}

.image-index {
  position: absolute;
  bottom: 160rpx;
  left: 50%;
  transform: translateX(-50%);
  background: rgba(0, 0, 0, 0.5);
  color: #fff;
  font-size: 24rpx;
  padding: 8rpx 24rpx;
  border-radius: 20rpx;
}

/* 底部栏 */
.bottom-bar {
  position: absolute;
  bottom: 0; left: 0; right: 0;
  display: flex;
  justify-content: space-around;
  padding: 24rpx 32rpx calc(env(safe-area-inset-bottom) + 24rpx);
  background: linear-gradient(0deg, rgba(0,0,0,0.7), transparent);
  z-index: 30;
}
.bar-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
}
.bar-label {
  font-size: 22rpx; color: #fff;
}

/* 顶部栏 */
.top-bar {
  position: absolute;
  top: 0; left: 0; right: 0;
  display: flex;
  align-items: center;
  padding: calc(env(safe-area-inset-top) + 16rpx) 32rpx 16rpx;
  background: linear-gradient(180deg, rgba(0,0,0,0.6), transparent);
  z-index: 30;
}
.back-btn {
  width: 64rpx; height: 64rpx;
  display: flex; align-items: center; justify-content: center;
}
.top-title {
  flex: 1; margin-left: 16rpx;
  font-size: 30rpx; color: #fff; font-weight: 500;
}
</style>