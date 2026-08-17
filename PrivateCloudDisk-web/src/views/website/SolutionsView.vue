<template>
  <div>
    <section class="bg-gradient-to-br from-primary/5 via-white to-info/5 py-20 sm:py-28">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="mx-auto max-w-3xl text-center">
          <span class="inline-flex items-center gap-2 rounded-full bg-primary/10 px-3 py-1 text-xs font-medium text-primary">业务场景</span>
          <h1 class="mt-4 text-4xl font-extrabold tracking-tight text-neutral-900 sm:text-5xl">按空间和文件业务组合能力</h1>
          <p class="mt-4 text-lg text-neutral-500">从项目协作、部门资料到内容处理和对外分享，PrivateCloudDisk 用同一套平台能力承载不同工作场景</p>
        </div>
      </div>
    </section>

    <!-- Industry Solutions -->
    <section class="py-20 sm:py-24">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="space-y-20">
          <div v-for="(sol, i) in solutions" :key="sol.title" class="grid grid-cols-1 items-center gap-12 lg:grid-cols-2" :class="i % 2 === 1 ? 'lg:[&>*:first-child]:order-2 lg:[&>*:last-child]:order-1' : ''">
            <div>
              <div class="flex items-center gap-2">
                <div class="flex h-10 w-10 items-center justify-center rounded-xl" :class="sol.bgClass">
                  <i :class="[sol.icon, 'text-lg', sol.iconClass]"></i>
                </div>
                <span class="text-xs font-semibold uppercase tracking-wider text-neutral-400">{{ sol.tag }}</span>
              </div>
              <h2 class="mt-3 text-3xl font-bold text-neutral-900 sm:text-4xl">{{ sol.title }}</h2>
              <p class="mt-4 text-neutral-500 leading-relaxed">{{ sol.desc }}</p>
              <ul class="mt-6 space-y-3">
                <li v-for="item in sol.features" :key="item" class="flex items-start gap-3 text-sm">
                  <i class="fa fa-check-circle text-success mt-0.5"></i>
                  <span class="text-neutral-600">{{ item }}</span>
                </li>
              </ul>
              <div class="mt-6 flex gap-3">
                <router-link to="/register" class="rounded-xl bg-primary px-6 py-2.5 text-sm font-semibold text-white hover:bg-primary/90">开始使用</router-link>
                <router-link to="/contact" class="rounded-xl border border-neutral-200 px-6 py-2.5 text-sm font-semibold text-neutral-600 hover:border-primary hover:text-primary">预约演示</router-link>
              </div>
            </div>
            <div class="rounded-2xl border border-neutral-200 bg-neutral-50 p-8">
              <div class="grid grid-cols-2 gap-4">
                <div v-for="h in sol.highlights" :key="h.label" class="rounded-xl bg-white p-4 shadow-sm">
                  <p class="text-2xl font-bold text-primary">{{ h.value }}</p>
                  <p class="mt-1 text-xs text-neutral-500">{{ h.label }}</p>
                </div>
              </div>
              <div class="mt-6 rounded-xl bg-white p-4 border border-neutral-100">
                <p class="text-xs font-medium text-neutral-500 mb-2">能力组成</p>
                <div class="flex flex-wrap gap-2">
                  <span v-for="c in sol.customers" :key="c" class="rounded-md bg-neutral-100 px-2 py-1 text-xs text-neutral-600">{{ c }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- By Scenario -->
    <section class="border-t border-neutral-100 bg-neutral-50/50 py-20 sm:py-24">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="mx-auto max-w-2xl text-center">
          <h2 class="text-3xl font-bold text-neutral-900">按实际工作选择组合</h2>
          <p class="mt-4 text-neutral-500">空间、文件、预览、分享和自动化能力可以按业务边界组合</p>
        </div>
        <div class="mt-12 grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
          <div v-for="scene in scenarios" :key="scene.title" class="rounded-2xl border border-neutral-200 bg-white p-8 transition hover:-translate-y-1 hover:shadow-lg">
            <div class="flex items-center gap-3 mb-4">
              <div class="flex h-10 w-10 items-center justify-center rounded-xl" :class="scene.bgClass">
                <i :class="[scene.icon, 'text-lg', scene.iconClass]"></i>
              </div>
            </div>
            <h3 class="text-lg font-semibold text-neutral-800">{{ scene.title }}</h3>
            <p class="mt-2 text-sm text-neutral-500 leading-relaxed">{{ scene.desc }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- CTA -->
    <section class="border-t border-neutral-100 py-20">
      <div class="mx-auto max-w-3xl px-4 text-center sm:px-6 lg:px-8">
        <h2 class="text-3xl font-bold text-neutral-900">需要梳理您的平台边界？</h2>
        <p class="mt-4 text-neutral-500">请结合现有部署、服务契约和客户端范围，选择适合的空间、文件与扩展能力</p>
        <div class="mt-8 flex justify-center gap-4">
          <router-link to="/contact" class="rounded-xl bg-primary px-8 py-3 text-sm font-semibold text-white hover:bg-primary/90">联系解决方案专家</router-link>
          <router-link to="/register" class="rounded-xl border border-neutral-200 px-8 py-3 text-sm font-semibold text-neutral-600 hover:border-primary hover:text-primary">查看入口</router-link>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
// 需求编号：REQ-WEB-CONTENT-2026-07
// 改动点：将行业客户宣传卡片改为项目空间、文件生命周期、扩展自动化和安全分享等可落地场景。
// 原有行为：展示虚构客户、行业合规结果和规模指标；新行为：展示平台能力如何组合到业务场景。
// 影响范围：仅影响官网场景文案与静态展示数据，保留原有卡片布局和交互入口。
const solutions = [
  {
    tag: '项目协作', title: '以空间承载项目文件', icon: 'fa fa-cubes', bgClass: 'bg-primary/10', iconClass: 'text-primary',
    desc: '为项目创建独立空间，围绕成员、角色、权限和目录组织文件，并通过分享与操作记录连接内外部协作。',
    features: ['创建和管理团队空间', '成员邀请与角色权限管理', '文件夹、文件和标签统一组织', '分享链接和外部访问控制', '收藏、最近访问和回收站协同', '按空间启用插件扩展'],
    highlights: [{ label: '核心边界', value: '空间' }, { label: '协作对象', value: '成员' }, { label: '资源对象', value: '文件' }, { label: '权限方式', value: '角色' }],
    customers: ['项目空间', '团队空间', '外部协作', '交付资料', '部门知识'],
  },
  {
    tag: '文件处理', title: '围绕生命周期处理内容', icon: 'fa fa-file-text-o', bgClass: 'bg-success/10', iconClass: 'text-success',
    desc: '从文件上传会话开始，经过分片、合并、校验、预处理和激活，再进入预览、下载和后续自动化处理。',
    features: ['文件与文件夹基础 CURD', '分片上传与断点续传', '文件夹上传与目录结构保持', '流式下载和 Range 访问', '图片、PDF、Office、代码等在线预览', '内容就绪与可用事件驱动处理'],
    highlights: [{ label: '上传方式', value: '分片' }, { label: '下载方式', value: '流式' }, { label: '预览方式', value: '按类型' }, { label: '处理方式', value: '事件' }],
    customers: ['文件上传', '在线预览', '媒体播放', '内容处理', '归档管理'],
  },
  {
    tag: '自动化扩展', title: '让插件和工作流连接业务', icon: 'fa fa-magic', bgClass: 'bg-danger/10', iconClass: 'text-danger',
    desc: '通过能力中心把平台 API、云插件、本地扩展和工作流统一到受控执行边界中，支持按空间和权限复用能力。',
    features: ['云插件版本、清单和权限管理', '本地扩展按客户端能力分发', '工作流 DSL 校验、发布和执行', '工作流模板市场导入与审核', '文件生命周期事件触发自动化', '执行记录、重试、取消与恢复'],
    highlights: [{ label: '扩展形态', value: '3 类' }, { label: '市场类型', value: '2 类' }, { label: '执行边界', value: '受控' }, { label: '状态记录', value: '完整' }],
    customers: ['云插件', '本地扩展', '工作流', '插件市场', '工作流市场'],
  },
  {
    tag: '对外分享', title: '用清晰边界交付文件', icon: 'fa fa-share-alt', bgClass: 'bg-warning/10', iconClass: 'text-warning',
    desc: '面向外部合作方提供文件分享能力，结合访问凭证、资源范围和撤回操作，把文件交付从个人行为变成可管理的业务动作。',
    features: ['创建和管理分享链接', '分享资源支持文件与目录', '外部访问按凭证授权', '分享描述与访问上下文保留', '支持撤回和生命周期管理', '与空间权限和文件状态联动'],
    highlights: [{ label: '分享对象', value: '文件/目录' }, { label: '访问方式', value: '凭证' }, { label: '管理方式', value: '可撤回' }, { label: '边界来源', value: '权限' }],
    customers: ['外部合作', '文件交付', '资料收集', '链接分享', '安全外发'],
  },
]

const scenarios = [
  { title: '多人空间协作', desc: '用空间、成员、角色和资源范围组织项目或部门文件。', icon: 'fa fa-users', bgClass: 'bg-primary/10', iconClass: 'text-primary' },
  { title: '文件在线处理', desc: '从上传、预览到下载和媒体播放，按文件类型选择对应处理能力。', icon: 'fa fa-file-o', bgClass: 'bg-purple-50', iconClass: 'text-purple-500' },
  { title: '标签与收藏', desc: '用标签、星标和最近访问建立个人与团队的快速入口。', icon: 'fa fa-tags', bgClass: 'bg-warning/10', iconClass: 'text-warning' },
  { title: '安全文件分享', desc: '使用分享链接和访问凭证向外部交付文件，并保留管理边界。', icon: 'fa fa-share-alt', bgClass: 'bg-success/10', iconClass: 'text-success' },
  { title: '云插件与本地扩展', desc: '根据运行环境和权限范围，为平台接入可复用扩展。', icon: 'fa fa-plug', bgClass: 'bg-info/10', iconClass: 'text-info' },
  { title: '工作流自动化', desc: '把文件事件、能力中心和定时调度组合为可执行流程。', icon: 'fa fa-sitemap', bgClass: 'bg-danger/10', iconClass: 'text-danger' },
]
</script>
