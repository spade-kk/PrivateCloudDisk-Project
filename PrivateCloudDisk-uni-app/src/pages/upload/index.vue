<template>
  <view class="upload-page">
    <!-- 上传入口 -->
    <view class="upload-section">
      <view class="upload-card" @click="handleChooseFile">
        <view class="upload-icon">
          <u-icon name="cloud-upload" size="64" color="#1a73e8" />
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
            :percentage="task.progress"
            :activeColor="task.status === 'failed' ? '#ea4335' : '#1a73e8'"
            height="6"
            :showText="false"
          />
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
import { uploadFile } from '@/api/upload'
import EmptyState from '@/components/file/EmptyState.vue'

let taskIdCounter = 0

export default {
  components: { EmptyState },
  data() {
    return { tasks: [] }
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
        success: (res) => {
          const file = res.tempFiles[0]
          this.addTask(file.name, file.path)
        }
      })
    },
    addTask(name, filePath) {
      const id = ++taskIdCounter
      this.tasks.unshift({
        id, name, filePath,
        progress: 0,
        status: 'pending' // pending | uploading | done | failed
      })

      this.startUpload(id, filePath)
    },
    async startUpload(id, filePath) {
      const task = this.tasks.find(t => t.id === id)
      if (!task) return

      task.status = 'uploading'

      try {
        await uploadFile(filePath, {
          onProgress: (progress) => {
            const t = this.tasks.find(t => t.id === id)
            if (t) t.progress = progress
          }
        })
        task.status = 'done'
        task.progress = 100
      } catch (e) {
        task.status = 'failed'
      }
    },
    statusText(status) {
      const map = { pending: '等待中', uploading: '上传中', done: '已完成', failed: '失败' }
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
  &:active { background: #f5f8ff; border-color: #1a73e8; }
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
.status-uploading { color: #1a73e8; }
.status-done { color: #34a853; }
.status-failed { color: #ea4335; }
</style>