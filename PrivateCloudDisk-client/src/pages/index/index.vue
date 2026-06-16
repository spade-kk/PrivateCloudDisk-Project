<template>
  <view class="home-page">
    <!-- 顶部搜索栏 -->
    <view class="search-bar" @click="goSearch">
      <u-icon name="search" size="36" color="#9aa0a6" />
      <text class="search-placeholder">搜索文件...</text>
    </view>

    <!-- 容量概览 -->
    <view class="quota-card" v-if="quota">
      <view class="quota-header flex-between">
        <text class="quota-title">存储空间</text>
        <text class="quota-text">
          {{ formatFileSize(quota.used_capacity || 0) }} / {{ formatFileSize(quota.total_capacity || 0) }}
        </text>
      </view>
      <u-line-progress
        :percentage="appStore.usagePercent"
        activeColor="#1a73e8"
        height="8"
        :showText="false"
      />
    </view>

    <!-- 快捷操作 -->
    <view class="quick-actions">
      <view class="action-item" @click="handleCreateFolder">
        <u-icon name="folder-add" size="44" color="#1a73e8" />
        <text class="action-text">新建文件夹</text>
      </view>
      <view class="action-item" @click="handleUploadFile">
        <u-icon name="upload" size="44" color="#34a853" />
        <text class="action-text">上传文件</text>
      </view>
      <view class="action-item" @click="goFavorites">
        <u-icon name="star" size="44" color="#fbbc04" />
        <text class="action-text">我的收藏</text>
      </view>
      <view class="action-item" @click="goTrash">
        <u-icon name="trash" size="44" color="#ea4335" />
        <text class="action-text">回收站</text>
      </view>
    </view>

    <!-- 面包屑导航 -->
    <view class="breadcrumb" v-if="!appStore.isRoot">
      <u-icon name="arrow-left" size="32" color="#1a73e8" @click="goBack" />
      <text class="breadcrumb-current">{{ appStore.currentNodeName }}</text>
    </view>

    <!-- 文件列表 -->
    <view class="file-list">
      <view
        class="file-item"
        v-for="item in nodeList"
        :key="item.node_id"
        @click="handleItemClick(item)"
        @longpress="handleLongPress(item)"
      >
        <!-- 文件夹 -->
        <template v-if="item.node_type === 'FOLDER'">
          <u-icon name="folder" size="44" color="#1a73e8" />
          <view class="file-info">
            <text class="file-name ellipsis">{{ item.node_name }}</text>
            <text class="file-meta">文件夹</text>
          </view>
        </template>
        <!-- 文件 -->
        <template v-else>
          <u-icon
            :name="getFileIcon(item.node_name)"
            size="44"
            :color="getFileIconColor(item.node_name)"
          />
          <view class="file-info">
            <text class="file-name ellipsis">{{ item.node_name }}</text>
            <text class="file-meta">{{ formatFileSize(item.node_size) }}</text>
          </view>
        </template>

        <u-icon name="arrow-right" size="28" color="#c4c7cc" />
      </view>

      <!-- 空状态 -->
      <u-empty
        v-if="!loading && nodeList.length === 0"
        text="当前目录为空"
        icon="folder"
      />

      <!-- 加载更多 -->
      <view class="load-more" v-if="hasMore">
        <u-loading-icon
          v-if="loadingMore"
          size="20"
          text="加载中..."
        />
        <text v-else class="load-more-text" @click="loadMore">点击加载更多</text>
      </view>
    </view>

    <!-- 操作菜单 (长按弹出) -->
    <u-action-sheet
      :show="menuShow"
      :actions="menuActions"
      @select="handleMenuSelect"
      @close="menuShow = false"
    />
  </view>
</template>

<script>
import { useAppStore } from '@/store/app'
import { useUserStore } from '@/store/user'
import { getRootNode, getChildrenPaged, createFolder } from '@/api/node'
import { getMyQuota } from '@/api/quota'
import { moveFileToTrash, moveFolderToTrash } from '@/api/trash'
import { formatFileSize, getFileIcon, getFileIconColor } from '@/utils/helper'
import { PAGE_SIZE } from '@/utils/const'

