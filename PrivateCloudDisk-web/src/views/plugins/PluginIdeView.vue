<template>
  <div class="plugin-ide-page pcd-ide-responsive pcd-plugin-ide">
    <IdeShell
      :title="isLocal ? '本地插件开发工作区' : '云插件开发工作区'"
      :fullscreen="store.fullscreen"
      :left-collapsed="leftCollapsed"
      :right-collapsed="rightCollapsed"
      :bottom-collapsed="!store.bottomOpen"
      :mobile-panel-open="mobilePanelOpen"
      :dirty="store.dirty"
      :save-state="store.saveState"
      @toggle-left="toggleDrawer('left')"
      @toggle-right="toggleDrawer('right')"
      @toggle-bottom="toggleDrawer('bottom')"
      @toggle-fullscreen="store.fullscreen = !store.fullscreen"
      @close-mobile-panels="closeMobilePanels"
    >
      <template #topbar>
        <div class="ide-topbar-title">
          <button class="ide-back" type="button" aria-label="返回插件中心" title="返回插件中心" @click="goBack"><i class="fa fa-arrow-left"></i></button>
          <input v-model.trim="form.name" class="ide-name-input" aria-label="插件名称" placeholder="未命名插件" />
          <span class="ide-type-badge">{{ isLocal ? 'LOCAL' : 'CLOUD' }}</span>
          <span class="ide-status" :class="`is-${store.runStatus}`"><i class="fa fa-circle"></i>{{ runStatusLabel }}</span>
        </div>
        <div class="ide-topbar-actions">
          <button class="ide-action" type="button" :disabled="saving" title="保存 (Ctrl/Cmd+S)" @click="save"><i class="fa fa-save"></i><span class="hidden sm:inline">保存</span></button>
          <button class="ide-action" type="button" :disabled="saving" title="校验" @click="validate"><i class="fa fa-check-circle"></i><span class="hidden sm:inline">校验</span></button>
          <button class="ide-action ide-action--run" type="button" :disabled="saving || store.runStatus === 'running'" @click="run"><i class="fa fa-play"></i><span class="hidden sm:inline">运行</span></button>
          <button v-if="store.runStatus === 'running'" class="ide-action ide-action--stop" type="button" @click="stopRun"><i class="fa fa-stop"></i><span class="hidden sm:inline">停止</span></button>
          <select v-model="form.version" class="ide-version" aria-label="版本"><option>{{ form.version }}</option></select>
          <button class="ide-action ide-action--publish" type="button" :disabled="saving || !serverValid" @click="publish"><i class="fa fa-paper-plane"></i><span class="hidden sm:inline">发布</span></button>
          <!-- [IDE-RESP-2026-08 / 2.2、2.13] 仅在手机显示，收纳桌面次级操作而不移除功能。 -->
          <button class="ide-action ide-action--more" type="button" aria-label="打开更多插件操作" title="更多操作" @click="mobileMoreOpen = true"><i class="fa fa-ellipsis-h"></i></button>
        </div>
      </template>

      <template #sidebar>
        <FileTree
          :files="store.files.flatMap(flatten).filter((item) => item.kind === 'file')"
          :tree="store.files"
          :selected-id="store.activeFileId"
          :active-panel="store.leftPanel"
          @select="selectFile"
          @create-file="createFile"
          @rename="renameFile"
          @delete="deleteFile"
          @panel-change="store.leftPanel = $event"
        >
          <template #snippets><SnippetLibrary @insert="insertCode" /></template>
          <template #templates><TemplateLibrary @apply="insertCode" /></template>
        </FileTree>
      </template>

      <template #editor>
        <section class="editor-area">
          <header class="editor-tabs" role="tablist" aria-label="已打开文件">
            <button v-for="fileId in store.openFileIds" :key="fileId" class="editor-tab" :class="{ active: fileId === store.activeFileId }" role="tab" type="button" @click="store.activeFileId = fileId">
              <i :class="fileIcon(fileById(fileId)?.name)"></i><span>{{ fileById(fileId)?.name || '文件' }}</span><em v-if="store.dirtyFileIds.includes(fileId)">●</em><i class="fa fa-times editor-tab-close" aria-label="关闭文件" @click.stop="closeFile(fileId)"></i>
            </button>
            <span class="editor-tabs-spacer"></span>
          </header>
          <div class="editor-breadcrumb"><i class="fa fa-folder-open-o"></i><span>{{ store.activeFile?.path || '请选择文件' }}</span><span v-if="store.activeFile">›</span><span class="muted">{{ isLocal ? 'JavaScript/TypeScript' : 'Python' }}</span></div>
          <div class="editor-main">
            <!-- 新 IDE 使用独立 Wrapper，按文件路径隔离模型、标记、补全和主题。 -->
            <MonacoEditorWrapper v-if="store.activeFile" v-model="code" :path="store.activeFile.path || store.activeFile.name" :language="store.activeFile.language || (isLocal ? 'javascript' : 'python')" height="100%" :dirty="store.dirtyFileIds.includes(store.activeFile.id)" :completion-items="completionItems" @validation-change="onValidationChange" />
            <div v-else class="editor-empty"><i class="fa fa-file-code-o"></i><p>从左侧文件树打开一个项目文件</p></div>
          </div>
          <footer class="editor-status"><span>Ln 1, Col 1</span><span>{{ isLocal ? 'JavaScript' : 'Python' }}</span><span>UTF-8</span><span>空格: 2</span><span :class="{ danger: store.problems.length }"><i class="fa fa-warning"></i> {{ store.problems.length }} 个问题</span></footer>
        </section>
      </template>

      <template #bottom>
        <BottomPanel v-model="store.bottomPanel" :problem-count="store.problems.length" @collapse="store.bottomOpen = false">
          <template #output><div v-for="(line, index) in store.output" :key="index" class="console-line">{{ line }}</div><p v-if="!store.output.length" class="console-empty">等待校验或保存输出…</p></template>
          <template #problems><button v-for="(problem, index) in store.problems" :key="index" class="problem-row" type="button" @click="focusProblem(problem)"><span class="problem-severity" :class="problem.severity"><i class="fa fa-circle"></i></span><span>{{ problem.message }}</span><small>{{ problem.path || store.activeFile?.path || '' }}{{ problem.line ? `:${problem.line}:${problem.column || 1}` : '' }}</small></button><p v-if="!store.problems.length" class="console-empty">暂无问题</p></template>
          <template #execution><div v-for="(line, index) in store.executionLogs" :key="index" class="console-line">{{ line }}</div><p v-if="!store.executionLogs.length" class="console-empty">尚未运行插件</p></template>
          <template #debug><div class="debug-info">project={{ store.projectId || 'draft' }} · space context=由请求拦截器注入<br />runtime={{ isLocal ? 'client sandbox' : 'python sandbox' }}</div></template>
        </BottomPanel>
      </template>

      <template #right>
        <aside class="properties-panel">
          <header class="properties-header"><strong>插件配置</strong><span>v{{ form.version }}</span></header>
          <section class="properties-section"><h3>基本信息</h3><label>标识<input v-model.trim="form.slug" class="property-input" :disabled="Boolean(pluginId)" /></label><label>描述<textarea v-model.trim="form.description" class="property-input min-h-16"></textarea></label><label>可见范围<select v-model="form.visibility" class="property-input"><option value="PRIVATE">仅自己</option><option value="SPACE">当前空间</option><option value="PUBLIC">公开市场</option></select></label></section>
          <section v-if="!isLocal" class="properties-section"><h3>生命周期入口</h3><label class="switch-row"><input v-model="form.preprocess" type="checkbox" /><span><strong>内容预处理</strong><small>file.content.ready · 可写暂存内容</small></span></label><label class="switch-row"><input v-model="form.available" type="checkbox" /><span><strong>文件可用后处理</strong><small>file.available · 内容已冻结</small></span></label><p class="hint warning">预处理失败或超时会自动回退原内容，哈希和安全扫描仍会继续。</p></section>
          <section v-else class="properties-section"><h3>目标平台</h3><div class="platform-grid"><label v-for="platform in platforms" :key="platform.value"><input v-model="form.platforms" type="checkbox" :value="platform.value" />{{ platform.label }}</label></div><p class="hint">桌面文件和设备能力需在兼容客户端测试。</p></section>
          <section class="properties-section"><h3>权限声明</h3><label v-for="permission in permissions" :key="permission.value" class="switch-row"><input v-model="form.permissions" type="checkbox" :value="permission.value" /><span><strong>{{ permission.label }}</strong><small>{{ permission.description }}</small></span></label></section>
          <section class="properties-section"><h3>沙箱限制</h3><dl class="limits"><div><dt>CPU</dt><dd>1 核</dd></div><div><dt>内存</dt><dd>512 MB</dd></div><div><dt>执行时间</dt><dd>120 秒</dd></div><div><dt>网络</dt><dd>默认禁止</dd></div></dl></section>
          <section v-if="serverMessage" class="properties-section" :class="serverValid ? 'is-success' : 'is-danger'"><h3>{{ serverValid ? '后端校验通过' : '后端校验未通过' }}</h3><p>{{ serverMessage }}</p></section>
        </aside>
      </template>
    </IdeShell>

    <!--
      [IDE-RESP-2026-08 / 2.13、9.7] 使用移动端底部抽屉呈现次级工具栏操作；
      保留桌面按钮与原 API 调用，防止小屏因隐藏控件而失去发布、版本和测试能力。
    -->
    <Teleport to="body">
      <div v-if="mobileMoreOpen" class="ide-mobile-sheet-mask" role="presentation" @click.self="mobileMoreOpen = false">
        <section class="ide-mobile-sheet" role="dialog" aria-modal="true" aria-label="更多插件操作">
          <div class="ide-mobile-sheet__handle" aria-hidden="true"></div>
          <header><strong>更多插件操作</strong><button type="button" aria-label="关闭更多操作" @click="mobileMoreOpen = false"><i class="fa fa-times"></i></button></header>
          <div class="ide-mobile-sheet__actions">
            <button type="button" :disabled="saving" @click="save(); mobileMoreOpen = false"><i class="fa fa-save"></i>保存草稿</button>
            <button type="button" :disabled="saving" @click="validate(); mobileMoreOpen = false"><i class="fa fa-check-circle"></i>校验代码</button>
            <button type="button" :disabled="saving || store.runStatus === 'running'" @click="run(); mobileMoreOpen = false"><i class="fa fa-play"></i>测试运行</button>
            <button type="button" :disabled="saving || !serverValid" @click="publish(); mobileMoreOpen = false"><i class="fa fa-paper-plane"></i>发布版本</button>
          </div>
          <label class="ide-mobile-sheet__field">版本<select v-model="form.version" class="property-input"><option>{{ form.version }}</option></select></label>
        </section>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { strToU8, zipSync } from 'fflate'
