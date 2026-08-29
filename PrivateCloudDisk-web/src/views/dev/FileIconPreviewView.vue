<template>
  <main class="file-icon-preview-page">
    <header class="file-icon-preview-page__header">
      <div>
        <span class="file-icon-preview-page__eyebrow">DEVELOPER TOOL</span>
        <h1>文件类型图标预览</h1>
        <p>完整核对 VS Code Icons 离线映射、特殊文件名、特殊目录和未知后缀动态 SVG。</p>
      </div>
      <router-link to="/app" class="file-icon-preview-page__back">返回控制面板</router-link>
    </header>

    <section v-if="!isDev" class="file-icon-preview-page__blocked">
      该页面仅在开发模式可用。
    </section>
    <template v-else>
      <section class="file-icon-preview-page__stats" aria-label="图标映射统计">
        <div><strong>{{ FILE_ICON_EXTENSION_COUNT }}</strong><span>文件后缀映射</span></div>
        <div><strong>{{ FILE_ICON_SPECIAL_FILE_COUNT }}</strong><span>特殊文件名</span></div>
        <div><strong>{{ FILE_ICON_SPECIAL_DIRECTORY_COUNT }}</strong><span>特殊目录名</span></div>
        <div><strong>{{ FILE_ICON_VSCODE_ICON_COUNT }}</strong><span>VS Code Icons 映射</span></div>
        <div><strong>{{ vscodeFileIconCollectionSize }}</strong><span>离线图标资源</span></div>
      </section>

      <section class="file-icon-preview-page__modes" aria-label="颜色模式演示">
        <h2>颜色填充模式（colorMode）</h2>
        <p>full-color 为默认；monochrome / github 随主题灰；monochrome-inverse 反色；customColor 自定义。</p>
        <div class="file-icon-preview-page__mode-row">
          <div v-for="mode in colorModes" :key="mode.label" class="file-icon-preview-page__mode-card">
            <small>{{ mode.label }}</small>
            <div class="file-icon-preview-page__mode-icons">
              <FileTypeIcon v-for="sample in modeSamples" :key="sample" :file-name="sample" color-mode="full-color" />
            </div>
            <div class="file-icon-preview-page__mode-icons">
              <FileTypeIcon v-for="sample in modeSamples" :key="sample" :file-name="sample" :color-mode="mode.colorMode" :custom-color="mode.customColor" />
            </div>
          </div>
        </div>
      </section>

      <section class="file-icon-preview-page__toolbar" aria-label="图标预览筛选">
        <div class="file-icon-preview-page__tabs" role="tablist" aria-label="映射类别">
          <button v-for="tab in tabs" :key="tab.key" type="button" :class="{ active: activeTab === tab.key }" role="tab" :aria-selected="activeTab === tab.key" @click="activeTab = tab.key">
            {{ tab.label }}<span>{{ tab.count }}</span>
          </button>
        </div>
        <label class="file-icon-preview-page__search">
          <span>搜索文件名、后缀或图标名</span>
          <input v-model.trim="query" type="search" placeholder="例如 .tsx、Dockerfile、folder-type-git" />
        </label>
      </section>

      <section class="file-icon-preview-page__summary">
        <div>
          <h2>{{ activeTabLabel }}</h2>
          <p>当前显示 {{ filteredItems.length }} / {{ currentItems.length }} 项；识别类型均来自本地打包的 vscode-icons，未知类型才使用动态 SVG。</p>
        </div>
        <button type="button" class="file-icon-preview-page__reset" :disabled="!query" @click="query = ''">清除筛选</button>
      </section>

      <section class="file-icon-preview-page__grid" aria-live="polite">
        <article v-for="item in filteredItems" :key="`${activeTab}-${item.key}`" class="file-icon-preview-page__card">
          <div class="file-icon-preview-page__icon">
            <FileTypeIcon :file-name="item.fileName" :path="item.path" :is-directory="item.directory" />
          </div>
          <strong :title="item.fileName">{{ item.fileName }}</strong>
          <small :title="item.path || item.fileName">{{ item.path || item.label }}</small>
          <code>{{ item.iconName || 'dynamic-svg' }}</code>
        </article>
        <div v-if="!filteredItems.length" class="file-icon-preview-page__empty">
          未找到匹配的映射，请尝试后缀、文件名或图标名。
        </div>
      </section>
    </template>
  </main>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import FileTypeIcon from '@/components/file/FileTypeIcon.vue'
import { vscodeFileIconCollection } from '@/utils/vscodeFileIconRegistry'
import {
  FILE_ICON_EXTENSION_COUNT,
  FILE_ICON_EXTENSION_MAP,
  FILE_ICON_SPECIAL_DIRECTORY_COUNT,
  FILE_ICON_SPECIAL_FILE_COUNT,
  FILE_ICON_SPECIAL_DIRECTORY_MAP,
  FILE_ICON_SPECIAL_FILE_MAP,
  FILE_ICON_VSCODE_EXTENSION_MAP,
  FILE_ICON_VSCODE_SPECIAL_DIRECTORY_MAP,
  FILE_ICON_VSCODE_SPECIAL_FILE_MAP,
  resolveFileTypeIcon,
} from '@/utils/fileTypeIcons'

