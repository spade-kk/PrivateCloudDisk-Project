<template>
  <Teleport to="body">
    <div
      v-if="visible"
      class="fixed inset-0 z-50 flex items-start justify-center bg-black/40 pt-[15vh]"
      @click.self="close"
    >
      <div class="w-full max-w-lg rounded-xl bg-white shadow-2xl">
        <!-- 搜索输入 -->
        <div class="flex items-center border-b px-4 py-3">
          <i class="fa fa-search text-neutral-400"></i>
          <input
            ref="inputRef"
            v-model="query"
            type="text"
            placeholder="输入命令搜索..."
            class="ml-3 flex-1 text-sm text-neutral-700 placeholder-neutral-400 outline-none"
            @keydown="onKeydown"
          />
          <kbd class="rounded border border-neutral-200 bg-neutral-100 px-1.5 py-0.5 text-xs text-neutral-400">ESC</kbd>
        </div>
        <!-- 命令列表 -->
        <div class="max-h-80 overflow-y-auto p-2">
          <div v-if="filteredCommands.length === 0" class="py-8 text-center text-sm text-neutral-400">
            未找到匹配的命令
          </div>
          <div v-for="(group, gi) in filteredCommands" :key="gi">
            <p class="px-3 py-2 text-xs font-medium uppercase tracking-wider text-neutral-400">{{ group.label }}</p>
            <button
              v-for="cmd in group.items"
              :key="cmd.key"
              @click="execute(cmd)"
              :class="[
                'flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left text-sm transition-colors',
                activeIndex === getFlatIndex(gi, group.items.indexOf(cmd)) ? 'bg-primary/10 text-primary' : 'text-neutral-700 hover:bg-neutral-50',
              ]"
            >
              <i :class="cmd.icon" class="w-5 text-center"></i>
              <span class="flex-1">{{ cmd.label }}</span>
              <kbd v-if="cmd.shortcut" class="rounded border border-neutral-200 bg-neutral-50 px-1.5 py-0.5 text-xs text-neutral-400">{{ cmd.shortcut }}</kbd>
            </button>
          </div>
        </div>
        <!-- 底部提示 -->
        <div class="border-t px-4 py-2 text-xs text-neutral-400">
          <span class="mr-4"><kbd class="rounded bg-neutral-100 px-1 py-0.5">↑↓</kbd> 导航</span>
          <span class="mr-4"><kbd class="rounded bg-neutral-100 px-1 py-0.5">Enter</kbd> 选择</span>
          <span><kbd class="rounded bg-neutral-100 px-1 py-0.5">Esc</kbd> 关闭</span>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'

const props = defineProps({
  visible: { type: Boolean, default: false },
  commands: { type: Array, default: () => [] },
})
const emit = defineEmits(['close'])
const router = useRouter()

const query = ref('')
const activeIndex = ref(0)
const inputRef = ref(null)

// 默认命令组
const defaultCommands = [
  {
    label: '导航',
    items: [
      { key: 'dashboard', label: '我的网盘', icon: 'fa fa-cloud', shortcut: 'G D', action: () => router.push('/') },
      { key: 'search', label: '文件搜索', icon: 'fa fa-search', shortcut: 'G S', action: () => router.push('/search') },
      { key: 'starred', label: '收藏夹', icon: 'fa fa-star', action: () => router.push('/starred') },
      { key: 'notifications', label: '消息中心', icon: 'fa fa-bell', action: () => router.push('/notifications') },
      { key: 'shares', label: '分享管理', icon: 'fa fa-share-alt', action: () => router.push('/shares') },
      { key: 'trash', label: '回收站', icon: 'fa fa-trash', action: () => router.push('/trash') },
      { key: 'transfers', label: '传输记录', icon: 'fa fa-exchange', action: () => router.push('/transfers') },
      { key: 'profile', label: '个人中心', icon: 'fa fa-user-circle', action: () => router.push('/profile') },
      { key: 'admin', label: '管理后台', icon: 'fa fa-cog', shortcut: 'G A', action: () => router.push('/admin') },
      { key: 'security', label: '安全中心', icon: 'fa fa-shield', action: () => router.push('/security') },
      { key: 'analytics', label: '数据分析', icon: 'fa fa-bar-chart', action: () => router.push('/analytics') },
      { key: 'settings', label: '系统设置', icon: 'fa fa-sliders', action: () => router.push('/settings') },
    ],
  },
  {
    label: '操作',
    items: [
      { key: 'new-folder', label: '新建文件夹', icon: 'fa fa-folder-plus', action: () => {} },
      { key: 'upload', label: '上传文件', icon: 'fa fa-upload', action: () => {} },
      { key: 'refresh', label: '刷新页面', icon: 'fa fa-refresh', shortcut: 'F5', action: () => location.reload() },
    ],
  },
]

const allCommands = computed(() => {
  if (props.commands.length) return props.commands
  return defaultCommands
})

const filteredCommands = computed(() => {
  if (!query.value.trim()) return allCommands.value
  const q = query.value.toLowerCase()
  return allCommands.value
    .map(group => ({
      ...group,
      items: group.items.filter(cmd =>
        cmd.label.toLowerCase().includes(q) || cmd.key.toLowerCase().includes(q)
      ),
    }))
    .filter(group => group.items.length > 0)
})

const flatCommands = computed(() => {
  const result = []
  filteredCommands.value.forEach(group => {
    group.items.forEach(item => result.push(item))
  })
  return result
})

function getFlatIndex(gi, ii) {
  let count = 0
  for (let i = 0; i < gi; i++) {
    count += filteredCommands.value[i].items.length
  }
  return count + ii
}

function onKeydown(e) {
  if (e.key === 'ArrowDown') {
    e.preventDefault()
    activeIndex.value = Math.min(activeIndex.value + 1, flatCommands.value.length - 1)
  } else if (e.key === 'ArrowUp') {
    e.preventDefault()
    activeIndex.value = Math.max(activeIndex.value - 1, 0)
  } else if (e.key === 'Enter') {
    e.preventDefault()
    if (flatCommands.value[activeIndex.value]) {
      execute(flatCommands.value[activeIndex.value])
    }
  } else if (e.key === 'Escape') {
    close()
  }
}

function execute(cmd) {
  if (cmd.action) cmd.action()
  close()
}

function close() {
  query.value = ''
  activeIndex.value = 0
  emit('close')
}

watch(() => props.visible, async (val) => {
  if (val) {
    await nextTick()
    inputRef.value?.focus()
  }
})
</script>