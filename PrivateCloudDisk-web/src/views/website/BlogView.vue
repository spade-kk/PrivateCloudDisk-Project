<template>
  <div>
    <section class="bg-gradient-to-br from-primary/5 via-white to-info/5 py-20 sm:py-28">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="mx-auto max-w-3xl text-center">
          <span class="inline-flex items-center gap-2 rounded-full bg-primary/10 px-3 py-1 text-xs font-medium text-primary">技术博客</span>
          <h1 class="mt-4 text-4xl font-extrabold tracking-tight text-neutral-900 sm:text-5xl">PrivateCloudDisk 技术文档</h1>
          <p class="mt-4 text-lg text-neutral-500">空间、文件、预览、插件、工作流和服务边界的项目说明</p>
        </div>
      </div>
    </section>

    <!-- Category Filter -->
    <section class="border-b border-neutral-100">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="flex items-center gap-2 overflow-x-auto py-4 no-scrollbar">
          <button v-for="cat in categories" :key="cat" @click="activeCategory = cat"
            :class="['rounded-full px-4 py-1.5 text-sm font-medium whitespace-nowrap transition', activeCategory === cat ? 'bg-primary text-white' : 'bg-neutral-100 text-neutral-600 hover:bg-neutral-200']">
            {{ cat }}
          </button>
        </div>
      </div>
    </section>

    <!-- Featured Post -->
    <section v-if="featuredPost" class="py-16 sm:py-20">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="grid grid-cols-1 gap-8 lg:grid-cols-2 rounded-3xl border border-neutral-200 overflow-hidden">
          <div class="bg-gradient-to-br from-primary/10 to-info/10 p-10 sm:p-14 flex items-center">
            <div>
              <span class="rounded-md bg-primary/10 px-2.5 py-1 text-xs font-medium text-primary">{{ featuredPost.category }}</span>
              <h2 class="mt-4 text-2xl font-bold text-neutral-900 sm:text-3xl">{{ featuredPost.title }}</h2>
              <p class="mt-3 text-neutral-500 leading-relaxed">{{ featuredPost.excerpt }}</p>
              <div class="mt-6 flex items-center gap-3">
                <div class="flex h-9 w-9 items-center justify-center rounded-full bg-primary/20 text-sm font-bold text-primary">{{ featuredPost.author.charAt(0) }}</div>
                <div>
                  <p class="text-sm font-semibold text-neutral-700">{{ featuredPost.author }}</p>
                  <p class="text-xs text-neutral-400">{{ featuredPost.date }} · {{ featuredPost.readTime }}</p>
                </div>
              </div>
              <a href="#" class="mt-6 inline-flex items-center gap-2 rounded-xl bg-primary px-6 py-2.5 text-sm font-semibold text-white hover:bg-primary/90">阅读全文 <i class="fa fa-arrow-right text-xs"></i></a>
            </div>
          </div>
          <div class="bg-neutral-900 p-10 sm:p-14 flex items-center justify-center">
            <div class="text-center text-neutral-500">
              <i class="fa fa-rss text-6xl text-neutral-700 mb-4"></i>
              <p class="text-sm">PrivateCloudDisk Project Notes</p>
              <p class="text-xs text-neutral-600 mt-1">探索技术前沿，分享工程实践</p>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- Post Grid -->
    <section class="py-16 sm:py-20">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="grid grid-cols-1 gap-8 md:grid-cols-2 lg:grid-cols-3">
          <article v-for="post in filteredPosts" :key="post.title" class="group rounded-2xl border border-neutral-200 overflow-hidden transition-all hover:border-primary/20 hover:shadow-lg hover:-translate-y-1 cursor-pointer">
            <div class="p-6">
              <div class="flex items-center gap-2 text-xs mb-3">
                <span class="rounded-md bg-primary/10 px-2 py-0.5 font-medium text-primary">{{ post.category }}</span>
                <span class="text-neutral-400">{{ post.date }}</span>
              </div>
              <h3 class="text-lg font-semibold text-neutral-800 group-hover:text-primary transition line-clamp-2">{{ post.title }}</h3>
              <p class="mt-2 text-sm text-neutral-500 leading-relaxed line-clamp-3">{{ post.excerpt }}</p>
              <div class="mt-4 flex items-center justify-between border-t border-neutral-100 pt-4">
                <div class="flex items-center gap-2">
                  <div class="flex h-7 w-7 items-center justify-center rounded-full bg-neutral-100 text-xs font-bold text-neutral-500">{{ post.author.charAt(0) }}</div>
                  <span class="text-xs text-neutral-500">{{ post.author }}</span>
                </div>
                <div class="flex items-center gap-3 text-xs text-neutral-400">
                  <span><i class="fa fa-clock-o mr-1"></i>{{ post.readTime }}</span>
                  <span><i class="fa fa-eye mr-1"></i>{{ post.views }}</span>
                </div>
              </div>
            </div>
          </article>
        </div>
        <!-- Pagination -->
        <div class="mt-12 flex items-center justify-center gap-2">
          <button class="flex h-9 w-9 items-center justify-center rounded-lg border border-neutral-200 text-sm text-neutral-400 hover:border-primary hover:text-primary disabled:opacity-30" disabled><i class="fa fa-angle-left"></i></button>
          <button class="flex h-9 w-9 items-center justify-center rounded-lg bg-primary text-sm font-medium text-white">1</button>
          <button class="flex h-9 w-9 items-center justify-center rounded-lg border border-neutral-200 text-sm text-neutral-600 hover:border-primary hover:text-primary">2</button>
          <button class="flex h-9 w-9 items-center justify-center rounded-lg border border-neutral-200 text-sm text-neutral-600 hover:border-primary hover:text-primary">3</button>
          <span class="px-1 text-neutral-300">...</span>
          <button class="flex h-9 w-9 items-center justify-center rounded-lg border border-neutral-200 text-sm text-neutral-600 hover:border-primary hover:text-primary">8</button>
          <button class="flex h-9 w-9 items-center justify-center rounded-lg border border-neutral-200 text-sm text-neutral-600 hover:border-primary hover:text-primary"><i class="fa fa-angle-right"></i></button>
        </div>
      </div>
    </section>

    <!-- Newsletter -->
    <section class="border-t border-neutral-100 bg-neutral-50/50 py-16">
      <div class="mx-auto max-w-3xl px-4 text-center sm:px-6 lg:px-8">
        <h2 class="text-2xl font-bold text-neutral-900">阅读项目文档</h2>
        <p class="mt-2 text-neutral-500">从根 README、架构文档和服务 README 了解当前实现</p>
        <form @submit.prevent class="mt-6 flex gap-3 justify-center">
          <input type="email" placeholder="输入您的邮箱" class="w-64 rounded-xl border border-neutral-200 px-4 py-2.5 text-sm focus:border-primary focus:outline-none" />
          <button type="submit" class="rounded-xl bg-primary px-6 py-2.5 text-sm font-semibold text-white hover:bg-primary/90">订阅</button>
        </form>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'

