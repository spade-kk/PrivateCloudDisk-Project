<template>
  <view class="detail-page">
    <!-- 文件基本信息 -->
    <view class="file-header">
      <u-icon
        :name="fileIcon"
        size="80"
        :color="fileIconColor"
      />
      <text class="file-name">{{ fileName }}</text>
      <text class="file-size" v-if="fileInfo">{{ formatFileSize(fileInfo.size) }}</text>
    </view>

    <!-- 操作按钮 -->
    <view class="action-section">
      <u-button
        type="primary"
        text="下载文件"
        icon="download"
        :loading="downloading"
        @click="handleDownload"
      />
      <u-button
        v-if="isPreviewable"
        text="预览"
        icon="eye"
        class="preview-btn"
        @click="handlePreview"
      />
    </view>

    <!-- 缩略图预览 (图片文件) -->
    <view class="thumbnail-section" v-if="thumbnailPath">
      <image
        :src="thumbnailPath"
        mode="widthFix"
        class="thumbnail-img"
      />
    </view>

    <!-- 下载进度 -->
    <view class="progress-section" v-if="downloadProgress > 0 && downloadProgress < 100">
      <text class="progress-text">下载中 {{ downloadProgress }}%</text>
      <u-line-progress
        :percentage="downloadProgress"
        activeColor="#1a73e8"
      />
    </view>

    <!-- 文件详细信息 -->
    <view class="detail-section" v-if="fileInfo">
      <u-cell-group>
        <u-cell title="文件名" :value="fileInfo.name" />
        <u-cell title="文件大小" :value="formatFileSize(fileInfo.size)" />
        <u-cell title="文件类型" :value="fileInfo.type || '未知'" />
        <u-cell title="上传时间" :value="formatTime(fileInfo.uploaded_time)" />
        <u-cell title="文件 ID" :value="fileInfo.id" />
      </u-cell-group>
    </view>

    <u-toast ref="toastRef" />
  </view>
</template>

<script>
import { getFileDetail } from '@/api/file'
import { requestOperationToken, downloadFile, getThumbnail } from '@/api/download'
import { formatFileSize, getFileIcon, getFileIconColor } from '@/utils/helper'
import { formatTime } from '@/utils/helper'
import { OPERATION_TYPE } from '@/utils/const'

const IMAGE_EXTS = ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp']

export default {
  data() {
    return {
      fileId: '',
      fileName: '',
      fileInfo: null,
      downloading: false,
      downloadProgress: 0,
      thumbnailPath: '',
      operationToken: ''
    }
  },
  computed: {
    fileIcon() { return getFileIcon(this.fileName) },
    fileIconColor() { return getFileIconColor(this.fileName) },
    isPreviewable() {
      if (!this.fileName) return false
      const ext = this.fileName.split('.').pop()?.toLowerCase()
      return IMAGE_EXTS.includes(ext)
    }
  },
  onLoad(options) {
    this.fileId = options.fileId || ''
    this.fileName = options.fileName || ''
    this.loadFileInfo()
  },
  methods: {
    formatFileSize,
    formatTime,

    async loadFileInfo() {
      if (!this.fileId) return
      try {
        const res = await getFileDetail(this.fileId)
        this.fileInfo = res.data
        this.fileName = res.data.name || this.fileName
      } catch (e) {}
    },

    /** 申请操作凭证并下载 */
    async handleDownload() {
      uni.showLoading({ title: '申请下载凭证...' })
      try {
        // 1. 申请操作凭证
        const tokenRes = await requestOperationToken({
          file_id: this.fileId,
          operation_type: OPERATION_TYPE.DOWNLOAD
        })
        const opToken = tokenRes.data.operation_token

        uni.hideLoading()
        uni.showToast({ title: '开始下载', icon: 'none' })

        // 2. 下载文件
        this.downloading = true
        this.downloadProgress = 0

        const tempPath = await downloadFile(this.fileId, opToken, (received, total) => {
          if (total > 0) {
            this.downloadProgress = Math.round((received / total) * 100)
          }
        })

        this.downloading = false
        uni.hideLoading()

        // 3. 保存到相册或打开文件
        if (this.isPreviewable) {
          this.saveToGallery(tempPath)
        } else {
          this.openFile(tempPath)
        }
      } catch (e) {
        this.downloading = false
        uni.hideLoading()
      }
    },

    /** 保存图片到相册 */
    saveToGallery(tempPath) {
      // #ifdef APP-PLUS || H5
      uni.saveImageToPhotosAlbum({
        filePath: tempPath,
        success: () => {
          uni.showToast({ title: '已保存到相册', icon: 'success' })
        }
      })
      // #endif
      // #ifdef MP-WEIXIN
      uni.saveImageToPhotosAlbum({
        filePath: tempPath,
        success: () => {
          uni.showToast({ title: '已保存到相册', icon: 'success' })
        }
      })
      // #endif
    },

    /** 打开文件 (APP 端) */
    openFile(tempPath) {
      // #ifdef APP-PLUS
      plus.runtime.openFile(tempPath, {
        showTitle: true
      })
      // #endif
      uni.showToast({ title: '下载完成', icon: 'success' })
    },

    /** 预览图片 */
    async handlePreview() {
      try {
        // 申请预览凭证
        const tokenRes = await requestOperationToken({
          file_id: this.fileId,
          operation_type: OPERATION_TYPE.PREVIEW
        })
        const opToken = tokenRes.data.operation_token

        // 获取缩略图用于即时展示
        uni.showLoading({ title: '加载预览...' })
        const thumb = await getThumbnail(this.fileId, opToken, 1024, 1024)
        this.thumbnailPath = thumb
        uni.hideLoading()

        // 预览大图
        uni.previewImage({
          urls: [thumb],
          current: 0
        })
      } catch (e) {
        uni.showToast({ title: '预览失败', icon: 'none' })
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.detail-page {
  min-height: 100vh;
  padding: 24rpx;
  background: $bg-color;
}

.file-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  background: $bg-white;
  padding: 48rpx 24rpx;
  border-radius: $radius-md;
  margin-bottom: 24rpx;

  .file-name {
    font-size: 32rpx;
    font-weight: 600;
    margin-top: 20rpx;
    text-align: center;
    word-break: break-all;
  }

  .file-size {
    font-size: 24rpx;
    color: $text-secondary;
    margin-top: 8rpx;
  }
}

.action-section {
  display: flex;
  gap: 20rpx;
  margin-bottom: 24rpx;

  .u-button {
    flex: 1;
  }

  .preview-btn {
    background: $bg-white;
    color: $primary-color;
    border: 2rpx solid $primary-color;
  }
}

.thumbnail-section {
  background: $bg-white;
  padding: 24rpx;
  border-radius: $radius-md;
  margin-bottom: 24rpx;
  overflow: hidden;

  .thumbnail-img {
    width: 100%;
    border-radius: $radius-sm;
  }
}

.progress-section {
  background: $bg-white;
  padding: 24rpx;
  border-radius: $radius-md;
  margin-bottom: 24rpx;

  .progress-text {
    font-size: 26rpx;
    color: $primary-color;
    margin-bottom: 12rpx;
    display: block;
  }
}

.detail-section {
  background: $bg-white;
  border-radius: $radius-md;
  overflow: hidden;
}
</style>