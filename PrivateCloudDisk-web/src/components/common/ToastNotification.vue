<template>
  <Teleport to="body">
    <div
      class="pointer-events-none fixed right-0 top-20 z-50 flex w-full justify-end overflow-hidden px-4"
      aria-live="polite"
    >
      <div
        class="pointer-events-auto max-w-xs transform transition-all duration-300 ease-out"
        :class="visible ? 'translate-x-0 opacity-100' : 'translate-x-[calc(100%+2rem)] opacity-0'"
      >
      <div :class="['text-white px-4 py-3 rounded-lg shadow-lg flex items-center gap-2', bgClass]">
        <i :class="iconClass"></i>
        <span>{{ message }}</span>
      </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useToastStore } from '@/stores/toastStore'

const toastStore = useToastStore()
const visible = computed(() => toastStore.visible)
const message = computed(() => toastStore.message)
const type = computed(() => toastStore.type)

const bgClass = computed(() => {
  switch (type.value) {
    case 'success': return 'bg-success'
    case 'error': return 'bg-danger'
    case 'warning': return 'bg-warning text-neutral-700'
    default: return 'bg-primary'
  }
})

const iconClass = computed(() => {
  switch (type.value) {
    case 'success': return 'fa fa-check-circle'
    case 'error': return 'fa fa-exclamation-circle'
    case 'warning': return 'fa fa-exclamation-triangle'
    default: return 'fa fa-info-circle'
  }
})
</script>
