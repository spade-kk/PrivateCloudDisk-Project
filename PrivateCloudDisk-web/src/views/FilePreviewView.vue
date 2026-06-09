<template>
  <div class="file-preview-view">
    <div class="preview-header">
      <div class="header-left">
        <button @click="goBack" class="back-btn">
          <i class="fa fa-arrow-left"></i>
          <span>返回</span>
        </button>
      </div>
      <div class="header-center">
        <h1 class="page-title">文件预览</h1>
      </div>
      <div class="header-right">
        <button @click="handleDownload" class="action-btn" title="下载">
          <i class="fa fa-download"></i>
        </button>
      </div>
    </div>

    <div class="preview-content">
      <FilePreview
        :visible="showPreview"
        :file="currentFile"
        @close="handleClose"
        @download="handleDownload"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import FilePreview from '@/components/preview/FilePreview.vue'

const route = useRoute()
const router = useRouter()

const currentFile = ref(null)
const showPreview = ref(false)

// 从路由参数获取文件信息
onMounted(() => {
  const fileId = route.params.fileId
  const fileName = route.query.name || '未知文件'
  const fileType = route.query.type || 'unknown'

  if (fileId) {
    currentFile.value = {
      node_id: fileId,
      node_name: decodeURIComponent(fileName),
      node_type: fileType === 'FOLDER' ? 'FOLDER' : 'FILE'
    }
    showPreview.value = true
  } else {
    // 没有文件ID，显示错误
  }
})

// 返回上一页
const goBack = () => {
  router.back()
}

// 关闭预览
const handleClose = () => {
  showPreview.value = false
  router.back()
}

// 下载文件
const handleDownload = (file) => {
  if (!file) return

  // 触发下载逻辑
  // 可以通过事件总线或 store 触发
  const downloadUrl = `/api/files/files/${file.node_id}/download`
  const link = document.createElement('a')
  link.href = downloadUrl
  link.download = file.node_name
  link.click()
}
</script>

<style scoped>
.file-preview-view {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #1e1e1e;
}

.preview-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem 1.5rem;
  background: #252526;
  border-bottom: 1px solid #3e3e42;
}

.header-left,
.header-center,
.header-right {
  flex: 1;
}

.header-left {
  display: flex;
  justify-content: flex-start;
}

.header-center {
  display: flex;
  justify-content: center;
}

.header-right {
  display: flex;
  justify-content: flex-end;
}

.back-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  background: #3e3e42;
  color: #cccccc;
  border: none;
  padding: 0.5rem 1rem;
  border-radius: 0.5rem;
  cursor: pointer;
  transition: all 0.2s;
  font-size: 0.9rem;
}

.back-btn:hover {
  background: #4b6cb7;
  color: white;
}

.page-title {
  font-size: 1.1rem;
  font-weight: 600;
  color: #cccccc;
  margin: 0;
}

.action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2.5rem;
  height: 2.5rem;
  border: none;
  background: #3e3e42;
  border-radius: 0.5rem;
  color: #cccccc;
  cursor: pointer;
  transition: all 0.2s;
  font-size: 0.95rem;
}

.action-btn:hover {
  background: #4b6cb7;
  color: white;
}

.preview-content {
  flex: 1;
  overflow: hidden;
}
</style>
