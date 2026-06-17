<template>
  <Teleport to="body">
    <div
      v-if="visible"
      ref="menuRef"
      class="fixed z-50 min-w-[160px] rounded-lg border border-neutral-200 bg-white py-1 shadow-xl"
      :style="{ left: x + 'px', top: y + 'px' }"
    >
      <button
        v-for="item in items"
        :key="item.key"
        @click="handleClick(item)"
        :class="[
          'flex w-full items-center gap-3 px-4 py-2.5 text-left text-sm transition-colors',
          item.danger ? 'text-danger hover:bg-danger/5' : 'text-neutral-700 hover:bg-neutral-50',
        ]"
      >
        <i v-if="item.icon" :class="item.icon" class="w-4 text-center text-sm"></i>
        <span>{{ item.label }}</span>
        <span v-if="item.shortcut" class="ml-auto text-xs text-neutral-400">{{ item.shortcut }}</span>
      </button>
      <div v-if="items.length === 0" class="px-4 py-3 text-xs text-neutral-400 text-center">
        无可用操作
      </div>
    </div>
  </Teleport>
  <!-- 遮罩层 -->
  <Teleport to="body">
    <div
      v-if="visible"
      class="fixed inset-0 z-40"
      @click="close"
      @contextmenu.prevent="close"
    ></div>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  x: { type: Number, default: 0 },
  y: { type: Number, default: 0 },
  items: { type: Array, default: () => [] },
})
const emit = defineEmits(['close', 'action'])

const menuRef = ref(null)

function close() {
  emit('close')
}

function handleClick(item) {
  emit('action', item)
  close()
}

// 调整菜单位置防止溢出
watch(() => props.visible, (val) => {
  if (val) {
    setTimeout(() => {
      if (!menuRef.value) return
      const rect = menuRef.value.getBoundingClientRect()
      if (rect.right > window.innerWidth) {
        menuRef.value.style.left = (window.innerWidth - rect.width - 10) + 'px'
      }
      if (rect.bottom > window.innerHeight) {
        menuRef.value.style.top = (window.innerHeight - rect.height - 10) + 'px'
      }
    }, 0)
  }
})
</script>