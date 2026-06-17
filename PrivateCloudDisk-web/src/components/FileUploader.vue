<template>
  <div class="file-upload-page">
    <!-- 顶部导航栏 -->
    <header class="header">
      <div class="container header-content">
        <div class="logo">
          <svg class="logo-icon" fill="currentColor" viewBox="0 0 20 20">
            <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm1-12a1 1 0 10-2 0v4a1 1 0 00.293.707l2.828 2.829a1 1 0 101.415-1.415L11 9.586V6z" clip-rule="evenodd"></path>
          </svg>
          <h1 class="logo-text">私有云存储</h1>
        </div>
        
        <div class="user-actions">
          <button class="action-btn">
            <svg class="action-icon" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"></path>
            </svg>
          </button>
          <button class="action-btn">
            <svg class="action-icon" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9"></path>
            </svg>
          </button>
          <div class="user-avatar">JS</div>
        </div>
      </div>
    </header>

    <!-- 主内容区 -->
    <main class="main-content">
      <div class="container">
        <!-- 路径导航 -->
        <div class="path-navigation">
          <span class="path-item current-path">我的文件</span>
          <svg class="path-separator" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7"></path>
          </svg>
          <span class="path-item">文档</span>
        </div>

        <!-- 操作工具栏 -->
        <div class="toolbar">
          <div class="toolbar-actions">
            <button 
              @click="showUploadModal = true"
              class="btn upload-btn"
            >
              <svg class="btn-icon" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12"></path>
              </svg>
              上传文件
            </button>
            
            <button class="btn new-folder-btn">
              <svg class="btn-icon" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10"></path>
              </svg>
              新建文件夹
            </button>
          </div>
          
          <div class="toolbar-controls">
            <div class="select-wrapper">
              <select class="custom-select">
                <option>最近修改时间</option>
                <option>名称</option>
                <option>大小</option>
                <option>类型</option>
              </select>
              <svg class="select-arrow" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7"></path>
              </svg>
            </div>
            
            <div class="view-toggle">
              <button class="view-btn active">
                <svg class="view-icon" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2V6zM14 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2V6zM4 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2v-2zM14 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2z"></path>
                </svg>
              </button>
              <button class="view-btn">
                <svg class="view-icon" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z"></path>
                </svg>
              </button>
            </div>
          </div>
        </div>

        <!-- 上传进度 -->
        <div v-if="uploadProgress > 0" class="upload-progress">
          <div class="progress-header">
            <span class="progress-filename">{{ uploadingFileName }}</span>
            <span class="progress-percentage">{{ uploadProgress }}%</span>
          </div>
          <div class="progress-bar-container">
            <div 
              class="progress-bar" 
              :style="{ width: uploadProgress + '%' }"
            ></div>
          </div>
        </div>

        <!-- 内容区域：文件夹和文件 -->
        <div class="content-grid">
          <!-- 文件夹 -->
          <div 
            v-for="folder in folders" 
            :key="folder.id"
            class="grid-item folder-item"
          >
            <div class="item-icon folder-icon">
              <svg class="icon" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-6l-2-2H5a2 2 0 00-2 2z"></path>
              </svg>
            </div>
            <h3 class="item-name">{{ folder.name }}</h3>
            <p class="item-meta">{{ folder.modified }}</p>
          </div>

          <!-- 文件 -->
          <div 
            v-for="file in files" 
            :key="file.id"
            class="grid-item file-item"
          >
            <div class="item-icon file-icon">
              <component :is="getFileIconComponent(file.type)" class="icon" />
            </div>
            <h3 class="item-name">{{ file.name }}</h3>
            <p class="item-meta">{{ file.size }} · {{ file.modified }}</p>
          </div>
        </div>

        <!-- 空状态 -->
        <div v-if="files.length === 0 && folders.length === 0" class="empty-state">
          <div class="empty-icon-container">
            <svg class="empty-icon" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"></path>
            </svg>
          </div>
          <h3 class="empty-title">没有文件或文件夹</h3>
          <p class="empty-desc">上传你的第一个文件或创建一个新文件夹来开始使用私有云存储</p>
          <button 
            @click="showUploadModal = true"
            class="btn primary-btn"
          >
            上传文件
          </button>
        </div>
      </div>
    </main>

    <!-- 页脚 -->
    <footer class="footer">
      <div class="container">
        <p class="copyright">© 2023 私有云存储系统 | 版本 1.0.0</p>
      </div>
    </footer>

    <!-- 上传文件模态框 -->
    <div v-if="showUploadModal" class="modal-backdrop">
      <div class="modal upload-modal animate-in">
        <div class="modal-header">
          <h2 class="modal-title">上传文件</h2>
          <button @click="showUploadModal = false" class="modal-close">
            <svg class="close-icon" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
            </svg>
          </button>
        </div>
        
        <div class="modal-body">
          <!-- 拖放区域 -->
          <div 
            @dragover.prevent @dragleave.prevent @drop.prevent="handleDrop"
            class="drop-area"
          >
            <svg class="drop-icon" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12"></path>
            </svg>
            <h3 class="drop-title">拖放文件到此处上传</h3>
            <p class="drop-desc">或点击选择文件</p>
            <label class="btn primary-btn select-file-btn">
              选择文件
              <input 
                type="file" 
                multiple 
                class="file-input" 
                @change="handleFileSelection"
              >
            </label>
            <p class="drop-note">支持的格式：文档、图片、视频等，单个文件最大 10GB</p>
          </div>
          
          <!-- 待上传文件列表 -->
          <div v-if="filesToUpload.length > 0" class="pending-files">
            <h3 class="pending-title">待上传文件</h3>
            <div class="pending-list">
              <div v-for="(file, index) in filesToUpload" :key="index" class="pending-file">
                <div class="file-info">
                  <component :is="getFileIconComponent(getFileType(file.name))" class="file-info-icon" />
                  <div class="file-details">
                    <p class="file-name">{{ file.name }}</p>
                    <p class="file-size">{{ formatFileSize(file.size) }}</p>
                  </div>
                </div>
                <button @click="removeFile(index)" class="remove-file">
                  <svg class="remove-icon" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"></path>
                  </svg>
                </button>
              </div>
            </div>
          </div>
        </div>
        
        <div class="modal-footer">
          <button 
            @click="showUploadModal = false; filesToUpload = []"
            class="btn secondary-btn"
          >
            取消
          </button>
          <button 
            @click="uploadFiles"
            :disabled="filesToUpload.length === 0 || isUploading"
            class="btn primary-btn upload-action-btn"
            :class="{ disabled: filesToUpload.length === 0 || isUploading }"
          >
            <span v-if="!isUploading">开始上传</span>
            <span v-if="isUploading">上传中...</span>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
