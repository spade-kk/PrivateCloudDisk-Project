<template>
  <view class="file-list-page">
    <u-loading-page v-if="loading" />
    <view v-else class="content">
      <view v-if="nodes.length === 0" class="empty">
        <u-empty text="此目录为空" mode="list" />
      </view>
      <view v-else class="node-list">
        <view
          v-for="node in nodes"
          :key="node.node_id"
          class="node-item"
          @click="handleNodeClick(node)"
        >
          <view class="node-left">
            <u-icon
              :name="node.is_folder ? 'folder' : 'file-text'"
              size="40"
              :color="node.is_folder ? '#1a73e8' : '#666'"
            />
            <text class="node-name">{{ node.name }}</text>
          </view>
          <view class="node-right">
            <text v-if="!node.is_folder" class="node-size">{{ formatFileSize(node.size || 0) }}</text>
            <u-icon name="arrow-right" size="28" color="#ccc" />
          </view>
        </view>
      </view>
    </view>
    <view class="bottom-bar">
      <u-button type="primary" @click="handleUpload">上传文件</u-button>
      <u-button @click="handleNewFolder">新建文件夹</u-button>
    </view>
  </view>
</template>

<script>
import { getChildrenPaged } from '@/api/node'
import { formatFileSize } from '@/utils/helper'

export default {
  data() {
    return {
      loading: true,
      folderId: null,
      folderName: '',
      nodes: [],
      cursor: null,
      hasMore: true
    }
  },
  onLoad(options) {
    this.folderId = options.folder_id || null
    this.folderName = options.folder_name || '文件目录'
    uni.setNavigationBarTitle({ title: this.folderName })
    this.loadNodes()
  },
  methods: {
    async loadNodes() {
      this.loading = true
      try {
        const res = await getChildrenPaged({
          node_id: this.folderId,
          limit: 50,
          cursor: this.cursor
        })
        this.nodes = res.nodes || res.data || []
        this.cursor = res.cursor || null
        this.hasMore = !!res.cursor
      } catch (e) {
        uni.showToast({ title: '加载失败', icon: 'none' })
      } finally {
        this.loading = false
      }
    },
    handleNodeClick(node) {
      if (node.is_folder) {
        uni.navigateTo({
          url: `/pages/file-list/index?folder_id=${node.node_id}&folder_name=${encodeURIComponent(node.name)}`
        })
      } else {
        uni.navigateTo({
          url: `/pages/file-detail/index?node_id=${node.node_id}`
        })
      }
    },
    handleUpload() {
      uni.navigateTo({ url: '/pages/upload/index' })
    },
    handleNewFolder() {
      uni.showModal({
        title: '新建文件夹',
        editable: true,
        placeholderText: '请输入文件夹名称',
        success: async (res) => {
          if (res.confirm && res.content) {
            try {
              const { createNode } = await import('@/api/node')
              await createNode({
                parent_id: this.folderId,
                name: res.content,
                is_folder: true
              })
              uni.showToast({ title: '创建成功' })
              this.loadNodes()
            } catch (e) {
              uni.showToast({ title: '创建失败', icon: 'none' })
            }
          }
        }
      })
    },
    formatFileSize
  }
}
</script>

<style lang="scss" scoped>
.file-list-page {
  min-height: 100vh;
  background: #f5f5f5;
  padding-bottom: 100rpx;
}
.content {
  padding: 20rpx;
}
.empty {
  margin-top: 200rpx;
}
.node-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff;
  padding: 24rpx 20rpx;
  border-radius: 12rpx;
  margin-bottom: 12rpx;
}
.node-left {
  display: flex;
  align-items: center;
  flex: 1;
  overflow: hidden;
}
.node-name {
  margin-left: 16rpx;
  font-size: 28rpx;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.node-right {
  display: flex;
  align-items: center;
}
.node-size {
  font-size: 24rpx;
  color: #999;
  margin-right: 8rpx;
}
.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  gap: 20rpx;
  padding: 20rpx;
  background: #fff;
  border-top: 1px solid #eee;
}
</style>