import IdeShell from '@/components/plugins/ide/IdeShell.vue'
import FileTree from '@/components/plugins/ide/FileTree.vue'
import BottomPanel from '@/components/plugins/ide/BottomPanel.vue'
import SnippetLibrary from '@/components/plugins/ide/SnippetLibrary.vue'
import TemplateLibrary from '@/components/plugins/ide/TemplateLibrary.vue'
import MonacoEditorWrapper, { type MonacoValidationIssue } from '@/components/plugins/ide/MonacoEditorWrapper.vue'
import { usePluginIdeStore, type FileTreeNodeItem, type IdeProblem } from '@/stores/pluginIdeStore'
import { cancelPluginExecutionApi, createPluginApi, createPluginVersionApi, getPluginApi, getPluginTestExecutionApi, listPluginVersionsApi, publishPluginVersionApi, runPluginTestApi, uploadPluginPackageApi, updatePluginApi, validatePluginVersionApi, type PluginType } from '@/api/modules/plugins'
import { useToastStore } from '@/stores/toastStore'

const cloudTemplate = `import pycloud
from pycloud import capability, test

@capability("file_analysis")
def preprocess(context):
    """文件合并后、哈希与安全扫描前执行。"""
    pycloud.log.info("开始内容预处理", {"file_id": context["file_id"]})
    return {"modified": False}

def on_available(context):
    """文件可访问后执行；此入口禁止修改原始内容。"""
    pycloud.log.info("文件已可访问", {"file_id": context["file_id"]})
    return {"metadata_updated": False}

@test
def test_preprocess(context):
    """开发测试入口：只读验证 SDK 能力，不触发文件生命周期写回。"""
    return {"ok": True}
`

