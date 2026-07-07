<template>
  <view class="home-page">
    <!-- 顶部搜索入口 -->
    <view class="search-bar" @click="goSearch">
      <u-icon name="search" size="36" color="#9aa0a6" />
      <text class="search-placeholder">搜索文件...</text>
    </view>

    <!-- 容量概览卡片 -->
    <view class="quota-card" v-if="state.quota">
      <view class="quota-header">
        <text class="quota-title">存储空间</text>
        <text class="quota-text">
          {{ formatFileSize(state.quota.used_capacity || 0) }} / {{ formatFileSize(state.quota.total_capacity || 0) }}
        </text>
      </view>
      <u-line-progress :percentage="usagePercent" activeColor="#1a73e8" height="8" :showText="false" />
      <view class="quota-footer">
        <text>文件 {{ state.quota.file_count || 0 }} 个</text>
        <text>{{ usagePercent }}% 已使用</text>
      </view>
    </view>

    <!-- 快捷操作 -->
    <view class="quick-actions">
      <view class="action-item" @click="handleCreateFolder">
        <view class="action-icon action-icon-blue">
          <u-icon name="folder-add" size="40" color="#1a73e8" />
        </view>
        <text class="action-text">新建文件夹</text>
      </view>
      <view class="action-item" @click="handleUploadFile">
        <view class="action-icon action-icon-green">
          <u-icon name="upload" size="40" color="#34a853" />
        </view>
        <text class="action-text">上传文件</text>
      </view>
      <view class="action-item" @click="goFavorites">
        <view class="action-icon action-icon-yellow">
          <u-icon name="star" size="40" color="#fbbc04" />
        </view>
        <text class="action-text">我的收藏</text>
      </view>
      <view class="action-item" @click="goTrash">
        <view class="action-icon action-icon-red">
          <u-icon name="trash" size="40" color="#ea4335" />
        </view>
        <text class="action-text">回收站</text>
      </view>
    </view>

    <!-- 面包屑导航 -->
    <BreadcrumbNav
      v-if="state.nodeStack.length > 0"
      :pathStack="state.nodeStack"
      @back="goBack"
      @home="goHome"
      @navigate="onBreadcrumbNavigate"
    />

    <!-- 文件列表 -->
    <view class="file-list">
      <FileItem
        v-for="node in state.children"
        :key="node.node_id"
        :node="node"
        @click="handleItemClick"
        @longpress="handleLongPress"
      />

      <!-- 空状态 -->
      <EmptyState
        v-if="!state.loading && state.children.length === 0"
        icon="folder"
        text="当前目录为空"
        subText="点击上方按钮上传文件或新建文件夹"
      />

      <!-- 加载中 -->
      <LoadingOverlay :visible="state.loading" text="加载中..." />

      <!-- 加载更多 -->
      <view class="load-more" v-if="state.hasMore && !state.loading">
        <u-loading-icon v-if="state.loadingMore" size="20" text="加载中..." />
        <text v-else class="load-more-text" @click="loadMore">点击加载更多</text>
      </view>
    </view>

    <!-- 操作菜单 (长按) -->
    <u-action-sheet
      :show="menuShow"
      :actions="menuActions"
      @select="handleMenuSelect"
      @close="menuShow = false"
    />

    <!-- 重命名弹窗 -->
    <u-modal
      :show="renameModalShow"
      title="重命名"
      :showCancelButton="true"
      confirmText="确认"
      @confirm="confirmRename"
      @cancel="renameModalShow = false"
    >
      <view class="modal-form">
        <u-input v-model="renameValue" placeholder="请输入新名称" clearable />
      </view>
    </u-modal>
  </view>
</template>

<script>
import { useFileBrowser } from '@/composables/useFileBrowser'
import { useUserAuth } from '@/composables/useUserAuth'
import { createFolder, renameNode } from '@/api/node'
import { moveFileToTrash, moveFolderToTrash } from '@/api/trash'
import { formatFileSize } from '@/utils/helper'
import FileItem from '@/components/file/FileItem.vue'
import BreadcrumbNav from '@/components/file/BreadcrumbNav.vue'
import EmptyState from '@/components/file/EmptyState.vue'
import LoadingOverlay from '@/components/common/LoadingOverlay.vue'

