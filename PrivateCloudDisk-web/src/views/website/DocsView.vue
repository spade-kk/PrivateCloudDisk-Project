<template>
  <div class="docs-center">
    <!-- ============================================================ -->
    <!-- Hero Section -->
    <!-- ============================================================ -->
    <section class="relative overflow-hidden bg-gradient-to-br from-primary/5 via-white to-info/5 py-20 sm:py-28">
      <!-- Background decorations -->
      <div class="absolute inset-0 pointer-events-none">
        <div class="absolute -top-40 -right-40 h-96 w-96 rounded-full bg-primary/10 blur-3xl"></div>
        <div class="absolute -bottom-40 -left-40 h-96 w-96 rounded-full bg-info/10 blur-3xl"></div>
      </div>

      <div class="relative mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="mx-auto max-w-3xl text-center">
          <!-- Badge -->
          <div class="mb-6 inline-flex items-center gap-2 rounded-full border border-primary/20 bg-primary/5 px-4 py-1.5 text-xs font-medium text-primary">
            <i class="fa fa-book text-[10px]"></i>
            官方文档中心
          </div>

          <!-- Title -->
          <h1 class="text-4xl font-extrabold tracking-tight text-neutral-900 sm:text-5xl lg:text-6xl">
            PrivateCloudDisk
            <span class="mt-2 block bg-gradient-to-r from-primary to-info bg-clip-text text-transparent">
              企业级私有云盘文档
            </span>
          </h1>

          <!-- Description -->
          <p class="mx-auto mt-6 max-w-2xl text-lg leading-relaxed text-neutral-500">
            面向开发者和运维工程师的完整技术文档，涵盖架构设计、安全机制、部署运维、开发指南等内容。
          </p>

          <!-- GitHub Link -->
          <div class="mt-6 flex items-center justify-center gap-4">
            <a href="https://github.com/your-repo/PrivateCloudDisk" target="_blank"
               class="inline-flex items-center gap-2 rounded-lg bg-neutral-900 px-4 py-2 text-sm font-medium text-white transition hover:bg-neutral-800">
              <i class="fa fa-github text-base"></i>
              GitHub 仓库
            </a>
            <a href="https://github.com/your-repo/PrivateCloudDisk/stargazers" target="_blank"
               class="inline-flex items-center gap-2 rounded-lg border border-neutral-200 px-4 py-2 text-sm font-medium text-neutral-600 transition hover:border-primary hover:text-primary">
              <i class="fa fa-star text-warning"></i>
              收藏项目
            </a>
          </div>

          <!-- Version Badge -->
          <div class="mt-4 inline-flex items-center gap-2 rounded-full border border-neutral-200 bg-white px-3 py-1 text-xs text-neutral-500">
            <span class="h-2 w-2 rounded-full bg-success"></span>
            当前版本以仓库构建与部署配置为准 · 文档持续更新
          </div>

          <!-- Search -->
          <div class="relative mx-auto mt-8 max-w-xl">
            <i class="fa fa-search absolute left-4 top-1/2 -translate-y-1/2 text-neutral-400"></i>
            <input v-model="searchQuery" type="text" placeholder="搜索文档..." 
                   class="w-full rounded-xl border border-neutral-200 bg-white py-3 pl-11 pr-4 text-sm focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/10 shadow-sm" />
          </div>
        </div>
      </div>
    </section>

    <!-- ============================================================ -->
    <!-- Project Stats & Quick Info -->
    <!-- ============================================================ -->
    <section class="border-y border-neutral-100 bg-white py-12">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="grid grid-cols-2 gap-8 sm:grid-cols-4">
          <div v-for="stat in projectStats" :key="stat.label" class="text-center">
            <div class="text-2xl font-bold text-primary">{{ stat.value }}</div>
            <div class="mt-1 text-xs text-neutral-500">{{ stat.label }}</div>
          </div>
        </div>
      </div>
    </section>

    <!-- ============================================================ -->
    <!-- Main Documentation Navigation -->
    <!-- ============================================================ -->
    <section class="py-16 sm:py-24">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <!-- Section Header -->
        <div class="mx-auto max-w-2xl text-center mb-12">
          <h2 class="text-3xl font-bold tracking-tight text-neutral-900">文档导航</h2>
          <p class="mt-4 text-neutral-500">选择您感兴趣的模块，开始探索 PrivateCloudDisk</p>
        </div>

        <!-- Main Navigation Cards -->
        <div class="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
          <router-link v-for="doc in docNavigation" :key="doc.key" 
                       :to="doc.path"
                       class="group rounded-2xl border border-neutral-200 bg-white p-6 transition-all duration-300 hover:border-primary/30 hover:shadow-xl hover:-translate-y-1">
            <div class="flex h-12 w-12 items-center justify-center rounded-xl transition-colors" :class="doc.bgClass">
              <i :class="[doc.icon, 'text-xl', doc.iconClass]"></i>
            </div>
            <h3 class="mt-4 text-lg font-semibold text-neutral-800 group-hover:text-primary transition-colors">{{ doc.label }}</h3>
            <p class="mt-2 text-sm text-neutral-400 line-clamp-2">{{ doc.desc }}</p>
            <div class="mt-4 flex items-center gap-1 text-xs font-medium text-primary opacity-0 group-hover:opacity-100 transition-opacity">
              阅读文档 <i class="fa fa-arrow-right text-[10px]"></i>
            </div>
          </router-link>
        </div>
      </div>
    </section>

    <!-- ============================================================ -->
    <!-- Feature Highlights -->
    <!-- ============================================================ -->
    <section class="border-t border-neutral-100 bg-neutral-50/50 py-16 sm:py-24">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="mx-auto max-w-2xl text-center mb-12">
          <h2 class="text-3xl font-bold tracking-tight text-neutral-900">核心特性</h2>
          <p class="mt-4 text-neutral-500">PrivateCloudDisk 的文件、空间、预览、扩展、部署和开发文档</p>
        </div>

        <div class="grid grid-cols-1 gap-8 sm:grid-cols-2 lg:grid-cols-3">
          <div v-for="feature in coreFeatures" :key="feature.title" 
               class="rounded-2xl border border-neutral-200 bg-white p-6 transition hover:-translate-y-1 hover:shadow-lg">
            <div class="flex h-12 w-12 items-center justify-center rounded-xl" :class="feature.bgClass">
              <i :class="[feature.icon, 'text-xl', feature.iconClass]"></i>
            </div>
            <h3 class="mt-4 text-base font-semibold text-neutral-800">{{ feature.title }}</h3>
            <p class="mt-2 text-sm text-neutral-500 leading-relaxed">{{ feature.desc }}</p>
            <ul class="mt-4 space-y-2">
              <li v-for="item in feature.items" :key="item" class="flex items-start gap-2 text-xs text-neutral-600">
                <i class="fa fa-check-circle text-success mt-0.5"></i>
                {{ item }}
              </li>
            </ul>
          </div>
        </div>
      </div>
    </section>

    <!-- ============================================================ -->
    <!-- Technology Stack -->
    <!-- ============================================================ -->
    <section class="py-16 sm:py-24">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="grid grid-cols-1 items-center gap-12 lg:grid-cols-2">
          <div>
            <span class="inline-flex items-center gap-2 rounded-full bg-primary/10 px-3 py-1 text-xs font-medium text-primary">技术栈</span>
            <h2 class="mt-4 text-3xl font-bold text-neutral-900 sm:text-4xl">现代化技术架构</h2>
            <p class="mt-4 text-neutral-500 leading-relaxed">
              基于业界主流技术栈构建，采用微服务架构，支持高并发、高可用、易扩展的部署方案。
            </p>
            <div class="mt-6 space-y-3">
              <div v-for="tech in techStack" :key="tech.category" class="flex items-start gap-3">
                <div class="flex h-8 w-8 items-center justify-center rounded-lg bg-primary/10">
                  <i :class="[tech.icon, 'text-sm text-primary']"></i>
                </div>
                <div>
                  <p class="text-sm font-medium text-neutral-800">{{ tech.category }}</p>
                  <p class="text-xs text-neutral-500">{{ tech.items }}</p>
                </div>
              </div>
            </div>
            <router-link to="/docs/architecture" 
                         class="mt-8 inline-flex items-center gap-2 rounded-lg bg-primary px-5 py-2.5 text-sm font-semibold text-white transition hover:bg-primary/90">
              查看完整架构文档 <i class="fa fa-arrow-right text-xs"></i>
            </router-link>
          </div>

          <!-- Tech Stack Diagram -->
          <div class="rounded-2xl border border-neutral-200 bg-neutral-50 p-6">
            <div class="space-y-4">
              <div v-for="layer in techLayers" :key="layer.name" 
                   class="rounded-xl border border-neutral-200 bg-white p-4">
                <div class="flex items-center gap-2 mb-3">
                  <span class="h-3 w-3 rounded-full" :class="layer.color"></span>
                  <span class="text-xs font-semibold text-neutral-700">{{ layer.name }}</span>
                </div>
                <div class="flex flex-wrap gap-2">
                  <span v-for="tech in layer.techs" :key="tech"
                        class="rounded-md border border-neutral-200 bg-white px-2 py-1 text-xs text-neutral-600">
                    {{ tech }}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ============================================================ -->
    <!-- Deployment Options -->
    <!-- ============================================================ -->
    <section class="border-t border-neutral-100 bg-neutral-50/50 py-16 sm:py-24">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="mx-auto max-w-2xl text-center mb-12">
          <h2 class="text-3xl font-bold tracking-tight text-neutral-900">部署方式</h2>
          <p class="mt-4 text-neutral-500">支持多种部署方案，满足不同规模企业的需求</p>
        </div>

        <div class="grid grid-cols-1 gap-6 lg:grid-cols-3">
          <div v-for="deploy in deploymentOptions" :key="deploy.title"
               class="rounded-2xl border border-neutral-200 bg-white p-6 transition hover:-translate-y-1 hover:shadow-lg">
            <div class="flex h-12 w-12 items-center justify-center rounded-xl" :class="deploy.bgClass">
              <i :class="[deploy.icon, 'text-xl', deploy.iconClass]"></i>
            </div>
            <h3 class="mt-4 text-lg font-semibold text-neutral-800">{{ deploy.title }}</h3>
            <p class="mt-2 text-sm text-neutral-500">{{ deploy.desc }}</p>
            <ul class="mt-4 space-y-2">
              <li v-for="item in deploy.items" :key="item" class="flex items-start gap-2 text-xs text-neutral-600">
                <i :class="item.icon === 'check' ? 'fa fa-check-circle text-success' : 'fa fa-circle text-neutral-300'"
                   class="mt-0.5 text-[10px]"></i>
                {{ item.text }}
              </li>
            </ul>
            <router-link :to="deploy.path"
                         class="mt-6 inline-flex items-center gap-1 text-xs font-medium text-primary hover:underline">
              查看部署文档 <i class="fa fa-arrow-right text-[10px]"></i>
            </router-link>
          </div>
        </div>
      </div>
    </section>

    <!-- ============================================================ -->
    <!-- Quick Start -->
    <!-- ============================================================ -->
    <section class="py-16 sm:py-24">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="mx-auto max-w-2xl text-center mb-12">
          <h2 class="text-3xl font-bold tracking-tight text-neutral-900">快速入门</h2>
          <p class="mt-4 text-neutral-500">只需五步，快速部署并使用 PrivateCloudDisk</p>
        </div>

        <div class="mx-auto max-w-4xl">
          <div class="space-y-4">
            <div v-for="(step, i) in quickStartSteps" :key="i"
                 class="flex gap-4 rounded-2xl border border-neutral-200 bg-white p-6 transition hover:border-primary/20">
              <div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-primary text-sm font-bold text-white">
                {{ i + 1 }}
              </div>
              <div class="flex-1">
                <h3 class="text-base font-semibold text-neutral-800">{{ step.title }}</h3>
                <p class="mt-1 text-sm text-neutral-500">{{ step.desc }}</p>
                <div v-if="step.code" class="mt-3 rounded-lg bg-neutral-900 p-3 font-mono text-xs text-neutral-300">
                  {{ step.code }}
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ============================================================ -->
    <!-- FAQ Section -->
    <!-- ============================================================ -->
    <section class="border-t border-neutral-100 bg-neutral-50/50 py-16 sm:py-24">
      <div class="mx-auto max-w-3xl px-4 sm:px-6 lg:px-8">
        <div class="mx-auto max-w-2xl text-center mb-12">
          <h2 class="text-3xl font-bold tracking-tight text-neutral-900">常见问题</h2>
          <p class="mt-4 text-neutral-500">您可能想了解的问题</p>
        </div>

        <div class="space-y-3">
          <div v-for="(faq, i) in faqs" :key="i"
               class="rounded-xl border border-neutral-200 bg-white p-5 transition hover:border-primary/20">
            <button @click="expandedFaq = expandedFaq === i ? null : i"
                    class="flex w-full items-center justify-between text-left">
              <span class="text-sm font-medium text-neutral-700">{{ faq.q }}</span>
              <i :class="[expandedFaq === i ? 'fa fa-angle-up' : 'fa fa-angle-down', 'text-neutral-400 shrink-0 ml-3']"></i>
            </button>
            <p v-if="expandedFaq === i" class="mt-3 text-sm text-neutral-500 leading-relaxed">{{ faq.a }}</p>
          </div>
        </div>

        <div class="mt-8 text-center">
          <router-link to="/docs/guide"
                       class="inline-flex items-center gap-2 text-sm font-medium text-primary hover:underline">
            查看更多问题 <i class="fa fa-arrow-right text-xs"></i>
          </router-link>
        </div>
      </div>
    </section>

    <!-- ============================================================ -->
    <!-- Community & Support -->
    <!-- ============================================================ -->
    <section class="py-16 sm:py-24">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="grid grid-cols-1 gap-6 lg:grid-cols-3">
          <div v-for="community in communityLinks" :key="community.title"
               class="rounded-2xl border border-neutral-200 bg-white p-6 text-center transition hover:-translate-y-1 hover:shadow-lg">
            <div class="mx-auto flex h-12 w-12 items-center justify-center rounded-xl" :class="community.bgClass">
              <i :class="[community.icon, 'text-xl', community.iconClass]"></i>
            </div>
            <h3 class="mt-4 text-base font-semibold text-neutral-800">{{ community.title }}</h3>
            <p class="mt-2 text-sm text-neutral-500">{{ community.desc }}</p>
            <a :href="community.link" target="_blank"
               class="mt-4 inline-flex items-center gap-1 text-xs font-medium text-primary hover:underline">
              {{ community.action }} <i class="fa fa-external-link text-[10px]"></i>
            </a>
          </div>
        </div>
      </div>
    </section>

    <!-- ============================================================ -->
    <!-- Still Need Help -->
    <!-- ============================================================ -->
    <section class="border-t border-neutral-100 bg-neutral-50/50 py-16">
      <div class="mx-auto max-w-3xl px-4 text-center sm:px-6 lg:px-8">
        <h2 class="text-2xl font-bold text-neutral-900">仍有疑问？</h2>
        <p class="mt-2 text-neutral-500">我们的技术支持团队随时为您提供帮助</p>
        <div class="mt-6 flex justify-center gap-4">
          <router-link to="/contact"
                       class="rounded-xl bg-primary px-6 py-2.5 text-sm font-semibold text-white hover:bg-primary/90">
            联系我们
          </router-link>
          <a href="https://github.com/your-repo/PrivateCloudDisk/issues" target="_blank"
             class="rounded-xl border border-neutral-200 px-6 py-2.5 text-sm font-semibold text-neutral-600 hover:border-primary hover:text-primary">
            提交 Issue
          </a>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'

