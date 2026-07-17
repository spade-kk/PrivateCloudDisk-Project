<!--
  ============================================================
  ArchiveTree.vue — 压缩包目录树组件
  ============================================================

  可复用的目录树组件，用于展示压缩包内的文件夹层级结构。
  特性：
    - 递归渲染目录树，支持无限层级嵌套
    - 文件夹可展开/折叠，默认展开第一层
    - 显示文件大小（自动格式化）、修改时间
    - 根据文件扩展名显示对应图标
    - 提供简洁的统计信息栏（总文件数、总目录数、总大小）

  接口：
    Props: treeData - ArchiveTreeData 类型，包含完整的目录树数据
    Events: 无（纯展示组件，不处理交互）
  ============================================================
-->
<template>
  <div class="archive-tree" v-if="treeData">
    <!-- ============================================================
         统计信息栏
         ============================================================ -->
    <div class="archive-tree-stats">
      <div class="stat-item">
        <i class="fa fa-folder-o"></i>
        <span class="stat-label">目录</span>
        <span class="stat-value">{{ treeData.totalDirs }}</span>
      </div>
      <div class="stat-item">
        <i class="fa fa-file-o"></i>
        <span class="stat-label">文件</span>
        <span class="stat-value">{{ treeData.totalFiles }}</span>
      </div>
      <div class="stat-item">
        <i class="fa fa-hdd-o"></i>
        <span class="stat-label">总大小</span>
        <span class="stat-value">{{ formatSize(treeData.totalSize) }}</span>
      </div>
    </div>

    <!-- ============================================================
         目录树主体
         ============================================================ -->
    <div class="archive-tree-body">
      <TreeNode
        v-for="(node, index) in treeData.tree.children"
        :key="index"
        :node="node"
        :depth="0"
      />
      <!-- 空压缩包提示 -->
      <div v-if="!treeData.tree.children || treeData.tree.children.length === 0" class="tree-empty">
        <i class="fa fa-inbox"></i>
        <p>该压缩包为空</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
// ============================================================
// ArchiveTree.vue — 压缩包目录树组件
// ============================================================
import { computed } from 'vue'
import TreeNode from './TreeNode.vue'
import type { ArchiveTreeData } from '@/api/modules/archivePreview'

// ============================================================
// Props
// ============================================================

const props = defineProps<{
  /** 目录树数据 */
  treeData: ArchiveTreeData | null
}>()

// ============================================================
// 工具函数
// ============================================================

/**
 * 格式化文件大小
 *
 * 根据字节数自动选择合适的单位（B/KB/MB/GB/TB）。
 * 保留 1 位小数，去除无效的 .0 后缀。
 */
function formatSize(bytes: number): string {
  if (bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const k = 1024
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  const value = bytes / Math.pow(k, i)
  const formatted = value.toFixed(1)
  // 去除 .0 后缀
  return formatted.endsWith('.0') ? `${formatted.slice(0, -2)} ${units[i]}` : `${formatted} ${units[i]}`
}
</script>

<style scoped>
/* ============================================================
   统计信息栏
   ============================================================ */
.archive-tree-stats {
  display: flex;
  gap: 24px;
  padding: 12px 16px;
  background: var(--color-bg-secondary, #f8f9fa);
  border-bottom: 1px solid var(--color-border, #e1e4e8);
  flex-shrink: 0;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--color-text-secondary, #586069);
}

.stat-item .fa {
  font-size: 14px;
  opacity: 0.7;
}

.stat-label {
  font-weight: 500;
}

.stat-value {
  font-weight: 700;
  color: var(--color-text-primary, #24292e);
  margin-left: 2px;
}

/* ============================================================
   目录树主体
   ============================================================ */
.archive-tree-body {
  flex: 1;
  overflow: auto;
  padding: 8px 0;
}

.tree-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 16px;
  color: var(--color-text-tertiary, #959da5);
}

.tree-empty .fa {
  font-size: 48px;
  margin-bottom: 12px;
  opacity: 0.4;
}

.tree-empty p {
  font-size: 14px;
  margin: 0;
}
</style>