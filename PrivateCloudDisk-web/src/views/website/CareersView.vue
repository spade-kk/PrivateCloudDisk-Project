<template>
  <div>
    <section class="bg-gradient-to-br from-primary/5 via-white to-info/5 py-20 sm:py-28">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="mx-auto max-w-3xl text-center">
          <span class="inline-flex items-center gap-2 rounded-full bg-primary/10 px-3 py-1 text-xs font-medium text-primary">加入我们</span>
          <h1 class="mt-4 text-4xl font-extrabold tracking-tight text-neutral-900 sm:text-5xl">与优秀的人一起改变世界</h1>
          <p class="mt-4 text-lg text-neutral-500">CloudDrive 正在寻找充满激情的人才，加入我们，一起构建下一代企业级云存储平台</p>
        </div>
      </div>
    </section>

    <!-- Why Join -->
    <section class="py-20 sm:py-24">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="mx-auto max-w-2xl text-center">
          <h2 class="text-3xl font-bold text-neutral-900">为什么加入 CloudDrive</h2>
        </div>
        <div class="mt-12 grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4">
          <div v-for="item in whyJoin" :key="item.title" class="rounded-2xl border border-neutral-200 bg-white p-6 text-center transition hover:-translate-y-1 hover:shadow-lg">
            <div class="mx-auto flex h-12 w-12 items-center justify-center rounded-xl" :class="item.bgClass">
              <i :class="[item.icon, 'text-lg', item.iconClass]"></i>
            </div>
            <h3 class="mt-4 text-base font-semibold text-neutral-800">{{ item.title }}</h3>
            <p class="mt-2 text-xs text-neutral-500 leading-relaxed">{{ item.desc }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- Culture -->
    <section class="border-t border-neutral-100 bg-neutral-50/50 py-20 sm:py-24">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="mx-auto max-w-2xl text-center">
          <h2 class="text-3xl font-bold text-neutral-900">我们的文化</h2>
          <p class="mt-4 text-neutral-500">开放、创新、成长——这是我们的工作方式</p>
        </div>
        <div class="mt-12 grid grid-cols-1 gap-8 lg:grid-cols-3">
          <div v-for="item in culture" :key="item.title" class="rounded-2xl border border-neutral-200 bg-white p-8">
            <span class="text-3xl">{{ item.emoji }}</span>
            <h3 class="mt-3 text-lg font-semibold text-neutral-800">{{ item.title }}</h3>
            <p class="mt-2 text-sm text-neutral-500 leading-relaxed">{{ item.desc }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- Open Positions -->
    <section class="py-20 sm:py-24">
      <div class="mx-auto max-w-4xl px-4 sm:px-6 lg:px-8">
        <div class="mx-auto max-w-2xl text-center">
          <h2 class="text-3xl font-bold text-neutral-900">开放职位</h2>
          <p class="mt-4 text-neutral-500">找到适合你的位置，开启新的旅程</p>
        </div>
        <div class="mt-12 space-y-8">
          <div v-for="dept in departments" :key="dept.name">
            <h3 class="text-lg font-semibold text-neutral-800 mb-4 flex items-center gap-2">
              <i :class="[dept.icon, 'text-primary']"></i>
              {{ dept.name }}
              <span class="text-xs text-neutral-400">({{ dept.jobs.length }} 个职位)</span>
            </h3>
            <div class="space-y-3">
              <div v-for="job in dept.jobs" :key="job.title" class="flex items-center justify-between rounded-xl border border-neutral-200 p-4 hover:border-primary/30 hover:shadow-sm transition cursor-pointer">
                <div>
                  <p class="text-sm font-semibold text-neutral-700">{{ job.title }}</p>
                  <div class="mt-1 flex items-center gap-3 text-xs text-neutral-400">
                    <span><i class="fa fa-map-marker mr-1"></i>{{ job.location }}</span>
                    <span><i class="fa fa-clock-o mr-1"></i>{{ job.type }}</span>
                    <span><i class="fa fa-money mr-1"></i>{{ job.salary }}</span>
                  </div>
                </div>
                <span class="shrink-0 text-xs text-primary font-medium hover:underline">查看详情</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- Benefits -->
    <section class="border-t border-neutral-100 bg-neutral-50/50 py-20 sm:py-24">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="mx-auto max-w-2xl text-center">
          <h2 class="text-3xl font-bold text-neutral-900">薪酬福利</h2>
        </div>
        <div class="mt-12 grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-6">
          <div v-for="b in benefits" :key="b" class="rounded-xl border border-neutral-200 bg-white p-4 text-center hover:border-primary/20 transition">
            <p class="text-sm font-medium text-neutral-700">{{ b }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- CTA -->
    <section class="py-20">
      <div class="mx-auto max-w-3xl px-4 text-center sm:px-6 lg:px-8">
        <div class="rounded-3xl bg-gradient-to-br from-primary to-info p-10 sm:p-16 text-white relative overflow-hidden">
          <div class="absolute -top-20 -right-20 h-64 w-64 rounded-full bg-white/10 blur-3xl"></div>
          <div class="relative">
            <h2 class="text-3xl font-bold">找不到合适的职位？</h2>
            <p class="mt-4 text-white/80">发送简历到 hr@clouddrive.com，我们会为你匹配合适的机会</p>
            <a href="mailto:hr@clouddrive.com" class="mt-6 inline-flex items-center gap-2 rounded-xl bg-white px-8 py-3 text-sm font-semibold text-primary hover:bg-neutral-50">投递简历 <i class="fa fa-paper-plane"></i></a>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
const whyJoin = [
  { title: '技术前沿', desc: '深度参与分布式存储、AI 搜索、安全加密等前沿技术研发，解决大规模系统架构挑战。', icon: 'fa fa-rocket', bgClass: 'bg-primary/10', iconClass: 'text-primary' },
  { title: '快速成长', desc: '扁平化管理，开放的晋升通道，丰富的内外部培训资源，助你快速成长。', icon: 'fa fa-line-chart', bgClass: 'bg-success/10', iconClass: 'text-success' },
  { title: '优秀团队', desc: '来自阿里、腾讯、Google 等顶级公司的技术专家和产品专家组成的核心团队。', icon: 'fa fa-users', bgClass: 'bg-info/10', iconClass: 'text-info' },
  { title: '灵活办公', desc: '支持远程办公和弹性工作时间，在北京、上海、深圳、成都设有办公地点。', icon: 'fa fa-laptop', bgClass: 'bg-purple-50', iconClass: 'text-purple-500' },
]

const culture = [
  { emoji: '🤝', title: '开放透明', desc: '我们践行开放透明的工作方式，代码开源、决策公开、信息共享。每个人都能了解公司的方向和进展。' },
  { emoji: '🚀', title: '追求卓越', desc: '我们追求技术卓越和产品极致。鼓励技术创新，支持探索新技术，允许试错并从失败中学习。' },
  { emoji: '💡', title: '以人为本', desc: '我们相信优秀的团队是最好的竞争力。关注每个人的成长和发展，提供有竞争力的薪酬和全面的福利保障。' },
]

const departments = [
  {
    name: '工程研发', icon: 'fa fa-code',
    jobs: [
      { title: '资深后端开发工程师 (Go/Python)', location: '北京 / 上海', type: '全职', salary: '30K-60K' },
      { title: '高级前端开发工程师 (Vue/React)', location: '北京', type: '全职', salary: '25K-50K' },
      { title: '存储系统研发工程师', location: '北京 / 深圳', type: '全职', salary: '35K-70K' },
      { title: 'iOS 开发工程师', location: '北京', type: '全职', salary: '25K-50K' },
      { title: 'Android 开发工程师', location: '深圳', type: '全职', salary: '25K-50K' },
    ],
  },
  {
    name: '产品设计', icon: 'fa fa-lightbulb-o',
    jobs: [
      { title: '高级产品经理', location: '北京', type: '全职', salary: '30K-55K' },
      { title: 'UI/UX 设计师', location: '北京 / 上海', type: '全职', salary: '25K-45K' },
    ],
  },
  {
    name: '安全与运维', icon: 'fa fa-shield',
    jobs: [
      { title: '安全研发工程师', location: '北京', type: '全职', salary: '30K-60K' },
      { title: 'SRE 运维工程师', location: '北京 / 上海', type: '全职', salary: '28K-55K' },
    ],
  },
  {
    name: '市场与销售', icon: 'fa fa-bullhorn',
    jobs: [
      { title: '大客户销售经理', location: '北京 / 上海 / 深圳', type: '全职', salary: '20K-40K + 提成' },
      { title: '解决方案架构师', location: '北京', type: '全职', salary: '30K-55K' },
    ],
  },
]

const benefits = ['六险一金', '弹性工作', '带薪年假', '股票期权', '免费三餐', '年度体检', '技术培训', '团建旅游', 'MacBook Pro', '远程办公', '育儿假', '节日礼金']
</script>