<!-- 旧的插件创建编辑页面 已经遗弃 但是还保留重复的页面 已经它相关的组件作为兼用 -->
<template>
  <div class="space-y-5">
    <PageHeader
      :title="isLocal ? '创建本地插件' : '创建云插件'"
      :description="isLocal
        ? '编写跨平台客户端扩展，并通过签名包安全分发'
        : '在受限 Python 沙箱中处理文件事件或导出工作流能力'"
      :breadcrumbs="[
        { label: '插件中心', to: '/app/plugins' },
        { label: isLocal ? '本地插件' : '云插件' },
      ]"
    >
      <template #actions>
        <button class="secondary-button" type="button" @click="router.push('/app/plugins')">
          返回列表
        </button>
        <button class="primary-button" type="button" :disabled="submitting" @click="saveAndValidate(false)">
          <i class="fa fa-floppy-o"></i>
          {{ submitting ? '处理中…' : '保存并校验' }}
        </button>
        <button class="publish-button" type="button" :disabled="submitting || !frontendValid" @click="saveAndValidate(true)">
          <i class="fa fa-paper-plane"></i> 发布版本
        </button>
      </template>
    </PageHeader>

    <div class="grid gap-5 xl:grid-cols-[minmax(0,1fr)_340px]">
      <PluginMonacoEditor
        v-model="code"
        :language="isLocal ? 'javascript' : 'python'"
        :title="entrypoint"
        height="min(66vh, 680px)"
        @validation-change="onValidationChange"
      />

      <aside class="space-y-4">
        <section class="config-card">
          <h2 class="config-title">基本信息</h2>
          <label class="form-label">插件名称<input v-model.trim="form.name" class="form-input" maxlength="120" /></label>
          <label class="form-label">唯一标识<input v-model.trim="form.slug" class="form-input" placeholder="example-plugin" /></label>
          <label class="form-label">版本<input v-model.trim="form.version" class="form-input" placeholder="1.0.0" /></label>
          <label class="form-label">描述<textarea v-model.trim="form.description" class="form-input min-h-20 resize-y" maxlength="2000"></textarea></label>
          <label class="form-label">可见范围
            <select v-model="form.visibility" class="form-input">
              <option value="PRIVATE">仅自己</option>
              <option value="SPACE">空间可用</option>
              <option value="PUBLIC">可提交市场</option>
            </select>
          </label>
        </section>

        <section v-if="!isLocal" class="config-card">
          <div class="flex items-center justify-between">
            <h2 class="config-title !mb-0">生命周期入口</h2>
            <span class="text-[11px] text-neutral-400">可同时启用</span>
          </div>
          <label class="event-option">
            <input v-model="form.preprocess" type="checkbox" />
            <span><strong>内容预处理</strong><small>file.content.ready · 可在激活前回写内容</small></span>
          </label>
          <label class="event-option">
            <input v-model="form.available" type="checkbox" />
            <span><strong>可用后处理</strong><small>file.available · 仅元数据、通知与归档</small></span>
          </label>
          <div class="rounded-xl bg-amber-50 p-3 text-xs leading-5 text-amber-700">
            同一插件可同时提供 <code>preprocess</code> 与 <code>on_available</code>。
            文件完成哈希与扫描后，运行时会永久撤销原始内容写权限。
          </div>
        </section>

        <section v-else class="config-card">
          <h2 class="config-title">目标客户端</h2>
          <div class="grid grid-cols-2 gap-2">
            <label v-for="platform in platformOptions" :key="platform.value" class="check-tile">
              <input v-model="form.platforms" type="checkbox" :value="platform.value" />
              <span>{{ platform.label }}</span>
            </label>
          </div>
          <div class="mt-3 rounded-xl bg-blue-50 p-3 text-xs leading-5 text-blue-700">
            发布包将由平台 Ed25519 密钥签名。客户端安装前必须核验设备绑定、SHA-256 与签名。
          </div>
        </section>

        <section class="config-card">
          <h2 class="config-title">权限声明</h2>
          <label v-for="permission in permissionOptions" :key="permission.value" class="permission-row">
            <input v-model="form.permissions" type="checkbox" :value="permission.value" />
            <span><strong>{{ permission.label }}</strong><small>{{ permission.description }}</small></span>
          </label>
        </section>

        <section class="config-card">
          <h2 class="config-title">安全与资源限制</h2>
          <dl class="limit-list">
            <div><dt>CPU</dt><dd>1 核（可配置）</dd></div>
            <div><dt>内存</dt><dd>512 MB</dd></div>
            <div><dt>执行时间</dt><dd>最长 120 秒</dd></div>
            <div><dt>网络</dt><dd>默认禁止出站</dd></div>
            <div><dt>文件系统</dt><dd>只读包 + 临时工作区</dd></div>
          </dl>
        </section>

        <section v-if="validationMessage" class="config-card" :class="validationPassed ? 'border-success/30' : 'border-danger/30'">
          <h2 class="config-title">{{ validationPassed ? '校验通过' : '校验未通过' }}</h2>
          <p class="text-sm leading-6" :class="validationPassed ? 'text-success' : 'text-danger'">
            {{ validationMessage }}
          </p>
        </section>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { strToU8, zipSync } from 'fflate'
