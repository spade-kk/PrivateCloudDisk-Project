<template>
  <div>
    <!-- Hero -->
    <section class="bg-gradient-to-br from-primary/5 via-white to-info/5 py-20 sm:py-28">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="mx-auto max-w-3xl text-center">
          <span class="inline-flex items-center gap-2 rounded-full bg-primary/10 px-3 py-1 text-xs font-medium text-primary">功能特性</span>
          <h1 class="mt-4 text-4xl font-extrabold tracking-tight text-neutral-900 sm:text-5xl">
            探索 CloudDrive 全部功能
          </h1>
          <p class="mt-4 text-lg text-neutral-500">从文件管理到团队协作，从安全防护到智能分析，我们提供一站式的企业级解决方案</p>
        </div>
      </div>
    </section>

    <!-- 文件管理 -->
    <section class="py-20 sm:py-24">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="grid grid-cols-1 items-center gap-12 lg:grid-cols-2">
          <div class="order-2 lg:order-1">
            <span class="inline-flex items-center gap-2 rounded-full bg-primary/10 px-3 py-1 text-xs font-medium text-primary">文件管理</span>
            <h2 class="mt-4 text-3xl font-bold text-neutral-900 sm:text-4xl">智能文件管理</h2>
            <p class="mt-4 text-neutral-500 leading-relaxed">拖拽上传、批量操作、智能分类、在线预览——让文件管理变得简单高效。</p>
            <ul class="mt-6 space-y-3">
              <li v-for="item in fileMgmtFeatures" :key="item" class="flex items-start gap-3 text-sm"><i class="fa fa-check-circle text-success mt-0.5"></i><span class="text-neutral-600">{{ item }}</span></li>
            </ul>
          </div>
          <div class="order-1 lg:order-2">
            <div class="rounded-2xl border border-neutral-200 bg-neutral-50 p-6">
              <div class="grid grid-cols-3 gap-3">
                <div v-for="icon in fileMgmtIcons" :key="icon" class="flex flex-col items-center gap-2 rounded-xl bg-white p-4 shadow-sm">
                  <i :class="[icon.icon, 'text-2xl', icon.color]"></i>
                  <span class="text-xs text-neutral-500">{{ icon.label }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 团队协作 -->
    <section class="border-t border-neutral-100 bg-neutral-50/50 py-20 sm:py-24">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="grid grid-cols-1 items-center gap-12 lg:grid-cols-2">
          <div>
            <div class="rounded-2xl border border-neutral-200 bg-white p-6">
              <div class="space-y-3">
                <div v-for="c in collabCards" :key="c.title" class="flex items-center gap-4 rounded-xl border border-neutral-100 p-4">
                  <div :class="['flex h-10 w-10 items-center justify-center rounded-lg', c.bg]"><i :class="[c.icon, c.color]"></i></div>
                  <div><p class="text-sm font-semibold text-neutral-700">{{ c.title }}</p><p class="text-xs text-neutral-400">{{ c.desc }}</p></div>
                </div>
              </div>
            </div>
          </div>
          <div>
            <span class="inline-flex items-center gap-2 rounded-full bg-success/10 px-3 py-1 text-xs font-medium text-success">团队协作</span>
            <h2 class="mt-4 text-3xl font-bold text-neutral-900 sm:text-4xl">高效团队协作</h2>
            <p class="mt-4 text-neutral-500 leading-relaxed">实时文件共享、在线协同编辑、精细化权限管理，让团队协作无缝衔接。</p>
            <ul class="mt-6 space-y-3">
              <li v-for="item in collabFeatures" :key="item" class="flex items-start gap-3 text-sm"><i class="fa fa-check-circle text-success mt-0.5"></i><span class="text-neutral-600">{{ item }}</span></li>
            </ul>
          </div>
        </div>
      </div>
    </section>

    <!-- 安全防护 -->
    <section class="py-20 sm:py-24">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="grid grid-cols-1 items-center gap-12 lg:grid-cols-2">
          <div>
            <span class="inline-flex items-center gap-2 rounded-full bg-danger/10 px-3 py-1 text-xs font-medium text-danger">安全防护</span>
            <h2 class="mt-4 text-3xl font-bold text-neutral-900 sm:text-4xl">企业级安全防护</h2>
            <p class="mt-4 text-neutral-500 leading-relaxed">银行级加密标准，全方位守护您的数据安全。</p>
            <div class="mt-6 grid grid-cols-1 gap-3 sm:grid-cols-2">
              <div v-for="s in securityFeatures" :key="s.title" class="rounded-xl border border-neutral-100 p-4 hover:border-primary/20 transition">
                <i :class="[s.icon, 'text-lg', s.color]"></i>
                <p class="mt-2 text-sm font-semibold text-neutral-700">{{ s.title }}</p>
                <p class="mt-1 text-xs text-neutral-400">{{ s.desc }}</p>
              </div>
            </div>
          </div>
          <div class="rounded-2xl border border-neutral-200 bg-neutral-900 p-8">
            <div class="space-y-4 font-mono text-xs">
              <div class="flex items-center gap-2 text-success"><span class="h-2 w-2 rounded-full bg-success"></span> AES-256-GCM Encryption Active</div>
              <div class="flex items-center gap-2 text-info"><span class="h-2 w-2 rounded-full bg-info"></span> TLS 1.3 Transport Layer</div>
              <div class="text-neutral-500 pl-4">│ cert: sha256WithRSAEncryption</div>
              <div class="text-neutral-500 pl-4">│ key: 2048 bit</div>
              <div class="text-neutral-500 pl-4">│ valid: 2025-01-01 ~ 2026-01-01</div>
              <div class="flex items-center gap-2 text-warning"><span class="h-2 w-2 rounded-full bg-warning"></span> Virus Scanner: Active</div>
              <div class="text-neutral-500 pl-4">│ engine: ClamAV 1.4.0</div>
              <div class="text-neutral-500 pl-4">│ last_update: 2026-01-12 08:00</div>
              <div class="flex items-center gap-2 text-success"><span class="h-2 w-2 rounded-full bg-success"></span> 2FA Enforcement: ON</div>
              <div class="text-neutral-500 pl-4">│ method: TOTP / SMS</div>
              <div class="flex items-center gap-2 text-info"><span class="h-2 w-2 rounded-full bg-info"></span> Audit Log: Recording</div>
              <div class="text-neutral-500 pl-4">│ retention: 365 days</div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 多端同步 -->
    <section class="border-t border-neutral-100 bg-neutral-50/50 py-20 sm:py-24">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="grid grid-cols-1 items-center gap-12 lg:grid-cols-2">
          <div class="order-2 lg:order-1">
            <div class="flex flex-wrap justify-center gap-4">
              <div v-for="d in devices" :key="d.label" class="flex flex-col items-center gap-2 rounded-2xl border border-neutral-200 bg-white p-6 w-28">
                <i :class="[d.icon, 'text-2xl text-primary']"></i>
                <span class="text-xs font-medium text-neutral-600">{{ d.label }}</span>
              </div>
            </div>
          </div>
          <div class="order-1 lg:order-2">
            <span class="inline-flex items-center gap-2 rounded-full bg-info/10 px-3 py-1 text-xs font-medium text-info">多端同步</span>
            <h2 class="mt-4 text-3xl font-bold text-neutral-900 sm:text-4xl">全平台无缝同步</h2>
            <p class="mt-4 text-neutral-500 leading-relaxed">Windows、macOS、Linux、iOS、Android 全平台覆盖，一处修改，处处同步。</p>
            <ul class="mt-6 space-y-3">
              <li v-for="item in syncFeatures" :key="item" class="flex items-start gap-3 text-sm"><i class="fa fa-check-circle text-success mt-0.5"></i><span class="text-neutral-600">{{ item }}</span></li>
            </ul>
            <router-link to="/download" class="mt-6 inline-flex items-center gap-2 text-sm font-semibold text-primary hover:underline">下载所有客户端 <i class="fa fa-arrow-right text-xs"></i></router-link>
          </div>
        </div>
      </div>
    </section>

    <!-- 智能分析 -->
    <section class="py-20 sm:py-24">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="grid grid-cols-1 items-center gap-12 lg:grid-cols-2">
          <div>
            <span class="inline-flex items-center gap-2 rounded-full bg-warning/10 px-3 py-1 text-xs font-medium text-warning">智能分析</span>
            <h2 class="mt-4 text-3xl font-bold text-neutral-900 sm:text-4xl">数据驱动决策</h2>
            <p class="mt-4 text-neutral-500 leading-relaxed">全面的数据分析和可视化报表，帮助您了解团队使用情况，优化存储资源配置。</p>
            <ul class="mt-6 space-y-3">
              <li v-for="item in analyticsFeatures" :key="item" class="flex items-start gap-3 text-sm"><i class="fa fa-check-circle text-success mt-0.5"></i><span class="text-neutral-600">{{ item }}</span></li>
            </ul>
          </div>
          <div class="rounded-2xl border border-neutral-200 bg-white p-6">
            <div class="grid grid-cols-2 gap-4 mb-4">
              <div class="rounded-xl border border-neutral-100 p-4"><p class="text-xs text-neutral-400">存储使用</p><p class="text-2xl font-bold text-neutral-800 mt-1">2.4 TB</p><div class="mt-2 h-2 rounded-full bg-neutral-200"><div class="h-2 rounded-full bg-primary" style="width:68%"></div></div></div>
              <div class="rounded-xl border border-neutral-100 p-4"><p class="text-xs text-neutral-400">活跃用户</p><p class="text-2xl font-bold text-neutral-800 mt-1">1,245</p><p class="text-xs text-success mt-1"><i class="fa fa-arrow-up"></i> +12%</p></div>
            </div>
            <div class="rounded-xl border border-neutral-100 p-4">
              <p class="text-xs text-neutral-400 mb-3">月流量趋势</p>
              <div class="flex items-end gap-1 h-20">
                <div v-for="(h, i) in [45,62,55,80,72,90,78,85,65,88,70,92]" :key="i" class="flex-1 rounded-t bg-info/30" :style="{ height: h + '%' }"></div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- More Features -->
    <section class="py-20 sm:py-24 bg-white">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="mx-auto max-w-2xl text-center mb-12">
          <h2 class="text-3xl font-bold text-neutral-900">更多高级功能</h2>
          <p class="mt-4 text-neutral-500">持续创新，为您提供更多企业级能力</p>
        </div>
        <div class="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
          <div v-for="f in moreFeatures" :key="f.title" class="rounded-2xl border border-neutral-200 bg-white p-6 transition hover:-translate-y-1 hover:shadow-lg">
            <div class="flex h-10 w-10 items-center justify-center rounded-xl" :class="f.bgClass">
              <i :class="[f.icon, 'text-lg', f.iconClass]"></i>
            </div>
            <h3 class="mt-4 text-base font-semibold text-neutral-800">{{ f.title }}</h3>
            <p class="mt-2 text-xs text-neutral-500 leading-relaxed">{{ f.desc }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- CTA -->
    <section class="border-t border-neutral-100 bg-neutral-50/50 py-20">
      <div class="mx-auto max-w-3xl px-4 text-center sm:px-6 lg:px-8">
        <h2 class="text-3xl font-bold text-neutral-900">准备好体验全部功能了吗？</h2>
        <p class="mt-4 text-neutral-500">免费注册，立即体验 CloudDrive 的全部企业级功能</p>
        <div class="mt-8 flex justify-center gap-4">
          <router-link to="/register" class="rounded-xl bg-primary px-8 py-3 text-sm font-semibold text-white hover:bg-primary/90">免费开始使用</router-link>
          <router-link to="/contact" class="rounded-xl border border-neutral-200 px-8 py-3 text-sm font-semibold text-neutral-600 hover:border-primary hover:text-primary">预约演示</router-link>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
const fileMgmtFeatures = [
  '支持拖拽上传和批量文件操作，自动去重',
  '文件夹无限嵌套，标签与星标分类管理',
  '100+ 文件格式在线预览，无需下载',
  'AI 智能分类，自动识别文档、图片、视频',
  '文件版本历史管理，随时恢复任意版本',
  '回收站 30 天保护，防止误删',
]

const fileMgmtIcons = [
  { icon: 'fa fa-file-word-o', color: 'text-primary', label: '文档' },
  { icon: 'fa fa-file-image-o', color: 'text-danger', label: '图片' },
  { icon: 'fa fa-file-video-o', color: 'text-warning', label: '视频' },
  { icon: 'fa fa-file-audio-o', color: 'text-success', label: '音频' },
  { icon: 'fa fa-file-archive-o', color: 'text-purple-500', label: '压缩包' },
  { icon: 'fa fa-file-code-o', color: 'text-info', label: '代码' },
]

const collabFeatures = [
  '创建共享文件夹，设置查看/编辑/管理权限',
  '实时评论与批注，团队成员即时沟通',
  '支持链接分享，可设置密码和有效期',
  '文件锁定功能，防止多人同时编辑冲突',
  '团队空间管理，按项目/部门组织文件',
  '活动日志追踪，了解团队操作动态',
]

const collabCards = [
  { title: '共享文件夹', desc: '团队统一存储空间', icon: 'fa fa-folder', bg: 'bg-primary/10', color: 'text-primary' },
  { title: '权限管理', desc: '精细化权限控制', icon: 'fa fa-lock', bg: 'bg-warning/10', color: 'text-warning' },
  { title: '实时评论', desc: '文件批注与讨论', icon: 'fa fa-comment', bg: 'bg-info/10', color: 'text-info' },
  { title: '链接分享', desc: '安全的外部分享', icon: 'fa fa-link', bg: 'bg-success/10', color: 'text-success' },
]

const securityFeatures = [
  { title: '端到端加密', desc: 'AES-256 加密传输与存储', icon: 'fa fa-lock', color: 'text-primary' },
  { title: '病毒扫描', desc: '实时检测恶意文件并隔离', icon: 'fa fa-bug', color: 'text-danger' },
  { title: '双因素认证', desc: 'TOTP/SMS 双重验证', icon: 'fa fa-shield', color: 'text-success' },
  { title: '审计日志', desc: '完整操作记录可追溯', icon: 'fa fa-list-alt', color: 'text-warning' },
  { title: 'IP 白名单', desc: '限定访问来源 IP', icon: 'fa fa-filter', color: 'text-purple-500' },
  { title: '数据备份', desc: '多地冗余备份容灾', icon: 'fa fa-database', color: 'text-info' },
]

const devices = [
  { label: 'Windows', icon: 'fa fa-windows' },
  { label: 'macOS', icon: 'fa fa-apple' },
  { label: 'Linux', icon: 'fa fa-linux' },
  { label: 'iOS', icon: 'fa fa-apple' },
  { label: 'Android', icon: 'fa fa-android' },
  { label: 'Web', icon: 'fa fa-globe' },
]

const syncFeatures = [
  '选择性同步，按需下载指定文件夹',
  '增量同步，仅传输变更部分，节省流量',
  '冲突检测与解决，自动合并或手动选择',
  '离线访问，无网络也能查看已同步文件',
  '局域网加速，同网络下点对点高速传输',
  '同步状态实时监控，了解传输进度',
]

const analyticsFeatures = [
  '存储使用趋势分析，预测容量需求',
  '用户活跃度统计，了解团队使用情况',
  '流量消耗报表，按用户/部门/时间维度',
  '热门文件排行，识别高价值内容',
  '异常行为检测，及时发现安全风险',
  '可导出 Excel/PDF 报表，支持定期邮件推送',
]

const moreFeatures = [
  { title: '命令行工具 (CLI)', desc: '提供强大的命令行工具，支持批处理、自动化脚本、CI/CD 集成等场景。', icon: 'fa fa-terminal', bgClass: 'bg-neutral-100', iconClass: 'text-neutral-600' },
  { title: 'WebDAV 协议', desc: '原生支持 WebDAV 协议，可挂载为本地磁盘，无缝集成到 Finder、文件管理器等工具。', icon: 'fa fa-hdd-o', bgClass: 'bg-info/10', iconClass: 'text-info' },
  { title: '文件水印', desc: '预览和下载时自动添加水印，支持自定义水印内容和样式，有效防止信息泄露。', icon: 'fa fa-tint', bgClass: 'bg-purple-50', iconClass: 'text-purple-500' },
  { title: '自动化工作流', desc: '基于事件触发器的自动化规则，如文件上传后自动扫描、自动分类、自动通知等。', icon: 'fa fa-magic', bgClass: 'bg-warning/10', iconClass: 'text-warning' },
  { title: '全文搜索', desc: '基于 Elasticsearch 的全文搜索引擎，不仅搜索文件名，还支持文档内容搜索。', icon: 'fa fa-search', bgClass: 'bg-primary/10', iconClass: 'text-primary' },
  { title: '数据迁移工具', desc: '提供从公有云盘、NAS、FTP 等平台的一键迁移工具，零停机迁移您的数据。', icon: 'fa fa-exchange', bgClass: 'bg-success/10', iconClass: 'text-success' },
]
</script>