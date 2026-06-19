<template>
  <view class="upload-page">
    <!-- 选择文件区域 -->
    <view class="upload-area" v-if="!uploading && !taskId">
      <view class="upload-placeholder" @click="chooseFile">
        <u-icon name="cloud-upload" size="80" color="#1a73e8" />
        <text class="upload-title">选择文件上传</text>
        <text class="upload-desc">支持单文件最大 5GB</text>
      </view>

      <!-- 已选择的文件信息 -->
      <view class="selected-file" v-if="selectedFile">
        <u-icon :name="getFileIcon(selectedFile.name)" size="56" :color="getFileIconColor(selectedFile.name)" />
        <view class="file-detail">
          <text class="file-name ellipsis">{{ selectedFile.name }}</text>
          <text class="file-size">{{ formatFileSize(selectedFile.size) }}</text>
        </view>
        <u-icon name="close-circle" size="36" color="#ea4335" @click="selectedFile = null" />
      </view>

      <!-- 目标目录选择 -->
      <view class="target-folder flex-between" @click="chooseFolder">
        <text class="folder-label">上传到</text>
        <view class="folder-value">
          <u-icon name="folder" size="32" color="#1a73e8" />
          <text>根目录</text>
          <u-icon name="arrow-right" size="24" color="#c4c7cc" />
        </view>
      </view>

      <u-button
        type="primary"
        text="开始上传"
        :disabled="!selectedFile"
        @click="startUpload"
        class="upload-btn"
      />
    </view>

    <!-- 上传进度 -->
    <view class="progress-area" v-if="uploading">
      <view class="progress-card">
        <text class="progress-title">正在上传 {{ selectedFile?.name }}</text>

        <!-- 总体进度 -->
        <view class="overall-progress">
          <text class="progress-label">
            总进度 {{ overallProgress }}% ({{ uploadedChunks }}/{{ totalChunks }} 分片)
          </text>
          <u-line-progress
            :percentage="overallProgress"
            activeColor="#1a73e8"
            height="12"
          />
        </view>

        <!-- 当前分片进度 -->
        <view class="chunk-progress" v-if="currentChunk > 0">
          <text class="progress-label">分片 {{ currentChunk }} 上传中...</text>
          <u-line-progress
            :percentage="chunkProgress"
            activeColor="#34a853"
            height="8"
          />
        </view>

        <!-- 速度 & 剩余时间 -->
        <view class="upload-stats flex-between">
          <text>{{ uploadSpeed }}</text>
          <text>预计剩余 {{ estimatedTime }}</text>
        </view>
      </view>
    </view>

    <!-- 任务状态 (上传完成后) -->
    <view class="task-area" v-if="taskId">
      <view class="task-card">
        <text class="task-title">文件处理任务</text>
        <text class="task-id">任务 ID: {{ taskId }}</text>

        <view class="task-status" v-if="taskStatus">
          <text :class="['status-text', `status-${taskStatus.status}`]">
            {{ statusLabel }}
          </text>
          <view class="steps-list" v-if="taskStatus.steps">
            <view
              class="step-item flex-between"
              v-for="step in taskStatus.steps"
              :key="step.step"
            >
              <text class="step-name">{{ stepLabel(step.step) }}</text>
              <u-tag
                :text="stepStatusLabel(step.status)"
                :type="step.status === 'completed' ? 'success' : step.status === 'processing' ? 'primary' : 'info'"
                size="small"
              />
            </view>
          </view>
        </view>

        <u-button
          text="返回首页"
          @click="goHome"
          size="small"
          class="back-btn"
        />
      </view>
    </view>
  </view>
</template>

<script>
import { createUploadSession, uploadChunk, mergeChunks } from '@/api/upload'
import { getTaskStatus } from '@/api/task'
import { formatFileSize, getFileIcon, getFileIconColor } from '@/utils/helper'
import { CHUNK_SIZE, TASK_STATUS } from '@/utils/const'