const localTemplate = `export async function activate(context) {
  // 客户端只会注入用户已授权的 SDK 能力。
  await plugin.system.notify('插件已启用', context.plugin.name)
}

export async function run(context) {
  const file = await plugin.file.read({ fileId: context.fileId })
  console.info('已读取用户明确选择的文件', { name: file.name, bytes: file.size })
  return { bytes: file.content.byteLength }
}
`

const route = useRoute(); const router = useRouter(); const toast = useToastStore(); const store = usePluginIdeStore()
const pluginId = computed(() => (route.params.pluginId ? String(route.params.pluginId) : ''))
const resolvedType = ref<'CLOUD_PLUGIN' | 'LOCAL_PLUGIN' | null>(null)
const isLocal = computed(() => route.params.type === 'local' || resolvedType.value === 'LOCAL_PLUGIN' || (store.projectType === 'LOCAL_PLUGIN' && !route.params.type))
const saving = ref(false); const serverValid = ref(false); const serverMessage = ref(''); const versionReady = ref(false); const rowVersion = ref(0)
const hydrating = ref(true)
const leftCollapsed = ref(false); const rightCollapsed = ref(false)
// [IDE-RESP-2026-08 / 1.5、2.7、3.1、4.1、5.1] 小屏将三块辅助面板切换为
// 抽屉；只在跨越断点时重置默认值，用户在同一设备上的开关选择不会被覆盖。
const compactViewport = ref(false); const mobileMoreOpen = ref(false)
const mobilePanelOpen = computed(() => compactViewport.value && (!leftCollapsed.value || !rightCollapsed.value || store.bottomOpen))
const form = reactive({ name: '', slug: '', version: '1.0.0', description: '', visibility: 'PRIVATE' as 'PRIVATE' | 'SPACE' | 'PUBLIC', preprocess: true, available: true, platforms: ['web'], permissions: ['plugin.log.write'] })
const platforms = [{ label: 'Web', value: 'web' }, { label: 'Windows', value: 'windows' }, { label: 'macOS', value: 'macos' }, { label: 'Linux', value: 'linux' }, { label: 'iOS', value: 'ios' }, { label: 'Android', value: 'android' }]
const permissions = computed(() => isLocal.value ? [{ value: 'client.file.read', label: '读取选定文件', description: '只访问用户明确选择的文件' }, { value: 'client.file.upload', label: '上传文件', description: '通过平台 SDK 写入当前空间' }, { value: 'client.system.notify', label: '系统通知', description: '显示本地通知' }, { value: 'plugin.log.write', label: '执行日志', description: '上传脱敏摘要' }] : [{ value: 'file.content.read_staging', label: '读取暂存内容', description: '仅预处理入口可用' }, { value: 'file.content.write_pre_activation', label: '激活前回写内容', description: '高风险内容写权限' }, { value: 'file.content.read', label: '读取最终内容', description: '安全扫描通过后的只读内容' }, { value: 'file.metadata.write', label: '修改元数据', description: '名称、摘要和标签' }, { value: 'notification.send', label: '发送通知', description: '向触发用户发送站内通知' }, { value: 'plugin.log.write', label: '执行日志', description: '写入脱敏摘要' }])
const code = computed({ get: () => store.activeFile ? store.contentFor(store.activeFile) : '', set: (value: string) => { if (store.activeFile) store.updateContent(store.activeFile, value) } })
const runStatusLabel = computed(() => ({ idle: '就绪', queued: '排队中', running: '运行中', success: '运行成功', failed: '运行失败', timeout: '已超时' }[store.runStatus]))
const completionItems = computed(() => isLocal.value ? [
  { label: 'plugin.file.read', insertText: 'plugin.file.read(${1:fileId})', kind: 'method' as const, documentation: '读取当前客户端中用户授权的文件' },
  { label: 'plugin.file.upload', insertText: 'plugin.file.upload(${1:content}, ${2:name})', kind: 'method' as const, documentation: '通过客户端 SDK 上传文件' },
  { label: 'plugin.ui.show', insertText: 'plugin.ui.show(${1:message})', kind: 'method' as const, documentation: '展示客户端 UI 提示' },
] : [
  { label: 'pycloud.file.read', insertText: 'pycloud.file.read(${1:file_id})', kind: 'method' as const, documentation: '读取插件上下文中已授权的文件' },
  { label: 'pycloud.file.update_metadata', insertText: 'pycloud.file.update_metadata(${1:file_id}, ${2:metadata})', kind: 'method' as const, documentation: '修改文件元数据；file.available 入口不可写原始内容' },
  { label: 'pycloud.log.info', insertText: 'pycloud.log.info(${1:message})', kind: 'method' as const, documentation: '写入脱敏执行日志' },
])