const searchQuery = ref('')
const expandedFaq = ref<number | null>(null)

const projectStats = [
  { value: '3.0.0', label: '当前版本' },
  { value: '10K+', label: '企业用户' },
  { value: '19+', label: '核心模块' },
  { value: 'MIT', label: '开源协议' },
]

const docNavigation = [
  {
    key: 'guide',
    path: '/docs/guide',
    label: '快速入门',
    desc: '5分钟快速部署，运行第一个实例',
    icon: 'fa fa-rocket',
    iconClass: 'text-primary',
    bgClass: 'bg-primary/10',
  },
  {
    key: 'architecture',
    path: '/docs/architecture',
    label: '架构设计',
    desc: '系统架构、微服务拓扑、技术选型',
    icon: 'fa fa-puzzle-piece',
    iconClass: 'text-purple-500',
    bgClass: 'bg-purple-50',
  },
  {
    key: 'deployment',
    path: '/docs/deployment',
    label: '部署指南',
    desc: 'Docker Compose、生产环境配置',
    icon: 'fa fa-cloud-upload',
    iconClass: 'text-info',
    bgClass: 'bg-info/10',
  },
  {
    key: 'development',
    path: '/docs/development',
    label: '开发指南',
    desc: '环境搭建、本地开发、调试技巧',
    icon: 'fa fa-code',
    iconClass: 'text-warning',
    bgClass: 'bg-warning/10',
  },
  {
    key: 'api',
    path: '/docs/api',
    label: 'API 参考',
    desc: 'REST API、认证方式、错误码',
    icon: 'fa fa-plug',
    iconClass: 'text-success',
    bgClass: 'bg-success/10',
  },
  {
    key: 'security',
    path: '/docs/security',
    label: '安全文档',
    desc: '认证鉴权、加密机制、权限控制',
    icon: 'fa fa-shield',
    iconClass: 'text-danger',
    bgClass: 'bg-danger/10',
  },
  {
    key: 'database',
    path: '/docs/database',
    label: '数据库设计',
    desc: '表结构、闭包表、索引策略',
    icon: 'fa fa-database',
    iconClass: 'text-primary',
    bgClass: 'bg-primary/10',
  },
  {
    key: 'customize',
    path: '/docs/customize',
    label: '自定义扩展',
    desc: '二次开发、插件机制、升级指南',
    icon: 'fa fa-wrench',
    iconClass: 'text-neutral-500',
    bgClass: 'bg-neutral-100',
  },
]

