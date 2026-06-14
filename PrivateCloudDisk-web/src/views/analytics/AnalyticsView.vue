<template>
  <div class="space-y-4 sm:space-y-6">
    <PageHeader
      title="数据分析"
      description="存储使用趋势、流量统计与用户行为分析"
      :breadcrumbs="[{ label: '数据分析', icon: 'fa fa-bar-chart' }]"
      :stats="overviewStats"
    />

    <div class="grid grid-cols-1 gap-4 lg:grid-cols-2">
      <!-- 存储趋势 -->
      <SimpleChart
        title="存储使用趋势"
        :data="storageTrendData"
        :period="period"
        :periods="periodOptions"
        chart-color="bg-primary"
        @period-change="period = $event"
      />

      <!-- 流量趋势 -->
      <SimpleChart
        title="流量趋势"
        :data="trafficTrendData"
        :period="period"
        :periods="periodOptions"
        chart-color="bg-info"
        @period-change="period = $event"
      />
    </div>

    <!-- 文件类型分布 -->
    <div class="grid grid-cols-1 gap-4 lg:grid-cols-3">
      <div class="responsive-panel p-4 sm:p-5 lg:col-span-1">
        <h3 class="mb-4 text-base font-semibold text-neutral-700">文件类型分布</h3>
        <div class="space-y-3">
          <div v-for="ft in fileTypeDistribution" :key="ft.type" class="flex items-center gap-3">
            <i :class="ft.icon" :style="{ color: ft.color }" class="text-lg"></i>
            <div class="flex-1">
              <div class="flex justify-between text-sm">
                <span class="text-neutral-600">{{ ft.label }}</span>
                <span class="text-neutral-400">{{ ft.percent }}%</span>
              </div>
              <div class="mt-1 h-1.5 w-full rounded-full bg-neutral-200">
                <div class="h-1.5 rounded-full" :style="{ width: ft.percent + '%', backgroundColor: ft.color }"></div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 用户活跃度 -->
      <div class="responsive-panel p-4 sm:p-5 lg:col-span-2">
        <h3 class="mb-4 text-base font-semibold text-neutral-700">用户活跃度</h3>
        <SimpleChart
          title=""
          :data="userActivityData"
          :height="180"
          chart-color="bg-success"
        />
      </div>
    </div>

    <!-- 热门文件 -->
    <div class="responsive-panel overflow-hidden">
      <div class="flex items-center justify-between border-b border-neutral-100 px-4 py-3">
        <h3 class="text-base font-semibold text-neutral-700">热门文件</h3>
      </div>
      <div class="overflow-x-auto">
        <table class="w-full text-left text-sm">
          <thead>
            <tr class="border-b border-neutral-100 bg-neutral-50/50">
              <th class="px-4 py-3 text-xs font-medium uppercase tracking-wider text-neutral-400">文件名</th>
              <th class="px-4 py-3 text-xs font-medium uppercase tracking-wider text-neutral-400">下载次数</th>
              <th class="px-4 py-3 text-xs font-medium uppercase tracking-wider text-neutral-400">大小</th>
              <th class="px-4 py-3 text-xs font-medium uppercase tracking-wider text-neutral-400">热度</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(file, i) in hotFiles" :key="i" class="border-b border-neutral-50 transition-colors hover:bg-neutral-50/50">
              <td class="px-4 py-3">
                <div class="flex items-center gap-2">
                  <i :class="file.icon || 'fa fa-file'" class="text-neutral-400"></i>
                  <span class="text-neutral-700">{{ file.name }}</span>
                </div>
              </td>
              <td class="px-4 py-3 text-neutral-600">{{ file.downloads }}</td>
              <td class="px-4 py-3 text-neutral-500">{{ file.size }}</td>
              <td class="px-4 py-3">
                <div class="flex items-center gap-2">
                  <div class="h-1.5 w-20 rounded-full bg-neutral-200">
                    <div class="h-1.5 rounded-full bg-warning" :style="{ width: file.hotness + '%' }"></div>
                  </div>
                  <span class="text-xs text-neutral-400">{{ file.hotness }}%</span>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import PageHeader from '@/components/common/PageHeader.vue'
import SimpleChart from '@/components/common/SimpleChart.vue'

const period = ref('week')

const periodOptions = [
  { value: 'day', label: '日' },
  { value: 'week', label: '周' },
  { value: 'month', label: '月' },
  { value: 'year', label: '年' },
]

const overviewStats = computed(() => [
  { key: 'storage', title: '已用存储', value: '2.4', unit: 'TB', trend: '+15%', trendUp: false, progress: 68, progressLabel: '68% 已使用' },
  { key: 'traffic', title: '本月流量', value: '856', unit: 'GB', trend: '+23%', trendUp: true },
  { key: 'users', title: '活跃用户', value: '1,245', unit: '人', trend: '+8%', trendUp: true },
  { key: 'files', title: '文件总数', value: '45,821', unit: '个', trend: '+12%', trendUp: true },
])

