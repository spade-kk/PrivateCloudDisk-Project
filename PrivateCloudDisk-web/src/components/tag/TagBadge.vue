<template>
  <span
    v-if="tag"
    class="inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-[10px] font-medium text-white whitespace-nowrap"
    :style="{ backgroundColor: tag.tag_color }"
    :title="tag.tag_name"
  >
    <i class="fa fa-tag text-[8px]"></i>
    <span class="max-w-[88px] overflow-hidden text-ellipsis whitespace-nowrap">{{ tag.tag_name }}</span>
  </span>
</template>

<script setup lang="ts">
import type { TagVO } from '@/api/modules/tags'

defineProps<{
  tag: TagVO
}>()
</script>