export default {
  components: { FileItem, BreadcrumbNav, EmptyState, LoadingOverlay },
  setup() {
    const { state, isRoot, usagePercent, init, loadChildren, loadMore, navigateTo, goBack, goHome, refresh } = useFileBrowser()
    const { requireAuth } = useUserAuth()

    return {
      state, isRoot, usagePercent,
      init, loadChildren, loadMore, navigateTo, goBack, goHome, refresh,
      requireAuth
    }
  },
  data() {
    return {
      menuShow: false,
      selectedItem: null,
      renameModalShow: false,
      renameValue: ''
    }
  },
  computed: {
    menuActions() {
      if (!this.selectedItem) return []
      const isFolder = this.selectedItem.node_type === 'FOLDER'
      return [
        { name: '重命名', value: 'rename' },
        { name: '移动', value: 'move' },
        { name: isFolder ? '移入回收站' : '删除', value: 'trash' }
      ]
    }
  },
  onShow() {
    if (!this.requireAuth()) return
    this.init().then(() => this.loadChildren(1))
  },
  methods: {
    formatFileSize,

    handleItemClick(node) {
      if (node.node_type === 'FOLDER') {
        this.navigateTo(node)
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

    onBreadcrumbNavigate(item) {
      const idx = this.state.nodeStack.findIndex(n => n.node_id === item.node_id)
      if (idx !== -1) {
        this.state.nodeStack = this.state.nodeStack.slice(0, idx + 1)
        this.loadChildren(1)
      }
    },

    async handleMenuSelect(action) {
      const item = this.selectedItem
      if (!item) return
      this.menuShow = false

      switch (action.value) {
        case 'rename':
          this.renameValue = item.node_name
          this.renameModalShow = true
          break
        case 'trash':
          await this.trashItem(item)
          break
        case 'move':
          uni.showToast({ title: '移动功能开发中', icon: 'none' })
          break
      }
    },

    async confirmRename() {
      if (!this.renameValue.trim()) return
      try {
        await renameNode(this.selectedItem.node_id, { name: this.renameValue.trim() })
        uni.showToast({ title: '重命名成功', icon: 'success' })
        this.renameModalShow = false
        this.refresh()
      } catch (e) {
        // 错误由 request.js 统一处理
      }
    },

    async trashItem(item) {
      try {
        if (item.node_type === 'FOLDER') {
          await moveFolderToTrash(item.node_id)
        } else {
          await moveFileToTrash(item.node_id)
        }
        uni.showToast({ title: '已移入回收站', icon: 'success' })
        this.refresh()
      } catch (e) {
        // 错误已处理
      }
    },

    handleCreateFolder() {
      uni.showModal({
        title: '新建文件夹',
        editable: true,
        placeholderText: '请输入文件夹名称',
        success: async (res) => {
          if (res.confirm && res.content) {
            try {
              await createFolder({
                node_id: this.state.rootNodeId,
                folder_name: res.content
              })
              if (this.state.nodeStack.length > 0) {
                // 如有父节点则在当前目录创建
                await createFolder({
                  node_id: this.state.nodeStack[this.state.nodeStack.length - 1].node_id,
                  folder_name: res.content
                })
              }
              uni.showToast({ title: '创建成功', icon: 'success' })
              this.refresh()
            } catch (e) {
              // 错误已处理
            }
          }
        }
      })
    },

    handleUploadFile() {
      uni.navigateTo({ url: '/pages/upload/index' })
    },

    goSearch() {
      uni.navigateTo({ url: '/pages/search/index' })
    },

    goFavorites() {
      uni.switchTab({ url: '/pages/favorites/index' })
    },

    goTrash() {
      uni.switchTab({ url: '/pages/trash/index' })
    }
  }
}
</script>

<style lang="scss" scoped>
.home-page { min-height: 100vh; background: #f5f5f5; padding-bottom: 16rpx; }

/* 搜索栏 */
.search-bar {
  display: flex;
  align-items: center;
  padding: 16rpx 32rpx;
  background: #fff;
  border-bottom: 1rpx solid #f0f0f0;
}
.search-placeholder { font-size: 28rpx; color: #9aa0a6; margin-left: 16rpx; }

/* 容量卡片 */
.quota-card {
  margin: 16rpx 24rpx;
  padding: 24rpx;
  background: #fff;
  border-radius: 16rpx;
  box-shadow: 0 2rpx 12rpx rgba(0,0,0,0.06);
}
.quota-header { display: flex; justify-content: space-between; margin-bottom: 16rpx; }
.quota-title { font-size: 28rpx; color: #202124; font-weight: 500; }
.quota-text { font-size: 24rpx; color: #5f6368; }
.quota-footer { display: flex; justify-content: space-between; margin-top: 12rpx; font-size: 22rpx; color: #9aa0a6; }

/* 快捷操作 */
.quick-actions {
  display: flex;
  justify-content: space-around;
  padding: 24rpx 16rpx;
  margin: 0 24rpx;
  background: #fff;
  border-radius: 16rpx;
  box-shadow: 0 2rpx 12rpx rgba(0,0,0,0.06);
}
.action-item { display: flex; flex-direction: column; align-items: center; }
.action-icon {
  width: 80rpx; height: 80rpx; border-radius: 20rpx;
  display: flex; align-items: center; justify-content: center;
  margin-bottom: 8rpx;
}
.action-icon-blue { background: #e8f0fe; }
.action-icon-green { background: #e6f4ea; }
.action-icon-yellow { background: #fef7e0; }
.action-icon-red { background: #fce8e6; }
.action-text { font-size: 22rpx; color: #5f6368; }

/* 文件列表 */
.file-list { margin: 16rpx 24rpx; background: #fff; border-radius: 16rpx; overflow: hidden; box-shadow: 0 2rpx 12rpx rgba(0,0,0,0.06); }

/* 加载更多 */
.load-more { padding: 24rpx; text-align: center; }
.load-more-text { font-size: 26rpx; color: #1a73e8; }

/* 弹窗内表单 */
.modal-form { padding: 24rpx; }
</style>