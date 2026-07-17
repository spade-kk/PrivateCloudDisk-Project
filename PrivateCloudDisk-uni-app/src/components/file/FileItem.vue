<template>
  <view
    class="file-item"
    :class="{ 'file-item--folder': node.node_type === 'FOLDER' }"
    @click="handleClick"
    @longpress="handleLongPress"
  >
    <view class="file-icon" :class="iconClass">
      <u-icon :name="iconName" :size="40" :color="iconColor" />
    </view>
    <view class="file-info">
      <text class="file-name ellipsis">{{ node.node_name }}</text>
      <text class="file-meta">{{ metaText }}</text>
    </view>
    <view class="file-action">
      <slot name="action" />
      <u-icon
        v-if="node.node_type === 'FOLDER'"
        name="arrow-right"
        size="28"
        color="#C0C0D0"
      />
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
      return this.node.node_type === 'FOLDER' ? '#4F6EF7' : getFileIconColor(this.node.node_name)
    },
    iconClass() {
      return this.node.node_type === 'FOLDER' ? 'file-icon--folder' : 'file-icon--file'
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
  padding: 22rpx 28rpx;
  background: $color-bg-card;
  transition: background $transition-fast;
  animation: itemSlideIn 0.3s ease both;
  animation-delay: calc(var(--item-index, 0) * 0.04s);

  &:not(:last-child) {
    @include hairline-bottom($color-bg-divider);
  }

  &:active {
    background: $color-bg-hover;
  }
}

@keyframes itemSlideIn {
  from {
    opacity: 0;
    transform: translateY(8rpx);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.file-icon {
  width: 72rpx;
  height: 72rpx;
  border-radius: 18rpx;
  @include flex-center;
  margin-right: 20rpx;
  flex-shrink: 0;
}

.file-icon--folder {
  background: $color-primary-lighter;
}

.file-icon--file {
  background: $color-bg-page;
}

.file-info {
  flex: 1;
  min-width: 0;
}

.file-name {
  font-size: $font-size-body;
  color: $color-text-primary;
  display: block;
  font-weight: $font-weight-medium;
}

.file-meta {
  font-size: $font-size-caption;
  color: $color-text-secondary;
  margin-top: 4rpx;
}

.file-action {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 12rpx;
}
</style>