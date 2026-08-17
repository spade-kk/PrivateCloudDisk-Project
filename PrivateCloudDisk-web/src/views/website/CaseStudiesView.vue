<template>
  <div>
    <section class="bg-gradient-to-br from-primary/5 via-white to-info/5 py-20 sm:py-28">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="mx-auto max-w-3xl text-center">
          <span class="inline-flex items-center gap-2 rounded-full bg-primary/10 px-3 py-1 text-xs font-medium text-primary">能力场景</span>
          <h1 class="mt-4 text-4xl font-extrabold tracking-tight text-neutral-900 sm:text-5xl">用真实能力描述典型落地方式</h1>
          <p class="mt-4 text-lg text-neutral-500">以下内容是基于当前仓库功能整理的场景示例，不代表特定客户、规模或服务指标</p>
        </div>
      </div>
    </section>

    <!-- Stats Bar -->
    <section class="border-y border-neutral-100 bg-neutral-50/50 py-8">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="grid grid-cols-2 gap-6 sm:grid-cols-4">
          <div class="text-center"><span class="text-3xl font-extrabold text-primary">空间</span><p class="mt-1 text-xs text-neutral-500">协作边界</p></div>
          <div class="text-center"><span class="text-3xl font-extrabold text-primary">文件</span><p class="mt-1 text-xs text-neutral-500">业务对象</p></div>
          <div class="text-center"><span class="text-3xl font-extrabold text-primary">预览</span><p class="mt-1 text-xs text-neutral-500">内容入口</p></div>
          <div class="text-center"><span class="text-3xl font-extrabold text-primary">自动化</span><p class="mt-1 text-xs text-neutral-500">扩展方式</p></div>
        </div>
      </div>
    </section>

    <!-- Featured Cases -->
    <section class="py-20 sm:py-24">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8 space-y-16">
        <div v-for="(cs, i) in caseStudies" :key="cs.company" class="grid grid-cols-1 items-center gap-10 lg:grid-cols-2 rounded-3xl border border-neutral-200 bg-white p-8 sm:p-10">
          <div :class="i % 2 === 1 ? 'lg:order-2' : ''">
            <div class="flex items-center gap-3 mb-4">
              <div class="flex h-14 w-14 items-center justify-center rounded-2xl bg-neutral-100 text-xl font-bold text-neutral-600">{{ cs.company.charAt(0) }}</div>
              <div>
                <p class="text-lg font-semibold text-neutral-800">{{ cs.company }}</p>
                <p class="text-sm text-neutral-400">{{ cs.industry }} · {{ cs.size }}</p>
              </div>
            </div>
            <h2 class="text-2xl font-bold text-neutral-900">{{ cs.title }}</h2>
            <p class="mt-4 text-neutral-500 leading-relaxed">{{ cs.challenge }}</p>
            <div class="mt-6 rounded-xl bg-neutral-50 p-5">
              <p class="text-xs font-semibold text-success uppercase tracking-wider mb-2">PrivateCloudDisk 能力组合</p>
              <p class="text-sm text-neutral-600 leading-relaxed">{{ cs.solution }}</p>
            </div>
            <div class="mt-6 grid grid-cols-3 gap-3">
              <div v-for="m in cs.results" :key="m.label" class="text-center rounded-xl bg-primary/5 p-3">
                <p class="text-xl font-bold text-primary">{{ m.value }}</p>
                <p class="text-[10px] text-neutral-500">{{ m.label }}</p>
              </div>
            </div>
          </div>
          <div :class="i % 2 === 1 ? 'lg:order-1' : ''">
            <div class="rounded-2xl bg-neutral-50 p-6 space-y-3">
              <p class="text-xs font-semibold text-neutral-400 uppercase tracking-wider">场景说明</p>
              <blockquote class="text-sm text-neutral-600 leading-relaxed italic">{{ cs.testimonial }}</blockquote>
              <div class="flex items-center gap-3 pt-3 border-t border-neutral-200">
                <div class="flex h-9 w-9 items-center justify-center rounded-full bg-primary/10 text-sm font-bold text-primary">{{ cs.contact.charAt(0) }}</div>
                <div>
                  <p class="text-sm font-semibold text-neutral-700">{{ cs.contact }}</p>
                  <p class="text-xs text-neutral-400">{{ cs.position }}</p>
                </div>
                <div class="ml-auto flex gap-0.5 text-warning">
                  <i v-for="n in 5" :key="n" class="fa fa-star text-xs"></i>
                </div>
              </div>
            </div>
            <div class="mt-4 rounded-2xl bg-neutral-50 p-4">
              <p class="text-xs font-semibold text-neutral-400 mb-2">使用的产品功能</p>
              <div class="flex flex-wrap gap-2">
                <span v-for="tag in cs.tags" :key="tag" class="rounded-md bg-white border border-neutral-200 px-2.5 py-1 text-xs text-neutral-600">{{ tag }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- More Customers -->
    <section class="border-t border-neutral-100 bg-neutral-50/50 py-16">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <h2 class="text-2xl font-bold text-neutral-900 text-center">更多可组合的平台能力</h2>
        <div class="mt-10 flex flex-wrap justify-center gap-6">
          <span v-for="logo in moreCustomers" :key="logo" class="rounded-xl border border-neutral-200 bg-white px-6 py-3 text-sm font-medium text-neutral-400 hover:text-primary hover:border-primary/30 transition cursor-default">{{ logo }}</span>
        </div>
      </div>
    </section>

    <section class="py-20">
      <div class="mx-auto max-w-3xl px-4 text-center sm:px-6 lg:px-8">
        <div class="rounded-3xl bg-gradient-to-br from-primary to-info p-10 sm:p-16 text-white relative overflow-hidden">
          <div class="absolute -top-20 -right-20 h-64 w-64 rounded-full bg-white/10 blur-3xl"></div>
          <div class="relative">
            <h2 class="text-3xl font-bold">从您的文件业务开始</h2>
            <p class="mt-4 text-white/80">根据当前部署和服务边界，逐步启用空间协作、预览与自动化能力</p>
            <div class="mt-8 flex justify-center gap-4">
              <router-link to="/register" class="rounded-xl bg-white px-8 py-3 text-sm font-semibold text-primary hover:bg-neutral-50">开始使用</router-link>
              <router-link to="/contact" class="rounded-xl border-2 border-white/30 px-8 py-3 text-sm font-semibold text-white hover:bg-white/10">预约演示</router-link>
            </div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
// 需求编号：REQ-WEB-CONTENT-2026-07
// 改动点：案例页改为基于当前实现的能力场景页，移除虚构客户、用户规模、成本、满意度和可用性数据。
// 原有行为：以不可核验的客户案例和结果指标建立背书；新行为：用空间协作、文件处理、自动化扩展和分享说明真实业务用法。
// 影响范围：仅影响官网展示数据和文字，保留原有案例卡片布局、标签和 CTA。
const caseStudies = [
  {
    company: '项目空间', industry: '团队协作场景', size: '空间边界',
    title: '把项目文件放进清晰的协作空间',
    challenge: '项目文件、个人资料和外部交付物需要分开管理，同时还要让成员在同一资源边界内协作。',
    solution: '创建团队空间，配置成员角色和权限，结合文件夹、标签、收藏、分享和回收站管理项目资料。',
    results: [{ label: '组织方式', value: '空间' }, { label: '协作主体', value: '成员' }, { label: '权限依据', value: '角色' }],
    testimonial: '空间把成员、资源和扩展能力放在同一个边界内，适合项目、部门和外部协作等场景。',
    contact: '平台能力', position: '空间协作',
    tags: ['团队空间', '成员管理', '角色权限', '文件分享', '空间插件'],
  },
  {
    company: '内容处理', industry: '文件生命周期场景', size: '事件驱动',
    title: '从上传到预览的文件处理链路',
    challenge: '文件上传不只是写入磁盘，还需要经过分片、合并、校验、预处理、激活和派生预览资源。',
    solution: '平台由业务服务管理元数据和状态，由存储服务处理文件 I/O，并通过事件、队列和内部 API 连接预览与自动化。',
    results: [{ label: '上传方式', value: '分片' }, { label: '传输方式', value: '流式' }, { label: '处理方式', value: '异步' }],
    testimonial: '文件状态和服务边界明确后，上传、在线预览、下载和后续处理可以独立演进。',
    contact: '平台能力', position: '文件处理',
    tags: ['文件 CURD', '分片上传', 'Range 下载', '在线预览', '媒体播放'],
  },
  {
    company: '自动化扩展', industry: '插件与工作流场景', size: '受控执行',
    title: '让文件事件触发可复用的业务流程',
    challenge: '不同团队需要不同的文件处理和业务编排逻辑，但扩展不能越过空间、权限和运行时安全边界。',
    solution: '通过插件服务、运行时、自动化、工作流和调度服务分工，实现插件版本管理、能力解析、事件执行、流程编排和市场复用。',
    results: [{ label: '扩展形态', value: '3 类' }, { label: '市场类型', value: '2 类' }, { label: '执行记录', value: '可查' }],
    testimonial: '插件和工作流使用同一套能力中心，但分别承担扩展、编排和执行职责，便于治理和复用。',
    contact: '平台能力', position: '自动化平台',
    tags: ['云插件', '本地扩展', '工作流', '能力中心', '市场'],
  },
  {
    company: '安全交付', industry: '对外分享场景', size: '按需授权',
    title: '用分享链接交付文件而不打破权限边界',
    challenge: '外部合作需要访问文件，但访问时间、资源范围和撤回能力必须独立于个人本地文件管理。',
    solution: '通过分享资源、访问凭证和分享管理接口控制外部访问，结合文件状态和空间权限保证资源边界清晰。',
    results: [{ label: '分享对象', value: '文件/目录' }, { label: '授权方式', value: '凭证' }, { label: '管理方式', value: '可撤回' }],
    testimonial: '分享能力把外部访问变成可管理的资源关系，适合文件交付、资料收集和跨团队协作。',
    contact: '平台能力', position: '文件分享',
    tags: ['分享链接', '访问凭证', '资源范围', '撤回管理', '操作追踪'],
  },
]

const moreCustomers = [
  '团队空间', '项目空间', '文件预览', '视频播放', '文件分享', '标签管理', '插件市场', '工作流市场',
  '云插件', '本地扩展', '开放 API', '私有部署',
]
</script>
