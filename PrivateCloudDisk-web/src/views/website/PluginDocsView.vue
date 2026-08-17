<template>
  <div class="bg-neutral-50">
    <section class="border-b border-neutral-200 bg-gradient-to-br from-slate-950 via-indigo-950 to-slate-900 py-16 text-white sm:py-20">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="mb-5 flex items-center gap-2 text-xs text-indigo-200">
          <router-link to="/docs" class="transition hover:text-white">文档中心</router-link>
          <i class="fa fa-angle-right"></i>
          <span>插件与自动化</span>
        </div>
        <p class="mb-4 inline-flex rounded-full border border-indigo-300/30 bg-indigo-300/10 px-3 py-1 text-xs font-semibold text-indigo-100">
          workflow.cloudflow.io/v1
        </p>
        <h1 class="max-w-4xl text-4xl font-black tracking-tight sm:text-5xl">让文件安全地产生业务价值</h1>
        <p class="mt-5 max-w-3xl text-base leading-8 text-slate-300 sm:text-lg">
          从激活前内容预处理、云端 Python 沙箱、本地客户端插件，到可视化工作流与能力中心，
          本指南给出可直接运行的契约和安全边界。
        </p>
        <div class="mt-8 flex flex-wrap gap-3">
          <a href="#quickstart" class="rounded-xl bg-white px-5 py-3 text-sm font-bold text-indigo-950 shadow-lg transition hover:-translate-y-0.5">开始开发</a>
          <router-link to="/app/plugins" class="rounded-xl border border-white/20 px-5 py-3 text-sm font-bold text-white transition hover:bg-white/10">打开插件控制台</router-link>
        </div>
      </div>
    </section>

    <section class="mx-auto grid max-w-7xl gap-8 px-4 py-12 sm:px-6 lg:grid-cols-[240px_minmax(0,1fr)] lg:px-8">
      <aside>
        <nav class="sticky top-24 rounded-2xl border border-neutral-200 bg-white p-4 shadow-sm" aria-label="插件文档目录">
          <p class="mb-3 px-3 text-[11px] font-bold uppercase tracking-widest text-neutral-400">开发指南</p>
          <a v-for="item in sections" :key="item.id" :href="`#${item.id}`" class="block rounded-lg px-3 py-2 text-sm text-neutral-600 transition hover:bg-indigo-50 hover:text-indigo-700">
            {{ item.label }}
          </a>
        </nav>
      </aside>

      <main class="min-w-0 space-y-6">
        <article id="lifecycle" class="doc-card">
          <p class="eyebrow">文件生命周期</p>
          <h2>内容预处理与激活后处理是同一插件的两个入口</h2>
          <p>预处理发生在最终哈希与安全扫描之前；激活之后原始内容不可变。插件失败、超时或自动化服务不可用时，平台会回退到原始副本并继续激活。</p>
          <div class="mt-5 overflow-x-auto rounded-xl bg-slate-950 p-5">
            <pre><code>merge.completed
  → pcd.file.content.ready.v1
  → preprocess(context)
  → pcd.file.content.processed.v1
  → hash → scan → active
  → pcd.file.available.v1
  → on_available(context)</code></pre>
          </div>
        </article>

        <article id="quickstart" class="doc-card">
          <p class="eyebrow">云插件快速入门</p>
          <h2>一个插件，同时监听两个生命周期</h2>
          <div class="code-block"><pre><code>import pycloud

def preprocess(context):
    content = pycloud.file.read_staging(context["file_id"])
    # 仅具备 write_pre_activation 权限时允许提交替换内容
    return {"modified": False}

