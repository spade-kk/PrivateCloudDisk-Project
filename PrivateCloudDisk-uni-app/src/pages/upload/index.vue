<template>
  <view class="upload-page">
    <!-- 上传入口 -->
    <view class="upload-section">
      <view class="upload-card" @click="handleChooseFile">
        <view class="upload-icon">
          <u-icon name="arrow-up" size="64" color="#4F6EF7" />
        </view>
        <text class="upload-title">选择文件上传</text>
        <text class="upload-desc">支持文档、图片、视频、音频等格式</text>
      </view>
    </view>

    <!-- 上传任务列表 -->
    <view class="task-section" v-if="tasks.length > 0">
      <view class="task-header">
        <text class="task-title">上传任务</text>
        <text class="task-count">{{ completedCount }}/{{ tasks.length }}</text>
      </view>
      <view class="task-list">
        <view class="task-item" v-for="task in tasks" :key="task.id">
          <view class="task-info">
            <text class="task-name ellipsis">{{ task.name }}</text>
            <text class="task-status" :class="'status-' + task.status">
              {{ statusText(task.status) }}
            </text>
          </view>
          <u-line-progress
            v-if="task.status !== 'done' && task.status !== 'failed'"
            :percentage="task.progress"
            :activeColor="task.status === 'failed' ? '#ea4335' : '#4F6EF7'"
            height="6"
            :showText="false"
          />
          <view class="task-progress-text" v-if="task.currentStage">
            <text class="progress-stage">{{ task.currentStage }}</text>
          </view>
        </view>
      </view>
    </view>

    <EmptyState
      v-else
      icon="cloud-upload"
      text="暂无上传任务"
      subText="点击上方选择文件开始上传"
    />
  </view>
</template>

<script>
import { getToken } from '@/utils/storage'
import { FILE_BASE_URL } from '@/utils/const'
import EmptyState from '@/components/file/EmptyState.vue'

let taskIdCounter = 0