//import { loginApi, createUploadsSessionApi } from '@/api'

// 定义文件图标组件
const ImageIcon = {
  template: `
    <svg :class="className" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z"></path>
    </svg>
  `,
  props: ['className']
};

const VideoIcon = {
  template: `
    <svg :class="className" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M14.752 11.168l-3.197-2.132A1 1 0 0010 9.87v4.263a1 1 0 001.555.832l3.197-2.132a1 1 0 000-1.664z"></path>
      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path>
    </svg>
  `,
  props: ['className']
};

const PdfIcon = {
  template: `
    <svg :class="className" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"></path>
    </svg>
  `,
  props: ['className']
};

const DocumentIcon = {
  template: `
    <svg :class="className" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"></path>
    </svg>
  `,
  props: ['className']
};

const SpreadsheetIcon = {
  template: `
    <svg :class="className" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 3v2m6-2v2M9 19v2m6-2v2M5 9H3m2 6H3m18-6h-2m2 6h-2M7 19h10a2 2 0 002-2V7a2 2 0 00-2-2H7a2 2 0 00-2 2v10a2 2 0 002 2zM9 9h6v6H9V9z"></path>
    </svg>
  `,
  props: ['className']
};

const TextIcon = {
  template: `
    <svg :class="className" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2"></path>
    </svg>
  `,
  props: ['className']
};

const DesignIcon = {
  template: `
    <svg :class="className" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z"></path>
    </svg>
  `,
  props: ['className']
};

const FileIcon = {
  template: `
    <svg :class="className" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253"></path>
    </svg>
  `,
  props: ['className']
};