function flatten(node: FileTreeNodeItem): FileTreeNodeItem[] { return [node, ...(node.children || []).flatMap(flatten)] }
function fileById(id: string) { return store.files.flatMap(flatten).find((item) => item.id === id) }
function fileIcon(name?: string) { const ext = name?.split('.').pop()?.toLowerCase(); return ext === 'py' || ext === 'js' || ext === 'ts' ? 'fa fa-file-code-o' : ext === 'md' ? 'fa fa-file-text-o' : 'fa fa-file-o' }
function selectFile(node: FileTreeNodeItem) {
  store.openFile(node)
  // [IDE-RESP-2026-08 / 3.8] 触摸端选择文件后回到 Monaco 主工作区。
  if (compactViewport.value) leftCollapsed.value = true
}
function renameFile(id: string) { const node = fileById(id); if (!node) return; const name = prompt('请输入新的文件名', node.name)?.trim(); if (name && !name.includes('/') && !name.includes('..')) store.renameNode(id, name) }
function deleteFile(id: string) { const node = fileById(id); if (!node || !confirm(`确定删除项目文件“${node.name}”吗？`)) return; store.deleteNode(id) }
function closeFile(id: string) { if (!store.closeFile(id)) toast.showToast('文件存在未保存更改，请先保存后再关闭', 'warning') }
function createFile() { const name = prompt('请输入项目相对路径，例如 src/helpers.py'); if (!name || name.includes('..') || name.startsWith('/')) return; const id = `file-${Date.now()}`; const ext = name.split('.').pop()?.toLowerCase(); store.files.push({ id, name: name.split('/').pop() || name, path: name, kind: 'file', language: ext === 'py' ? 'python' : ext === 'yaml' || ext === 'yml' ? 'yaml' : 'javascript', content: '' }); const file = fileById(id); if (file) store.openFile(file) }
function insertCode(value: string) { if (store.activeFile) store.updateContent(store.activeFile, `${store.contentFor(store.activeFile)}\n${value}`) }
function onValidationChange(valid: boolean, issues: MonacoValidationIssue[]) {
  serverValid.value = valid
  store.localProblems = issues.map((issue) => ({ severity: issue.severity, message: issue.message, line: issue.line, column: issue.column, path: issue.path || store.activeFile?.path }))
  store.issues = store.localProblems
}
function focusProblem(problem: IdeProblem) { if (problem.path) { const file = store.files.flatMap(flatten).find((item) => item.path === problem.path); if (file) store.openFile(file) } }
function goBack() { router.push('/app/plugins') }
function closeMobilePanels() {
  leftCollapsed.value = true
  rightCollapsed.value = true
  store.bottomOpen = false
}
function toggleDrawer(panel: 'left' | 'right' | 'bottom') {
  if (panel === 'left') leftCollapsed.value = !leftCollapsed.value
  if (panel === 'right') rightCollapsed.value = !rightCollapsed.value
  if (panel === 'bottom') store.bottomOpen = !store.bottomOpen
  if (!compactViewport.value) return
  if (panel !== 'left') leftCollapsed.value = true
  if (panel !== 'right') rightCollapsed.value = true
  if (panel !== 'bottom') store.bottomOpen = false
}
function syncResponsiveLayout() {
  const wasCompact = compactViewport.value
  compactViewport.value = window.innerWidth < 1024
  if (compactViewport.value && !wasCompact) closeMobilePanels()
  if (!compactViewport.value && wasCompact) {
    leftCollapsed.value = false
    rightCollapsed.value = false
    store.bottomOpen = true
  }
}
function buildPackage(id: string) { const entries: Record<string, Uint8Array> = {}; for (const file of store.files.flatMap(flatten).filter((item) => item.kind === 'file')) entries[file.path || file.name] = strToU8(store.contentFor(file)); entries['manifest.yaml'] = strToU8(`manifest_version: 1\nplugin:\n  id: ${id}\n  type: ${isLocal.value ? 'LOCAL_PLUGIN' : 'CLOUD_PLUGIN'}\n  version: ${form.version}\n  entrypoint: ${isLocal.value ? 'src/plugin.js' : 'src/main.py'}\n`); return new File([zipSync(entries, { level: 6 })], `${form.slug || id}-${form.version}.pcdpkg`, { type: 'application/vnd.pcd.plugin+zip' }) }
function entrypoints() { const result: any[] = []; if (form.preprocess) result.push({ event: 'pcd.file.content.ready.v1', function: 'preprocess', priority: 100, conditions: {}, permissions: form.permissions.filter((item) => item.includes('staging') || item.includes('pre_activation') || item === 'plugin.log.write') }); if (form.available) result.push({ event: 'pcd.file.available.v1', function: 'on_available', priority: 100, conditions: {}, permissions: form.permissions.filter((item) => !item.includes('pre_activation') && !item.includes('staging')) }); return result }
function validateForm() { if (!form.name || !/^[a-z][a-z0-9-]{2,119}$/.test(form.slug)) return '请填写名称，并使用合法的小写插件标识'; if (!/^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)/.test(form.version)) return '版本号必须符合 SemVer'; if (!isLocal.value && !form.preprocess && !form.available) return '至少选择一个生命周期入口'; if (isLocal.value && !form.platforms.length) return '至少选择一个目标平台'; return '' }

