<template>
  <div class="space-y-4 sm:space-y-6">
    <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
      <h1 class="text-xl font-bold sm:text-2xl">传输记录</h1>
      <div class="flex flex-wrap items-center gap-2">
        <button
          v-if="throughputStore.points.length > 0"
          class="touch-button rounded-lg border border-neutral-200 px-3 py-2 text-xs font-medium text-neutral-500 transition hover:border-neutral-300 hover:bg-neutral-100"
          @click="throughputStore.clearHistory()"
        >
          <i class="fa fa-line-chart mr-1"></i>清除图表
        </button>
        <button
          v-if="store.records.length > 0"
          class="touch-button rounded-lg border border-neutral-200 px-3 py-2 text-xs font-medium text-neutral-500 transition hover:border-red-200 hover:bg-red-50 hover:text-red-500"
          @click="handleClearCompleted"
        >
          <i class="fa fa-trash-o mr-1"></i>清除已完成/失败
        </button>
      </div>
    </div>

    <!-- 实时吞吐量图表
    <ThroughputChart /> -->

    <!-- 进行中的传输 -->
    <div v-if="ongoingRecords.length > 0" class="overflow-hidden rounded-lg bg-white shadow-card">
      <div class="border-b bg-amber-50/50 px-4 py-3">
        <span class="text-sm font-semibold text-amber-700">
          <i class="fa fa-spinner fa-pulse mr-2"></i>进行中 ({{ ongoingRecords.length }})
        </span>
      </div>
      <TransferTable :records="ongoingRecords" />
    </div>

    <!-- 历史记录 -->
    <div class="overflow-hidden rounded-lg bg-white shadow-card">
      <div class="border-b bg-neutral-50 px-4 py-3 flex items-center justify-between">
        <span class="text-sm font-semibold text-neutral-600">
          <i class="fa fa-history mr-2"></i>传输历史 ({{ completedRecords.length }})
        </span>
        <span class="text-xs text-neutral-400">{{ store.records.length }} 条记录</span>
      </div>
      <!-- 空 -->
      <div v-if="store.records.length === 0" class="py-16 text-center">
        <i class="fa fa-exchange text-4xl text-neutral-200 mb-3 block"></i>
        <p class="text-neutral-400 text-sm">暂无传输记录</p>
        <p class="text-neutral-300 text-xs mt-1">上传或下载文件后，记录将显示在这里</p>
      </div>

      <div v-else-if="completedRecords.length === 0" class="py-10 text-center text-neutral-400 text-sm">
        暂无已完成的传输记录
      </div>

      <TransferTable v-else :records="completedRecords" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useTransferStore } from '@/stores/transferStore'
import { useThroughputStore } from '@/stores/throughputStore'
import TransferTable from '@/components/transfer/TransferTable.vue'
import ThroughputChart from '@/components/transfer/ThroughputChart.vue'

const store = useTransferStore()
const throughputStore = useThroughputStore()

const ongoingRecords = computed(() =>
  store.records.filter(r =>
    r.status === 'uploading' || r.status === 'downloading' || r.status === 'processing'
  )
)

const completedRecords = computed(() =>
  store.records.filter(r =>
    r.status === 'completed' || r.status === 'failed' || r.status === 'cancelled'
  )
)

function handleClearCompleted() {
  if (confirm('确定要清除所有已完成/失败的传输记录吗？正在进行的记录不受影响。')) {
    store.clearCompleted()
  }
}
</script>