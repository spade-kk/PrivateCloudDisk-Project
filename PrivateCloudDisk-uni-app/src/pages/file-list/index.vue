<template>
  <view class="file-list-page">
    <BreadcrumbNav
      :pathStack="nodeStack"
      @back="goBack"
      @home="goHome"
      @navigate="onBreadcrumbNavigate"
    />

    <view class="file-list">
      <FileItem
        v-for="node in children"
        :key="node.node_id"
        :node="node"
        @click="handleItemClick"
        @longpress="handleLongPress"
      />
      <EmptyState v-if="!loading && children.length === 0" icon="folder" text="当前目录为空" />
      <LoadingOverlay :visible="loading" text="加载中..." />
    </view>

    <u-action-sheet
      :show="menuShow"
      :actions="menuActions"
      @select="handleMenuSelect"
      @close="menuShow = false"
    />

    <u-modal
      :show="renameModalShow"
      title="重命名"
      :showCancelButton="true"
      :showConfirmButton="true"
      @confirm="handleRenameConfirm"
      @cancel="renameModalShow = false"
    >
      <u-input v-model="renameName" placeholder="请输入新名称" />
    </u-modal>
  </view>
</template>

<script>
import { getChildren, renameNode, moveNode, deleteNode } from '@/api/node'
import FileItem from '@/components/file/FileItem.vue'
import BreadcrumbNav from '@/components/file/BreadcrumbNav.vue'
import EmptyState from '@/components/file/EmptyState.vue'
import LoadingOverlay from '@/components/common/LoadingOverlay.vue'

export default {
  components: { FileItem, BreadcrumbNav, EmptyState, LoadingOverlay },
  data() {
    return {
      nodeStack: [],
      children: [],
      loading: true,
      menuShow: false,
      selectedItem: null,
      renameModalShow: false,
      renameName: ''
    }
  },
  computed: {
    menuActions() {
      if (!this.selectedItem) return []
      return [
        { name: '查看详情', value: 'detail' },
        { name: '重命名', value: 'rename' },
        { name: '移动', value: 'move' },
        { name: '删除', value: 'delete' }
      ]
    }
  },
  onLoad(options) {
    if (options.folderId) {
      this.nodeStack.push({
        node_id: options.folderId,
        node_name: decodeURIComponent(options.folderName || '目录')
      })
    }
    this.loadChildren()
  },
  methods: {
    async loadChildren() {
      const nodeId = this.nodeStack.length > 0
        ? this.nodeStack[this.nodeStack.length - 1].node_id
        : null
      if (!nodeId) return

      this.loading = true
      try {
        const res = await getChildren(nodeId)
        this.children = Array.isArray(res.data) ? res.data : (res.data?.items || [])
      } catch (e) {
        console.error('[FileList] 加载失败:', e)
      } finally {
        this.loading = false
      }
    },

    handleItemClick(node) {
      if (node.node_type === 'FOLDER') {
        this.nodeStack.push({ node_id: node.node_id, node_name: node.node_name })
        this.loadChildren()
      } else {
        uni.navigateTo({
          url: `/pages/file-detail/index?fileId=${node.node_id}&fileName=${encodeURIComponent(node.node_name)}`
        })
      }
    },

    handleLongPress(node) {
      this.selectedItem = node
      this.menuShow = true
    },

    handleMenuSelect(action) {
      this.menuShow = false
      if (!this.selectedItem) return

      switch (action.value) {
        case 'detail':
          this.openDetail()
          break
        case 'rename':
          this.openRename()
          break
        case 'move':
          this.openMove()
          break
        case 'delete':
          this.handleDelete()
          break
      }
    },

    openDetail() {
      const node = this.selectedItem
      if (node.node_type === 'FOLDER') {
        this.nodeStack.push({ node_id: node.node_id, node_name: node.node_name })
        this.loadChildren()
      } else {
        uni.navigateTo({
          url: `/pages/file-detail/index?fileId=${node.node_id}&fileName=${encodeURIComponent(node.node_name)}`
        })
      }
    },

    openRename() {
      this.renameName = this.selectedItem.node_name
      this.renameModalShow = true
    },

    async handleRenameConfirm() {
      if (!this.renameName.trim()) {
        return uni.showToast({ title: '请输入名称', icon: 'none' })
      }
      try {
        await renameNode(this.selectedItem.node_id, this.renameName.trim())
        uni.showToast({ title: '重命名成功', icon: 'success' })
        this.loadChildren()
      } catch (e) {
        console.error('[FileList] 重命名失败:', e)
        uni.showToast({ title: e.message || '重命名失败', icon: 'none' })
      } finally {
        this.renameModalShow = false
      }
    },

    openMove() {
      uni.showToast({ title: '移动功能开发中', icon: 'none' })
    },

    async handleDelete() {
      const node = this.selectedItem
      const res = await uni.showModal({
        title: '确认删除',
        content: `${node.node_type === 'FOLDER' ? '文件夹' : '文件'} "${node.node_name}" 将被移入回收站，可于30天内恢复。确定删除吗？`
      })
      if (res.confirm) {
        try {
          await deleteNode(node.node_id)
          uni.showToast({ title: '已移入回收站', icon: 'success' })
          this.loadChildren()
        } catch (e) {
          console.error('[FileList] 删除失败:', e)
          uni.showToast({ title: e.message || '删除失败', icon: 'none' })
        }
      }
    },

    goBack() {
      if (this.nodeStack.length > 0) {
        this.nodeStack.pop()
        this.loadChildren()
      } else {
        uni.navigateBack()
      }
    },

    goHome() {
      this.nodeStack = []
      this.loadChildren()
    },

    onBreadcrumbNavigate(item) {
      const idx = this.nodeStack.findIndex(n => n.node_id === item.node_id)
      if (idx !== -1) {
        this.nodeStack = this.nodeStack.slice(0, idx + 1)
        this.loadChildren()
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.file-list-page { min-height: 100vh; background: #f5f5f5; }
.file-list { margin: 16rpx 24rpx; background: #fff; border-radius: 16rpx; overflow: hidden; box-shadow: 0 2rpx 12rpx rgba(0,0,0,0.06); }
</style>