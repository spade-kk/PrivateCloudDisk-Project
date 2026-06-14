<template>
  <div class="space-y-4 sm:space-y-6">
    <PageHeader
      title="系统设置"
      description="管理偏好、通知、外观、安全与数据"
      :breadcrumbs="[{ label: '系统设置', icon: 'fa fa-sliders' }]"
      :tabs="settingsTabs"
      :active-tab="activeTab"
      @tab-change="activeTab = $event"
    />

    <!-- 偏好设置 -->
    <div v-if="activeTab === 'preferences'" class="responsive-panel p-4 sm:p-6">
      <h3 class="mb-6 text-base font-semibold text-neutral-700">偏好设置</h3>
      <div class="space-y-5">
        <div class="flex items-center justify-between">
          <div>
            <p class="text-sm font-medium text-neutral-700">默认视图</p>
            <p class="text-xs text-neutral-400">文件列表的默认显示方式</p>
          </div>
          <select v-model="settingsStore.preferences.defaultView" @change="savePref('defaultView', settingsStore.preferences.defaultView)" class="rounded-lg border border-neutral-200 px-3 py-1.5 text-sm">
            <option value="grid">网格视图</option>
            <option value="list">列表视图</option>
          </select>
        </div>
        <div class="flex items-center justify-between">
          <div>
            <p class="text-sm font-medium text-neutral-700">每页显示数量</p>
            <p class="text-xs text-neutral-400">文件列表中每页显示的项目数</p>
          </div>
          <select v-model="settingsStore.preferences.itemsPerPage" @change="savePref('itemsPerPage', settingsStore.preferences.itemsPerPage)" class="rounded-lg border border-neutral-200 px-3 py-1.5 text-sm">
            <option :value="20">20</option>
            <option :value="50">50</option>
            <option :value="100">100</option>
          </select>
        </div>
        <div class="flex items-center justify-between">
          <div>
            <p class="text-sm font-medium text-neutral-700">自动播放</p>
            <p class="text-xs text-neutral-400">预览视频时自动开始播放</p>
          </div>
          <label class="relative inline-flex cursor-pointer items-center">
            <input type="checkbox" v-model="settingsStore.preferences.autoPlay" @change="savePref('autoPlay', settingsStore.preferences.autoPlay)" class="peer sr-only" />
            <div class="h-6 w-11 rounded-full bg-neutral-200 after:absolute after:left-[2px] after:top-[2px] after:h-5 after:w-5 after:rounded-full after:bg-white after:transition-all peer-checked:bg-primary peer-checked:after:translate-x-full"></div>
          </label>
        </div>
        <div class="flex items-center justify-between">
          <div>
            <p class="text-sm font-medium text-neutral-700">语言</p>
            <p class="text-xs text-neutral-400">界面显示语言</p>
          </div>
          <select v-model="settingsStore.preferences.language" @change="savePref('language', settingsStore.preferences.language)" class="rounded-lg border border-neutral-200 px-3 py-1.5 text-sm">
            <option value="zh-CN">简体中文</option>
            <option value="en-US">English</option>
          </select>
        </div>
      </div>
    </div>

    <!-- 通知设置 -->
    <div v-if="activeTab === 'notifications'" class="responsive-panel p-4 sm:p-6">
      <h3 class="mb-6 text-base font-semibold text-neutral-700">通知设置</h3>
      <div class="space-y-5">
        <div v-for="notif in notificationItems" :key="notif.key" class="flex items-center justify-between">
          <div>
            <p class="text-sm font-medium text-neutral-700">{{ notif.label }}</p>
            <p class="text-xs text-neutral-400">{{ notif.description }}</p>
          </div>
          <label class="relative inline-flex cursor-pointer items-center">
            <input type="checkbox" v-model="settingsStore.notificationSettings[notif.key]" @change="saveNotification" class="peer sr-only" />
            <div class="h-6 w-11 rounded-full bg-neutral-200 after:absolute after:left-[2px] after:top-[2px] after:h-5 after:w-5 after:rounded-full after:bg-white after:transition-all peer-checked:bg-primary peer-checked:after:translate-x-full"></div>
          </label>
        </div>
      </div>
    </div>

    <!-- 外观设置 -->
    <div v-if="activeTab === 'appearance'" class="responsive-panel p-4 sm:p-6">
      <h3 class="mb-6 text-base font-semibold text-neutral-700">外观设置</h3>
      <div class="space-y-5">
        <div class="flex items-center justify-between">
          <div>
            <p class="text-sm font-medium text-neutral-700">主题模式</p>
            <p class="text-xs text-neutral-400">选择浅色或深色主题</p>
          </div>
          <div class="flex gap-2">
            <button @click="setTheme('light')" :class="['rounded-lg px-4 py-2 text-sm border transition', settingsStore.appearance.theme === 'light' ? 'border-primary bg-primary/10 text-primary' : 'border-neutral-200 text-neutral-500']">
              <i class="fa fa-sun-o mr-1"></i> 浅色
            </button>
            <button @click="setTheme('dark')" :class="['rounded-lg px-4 py-2 text-sm border transition', settingsStore.appearance.theme === 'dark' ? 'border-primary bg-primary/10 text-primary' : 'border-neutral-200 text-neutral-500']">
              <i class="fa fa-moon-o mr-1"></i> 深色
            </button>
          </div>
        </div>
        <div class="flex items-center justify-between">
          <div>
            <p class="text-sm font-medium text-neutral-700">字体大小</p>
            <p class="text-xs text-neutral-400">调整界面文字大小</p>
          </div>
          <select v-model="settingsStore.appearance.fontSize" @change="saveAppearance('fontSize', settingsStore.appearance.fontSize)" class="rounded-lg border border-neutral-200 px-3 py-1.5 text-sm">
            <option value="small">小</option>
            <option value="medium">中</option>
            <option value="large">大</option>
          </select>
        </div>
        <div class="flex items-center justify-between">
          <div>
            <p class="text-sm font-medium text-neutral-700">界面密度</p>
            <p class="text-xs text-neutral-400">控制内容间距</p>
          </div>
          <select v-model="settingsStore.appearance.density" @change="saveAppearance('density', settingsStore.appearance.density)" class="rounded-lg border border-neutral-200 px-3 py-1.5 text-sm">
            <option value="compact">紧凑</option>
            <option value="comfortable">舒适</option>
            <option value="spacious">宽松</option>
          </select>
        </div>
        <div class="flex items-center justify-between">
          <div>
            <p class="text-sm font-medium text-neutral-700">动画效果</p>
            <p class="text-xs text-neutral-400">启用界面过渡动画</p>
          </div>
          <label class="relative inline-flex cursor-pointer items-center">
            <input type="checkbox" v-model="settingsStore.appearance.animationEnabled" @change="saveAppearance('animationEnabled', settingsStore.appearance.animationEnabled)" class="peer sr-only" />
            <div class="h-6 w-11 rounded-full bg-neutral-200 after:absolute after:left-[2px] after:top-[2px] after:h-5 after:w-5 after:rounded-full after:bg-white after:transition-all peer-checked:bg-primary peer-checked:after:translate-x-full"></div>
          </label>
        </div>
      </div>
    </div>

    <!-- 安全设置 -->
    <div v-if="activeTab === 'security'" class="responsive-panel p-4 sm:p-6">
      <h3 class="mb-6 text-base font-semibold text-neutral-700">修改密码</h3>
      <form @submit.prevent="handleChangePassword" class="max-w-md space-y-4">
        <div>
          <label class="text-sm font-medium text-neutral-600">当前密码</label>
          <input v-model="passwordForm.current" type="password" class="mt-1 w-full rounded-lg border border-neutral-200 px-3 py-2 text-sm focus:border-primary focus:outline-none" required />
        </div>
        <div>
          <label class="text-sm font-medium text-neutral-600">新密码</label>
          <input v-model="passwordForm.new" type="password" class="mt-1 w-full rounded-lg border border-neutral-200 px-3 py-2 text-sm focus:border-primary focus:outline-none" required minlength="8" />
        </div>
        <div>
          <label class="text-sm font-medium text-neutral-600">确认新密码</label>
          <input v-model="passwordForm.confirm" type="password" class="mt-1 w-full rounded-lg border border-neutral-200 px-3 py-2 text-sm focus:border-primary focus:outline-none" required />
        </div>
        <button type="submit" :disabled="settingsStore.saving" class="rounded-lg bg-primary px-6 py-2 text-sm text-white hover:bg-primary/90 disabled:opacity-50">
          {{ settingsStore.saving ? '保存中...' : '更新密码' }}
        </button>
      </form>
    </div>

    <!-- 数据管理 -->
    <div v-if="activeTab === 'data'" class="responsive-panel p-4 sm:p-6">
      <h3 class="mb-6 text-base font-semibold text-neutral-700">数据管理</h3>
      <div class="space-y-4">
        <div class="flex items-center justify-between rounded-lg border border-neutral-200 p-4">
          <div>
            <p class="text-sm font-medium text-neutral-700">导出个人数据</p>
            <p class="text-xs text-neutral-400">下载所有个人文件和数据的副本</p>
          </div>
          <button @click="settingsStore.exportData()" class="rounded-lg border border-neutral-200 px-4 py-2 text-sm text-neutral-600 hover:bg-neutral-50">
            <i class="fa fa-download mr-1"></i> 导出
          </button>
        </div>
        <div class="flex items-center justify-between rounded-lg border border-danger/30 p-4">
          <div>
            <p class="text-sm font-medium text-danger">删除账号</p>
            <p class="text-xs text-neutral-400">永久删除账号及所有数据，此操作不可撤销</p>
          </div>
          <button @click="showDeleteAccount = true" class="rounded-lg border border-danger px-4 py-2 text-sm text-danger hover:bg-danger/5">
            <i class="fa fa-exclamation-triangle mr-1"></i> 删除账号
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import PageHeader from '@/components/common/PageHeader.vue'
import { useSettingsStore } from '@/stores/settingsStore'