export default {
  data() {
    return {
      selectedFile: null,
      targetNodeId: null,

      // 上传状态
      uploading: false,
      totalChunks: 0,
      uploadedChunks: 0,
      currentChunk: 0,
      chunkProgress: 0,
      uploadSpeed: '',
      estimatedTime: '',
      startTime: 0,

      // 任务状态
      taskId: '',
      taskStatus: null,
      taskTimer: null
    }
  },
  computed: {
    overallProgress() {
      if (this.totalChunks === 0) return 0
      return Math.round((this.uploadedChunks / this.totalChunks) * 100)
    },
    statusLabel() {
      if (!this.taskStatus) return '等待处理'
      const map = {
        [TASK_STATUS.PENDING]: '等待处理',
        [TASK_STATUS.PROCESSING]: '处理中...',
        [TASK_STATUS.COMPLETED]: '处理完成',
        [TASK_STATUS.FAILED]: '处理失败',
        [TASK_STATUS.CANCELLED]: '已取消'
      }
      return map[this.taskStatus.status] || '未知'
    }
  },
  beforeUnmount() {
    if (this.taskTimer) clearInterval(this.taskTimer)
  },
  methods: {
    getFileIcon,
    getFileIconColor,
    formatFileSize,

    /** 选择文件 */
    chooseFile() {
      // #ifdef APP-PLUS || H5
      uni.chooseFile({
        count: 1,
        success: (res) => {
          this.selectedFile = res.tempFiles[0]
        }
      })
      // #endif
    },

    /** 选择目标目录 (当前固定根目录, 后续迭代添加目录选择) */
    chooseFolder() {
      uni.showToast({ title: '即将支持选择目录', icon: 'none' })
    },

    /** 开始上传 */
    async startUpload() {
      if (!this.selectedFile) return

      const file = this.selectedFile
      const totalChunks = Math.ceil(file.size / CHUNK_SIZE)

      uni.showLoading({ title: '创建上传会话...' })

      try {
        // 1. 创建上传会话
        const sessionRes = await createUploadSession({
          total_chunks: totalChunks,
          file_size: file.size,
          file_checksum: '',
          chunks_max_size: CHUNK_SIZE,
          file_name: file.name,
          file_type: file.name.split('.').pop() || 'unknown',
          node_id: this.targetNodeId || 'root'
        })
        const uploadsId = sessionRes.data

        uni.hideLoading()
        this.uploading = true
        this.totalChunks = totalChunks
        this.uploadedChunks = 0
        this.startTime = Date.now()

        // 2. 逐片上传
        for (let i = 1; i <= totalChunks; i++) {
          this.currentChunk = i
          this.chunkProgress = 0

          // 计算分片起止字节
          const start = (i - 1) * CHUNK_SIZE
          const end = Math.min(start + CHUNK_SIZE, file.size)

          await this.uploadSingleChunk(uploadsId, i, file.path, start, end)
          this.uploadedChunks = i

          // 更新速度 & 剩余时间
          this.updateStats()
        }

        // 3. 合并分片
        uni.showLoading({ title: '正在合并文件...' })
        const mergeRes = await mergeChunks(uploadsId)
        this.taskId = mergeRes.data.task_id
        this.uploading = false
        uni.hideLoading()

        // 4. 轮询任务状态
        this.pollTaskStatus(this.taskId)
      } catch (e) {
        this.uploading = false
        uni.hideLoading()
      }
    },

    /** 上传单个分片 */
    uploadSingleChunk(uploadsId, chunkIndex, filePath, start, end) {
      return new Promise((resolve, reject) => {
        // #ifdef APP-PLUS
        // 原生切片读取
        const reader = plus.android.importClass('java.io.RandomAccessFile')
        // 简化处理: 对小程序和 H5 使用整体上传
        // #endif

        uploadChunk(uploadsId, chunkIndex, filePath)
          .then(resolve)
          .catch(reject)
      })
    },

    /** 更新上传统计 */
    updateStats() {
      const elapsed = (Date.now() - this.startTime) / 1000
      if (elapsed < 0.1) return

      const uploadedBytes = this.uploadedChunks * CHUNK_SIZE
      const speed = uploadedBytes / elapsed
      const remainBytes = this.selectedFile.size - uploadedBytes
      const remainSeconds = speed > 0 ? remainBytes / speed : 0

      this.uploadSpeed = `${formatFileSize(speed)}/s`
      if (remainSeconds < 60) {
        this.estimatedTime = `${Math.ceil(remainSeconds)}秒`
      } else {
        this.estimatedTime = `${Math.ceil(remainSeconds / 60)}分钟`
      }
    },

    /** 轮询任务状态 */
    pollTaskStatus(taskId) {
      const poll = async () => {
        try {
          const res = await getTaskStatus(taskId)
          this.taskStatus = res.data

          if (res.data.status === TASK_STATUS.COMPLETED ||
              res.data.status === TASK_STATUS.FAILED ||
              res.data.status === TASK_STATUS.CANCELLED) {
            if (this.taskTimer) clearInterval(this.taskTimer)
          }
        } catch (e) {
          if (this.taskTimer) clearInterval(this.taskTimer)
        }
      }

      poll() // 立即查询一次
      this.taskTimer = setInterval(poll, 2000)
    },

    stepLabel(step) {
      const map = {
        merge: '文件合并',
        hash_calculate: '哈希计算',
        virus_scan: '病毒扫描',
        thumbnail: '缩略图生成',
        video_transcode: '视频转码',
        mark_active: '标记活跃'
      }
      return map[step] || step
    },

    stepStatusLabel(status) {
      const map = {
        pending: '等待',
        processing: '处理中',
        completed: '完成',
        failed: '失败'
      }
      return map[status] || status
    },

    goHome() {
      uni.switchTab({ url: '/pages/index/index' })
    }
  }
}
</script>