const coreFeatures = [
  {
    title: '断点续传与分片上传',
    desc: '支持分片上传与断点续传，具体大小限制以部署配置和服务校验为准',
    icon: 'fa fa-cloud-upload',
    iconClass: 'text-primary',
    bgClass: 'bg-primary/10',
    items: ['分片上传会话', '上传状态查询', '合并后异步处理', '失败状态可追踪'],
  },
  {
    title: '企业级安全防护',
    desc: '认证、空间上下文、操作凭证和内部服务校验共同构成访问边界',
    icon: 'fa fa-shield',
    iconClass: 'text-success',
    bgClass: 'bg-success/10',
    items: ['网关 JWT 认证', '空间成员与资源范围', '预览/下载操作凭证', '内部服务令牌校验'],
  },
  {
    title: '智能文件预览',
    desc: '支持多类文件在线预览，无需下载即可查看；实际格式以服务配置和文件类型为准',
    icon: 'fa fa-eye',
    iconClass: 'text-info',
    bgClass: 'bg-info/10',
    items: ['Office文档预览', '图片缩略图', 'PDF在线阅读', '代码高亮显示'],
  },
  {
    title: '高效目录树管理',
    desc: '围绕文件夹节点和资源关系提供目录树管理',
    icon: 'fa fa-folder-tree',
    iconClass: 'text-warning',
    bgClass: 'bg-warning/10',
    items: ['文件夹 CRUD', '目录节点查询', '移动与复制', '路径导航'],
  },
  {
    title: '团队协作与分享',
    desc: '空间、角色、分享链接和回收站共同支撑协作边界',
    icon: 'fa fa-users',
    iconClass: 'text-purple-500',
    bgClass: 'bg-purple-50',
    items: ['空间成员管理', '分享资源控制', '收藏与标签', '回收站恢复'],
  },
  {
    title: '分布式限流与高可用',
    desc: '通过异步队列、任务状态和部署配置组织文件处理链路',
    icon: 'fa fa-server',
    iconClass: 'text-danger',
    bgClass: 'bg-danger/10',
    items: ['RabbitMQ 异步任务', '文件处理 Worker', '健康检查接口', '监控与链路接入'],
  },
]