const settingsStore = useSettingsStore()
const activeTab = ref('preferences')
const showDeleteAccount = ref(false)

const passwordForm = ref({ current: '', new: '', confirm: '' })

const settingsTabs = [
  { key: 'preferences', label: '偏好设置', icon: 'fa fa-cog' },
  { key: 'notifications', label: '通知设置', icon: 'fa fa-bell' },
  { key: 'appearance', label: '外观设置', icon: 'fa fa-paint-brush' },
  { key: 'security', label: '安全设置', icon: 'fa fa-lock' },
  { key: 'data', label: '数据管理', icon: 'fa fa-database' },
]

const notificationItems = [
  { key: 'emailNotifications', label: '邮件通知', description: '接收重要操作和系统通知' },
  { key: 'pushNotifications', label: '推送通知', description: '在浏览器中接收实时推送通知' },
  { key: 'fileShared', label: '文件分享通知', description: '当有文件被分享给您时通知' },
  { key: 'fileDownloaded', label: '下载通知', description: '文件被下载时通知' },
  { key: 'storageWarning', label: '存储警告', description: '存储空间不足时发送警告' },
  { key: 'securityAlerts', label: '安全警报', description: '检测到异常登录等安全事件时通知' },
  { key: 'weeklyDigest', label: '每周摘要', description: '接收每周使用情况摘要' },
]

function savePref(key, value) {
  settingsStore.savePreferences({ [key]: value })
}

function saveNotification() {
  settingsStore.saveNotificationSettings({ ...settingsStore.notificationSettings })
}

function saveAppearance(key, value) {
  settingsStore.saveAppearance({ [key]: value })
}

function setTheme(theme) {
  saveAppearance('theme', theme)
  if (theme === 'dark') {
    document.documentElement.classList.add('dark')
  } else {
    document.documentElement.classList.remove('dark')
  }
}

async function handleChangePassword() {
  if (passwordForm.value.new !== passwordForm.value.confirm) {
    alert('两次密码输入不一致')
    return
  }
  const res = await settingsStore.changePassword({
    currentPassword: passwordForm.value.current,
    newPassword: passwordForm.value.new,
  })
  if (res.code === 200) {
    alert('密码修改成功')
    passwordForm.value = { current: '', new: '', confirm: '' }
  } else {
    alert(res.message || '密码修改失败')
  }
}

onMounted(() => {
  settingsStore.fetchPreferences()
  settingsStore.fetchNotificationSettings()
  settingsStore.fetchAppearance()
})
</script>