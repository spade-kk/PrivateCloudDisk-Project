<template>
  <view class="file-item" @click="handleClick" @longpress="handleLongPress">
    <view class="file-icon">
      <u-icon :name="iconName" :size="44" :color="iconColor" />
    </view>
    <view class="file-info">
      <text class="file-name ellipsis">{{ node.node_name }}</text>
      <text class="file-meta">{{ metaText }}</text>
    </view>
    <view class="file-action">
      <slot name="action" />
      <u-icon v-if="node.node_type === 'FOLDER'" name="arrow-right" size="28" color="#c4c7cc" />
    </view>
  </view>
</template>

<script>
import { getFileIcon, getFileIconColor, formatFileSize } from '@/utils/helper'

export default {
  name: 'FileItem',
  props: {
    node: { type: Object, required: true }
  },
  emits: ['click', 'longpress'],
  computed: {
    iconName() {
      return this.node.node_type === 'FOLDER' ? 'folder' : getFileIcon(this.node.node_name)
    },
    iconColor() {
      return this.node.node_type === 'FOLDER' ? '#1a73e8' : getFileIconColor(this.node.node_name)
    },
    metaText() {
      if (this.node.node_type === 'FOLDER') return '文件夹'
      return formatFileSize(this.node.node_size || 0)
    }
  },
  methods: {
    handleClick() { this.$emit('click', this.node) },
    handleLongPress() { this.$emit('longpress', this.node) }
  }
}
</script>

<style lang="scss" scoped>
.file-item {
  display: flex;
  align-items: center;
  padding: 24rpx 32rpx;
  background: #fff;
  border-bottom: 1rpx solid #f0f0f0;
  &:active { background: #f5f8ff; }
}
.file-icon { margin-right: 24rpx; flex-shrink: 0; }
.file-info { flex: 1; min-width: 0; }
.file-name { font-size: 30rpx; color: #202124; display: block; }
.file-meta { font-size: 24rpx; color: #9aa0a6; margin-top: 4rpx; }
.file-action { flex-shrink: 0; display: flex; align-items: center; }
</style>