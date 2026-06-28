<template>
  <Teleport to="body">
    <!-- 遮罩层 -->
    <div
      v-if="visible"
      class="fixed inset-0 z-50"
      @click="close"
      @contextmenu.prevent="close"
      @keydown="onKeydown"
    ></div>

    <!-- 菜单本体 -->
    <Transition name="menu-scale">
      <div
        v-if="visible"
        ref="menuRef"
        class="context-menu fixed z-[100] flex min-w-[180px] max-w-[280px] flex-col rounded-xl py-1.5"
        :style="menuStyle"
        @contextmenu.prevent
      >
        <template v-for="(item, idx) in items" :key="idx">
          <!-- 分隔线 -->
          <div
            v-if="item.type === 'separator'"
            class="mx-3 my-1 h-px bg-neutral-200/70 dark:bg-neutral-700/50"
          ></div>

          <!-- 分组标题 -->
          <div
            v-else-if="item.type === 'header'"
            class="px-3 pb-0.5 pt-1 text-[11px] font-semibold uppercase tracking-wider text-neutral-400"
          >
            {{ item.label }}
          </div>

          <!-- 普通菜单项 -->
          <button
            v-else
            :disabled="item.disabled"
            :class="[
              'context-menu-item group mx-1.5 flex items-center gap-3 rounded-lg px-3 py-2 text-left text-sm transition-colors',
              item.disabled
                ? 'cursor-not-allowed opacity-40'
                : item.danger
                  ? 'text-red-600 hover:bg-red-50 dark:text-red-400 dark:hover:bg-red-500/10'
                  : 'text-neutral-700 hover:bg-neutral-100 dark:text-neutral-200 dark:hover:bg-white/8',
              item.highlighted ? 'bg-neutral-100 dark:bg-white/8' : '',
            ]"
            @click="!item.disabled && handleClick(item)"
            @mouseenter="highlightedIndex = idx"
          >
            <!-- 图标 -->
            <span class="flex h-5 w-5 shrink-0 items-center justify-center text-sm" :class="item.danger ? 'text-red-500 dark:text-red-400' : 'text-neutral-400'">
              <i v-if="item.icon" :class="item.icon"></i>
            </span>

            <!-- 标签 -->
            <span class="flex-1 truncate">{{ item.label }}</span>

            <!-- 快捷键 -->
            <span v-if="item.shortcut" class="ml-4 shrink-0 text-[11px] text-neutral-400">{{ item.shortcut }}</span>
          </button>
        </template>

        <div v-if="items.length === 0" class="px-4 py-3 text-center text-xs text-neutral-400">
          无可用操作
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'

export interface ContextMenuItem {
  label: string
  key?: string
  icon?: string
  shortcut?: string
  disabled?: boolean
  danger?: boolean
  type?: 'item' | 'separator' | 'header'
  highlighted?: boolean
  data?: any
}

const props = defineProps({
  visible: { type: Boolean, default: false },
  x: { type: Number, default: 0 },
  y: { type: Number, default: 0 },
  items: { type: Array as () => ContextMenuItem[], default: () => [] },
})

const emit = defineEmits(['close', 'action'])

const menuRef = ref<HTMLElement | null>(null)
const placementX = ref(0)
const placementY = ref(0)
const highlightedIndex = ref(-1)

// 计算最终菜单位置（防溢出）
const menuStyle = computed(() => ({
  left: placementX.value + 'px',
  top: placementY.value + 'px',
}))

// 可见时计算位置
watch(
  () => props.visible,
  (val) => {
    if (val) {
      highlightedIndex.value = -1
      nextTick(() => {
        if (!menuRef.value) return
        const rect = menuRef.value.getBoundingClientRect()
        let finalX = props.x
        let finalY = props.y

        if (rect.right > window.innerWidth - 8) {
          finalX = window.innerWidth - rect.width - 8
        }
        if (rect.bottom > window.innerHeight - 8) {
          finalY = window.innerHeight - rect.height - 8
        }
        if (finalX < 8) finalX = 8
        if (finalY < 8) finalY = 8

        placementX.value = finalX
        placementY.value = finalY
      })
    }
  },
)

function close() {
  emit('close')
}

function handleClick(item: ContextMenuItem) {
  emit('action', item)
  close()
}

// 键盘导航
function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') {
    close()
    return
  }
  const clickable = props.items.filter((i) => !i.type || i.type === 'item')
  if (clickable.length === 0) return

  if (e.key === 'ArrowDown') {
    e.preventDefault()
    highlightedIndex.value = (highlightedIndex.value + 1) % clickable.length
  } else if (e.key === 'ArrowUp') {
    e.preventDefault()
    highlightedIndex.value =
      (highlightedIndex.value - 1 + clickable.length) % clickable.length
  } else if (e.key === 'Enter') {
    e.preventDefault()
    const idx = highlightedIndex.value
    if (idx >= 0 && idx < clickable.length) {
      const item = clickable[idx]
      if (!item.disabled) {
        handleClick(item)
      }
    }
  }
}
</script>

<style>
/* 全局动画 — 不能 scoped，因为 Teleport 到 body */
.menu-scale-enter-active {
  transition: opacity 120ms ease-out, transform 120ms ease-out;
}
.menu-scale-leave-active {
  transition: opacity 80ms ease-in, transform 80ms ease-in;
}
.menu-scale-enter-from {
  opacity: 0;
  transform: scale(0.95) translateY(-4px);
}
.menu-scale-leave-to {
  opacity: 0;
  transform: scale(0.97) translateY(-2px);
}
</style>

<style scoped>
.context-menu {
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid rgba(0, 0, 0, 0.08);
  box-shadow:
    0 0 0 0.5px rgba(0, 0, 0, 0.04),
    0 4px 32px rgba(0, 0, 0, 0.12),
    0 1px 3px rgba(0, 0, 0, 0.06);
}

@media (prefers-color-scheme: dark) {
  .context-menu {
    background: rgba(30, 30, 30, 0.9);
    backdrop-filter: blur(20px) saturate(180%);
    -webkit-backdrop-filter: blur(20px) saturate(180%);
    border: 1px solid rgba(255, 255, 255, 0.08);
    box-shadow:
      0 0 0 0.5px rgba(255, 255, 255, 0.03),
      0 4px 32px rgba(0, 0, 0, 0.4),
      0 1px 3px rgba(0, 0, 0, 0.2);
  }
}

.context-menu-item:active:not(:disabled) {
  transform: scale(0.97);
}
</style>