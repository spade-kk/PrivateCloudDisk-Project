<template>
  <aside class="directory-card" aria-labelledby="share-directory-title">
    <div class="directory-heading">
      <div>
        <p class="directory-kicker">当前位置</p>
        <h2 id="share-directory-title">
          <i class="fa fa-sitemap" aria-hidden="true"></i>
          目录导航
        </h2>
      </div>
      <div class="directory-actions">
        <button
          type="button"
          class="icon-action"
          :aria-expanded="expanded"
          aria-controls="share-directory-tree"
          :title="expanded ? '收起目录树' : '展开目录树'"
          @click="expanded = !expanded"
        >
          <i :class="expanded ? 'fa fa-angle-up' : 'fa fa-angle-down'" aria-hidden="true"></i>
        </button>
        <button type="button" class="icon-action" title="返回全部资源" @click="$emit('close')">
          <i class="fa fa-times" aria-hidden="true"></i>
          <span class="sr-only">返回全部资源</span>
        </button>
      </div>
    </div>

    <Transition name="tree-expand">
      <div v-show="expanded" id="share-directory-tree" class="tree-scroll" tabindex="0">
        <nav class="directory-tree" aria-label="分享文件夹层级">
          <button
            v-for="node in nodes"
            :key="`${node.kind}-${node.id}`"
            type="button"
            class="tree-node"
            :class="[`tree-node--${node.kind}`, { 'tree-node--current': node.isCurrent }]"
            :style="{ '--tree-depth': node.depth }"
            :aria-current="node.isCurrent ? 'page' : undefined"
            :disabled="node.isCurrent"
            @click="activateNode(node)"
          >
            <span v-if="node.depth > 0" class="tree-branch" aria-hidden="true"></span>
            <i
              :class="node.kind === 'current' ? 'fa fa-folder-open' : 'fa fa-folder-o'"
              aria-hidden="true"
            ></i>
            <span class="tree-label">{{ node.name }}</span>
            <i
              v-if="node.kind === 'ancestor' || node.kind === 'child'"
              class="fa fa-angle-right tree-chevron"
              aria-hidden="true"
            ></i>
          </button>
        </nav>
      </div>
    </Transition>

    <p class="directory-hint">
      <i class="fa fa-arrows-h" aria-hidden="true"></i>
      深层目录可横向滚动，名称始终完整显示
    </p>
  </aside>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import {
  buildShareDirectoryNodes,
  type ShareBreadcrumbItem,
  type ShareDirectoryChild,
  type ShareDirectoryNode,
} from '@/utils/shareNavigation'

const props = defineProps<{
  breadcrumbs: ShareBreadcrumbItem[]
  children: ShareDirectoryChild[]
}>()

const emit = defineEmits<{
  close: []
  navigate: [item: ShareBreadcrumbItem]
  child: [item: ShareDirectoryChild]
}>()

const expanded = ref(true)
const nodes = computed(() => buildShareDirectoryNodes(props.breadcrumbs, props.children))

function activateNode(node: ShareDirectoryNode) {
  if (node.isCurrent) return
  if (node.kind === 'root') {
    emit('close')
    return
  }
  if (node.kind === 'child') {
    emit('child', { id: node.id, name: node.name })
    return
  }
  emit('navigate', { id: node.id, name: node.name })
}
</script>

<style scoped>
.directory-card {
  overflow: hidden;
  border: 1px solid rgba(226, 232, 240, 0.9);
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 14px 40px rgba(15, 23, 42, 0.07);
}

.directory-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 18px 18px 13px;
}

.directory-kicker {
  margin: 0 0 3px;
  color: #94a3b8;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.directory-heading h2 {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  color: #172033;
  font-size: 15px;
  font-weight: 750;
}

.directory-heading h2 i { color: #165dff; }
.directory-actions { display: flex; gap: 4px; }

.icon-action {
  display: inline-flex;
  width: 40px;
  height: 40px;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 12px;
  background: transparent;
  color: #64748b;
  cursor: pointer;
  transition: background-color 160ms ease, color 160ms ease, transform 160ms ease;
}

.icon-action:hover { background: #eff5ff; color: #165dff; transform: translateY(-1px); }
.icon-action:focus-visible { outline: 3px solid rgba(22, 93, 255, 0.24); outline-offset: 2px; }

.tree-scroll {
  max-height: min(54vh, 560px);
  overflow: auto;
  padding: 2px 10px 8px;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
}

.tree-scroll:focus-visible { outline: 3px solid rgba(22, 93, 255, 0.18); outline-offset: -3px; }
.directory-tree { min-width: max-content; padding: 2px 0; }

.tree-node {
  --indent-size: 20px;
  position: relative;
  display: grid;
  grid-template-columns: 18px minmax(190px, 1fr) 14px;
  width: max(100%, calc(245px + var(--tree-depth) * var(--indent-size)));
  min-height: 44px;
  align-items: center;
  gap: 9px;
  margin: 2px 0;
  padding: 8px 10px 8px calc(10px + var(--tree-depth) * var(--indent-size));
  border: 1px solid transparent;
  border-radius: 12px;
  background: transparent;
  color: #526078;
  text-align: left;
  cursor: pointer;
  /* AUDIT FIX [2.4]（需求四-3）: 目录层级是辅助信息，字号需低于中间文件名并随视口平滑调整。 */
  font-size: clamp(11px, 0.35vw + 9.5px, 12.5px);
  transition: background-color 160ms ease, border-color 160ms ease, color 160ms ease, transform 160ms ease;
}

.tree-node:hover:not(:disabled) {
  border-color: #dbe7ff;
  background: #f4f7ff;
  color: #164ecc;
  transform: translateX(2px);
}

.tree-node:focus-visible { outline: 3px solid rgba(22, 93, 255, 0.22); outline-offset: 1px; }
.tree-node > .fa-folder-o,
.tree-node > .fa-folder-open { color: #e9a009; font-size: 16px; }

.tree-node--current {
  border-color: #d7e4ff;
  background: linear-gradient(105deg, #edf4ff, #f8fbff);
  color: #133f9c;
  font-weight: 700;
  cursor: default;
}

.tree-node--root { color: #334155; font-weight: 650; }
.tree-label { min-width: 0; white-space: normal; overflow-wrap: anywhere; word-break: break-word; line-height: 1.45; }
.tree-chevron { color: #a8b3c5; justify-self: end; }

.tree-branch {
  position: absolute;
  left: calc(8px + (var(--tree-depth) - 1) * var(--indent-size));
  top: -4px;
  width: 13px;
  height: 27px;
  border-bottom: 1px solid #d9e1ec;
  border-left: 1px solid #d9e1ec;
  border-bottom-left-radius: 7px;
}

.directory-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  padding: 11px 16px 14px;
  border-top: 1px solid #f0f3f8;
  color: #94a3b8;
  font-size: 11px;
  line-height: 1.5;
}

.tree-expand-enter-active,
.tree-expand-leave-active { transition: max-height 220ms ease, opacity 180ms ease; overflow: hidden; }
.tree-expand-enter-from,
.tree-expand-leave-to { max-height: 0; opacity: 0; }
.tree-expand-enter-to,
.tree-expand-leave-from { max-height: 600px; opacity: 1; }

@media (prefers-reduced-motion: reduce) {
  .icon-action,
  .tree-node,
  .tree-expand-enter-active,
  .tree-expand-leave-active { transition: none; }
}

@media (max-width: 767px) {
  .tree-node {
    --indent-size: 17px;
    min-height: 40px;
    font-size: clamp(10.5px, 2.8vw, 12px);
  }
}
</style>