export default {
  data() {
    return {
      loading: false,
      loadingMore: false,
      nodeList: [],
      page: 1,
      hasMore: false,
      quota: null,

      // 操作菜单
      menuShow: false,
      selectedItem: null
    }
  },
  computed: {
    appStore() { return useAppStore() },
    userStore() { return useUserStore() },
    menuActions() {
      if (!this.selectedItem) return []
      const isFolder = this.selectedItem.node_type === 'FOLDER'
      return [
        { name: '重命名', value: 'rename' },
        { name: '移动', value: 'move' },
        { name: isFolder ? '移动到回收站' : '删除', value: 'trash' },
        ...(isFolder ? [] : [{ name: '下载', value: 'download' }])
      ]
    }
  },
  onShow() {
    // 未登录 → 跳转登录页
    if (!this.userStore.isLoggedIn) {
      uni.reLaunch({ url: '/pages/login/index' })
      return
    }
    this.init()
  },
  methods: {
    formatFileSize,
    getFileIcon,
    getFileIconColor,

    async init() {
      await Promise.all([this.loadRootNode(), this.loadQuota()])
      await this.loadNodeChildren()
    },

    /** 加载根目录 ID */
    async loadRootNode() {
      try {
        const res = await getRootNode()
        this.appStore.setRootNodeId(res.data.node_id)
      } catch (e) {
        console.error('获取根目录失败:', e)
      }
    },

    /** 加载配额 */
    async loadQuota() {
      try {
        const res = await getMyQuota()
        this.quota = res.data
        this.appStore.setQuota(res.data)
      } catch (e) {
        console.error('获取配额失败:', e)
      }
    },

    /** 加载子节点 */
    async loadNodeChildren(page = 1) {
      const nodeId = this.appStore.currentNodeId
      if (!nodeId) return

      this.loading = page === 1
      try {
        const res = await getChildrenPaged(nodeId, {
          page,
          pageSize: PAGE_SIZE,
          sortBy: 'name',
          sortOrder: 'asc'
        })
        const data = res.data
        if (page === 1) {
          this.nodeList = data.items || []
        } else {
          this.nodeList.push(...(data.items || []))
        }
        this.hasMore = (data.items?.length || 0) >= PAGE_SIZE
        this.page = page
      } catch (e) {
        console.error('加载文件列表失败:', e)
      } finally {
        this.loading = false
        this.loadingMore = false
      }
    },

    /** 点击节点 */
    handleItemClick(item) {
      if (item.node_type === 'FOLDER') {
        this.appStore.pushNode(item)
        this.loadNodeChildren(1)
      } else {
        // 打开文件详情 / 预览 (跳转到文件操作页面)
        uni.navigateTo({
          url: `/pages/file-detail/index?fileId=${item.node_id}&fileName=${item.node_name}`
        })
      }
    },

    /** 返回上级 */
    goBack() {
      this.appStore.popNode()
      this.loadNodeChildren(1)
    },

    /** 加载更多 */
    loadMore() {
      if (this.loadingMore || !this.hasMore) return
      this.loadingMore = true
      this.loadNodeChildren(this.page + 1)
    },

    /** 长按弹出菜单 */
    handleLongPress(item) {
      this.selectedItem = item
      this.menuShow = true
    },

    /** 操作菜单选择 */
    async handleMenuSelect(action) {
      const item = this.selectedItem
      if (!item) return

      switch (action.value) {
        case 'rename':
          this.showRenameDialog(item)
          break
        case 'trash':
          await this.trashItem(item)
          break
        case 'download':
          await this.downloadFile(item)
          break
        case 'move':
          this.showMoveDialog(item)
          break
      }
      this.menuShow = false
    },

    /** 新建文件夹 */
    showCreateFolderDialog() {
      uni.showModal({
        title: '新建文件夹',
        editable: true,
        placeholderText: '请输入文件夹名称',
        success: async (res) => {
          if (res.confirm && res.content) {
            try {
              await createFolder({
                node_id: this.appStore.currentNodeId,
                folder_name: res.content
              })
              uni.showToast({ title: '创建成功', icon: 'success' })
              this.loadNodeChildren(1)
            } catch (e) {
              // 错误已由 request.js 处理
            }
          }
        }
      })
    },

    /** 重命名 */
    showRenameDialog(item) {
      const isFolder = item.node_type === 'FOLDER'
      const currentName = isFolder ? item.node_name : item.node_name
      uni.showModal({
        title: '重命名',
        editable: true,
        placeholderText: '请输入新名称',
        content: currentName,
        success: async (res) => {
          if (res.confirm && res.content && res.content !== currentName) {
            try {
              const api = isFolder
                ? (await import('@/api/node')).renameNode
                : (await import('@/api/file')).renameFile
              const idKey = isFolder ? item.node_id : item.node_id
              await api(idKey, {
                [isFolder ? 'new_node_name' : 'file_new_name']: res.content
              })
              uni.showToast({ title: '重命名成功', icon: 'success' })
              this.loadNodeChildren(this.page)
            } catch (e) {}
          }
        }
      })
    },

    /** 移到回收站 */
    async trashItem(item) {
      const isFolder = item.node_type === 'FOLDER'
      uni.showModal({
        title: '确认删除',
        content: `确定将「${item.node_name}」移入回收站吗？`,
        success: async (res) => {
          if (res.confirm) {
            try {
              if (isFolder) {
                await moveFolderToTrash(item.node_id)
              } else {
                await moveFileToTrash(item.node_id)
              }
              uni.showToast({ title: '已移入回收站', icon: 'success' })
              this.loadNodeChildren(1)
            } catch (e) {}
          }
        }
      })
    },

    /** 显示移动对话框 */
    showMoveDialog(item) {
      // TODO: 弹出目录选择器
      uni.showToast({ title: '移动功能开发中', icon: 'none' })
    },

    /** 下载文件 */
    async downloadFile(item) {
      uni.navigateTo({
        url: `/pages/file-detail/index?fileId=${item.node_id}&fileName=${item.node_name}`
      })
    },

    /** 上传文件 */
    handleUploadFile() {
      uni.navigateTo({ url: '/pages/upload/index' })
    },

    /** 处理新建文件夹 */
    handleCreateFolder() {
      this.showCreateFolderDialog()
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
  padding-bottom: 40rpx;
}

.search-bar {
  display: flex;
  align-items: center;
  background: $bg-white;
  margin: 20rpx 24rpx;
  padding: 16rpx 24rpx;
  border-radius: 36rpx;
  box-shadow: $shadow-light;

  .search-placeholder {
    margin-left: 12rpx;
    color: $text-placeholder;
    font-size: 26rpx;
  }
}

.quota-card {
  background: $bg-white;
  margin: 0 24rpx 20rpx;
  padding: 20rpx 24rpx;
  border-radius: $radius-md;
  box-shadow: $shadow-light;

  .quota-header {
    margin-bottom: 12rpx;
  }

  .quota-title {
    font-size: 26rpx;
    font-weight: 600;
  }

  .quota-text {
    font-size: 24rpx;
    color: $text-secondary;
  }
}

.quick-actions {
  display: flex;
  justify-content: space-around;
  background: $bg-white;
  margin: 0 24rpx 20rpx;
  padding: 24rpx 0;
  border-radius: $radius-md;
  box-shadow: $shadow-light;

  .action-item {
    display: flex;
    flex-direction: column;
    align-items: center;

    .action-text {
      font-size: 22rpx;
      color: $text-regular;
      margin-top: 8rpx;
    }
  }
}

.breadcrumb {
  display: flex;
  align-items: center;
  padding: 16rpx 24rpx;
  background: $bg-white;
  margin: 0 24rpx 16rpx;
  border-radius: $radius-md;

  .breadcrumb-current {
    margin-left: 12rpx;
    font-size: 28rpx;
    font-weight: 600;
    color: $text-primary;
  }
}

.file-list {
  background: $bg-white;
  margin: 0 24rpx;
  border-radius: $radius-md;
  overflow: hidden;
  box-shadow: $shadow-light;
}

.file-item {
  display: flex;
  align-items: center;
  padding: 24rpx;
  border-bottom: 1rpx solid $border-color;

  &:last-child {
    border-bottom: none;
  }

  .file-info {
    flex: 1;
    margin-left: 20rpx;
    margin-right: 12rpx;

    .file-name {
      font-size: 28rpx;
      color: $text-primary;
    }

    .file-meta {
      font-size: 22rpx;
      color: $text-secondary;
      margin-top: 4rpx;
    }
  }
}

.load-more {
  padding: 24rpx;
  text-align: center;

  .load-more-text {
    color: $primary-color;
    font-size: 26rpx;
  }
}
</style>