interface PreviewItem {
  key: string
  label: string
  fileName: string
  path?: string
  directory?: boolean
  iconName?: string
}
type PreviewTab = 'extensions' | 'special-files' | 'special-directories'

const isDev = import.meta.env.DEV
const activeTab = ref<PreviewTab>('extensions')
const query = ref('')

function extensionFileName(extension: string) {
  return `sample.${extension.replace(/^\./, '')}`
}

function specialFileSample(name: string) {
  if (name.includes('*')) return name.replace('*', 'example')
  if (name.includes('/')) return name.split('/').pop() || name
  return name
}

const extensionItems = Object.keys(FILE_ICON_EXTENSION_MAP).map((extension) => {
  const fileName = extensionFileName(extension)
  const descriptor = resolveFileTypeIcon({ fileName })
  return { key: extension, label: `.${extension}`, fileName, iconName: FILE_ICON_VSCODE_EXTENSION_MAP[extension] || descriptor.iconName }
})

const specialFileItems = Object.keys(FILE_ICON_SPECIAL_FILE_MAP).map((name) => {
  const fileName = specialFileSample(name)
  const path = name.includes('/') ? name : fileName
  const descriptor = resolveFileTypeIcon({ fileName, path })
  return { key: name, label: name, fileName, path: path === fileName ? undefined : path, iconName: FILE_ICON_VSCODE_SPECIAL_FILE_MAP[name] || descriptor.iconName }
})

const specialDirectoryItems = Object.keys(FILE_ICON_SPECIAL_DIRECTORY_MAP).map((name) => {
  const descriptor = resolveFileTypeIcon({ fileName: name, isDirectory: true })
  return { key: name, label: name, fileName: name, directory: true, iconName: FILE_ICON_VSCODE_SPECIAL_DIRECTORY_MAP[name] || descriptor.iconName }
})

const tabData = computed<Record<PreviewTab, PreviewItem[]>>(() => ({
  extensions: extensionItems,
  'special-files': specialFileItems,
  'special-directories': specialDirectoryItems,
}))
const tabs = computed(() => [
  { key: 'extensions' as const, label: '文件后缀', count: extensionItems.length },
  { key: 'special-files' as const, label: '特殊文件名', count: specialFileItems.length },
  { key: 'special-directories' as const, label: '特殊目录名', count: specialDirectoryItems.length },
])
const currentItems = computed(() => tabData.value[activeTab.value])
const activeTabLabel = computed(() => tabs.value.find((tab) => tab.key === activeTab.value)?.label || '')
const filteredItems = computed(() => {
  const normalized = query.value.toLowerCase()
  if (!normalized) return currentItems.value
  return currentItems.value.filter((item) => `${item.fileName} ${item.path || ''} ${item.label} ${item.iconName || ''}`.toLowerCase().includes(normalized))
})
const colorModes = [
  { label: 'full-color（默认）', colorMode: 'full-color' as const },
  { label: 'monochrome 主题灰', colorMode: 'monochrome' as const },
  { label: 'monochrome-inverse 反色', colorMode: 'monochrome-inverse' as const },
  { label: 'github 预设', colorMode: 'github' as const },
  { label: 'customColor #0969DA', colorMode: 'monochrome' as const, customColor: '#0969DA' },
]
const modeSamples = ['main.py', 'server.go', 'App.tsx', 'Dockerfile', 'config.yaml', 'unknown.zzz']
const vscodeFileIconCollectionSize = Object.keys(vscodeFileIconCollection.icons || {}).length
const FILE_ICON_VSCODE_ICON_COUNT = Object.keys(FILE_ICON_VSCODE_EXTENSION_MAP).length + Object.keys(FILE_ICON_VSCODE_SPECIAL_FILE_MAP).length + Object.keys(FILE_ICON_VSCODE_SPECIAL_DIRECTORY_MAP).length
</script>

