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
  </view>
</template>

<script>
import { getChildrenPaged } from '@/api/node'
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
      selectedItem: null
    }
  },
  computed: {
    menuActions() {
      if (!this.selectedItem) return []
      return [
        { name: '查看详情', value: 'detail' }
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
        const res = await getChildrenPaged(nodeId, { page: 1, pageSize: 50 })
        this.children = res.data?.items || []
      } catch (e) { /* 已处理 */ } finally {
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
      if (action.value === 'detail' && this.selectedItem) {
        uni.navigateTo({
          url: `/pages/file-detail/index?fileId=${this.selectedItem.node_id}&fileName=${encodeURIComponent(this.selectedItem.node_name)}`
        })
      }
      this.menuShow = false
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