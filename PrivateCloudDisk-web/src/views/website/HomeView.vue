<template>
  <div class="overflow-hidden">
    <!-- Hero Section -->
    <section class="relative overflow-hidden bg-gradient-to-br from-primary/5 via-white to-info/5">
      <!-- Background decoration -->
      <div class="absolute inset-0 pointer-events-none">
        <div class="absolute -top-40 -right-40 h-96 w-96 rounded-full bg-primary/10 blur-3xl"></div>
        <div class="absolute -bottom-40 -left-40 h-96 w-96 rounded-full bg-info/10 blur-3xl"></div>
        <div class="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 h-64 w-64 rounded-full bg-warning/5 blur-3xl"></div>
      </div>
      <div class="relative mx-auto max-w-7xl px-4 pb-20 pt-20 sm:px-6 sm:pb-28 sm:pt-28 lg:px-8 lg:pt-36">
        <div class="mx-auto max-w-3xl text-center">
          <div class="mb-6 inline-flex items-center gap-2 rounded-full border border-primary/20 bg-primary/5 px-4 py-1.5 text-xs font-medium text-primary">
            <i class="fa fa-bolt text-[10px]"></i>
            全新 v3.0 版本发布 · 性能提升 300%
          </div>
          <h1 class="text-4xl font-extrabold tracking-tight text-neutral-900 sm:text-5xl lg:text-6xl">
            企业级私有云盘
            <span class="mt-2 block bg-gradient-to-r from-primary to-info bg-clip-text text-transparent">
              安全 · 高效 · 智能
            </span>
          </h1>
          <p class="mx-auto mt-6 max-w-2xl text-lg leading-relaxed text-neutral-500 sm:text-xl">
            CloudDrive 为企业提供端到端加密的私有云存储解决方案。支持文件管理、团队协作、智能搜索、病毒扫描，全方位保障数据安全。
          </p>
          <div class="mt-10 flex flex-col items-center gap-4 sm:flex-row sm:justify-center">
            <router-link to="/register" class="inline-flex items-center gap-2 rounded-xl bg-primary px-8 py-3.5 text-base font-semibold text-white shadow-lg shadow-primary/25 transition hover:bg-primary/90 hover:shadow-xl hover:-translate-y-0.5">
              免费开始使用
              <i class="fa fa-arrow-right text-sm"></i>
            </router-link>
            <router-link to="/download" class="inline-flex items-center gap-2 rounded-xl border-2 border-neutral-200 px-8 py-3.5 text-base font-semibold text-neutral-700 transition hover:border-primary hover:text-primary">
              <i class="fa fa-download"></i>
              下载客户端
            </router-link>
          </div>
          <p class="mt-4 text-xs text-neutral-400">无需信用卡 · 免费 10GB 存储 · 随时升级</p>
        </div>

        <!-- Dashboard Preview -->
        <div class="mx-auto mt-16 max-w-5xl sm:mt-20">
          <div class="relative rounded-2xl border border-neutral-200/60 bg-white shadow-2xl shadow-neutral-900/5 overflow-hidden">
            <div class="flex items-center gap-2 border-b border-neutral-100 px-4 py-2.5 bg-neutral-50/50">
              <span class="h-3 w-3 rounded-full bg-danger/80"></span>
              <span class="h-3 w-3 rounded-full bg-warning/80"></span>
              <span class="h-3 w-3 rounded-full bg-success/80"></span>
              <span class="ml-2 text-xs text-neutral-400">CloudDrive Dashboard</span>
            </div>
            <div class="p-4 sm:p-6">
              <div class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
                <div v-for="card in previewCards" :key="card.label" class="rounded-xl border border-neutral-100 p-4">
                  <div class="flex items-center justify-between">
                    <span class="text-xs font-medium text-neutral-400">{{ card.label }}</span>
                    <i :class="[card.icon, 'text-neutral-300']"></i>
                  </div>
                  <p class="mt-2 text-2xl font-bold text-neutral-800">{{ card.value }}</p>
                  <p class="mt-1 text-xs" :class="card.trendUp ? 'text-success' : 'text-neutral-400'">
                    <i :class="card.trendUp ? 'fa fa-arrow-up' : 'fa fa-minus'"></i>
                    {{ card.trend }}
                  </p>
                </div>
              </div>
              <div class="mt-4 grid grid-cols-1 gap-4 lg:grid-cols-3">
                <div class="rounded-xl border border-neutral-100 p-4 lg:col-span-2">
                  <p class="text-xs font-medium text-neutral-400 mb-3">存储使用趋势</p>
                  <div class="flex items-end gap-1 h-24">
                    <div v-for="(h, i) in [40,65,52,80,70,90,75,85,60,72,88,68]" :key="i" class="flex-1 rounded-t bg-primary/20 hover:bg-primary/40 transition" :style="{ height: h + '%' }"></div>
                  </div>
                </div>
                <div class="rounded-xl border border-neutral-100 p-4">
                  <p class="text-xs font-medium text-neutral-400 mb-3">文件类型分布</p>
                  <div class="space-y-2">
                    <div v-for="ft in fileTypes" :key="ft.type" class="flex items-center gap-2 text-xs">
                      <span :style="{ backgroundColor: ft.color }" class="h-2 w-2 rounded-full shrink-0"></span>
                      <span class="text-neutral-600">{{ ft.type }}</span>
                      <span class="ml-auto text-neutral-400">{{ ft.pct }}%</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- Trusted By -->
    <section class="border-y border-neutral-100 bg-neutral-50/50 py-10">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <p class="text-center text-xs font-semibold uppercase tracking-widest text-neutral-400 mb-8">已为 10,000+ 企业提供服务</p>
        <div class="flex flex-wrap items-center justify-center gap-8 sm:gap-12 opacity-50">
          <span v-for="brand in brands" :key="brand" class="text-lg font-bold text-neutral-400">{{ brand }}</span>
        </div>
      </div>
    </section>

    <!-- Features Section -->
    <section class="py-20 sm:py-28">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="mx-auto max-w-2xl text-center">
          <h2 class="text-3xl font-bold tracking-tight text-neutral-900 sm:text-4xl">
            为什么选择 CloudDrive
          </h2>
          <p class="mt-4 text-neutral-500">六大核心能力，全方位满足企业级文件管理需求</p>
        </div>
        <div class="mt-16 grid grid-cols-1 gap-8 sm:grid-cols-2 lg:grid-cols-3">
          <div v-for="feat in features" :key="feat.title" class="group rounded-2xl border border-neutral-100 p-8 transition-all hover:border-primary/20 hover:shadow-lg hover:shadow-primary/5 hover:-translate-y-1">
            <div class="mb-5 flex h-12 w-12 items-center justify-center rounded-xl" :class="feat.bgClass">
              <i :class="[feat.icon, 'text-xl', feat.iconClass]"></i>
            </div>
            <h3 class="text-lg font-semibold text-neutral-800">{{ feat.title }}</h3>
            <p class="mt-2 text-sm leading-relaxed text-neutral-500">{{ feat.desc }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- Stats Section -->
    <section class="bg-neutral-900 py-20 sm:py-28">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="grid grid-cols-2 gap-8 sm:grid-cols-4">
          <div v-for="stat in stats" :key="stat.label" class="text-center">
            <p class="text-4xl font-extrabold text-white sm:text-5xl">{{ stat.value }}</p>
            <p class="mt-2 text-sm font-medium text-neutral-400">{{ stat.label }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- Security Section -->
    <section class="py-20 sm:py-28">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="grid grid-cols-1 items-center gap-12 lg:grid-cols-2">
          <div>
            <span class="inline-flex items-center gap-2 rounded-full bg-success/10 px-3 py-1 text-xs font-medium text-success">
              <i class="fa fa-shield"></i> 安全认证
            </span>
            <h2 class="mt-4 text-3xl font-bold tracking-tight text-neutral-900 sm:text-4xl">
              企业级安全防护
            </h2>
            <p class="mt-4 text-neutral-500 leading-relaxed">
              端到端 AES-256 加密、文件病毒实时扫描、多因素认证、操作审计日志、IP 白名单——我们提供银行级别的安全防护，确保您的数据万无一失。
            </p>
            <ul class="mt-6 space-y-3">
              <li v-for="item in securityItems" :key="item" class="flex items-center gap-3 text-sm text-neutral-600">
                <i class="fa fa-check-circle text-success"></i> {{ item }}
              </li>
            </ul>
            <router-link to="/features" class="mt-8 inline-flex items-center gap-2 text-sm font-semibold text-primary hover:underline">
              了解更多安全特性 <i class="fa fa-arrow-right text-xs"></i>
            </router-link>
          </div>
          <div class="relative">
            <div class="rounded-2xl border border-neutral-200 bg-white p-6 shadow-xl">
              <div class="space-y-4">
                <div v-for="cert in certs" :key="cert.label" class="flex items-center gap-4 rounded-xl border border-neutral-100 p-4">
                  <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-success/10">
                    <i :class="[cert.icon, 'text-success']"></i>
                  </div>
                  <div>
                    <p class="text-sm font-semibold text-neutral-700">{{ cert.label }}</p>
                    <p class="text-xs text-neutral-400">{{ cert.desc }}</p>
                  </div>
                  <i class="fa fa-check-circle text-success ml-auto"></i>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- Testimonials -->
    <section class="border-t border-neutral-100 bg-neutral-50/50 py-20 sm:py-28">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="mx-auto max-w-2xl text-center">
          <h2 class="text-3xl font-bold tracking-tight text-neutral-900 sm:text-4xl">客户怎么说</h2>
        </div>
        <div class="mt-16 grid grid-cols-1 gap-8 md:grid-cols-3">
          <div v-for="t in testimonials" :key="t.name" class="rounded-2xl border border-neutral-200 bg-white p-6">
            <div class="flex gap-0.5 text-warning">
              <i v-for="n in 5" :key="n" class="fa fa-star text-xs"></i>
            </div>
            <p class="mt-4 text-sm leading-relaxed text-neutral-600">{{ t.text }}</p>
            <div class="mt-4 flex items-center gap-3 border-t border-neutral-100 pt-4">
              <div class="flex h-10 w-10 items-center justify-center rounded-full bg-primary/10 text-sm font-bold text-primary">
                {{ t.name.charAt(0) }}
              </div>
              <div>
                <p class="text-sm font-semibold text-neutral-700">{{ t.name }}</p>
                <p class="text-xs text-neutral-400">{{ t.role }}</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- Solutions Overview Section -->
    <section class="py-20 sm:py-28 bg-white">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="mx-auto max-w-2xl text-center">
          <span class="inline-flex items-center gap-2 rounded-full bg-info/10 px-3 py-1 text-xs font-medium text-info">行业解决方案</span>
          <h2 class="mt-4 text-3xl font-bold tracking-tight text-neutral-900 sm:text-4xl">为各行业量身打造</h2>
          <p class="mt-4 text-neutral-500">无论您身处哪个行业，CloudDrive 都能提供贴合需求的解决方案</p>
        </div>
        <div class="mt-16 grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4">
          <div v-for="sol in solutions" :key="sol.title" class="group rounded-2xl border border-neutral-100 p-6 transition-all hover:border-primary/20 hover:shadow-lg hover:-translate-y-1">
            <div class="mb-4 flex h-12 w-12 items-center justify-center rounded-xl" :class="sol.bgClass">
              <i :class="[sol.icon, 'text-xl', sol.iconClass]"></i>
            </div>
            <h3 class="text-base font-semibold text-neutral-800">{{ sol.title }}</h3>
            <p class="mt-2 text-xs leading-relaxed text-neutral-500">{{ sol.desc }}</p>
            <router-link :to="sol.href" class="mt-4 inline-flex items-center gap-1 text-xs font-medium text-primary hover:underline">了解更多 <i class="fa fa-arrow-right text-[10px]"></i></router-link>
          </div>
        </div>
      </div>
    </section>

    <!-- Integration Partners -->
    <section class="border-t border-neutral-100 bg-neutral-50/30 py-20 sm:py-24">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="mx-auto max-w-2xl text-center">
          <span class="inline-flex items-center gap-2 rounded-full bg-purple-50 px-3 py-1 text-xs font-medium text-purple-600">生态集成</span>
          <h2 class="mt-4 text-3xl font-bold tracking-tight text-neutral-900 sm:text-4xl">与您现有的工具无缝集成</h2>
          <p class="mt-4 text-neutral-500">CloudDrive 支持与 100+ 企业级工具集成，无需改变现有工作流程</p>
        </div>
        <div class="mt-12 grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-6">
          <div v-for="it in integrations" :key="it.name" class="flex flex-col items-center gap-2 rounded-xl border border-neutral-100 bg-white p-5 transition hover:shadow-md hover:border-primary/20">
            <i :class="[it.icon, 'text-2xl', it.color]"></i>
            <span class="text-xs font-medium text-neutral-600">{{ it.name }}</span>
          </div>
        </div>
        <p class="mt-8 text-center">
          <router-link to="/features" class="text-sm font-medium text-primary hover:underline">查看全部集成 <i class="fa fa-arrow-right text-xs"></i></router-link>
        </p>
      </div>
    </section>

    <!-- Case Study Highlights -->
    <section class="py-20 sm:py-28 bg-white">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="flex flex-col sm:flex-row sm:items-end sm:justify-between gap-4">
          <div>
            <span class="inline-flex items-center gap-2 rounded-full bg-success/10 px-3 py-1 text-xs font-medium text-success">客户案例</span>
            <h2 class="mt-4 text-3xl font-bold tracking-tight text-neutral-900 sm:text-4xl">标杆客户的信赖之选</h2>
          </div>
          <router-link to="/case-studies" class="text-sm font-medium text-primary hover:underline shrink-0">查看全部案例 <i class="fa fa-arrow-right text-xs"></i></router-link>
        </div>
        <div class="mt-12 grid grid-cols-1 gap-8 md:grid-cols-3">
          <div v-for="cs in caseHighlights" :key="cs.company" class="group rounded-2xl border border-neutral-200 overflow-hidden transition hover:shadow-lg hover:-translate-y-1">
            <div :class="[cs.gradient, 'h-3']"></div>
            <div class="p-6">
              <div class="flex items-center gap-3">
                <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-neutral-100 text-sm font-bold text-neutral-600">{{ cs.company.charAt(0) }}</div>
                <div>
                  <p class="text-sm font-semibold text-neutral-800">{{ cs.company }}</p>
                  <p class="text-xs text-neutral-400">{{ cs.industry }}</p>
                </div>
              </div>
              <p class="mt-4 text-sm leading-relaxed text-neutral-600">{{ cs.summary }}</p>
              <div class="mt-4 grid grid-cols-3 gap-2 border-t border-neutral-100 pt-4">
                <div v-for="m in cs.metrics" :key="m.label" class="text-center">
                  <p class="text-lg font-bold text-primary">{{ m.value }}</p>
                  <p class="text-[10px] text-neutral-400">{{ m.label }}</p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- Blog Preview Section -->
    <section class="border-t border-neutral-100 bg-neutral-50/30 py-20 sm:py-28">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="flex flex-col sm:flex-row sm:items-end sm:justify-between gap-4">
          <div>
            <span class="inline-flex items-center gap-2 rounded-full bg-warning/10 px-3 py-1 text-xs font-medium text-warning">技术博客</span>
            <h2 class="mt-4 text-3xl font-bold tracking-tight text-neutral-900 sm:text-4xl">最新动态与技术分享</h2>
          </div>
          <router-link to="/blog" class="text-sm font-medium text-primary hover:underline shrink-0">查看全部文章 <i class="fa fa-arrow-right text-xs"></i></router-link>
        </div>
        <div class="mt-12 grid grid-cols-1 gap-8 md:grid-cols-3">
          <article v-for="post in blogPosts" :key="post.title" class="group rounded-2xl border border-neutral-200 bg-white overflow-hidden transition hover:shadow-lg hover:-translate-y-1 cursor-pointer">
            <div class="p-6">
              <div class="flex items-center gap-2 text-xs">
                <span class="rounded-md bg-primary/10 px-2 py-0.5 font-medium text-primary">{{ post.category }}</span>
                <span class="text-neutral-400">{{ post.date }}</span>
              </div>
              <h3 class="mt-3 text-base font-semibold text-neutral-800 group-hover:text-primary transition line-clamp-2">{{ post.title }}</h3>
              <p class="mt-2 text-sm text-neutral-500 line-clamp-2">{{ post.excerpt }}</p>
              <div class="mt-4 flex items-center gap-3 border-t border-neutral-100 pt-4">
                <div class="flex h-7 w-7 items-center justify-center rounded-full bg-neutral-100 text-xs font-bold text-neutral-500">{{ post.author.charAt(0) }}</div>
                <div>
                  <p class="text-xs font-medium text-neutral-600">{{ post.author }}</p>
                  <p class="text-[10px] text-neutral-400">{{ post.readTime }}</p>
                </div>
              </div>
            </div>
          </article>
        </div>
      </div>
    </section>

    <!-- FAQ Quick Section -->
    <section class="py-20 sm:py-28 bg-white">
      <div class="mx-auto max-w-3xl px-4 sm:px-6 lg:px-8">
        <div class="text-center">
          <h2 class="text-3xl font-bold tracking-tight text-neutral-900 sm:text-4xl">常见问题</h2>
          <p class="mt-4 text-neutral-500">关于 CloudDrive 的常见疑问</p>
        </div>
        <div class="mt-10 grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div v-for="(faq, i) in homeFaqs" :key="i" class="rounded-xl border border-neutral-100 p-5 hover:border-primary/20 transition">
            <p class="text-sm font-semibold text-neutral-700 mb-2">{{ faq.q }}</p>
            <p class="text-xs text-neutral-500 leading-relaxed">{{ faq.a }}</p>
          </div>
        </div>
        <p class="mt-8 text-center">
          <router-link to="/docs" class="text-sm font-medium text-primary hover:underline">查看更多常见问题 <i class="fa fa-arrow-right text-xs"></i></router-link>
        </p>
      </div>
    </section>

    <!-- CTA Section -->
    <section class="py-20 sm:py-28">
      <div class="mx-auto max-w-4xl px-4 sm:px-6 lg:px-8">
        <div class="relative overflow-hidden rounded-3xl bg-gradient-to-br from-primary to-info p-10 sm:p-16 text-center">
          <div class="absolute -top-20 -right-20 h-64 w-64 rounded-full bg-white/10 blur-3xl"></div>
          <div class="absolute -bottom-20 -left-20 h-64 w-64 rounded-full bg-white/10 blur-3xl"></div>
          <div class="relative">
            <h2 class="text-3xl font-bold text-white sm:text-4xl">准备好开始了吗？</h2>
            <p class="mt-4 text-lg text-white/80">免费注册即可获得 10GB 存储空间，无需信用卡</p>
            <div class="mt-8 flex flex-col items-center gap-4 sm:flex-row sm:justify-center">
              <router-link to="/register" class="inline-flex items-center gap-2 rounded-xl bg-white px-8 py-3.5 text-base font-semibold text-primary shadow-lg transition hover:bg-neutral-50 hover:-translate-y-0.5">
                立即注册
                <i class="fa fa-arrow-right text-sm"></i>
              </router-link>
              <router-link to="/contact" class="inline-flex items-center gap-2 rounded-xl border-2 border-white/30 px-8 py-3.5 text-base font-semibold text-white transition hover:border-white hover:bg-white/10">
                <i class="fa fa-comments"></i>
                联系销售
              </router-link>
            </div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
const previewCards = [
  { label: '已用存储', value: '2.4 TB', icon: 'fa fa-hdd-o', trend: '较上月 +15%', trendUp: false },
  { label: '文件总数', value: '45,821', icon: 'fa fa-files-o', trend: '较上月 +8%', trendUp: true },
  { label: '活跃用户', value: '1,245', icon: 'fa fa-user-o', trend: '较上月 +12%', trendUp: true },
  { label: '下载流量', value: '856 GB', icon: 'fa fa-download', trend: '较上月 +23%', trendUp: true },
]

const fileTypes = [
  { type: '文档', pct: 35, color: '#2B7FFF' },
  { type: '图片', pct: 28, color: '#FF6B6B' },
  { type: '视频', pct: 18, color: '#FFD93D' },
  { type: '其他', pct: 19, color: '#95A5A6' },
]

const brands = ['中国移动', '建设银行', '清华大学', '华为云', '字节跳动', '招商银行', '中信证券', '阿里巴巴']

const features = [
  { title: '智能文件管理', desc: '支持拖拽上传、批量操作、文件夹嵌套、标签分类，AI 智能分类自动整理您的文件。', icon: 'fa fa-folder-open', bgClass: 'bg-primary/10', iconClass: 'text-primary' },
  { title: '高效团队协作', desc: '实时共享文件夹、在线协同编辑、权限精细管理、评论批注，团队协作无缝衔接。', icon: 'fa fa-users', bgClass: 'bg-success/10', iconClass: 'text-success' },
  { title: '全文智能搜索', desc: '支持文件名、文件内容、OCR 图片文字搜索，毫秒级响应，精准定位您需要的文件。', icon: 'fa fa-search', bgClass: 'bg-warning/10', iconClass: 'text-warning' },
  { title: '多端无缝同步', desc: 'Windows、macOS、Linux、iOS、Android 全平台支持，文件实时同步，随时随地访问。', icon: 'fa fa-sync', bgClass: 'bg-info/10', iconClass: 'text-info' },
  { title: '企业级安全防护', desc: 'AES-256 端到端加密、病毒实时扫描、双因素认证、操作审计，银行级安全标准。', icon: 'fa fa-shield', bgClass: 'bg-danger/10', iconClass: 'text-danger' },
  { title: '开放 API 集成', desc: '提供 RESTful API 和 SDK，轻松集成到现有系统，支持 LDAP/SSO 单点登录。', icon: 'fa fa-code', bgClass: 'bg-purple-100', iconClass: 'text-purple-600' },
]

const stats = [
  { value: '10,000+', label: '服务企业' },
  { value: '500 万+', label: '活跃用户' },
  { value: '99.99%', label: '服务可用性' },
  { value: '200+', label: '国家/地区' },
]

const securityItems = [
  'AES-256 端到端加密传输与存储',
  '实时病毒扫描与恶意文件隔离',
  '双因素认证 (2FA) 与单点登录 (SSO)',
  '完整操作审计日志，满足合规要求',
  'IP 白名单与异常登录检测',
  '数据多地冗余备份，容灾恢复',
]

const certs = [
  { label: 'ISO 27001', desc: '信息安全管理体系认证', icon: 'fa fa-certificate' },
  { label: '等保三级', desc: '国家信息安全等级保护', icon: 'fa fa-shield' },
  { label: 'SOC 2 Type II', desc: '服务组织控制报告', icon: 'fa fa-check-circle' },
  { label: 'GDPR 合规', desc: '欧盟通用数据保护条例', icon: 'fa fa-globe' },
]

const testimonials = [
  { name: '张伟', role: '技术总监 · 某金融科技公司', text: 'CloudDrive 帮助我们解决了跨部门文件共享的痛点，安全审计功能让我们的合规团队非常满意。部署简单，运维成本低，强烈推荐。' },
  { name: '李娜', role: 'CTO · 某互联网企业', text: '从传统 NAS 迁移到 CloudDrive 后，团队协作效率提升了 60%。API 集成非常方便，轻松接入我们的 CI/CD 流程。' },
  { name: '王强', role: 'IT 经理 · 某制造业集团', text: '作为一家有 3000+ 员工的企业，数据安全是我们的首要关注点。CloudDrive 的端到端加密和病毒扫描让我们非常放心。' },
]

const solutions = [
  { title: '金融行业', desc: '满足等保三级和金融行业监管要求，审计日志完整可追溯，数据加密存储确保合规。', icon: 'fa fa-bank', bgClass: 'bg-primary/10', iconClass: 'text-primary', href: '/solutions' },
  { title: '教育行业', desc: '教学资源统一管理，课件在线预览，师生共享空间，支持大规模并发访问。', icon: 'fa fa-graduation-cap', bgClass: 'bg-success/10', iconClass: 'text-success', href: '/solutions' },
  { title: '医疗行业', desc: '符合 HIPAA 和医疗数据保护法规，病历影像安全存储，跨院区数据共享。', icon: 'fa fa-heartbeat', bgClass: 'bg-danger/10', iconClass: 'text-danger', href: '/solutions' },
  { title: '制造业', desc: '设计图纸安全管控，供应链文件协同，多地工厂数据实时同步，支持大文件传输。', icon: 'fa fa-industry', bgClass: 'bg-warning/10', iconClass: 'text-warning', href: '/solutions' },
]

const integrations = [
  { name: 'Slack', icon: 'fa fa-slack', color: 'text-purple-500' },
  { name: '企业微信', icon: 'fa fa-weixin', color: 'text-success' },
  { name: '钉钉', icon: 'fa fa-comments', color: 'text-info' },
  { name: '飞书', icon: 'fa fa-paper-plane', color: 'text-primary' },
  { name: 'Microsoft 365', icon: 'fa fa-windows', color: 'text-primary' },
  { name: 'Google Workspace', icon: 'fa fa-google', color: 'text-danger' },
  { name: 'Jenkins', icon: 'fa fa-cogs', color: 'text-neutral-600' },
  { name: 'GitHub', icon: 'fa fa-github', color: 'text-neutral-800' },
  { name: 'Jira', icon: 'fa fa-tasks', color: 'text-info' },
  { name: 'Notion', icon: 'fa fa-file-text', color: 'text-neutral-700' },
  { name: 'Zapier', icon: 'fa fa-bolt', color: 'text-warning' },
  { name: 'LDAP/AD', icon: 'fa fa-sitemap', color: 'text-purple-600' },
]

const caseHighlights = [
  {
    company: '中国移动', industry: '电信运营商',
    summary: '通过部署 CloudDrive 企业版，实现了全国 31 个省份分支机构文件的统一管理与安全共享，文件流转效率提升 80%。',
    gradient: 'bg-gradient-to-r from-primary to-info',
    metrics: [{ label: '效率提升', value: '80%' }, { label: '用户数', value: '5万+' }, { label: '年省成本', value: '2000万' }],
  },
  {
    company: '清华大学', industry: '高等教育',
    summary: '为全校 4 万余名师生提供统一的文件存储与协作平台，支撑在线教学、科研数据管理、跨院系合作等场景。',
    gradient: 'bg-gradient-to-r from-purple-500 to-pink-500',
    metrics: [{ label: '师生覆盖', value: '4万+' }, { label: '存储用量', value: '800TB' }, { label: '运行时间', value: '3年' }],
  },
  {
    company: '招商银行', industry: '金融服务',
    summary: '满足金融级安全合规要求的私有云盘解决方案，实现敏感数据不出行、操作全程审计、跨部门安全协作。',
    gradient: 'bg-gradient-to-r from-success to-info',
    metrics: [{ label: '安全合规', value: '100%' }, { label: '审计记录', value: '10亿+' }, { label: '年可用率', value: '99.997%' }],
  },
]

const blogPosts = [
  {
    title: 'CloudDrive v3.2 发布：AI 智能搜索与全新协作体验', category: '产品更新', date: '2026-01-12',
    excerpt: '全新的 AI 驱动搜索引擎让文件查找速度提升 10 倍，支持自然语言查询和 OCR 图片文字搜索。',
    author: '陈浩', readTime: '6 分钟',
  },
  {
    title: '企业私有云存储安全架构深度解析', category: '技术分享', date: '2026-01-05',
    excerpt: '深入探讨端到端加密、零信任架构、密钥管理等企业级安全技术的实现原理与最佳实践。',
    author: '林薇', readTime: '12 分钟',
  },
  {
    title: '从 0 到 10,000 家企业：CloudDrive 的架构演进之路', category: '案例分享', date: '2025-12-28',
    excerpt: '回顾 CloudDrive 从单体架构到微服务架构的演进历程，分享大规模分布式系统的设计经验。',
    author: '刘洋', readTime: '8 分钟',
  },
]

const homeFaqs = [
  { q: 'CloudDrive 与公有云盘有什么区别？', a: 'CloudDrive 是私有化部署方案，数据完全存储在企业自有或指定服务器上，不经过第三方平台。所有数据采用端到端加密，企业拥有完全的数据主权。' },
  { q: '如何保证数据不丢失？', a: '我们采用 3 副本冗余存储 + 跨区域异地备份 + 回收站 30 天保护 + 文件版本历史四重保护机制，确保数据安全不丢失。' },
  { q: '是否支持大型文件上传？', a: '支持。单文件最大支持 50GB（专业版以上），采用分片断点续传技术，网络中断后可自动恢复，无需重新上传。' },
  { q: '部署需要多长时间？', a: '标准部署仅需 30 分钟即可完成。提供 Docker 一键部署、Kubernetes Helm Chart，以及专业团队上门部署服务。' },
  { q: '是否支持定制化开发？', a: '旗舰版支持专属定制开发，包括品牌定制、功能扩展、第三方系统集成、API 定制等，专属客户经理全程跟进。' },
  { q: '费用如何计算？', a: '按存储空间和用户数计费，支持月付和年付。免费版提供 10GB 空间，专业版 1TB 仅需 ¥29/月。详细方案请查看定价页。' },
]
</script>