const techStack = [
  { category: '前端技术', icon: 'fa fa-desktop', items: 'Vue 3 + Vite + Tailwind CSS + Pinia' },
  { category: '后端网关', icon: 'fa fa-gateway', items: 'Spring Cloud Gateway + WebFlux + JWT' },
  { category: '业务服务', icon: 'fa fa-cogs', items: 'Spring Boot 3.4.7 + MyBatis + MySQL' },
  { category: '文件服务', icon: 'fa fa-file', items: 'FastAPI + Uvicorn + Python 3.11' },
  { category: '即时通讯', icon: 'fa fa-comments', items: 'Spring Boot + Netty + WebRTC' },
  { category: '通知与扩展', icon: 'fa fa-bell', items: 'Notification Service + Plugin / Workflow Services' },
  { category: '数据存储', icon: 'fa fa-database', items: 'MySQL 8.0 + Redis 7 + MinIO' },
  { category: '消息队列', icon: 'fa fa-envelope', items: 'RabbitMQ + OpenSearch' },
  { category: '监控链路', icon: 'fa fa-chart-line', items: 'Prometheus + Grafana + SkyWalking' },
]

const techLayers = [
  {
    name: '客户端层',
    color: 'bg-blue-500',
    techs: ['Vue 3 Web', 'React 19 Admin', 'Electron Desktop', 'uni-app Mobile', 'SwiftUI iOS', 'SwiftUI macOS', 'Kotlin Android', 'WPF .NET Windows', 'Go CLI'],
  },
  {
    name: '网关层',
    color: 'bg-orange-500',
    techs: ['Nginx', 'Spring Cloud Gateway', 'JWT鉴权', '限流熔断', 'CORS'],
  },
  {
    name: '业务服务层',
    color: 'bg-green-500',
    techs: ['Platform Service', 'Notification Service', 'Billing Service', 'IM Platform', 'Plugin Service', 'Workflow Service'],
  },
  {
    name: '文件服务层',
    color: 'bg-purple-500',
    techs: ['File Service (FastAPI)', 'File Worker', 'MinIO SDK', 'pyvips'],
  },
  {
    name: 'IM服务层',
    color: 'bg-pink-500',
    techs: ['IM Server (Netty)', 'WebRTC', 'WebSocket', '消息推送'],
  },
  {
    name: '中间件层',
    color: 'bg-red-500',
    techs: ['MySQL', 'Redis', 'RabbitMQ', 'MinIO', 'OpenSearch'],
  },
  {
    name: '治理与监控',
    color: 'bg-cyan-500',
    techs: ['Docker Compose', '健康检查', 'Prometheus', 'Grafana', 'SkyWalking'],
  },
]