// 状态管理
const showUploadModal = ref(false);
const filesToUpload = ref([]);
const isUploading = ref(false);
const uploadProgress = ref(0);
const uploadingFileName = ref('');

// 模拟已上传的文件夹数据
const folders = ref([
  { id: 1, name: '项目文档', modified: '今天 14:30' },
  { id: 2, name: '设计资源', modified: '昨天 09:15' },
  { id: 3, name: '视频素材', modified: '2023-06-10' },
  { id: 4, name: '备份文件', modified: '2023-06-05' }
]);

// 模拟已上传的文件数据
const files = ref([
  { id: 1, name: '产品需求文档.docx', type: 'document', size: '2.4 MB', modified: '今天 16:45' },
  { id: 2, name: 'UI设计稿.png', type: 'image', size: '5.7 MB', modified: '今天 11:20' },
  { id: 3, name: '项目演示视频.mp4', type: 'video', size: '125 MB', modified: '昨天 15:30' },
  { id: 4, name: '数据分析.xlsx', type: 'spreadsheet', size: '1.8 MB', modified: '2023-06-12' },
  { id: 5, name: '开发计划.pdf', type: 'pdf', size: '845 KB', modified: '2023-06-10' },
  { id: 6, name: '会议记录.txt', type: 'text', size: '12 KB', modified: '2023-06-08' },
  { id: 7, name: '系统架构图.fig', type: 'design', size: '3.2 MB', modified: '2023-06-05' },
  { id: 8, name: '测试报告.docx', type: 'document', size: '980 KB', modified: '2023-06-01' }
]);

// 文件处理函数
const handleFileSelection = (e) => {
  const selectedFiles = Array.from(e.target.files);
  if (selectedFiles.length) {
    filesToUpload.value = [...filesToUpload.value, ...selectedFiles];
    // 清空input值，允许重复选择同一文件
    e.target.value = '';
  }
};

const handleDrop = (e) => {
  const droppedFiles = Array.from(e.dataTransfer.files);
  if (droppedFiles.length) {
    filesToUpload.value = [...filesToUpload.value, ...droppedFiles];
  }
};

const removeFile = (index) => {
  filesToUpload.value.splice(index, 1);
};

// 上传文件函数 - 模拟上传过程
const uploadFiles = async () => {
  if (filesToUpload.value.length === 0) return;
  
  isUploading.value = true;
  
  // 模拟逐个上传文件
  for (const file of filesToUpload.value) {
    uploadingFileName.value = file.name;
    uploadProgress.value = 0;
    
    // 模拟上传进度
    const uploadInterval = setInterval(() => {
      uploadProgress.value += 5;
      if (uploadProgress.value >= 100) {
        clearInterval(uploadInterval);
        
        // 上传完成后，将文件添加到已上传列表
        files.value.unshift({
          id: Date.now(),
          name: file.name,
          type: getFileType(file.name),
          size: formatFileSize(file.size),
          modified: '刚刚'
        });
      }
    }, 100);

    //登陆账号
    //loginApi("15777446691", "20070315mwz");
    //创建上传会话
    //createUploadsSessionApi();
    //文件分切片开始依次上传

    //上传完毕 发送合并文件指令
    
    // 等待当前文件上传完成
    await new Promise(resolve => setTimeout(resolve, 2000));
  }
  
  // 重置上传状态
  isUploading.value = false;
  uploadProgress.value = 0;
  uploadingFileName.value = '';
  filesToUpload.value = [];
  showUploadModal.value = false;
};

// 工具函数：获取文件类型
const getFileType = (fileName) => {
  const ext = fileName.split('.').pop().toLowerCase();
  
  if (['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp'].includes(ext)) {
    return 'image';
  } else if (['mp4', 'mov', 'avi', 'mkv', 'flv', 'wmv'].includes(ext)) {
    return 'video';
  } else if (['pdf'].includes(ext)) {
    return 'pdf';
  } else if (['doc', 'docx'].includes(ext)) {
    return 'document';
  } else if (['xls', 'xlsx'].includes(ext)) {
    return 'spreadsheet';
  } else if (['txt', 'md', 'rtf'].includes(ext)) {
    return 'text';
  } else if (['psd', 'ai', 'fig', 'sketch'].includes(ext)) {
    return 'design';
  } else {
    return 'file';
  }
};