import PageHeader from '@/components/common/PageHeader.vue'
import PluginMonacoEditor from '@/components/plugins/PluginMonacoEditor.vue'
import {
  createPluginApi,
  createPluginVersionApi,
  publishPluginVersionApi,
  uploadPluginPackageApi,
  validatePluginVersionApi,
  type PluginEntrypoint,
  type PluginType,
} from '@/api/modules/plugins'
import { useToastStore } from '@/stores/toastStore'

const route = useRoute()
const router = useRouter()
const toast = useToastStore()
const isLocal = computed(() => route.params.type === 'local')
const pluginType = computed<PluginType>(() => isLocal.value ? 'LOCAL_PLUGIN' : 'CLOUD_PLUGIN')
const entrypoint = computed(() => isLocal.value ? 'src/plugin.js' : 'src/main.py')
const submitting = ref(false)
const frontendValid = ref(true)
const validationPassed = ref(false)
const validationMessage = ref('')

const cloudTemplate = `import pycloud

def preprocess(context):
    """文件合并后、哈希与安全扫描前执行。"""
    pycloud.log.info("开始内容预处理", {"file_id": context["file_id"]})
    return {"modified": False}

def on_available(context):
    """文件可访问后执行；此入口禁止修改原始内容。"""
    pycloud.log.info("文件已可访问", {"file_id": context["file_id"]})
    return {"metadata_updated": False}
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

const code = ref(isLocal.value ? localTemplate : cloudTemplate)
watch(isLocal, (value) => { code.value = value ? localTemplate : cloudTemplate })

const form = reactive({
  name: '',
  slug: '',
  version: '1.0.0',
  description: '',
  visibility: 'PRIVATE' as 'PRIVATE' | 'SPACE' | 'PUBLIC',
  preprocess: true,
  available: true,
  platforms: ['web'] as string[],
  permissions: ['plugin.log.write'] as string[],
})

const platformOptions = [
  { label: 'Web', value: 'web' },
  { label: 'Windows', value: 'windows' },
  { label: 'macOS', value: 'macos' },
  { label: 'Linux', value: 'linux' },
  { label: 'iOS', value: 'ios' },
  { label: 'Android', value: 'android' },
]

const permissionOptions = computed(() => isLocal.value ? [
  { value: 'client.file.read', label: '读取选定文件', description: '仅访问用户明确选择或空间授权的文件' },
  { value: 'client.file.upload', label: '上传文件', description: '通过平台 SDK 写入当前空间' },
  { value: 'client.ui.show', label: '显示界面', description: '在受限插件面板内渲染 UI' },
  { value: 'client.clipboard.write', label: '写入剪贴板', description: '不能读取剪贴板历史' },
  { value: 'client.system.notify', label: '系统通知', description: '显示本地通知' },
  { value: 'plugin.log.write', label: '执行日志', description: '上传脱敏后的执行摘要' },
] : [
  { value: 'file.content.read_staging', label: '读取暂存内容', description: '仅 file.content.ready 阶段可用' },
  { value: 'file.content.write_pre_activation', label: '激活前回写内容', description: '仅预处理入口可申请的高风险权限' },
  { value: 'file.content.read', label: '读取最终内容', description: '安全扫描通过后的只读内容' },
  { value: 'file.metadata.read', label: '读取元数据', description: '文件名、MIME、大小与空间上下文' },
  { value: 'file.metadata.write', label: '修改元数据', description: '名称、标签、摘要等' },
  { value: 'notification.send', label: '发送通知', description: '向触发用户发送站内通知' },
  { value: 'plugin.log.write', label: '执行日志', description: '写入脱敏的插件日志' },
])

watch(() => form.preprocess, (enabled) => {
  const required = ['file.content.read_staging', 'file.content.write_pre_activation']
  if (enabled) form.permissions = Array.from(new Set([...form.permissions, ...required]))
  else form.permissions = form.permissions.filter((permission) => !required.includes(permission))
}, { immediate: true })

watch(() => form.available, (enabled) => {
  const required = ['file.content.read', 'file.metadata.read']
  if (enabled) form.permissions = Array.from(new Set([...form.permissions, ...required]))
}, { immediate: true })

function onValidationChange(valid: boolean) {
  frontendValid.value = valid
}

function validateForm(): string | null {
  if (!form.name || !/^[a-z][a-z0-9-]{2,119}$/.test(form.slug)) return '请填写名称，并使用合法的小写插件标识'
  if (!/^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-[0-9A-Za-z.-]+)?$/.test(form.version)) return '版本号必须符合 SemVer'
  if (!frontendValid.value) return '请先修复编辑器中的语法或安全问题'
  if (!form.permissions.length) return '至少声明一项权限'
  if (!isLocal.value && !form.preprocess && !form.available) return '至少启用一个云插件生命周期入口'
  if (isLocal.value && !form.platforms.length) return '至少选择一个目标平台'
  return null
}

function entrypoints(): PluginEntrypoint[] {
  const result: PluginEntrypoint[] = []
  if (form.preprocess) result.push({
    event: 'pcd.file.content.ready.v1',
    function: 'preprocess',
    priority: 100,
    conditions: {},
    permissions: form.permissions.filter((permission) =>
      ['file.content.read_staging', 'file.content.write_pre_activation', 'plugin.log.write'].includes(permission)),
  })
  if (form.available) result.push({
    event: 'pcd.file.available.v1',
    function: 'on_available',
    priority: 100,
    conditions: {},
    permissions: form.permissions.filter((permission) => permission !== 'file.content.write_pre_activation'
      && permission !== 'file.content.read_staging'),
  })
  return result
}

function buildPackage(pluginId: string): File {
  const manifest = [
    'manifest_version: 1',
    'plugin:',
    `  id: ${pluginId}`,
    `  type: ${pluginType.value}`,
    `  version: ${form.version}`,
    `  entrypoint: ${entrypoint.value}`,
    '',
  ].join('\n')
  const archive = zipSync({
    'manifest.yaml': strToU8(manifest),
    [entrypoint.value]: strToU8(code.value),
  }, { level: 6 })
  return new File([archive], `${form.slug}-${form.version}.pcdpkg`, {
    type: 'application/vnd.pcd.plugin+zip',
  })
}

async function saveAndValidate(shouldPublish: boolean): Promise<void> {
  const error = validateForm()
  if (error) {
    toast.showToast(error, 'warning')
    return
  }
  submitting.value = true
  validationMessage.value = ''
  validationPassed.value = false
  try {
    const plugin = (await createPluginApi({
      name: form.name,
      slug: form.slug,
      description: form.description,
      type: pluginType.value,
      visibility: form.visibility,
    })).data
    const clientTypes = form.platforms.includes('web')
      ? ['web', ...(form.platforms.some((value) => value !== 'web') ? ['desktop', 'mobile'] : [])]
      : ['desktop', 'mobile']
    await createPluginVersionApi(plugin.pluginId, {
      version: form.version,
      runtime: isLocal.value ? 'JAVASCRIPT_ES2022' : 'PYTHON_3_11',
      entrypoint: entrypoint.value,
      permissions: form.permissions,
      supported_platforms: isLocal.value ? form.platforms : ['web'],
      client_types: isLocal.value ? Array.from(new Set(clientTypes)) : ['web'],
      entrypoints: isLocal.value ? [] : entrypoints(),
      capabilities: [],
      manifest: {
        editor: 'web',
        source_language: isLocal.value ? 'javascript' : 'python',
      },
    })
    await uploadPluginPackageApi(plugin.pluginId, form.version, buildPackage(plugin.pluginId))
    const validation = await validatePluginVersionApi(plugin.pluginId, form.version)
    validationPassed.value = validation.data.valid
    validationMessage.value = validation.data.valid
      ? '前端检查与后端语法/安全扫描均已通过。'
      : validation.data.issues?.map((issue) => issue.message).filter(Boolean).join('；') || '后端校验未通过'
    if (!validation.data.valid) return
    if (shouldPublish) {
      await publishPluginVersionApi(plugin.pluginId, form.version)
      toast.showToast('插件版本已签名并发布', 'success')
      await router.push('/app/plugins')
    } else {
      toast.showToast('插件草稿与版本已保存，后端校验通过', 'success')
    }
  } catch (error: any) {
    validationMessage.value = error?.response?.data?.message || error?.message || '保存失败'
    toast.showToast(validationMessage.value, 'error')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.config-card { @apply rounded-2xl border border-neutral-200 bg-white p-4 shadow-sm; }
.config-title { @apply mb-3 text-sm font-bold text-neutral-800; }
.form-label { @apply mb-3 block text-xs font-semibold text-neutral-500; }
.form-input { @apply mt-1.5 w-full rounded-xl border border-neutral-200 bg-white px-3 py-2.5 text-sm text-neutral-700 outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/10; }
.event-option,
.permission-row { @apply mb-2 flex cursor-pointer items-start gap-3 rounded-xl border border-neutral-100 p-3 transition hover:border-primary/20 hover:bg-primary/[0.03]; }
.event-option input,
.permission-row input,
.check-tile input { @apply mt-1 accent-primary; }
.event-option span,
.permission-row span { @apply min-w-0; }
.event-option strong,
.permission-row strong { @apply block text-sm text-neutral-700; }
.event-option small,
.permission-row small { @apply mt-0.5 block text-xs leading-5 text-neutral-400; }
.check-tile { @apply flex cursor-pointer items-center gap-2 rounded-xl border border-neutral-200 px-3 py-2.5 text-xs text-neutral-600; }
.limit-list div { @apply flex justify-between border-b border-neutral-100 py-2 text-xs last:border-0; }
.limit-list dt { @apply text-neutral-400; }
.limit-list dd { @apply font-medium text-neutral-600; }
.primary-button,
.secondary-button,
.publish-button { @apply inline-flex min-h-10 items-center gap-2 rounded-xl px-4 text-sm font-semibold transition disabled:cursor-not-allowed disabled:opacity-50; }
.primary-button { @apply bg-primary text-white hover:bg-primary/90; }
.publish-button { @apply bg-neutral-900 text-white hover:bg-neutral-800; }
.secondary-button { @apply border border-neutral-200 bg-white text-neutral-600 hover:border-primary/30 hover:text-primary; }
</style>
