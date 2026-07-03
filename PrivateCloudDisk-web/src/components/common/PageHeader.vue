<template>
  <div class="mb-4 sm:mb-6">
    <!-- 面包屑 -->
    <BreadcrumbNav v-if="breadcrumbs.length" :items="breadcrumbs" class="mb-3" />
    <!-- 标题栏 -->
    <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
      <div class="min-w-0">
        <h1 class="text-lg font-bold text-neutral-800 sm:text-xl">{{ title }}</h1>
        <p v-if="description" class="mt-1 text-sm text-neutral-400">{{ description }}</p>
      </div>
      <div v-if="$slots.actions" class="flex flex-wrap items-center gap-2">
        <slot name="actions"></slot>
      </div>
    </div>
    <!-- 统计卡片 -->
    <div v-if="stats.length" class="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
      <StatCard
        v-for="stat in stats"
        :key="stat.key"
        :title="stat.title"
        :value="stat.value"
        :unit="stat.unit"
        :description="stat.description"
        :trend="stat.trend"
        :trend-up="stat.trendUp"
        :progress="stat.progress"
        :progress-label="stat.progressLabel"
        :progress-color="stat.progressColor"
      />
    </div>
    <!-- 标签页 -->
    <div v-if="tabs.length" class="mt-4 -mx-1 overflow-x-auto border-b border-neutral-200 sm:mx-0">
      <div class="flex gap-1 whitespace-nowrap px-1 sm:px-0">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          @click="$emit('tab-change', tab.key)"
          :class="[
            'border-b-2 px-3 py-2.5 text-sm font-medium transition-colors sm:px-4',
            activeTab === tab.key
              ? 'border-primary text-primary'
              : 'border-transparent text-neutral-400 hover:text-neutral-600',
          ]"
        >
          <i v-if="tab.icon" :class="tab.icon" class="mr-1.5"></i>
          {{ tab.label }}
          <span v-if="tab.count !== undefined" class="ml-1.5 rounded-full bg-neutral-100 px-1.5 py-0.5 text-xs text-neutral-500">
            {{ tab.count }}
          </span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import BreadcrumbNav from './BreadcrumbNav.vue'
import StatCard from './StatCard.vue'

defineProps({
  title: { type: String, required: true },
  description: { type: String, default: '' },
  breadcrumbs: { type: Array, default: () => [] },
  stats: { type: Array, default: () => [] },
  tabs: { type: Array, default: () => [] },
  activeTab: { type: String, default: '' },
})
defineEmits(['tab-change'])
</script>