const storageTrendData = computed(() => {
  const data = {
    day: [
      { label: '00:00', value: 45 }, { label: '04:00', value: 42 }, { label: '08:00', value: 48 },
      { label: '12:00', value: 55 }, { label: '16:00', value: 62 }, { label: '20:00', value: 58 },
    ],
    week: [
      { label: '周一', value: 120 }, { label: '周二', value: 145 }, { label: '周三', value: 132 },
      { label: '周四', value: 168 }, { label: '周五', value: 155 }, { label: '周六', value: 80 }, { label: '周日', value: 65 },
    ],
    month: [
      { label: '第1周', value: 520 }, { label: '第2周', value: 610 }, { label: '第3周', value: 580 }, { label: '第4周', value: 720 },
    ],
    year: [
      { label: '1月', value: 1800 }, { label: '2月', value: 1650 }, { label: '3月', value: 2100 },
      { label: '4月', value: 1950 }, { label: '5月', value: 2400 }, { label: '6月', value: 2200 },
    ],
  }
  return data[period.value] || data.week
})

const trafficTrendData = computed(() => {
  const data = {
    week: [
      { label: '周一', value: 85 }, { label: '周二', value: 92 }, { label: '周三', value: 78 },
      { label: '周四', value: 110 }, { label: '周五', value: 95 }, { label: '周六', value: 45 }, { label: '周日', value: 35 },
    ],
    month: [
      { label: '第1周', value: 380 }, { label: '第2周', value: 420 }, { label: '第3周', value: 350 }, { label: '第4周', value: 480 },
    ],
    year: [
      { label: '1月', value: 1200 }, { label: '2月', value: 1100 }, { label: '3月', value: 1500 },
      { label: '4月', value: 1400 }, { label: '5月', value: 1800 }, { label: '6月', value: 1600 },
    ],
    day: [
      { label: '00:00', value: 12 }, { label: '04:00', value: 8 }, { label: '08:00', value: 25 },
      { label: '12:00', value: 35 }, { label: '16:00', value: 42 }, { label: '20:00', value: 30 },
    ],
  }
  return data[period.value] || data.week
})

const userActivityData = computed(() => {
  const data = {
    week: [
      { label: '周一', value: 320 }, { label: '周二', value: 380 }, { label: '周三', value: 350 },
      { label: '周四', value: 420 }, { label: '周五', value: 390 }, { label: '周六', value: 210 }, { label: '周日', value: 180 },
    ],
    month: [
      { label: '第1周', value: 1200 }, { label: '第2周', value: 1450 }, { label: '第3周', value: 1350 }, { label: '第4周', value: 1580 },
    ],
    year: [
      { label: '1月', value: 4500 }, { label: '2月', value: 4200 }, { label: '3月', value: 5200 },
      { label: '4月', value: 4800 }, { label: '5月', value: 5600 }, { label: '6月', value: 5300 },
    ],
    day: [
      { label: '00:00', value: 45 }, { label: '04:00', value: 30 }, { label: '08:00', value: 120 },
      { label: '12:00', value: 180 }, { label: '16:00', value: 210 }, { label: '20:00', value: 150 },
    ],
  }
  return data[period.value] || data.week
})

const fileTypeDistribution = [
  { type: 'document', label: '文档', icon: 'fa fa-file-word-o', color: '#2B7FFF', percent: 35 },
  { type: 'image', label: '图片', icon: 'fa fa-file-image-o', color: '#FF6B6B', percent: 28 },
  { type: 'video', label: '视频', icon: 'fa fa-file-video-o', color: '#FFD93D', percent: 18 },
  { type: 'audio', label: '音频', icon: 'fa fa-file-audio-o', color: '#6BCB77', percent: 8 },
  { type: 'archive', label: '压缩包', icon: 'fa fa-file-archive-o', color: '#9B59B6', percent: 6 },
  { type: 'other', label: '其他', icon: 'fa fa-file-o', color: '#95A5A6', percent: 5 },
]

const hotFiles = [
  { name: '2025年度报告.pptx', icon: 'fa fa-file-powerpoint-o text-danger', downloads: 2345, size: '12.5 MB', hotness: 98 },
  { name: '产品设计规范.pdf', icon: 'fa fa-file-pdf-o text-danger', downloads: 1890, size: '8.2 MB', hotness: 85 },
  { name: '团队合影.jpg', icon: 'fa fa-file-image-o text-warning', downloads: 1567, size: '4.1 MB', hotness: 72 },
  { name: '项目计划.xlsx', icon: 'fa fa-file-excel-o text-success', downloads: 1234, size: '2.8 MB', hotness: 65 },
  { name: '开发文档.zip', icon: 'fa fa-file-archive-o text-purple-500', downloads: 987, size: '45.3 MB', hotness: 52 },
]

onMounted(() => {
  // 实际项目中在此调用 API 获取数据
  // analyticsStore.fetchOverview()
  // analyticsStore.fetchStorageTrend()
  // etc.
})
</script>