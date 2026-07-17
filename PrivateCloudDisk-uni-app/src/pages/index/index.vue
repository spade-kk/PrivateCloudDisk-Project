<template>
  <view class="home-page">
    <!-- 顶部渐变头部 -->
    <view class="page-header">
      <view class="header-top">
        <text class="header-title">私有云盘</text>
        <view class="header-actions">
          <view class="header-btn" @click="goSearch">
            <u-icon name="search" size="40" color="#fff" />
          </view>
        </view>
      </view>
      <!-- 搜索栏 -->
      <view class="search-bar" @click="goSearch">
        <u-icon name="search" size="34" color="#9A9AB0" />
        <text class="search-placeholder">搜索文件...</text>
      </view>
    </view>

    <!-- 容量概览卡片 -->
    <view class="quota-card" v-if="state.quota">
      <view class="quota-ring">
        <view class="ring-circle" :style="ringStyle">
          <text class="ring-percent">{{ usagePercent }}%</text>
        </view>
      </view>
      <view class="quota-info">
        <text class="quota-title">存储空间</text>
        <view class="quota-bar-wrap">
          <view class="quota-bar">
            <view class="quota-bar-fill" :style="{ width: usagePercent + '%' }" />
          </view>
        </view>
        <view class="quota-detail">
          <text class="quota-size">{{ formatFileSize(state.quota.used_capacity || 0) }} / {{ formatFileSize(state.quota.total_capacity || 0) }}</text>
          <text class="quota-files">{{ state.quota.file_count || 0 }} 个文件</text>
        </view>
      </view>
    </view>

    <!-- 快捷操作 -->
    <view class="quick-actions">
      <view class="action-item" @click="handleCreateFolder">
        <view class="action-icon action-icon--primary">
          <u-icon name="plus-circle" size="44" color="#4F6EF7" />
        </view>
        <text class="action-text">新建文件夹</text>
      </view>
      <view class="action-item" @click="handleUploadFile">
        <view class="action-icon action-icon--success">
          <u-icon name="upload" size="44" color="#00C48C" />
        </view>
        <text class="action-text">上传文件</text>
      </view>
      <view class="action-item" @click="goFavorites">
        <view class="action-icon action-icon--warning">
          <u-icon name="star" size="44" color="#FFB347" />
        </view>
        <text class="action-text">我的收藏</text>
      </view>
      <view class="action-item" @click="goTrash">
        <view class="action-icon action-icon--danger">
          <u-icon name="trash" size="44" color="#FF5C5C" />
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
    <view class="file-section">
      <view class="file-section-header" v-if="state.children.length > 0">
        <text class="file-section-title">文件列表</text>
        <text class="file-section-count">{{ state.children.length }} 项</text>
      </view>
      <view class="file-list">
        <FileItem
          v-for="(node, idx) in state.children"
          :key="node.node_id"
          :node="node"
          :style="{ '--item-index': idx }"
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
    const { state, isRoot, usagePercent, init, loadChildren, navigateTo, goBack, goHome, refresh } = useFileBrowser()
    const { requireAuth } = useUserAuth()

    return {
      state, isRoot, usagePercent,
      init, loadChildren, navigateTo, goBack, goHome, refresh,
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
    },
    ringStyle() {
      const pct = this.usagePercent || 0
      const angle = (pct / 100) * 360
      const color = pct > 80 ? '#FF5C5C' : pct > 60 ? '#FFB347' : '#4F6EF7'
      return {
        background: `conic-gradient(${color} ${angle}deg, #E8E9F0 ${angle}deg 360deg)`
      }
    }
  },
  onShow() {
    // 更新自定义 TabBar 选中状态
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({ currentIndex: 0 })
    }
    if (!this.requireAuth()) return
    this.init().then(() => this.loadChildren())
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
        this.loadChildren()
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
        await renameNode(this.selectedItem.node_id, this.renameValue.trim())
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
              const parentNodeId = this.state.nodeStack.length > 0
                ? this.state.nodeStack[this.state.nodeStack.length - 1].node_id
                : this.state.rootNodeId
              await createFolder(parentNodeId, res.content)
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
.home-page {
  min-height: 100vh;
  background: $color-bg-page;
  padding-bottom: 140rpx; /* 给自定义 tabbar 留空间 */
}

/* ========== 顶部渐变头部 ========== */
.page-header {
  background: $gradient-primary;
  padding-top: calc(var(--status-bar-height, 44px) + 16rpx);
  padding-bottom: 24rpx;
  border-radius: 0 0 40rpx 40rpx;
}