export default {
  components: { EmptyState },
  data() {
    return {
      tasks: [],
      chunkSize: 2 * 1024 * 1024
    }
  },
  computed: {
    completedCount() {
      return this.tasks.filter(t => t.status === 'done').length
    }
  },
  methods: {
    handleChooseFile() {
      uni.chooseFile({
        count: 1,
        type: 'all',
        success: (res) => {
          const file = res.tempFiles[0]
          this.addTask(file.name, file.path, file.size || 0)
        },
        fail: () => {
          uni.showToast({ title: '文件选择失败', icon: 'none' })
        }
      })
    },

    addTask(name, filePath, fileSize) {
      const id = ++taskIdCounter
      this.tasks.unshift({
        id, name, filePath, fileSize,
        progress: 0,
        status: 'pending',
        currentStage: '准备上传...'
      })
      this.startUpload(id, filePath, name, fileSize)
    },

    async startUpload(id, filePath, fileName, fileSize) {
      const task = this.tasks.find(t => t.id === id)
      if (!task) return

      task.status = 'uploading'
      task.currentStage = '创建上传会话...'

      try {
        const totalChunks = Math.ceil(fileSize / this.chunkSize)
        const checksum = await this.calculateChecksum(fileSize)

        const sessionData = {
          total_chunks: totalChunks,
          file_size: fileSize,
          file_checksum: checksum,
          chunks_max_size: this.chunkSize,
          file_type: this.getFileType(fileName),
          file_name: fileName,
          node_id: 'root'
        }

        const sessionRes = await this.createUploadSession(sessionData)
        const uploadsId = sessionRes.data.uploads_id

        task.currentStage = '上传分片...'

        await this.uploadChunks(id, filePath, uploadsId, totalChunks, fileSize)

        task.currentStage = '合并分片...'

        const mergeRes = await this.mergeChunks(uploadsId)
        const backendTaskId = mergeRes.data.backend_task_id

        task.currentStage = '处理中...'

        await this.pollTaskStatus(id, backendTaskId)

        task.status = 'done'
        task.progress = 100
        task.currentStage = '上传完成'
        uni.showToast({ title: '上传成功', icon: 'success' })

      } catch (e) {
        console.error('[Upload] 上传失败:', e)
        task.status = 'failed'
        task.currentStage = '上传失败'
        uni.showToast({ title: e.message || '上传失败', icon: 'none' })
      }
    },

    getFileType(fileName) {
      const ext = fileName.split('.').pop().toLowerCase()
      const types = {
        'jpg': 'image/jpeg', 'jpeg': 'image/jpeg', 'png': 'image/png',
        'gif': 'image/gif', 'bmp': 'image/bmp', 'webp': 'image/webp',
        'mp4': 'video/mp4', 'avi': 'video/x-msvideo', 'mov': 'video/quicktime',
        'wmv': 'video/x-ms-wmv', 'flv': 'video/x-flv', 'mkv': 'video/x-matroska',
        'mp3': 'audio/mpeg', 'wav': 'audio/wav', 'ogg': 'audio/ogg',
        'pdf': 'application/pdf', 'doc': 'application/msword',
        'docx': 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
        'xls': 'application/vnd.ms-excel',
        'xlsx': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        'ppt': 'application/vnd.ms-powerpoint',
        'pptx': 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
        'txt': 'text/plain', 'json': 'application/json',
        'zip': 'application/zip', 'rar': 'application/x-rar-compressed',
        '7z': 'application/x-7z-compressed'
      }
      return types[ext] || 'application/octet-stream'
    },

    async calculateChecksum(fileSize) {
      return new Promise((resolve) => {
        const array = new Uint8Array(32)
        crypto.getRandomValues(array)
        resolve(Array.from(array).map(b => b.toString(16).padStart(2, '0')).join(''))
      })
    },

    createUploadSession(data) {
      return new Promise((resolve, reject) => {
        const token = getToken()
        uni.request({
          url: `${FILE_BASE_URL}/business/uploads/`,
          method: 'POST',
          data,
          header: {
            'Content-Type': 'application/json',
            ...(token ? { Authorization: `Bearer ${token}` } : {})
          },
          success: (res) => {
            try {
              const body = typeof res.data === 'string' ? JSON.parse(res.data) : res.data
              if (body.code === 200) resolve(body)
              else reject(new Error(body.message || '创建会话失败'))
            } catch (e) {
              reject(new Error('响应解析失败'))
            }
          },
          fail: reject
        })
      })
    },

    async uploadChunks(taskId, filePath, uploadsId, totalChunks, fileSize) {
      for (let i = 0; i < totalChunks; i++) {
        const task = this.tasks.find(t => t.id === taskId)
        if (!task) return

        await this.uploadChunk(taskId, filePath, uploadsId, i, totalChunks, fileSize)
      }
    },

    uploadChunk(taskId, filePath, uploadsId, chunkIndex, totalChunks, fileSize) {
      return new Promise((resolve, reject) => {
        const token = getToken()
        const uploadTask = uni.uploadFile({
          url: `${FILE_BASE_URL}/files/uploads/${uploadsId}/chunks`,
          filePath,
          name: 'file',
          formData: { chunk_index: chunkIndex },
          header: {
            ...(token ? { Authorization: `Bearer ${token}` } : {})
          },
          success: (res) => {
            try {
              const body = JSON.parse(res.data)
              if (body.code === 200) {
                const task = this.tasks.find(t => t.id === taskId)
                if (task) {
                  const progress = Math.round(((chunkIndex + 1) / totalChunks) * 90)
                  task.progress = progress
                }
                resolve(body)
              } else {
                reject(new Error(body.message || '分片上传失败'))
              }
            } catch (e) {
              reject(new Error('响应解析失败'))
            }
          },
          fail: reject
        })

        if (uploadTask) {
          uploadTask.onProgressUpdate((res) => {
            const task = this.tasks.find(t => t.id === taskId)
            if (task) {
              const baseProgress = (chunkIndex / totalChunks) * 90
              const chunkProgress = (res.progress / 100) * (90 / totalChunks)
              task.progress = Math.min(90, Math.round(baseProgress + chunkProgress))
            }
          })
        }
      })
    },

    mergeChunks(uploadsId) {
      return new Promise((resolve, reject) => {
        const token = getToken()
        uni.request({
          url: `${FILE_BASE_URL}/files/uploads/${uploadsId}/merge`,
          method: 'POST',
          header: {
            ...(token ? { Authorization: `Bearer ${token}` } : {})
          },
          success: (res) => {
            try {
              const body = typeof res.data === 'string' ? JSON.parse(res.data) : res.data
              if (body.code === 200) resolve(body)
              else reject(new Error(body.message || '合并失败'))
            } catch (e) {
              reject(new Error('响应解析失败'))
            }
          },
          fail: reject
        })
      })
    },

    async pollTaskStatus(taskId, backendTaskId) {
      const maxAttempts = 30
      for (let i = 0; i < maxAttempts; i++) {
        const task = this.tasks.find(t => t.id === taskId)
        if (!task) return

        try {
          const res = await this.getTaskStatus(backendTaskId)
          const data = res.data

          if (data.status === 'completed') {
            task.currentStage = '处理完成'
            return
          } else if (data.status === 'failed') {
            throw new Error(data.message || '文件处理失败')
          }

          task.currentStage = data.current_stage || '处理中...'

        } catch (e) {
          throw e
        }

        await new Promise(r => setTimeout(r, 2000))
      }

      throw new Error('任务超时')
    },

    getTaskStatus(backendTaskId) {
      return new Promise((resolve, reject) => {
        const token = getToken()
        uni.request({
          url: `${FILE_BASE_URL}/files/tasks/${backendTaskId}`,
          method: 'GET',
          header: {
            ...(token ? { Authorization: `Bearer ${token}` } : {})
          },
          success: (res) => {
            try {
              const body = typeof res.data === 'string' ? JSON.parse(res.data) : res.data
              if (body.code === 200) resolve(body)
              else reject(new Error(body.message || '查询任务状态失败'))
            } catch (e) {
              reject(new Error('响应解析失败'))
            }
          },
          fail: reject
        })
      })
    },

    statusText(status) {
      const map = {
        pending: '等待中',
        uploading: '上传中',
        done: '已完成',
        failed: '失败'
      }
      return map[status] || status
    }
  }
}
</script>

