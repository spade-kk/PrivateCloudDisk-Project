<template>
  <div class="space-y-4 sm:space-y-6">
    <!-- 主内容区 -->
    <!-- <main class="pt-20 pb-16 container mx-auto px-4"> -->
      <!-- 路径导航 -->
      <!-- <div class="bg-white rounded-lg shadow-card p-4 mb-6">
        <PathNavigator :pathStack="fileBrowserStore.pathStack" @navigate="navigateTo" @home="goHome" />
      </div> -->

      <!-- 操作栏 -->
      <!-- <div class="bg-white rounded-lg shadow-card p-4 mb-6 flex flex-wrap items-center justify-between gap-4">
        <div class="flex items-center space-x-3">
          <button @click="showCreateModal = true" class="bg-primary hover:bg-primary/90 text-white px-4 py-2 rounded-lg flex items-center space-x-1">
            <i class="fa fa-folder-plus"></i><span>新建文件夹</span>
          </button>
          <button @click="triggerFileSelect" class="bg-success hover:bg-success/90 text-white px-4 py-2 rounded-lg flex items-center space-x-1">
            <i class="fa fa-upload"></i><span>上传文件</span>
          </button>
          <input ref="fileInputRef" type="file" class="hidden" @change="onFileSelected" />
        </div>
        <div class="flex items-center space-x-3">
          <div class="relative">
            <input v-model="fileBrowserStore.searchKeyword" type="text" class="pl-10 pr-4 py-2 border border-neutral-200 rounded-lg w-64 focus:ring-2 focus:ring-primary/30" placeholder="搜索文件或文件夹...">
            <i class="fa fa-search absolute left-3 top-1/2 -translate-y-1/2 text-neutral-400"></i>
          </div>
          <div class="flex items-center space-x-1 border border-neutral-200 rounded-lg p-1">
            <button @click="viewMode = 'grid'" :class="viewMode === 'grid' ? 'bg-primary text-white' : 'text-neutral-600 hover:bg-neutral-100'" class="px-3 py-1.5 rounded transition-all">
              <i class="fa fa-th"></i>
            </button>
            <button @click="viewMode = 'list'" :class="viewMode === 'list' ? 'bg-primary text-white' : 'text-neutral-600 hover:bg-neutral-100'" class="px-3 py-1.5 rounded transition-all">
              <i class="fa fa-list"></i>
            </button>
          </div>
        </div>
      </div> -->

    <!-- 路径导航 -->
    <div class="responsive-panel p-3 sm:p-4">
      <PathNavigator :pathStack="fileBrowserStore.pathStack" @navigate="navigateTo" @home="goHome" />
    </div>
    <WorkspaceOverview
      :nodes="fileBrowserStore.nodes"
      :selected-count="selectionStore.selectedIds.size"
      :path-depth="fileBrowserStore.pathStack.length"
    />
    <!-- 原有操作栏 -->
    <div class="responsive-panel flex flex-col gap-4 p-3 sm:p-4 md:flex-row md:flex-wrap md:items-center md:justify-between">
      <div class="grid grid-cols-2 gap-2 sm:flex sm:flex-wrap sm:items-center sm:gap-3 md:flex-nowrap">
        <button @click="showCreateModal = true" class="touch-button flex items-center justify-center gap-2 rounded-lg bg-primary px-3 py-2 text-sm text-white sm:px-4">
          <i class="fa fa-folder-plus"></i><span>新建文件夹</span>
        </button>
        <button @click="triggerFileSelect" class="touch-button flex items-center justify-center gap-2 rounded-lg bg-success px-3 py-2 text-sm text-white sm:px-4">
          <i class="fa fa-upload"></i><span>上传文件</span>
        </button>
        <input ref="fileInputRef" type="file" class="hidden" @change="onFileSelected" />
        <button v-if="selectionStore.selectedIds.size > 0" @click="clearSelection" class="col-span-2 text-sm text-neutral-500 hover:text-danger sm:col-span-1">
          <i class="fa fa-times-circle"></i> 取消选中 ({{ selectionStore.selectedIds.size }})
        </button>
      </div>
      <div class="flex flex-col gap-3 sm:flex-row sm:items-center md:ml-auto md:justify-end">
        <div class="relative w-full sm:w-72 md:w-64 lg:w-72">
          <input v-model="fileBrowserStore.searchKeyword" type="text" class="w-full rounded-lg border px-10 py-2" placeholder="搜索...">
          <i class="fa fa-search absolute left-3 top-1/2 -translate-y-1/2 text-neutral-400"></i>
        </div>
        <div class="grid grid-cols-2 rounded-lg border p-1 sm:flex">
          <button @click="viewMode = 'grid'" :class="viewMode === 'grid' ? 'bg-primary text-white' : 'text-neutral-600'" class="touch-button rounded px-3 py-1.5"> <i class="fa fa-th"></i> </button>
          <button @click="viewMode = 'list'" :class="viewMode === 'list' ? 'bg-primary text-white' : 'text-neutral-600'" class="touch-button rounded px-3 py-1.5"> <i class="fa fa-list"></i> </button>
        </div>
      </div>
    </div>


      <!-- 批量操作栏 -->
      <BatchActionsBar
        v-if="selectionStore.selectedIds.size > 0"
        :count="selectionStore.selectedIds.size"
        @batch-delete="batchDelete"
        @batch-move="openMoveDialog('move')"
        @batch-copy="openMoveDialog('copy')"
        @batch-download="batchDownload"
        @clear-selection="clearSelection"
      />
      <!-- 加载中 -->
      <div v-if="fileBrowserStore.loading" class="flex flex-col items-center justify-center py-20">
        <LoadingSpinner />
        <p class="text-neutral-500 mt-4">加载中...</p>
      </div>

      <!-- 加载失败 -->
      <PageState
        v-else-if="fileBrowserStore.error"
        type="error"
        icon="fa fa-exclamation-triangle"
        :title="fileBrowserStore.error.title"
        :description="fileBrowserStore.error.message"
        action-text="重试"
        action-icon="fa fa-refresh"
        @action="fileBrowserStore.retry"
      />

      <!-- 空状态 -->
      <EmptyState v-else-if="filteredNodes.length === 0" message="该文件夹为空" @create="showCreateModal = true" />

      <!-- 文件列表 -->
      <div v-else>
        <FileGridView
        v-if="viewMode === 'grid'"
        :nodes="filteredNodes"
        :selectedIds="selectionStore.selectedIds"
        @itemClick="onNodeClick"
        @selection-change="toggleSelect"
      />
      <FileListView
        v-else
        :nodes="filteredNodes"
        :selectedIds="selectionStore.selectedIds"
        @itemClick="onNodeClick"
        @action="onNodeAction"
        @selection-change="toggleSelect"
      />
      </div>
    <!-- </main> -->

    <!-- 页脚 -->
    <!-- <footer class="bg-white border-t border-neutral-200 py-4">
      <div class="container mx-auto px-4 text-center text-neutral-500 text-sm">
        <p>© 2025 CloudDrive 私有云网盘管理系统</p>
      </div>
    </footer> -->

    <!-- 各种弹窗/抽屉 -->
    <CreateFolderModal :visible="showCreateModal" @close="showCreateModal = false" @confirm="handleCreateFolder" />
    <UploadConfirmModal :visible="uploadConfirmVisible" :file="selectedFile" @close="uploadConfirmVisible = false" @confirm="startUploadConfirmed" />
    <DownloadConfirmModal :visible="downloadModalVisible" :fileName="pendingDownload?.node_name" @close="downloadModalVisible = false" @confirm="executeDownload" />
    <RenameDialog :visible="renameVisible" :currentName="renameTarget?.node_name" @close="renameVisible = false" @confirm="handleRename" />
    <MoveCopyDialog :visible="moveVisible" :mode="moveMode" @close="moveVisible = false" @confirm="handleMoveCopy" />
    <FileDetailDrawer :visible="detailVisible" :node="detailNode" @close="detailVisible = false" />
    <FilePreview :visible="previewVisible" :node="previewNode" @close="previewVisible = false" />
    <UploadProgressPanel 
      :visible="uploaderStore.isUploading"
      :minimized="uploadMinimized"
      :progress="uploaderStore.uploadProgress"
      :speed="uploaderStore.uploadSpeed"
      :fileName="uploaderStore.uploadFileName"
      :paused="uploaderStore.uploadPaused"
      @minimize="uploadMinimized = true"
      @restore="uploadMinimized = false"
      @togglePause="toggleUploadPause"
      @cancel="cancelUpload"
    />
    <!-- 模态框组件 -->
    <LoginModal v-if="false" /> <!-- 已改为独立登录页，此处不需要 -->
    <!-- <CreateFolderModal :visible="showCreateModal" @close="showCreateModal = false" @confirm="handleCreateFolder" />
    <DownloadConfirmModal :visible="downloadModalVisible" :fileName="pendingDownload?.node_name" @close="downloadModalVisible = false" @confirm="executeDownload" />
    <UploadConfirmModal :visible="uploadConfirmVisible" :file="selectedFile" @close="uploadConfirmVisible = false" @confirm="startUploadConfirmed" />
    <UploadProgressPanel
      :visible="uploaderStore.isUploading"
      :minimized="uploadMinimized"
      :progress="uploaderStore.uploadProgress"
      :speed="uploaderStore.uploadSpeed"
      :fileName="uploaderStore.uploadFileName"
      :paused="uploaderStore.uploadPaused"
      @minimize="uploadMinimized = true"
      @restore="uploadMinimized = false"
      @togglePause="toggleUploadPause"
      @cancel="cancelUpload"
    /> -->
  </div>
