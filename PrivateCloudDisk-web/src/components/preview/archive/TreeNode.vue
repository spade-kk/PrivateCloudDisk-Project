<!--
  ============================================================
  TreeNode.vue — 递归目录树节点组件
  ============================================================

  递归渲染压缩包目录树中的单个节点。
  特性：
    - 目录节点：可展开/折叠，显示子节点
    - 文件节点：显示文件图标、大小、修改时间
    - 根据文件扩展名匹配 Font Awesome 图标
    - 支持键盘导航（Enter 展开/折叠目录）
    - 缩进式层级显示，视觉清晰

  接口：
    Props: node - ArchiveTreeNode 类型，depth - 当前深度
    Events: 无（纯展示组件）
  ============================================================
-->
<template>
  <div class="tree-node">
    <!-- ============================================================
         目录节点
         ============================================================ -->
    <div
      v-if="node.type === 'directory'"
      class="node-row node-directory"
      :style="{ paddingLeft: depth * 20 + 12 + 'px' }"
      @click="toggleExpand"
      @keydown.enter="toggleExpand"
      role="button"
      tabindex="0"
      :aria-expanded="isExpanded"
      :aria-label="node.name + ' 目录'"
    >
      <!-- 展开/折叠箭头 -->
      <span class="node-arrow" :class="{ expanded: isExpanded }">
        <i class="fa fa-chevron-right"></i>
      </span>
      <!-- 目录图标 -->
      <span class="node-icon">
        <i :class="isExpanded ? 'fa fa-folder-open' : 'fa fa-folder'"></i>
      </span>
      <!-- 目录名称 -->
      <span class="node-name">{{ node.name }}</span>
    </div>

    <!-- 子节点（展开时显示） -->
    <div v-if="node.type === 'directory' && isExpanded" class="node-children">
      <TreeNode
        v-for="(child, index) in node.children"
        :key="index"
        :node="child"
        :depth="depth + 1"
      />
    </div>

    <!-- ============================================================
         文件节点
         ============================================================ -->
    <div
      v-else
      class="node-row node-file"
      :style="{ paddingLeft: depth * 20 + 12 + 'px' }"
      :aria-label="node.name + ' 文件'"
    >
      <!-- 文件图标（基于扩展名） -->
      <span class="node-arrow node-arrow-placeholder"></span>
      <span class="node-icon">
        <i :class="getFileIcon(node.name)"></i>
      </span>
      <!-- 文件名称 -->
      <span class="node-name">{{ node.name }}</span>
      <!-- 文件元数据 -->
      <span class="node-meta">
        <span class="node-size" v-if="node.size !== undefined">{{ formatSize(node.size) }}</span>
        <span class="node-modified" v-if="node.modified">{{ formatDate(node.modified) }}</span>
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
// ============================================================
// TreeNode.vue — 递归目录树节点组件
// ============================================================
import { ref } from 'vue'
import type { ArchiveTreeNode } from '@/api/modules/archivePreview'

// ============================================================
// Props
// ============================================================

const props = defineProps<{
  /** 树节点数据 */
  node: ArchiveTreeNode
  /** 当前深度（用于缩进计算） */
  depth: number
}>()

// ============================================================
// 状态
// ============================================================

/** 目录是否展开（默认深度 0-1 展开，更深层折叠） */
const isExpanded = ref(props.depth < 1)

// ============================================================
// 交互
// ============================================================

/** 切换目录展开/折叠 */
function toggleExpand(): void {
  isExpanded.value = !isExpanded.value
}

// ============================================================
// 工具函数
// ============================================================