def on_available(context):
    pycloud.log.info("文件已可访问", {"file_id": context["file_id"]})
    return {"metadata_updated": False}</code></pre></div>
        </article>

        <article id="sdk" class="doc-card">
          <p class="eyebrow">pycloud SDK</p>
          <h2>所有外部能力都经过一次性执行令牌</h2>
          <div class="mt-5 overflow-x-auto">
            <table>
              <thead><tr><th>能力</th><th>权限</th><th>可用阶段</th></tr></thead>
              <tbody>
                <tr v-for="item in sdkRows" :key="item.api"><td><code>{{ item.api }}</code></td><td>{{ item.permission }}</td><td>{{ item.stage }}</td></tr>
              </tbody>
            </table>
          </div>
        </article>

        <article id="sandbox" class="doc-card">
          <p class="eyebrow">运行安全</p>
          <h2>容器隔离只是第一层</h2>
          <div class="mt-5 grid gap-3 sm:grid-cols-2">
            <div v-for="limit in sandboxLimits" :key="limit.title" class="rounded-xl border border-neutral-200 bg-neutral-50 p-4">
              <strong class="text-sm text-neutral-900">{{ limit.title }}</strong>
              <p class="mt-1 text-sm leading-6 text-neutral-500">{{ limit.text }}</p>
            </div>
          </div>
        </article>

        <article id="local" class="doc-card">
          <p class="eyebrow">本地插件</p>
          <h2>签名验包后进入无同源权限沙箱</h2>
          <p>Web 运行时先校验 SHA-256 与 Ed25519 平台签名，再解包入口脚本。插件不能读取 Cookie、Token、宿主 DOM 或直接联网，所有 SDK 请求由宿主按用户授权代理。</p>
        </article>

        <article id="workflow" class="doc-card">
          <p class="eyebrow">工作流 DSL</p>
          <h2>声明顺序、依赖和能力，不执行任意表达式</h2>
          <div class="code-block"><pre><code v-pre>workflow "ArchiveContract" {
    metadata { display_name = "合同归档" version = "1.0" }
    trigger { event { name = "pcd.file.available.v1" } }
    step archive {
        action file.move { file_id = vars.file_id target = "/合同归档/" }
    }
}</code></pre></div>
        </article>

        <article id="release" class="doc-card">
          <p class="eyebrow">版本与发布</p>
          <h2>草稿可修改，发布版本永久不可变</h2>
          <ol class="mt-4 space-y-3 text-sm leading-6 text-neutral-600">
            <li v-for="(step, index) in releaseSteps" :key="step" class="flex gap-3">
              <span class="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-indigo-100 text-xs font-bold text-indigo-700">{{ index + 1 }}</span>
              <span>{{ step }}</span>
            </li>
          </ol>
        </article>
      </main>
    </section>
  </div>
</template>

<script setup lang="ts">
const sections = [
  { id: 'lifecycle', label: '文件生命周期' },
  { id: 'quickstart', label: '云插件入门' },
  { id: 'sdk', label: 'pycloud SDK' },
  { id: 'sandbox', label: '沙箱限制' },
  { id: 'local', label: '本地插件' },
  { id: 'workflow', label: '工作流 DSL' },
  { id: 'release', label: '版本与发布' },
]

const sdkRows = [
  { api: 'file.read_staging', permission: 'file.content.read_staging', stage: '激活前' },
  { api: 'file.write_pre_activation', permission: 'file.content.write_pre_activation', stage: '激活前' },
  { api: 'file.read', permission: 'file.content.read', stage: '激活后' },
  { api: 'file.update_metadata', permission: 'file.metadata.write', stage: '激活后' },
  { api: 'notification.send', permission: 'notification.send', stage: '全阶段' },
]

const sandboxLimits = [
  { title: '资源配额', text: '默认 1 vCPU、512 MiB、120 秒，到期后强制终止并清理工作区。' },
  { title: '网络与文件系统', text: '默认禁止出站；根目录只读，仅临时工作目录可写。' },
  { title: 'Python 能力', text: '仅允许 pycloud 与安全模块白名单，禁止动态执行和危险导入。' },
  { title: '输入输出', text: '源码、AST、日志、消息体和输出均有独立大小上限。' },
]

const releaseSteps = [
  '创建草稿并声明最小权限、入口函数和触发条件。',
  '上传候选包，完成哈希、解压边界、语法和安全扫描。',
  '发布不可变 SemVer 版本；已发布版本不能被覆盖。',
  '公共插件提交市场审核，通过后才允许跨账户或安装到空间。',
]
</script>

<style scoped>
.doc-card { @apply scroll-mt-24 rounded-2xl border border-neutral-200 bg-white p-6 shadow-sm sm:p-8; }
.doc-card h2 { @apply mt-1 text-2xl font-black tracking-tight text-neutral-900; }
.doc-card > p:not(.eyebrow) { @apply mt-3 text-sm leading-7 text-neutral-600; }
.eyebrow { @apply text-xs font-bold uppercase tracking-widest text-indigo-600; }
.code-block { @apply mt-5 overflow-x-auto rounded-xl bg-slate-950 p-5; }
pre { @apply min-w-max whitespace-pre font-mono text-sm leading-7 text-slate-200; }
table { @apply w-full min-w-[620px] text-left text-sm; }
th { @apply border-b border-neutral-200 px-3 py-3 font-bold text-neutral-800; }
td { @apply border-b border-neutral-100 px-3 py-3 text-neutral-600; }
td code { @apply rounded bg-indigo-50 px-2 py-1 text-xs text-indigo-700; }
</style>