<style lang="scss" scoped>
.upload-page { min-height: 100vh; background: #f5f5f5; padding-bottom: 32rpx; }

.upload-section { padding: 32rpx 24rpx; }
.upload-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 64rpx 32rpx;
  background: #fff;
  border-radius: 16rpx;
  border: 2rpx dashed #c4c7cc;
  box-shadow: 0 2rpx 12rpx rgba(0,0,0,0.06);
  &:active { background: #f5f8ff; border-color: #4F6EF7; }
}
.upload-icon { margin-bottom: 16rpx; }
.upload-title { font-size: 32rpx; color: #202124; font-weight: 500; }
.upload-desc { font-size: 24rpx; color: #9aa0a6; margin-top: 8rpx; }

.task-section { margin: 0 24rpx; }
.task-header { display: flex; justify-content: space-between; padding: 16rpx 0; }
.task-title { font-size: 28rpx; color: #202124; font-weight: 500; }
.task-count { font-size: 24rpx; color: #9aa0a6; }
.task-list { background: #fff; border-radius: 16rpx; overflow: hidden; box-shadow: 0 2rpx 12rpx rgba(0,0,0,0.06); }
.task-item { padding: 24rpx 32rpx; border-bottom: 1rpx solid #f0f0f0; &:last-child { border-bottom: none; } }
.task-info { display: flex; justify-content: space-between; margin-bottom: 12rpx; }
.task-name { font-size: 28rpx; color: #202124; flex: 1; min-width: 0; }
.task-status { font-size: 24rpx; margin-left: 16rpx; }
.status-pending { color: #9aa0a6; }
.status-uploading { color: #4F6EF7; }
.status-done { color: #34a853; }
.status-failed { color: #ea4335; }

.task-progress-text { margin-top: 8rpx; }
.progress-stage { font-size: 22rpx; color: #9aa0a6; }
</style>