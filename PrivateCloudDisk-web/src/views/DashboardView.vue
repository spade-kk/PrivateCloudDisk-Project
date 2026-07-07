<template>
  <div class="space-y-4 sm:space-y-6" @contextmenu="onBlankAreaContextMenu">
    <!-- 工作区位置与状态 -->
    <div class="responsive-panel dashboard-location-panel p-3 sm:p-4">
      <div class="min-w-0 flex-1">
        <PathNavigator :pathStack="fileBrowserStore.pathStack" @navigate="navigateTo" @home="goHome" />
      </div>
      <WorkspaceOverview
        class="dashboard-location-overview"
        :nodes="fileBrowserStore.nodes"
        :selected-count="selectionStore.selectedIds.size"
        :path-depth="fileBrowserStore.pathStack.length"
      />
    </div>

    <!-- 操作栏 -->
    <div class="responsive-panel flex flex-col gap-3 p-3 sm:flex-row sm:flex-wrap sm:items-center sm:justify-between sm:gap-4 sm:p-4">
      <div class="grid grid-cols-2 gap-2 sm:flex sm:flex-wrap sm:items-center sm:gap-3 md:flex-nowrap">
        <button @click="showCreateModal = true" class="touch-button flex items-center justify-center gap-2 rounded-lg bg-primary px-3 py-2.5 text-sm text-white sm:px-4 sm:py-2">
          <i class="fa fa-folder-plus"></i><span class="hidden sm:inline">新建文件夹</span><span class="sm:hidden">新建</span>
        </button>
        <button @click="triggerFileSelect" class="touch-button flex items-center justify-center gap-2 rounded-lg bg-success px-3 py-2.5 text-sm text-white sm:px-4 sm:py-2">
          <i class="fa fa-upload"></i><span class="hidden sm:inline">上传文件</span><span class="sm:hidden">上传</span>
        </button>
        <input ref="fileInputRef" type="file" class="hidden" @change="onFileSelected" />
        <FolderUploadPanel />
        <button v-if="selectionStore.selectedIds.size > 0" @click="clearSelection" class="col-span-2 text-sm text-neutral-500 hover:text-danger sm:col-span-1">
          <i class="fa fa-times-circle"></i> 取消选中 ({{ selectionStore.selectedIds.size }})
        </button>
      </div>
      <div class="flex flex-col gap-3 sm:flex-row sm:items-center md:ml-auto md:justify-end">
        <div class="w-full sm:w-64 md:w-72 lg:w-96">
          <SmartSearchBox mode="compact" />
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
        :starredIds="starredStore.allStarredIds"
        @itemClick="onNodeClick"
        @action="onNodeAction"
        @selection-change="toggleSelect"
        @star="onStarToggle"
        @contextmenu="onItemContextMenu"
      />
      <FileListView
        v-else
        :nodes="filteredNodes"
        :selectedIds="selectionStore.selectedIds"
        :starredIds="starredStore.allStarredIds"
        @itemClick="onNodeClick"
        @action="onNodeAction"
        @selection-change="toggleSelect"
        @star="onStarToggle"
        @contextmenu="onItemContextMenu"
      />
      </div>

    <!-- 各种弹窗/抽屉 -->
    <CreateFolderModal :visible="showCreateModal" @close="showCreateModal = false" @confirm="handleCreateFolder" />
    <UploadConfirmModal :visible="uploadConfirmVisible" :file="selectedFile" @close="closeUploadConfirm" @confirm="startUploadConfirmed" />
    <DownloadConfirmModal :visible="downloadModalVisible" :fileName="pendingDownload?.node_name" @close="downloadModalVisible = false" @confirm="executeDownload" />
    <RenameDialog :visible="renameVisible" :currentName="renameTarget?.node_name" @close="renameVisible = false" @confirm="handleRename" />
    <MoveCopyDialog :visible="moveVisible" :mode="moveMode" @close="moveVisible = false" @confirm="handleMoveCopy" />
    <FileDetailDrawer :visible="detailVisible" :node="detailNode" @close="detailVisible = false" />
    <FilePreview :visible="previewVisible" :node="previewNode" @close="previewVisible = false" />
    <FolderDownloadPanel />
    <!-- 右键菜单 -->
    <ContextMenu
      :visible="contextMenuVisible"
      :x="contextMenuX"
      :y="contextMenuY"
      :items="contextMenuItems"
      @close="closeContextMenu"
      @action="onContextMenuAction"
    />
    <!-- 隐藏的文件夹选择器（右键菜单「上传文件夹」使用） -->
    <input
      ref="folderInputRef"
      type="file"
      class="hidden"
      webkitdirectory
      directory
      multiple
      @change="onContextFolderSelected"
    />
    <!-- 模态框组件 -->
    <!-- <LoginModal v-if="false" />  -->
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { useFileBrowserStore } from '@/stores/fileBrowserStore'
import { useUploaderStore } from '@/stores/uploaderStore'
import { useDownloaderStore } from '@/stores/downloaderStore'
import { useStorageStore } from '@/stores/storageStore'
import { useToastStore } from '@/stores/toastStore'
import { useStarredStore } from '@/stores/starred'
import PathNavigator from '@/components/file/PathNavigator.vue'
import StorageInfo from '@/components/file/StorageInfo.vue'
import FileGridView from '@/components/file/FileGridView.vue'
import FileListView from '@/components/file/FileListView.vue'
import CreateFolderModal from '@/components/modals/CreateFolderModal.vue'
import DownloadConfirmModal from '@/components/modals/DownloadConfirmModal.vue'
import UploadConfirmModal from '@/components/modals/UploadConfirmModal.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import PageState from '@/components/common/PageState.vue'
import { useSelectionStore } from '@/stores/selectionStore'
import { useFolderUploaderStore } from '@/stores/folderUploaderStore'
import { useFolderDownloaderStore } from '@/stores/folderDownloaderStore'
import BatchActionsBar from '@/components/file/BatchActionsBar.vue'
import RenameDialog from '@/components/file/RenameDialog.vue'
import MoveCopyDialog from '@/components/file/MoveCopyDialog.vue'
import FileDetailDrawer from '@/components/file/FileDetailDrawer.vue'
import FilePreview from '@/components/file/FilePreview.vue'
import FolderUploadPanel from '@/components/file/FolderUploadPanel.vue'
import FolderDownloadPanel from '@/components/file/FolderDownloadPanel.vue'
import WorkspaceOverview from '@/components/dashboard/WorkspaceOverview.vue'
import SmartSearchBox from '@/components/search/SmartSearchBox.vue'
import ContextMenu from '@/components/common/ContextMenu.vue'
import type { ContextMenuItem } from '@/components/common/ContextMenu.vue'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const fileBrowserStore = useFileBrowserStore()
const uploaderStore = useUploaderStore()
const downloaderStore = useDownloaderStore()
const storageStore = useStorageStore()
const toastStore = useToastStore()
const selectionStore = useSelectionStore()
const starredStore = useStarredStore()
const folderUploaderStore = useFolderUploaderStore()
const folderDownloaderStore = useFolderDownloaderStore()

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
const fileInputRef = ref(null)
const folderInputRef = ref<HTMLInputElement | null>(null)

