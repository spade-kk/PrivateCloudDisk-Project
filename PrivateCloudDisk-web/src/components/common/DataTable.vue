<template>
  <div class="responsive-panel overflow-hidden">
    <!-- 工具栏 -->
    <div v-if="showToolbar" class="flex flex-col gap-3 border-b border-neutral-100 p-3 sm:flex-row sm:items-center sm:justify-between sm:p-4">
      <div class="flex flex-wrap items-center gap-2">
        <slot name="toolbar-left">
          <span v-if="title" class="text-base font-semibold text-neutral-700">{{ title }}</span>
        </slot>
      </div>
      <div class="flex flex-wrap items-center gap-2">
        <slot name="toolbar-right">
          <!-- 搜索 -->
          <div v-if="searchable" class="relative">
            <i class="fa fa-search absolute left-3 top-1/2 -translate-y-1/2 text-xs text-neutral-400"></i>
            <input
              v-model="searchQuery"
              type="text"
              placeholder="搜索..."
              class="rounded-lg border border-neutral-200 py-1.5 pl-8 pr-3 text-sm focus:border-primary focus:outline-none"
              @input="onSearch"
            />
          </div>
          <!-- 刷新 -->
          <button v-if="showRefresh" @click="$emit('refresh')" class="icon-button" title="刷新">
            <i class="fa fa-refresh"></i>
          </button>
        </slot>
      </div>
    </div>

    <!-- 表格 -->
    <div class="overflow-x-auto">
      <table class="w-full text-left text-sm">
        <thead>
          <tr class="border-b border-neutral-100 bg-neutral-50/50">
            <th v-if="selectable" class="w-10 px-4 py-3">
              <input
                type="checkbox"
                :checked="allSelected"
                @change="$emit('select-all', $event.target.checked)"
                class="h-4 w-4 rounded border-neutral-300 text-primary focus:ring-primary"
              />
            </th>
            <th
              v-for="col in columns"
              :key="col.key"
              :class="['px-4 py-3 text-xs font-medium uppercase tracking-wider text-neutral-400', col.sortable ? 'cursor-pointer select-none hover:text-primary' : '', col.class || '']"
              :style="{ width: col.width, minWidth: col.minWidth }"
              @click="col.sortable && onSort(col.key)"
            >
              <div class="flex items-center gap-1">
                {{ col.label }}
                <span v-if="col.sortable && sortKey === col.key" class="text-primary">
                  <i :class="sortOrder === 'asc' ? 'fa fa-sort-up' : 'fa fa-sort-down'"></i>
                </span>
                <i v-else-if="col.sortable" class="fa fa-sort text-neutral-300 text-xs"></i>
              </div>
            </th>
            <th v-if="$slots.actions" class="w-20 px-4 py-3 text-right text-xs font-medium uppercase tracking-wider text-neutral-400">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td :colspan="colSpan" class="py-16 text-center">
              <LoadingSpinner />
              <p class="mt-3 text-sm text-neutral-400">加载中...</p>
            </td>
          </tr>
          <tr v-else-if="!data.length">
            <td :colspan="colSpan" class="py-16 text-center">
              <i class="fa fa-inbox text-3xl text-neutral-300"></i>
              <p class="mt-3 text-sm text-neutral-400">{{ emptyText }}</p>
            </td>
          </tr>
          <tr
            v-for="(row, index) in data"
            :key="row.id || index"
            :class="[
              'border-b border-neutral-50 transition-colors hover:bg-neutral-50/50',
              selectedIds?.has(row.id) ? 'bg-primary/5' : '',
            ]"
            @click="$emit('row-click', row, index)"
          >
            <td v-if="selectable" class="px-4 py-3" @click.stop>
              <input
                type="checkbox"
                :checked="selectedIds?.has(row.id)"
                @change="$emit('select', row, $event.target.checked)"
                class="h-4 w-4 rounded border-neutral-300 text-primary focus:ring-primary"
              />
            </td>
            <td v-for="col in columns" :key="col.key" :class="['px-4 py-3', col.cellClass || '']">
              <slot :name="`cell-${col.key}`" :row="row" :value="getNestedValue(row, col.key)" :index="index">
                <!-- 默认渲染 -->
                <span v-if="col.type === 'status'">
                  <StatusBadge :status="getNestedValue(row, col.key)" :statusMap="col.statusMap" />
                </span>
                <span v-else-if="col.type === 'date'">
                  {{ formatDate(getNestedValue(row, col.key)) }}
                </span>
                <span v-else-if="col.type === 'size'">
                  {{ formatSize(getNestedValue(row, col.key)) }}
                </span>
                <span v-else class="text-neutral-700">
                  {{ getNestedValue(row, col.key) }}
                </span>
              </slot>
            </td>
            <td v-if="$slots.actions" class="px-4 py-3 text-right" @click.stop>
              <slot name="actions" :row="row" :index="index"></slot>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 分页 -->
    <div v-if="showPagination && totalPages > 1" class="flex items-center justify-between border-t border-neutral-100 px-4 py-3">
      <span class="text-xs text-neutral-400">共 {{ total }} 条记录</span>
      <div class="flex items-center gap-1">
        <button
          :disabled="currentPage <= 1"
          @click="changePage(currentPage - 1)"
          class="rounded-lg px-2 py-1 text-sm text-neutral-400 hover:bg-neutral-100 disabled:opacity-30"
        >
          <i class="fa fa-angle-left"></i>
        </button>
        <button
          v-for="p in visiblePages"
          :key="p"
          @click="changePage(p)"
          :class="['rounded-lg px-3 py-1 text-sm', p === currentPage ? 'bg-primary text-white' : 'text-neutral-600 hover:bg-neutral-100']"
        >
          {{ p }}
        </button>
        <button
          :disabled="currentPage >= totalPages"
          @click="changePage(currentPage + 1)"
          class="rounded-lg px-2 py-1 text-sm text-neutral-400 hover:bg-neutral-100 disabled:opacity-30"
        >
          <i class="fa fa-angle-right"></i>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import LoadingSpinner from './LoadingSpinner.vue'