// 工具函数：获取文件图标组件
const getFileIconComponent = (fileType) => {
  const iconComponents = {
    image: ImageIcon,
    video: VideoIcon,
    pdf: PdfIcon,
    document: DocumentIcon,
    spreadsheet: SpreadsheetIcon,
    text: TextIcon,
    design: DesignIcon,
    file: FileIcon
  };
  
  return iconComponents[fileType] || FileIcon;
};

// 工具函数：格式化文件大小
const formatFileSize = (bytes) => {
  if (bytes === 0) return '0 Bytes';
  
  const k = 1024;
  const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
};
</script>

<style>
/* 基础样式与变量 */
:root {
  --primary-color: #2563eb;
  --primary-hover: #1d4ed8;
  --secondary-color: #f3f4f6;
  --secondary-hover: #e5e7eb;
  --text-primary: #111827;
  --text-secondary: #6b7280;
  --bg-primary: #f9fafb;
  --bg-secondary: #ffffff;
  --border-color: #e5e7eb;
  --folder-color: #f59e0b;
  --danger-color: #ef4444;
  --shadow-sm: 0 1px 2px rgba(0, 0, 0, 0.05);
  --shadow: 0 1px 3px rgba(0, 0, 0, 0.1), 0 1px 2px rgba(0, 0, 0, 0.06);
  --radius: 0.5rem;
  --transition: all 0.3s ease;
}

.dark {
  --primary-color: #3b82f6;
  --primary-hover: #60a5fa;
  --secondary-color: #1f2937;
  --secondary-hover: #374151;
  --text-primary: #f9fafb;
  --text-secondary: #d1d5db;
  --bg-primary: #111827;
  --bg-secondary: #1f2937;
  --border-color: #374151;
  --folder-color: #fbbf24;
}

* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
  font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
}

.file-upload-page {
  min-height: 100vh;
  background-color: var(--bg-primary);
  color: var(--text-primary);
  display: flex;
  flex-direction: column;
}

.container {
  width: 100%;
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 1rem;
}

/* 动画效果 */
@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes zoomIn {
  from { transform: scale(0.95); opacity: 0; }
  to { transform: scale(1); opacity: 1; }
}

.animate-in {
  animation-duration: 0.3s;
  animation-fill-mode: both;
  animation-name: fadeIn, zoomIn;
}

/* 按钮样式 */
.btn {
  display: inline-flex;
  align-items: center;
  padding: 0.5rem 1rem;
  border-radius: var(--radius);
  font-weight: 500;
  cursor: pointer;
  transition: var(--transition);
  border: none;
  background: none;
  color: inherit;
  font-size: 1rem;
}

.btn-icon {
  width: 1.25rem;
  height: 1.25rem;
  margin-right: 0.5rem;
}

.primary-btn {
  background-color: var(--primary-color);
  color: white;
}

.primary-btn:hover {
  background-color: var(--primary-hover);
}

.secondary-btn {
  background-color: var(--secondary-color);
  color: var(--text-primary);
  border: 1px solid var(--border-color);
}

.secondary-btn:hover {
  background-color: var(--secondary-hover);
}

.btn.disabled {
  opacity: 0.5;
  cursor: not-allowed;
  pointer-events: none;
}

/* 头部样式 */
.header {
  background-color: var(--bg-secondary);
  box-shadow: var(--shadow-sm);
  position: sticky;
  top: 0;
  z-index: 50;
  transition: var(--transition);
}

.header-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.75rem 1rem;
}

