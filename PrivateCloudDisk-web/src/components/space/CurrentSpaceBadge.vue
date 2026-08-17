<template>
  <div
    class="inline-flex min-w-0 items-center gap-2 rounded-full border border-primary/15 bg-primary/5 px-3 py-1.5 text-xs text-neutral-600"
    :title="`当前操作空间：${spaceStore.currentSpaceName}`"
  >
    <i :class="spaceStore.spaceTypeIcon" class="shrink-0 text-primary"></i>
    <span class="text-neutral-400">当前空间</span>
    <strong class="max-w-48 truncate font-medium text-neutral-700">
      {{ spaceStore.currentSpaceName }}
    </strong>
    <i v-if="spaceStore.switching" class="fa fa-spinner fa-spin text-primary"></i>
  </div>
</template>

<script setup lang="ts">
import { useSpaceStore } from '@/stores/spaceStore'

const spaceStore = useSpaceStore()
</script>