const deploymentOptions = [
  {
    title: 'Docker Compose',
    desc: '适用于开发和测试环境，一键部署所有服务',
    icon: 'fa fa-docker',
    iconClass: 'text-info',
    bgClass: 'bg-info/10',
    items: [
      { text: '单机一键部署', icon: 'check' },
      { text: '自动服务发现', icon: 'check' },
      { text: '数据持久化', icon: 'check' },
      { text: '水平扩展限制', icon: 'minus' },
    ],
    path: '/docs/deployment',
  },
  {
    title: 'Kubernetes',
    desc: '适用于生产环境，支持高可用和自动扩缩容',
    icon: 'fa fa-dharmachakra',
    iconClass: 'text-primary',
    bgClass: 'bg-primary/10',
    items: [
      { text: '高可用集群部署', icon: 'check' },
      { text: '自动扩缩容', icon: 'check' },
      { text: '滚动更新', icon: 'check' },
      { text: '需要K8s经验', icon: 'minus' },
    ],
    path: '/docs/deployment',
  },
  {
    title: '手动编译部署',
    desc: '适用于特殊定制需求，完全掌控每个环节',
    icon: 'fa fa-terminal',
    iconClass: 'text-success',
    bgClass: 'bg-success/10',
    items: [
      { text: '完全定制化', icon: 'check' },
      { text: '详细日志掌控', icon: 'check' },
      { text: '优化资源配置', icon: 'check' },
      { text: '部署复杂度高', icon: 'minus' },
    ],
    path: '/docs/development',
  },
]