.header-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 32rpx 16rpx;
}

.header-title {
  font-size: $font-size-headline;
  font-weight: $font-weight-bold;
  color: $color-text-inverse;
  letter-spacing: 2rpx;
}

.header-actions {
  display: flex;
  gap: 16rpx;
}

.header-btn {
  width: 64rpx;
  height: 64rpx;
  border-radius: $radius-md;
  background: rgba(255, 255, 255, 0.15);
  @include flex-center;
  transition: all $transition-fast;

  &:active {
    background: rgba(255, 255, 255, 0.25);
    transform: scale(0.95);
  }
}

/* 搜索栏 */
.search-bar {
  display: flex;
  align-items: center;
  margin: 0 32rpx;
  padding: 18rpx 24rpx;
  background: rgba(255, 255, 255, 0.15);
  border-radius: $radius-full;
  border: 1rpx solid rgba(255, 255, 255, 0.2);
}

.search-placeholder {
  font-size: $font-size-body;
  color: rgba(255, 255, 255, 0.7);
  margin-left: 12rpx;
}

/* ========== 容量卡片 ========== */
.quota-card {
  display: flex;
  align-items: center;
  margin: -24rpx 24rpx 16rpx;
  padding: 28rpx 24rpx;
  background: $color-bg-card;
  border-radius: $card-radius;
  box-shadow: $shadow-lg;
}

.quota-ring {
  flex-shrink: 0;
  margin-right: 24rpx;
}

.ring-circle {
  width: 80rpx;
  height: 80rpx;
  border-radius: 50%;
  @include flex-center;
  padding: 8rpx;

  &::after {
    content: '';
    width: 64rpx;
    height: 64rpx;
    border-radius: 50%;
    background: #fff;
  }
}

.ring-percent {
  position: absolute;
  font-size: 20rpx;
  font-weight: $font-weight-semibold;
  color: $color-text-primary;
  z-index: 1;
}

.quota-info {
  flex: 1;
  min-width: 0;
}

.quota-title {
  font-size: $font-size-body;
  font-weight: $font-weight-medium;
  color: $color-text-primary;
  margin-bottom: 12rpx;
  display: block;
}

.quota-bar-wrap {
  margin-bottom: 8rpx;
}

.quota-bar {
  height: 6rpx;
  background: $color-bg-divider;
  border-radius: 3rpx;
  overflow: hidden;
}

.quota-bar-fill {
  height: 100%;
  background: $gradient-primary;
  border-radius: 3rpx;
  transition: width 0.3s ease;
}

.quota-detail {
  display: flex;
  justify-content: space-between;
}

.quota-size {
  font-size: $font-size-caption;
  color: $color-text-secondary;
}

.quota-files {
  font-size: $font-size-caption;
  color: $color-text-secondary;
}

/* ========== 快捷操作 ========== */
.quick-actions {
  display: flex;
  justify-content: space-around;
  padding: 28rpx 16rpx;
  margin: 0 24rpx;
  background: $color-bg-card;
  border-radius: $card-radius;
  box-shadow: $shadow-md;
}

.action-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  transition: transform $transition-fast;

  &:active {
    transform: scale(0.94);
  }
}

.action-icon {
  width: 88rpx;
  height: 88rpx;
  border-radius: 22rpx;
  @include flex-center;
  margin-bottom: 10rpx;
}

.action-icon--primary { background: $color-primary-lighter; }
.action-icon--success { background: $color-success-light; }
.action-icon--warning { background: $color-warning-light; }
.action-icon--danger  { background: $color-danger-light; }

.action-text {
  font-size: $font-size-caption;
  color: $color-text-regular;
  font-weight: $font-weight-medium;
}

/* ========== 文件列表区域 ========== */
.file-section {
  margin: 16rpx 24rpx;
}

.file-section-header {
  @include flex-between;
  padding: 0 8rpx 16rpx;
}

.file-section-title {
  font-size: $font-size-subtitle;
  font-weight: $font-weight-semibold;
  color: $color-text-primary;
}

.file-section-count {
  font-size: $font-size-body-sm;
  color: $color-text-secondary;
}

.file-list {
  background: $color-bg-card;
  border-radius: $card-radius;
  overflow: hidden;
  box-shadow: $shadow-md;
}

/* 弹窗内表单 */
.modal-form {
  padding: 24rpx;
}
</style>