/** 根据文件扩展名获取 Font Awesome 图标 */
function getFileIcon(fileName: string): string {
  const ext = fileName.split('.').pop()?.toLowerCase() || ''

  const iconMap: Record<string, string> = {
    // 图片
    jpg: 'fa fa-file-image-o', jpeg: 'fa fa-file-image-o', png: 'fa fa-file-image-o',
    gif: 'fa fa-file-image-o', webp: 'fa fa-file-image-o', svg: 'fa fa-file-image-o',
    bmp: 'fa fa-file-image-o', ico: 'fa fa-file-image-o', tiff: 'fa fa-file-image-o',
    // 视频
    mp4: 'fa fa-file-video-o', webm: 'fa fa-file-video-o', mov: 'fa fa-file-video-o',
    avi: 'fa fa-file-video-o', mkv: 'fa fa-file-video-o', flv: 'fa fa-file-video-o',
    // 音频
    mp3: 'fa fa-file-audio-o', wav: 'fa fa-file-audio-o', flac: 'fa fa-file-audio-o',
    aac: 'fa fa-file-audio-o', ogg: 'fa fa-file-audio-o',
    // 文档
    pdf: 'fa fa-file-pdf-o',
    doc: 'fa fa-file-word-o', docx: 'fa fa-file-word-o',
    xls: 'fa fa-file-excel-o', xlsx: 'fa fa-file-excel-o', csv: 'fa fa-file-excel-o',
    ppt: 'fa fa-file-powerpoint-o', pptx: 'fa fa-file-powerpoint-o',
    // 代码
    js: 'fa fa-file-code-o', ts: 'fa fa-file-code-o', jsx: 'fa fa-file-code-o',
    tsx: 'fa fa-file-code-o', html: 'fa fa-file-code-o', css: 'fa fa-file-code-o',
    json: 'fa fa-file-code-o', xml: 'fa fa-file-code-o', py: 'fa fa-file-code-o',
    java: 'fa fa-file-code-o', cpp: 'fa fa-file-code-o', c: 'fa fa-file-code-o',
    go: 'fa fa-file-code-o', rs: 'fa fa-file-code-o', rb: 'fa fa-file-code-o',
    php: 'fa fa-file-code-o', swift: 'fa fa-file-code-o', kt: 'fa fa-file-code-o',
    sql: 'fa fa-file-code-o', sh: 'fa fa-file-code-o', yaml: 'fa fa-file-code-o',
    yml: 'fa fa-file-code-o', toml: 'fa fa-file-code-o',
    // 文本
    txt: 'fa fa-file-text-o', md: 'fa fa-file-text-o', log: 'fa fa-file-text-o',
    // 压缩包
    zip: 'fa fa-file-archive-o', rar: 'fa fa-file-archive-o', '7z': 'fa fa-file-archive-o',
    tar: 'fa fa-file-archive-o', gz: 'fa fa-file-archive-o', bz2: 'fa fa-file-archive-o',
    xz: 'fa fa-file-archive-o', tgz: 'fa fa-file-archive-o', iso: 'fa fa-file-archive-o',
  }

  return iconMap[ext] || 'fa fa-file-o'
}

/** 格式化文件大小 */
function formatSize(bytes: number): string {
  if (bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const k = 1024
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  const value = bytes / Math.pow(k, i)
  const formatted = value.toFixed(1)
  return formatted.endsWith('.0') ? `${formatted.slice(0, -2)} ${units[i]}` : `${formatted} ${units[i]}`
}

/** 格式化日期（仅显示日期部分） */
function formatDate(isoString: string): string {
  if (!isoString) return ''
  try {
    const date = new Date(isoString)
    return date.toLocaleDateString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    })
  } catch {
    return isoString
  }
}
</script>

<style scoped>
/* ============================================================
   节点行
   ============================================================ */
.node-row {
  display: flex;
  align-items: center;
  height: 32px;
  padding-right: 12px;
  cursor: default;
  transition: background 0.1s ease;
  user-select: none;
}

.node-row:hover {
  background: var(--color-bg-hover, rgba(0, 0, 0, 0.04));
}

.node-directory {
  cursor: pointer;
}

.node-directory:focus-visible {
  outline: 2px solid var(--color-primary, #0366d6);
  outline-offset: -2px;
}

/* ============================================================
   展开/折叠箭头
   ============================================================ */
.node-arrow {
  width: 16px;
  height: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: transform 0.15s ease;
  color: var(--color-text-tertiary, #959da5);
  font-size: 10px;
}

.node-arrow.expanded {
  transform: rotate(90deg);
}

.node-arrow-placeholder {
  visibility: hidden;
}

/* ============================================================
   节点图标
   ============================================================ */
.node-icon {
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-right: 6px;
  font-size: 14px;
  color: var(--color-text-secondary, #586069);
}

.node-directory .node-icon {
  color: #f0ad4e;
}

/* ============================================================
   节点名称
   ============================================================ */
.node-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  color: var(--color-text-primary, #24292e);
  line-height: 1.4;
}

/* ============================================================
   文件元数据（大小、修改时间）
   ============================================================ */
.node-meta {
  display: flex;
  gap: 16px;
  flex-shrink: 0;
  margin-left: 12px;
}

.node-size {
  font-size: 12px;
  color: var(--color-text-tertiary, #959da5);
  font-family: 'SF Mono', 'Menlo', 'Monaco', 'Consolas', monospace;
  min-width: 60px;
  text-align: right;
}

.node-modified {
  font-size: 12px;
  color: var(--color-text-tertiary, #959da5);
  min-width: 90px;
  text-align: right;
}

/* ============================================================
   子节点容器
   ============================================================ */
.node-children {
  /* 子节点由递归 TreeNode 渲染 */
}
</style>