.logo {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.logo-icon {
  width: 2rem;
  height: 2rem;
  color: var(--primary-color);
}

.logo-text {
  font-size: 1.25rem;
  font-weight: 600;
}

.user-actions {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.action-btn {
  padding: 0.5rem;
  border-radius: 50%;
  cursor: pointer;
  transition: var(--transition);
  background: none;
  border: none;
  color: inherit;
}

.action-btn:hover {
  background-color: var(--secondary-color);
}

.action-icon {
  width: 1.25rem;
  height: 1.25rem;
}

.user-avatar {
  width: 2rem;
  height: 2rem;
  border-radius: 50%;
  background-color: var(--primary-color);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 500;
  cursor: pointer;
}

/* 主内容区样式 */
.main-content {
  flex: 1;
  padding: 1.5rem 0;
}

/* 路径导航 */
.path-navigation {
  display: flex;
  align-items: center;
  font-size: 0.875rem;
  margin-bottom: 1.5rem;
}

.path-item {
  color: var(--text-secondary);
}

.path-item.current-path {
  color: var(--primary-color);
  cursor: pointer;
  text-decoration: underline;
  text-underline-offset: 2px;
}

.path-separator {
  width: 1rem;
  height: 1rem;
  margin: 0 0.5rem;
  color: var(--text-secondary);
}

/* 工具栏样式 */
.toolbar {
  background-color: var(--bg-secondary);
  border-radius: var(--radius);
  box-shadow: var(--shadow-sm);
  padding: 1rem;
  margin-bottom: 1.5rem;
  transition: var(--transition);
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.toolbar:hover {
  box-shadow: var(--shadow);
}

.toolbar-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem;
}

.upload-btn {
  background-color: var(--primary-color);
  color: white;
}

.upload-btn:hover {
  background-color: var(--primary-hover);
  transform: scale(1.05);
}

.upload-btn:active {
  transform: scale(0.95);
}

.new-folder-btn {
  background-color: var(--secondary-color);
}

.new-folder-btn:hover {
  background-color: var(--secondary-hover);
}

.toolbar-controls {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

/* 选择器样式 */
.select-wrapper {
  position: relative;
}

.custom-select {
  padding: 0.5rem 2rem 0.5rem 0.75rem;
  background-color: var(--secondary-color);
  border-radius: var(--radius);
  border: none;
  appearance: none;
  font-size: 0.875rem;
  color: var(--text-primary);
  focus: outline-none;
  focus: ring-2;
  focus: ring-primary-color;
}

.select-arrow {
  position: absolute;
  right: 0.75rem;
  top: 50%;
  transform: translateY(-50%);
  width: 1rem;
  height: 1rem;
  color: var(--text-secondary);
  pointer-events: none;
}

/* 视图切换 */
.view-toggle {
  display: flex;
  border-radius: var(--radius);
  overflow: hidden;
  border: 1px solid var(--border-color);
}

.view-btn {
  padding: 0.5rem;
  background: none;
  border: none;
  color: inherit;
  cursor: pointer;
  transition: var(--transition);
}

.view-btn.active {
  background-color: var(--secondary-color);
}

.view-btn:hover:not(.active) {
  background-color: var(--secondary-color);
  opacity: 0.8;
}

.view-icon {
  width: 1.25rem;
  height: 1.25rem;
}

/* 上传进度 */
.upload-progress {
  background-color: var(--bg-secondary);
  border-radius: var(--radius);
  box-shadow: var(--shadow-sm);
  padding: 1rem;
  margin-bottom: 1.5rem;
}

.progress-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 0.5rem;
}

.progress-filename {
  font-weight: 500;
}

.progress-percentage {
  font-size: 0.875rem;
  color: var(--text-secondary);
}

.progress-bar-container {
  width: 100%;
  background-color: var(--secondary-color);
  border-radius: 1rem;
  height: 0.625rem;
  overflow: hidden;
}

.progress-bar {
  background-color: var(--primary-color);
  height: 100%;
  border-radius: 1rem;
  transition: width 0.3s ease-out;
}

/* 内容网格 */
.content-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 1rem;
}

.grid-item {
  background-color: var(--bg-secondary);
  border-radius: var(--radius);
  box-shadow: var(--shadow-sm);
  padding: 1rem;
  transition: var(--transition);
  cursor: pointer;
}

.grid-item:hover {
  box-shadow: var(--shadow);
  transform: translateY(-4px);
}

.item-icon {
  width: 3rem;
  height: 3rem;
  border-radius: var(--radius);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 0.75rem;
}

.folder-icon {
  background-color: rgba(245, 158, 11, 0.1);
  color: var(--folder-color);
}

.file-icon {
  background-color: rgba(37, 99, 235, 0.1);
  color: var(--primary-color);
}

.icon {
  width: 1.5rem;
  height: 1.5rem;
}

.item-name {
  font-weight: 500;
  margin-bottom: 0.25rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.item-meta {
  font-size: 0.75rem;
  color: var(--text-secondary);
}

/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 4rem 1rem;
  text-align: center;
}

.empty-icon-container {
  width: 5rem;
  height: 5rem;
  border-radius: 50%;
  background-color: var(--secondary-color);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 1rem;
}

.empty-icon {
  width: 2.5rem;
  height: 2.5rem;
  color: var(--text-secondary);
}

.empty-title {
  font-size: 1.125rem;
  font-weight: 500;
  margin-bottom: 0.5rem;
}

.empty-desc {
  color: var(--text-secondary);
  max-width: 280px;
  margin-bottom: 1.5rem;
}

/* 页脚样式 */
.footer {
  background-color: var(--bg-secondary);
  border-top: 1px solid var(--border-color);
  padding: 1rem 0;
}

.copyright {
  font-size: 0.875rem;
  color: var(--text-secondary);
  text-align: center;
}

/* 模态框样式 */
.modal-backdrop {
  position: fixed;
  inset: 0;
  background-color: rgba(0, 0, 0, 0.5);
  z-index: 50;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1rem;
}

.modal {
  background-color: var(--bg-secondary);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  width: 100%;
  max-width: 560px;
  max-height: 90vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.modal-header {
  padding: 1rem;
  border-bottom: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.modal-title {
  font-size: 1.125rem;
  font-weight: 600;
}

.modal-close {
  padding: 0.25rem;
  border-radius: 50%;
  cursor: pointer;
  transition: var(--transition);
  background: none;
  border: none;
  color: inherit;
}

.modal-close:hover {
  background-color: var(--secondary-color);
}

.close-icon {
  width: 1.25rem;
  height: 1.25rem;
}

.modal-body {
  flex: 1;
  padding: 1.5rem;
  overflow-y: auto;
}

.modal-footer {
  padding: 1rem;
  border-top: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 0.75rem;
}

/* 拖放区域 */
.drop-area {
  border: 2px dashed var(--border-color);
  border-radius: var(--radius);
  padding: 2.5rem;
  text-align: center;
  margin-bottom: 1.5rem;
  transition: var(--transition);
  background-color: rgba(0, 0, 0, 0.02);
}

.drop-area:hover {
  border-color: var(--primary-color);
}

.drop-icon {
  width: 3rem;
  height: 3rem;
  margin: 0 auto 1rem;
  color: var(--text-secondary);
}

.drop-title {
  font-weight: 500;
  margin-bottom: 0.5rem;
}

.drop-desc {
  font-size: 0.875rem;
  color: var(--text-secondary);
  margin-bottom: 1rem;
}

.file-input {
  display: none;
}

.drop-note {
  font-size: 0.75rem;
  color: var(--text-secondary);
  margin-top: 1rem;
}

/* 待上传文件 */
.pending-files {
  margin-bottom: 1.5rem;
}

.pending-title {
  font-weight: 500;
  margin-bottom: 0.75rem;
}

.pending-list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.pending-file {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.75rem;
  background-color: rgba(0, 0, 0, 0.02);
  border-radius: var(--radius);
}

.file-info {
  display: flex;
  align-items: center;
}

.file-info-icon {
  width: 1.25rem;
  height: 1.25rem;
  margin-right: 0.75rem;
  color: var(--primary-color);
}

.file-details {
  min-width: 0;
}

.file-name {
  font-size: 0.875rem;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.file-size {
  font-size: 0.75rem;
  color: var(--text-secondary);
}

.remove-file {
  padding: 0.25rem;
  background: none;
  border: none;
  color: var(--text-secondary);
  cursor: pointer;
  transition: var(--transition);
}

.remove-file:hover {
  color: var(--danger-color);
}

.remove-icon {
  width: 1rem;
  height: 1rem;
}

/* 响应式调整 */
@media (max-width: 768px) {
  .content-grid {
    grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  }
  
  .toolbar-actions, .toolbar-controls {
    width: 100%;
    justify-content: center;
  }
  
  .modal {
    max-width: 100%;
  }
}

@media (max-width: 480px) {
  .content-grid {
    grid-template-columns: repeat(auto-fill, minmax(100px, 1fr));
  }
  
  .drop-area {
    padding: 1.5rem;
  }
  
  .item-name {
    font-size: 0.875rem;
  }
}
</style>
