<template>
  <div>
    <section class="bg-gradient-to-br from-primary/5 via-white to-info/5 py-20 sm:py-28">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="mx-auto max-w-3xl text-center">
          <span class="inline-flex items-center gap-2 rounded-full bg-primary/10 px-3 py-1 text-xs font-medium text-primary">技术博客</span>
          <h1 class="mt-4 text-4xl font-extrabold tracking-tight text-neutral-900 sm:text-5xl">CloudDrive 技术博客</h1>
          <p class="mt-4 text-lg text-neutral-500">产品更新、技术分享、最佳实践、行业洞察——了解 CloudDrive 最新动态</p>
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
              <p class="text-sm">CloudDrive Engineering Blog</p>
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
        <h2 class="text-2xl font-bold text-neutral-900">订阅 CloudDrive 技术博客</h2>
        <p class="mt-2 text-neutral-500">第一时间获取产品更新、技术分享和行业洞察</p>
        <form @submit.prevent class="mt-6 flex gap-3 justify-center">
          <input type="email" placeholder="输入您的邮箱" class="w-64 rounded-xl border border-neutral-200 px-4 py-2.5 text-sm focus:border-primary focus:outline-none" />
          <button type="submit" class="rounded-xl bg-primary px-6 py-2.5 text-sm font-semibold text-white hover:bg-primary/90">订阅</button>
        </form>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'

const activeCategory = ref('全部')

const categories = ['全部', '产品更新', '技术分享', '最佳实践', '案例分享', '安全研究', '行业洞察']

const featuredPost = {
  title: 'CloudDrive v3.2 发布：AI 智能搜索与全新协作体验',
  category: '产品更新',
  excerpt: '全新的 AI 驱动文件搜索引擎让文件查找速度提升 10 倍，支持自然语言查询、OCR 图片文字搜索、语义理解等能力。同时带来全新的团队协作空间、实时协同编辑和智能文件标签功能。',
  author: '陈浩', date: '2026-01-12', readTime: '6 分钟',
}

const allPosts = [
  { title: 'CloudDrive v3.2 发布：AI 智能搜索与全新协作体验', category: '产品更新', date: '2026-01-12', excerpt: '全新的 AI 驱动搜索引擎让文件查找速度提升 10 倍，支持自然语言查询和 OCR 图片文字搜索。', author: '陈浩', readTime: '6 分钟', views: '12.3k' },
  { title: '企业私有云存储安全架构深度解析', category: '技术分享', date: '2026-01-05', excerpt: '深入探讨端到端加密、零信任架构、密钥管理等企业级安全技术的实现原理与最佳实践。', author: '林薇', readTime: '12 分钟', views: '8.9k' },
  { title: '从 0 到 10,000 家企业：CloudDrive 架构演进之路', category: '案例分享', date: '2025-12-28', excerpt: '回顾 CloudDrive 从单体架构到微服务架构的演进历程，分享大规模分布式系统的设计经验。', author: '刘洋', readTime: '8 分钟', views: '15.2k' },
  { title: '大文件分片上传的技术设计与优化', category: '技术分享', date: '2025-12-20', excerpt: '深入分析大文件分片上传的技术挑战，包括并发控制、断点续传、完整性校验等核心问题的解决方案。', author: '王明', readTime: '10 分钟', views: '6.7k' },
  { title: 'GDPR 合规下的数据管理与隐私保护实践', category: '安全研究', date: '2025-12-15', excerpt: '解读 GDPR 对企业数据处理的要求，分享 CloudDrive 在数据分类、访问控制、审计追踪方面的实践经验。', author: '林薇', readTime: '9 分钟', views: '5.4k' },
  { title: '云存储成本优化：从月均百万到十万的实践之路', category: '最佳实践', date: '2025-12-10', excerpt: '分享 CloudDrive 在存储成本优化方面的实践经验，包括冷热数据分层、智能压缩、去重等技术手段。', author: '陈浩', readTime: '7 分钟', views: '11.1k' },
  { title: '2025 年企业数据安全趋势报告', category: '行业洞察', date: '2025-12-01', excerpt: '基于 10,000+ 企业客户数据，分析 2025 年企业数据安全面临的挑战与应对策略。', author: '林薇', readTime: '15 分钟', views: '20.5k' },
  { title: '基于 WebAssembly 的前端文件预览性能优化', category: '技术分享', date: '2025-11-25', excerpt: '利用 WebAssembly 加速 Office 文档、CAD 图纸等大型文件的浏览器端预览，预览速度提升 8 倍。', author: '王明', readTime: '8 分钟', views: '4.3k' },
  { title: '企业 DevOps 流程中的文件管理最佳实践', category: '最佳实践', date: '2025-11-18', excerpt: '介绍如何将 CloudDrive 集成到 CI/CD 流程中，实现构建产物自动归档、版本管理和环境差异化部署。', author: '刘洋', readTime: '6 分钟', views: '3.9k' },
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