const activeCategory = ref('全部')

const categories = ['全部', '产品能力', '技术分享', '架构说明', '平台扩展']

const featuredPost = {
  title: '空间协作与文件资源边界',
  category: '产品能力',
  excerpt: '介绍空间、成员、角色、目录和分享资源之间的关系，以及如何组织项目文件。',
  author: '项目文档', date: '2026-07-29', readTime: '按需阅读',
}

const allPosts = [
  { title: '空间协作与文件资源边界', category: '产品能力', date: '2026-07-29', excerpt: '介绍空间、成员、角色、目录和分享资源之间的关系，以及如何组织项目文件。', author: '项目文档', readTime: '按需阅读', views: '文档' },
  { title: '文件预览与异步处理链路', category: '技术分享', date: '2026-07-29', excerpt: '梳理分片上传、内容处理、预览资源、缩略图和媒体播放之间的服务协作。', author: '项目文档', readTime: '按需阅读', views: '文档' },
  { title: '从单体边界到微服务职责：PrivateCloudDisk 架构导读', category: '架构说明', date: '2026-07-29', excerpt: '从网关、平台、存储、通知、即时通讯到插件和工作流服务，梳理当前仓库的职责边界。', author: '项目文档', readTime: '按需阅读', views: '文档' },
  { title: '分片上传与文件处理状态', category: '技术分享', date: '2026-07-29', excerpt: '说明上传会话、分片、合并、内容处理和失败状态如何由 Platform、Storage 与 Worker 协作。', author: '项目文档', readTime: '按需阅读', views: '文档' },
  { title: '文件在线预览与预览资源管理', category: '安全研究', date: '2026-07-29', excerpt: '介绍预览令牌、缩略图、Office/PDF 处理和内容访问边界的实现方式。', author: '项目文档', readTime: '按需阅读', views: '文档' },
  { title: '空间上下文如何贯穿文件业务', category: '最佳实践', date: '2026-07-29', excerpt: '说明空间成员、资源范围、配额和搜索字段如何在服务之间保持一致。', author: '项目文档', readTime: '按需阅读', views: '文档' },
  { title: '插件、工作流与市场的服务分工', category: '平台扩展', date: '2026-07-29', excerpt: '从插件服务、运行时、自动化、工作流和调度服务的职责出发，解释平台扩展链路。', author: '项目文档', readTime: '按需阅读', views: '文档' },
  { title: '用事件驱动文件自动化处理', category: '技术分享', date: '2026-07-29', excerpt: '围绕内容就绪和文件可用事件，说明异步处理、幂等和执行状态如何协同。', author: '项目文档', readTime: '按需阅读', views: '文档' },
  { title: '部署、契约与服务边界检查清单', category: '架构说明', date: '2026-07-29', excerpt: '从 Compose、Gateway 路由、服务 DNS、事件契约和各子项目 README 出发复核部署链路。', author: '项目文档', readTime: '按需阅读', views: '文档' },
]

const filteredPosts = computed(() => {
  if (activeCategory.value === '全部') return allPosts
  return allPosts.filter(p => p.category === activeCategory.value)
})
</script>

<style scoped>
.no-scrollbar::-webkit-scrollbar { display: none; }
.no-scrollbar { -ms-overflow-style: none; scrollbar-width: none; }
</style>
