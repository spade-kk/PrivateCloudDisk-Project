<template>
  <view class="video-player-page" :class="{ 'is-fullscreen': isFullscreen }">
    <!-- 视频播放器 -->
    <view class="player-container">
      <video
        id="video-player"
        class="video-element"
        :src="src"
        :poster="poster"
        :initial-time="initialTime"
        :autoplay="true"
        :controls="false"
        :loop="false"
        :muted="isMuted"
        :show-center-play-btn="false"
        :show-play-btn="false"
        :enable-progress-gesture="false"
        :object-fit="'contain'"
        :play-btn-position="'center'"
        :direction="isFullscreen ? 90 : 0"
        @play="eventHandlers.onPlay"
        @pause="eventHandlers.onPause"
        @ended="eventHandlers.onEnded"
        @timeupdate="eventHandlers.onTimeUpdate"
        @waiting="eventHandlers.onWaiting"
        @canplay="eventHandlers.onCanplay"
        @error="eventHandlers.onError"
        @loadedmetadata="eventHandlers.onLoadedmetadata"
        @fullscreenchange="eventHandlers.onFullscreenChange"
        @progress="eventHandlers.onProgress"
      />

      <!-- 加载中遮罩 -->
      <view class="loading-overlay" v-if="isLoading">
        <view class="loading-spinner">
          <u-loading-icon mode="circle" size="56" color="#fff" />
        </view>
        <text class="loading-text">加载中...</text>
      </view>

      <!-- 缓冲提示 -->
      <view class="buffering-indicator" v-if="isBuffering && !isLoading">
        <u-loading-icon mode="circle" size="32" color="#4F6EF7" />
      </view>

      <!-- 错误提示 -->
      <view class="error-overlay" v-if="hasError" @click="retryPlay">
        <u-icon name="error-circle" size="80" color="#fff" />
        <text class="error-text">{{ errorMessage }}</text>
        <view class="retry-btn">点击重试</view>
      </view>

      <!-- 中央播放按钮 -->
      <view class="center-play-btn" v-if="!isPlaying && !isLoading && !hasError" @click="play">
        <u-icon name="play-circle-fill" size="96" color="rgba(255,255,255,0.9)" />
      </view>
    </view>

    <!-- 顶部控制栏 -->
    <view class="top-control-bar" v-if="showControls">
      <view class="back-btn" @click="goBack">
        <u-icon name="arrow-left" size="44" color="#fff" />
      </view>
      <text class="video-title ellipsis">{{ videoTitle }}</text>
      <view class="more-btn" @click="showMoreMenu">
        <u-icon name="more-dot-fill" size="44" color="#fff" />
      </view>
    </view>

    <!-- 底部控制栏 -->
    <view class="bottom-control-bar" v-if="showControls" @click.stop>
      <!-- 进度条 -->
      <view class="progress-section">
        <view class="progress-bar" @click="onProgressBarClick">
          <view class="progress-track">
            <view class="progress-buffered" :style="{ width: buffered + '%' }" />
            <view class="progress-filled" :style="{ width: progress + '%' }" />
            <view class="progress-thumb" :style="{ left: progress + '%' }" />
          </view>
        </view>
        <view class="time-display">
          <text class="time-text">{{ formattedCurrentTime }}</text>
          <text class="time-separator">/</text>
          <text class="time-text">{{ formattedDuration }}</text>
        </view>
      </view>

      <!-- 操作按钮 -->
      <view class="control-buttons">
        <view class="left-controls">
          <view class="ctrl-btn" @click="togglePlay">
            <u-icon :name="isPlaying ? 'pause' : 'play-right'" size="48" color="#fff" />
          </view>
          <view class="ctrl-btn" @click="rewind(10)">
            <text class="skip-text">-10s</text>
          </view>
          <view class="ctrl-btn" @click="forward(10)">
            <text class="skip-text">+10s</text>
          </view>
          <view class="ctrl-btn mute-btn" @click="toggleMute">
            <u-icon :name="isMuted ? 'volume-off' : 'volume'" size="40" color="#fff" />
          </view>
          <!-- 音量滑块 -->
          <view class="volume-slider" v-if="showVolumeSlider">
            <slider
              :value="volume * 100"
              :min="0"
              :max="100"
              :step="1"
              activeColor="#4F6EF7"
              backgroundColor="rgba(255,255,255,0.3)"
              block-size="16"
              @change="onVolumeChange"
            />
          </view>
        </view>

        <view class="right-controls">
          <!-- 播放速度 -->
          <view class="ctrl-btn rate-btn" @click="cyclePlaybackRate">
            <text class="rate-text">{{ playbackRate }}x</text>
          </view>
          <!-- 画质 -->
          <view class="ctrl-btn quality-btn" v-if="qualities.length > 1" @click="showQualityPicker">
            <text class="quality-text">{{ currentQuality ? currentQuality.label : '自动' }}</text>
          </view>
          <!-- 全屏 -->
          <view class="ctrl-btn" @click="toggleFullscreen">
            <u-icon :name="isFullscreen ? 'arrow-left-double' : 'arrow-right-double'" size="40" color="#fff" />
          </view>
        </view>
      </view>
    </view>

    <!-- 画质选择弹窗 -->
    <u-popup :show="qualityPickerVisible" mode="bottom" round="24" @close="qualityPickerVisible = false">
      <view class="quality-picker">
        <view class="picker-header">
          <text class="picker-title">清晰度</text>
        </view>
        <view
          class="quality-item"
          v-for="(q, idx) in qualities"
          :key="idx"
          :class="{ active: currentQualityIndex === idx }"
          @click="selectQuality(idx)"
        >
          <text class="quality-label">{{ q.label }}</text>
          <u-icon v-if="currentQualityIndex === idx" name="checkmark" size="36" color="#4F6EF7" />
        </view>
      </view>
    </u-popup>

    <!-- 更多菜单弹窗 -->
    <u-popup :show="moreMenuVisible" mode="bottom" round="24" @close="moreMenuVisible = false">
      <view class="more-menu">
        <view class="picker-header">
          <text class="picker-title">更多操作</text>
        </view>
        <view class="menu-item" @click="handleDownload">
          <u-icon name="download" size="40" color="#333" />
          <text class="menu-label">下载视频</text>
        </view>
        <view class="menu-item" @click="handleShare">
          <u-icon name="share" size="40" color="#333" />
          <text class="menu-label">分享</text>
        </view>
        <view class="menu-item" @click="handleReport">
          <u-icon name="info-circle" size="40" color="#333" />
          <text class="menu-label">视频信息</text>
        </view>
      </view>
    </u-popup>
  </view>