<style scoped>
.file-icon-preview-page { min-height: 100vh; padding: 32px; background: var(--surface, #f8fafc); color: var(--text, #1e293b); }
.file-icon-preview-page__header, .file-icon-preview-page__stats, .file-icon-preview-page__toolbar, .file-icon-preview-page__summary, .file-icon-preview-page__grid { max-width: 1440px; margin-right: auto; margin-left: auto; }
.file-icon-preview-page__header { display: flex; justify-content: space-between; gap: 24px; margin-bottom: 24px; }
.file-icon-preview-page__eyebrow { color: #2563eb; font-size: 11px; font-weight: 800; letter-spacing: .14em; }
.file-icon-preview-page h1 { margin: 6px 0; font-size: 28px; }
.file-icon-preview-page p { margin: 0; color: #64748b; }
.file-icon-preview-page__back { align-self: center; color: #2563eb; }
.file-icon-preview-page__stats { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 12px; margin-bottom: 24px; }
.file-icon-preview-page__stats div { padding: 16px; border: 1px solid #e2e8f0; border-radius: 12px; background: #fff; }
.file-icon-preview-page__stats strong, .file-icon-preview-page__stats span { display: block; }
.file-icon-preview-page__stats strong { font-size: 24px; }
.file-icon-preview-page__stats span { color: #64748b; font-size: 12px; }
.file-icon-preview-page__toolbar { display: flex; align-items: end; justify-content: space-between; gap: 16px; margin-bottom: 16px; }
.file-icon-preview-page__tabs { display: flex; gap: 6px; overflow-x: auto; }
.file-icon-preview-page__tabs button { border: 0; border-radius: 8px; padding: 9px 12px; background: transparent; color: #64748b; cursor: pointer; white-space: nowrap; }
.file-icon-preview-page__tabs button.active { background: #dbeafe; color: #1d4ed8; font-weight: 700; }
.file-icon-preview-page__tabs span { margin-left: 6px; font-size: 11px; opacity: .75; }
.file-icon-preview-page__search { display: grid; gap: 5px; min-width: min(360px, 100%); color: #64748b; font-size: 11px; }
.file-icon-preview-page__search input { width: 100%; border: 1px solid #cbd5e1; border-radius: 8px; padding: 9px 11px; background: #fff; color: inherit; }
.file-icon-preview-page__summary { display: flex; align-items: center; justify-content: space-between; gap: 20px; margin-bottom: 12px; }
.file-icon-preview-page__summary h2 { margin: 0 0 4px; font-size: 17px; }
.file-icon-preview-page__summary p { font-size: 12px; }
.file-icon-preview-page__reset { border: 1px solid #cbd5e1; border-radius: 8px; padding: 8px 12px; background: #fff; color: #475569; cursor: pointer; }
.file-icon-preview-page__reset:disabled { cursor: default; opacity: .45; }
.file-icon-preview-page__grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(154px, 1fr)); gap: 10px; }
.file-icon-preview-page__card { display: flex; min-height: 144px; align-items: center; justify-content: center; gap: 7px; padding: 16px 10px; border: 1px solid #e2e8f0; border-radius: 12px; background: #fff; text-align: center; }
.file-icon-preview-page__icon { display: grid; width: 44px; height: 44px; place-items: center; color: #334155; font-size: 40px; }
.file-icon-preview-page__card strong, .file-icon-preview-page__card small, .file-icon-preview-page__card code { max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.file-icon-preview-page__card strong { font-size: 12px; }
.file-icon-preview-page__card small { color: #64748b; font-size: 10px; }
.file-icon-preview-page__card code { max-width: 130px; color: #64748b; font-size: 9px; }
.file-icon-preview-page__empty, .file-icon-preview-page__blocked { grid-column: 1 / -1; margin: 40px auto; padding: 20px; border-radius: 12px; background: #fff; text-align: center; }
:global(.dark) .file-icon-preview-page { --surface: #0f172a; --text: #e2e8f0; }
:global(.dark) .file-icon-preview-page__stats div, :global(.dark) .file-icon-preview-page__card, :global(.dark) .file-icon-preview-page__empty, :global(.dark) .file-icon-preview-page__blocked, :global(.dark) .file-icon-preview-page__reset, :global(.dark) .file-icon-preview-page__search input { border-color: #334155; background: #1e293b; color: #e2e8f0; }
:global(.dark) .file-icon-preview-page__tabs button.active { background: #1e3a8a; color: #bfdbfe; }
@media (max-width: 900px) { .file-icon-preview-page__stats { grid-template-columns: repeat(3, minmax(0, 1fr)); } .file-icon-preview-page__toolbar { display: block; } .file-icon-preview-page__search { margin-top: 12px; } }
@media (max-width: 640px) { .file-icon-preview-page { padding: 18px 12px; } .file-icon-preview-page__header { display: block; } .file-icon-preview-page__back { display: inline-block; margin-top: 16px; } .file-icon-preview-page__stats { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; } .file-icon-preview-page__stats div { padding: 12px 8px; } .file-icon-preview-page__stats strong { font-size: 18px; } .file-icon-preview-page__summary { display: block; } .file-icon-preview-page__reset { margin-top: 10px; } .file-icon-preview-page__grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
</style>


/* [REQ-GIT-ICON-20260818] 颜色模式演示区域：本站点内演示，不影响业务页面。 */
.file-icon-preview-page__modes { margin-bottom: 24px; }
.file-icon-preview-page__modes h2 { margin: 0 0 4px; font-size: 17px; }
.file-icon-preview-page__modes > p { margin: 0 0 12px; font-size: 12px; }
.file-icon-preview-page__mode-row { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 10px; }
.file-icon-preview-page__mode-card { padding: 12px; border: 1px solid #e2e8f0; border-radius: 12px; background: #fff; }
.file-icon-preview-page__mode-card > small { display: block; margin-bottom: 8px; color: #64748b; font-size: 11px; }
.file-icon-preview-page__mode-icons { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; font-size: 34px; color: var(--git-icon-color, #57606a); }
.file-icon-preview-page__mode-icons .file-type-icon { color: inherit; }
:global(.dark) .file-icon-preview-page__mode-card { border-color: #334155; background: #1e293b; }
