<template>
  <div>
    <section class="bg-gradient-to-br from-primary/5 via-white to-info/5 py-20 sm:py-28">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="mx-auto max-w-3xl text-center">
          <span class="inline-flex items-center gap-2 rounded-full bg-primary/10 px-3 py-1 text-xs font-medium text-primary">媒体报道</span>
          <h1 class="mt-4 text-4xl font-extrabold tracking-tight text-neutral-900 sm:text-5xl">媒体中心</h1>
          <p class="mt-4 text-lg text-neutral-500">了解 CloudDrive 的最新动态、媒体报道和品牌资源</p>
        </div>
      </div>
    </section>

    <!-- Press Releases -->
    <section class="py-20 sm:py-24">
      <div class="mx-auto max-w-4xl px-4 sm:px-6 lg:px-8">
        <h2 class="text-2xl font-bold text-neutral-900 mb-10">新闻稿</h2>
        <div class="space-y-6">
          <div v-for="pr in pressReleases" :key="pr.title" class="rounded-2xl border border-neutral-200 p-6 hover:border-primary/20 hover:shadow-sm transition cursor-pointer">
            <div class="flex items-center gap-2 text-xs text-neutral-400 mb-2">
              <span class="rounded-md bg-primary/10 px-2 py-0.5 text-primary font-medium">{{ pr.category }}</span>
              <span>{{ pr.date }}</span>
            </div>
            <h3 class="text-lg font-semibold text-neutral-800 hover:text-primary transition">{{ pr.title }}</h3>
            <p class="mt-2 text-sm text-neutral-500">{{ pr.summary }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- Media Coverage -->
    <section class="border-t border-neutral-100 bg-neutral-50/50 py-20">
      <div class="mx-auto max-w-4xl px-4 sm:px-6 lg:px-8">
        <h2 class="text-2xl font-bold text-neutral-900 mb-10">媒体报道</h2>
        <div class="grid grid-cols-1 gap-6 sm:grid-cols-2">
          <div v-for="m in mediaCoverage" :key="m.source" class="rounded-2xl border border-neutral-200 bg-white p-6 hover:shadow-md transition">
            <p class="text-xs font-semibold text-primary uppercase tracking-wider">{{ m.source }}</p>
            <h3 class="mt-2 text-base font-semibold text-neutral-800">{{ m.title }}</h3>
            <p class="mt-2 text-xs text-neutral-400">{{ m.date }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- Brand Resources -->
    <section class="py-20">
      <div class="mx-auto max-w-4xl px-4 sm:px-6 lg:px-8">
        <h2 class="text-2xl font-bold text-neutral-900 mb-10">品牌资源</h2>
        <div class="grid grid-cols-1 gap-6 sm:grid-cols-3">
          <div class="rounded-2xl border border-neutral-200 p-6 text-center hover:shadow-md transition">
            <i class="fa fa-image text-3xl text-primary mb-3"></i>
            <h3 class="text-base font-semibold text-neutral-800">Logo 素材</h3>
            <p class="mt-1 text-xs text-neutral-400">PNG, SVG, AI 格式</p>
            <a href="#" class="mt-4 inline-block text-sm font-medium text-primary hover:underline">下载</a>
          </div>
          <div class="rounded-2xl border border-neutral-200 p-6 text-center hover:shadow-md transition">
            <i class="fa fa-paint-brush text-3xl text-purple-500 mb-3"></i>
            <h3 class="text-base font-semibold text-neutral-800">品牌指南</h3>
            <p class="mt-1 text-xs text-neutral-400">颜色、字体、使用规范</p>
            <a href="#" class="mt-4 inline-block text-sm font-medium text-primary hover:underline">查看</a>
          </div>
          <div class="rounded-2xl border border-neutral-200 p-6 text-center hover:shadow-md transition">
            <i class="fa fa-camera text-3xl text-info mb-3"></i>
            <h3 class="text-base font-semibold text-neutral-800">产品截图</h3>
            <p class="mt-1 text-xs text-neutral-400">Web 端与移动端</p>
            <a href="#" class="mt-4 inline-block text-sm font-medium text-primary hover:underline">下载</a>
          </div>
        </div>
      </div>
    </section>

    <section class="border-t border-neutral-100 py-16">
      <div class="mx-auto max-w-4xl px-4 text-center sm:px-6 lg:px-8">
        <h2 class="text-2xl font-bold text-neutral-900">媒体联系</h2>
        <p class="mt-2 text-neutral-500">媒体采访、品牌合作等事宜，请联系我们的公关团队</p>
        <p class="mt-4 text-primary font-medium">press@clouddrive.com</p>
      </div>
    </section>
  </div>
</template>

<script setup>
const pressReleases = [
  { category: '产品发布', date: '2026-01-10', title: 'CloudDrive 发布 v3.2 版本，AI 驱动文件搜索引领行业变革', summary: '全新的 AI 搜索引擎让文件查找速度提升 10 倍，支持自然语言查询和 OCR 图片文字搜索，为云存储行业带来颠覆性创新。' },
  { category: '融资新闻', date: '2025-10-15', title: 'CloudDrive 完成 5 亿元 B 轮融资，加速企业级私有云存储布局', summary: '本轮融资由红杉资本领投，腾讯跟投，资金将用于产品研发、市场拓展和国际化战略。' },
  { category: '企业合作', date: '2025-08-20', title: 'CloudDrive 与阿里云达成战略合作，共同推进企业云存储生态建设', summary: '双方将在产品集成、市场推广、技术研发等方面深度合作，为企业客户提供更完善的云存储解决方案。' },
  { category: '行业荣誉', date: '2025-06-05', title: 'CloudDrive 获评"2025 年度最佳企业级云存储解决方案"', summary: '在一年一度的中国云计算大会上，CloudDrive 凭借卓越的产品能力和客户口碑，荣获年度最佳企业级云存储解决方案大奖。' },
]

const mediaCoverage = [
  { source: '36氪', title: 'CloudDrive：企业级私有云盘的"中国方案"', date: '2025-12-20' },
  { source: 'InfoQ', title: '深度解析 CloudDrive 3.0 架构升级：从单体到微服务的演进之路', date: '2025-11-18' },
  { source: '虎嗅', title: '后疫情时代，企业云存储市场迎来新变局', date: '2025-10-25' },
  { source: '极客公园', title: 'CloudDrive 创始人：我们想做企业数据的"守护者"', date: '2025-09-12' },
  { source: 'CSDN', title: '技术揭秘：CloudDrive 如何实现真正的端到端加密', date: '2025-08-08' },
  { source: '科技日报', title: '国产云盘企业 CloudDrive 的全球化之路', date: '2025-07-15' },
]
</script>