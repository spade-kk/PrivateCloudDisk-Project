<template>
  <div>
    <!-- Hero -->
    <section class="bg-gradient-to-br from-primary/5 via-white to-info/5 py-20 sm:py-28">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="mx-auto max-w-3xl text-center">
          <span class="inline-flex items-center gap-2 rounded-full bg-primary/10 px-3 py-1 text-xs font-medium text-primary">文档中心</span>
          <h1 class="mt-4 text-4xl font-extrabold tracking-tight text-neutral-900 sm:text-5xl">帮助文档</h1>
          <p class="mt-4 text-lg text-neutral-500">快速入门、使用指南、API 参考等完整文档</p>
          <!-- Search -->
          <div class="relative mx-auto mt-8 max-w-xl">
            <i class="fa fa-search absolute left-4 top-1/2 -translate-y-1/2 text-neutral-400"></i>
            <input v-model="searchQuery" type="text" placeholder="搜索文档..." class="w-full rounded-xl border border-neutral-200 py-3 pl-11 pr-4 text-sm focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/10" />
          </div>
        </div>
      </div>
    </section>

    <!-- Categories -->
    <section class="py-20 sm:py-24">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
          <div v-for="cat in filteredCategories" :key="cat.key" class="rounded-2xl border border-neutral-200 p-8 transition-all hover:border-primary/30 hover:shadow-lg hover:-translate-y-1 cursor-pointer">
            <div class="mb-4 flex h-12 w-12 items-center justify-center rounded-xl" :class="cat.bgClass">
              <i :class="[cat.icon, 'text-xl', cat.iconClass]"></i>
            </div>
            <h3 class="text-lg font-semibold text-neutral-800">{{ cat.label }}</h3>
            <p class="mt-1 text-sm text-neutral-400">{{ cat.desc }}</p>
            <div class="mt-4 space-y-2">
              <a v-for="article in cat.articles" :key="article" href="#" class="flex items-center gap-2 text-sm text-neutral-600 hover:text-primary transition">
                <i class="fa fa-file-text-o text-xs text-neutral-300"></i>
                {{ article }}
              </a>
            </div>
            <a href="#" class="mt-4 inline-flex items-center gap-1 text-xs font-medium text-primary hover:underline">
              {{ cat.articleCount }} 篇文章 <i class="fa fa-arrow-right text-[10px]"></i>
            </a>
          </div>
        </div>
      </div>
    </section>

    <!-- Getting Started Guide -->
    <section class="border-t border-neutral-100 bg-neutral-50/50 py-20 sm:py-24">
      <div class="mx-auto max-w-4xl px-4 sm:px-6 lg:px-8">
        <h2 class="text-2xl font-bold text-neutral-900 text-center">快速入门</h2>
        <div class="mt-10 space-y-6">
          <div v-for="(step, i) in quickStartSteps" :key="i" class="flex gap-4 rounded-2xl border border-neutral-200 bg-white p-6">
            <div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-primary text-sm font-bold text-white">{{ i + 1 }}</div>
            <div>
              <h3 class="text-base font-semibold text-neutral-800">{{ step.title }}</h3>
              <p class="mt-1 text-sm text-neutral-500">{{ step.desc }}</p>
              <a :href="step.link" class="mt-2 inline-flex items-center gap-1 text-xs font-medium text-primary hover:underline">了解更多 <i class="fa fa-arrow-right text-[10px]"></i></a>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- FAQ -->
    <section class="py-20 sm:py-24">
      <div class="mx-auto max-w-3xl px-4 sm:px-6 lg:px-8">
        <h2 class="text-2xl font-bold text-neutral-900 text-center">热门问题</h2>
        <div class="mt-10 space-y-3">
          <div v-for="(faq, i) in faqs" :key="i" class="rounded-xl border border-neutral-200 p-5">
            <button @click="expandedFaq = expandedFaq === i ? null : i" class="flex w-full items-center justify-between text-left">
              <span class="text-sm font-medium text-neutral-700">{{ faq.q }}</span>
              <i :class="[expandedFaq === i ? 'fa fa-angle-up' : 'fa fa-angle-down', 'text-neutral-400 shrink-0 ml-3']"></i>
            </button>
            <p v-if="expandedFaq === i" class="mt-3 text-sm text-neutral-500 leading-relaxed">{{ faq.a }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- Still need help -->
    <section class="border-t border-neutral-100 bg-neutral-50/50 py-20">
      <div class="mx-auto max-w-3xl px-4 text-center sm:px-6 lg:px-8">
        <h2 class="text-2xl font-bold text-neutral-900">仍未找到答案？</h2>
        <p class="mt-2 text-neutral-500">我们的支持团队随时为您提供帮助</p>
        <div class="mt-6 flex justify-center gap-4">
          <router-link to="/contact" class="rounded-xl bg-primary px-6 py-2.5 text-sm font-semibold text-white hover:bg-primary/90">联系我们</router-link>
          <a href="#" class="rounded-xl border border-neutral-200 px-6 py-2.5 text-sm font-semibold text-neutral-600 hover:border-primary hover:text-primary">社区论坛</a>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'

const searchQuery = ref('')
const expandedFaq = ref(null)

const categories = [
  {
    key: 'getting-started', label: '新手指南', desc: '快速上手 CloudDrive', icon: 'fa fa-rocket', iconClass: 'text-primary', bgClass: 'bg-primary/10',
    articleCount: 8, articles: ['创建您的第一个云盘', '上传文件与文件夹', '安装桌面客户端', '移动端使用指南'],
  },
  {
    key: 'file-management', label: '文件管理', desc: '上传、下载与组织文件', icon: 'fa fa-folder', iconClass: 'text-warning', bgClass: 'bg-warning/10',
    articleCount: 12, articles: ['文件上传最佳实践', '批量操作指南', '文件版本管理', '回收站与恢复'],
  },
  {
    key: 'sharing', label: '分享与协作', desc: '团队协作与文件共享', icon: 'fa fa-share-alt', iconClass: 'text-info', bgClass: 'bg-info/10',
    articleCount: 10, articles: ['创建共享链接', '团队空间管理', '权限设置详解', '实时评论功能'],
  },
  {
    key: 'security', label: '安全与隐私', desc: '保护您的数据安全', icon: 'fa fa-shield', iconClass: 'text-success', bgClass: 'bg-success/10',
    articleCount: 7, articles: ['启用双因素认证', '加密机制说明', '审计日志使用', '安全最佳实践'],
  },
  {
    key: 'api', label: 'API 参考', desc: '开发者接口文档', icon: 'fa fa-code', iconClass: 'text-purple-500', bgClass: 'bg-purple-50',
    articleCount: 15, articles: ['REST API 概述', '认证与授权', '文件上传 API', '错误码参考'],
  },
  {
    key: 'troubleshooting', label: '故障排除', desc: '常见问题诊断与解决', icon: 'fa fa-wrench', iconClass: 'text-danger', bgClass: 'bg-danger/10',
    articleCount: 9, articles: ['上传失败怎么办', '同步问题排查', '客户端崩溃处理', '网络连接问题'],
  },
]

const filteredCategories = computed(() => {
  if (!searchQuery.value.trim()) return categories
  const q = searchQuery.value.toLowerCase()
  return categories.filter(c => c.label.toLowerCase().includes(q) || c.desc.toLowerCase().includes(q) || c.articles.some(a => a.toLowerCase().includes(q)))
})

const quickStartSteps = [
  { title: '注册账号', desc: '使用手机号或邮箱注册 CloudDrive 账号，完成邮箱验证后即可免费获得 10GB 存储空间。', link: '/register' },
  { title: '创建第一个文件夹', desc: '登录后点击「新建文件夹」创建您的第一个存储目录，支持无限嵌套的文件夹结构。', link: '#' },
  { title: '上传文件', desc: '拖拽文件到浏览器窗口或点击「上传」按钮，支持单文件最大 50GB 的上传能力。', link: '#' },
  { title: '分享文件', desc: '右键点击文件选择「分享」，生成分享链接并设置访问密码和有效期。', link: '#' },
  { title: '邀请团队成员', desc: '在「团队协作」中创建团队，邀请成员加入并设置不同的访问权限。', link: '/team' },
]

const faqs = [
  { q: '支持哪些文件类型上传？', a: 'CloudDrive 支持所有文件类型，包括文档（Word、Excel、PPT、PDF）、图片（JPG、PNG、GIF、RAW）、视频（MP4、MOV、AVI）、音频（MP3、WAV、FLAC）、压缩包（ZIP、RAR、7Z）、代码文件等。部分文件类型支持在线预览。' },
  { q: '上传速度慢怎么办？', a: '1) 尝试使用桌面客户端的断点续传功能 2) 开启局域网加速 3) 检查本地网络带宽 4) 对于超大文件，我们推荐使用分片上传功能，可以显著提高上传成功率。如果问题持续，请联系技术支持。' },
  { q: '数据存储在哪里？', a: '数据存储在您创建云盘时选择的区域数据中心。所有数据均有 3 副本冗余存储，同时定期进行异地备份，确保数据安全可靠。' },
  { q: '如何确保文件不丢失？', a: 'CloudDrive 采用多重保护机制：1) 3 副本冗余存储 2) 跨区域异地备份 3) 回收站 30 天保护期 4) 文件版本历史恢复 5) 企业版支持自定义数据保留策略。' },
]
</script>