const quickStartSteps = [
  {
    title: '克隆项目代码',
    desc: '从 GitHub 克隆 PrivateCloudDisk 项目到本地',
    code: 'git clone https://github.com/your-repo/PrivateCloudDisk.git',
  },
  {
    title: '配置环境变量',
    desc: '复制环境变量模板文件并配置数据库、Redis 等连接信息',
    code: 'cp .env.example .env && vim .env',
  },
  {
    title: '启动基础服务',
    desc: '使用 Docker Compose 启动 MySQL、Redis、RabbitMQ 等中间件服务',
    code: 'docker compose up -d mysql redis rabbitmq minio',
  },
  {
    title: '启动后端服务',
    desc: '分别启动网关服务、平台服务、文件服务',
    code: './gradlew bootRun # Java 服务\nuvicorn app.main:app --reload # storage-service',
  },
  {
    title: '访问 Web 控制台',
    desc: '打开浏览器访问部署方配置的 Web 地址，使用部署初始化的账户登录',
    code: '账号与密码：由部署初始化或测试环境配置提供',
  },
]

const faqs = [
  {
    q: 'PrivateCloudDisk 支持哪些操作系统？',
    a: '系统服务主要通过 Docker Compose 组织，客户端包含 Web、桌面端、移动端和原生客户端目录；具体平台支持以对应子项目 README 和实际构建结果为准。',
  },
  {
    q: '如何获取技术支持？',
    a: '请先阅读根 README、docs 目录和对应子项目 README；遇到问题时提交包含服务、请求路径、日志和复现步骤的 Issue。',
  },
  {
    q: '数据存储在哪里？是否安全？',
    a: '数据由平台业务元数据、文件存储、缓存和消息队列共同承载，具体使用本地存储或对象存储取决于部署配置；加密、备份和网络隔离需要结合生产环境验证。',
  },
  {
    q: '支持横向扩展吗？',
    a: '服务按网关、平台业务、文件处理、通知、即时通讯、插件、工作流、自动化和调度等边界拆分，部分服务可以按部署拓扑扩展；扩展能力需结合数据库、消息队列和存储的实际配置验证。',
  },
  {
    q: '开源版本和商业版本有什么区别？',
    a: '仓库当前重点提供文件管理、空间协作、预览、分享、插件和工作流等代码能力。授权、商业支持、SLA 和定制服务不在本页面作未经确认的承诺。',
  },
]
const communityLinks = [
  {
    title: 'GitHub Issues',
    desc: '报告Bug或提交功能请求',
    icon: 'fa fa-github',
    iconClass: 'text-neutral-700',
    bgClass: 'bg-neutral-100',
    link: 'https://github.com/your-repo/PrivateCloudDisk/issues',
    action: '前往反馈',
  },
  {
    title: '开发者社区',
    desc: '与开发者交流、分享经验',
    icon: 'fa fa-comments',
    iconClass: 'text-primary',
    bgClass: 'bg-primary/10',
    link: 'https://github.com/your-repo/PrivateCloudDisk/discussions',
    action: '加入讨论',
  },
  {
    title: '贡献代码',
    desc: '查看贡献指南，一起完善项目',
    icon: 'fa fa-code-branch',
    iconClass: 'text-success',
    bgClass: 'bg-success/10',
    link: 'https://github.com/your-repo/PrivateCloudDisk/blob/main/CONTRIBUTING.md',
    action: '贡献指南',
  },
]
</script>