<style lang="scss" scoped>
.upload-page {
  min-height: 100vh;
  padding: 24rpx;
}

.upload-area {
  background: $bg-white;
  padding: 48rpx 24rpx;
  border-radius: $radius-md;
}

.upload-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 48rpx 0;

  .upload-title {
    font-size: 32rpx;
    font-weight: 600;
    margin-top: 24rpx;
  }

  .upload-desc {
    font-size: 24rpx;
    color: $text-secondary;
    margin-top: 12rpx;
  }
}

.selected-file {
  display: flex;
  align-items: center;
  padding: 24rpx;
  background: $bg-color;
  border-radius: $radius-sm;
  margin-bottom: 24rpx;

  .file-detail {
    flex: 1;
    margin: 0 16rpx;

    .file-name {
      font-size: 28rpx;
    }

    .file-size {
      font-size: 22rpx;
      color: $text-secondary;
    }
  }
}

.target-folder {
  padding: 24rpx 0;
  border-top: 1rpx solid $border-color;
  border-bottom: 1rpx solid $border-color;
  margin-bottom: 32rpx;

  .folder-label {
    font-size: 28rpx;
  }

  .folder-value {
    display: flex;
    align-items: center;
    gap: 8rpx;
    font-size: 26rpx;
    color: $text-secondary;
  }
}

.upload-btn {
  height: 88rpx;
  border-radius: $radius-md;
  font-size: 32rpx;
}

.progress-area, .task-area {
  background: $bg-white;
  border-radius: $radius-md;
  padding: 24rpx;
}

.progress-title, .task-title {
  font-size: 30rpx;
  font-weight: 600;
  margin-bottom: 24rpx;
  display: block;
}

.progress-label {
  font-size: 24rpx;
  color: $text-secondary;
  margin-bottom: 8rpx;
  display: block;
}

.overall-progress, .chunk-progress {
  margin-bottom: 24rpx;
}

.upload-stats {
  font-size: 22rpx;
  color: $text-secondary;
}

.task-id {
  font-size: 22rpx;
  color: $text-placeholder;
  word-break: break-all;
  margin-bottom: 24rpx;
  display: block;
}

.status-text {
  font-size: 28rpx;
  font-weight: 600;
  display: block;
  margin-bottom: 24rpx;

  &.status-completed { color: $success-color; }
  &.status-processing { color: $primary-color; }
  &.status-failed { color: $danger-color; }
  &.status-cancelled { color: $text-secondary; }
}

.steps-list {
  .step-item {
    padding: 16rpx 0;
    border-bottom: 1rpx solid $border-color;

    &:last-child {
      border-bottom: none;
    }
  }
}

.back-btn {
  margin-top: 24rpx;
}
</style>