import StatusBadge from './StatusBadge.vue'

const props = defineProps({
  columns: { type: Array, required: true },
  data: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  emptyText: { type: String, default: '暂无数据' },
  title: { type: String, default: '' },
  showToolbar: { type: Boolean, default: true },
  searchable: { type: Boolean, default: false },
  showRefresh: { type: Boolean, default: false },
  selectable: { type: Boolean, default: false },
  selectedIds: { type: Set, default: undefined },
  showPagination: { type: Boolean, default: true },
  total: { type: Number, default: 0 },
  currentPage: { type: Number, default: 1 },
  pageSize: { type: Number, default: 20 },
})
const emit = defineEmits(['refresh', 'select-all', 'select', 'row-click', 'page-change', 'sort-change', 'search'])

const searchQuery = ref('')
const sortKey = ref('')
const sortOrder = ref('asc')

const allSelected = computed(() => {
  if (!props.selectedIds || !props.data.length) return false
  return props.data.every(row => props.selectedIds.has(row.id))
})

const totalPages = computed(() => Math.ceil(props.total / props.pageSize) || 1)

const colSpan = computed(() => {
  let span = props.columns.length
  if (props.selectable) span++
  if (emit('has-actions') !== undefined) span++ // 简化处理
  return span || 5
})

const visiblePages = computed(() => {
  const pages = []
  const tp = totalPages.value
  const cp = props.currentPage
  let start = Math.max(1, cp - 2)
  let end = Math.min(tp, cp + 2)
  if (end - start < 4) {
    if (start === 1) end = Math.min(tp, start + 4)
    else start = Math.max(1, end - 4)
  }
  for (let i = start; i <= end; i++) pages.push(i)
  return pages
})

function getNestedValue(obj, path) {
  return path.split('.').reduce((o, k) => (o || {})[k], obj)
}

function formatDate(val) {
  if (!val) return '-'
  try {
    return new Date(val).toLocaleString('zh-CN', {
      year: 'numeric', month: '2-digit', day: '2-digit',
      hour: '2-digit', minute: '2-digit',
    })
  } catch { return val }
}

function formatSize(val) {
  if (!val && val !== 0) return '-'
  const bytes = Number(val)
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB'
  if (bytes < 1073741824) return (bytes / 1048576).toFixed(1) + ' MB'
  return (bytes / 1073741824).toFixed(2) + ' GB'
}

function changePage(page) {
  if (page < 1 || page > totalPages.value) return
  emit('page-change', page)
}

function onSort(key) {
  if (sortKey.value === key) {
    sortOrder.value = sortOrder.value === 'asc' ? 'desc' : 'asc'
  } else {
    sortKey.value = key
    sortOrder.value = 'asc'
  }
  emit('sort-change', { key: sortKey.value, order: sortOrder.value })
}

function onSearch() {
  emit('search', searchQuery.value)
}
</script>