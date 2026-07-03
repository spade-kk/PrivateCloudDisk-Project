<template>
  <div class="space-selector px-2 py-2">
    <!-- 当前空间信息 -->
    <div
      class="flex cursor-pointer items-center rounded-lg px-3 py-2 transition hover:bg-primary/5"
      @click="open = !open"
    >
      <i :class="spaceStore.spaceTypeIcon" class="h-5 w-5 shrink-0 text-primary"></i>
      <div v-if="!collapsed" class="ml-3 min-w-0 flex-1">
        <p class="truncate text-sm font-medium text-neutral-800">
          {{ spaceStore.currentSpace?.spaceName || '选择空间' }}
        </p>
        <p class="truncate text-xs text-neutral-400">
          {{ spaceStore.spaceTypeLabel }}
        </p>
      </div>
      <i
        v-if="!collapsed"
        class="fa fa-chevron-down ml-1 shrink-0 text-xs text-neutral-400 transition-transform"
        :class="open ? 'rotate-180' : ''"
      ></i>
    </div>

    <!-- 下拉空间列表 -->
    <div
      v-if="open && !collapsed"
      class="mt-1 max-h-64 overflow-y-auto rounded-lg border bg-white py-1 shadow-lg"
    >
      <div
        v-for="space in spaceStore.spaces"
        :key="space.spaceId"
        class="flex cursor-pointer items-center px-3 py-2 transition hover:bg-primary/5"
        :class="space.spaceId === spaceStore.currentSpaceId ? 'bg-primary/10' : ''"
        @click="selectSpace(space.spaceId)"
      >
        <i :class="getSpaceIcon(space.spaceType)" class="h-4 w-4 shrink-0 text-primary"></i>
        <div class="ml-3 min-w-0 flex-1">
          <p class="truncate text-sm text-neutral-700">{{ space.spaceName }}</p>
          <p class="text-xs text-neutral-400">{{ getSpaceTypeLabel(space.spaceType) }}</p>
        </div>
        <i
          v-if="space.spaceId === spaceStore.currentSpaceId"
          class="fa fa-check ml-2 text-xs text-primary"
        ></i>
      </div>

      <!-- 创建新空间按钮 -->
      <div class="mt-1 border-t pt-1">
        <button
          class="flex w-full items-center px-3 py-2 text-sm text-primary transition hover:bg-primary/5"
          @click="$emit('create')"
        >
          <i class="fa fa-plus mr-2"></i>
          创建新空间
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useSpaceStore } from '@/stores/spaceStore'

defineProps<{
  collapsed?: boolean
}>()

defineEmits<{
  create: []
}>()

const spaceStore = useSpaceStore()
const open = ref(false)

function selectSpace(spaceId: string) {
  spaceStore.switchSpace(spaceId)
  open.value = false
}

function getSpaceIcon(type: string): string {
  const icons: Record<string, string> = {
    personal: 'i-lucide-user',
    enterprise: 'i-lucide-building-2',
    public: 'i-lucide-globe',
    team: 'i-lucide-users',
  }
  return icons[type] || 'i-lucide-folder'
}

function getSpaceTypeLabel(type: string): string {
  const labels: Record<string, string> = {
    personal: '个人空间',
    enterprise: '企业空间',
    public: '公共空间',
    team: '团队空间',
  }
  return labels[type] || '空间'
}
</script>