</template>

<script setup>
import { ref, computed, watch, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { useFileBrowserStore } from '@/stores/fileBrowserStore'
import { useUploaderStore } from '@/stores/uploaderStore'
import { useDownloaderStore } from '@/stores/downloaderStore'
import { useStorageStore } from '@/stores/storageStore'
import { useToastStore } from '@/stores/toastStore'
import PathNavigator from '@/components/file/PathNavigator.vue'
import StorageInfo from '@/components/file/StorageInfo.vue'
import FileGridView from '@/components/file/FileGridView.vue'
import FileListView from '@/components/file/FileListView.vue'
import CreateFolderModal from '@/components/modals/CreateFolderModal.vue'
import DownloadConfirmModal from '@/components/modals/DownloadConfirmModal.vue'
import UploadConfirmModal from '@/components/modals/UploadConfirmModal.vue'
import UploadProgressPanel from '@/components/upload/UploadProgressPanel.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import PageState from '@/components/common/PageState.vue'
import { useSelectionStore } from '@/stores/selectionStore'
import BatchActionsBar from '@/components/file/BatchActionsBar.vue'
import RenameDialog from '@/components/file/RenameDialog.vue'
import MoveCopyDialog from '@/components/file/MoveCopyDialog.vue'
import FileDetailDrawer from '@/components/file/FileDetailDrawer.vue'
import FilePreview from '@/components/file/FilePreview.vue'
import WorkspaceOverview from '@/components/dashboard/WorkspaceOverview.vue'

const router = useRouter()
const authStore = useAuthStore()
const fileBrowserStore = useFileBrowserStore()
const uploaderStore = useUploaderStore()
const downloaderStore = useDownloaderStore()
const storageStore = useStorageStore()
const toastStore = useToastStore()
const selectionStore = useSelectionStore()

const renameVisible = ref(false)
const renameTarget = ref(null)
const moveVisible = ref(false)
const moveMode = ref('move') // 'move' or 'copy'
const detailVisible = ref(false)
const detailNode = ref(null)
const previewVisible = ref(false)
const previewNode = ref(null)
const viewMode = ref('grid')
const showCreateModal = ref(false)
const downloadModalVisible = ref(false)
const uploadConfirmVisible = ref(false)
const selectedFile = ref(null)
const pendingDownload = ref(null)
const uploadMinimized = ref(false)
const fileInputRef = ref(null)

const filteredNodes = computed(() => fileBrowserStore.filteredNodes)

// 生命周期：登录后加载数据
watch(() => authStore.isLoggedIn, async (loggedIn) => {
  if (loggedIn) {
    await fileBrowserStore.loadRoot()
    await storageStore.fetchStorageInfo()
  }
}, { immediate: true })

// 监听上传完成，自动合并
watch(() => uploaderStore.chunksStatus, (newVal) => {
  if (newVal.length > 0 && newVal.every(c => c.status === 'success')) {
    uploaderStore.completeUpload()
  }
}, { deep: true })

function goHome() {
  fileBrowserStore.goHome()
}

function navigateTo(node) {
  fileBrowserStore.navigateTo(node)
}

// function onNodeClick(node) {
//   if (node.node_type === 'FOLDER') {
//     fileBrowserStore.navigateTo(node)
//   } else {
//     pendingDownload.value = node
//     downloadModalVisible.value = true
//   }
// }

// 单击节点：文件夹导航，文件则显示详情或预览
const onNodeClick = (node) => {
  if (node.node_type === 'FOLDER') navigateTo(node)
  else {
    // 根据文件类型决定预览或仅显示详情
    const ext = node.node_name.split('.').pop()?.toLowerCase()
    if (['jpg', 'jpeg', 'png', 'gif', 'pdf', 'txt', 'md', 'html', 'css', 'js'].includes(ext)) {
      previewNode.value = node
      previewVisible.value = true
    } else {
      detailNode.value = node
      detailVisible.value = true
    }
  }
}

function onNodeAction(node) {
  if (node.node_type === 'FOLDER') {
    fileBrowserStore.navigateTo(node)
  } else {
    pendingDownload.value = node
    downloadModalVisible.value = true
  }
}

// 右键菜单可触发重命名等（为简洁，本处通过操作按钮触发）
const handleRename = async (newName) => {
  // 调用API重命名
  // await renameNode(renameTarget.value.node_id, newName)
  // 刷新列表
}

async function handleCreateFolder(name) {
  const result = await fileBrowserStore.createFolder(name)
  if (result.success) {
    toastStore.showToast('文件夹创建成功', 'success')
  } else {
    toastStore.showToast(result.message, 'error')
  }
  showCreateModal.value = false
}

const handleMoveCopy = async (targetFolderId) => {
  const ids = Array.from(selectionStore.selectedIds)
  const action = moveMode.value === 'move' ? 'move' : 'copy'
  try {
    await client.post(`/v1/nodes/batch-${action}`, { ids, targetFolderId })
    toastStore.showToast(`${moveMode.value === 'move' ? '移动' : '复制'}成功`, 'success')
    fileBrowserStore.refresh()
    clearSelection()
    moveDialogVisible.value = false
  } catch (err) {
    toastStore.showToast('操作失败', 'error')
  }
}

const batchDelete = async () => {
  const ids = Array.from(selectionStore.selectedIds)
  // 批量删除API
  selectionStore.clearSelection()
}

const batchMove = () => { moveMode.value = 'move'; moveVisible.value = true }
const batchDownload = async () => {
  // 打包下载
}

async function executeDownload() {
  if (!pendingDownload.value) return
  downloadModalVisible.value = false
  const { node_id, node_name, node_size } = pendingDownload.value
  try {
    const blob = await downloaderStore.downloadFile(node_id, node_name, node_size, (percent) => {
      console.log(`下载进度: ${percent.toFixed(2)}%`)
    })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = node_name
    a.click()
    URL.revokeObjectURL(url)
    toastStore.showToast('下载成功', 'success')
  } catch (err) {
    // 错误已在store中提示
  }
}


function triggerFileSelect() {
  fileInputRef.value.click()
}

function onFileSelected(e) {
  if (e.target.files && e.target.files[0]) {
    selectedFile.value = e.target.files[0]
    uploadConfirmVisible.value = true
  }
  e.target.value = ''
}

// 批量操作
const toggleSelect = (id) => selectionStore.toggleSelect(id)
const clearSelection = () => selectionStore.clearSelection()

function startUploadConfirmed() {
  uploadConfirmVisible.value = false
  if (selectedFile.value) {
    uploaderStore.startUpload(selectedFile.value)
  }
}

function toggleUploadPause() {
  if (uploaderStore.uploadPaused) {
    uploaderStore.resumeUpload()
  } else {
    uploaderStore.pauseUpload()
  }
}

function cancelUpload() {
  uploaderStore.cancelUpload(false)
  uploadMinimized.value = false
}

function logout() {
  authStore.logout()
  router.push('/login')
}
</script>