async function ensureVersion() {
  if (versionReady.value && pluginId.value) return pluginId.value
  let id = pluginId.value
  if (!id) { const created = await createPluginApi({ name: form.name, slug: form.slug, description: form.description, type: (isLocal.value ? 'LOCAL_PLUGIN' : 'CLOUD_PLUGIN') as PluginType, visibility: form.visibility }); id = created.data.pluginId; rowVersion.value = created.data.rowVersion; await router.replace(`/developer/plugins/${id}/edit`) }
  const versions = (await listPluginVersionsApi(id)).data || []
  if (!versions.some((item) => item.version === form.version)) { await createPluginVersionApi(id, { version: form.version, runtime: isLocal.value ? 'JAVASCRIPT_ES2022' : 'PYTHON_3_11', entrypoint: isLocal.value ? 'src/plugin.js' : 'src/main.py', permissions: form.permissions, supported_platforms: isLocal.value ? form.platforms : ['web'], client_types: isLocal.value ? ['web', 'desktop', 'mobile'] : ['web'], entrypoints: isLocal.value ? [] : entrypoints(), capabilities: [], manifest: { editor: 'web', source_language: isLocal.value ? 'javascript' : 'python' } }) }
  versionReady.value = true; return id
}
async function save() { const issue = validateForm(); if (issue) { toast.showToast(issue, 'warning'); return false }; saving.value = true; store.saveState = 'saving'; store.output.push(`[${new Date().toLocaleTimeString()}] 开始保存草稿`); try { const id = await ensureVersion(); if (id) { const updated = await updatePluginApi(id, rowVersion.value, { name: form.name, description: form.description, visibility: form.visibility }); rowVersion.value = updated.data.rowVersion } await uploadPluginPackageApi(id, form.version, buildPackage(id)); const result = await validatePluginVersionApi(id, form.version); const findings = result.data.findings || result.data.issues || []; store.serverProblems = findings.map((item) => ({ severity: item.type === 'WARNING' ? 'warning' : 'error', message: item.message || '后端校验失败', line: item.line, column: item.column, suggestion: item.suggestion })); serverValid.value = result.data.valid; serverMessage.value = result.data.valid ? '后端 AST、权限和供应链校验通过。' : result.data.message || findings.map((item) => item.message).filter(Boolean).join('；') || '请修复 Problems 面板中的问题'; store.output.push(`[${new Date().toLocaleTimeString()}] ${serverMessage.value}`); if (!result.data.valid) { store.saveState = 'error'; return false }; store.dirtyFileIds.slice().forEach((id) => store.markFileSaved(id)); store.saveState = 'saved'; toast.showToast('插件草稿已保存并通过校验', 'success'); return true } catch (error: any) { store.saveState = 'error'; serverValid.value = false; serverMessage.value = error?.response?.data?.message || error?.message || '保存失败'; store.output.push(`[错误] ${serverMessage.value}`); toast.showToast(serverMessage.value, 'error'); return false } finally { saving.value = false } }
async function validate() { await save() }
async function run() { if (!(await save())) return; if (!pluginId.value) return; store.runStatus = 'queued'; store.executionLogs = ['已提交 Runtime dry-run，等待沙箱…']; try { const result = await runPluginTestApi(pluginId.value, form.version, { entrypoint: 'test_preprocess', dryRun: true }); store.executionId = result.data.executionId; store.runStatus = 'running'; store.executionLogs.push(`execution_id=${result.data.executionId}`); toast.showToast('测试运行已进入异步队列', 'success'); void pollTestExecution(result.data.executionId) } catch (error: any) { store.runStatus = 'failed'; store.executionLogs.push(error?.message || 'Runtime 测试接口暂不可用'); toast.showToast('测试运行接口暂不可用，已保留代码和校验结果', 'warning') } }
async function pollTestExecution(executionId: string) { for (let attempt = 0; attempt < 120 && store.executionId === executionId; attempt += 1) { await new Promise((resolve) => setTimeout(resolve, 1000)); try { const status = (await getPluginTestExecutionApi(executionId)).data; if (status.status === 'SUCCESS' || status.status === 'FAILED' || status.status === 'TIMEOUT' || status.status === 'CANCELLED') { store.runStatus = status.status === 'SUCCESS' ? 'success' : status.status === 'TIMEOUT' ? 'timeout' : 'failed'; store.executionLogs.push(`status=${status.status}`, status.errorSummary || JSON.stringify(status.result || {})); return } } catch { /* 状态查询短暂失败时保留运行态，下一轮继续重试 */ } } }
async function stopRun() { if (store.executionId) { try { await cancelPluginExecutionApi(store.executionId) } catch { /* Runtime 会在超时边界自动回收 */ } } store.runStatus = 'idle'; store.executionLogs.push('已请求停止当前测试运行') }
async function publish() { if (!(await save()) || !pluginId.value) return; try { await publishPluginVersionApi(pluginId.value, form.version); toast.showToast(`插件 v${form.version} 已发布`, 'success') } catch (error: any) { toast.showToast(error?.message || '发布失败', 'error') } }
async function load() { store.reset(); hydrating.value = true; store.setProject(isLocal.value ? 'LOCAL_PLUGIN' : 'CLOUD_PLUGIN', pluginId.value || null); const defaults: FileTreeNodeItem[] = [{ id: 'src', name: 'src', path: 'src', kind: 'folder', children: [{ id: 'entry', name: isLocal.value ? 'plugin.js' : 'main.py', path: isLocal.value ? 'src/plugin.js' : 'src/main.py', kind: 'file', language: isLocal.value ? 'javascript' : 'python', content: isLocal.value ? localTemplate : cloudTemplate }] }, { id: 'manifest', name: 'manifest.yaml', path: 'manifest.yaml', kind: 'file', language: 'yaml', content: 'manifest_version: 1\n' }, { id: 'readme', name: 'README.md', path: 'README.md', kind: 'file', language: 'markdown', content: '# 插件说明\n\n在这里记录插件用途、权限和测试方式。\n' }]; store.setFiles(defaults); if (pluginId.value) { try { const plugin = (await getPluginApi(pluginId.value)).data; Object.assign(form, { name: plugin.name, slug: plugin.slug, description: plugin.description || '', visibility: plugin.visibility }); rowVersion.value = plugin.rowVersion; const versions = (await listPluginVersionsApi(pluginId.value)).data || []; if (versions[0]) { form.version = versions[0].version; versionReady.value = true } } catch (error: any) { toast.showToast(error?.message || '插件加载失败', 'error') } } hydrating.value = false; store.markSaved() }
let autoSaveTimer: ReturnType<typeof setInterval> | null = null
function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') { mobileMoreOpen.value = false; return }
  const modifier = event.ctrlKey || event.metaKey
  if (!modifier) return
  if (event.key.toLowerCase() === 's') { event.preventDefault(); void save() }
  if (event.shiftKey && event.key.toLowerCase() === 'p') {
    event.preventDefault()
    const command = prompt('命令面板：输入 save / validate / run / publish')?.trim().toLowerCase()
    if (command === 'save') void save()
    else if (command === 'validate') void validate()
    else if (command === 'run') void run()
    else if (command === 'publish') void publish()
  }
}
onMounted(async () => { syncResponsiveLayout(); if (!route.params.type && pluginId.value) { try { resolvedType.value = (await getPluginApi(pluginId.value)).data.pluginType === 'LOCAL_PLUGIN' ? 'LOCAL_PLUGIN' : 'CLOUD_PLUGIN' } catch { /* load() 会给出统一错误提示 */ } } await load(); autoSaveTimer = setInterval(() => { if (store.autoSave && store.dirty && !saving.value) void save() }, 30_000); window.addEventListener('beforeunload', beforeUnload); window.addEventListener('keydown', onKeydown); window.addEventListener('resize', syncResponsiveLayout, { passive: true }) })
onBeforeUnmount(() => { if (autoSaveTimer) clearInterval(autoSaveTimer); window.removeEventListener('beforeunload', beforeUnload); window.removeEventListener('keydown', onKeydown); window.removeEventListener('resize', syncResponsiveLayout); store.reset() })
function beforeUnload(event: BeforeUnloadEvent) { if (!store.dirty) return; event.preventDefault(); event.returnValue = '' }
onBeforeRouteLeave(() => { if (store.dirty && !confirm('当前插件有未保存更改，确定离开吗？')) return false })
watch(form, () => { if (!hydrating.value) store.markDirty(true) }, { deep: true })
</script>