// ============================================================
// 右键菜单状态
// ============================================================
const contextMenuVisible = ref(false)
const contextMenuX = ref(0)
const contextMenuY = ref(0)
const contextMenuItems = ref<ContextMenuItem[]>([])
const contextMenuNode = ref<any>(null) // 右键命中的节点（文件/文件夹），null 表示空白区域

const filteredNodes = computed(() => fileBrowserStore.filteredNodes)

// 生命周期：登录后加载数据
onMounted(async () => {
  await starredStore.initStarredIds()
})

watch(() => authStore.isLoggedIn, async (loggedIn) => {
  if (loggedIn) {
    await fileBrowserStore.loadRoot()
    if (route.query.node) {
      await fileBrowserStore.loadChildren(route.query.node)
    }
    await storageStore.fetchStorageInfo()
  }
}, { immediate: true })

watch(() => route.query.node, async (nodeId) => {
  if (authStore.isLoggedIn && nodeId) {
    await fileBrowserStore.loadChildren(nodeId)
  }
})

function goHome() {
  fileBrowserStore.goHome()
}

function navigateTo(node: any) {
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

async function onNodeAction(node, actionType) {
  if (actionType === 'download') {
    if (node.node_type === 'FOLDER') {
      // 文件夹下载：获取所有文件并下载
      folderDownloaderStore.startFolderDownload(node.node_id, node.node_name)
    } else {
      pendingDownload.value = node
      downloadModalVisible.value = true
    }
  } else if (actionType === 'rename') {
    renameTarget.value = node
    renameVisible.value = true
  } else if (actionType === 'delete') {
    // 直接调用删除接口，传入单个ID
    const id = node.node_id
    const type = node.node_type
    let result = null
    if (type === 'FILE') {
      result = await fileBrowserStore.deleteFileNode(id)
    } else {
      result = await fileBrowserStore.deleteFolderNode(id)
    }
    if(result && result.success) toastStore.showToast('删除成功', 'success')
    
  } else if (actionType === 'detail') {
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

// 右键菜单可触发重命名等（为简洁，本处通过操作按钮触发）
const handleRename = async (newName) => {
  // 调用API重命名
  // 刷新列表
  const type = renameTarget.value.node_type
  const id = renameTarget.value.node_id
  let result = null
  if (type === 'FILE') {
    result = await fileBrowserStore.renameFileNode(id, newName)
  } else {
    result = await fileBrowserStore.renameFolderNode(id, newName)
  }
  if (!result || !result.success) toastStore.showToast('重命名失败', 'error')
  else toastStore.showToast('重命名成功', 'success')

  renameVisible.value = false
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

function openMoveDialog(mode) {
  moveMode.value = mode
  moveVisible.value = true
}

const handleMoveCopy = async (targetFolderId) => {
  const ids = Array.from(selectionStore.selectedIds)
  const action = moveMode.value === 'move' ? 'move' : 'copy'
  try {
    //await client.post(`/v1/nodes/batch-${action}`, { items, targetFolderId })
    ids.forEach(async (id) => {
      const type = selectionStore.selectedTypes.get(id)
      let result = null
      if (type === 'FILE') {
        if (action === 'move') result = await fileBrowserStore.moveFile(id, targetFolderId)
      } else {
        if (action === 'move') result = await fileBrowserStore.moveFolder(id, targetFolderId)
      }
      if (!result || !result.success) {
        toastStore.showToast(`${type === 'FILE' ? 'File' : "Folder"} Node ID${id} ${action === 'move' ? '移动' : '复制'}失败`, 'error')
        throw new Error('批量操作失败')
      }
    })
    toastStore.showToast(`批量${moveMode.value === 'move' ? '移动' : '复制'}成功`, 'success')
    clearSelection()
    moveVisible.value = false
  } catch (err) {
    toastStore.showToast('操作失败', 'error')
  }
}

const batchDelete = async () => {
  const ids = Array.from(selectionStore.selectedIds)
  let result = null
  try {
    // 批量删除API
    ids.forEach(async (id) => {
      const type = selectionStore.selectedTypes.get(id)
      if (type === 'FILE') {
        result = await fileBrowserStore.deleteFileNode(id)
      } else {
        result = await fileBrowserStore.deleteFolderNode(id)
      }
      if (!result || !result.success) {
        toastStore.showToast(`${type === 'FILE' ? 'File' : "Folder"} Node ID${id} 删除失败`, 'error')
        throw new Error('批量删除失败')
      }
    })
  }
  catch (err) {
    toastStore.showToast('批量删除失败', 'error')
    selectionStore.clearSelection()
  }
  toastStore.showToast('批量删除成功', 'success')
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
    const blob = await downloaderStore.downloadFile(node_id, node_size, node_name)
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
  fileInputRef.value?.click()
}

function onFileSelected(e) {
  if (e.target.files && e.target.files[0]) {
    selectedFile.value = e.target.files[0]
    uploadConfirmVisible.value = true
  }
  e.target.value = ''
}

function closeUploadConfirm() {
  uploadConfirmVisible.value = false
  selectedFile.value = null
}

// 批量操作
const toggleSelect = (id, type) => selectionStore.toggleSelect(id, type)
const clearSelection = () => selectionStore.clearSelection()

function startUploadConfirmed() {
  uploadConfirmVisible.value = false
  if (selectedFile.value) {
    uploaderStore.startUpload(selectedFile.value)
    selectedFile.value = null
  }
}

// ============================================================
// 收藏操作
// ============================================================

async function onStarToggle(node: any) {
  try {
    const isNowStarred = await starredStore.toggleStar(node.node_id, node.node_type)
    toastStore.showSuccess(isNowStarred ? '已收藏' : '已取消收藏')
  } catch (err: any) {
    toastStore.showError('操作失败')
  }
}

function logout() {
  authStore.logout()
  router.push('/login')
}

// ============================================================
// 右键菜单逻辑
// ============================================================

/** 空白区域右键菜单 */
function onBlankAreaContextMenu(event: MouseEvent) {
  event.preventDefault()
  contextMenuNode.value = null
  contextMenuX.value = event.clientX
  contextMenuY.value = event.clientY
  contextMenuItems.value = [
    {
      label: '新建',
      type: 'header',
    },
    {
      label: '新建文件夹',
      key: 'create-folder',
      icon: 'fa fa-folder-plus',
    },
    {
      label: '上传文件',
      key: 'upload-file',
      icon: 'fa fa-upload',
    },
    {
      label: '上传文件夹',
      key: 'upload-folder',
      icon: 'fa fa-folder-open',
    },
    {
      type: 'separator',
      label: '',
    },
    {
      label: '刷新',
      key: 'refresh',
      icon: 'fa fa-refresh',
      shortcut: 'F5',
    },
  ]
  contextMenuVisible.value = true
}

/** 文件/文件夹右键菜单 */
function onItemContextMenu(event: MouseEvent, node: any) {
  event.preventDefault()
  contextMenuNode.value = node
  contextMenuX.value = event.clientX
  contextMenuY.value = event.clientY

  const isFolder = node.node_type === 'FOLDER'

  if (isFolder) {
    // 文件夹菜单
    contextMenuItems.value = [
      {
        label: '文件夹操作',
        type: 'header',
      },
      {
        label: '上传文件',
        key: 'upload-file',
        icon: 'fa fa-upload',
      },
      {
        label: '上传文件夹',
        key: 'upload-folder',
        icon: 'fa fa-folder-open',
      },
      {
        label: '新建子文件夹',
        key: 'create-subfolder',
        icon: 'fa fa-folder-plus',
      },
      {
        type: 'separator',
        label: '',
      },
      {
        label: '打开',
        key: 'open',
        icon: 'fa fa-folder-open',
      },
      {
        label: '下载文件夹',
        key: 'download',
        icon: 'fa fa-download',
      },
      {
        type: 'separator',
        label: '',
      },
      {
        label: '分享',
        key: 'share',
        icon: 'fa fa-share-alt',
      },
      {
        label: '收藏',
        key: 'star',
        icon: 'fa fa-star-o',
      },
      {
        type: 'separator',
        label: '',
      },
      {
        label: '移动',
        key: 'move',
        icon: 'fa fa-arrows',
      },
      {
        label: '重命名',
        key: 'rename',
        icon: 'fa fa-pencil',
        shortcut: 'F2',
      },
      {
        type: 'separator',
        label: '',
      },
      {
        label: '删除',
        key: 'delete',
        icon: 'fa fa-trash-o',
        danger: true,
      },
      {
        type: 'separator',
        label: '',
      },
      {
        label: '详细信息',
        key: 'detail',
        icon: 'fa fa-info-circle',
        shortcut: 'Alt+Enter',
      },
    ]
  } else {
    // 文件菜单
    contextMenuItems.value = [
      {
        label: '文件操作',
        type: 'header',
      },
      {
        label: '下载',
        key: 'download',
        icon: 'fa fa-download',
      },
      {
        label: '预览',
        key: 'preview',
        icon: 'fa fa-eye',
      },
      {
        type: 'separator',
        label: '',
      },
      {
        label: '分享',
        key: 'share',
        icon: 'fa fa-share-alt',
      },
      {
        label: '收藏',
        key: 'star',
        icon: 'fa fa-star-o',
      },
      {
        type: 'separator',
        label: '',
      },
      {
        label: '移动',
        key: 'move',
        icon: 'fa fa-arrows',
      },
      {
        label: '重命名',
        key: 'rename',
        icon: 'fa fa-pencil',
        shortcut: 'F2',
      },
      {
        type: 'separator',
        label: '',
      },
      {
        label: '删除',
        key: 'delete',
        icon: 'fa fa-trash-o',
        danger: true,
      },
      {
        type: 'separator',
        label: '',
      },
      {
        label: '详细信息',
        key: 'detail',
        icon: 'fa fa-info-circle',
        shortcut: 'Alt+Enter',
      },
    ]
  }
  contextMenuVisible.value = true
}

/** 关闭右键菜单 */
function closeContextMenu() {
  contextMenuVisible.value = false
  contextMenuNode.value = null
}

/** 处理右键菜单操作 */
function onContextMenuAction(item: ContextMenuItem) {
  const node = contextMenuNode.value

  switch (item.key) {
    // --- 空白区域操作 ---
    case 'create-folder':
      showCreateModal.value = true
      break
    case 'upload-file':
      triggerFileSelect()
      break
    case 'upload-folder':
      folderInputRef.value?.click()
      break
    case 'refresh':
      fileBrowserStore.refresh()
      break

    // --- 文件/文件夹通用操作 ---
    case 'open':
      if (node && node.node_type === 'FOLDER') {
        navigateTo(node)
      }
      break
    case 'download':
      if (node) {
        if (node.node_type === 'FOLDER') {
          folderDownloaderStore.startFolderDownload(node.node_id, node.node_name)
        } else {
          pendingDownload.value = node
          downloadModalVisible.value = true
        }
      }
      break
    case 'preview':
      if (node) {
        previewNode.value = node
        previewVisible.value = true
      }
      break
    case 'share':
      if (node) {
        toastStore.showToast('分享功能开发中', 'info')
      }
      break
    case 'star':
      if (node) {
        onStarToggle(node)
      }
      break
    case 'move':
      if (node) {
        selectionStore.clearSelection()
        selectionStore.toggleSelect(node.node_id, node.node_type)
        moveMode.value = 'move'
        moveVisible.value = true
      }
      break
    case 'rename':
      if (node) {
        renameTarget.value = node
        renameVisible.value = true
      }
      break
    case 'delete':
      if (node) {
        onNodeAction(node, 'delete')
      }
      break
    case 'detail':
      if (node) {
        detailNode.value = node
        detailVisible.value = true
      }
      break
    case 'create-subfolder':
      if (node && node.node_type === 'FOLDER') {
        // 进入该文件夹后再创建
        navigateTo(node)
        setTimeout(() => {
          showCreateModal.value = true
        }, 300)
      }
      break
  }
}

/** 右键菜单触发的文件夹选择 */
function onContextFolderSelected(e: Event) {
  const input = e.target as HTMLInputElement
  const fileList = input.files
  if (fileList && fileList.length > 0) {
    const files = Array.from(fileList)
    const targetNodeId = fileBrowserStore.currentNodeId
    folderUploaderStore.startFolderUpload(targetNodeId, files)
  }
  input.value = ''
}
</script>

<style scoped>
.dashboard-location-panel {
  display: flex;
  align-items: stretch;
  gap: 16px;
}

.dashboard-location-overview {
  width: min(420px, 34%);
  flex-shrink: 0;
}

@media (max-width: 1180px) {
  .dashboard-location-panel {
    flex-direction: column;
  }

  .dashboard-location-overview {
    width: 100%;
  }
}
</style>