</template>

<script>
import { useVideoPlayer } from '@/composables/useVideoPlayer'
import { getFileDetail } from '@/api/node'
import { downloadFile } from '@/api/download'

export default {
  data() {
    return {
      videoTitle: '',
      poster: '',
      initialTime: 0,
      showControls: true,
      controlsTimer: null,
      showVolumeSlider: false,
      qualityPickerVisible: false,
      moreMenuVisible: false,
      fileId: ''
    }
  },
  setup() {
    const player = useVideoPlayer({
      qualities: [
        { label: '自动', url: '' },
        { label: '1080P', url: '' },
        { label: '720P', url: '' },
        { label: '480P', url: '' }
      ]
    })

    // 3秒后自动隐藏控制栏
    const scheduleHideControls = () => {
      if (player.controlsTimer) clearTimeout(player.controlsTimer)
      player.controlsTimer = setTimeout(() => {
        if (player.isPlaying.value) {
          player.showControls = false
        }
      }, 3000)
    }

    return {
      ...player,
      scheduleHideControls
    }
  },
  onLoad(options) {
    this.fileId = options.fileId || ''
    this.videoTitle = decodeURIComponent(options.title || '视频播放')
    this.poster = decodeURIComponent(options.poster || '')
    this.initialTime = Number(options.t || 0)

    this.loadVideoInfo()
  },
  onReady() {
    this.initVideo('video-player')
  },
  onUnload() {
    this.clearRetryTimer()
    if (this.controlsTimer) clearTimeout(this.controlsTimer)
  },
  methods: {
    /**
     * 加载视频信息 (获取播放地址)
     */
    async loadVideoInfo() {
      if (!this.fileId) {
        this.hasError = true
        this.errorMessage = '缺少文件信息'
        return
      }
      try {
        const res = await getFileDetail(this.fileId)
        const file = res.data
        if (file) {
          // 构建视频播放地址 (支持 HLS m3u8)
          this.src = file.stream_url || file.download_url || ''
          this.poster = this.poster || file.thumbnail_url || ''

          // 多码率配置
          if (file.qualities && file.qualities.length > 0) {
            this.qualities = file.qualities
          }
        }
      } catch (e) {
        this.hasError = true
        this.errorMessage = '获取视频信息失败'
      }
    },

    /**
     * 重试播放
     */
    retryPlay() {
      this.hasError = false
      this.errorMessage = ''
      this.isLoading = true
      this.loadVideoInfo()
    },

    /**
     * 进度条点击
     */
    onProgressBarClick(e) {
      const rect = e.target.getBoundingClientRect
        ? e.target
        : e.currentTarget
      // 简化处理：使用触摸位置
      if (e.detail && e.detail.x) {
        const percent = (e.detail.x / 300) * 100 // 估算
        this.seekPercent(percent)
      }
    },

    /**
     * 音量滑块变化
     */
    onVolumeChange(e) {
      this.setVolume(e.detail.value / 100)
    },

    /**
     * 画质选择
     */
    showQualityPicker() {
      this.qualityPickerVisible = true
    },
    selectQuality(idx) {
      this.switchQuality(idx)
      this.qualityPickerVisible = false
    },

    /**
     * 更多菜单
     */
    showMoreMenu() {
      this.moreMenuVisible = true
    },
    handleDownload() {
      this.moreMenuVisible = false
      uni.showLoading({ title: '准备下载...' })
      downloadFile(this.fileId).then(() => {
        uni.hideLoading()
        uni.showToast({ title: '已加入下载队列', icon: 'success' })
      }).catch(() => {
        uni.hideLoading()
      })
    },
    handleShare() {
      this.moreMenuVisible = false
      uni.showToast({ title: '分享功能开发中', icon: 'none' })
    },
    handleReport() {
      this.moreMenuVisible = false
      uni.showToast({ title: `文件ID: ${this.fileId}`, icon: 'none' })
    },

    /**
     * 点击屏幕切换控制栏显示
     */
    toggleControls() {
      this.showControls = !this.showControls
      if (this.showControls) {
        this.scheduleHideControls()
      }
    },

    /**
     * 返回上一页
     */
    goBack() {
      if (this.isFullscreen) {
        this.exitFullscreen()
      } else {
        uni.navigateBack()
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.video-player-page {
  position: relative;
  width: 100%;
  height: 100vh;
  background: #000;
  overflow: hidden;
}

.player-container {
  position: relative;
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.video-element {
  width: 100%;
  height: 100%;
}

/* 加载中遮罩 */
.loading-overlay {
  position: absolute;
  top: 0; left: 0; right: 0; bottom: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.5);
  z-index: 10;
}
.loading-spinner { margin-bottom: 16rpx; }
.loading-text { font-size: 26rpx; color: rgba(255,255,255,0.8); }

/* 缓冲指示器 */
.buffering-indicator {
  position: absolute;
  top: 50%; left: 50%;
  transform: translate(-50%, -50%);
  z-index: 9;
  background: rgba(0,0,0,0.5);
  border-radius: 16rpx;
  padding: 16rpx 24rpx;
}

/* 错误遮罩 */
.error-overlay {
  position: absolute;
  top: 0; left: 0; right: 0; bottom: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.8);
  z-index: 20;
}
.error-text {
  font-size: 28rpx; color: rgba(255,255,255,0.8);
  margin-top: 24rpx; max-width: 80%; text-align: center;
}
.retry-btn {
  margin-top: 32rpx;
  padding: 16rpx 48rpx;
  background: #4F6EF7; color: #fff;
  border-radius: 40rpx; font-size: 28rpx;
}

/* 中央播放按钮 */
.center-play-btn {
  position: absolute;
  top: 50%; left: 50%;
  transform: translate(-50%, -50%);
  z-index: 15;
}

/* 顶部控制栏 */
.top-control-bar {
  position: absolute;
  top: 0; left: 0; right: 0;
  padding: calc(env(safe-area-inset-top) + 16rpx) 32rpx 16rpx;
  display: flex; align-items: center;
  background: linear-gradient(180deg, rgba(0,0,0,0.6), transparent);
  z-index: 30;
}
.back-btn, .more-btn {
  width: 64rpx; height: 64rpx;
  display: flex; align-items: center; justify-content: center;
}
.video-title {
  flex: 1; margin: 0 24rpx;
  font-size: 30rpx; color: #fff; font-weight: 500;
}

/* 底部控制栏 */
.bottom-control-bar {
  position: absolute;
  bottom: 0; left: 0; right: 0;
  padding: 16rpx 32rpx calc(env(safe-area-inset-bottom) + 16rpx);
  background: linear-gradient(0deg, rgba(0,0,0,0.7), transparent);
  z-index: 30;
}

/* 进度条 */
.progress-section { margin-bottom: 16rpx; }
.progress-bar {
  padding: 16rpx 0;
  position: relative;
}
.progress-track {
  width: 100%; height: 6rpx;
  background: rgba(255,255,255,0.3);
  border-radius: 3rpx;
  position: relative;
}
.progress-buffered {
  position: absolute; top: 0; left: 0; height: 100%;
  background: rgba(255,255,255,0.5);
  border-radius: 3rpx;
}
.progress-filled {
  position: absolute; top: 0; left: 0; height: 100%;
  background: #4F6EF7;
  border-radius: 3rpx;
}
.progress-thumb {
  position: absolute; top: 50%; width: 16rpx; height: 16rpx;
  background: #fff; border-radius: 50%;
  transform: translate(-50%, -50%);
  box-shadow: 0 2rpx 8rpx rgba(0,0,0,0.3);
}
.time-display {
  display: flex; align-items: center;
  margin-top: 8rpx;
}
.time-text {
  font-size: 22rpx; color: rgba(255,255,255,0.7);
  font-variant-numeric: tabular-nums;
}
.time-separator {
  margin: 0 8rpx; font-size: 22rpx; color: rgba(255,255,255,0.5);
}

/* 控制按钮 */
.control-buttons {
  display: flex; align-items: center; justify-content: space-between;
}
.left-controls, .right-controls {
  display: flex; align-items: center; gap: 8rpx;
}
.ctrl-btn {
  width: 64rpx; height: 64rpx;
  display: flex; align-items: center; justify-content: center;
}
.skip-text {
  font-size: 22rpx; color: #fff; font-weight: 500;
}
.rate-text {
  font-size: 24rpx; color: #4F6EF7; font-weight: 600;
  background: rgba(255,255,255,0.15); border-radius: 8rpx;
  padding: 4rpx 12rpx;
}
.quality-text {
  font-size: 22rpx; color: #fff;
  background: rgba(255,255,255,0.15); border-radius: 8rpx;
  padding: 4rpx 12rpx;
}
.volume-slider {
  width: 160rpx; margin-left: 8rpx;
}

/* 画质选择 / 更多菜单 */
.quality-picker, .more-menu {
  padding: 32rpx;
  background: #fff;
}
.picker-header {
  text-align: center; margin-bottom: 24rpx;
}
.picker-title {
  font-size: 32rpx; font-weight: 600; color: #333;
}
.quality-item {
  display: flex; align-items: center; justify-content: space-between;
  padding: 28rpx 32rpx;
  border-bottom: 1rpx solid #f0f0f0;
}
.quality-item.active { background: #e8f0fe; }
.quality-label { font-size: 28rpx; color: #333; }
.menu-item {
  display: flex; align-items: center;
  padding: 28rpx 32rpx;
  border-bottom: 1rpx solid #f0f0f0;
}
.menu-label { margin-left: 24rpx; font-size: 28rpx; color: #333; }
</style>