<style scoped>
.plugin-ide-page { min-height: 100%; padding: 12px; background: #eef2f7; }.ide-topbar-title,.ide-topbar-actions{display:flex;min-width:0;align-items:center;gap:8px}.ide-back,.ide-action{display:inline-flex;min-height:34px;align-items:center;gap:6px;border-radius:8px;padding:0 9px;color:#cbd5e1;font-size:12px}.ide-back{min-width:32px;justify-content:center}.ide-back:hover,.ide-action:hover{background:#273244;color:#fff}.ide-action:disabled{opacity:.45}.ide-action--run{background:#166534;color:#dcfce7}.ide-action--stop{background:#7f1d1d;color:#fee2e2}.ide-action--publish{background:#2563eb;color:#eff6ff}.ide-name-input{width:min(220px,30vw);min-width:80px;border:0;border-bottom:1px solid #475569;background:transparent;color:#f8fafc;font-size:13px;font-weight:700;outline:none}.ide-type-badge,.ide-version{border-radius:6px;background:#273244;color:#cbd5e1;padding:5px 7px;font-size:10px}.ide-version{border:1px solid #475569;outline:none}.ide-status{font-size:10px;color:#86efac}.ide-status i{margin-right:4px;font-size:7px}.ide-status.is-running{color:#fcd34d}.ide-status.is-failed,.ide-status.is-timeout{color:#fca5a5}.editor-area{display:flex;min-height:0;height:100%;flex-direction:column;background:#0f172a}.editor-tabs{display:flex;min-height:38px;overflow:auto;border-bottom:1px solid #273244;background:#172033}.editor-tab{display:inline-flex;min-width:130px;max-width:190px;align-items:center;gap:7px;padding:0 11px;border-right:1px solid #273244;color:#94a3b8;font-size:11px}.editor-tab.active{background:#0f172a;color:#f8fafc}.editor-tab span{min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.editor-tab em{color:#fbbf24;font-size:10px;font-style:normal}.editor-tab-close{margin-left:auto;font-size:10px}.editor-tabs-spacer{flex:1}.editor-breadcrumb{display:flex;min-height:30px;align-items:center;gap:7px;padding:0 13px;color:#94a3b8;background:#111827;font-size:10px}.editor-breadcrumb .muted{color:#64748b}.editor-main{min-height:0;flex:1}.editor-empty{display:flex;height:100%;align-items:center;justify-content:center;flex-direction:column;gap:10px;color:#64748b}.editor-empty i{font-size:32px}.editor-status{display:flex;min-height:26px;justify-content:flex-end;gap:14px;padding:0 12px;color:#94a3b8;background:#172033;font-size:10px}.editor-status .danger{color:#fca5a5}.properties-panel{min-height:100%;background:#f8fafc;color:#334155}.properties-header{display:flex;align-items:center;justify-content:space-between;padding:14px 13px;border-bottom:1px solid #dbe4f0}.properties-header strong{font-size:13px}.properties-header span{color:#64748b;font-size:10px}.properties-section{padding:13px;border-bottom:1px solid #e2e8f0}.properties-section h3{margin-bottom:10px;color:#334155;font-size:11px;font-weight:700}.properties-section label{display:block;margin-bottom:9px;color:#64748b;font-size:10px;font-weight:600}.property-input{display:block;width:100%;margin-top:5px;border:1px solid #dbe4f0;border-radius:8px;background:#fff;padding:7px 8px;color:#334155;font-size:11px;outline:none}.property-input:focus{border-color:#2563eb}.switch-row{display:flex!important;align-items:flex-start;gap:8px;padding:7px;border-radius:8px}.switch-row:hover{background:#eef4ff}.switch-row input{margin-top:2px;accent-color:#2563eb}.switch-row strong{display:block;color:#475569;font-size:11px}.switch-row small{display:block;margin-top:3px;color:#94a3b8;font-size:9px;line-height:1.4}.platform-grid{display:grid;grid-template-columns:1fr 1fr;gap:6px}.platform-grid label{display:flex!important;align-items:center;gap:5px;padding:6px;border:1px solid #dbe4f0;border-radius:7px}.platform-grid input{accent-color:#2563eb}.hint{margin:6px 0 0;border-radius:8px;background:#eef4ff;padding:8px;color:#64748b;font-size:10px;line-height:1.5}.hint.warning{background:#fff7ed;color:#b45309}.properties-section.is-success{background:#f0fdf4}.properties-section.is-danger{background:#fff1f2}.properties-section.is-success p{color:#15803d;font-size:11px}.properties-section.is-danger p{color:#be123c;font-size:11px}.console-line{padding:2px 0;color:#cbd5e1;font:11px/1.5 ui-monospace,SFMono-Regular,Menlo,monospace}.console-empty{color:#64748b}.problem-row{display:grid;width:100%;grid-template-columns:auto minmax(0,1fr) auto;gap:8px;align-items:center;padding:5px 2px;color:#cbd5e1;text-align:left;font-size:11px}.problem-row:hover{background:#273244}.problem-row small{color:#64748b}.problem-severity.error{color:#f87171}.problem-severity.warning{color:#fbbf24}.problem-severity.info{color:#60a5fa}.debug-info{color:#94a3b8;font:11px/1.8 ui-monospace,SFMono-Regular,Menlo,monospace}.ide-action--run:hover{background:#15803d}.ide-action--publish:hover{background:#1d4ed8}@media(max-width:767px){.plugin-ide-page{padding:4px}.ide-name-input{max-width:120px}.ide-type-badge{display:none}}
</style>

<style scoped>
/* [IDE-RESP-2026-08 / 编辑器高度修复] 让中央编辑区消耗 Shell 中除终端外的
   所有可用高度；min-height: 0 是 Flex 子项允许自身收缩并把滚动交给 Monaco 的关键。 */
.editor-area { min-height: 0; height: 100%; flex: 1; overflow: hidden; }
.editor-main { min-height: 0; overflow: hidden; }
</style>
