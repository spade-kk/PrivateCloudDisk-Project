<template>
  <div>
    <!-- Hero -->
    <section class="bg-gradient-to-br from-primary/5 via-white to-info/5 py-20 sm:py-28">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="mx-auto max-w-3xl text-center">
          <span class="inline-flex items-center gap-2 rounded-full bg-primary/10 px-3 py-1 text-xs font-medium text-primary">关于我们</span>
          <h1 class="mt-4 text-4xl font-extrabold tracking-tight text-neutral-900 sm:text-5xl">让数据管理更简单</h1>
          <p class="mt-4 text-lg text-neutral-500">PrivateCloudDisk 致力于把文件管理、空间协作和可扩展自动化能力放在同一个私有云平台中</p>
        </div>
      </div>
    </section>

    <!-- Story -->
    <section class="py-20 sm:py-24">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="grid grid-cols-1 items-center gap-12 lg:grid-cols-2">
          <div>
            <h2 class="text-3xl font-bold text-neutral-900">我们的故事</h2>
            <p class="mt-4 text-neutral-500 leading-relaxed">
              PrivateCloudDisk 是一个围绕文件与空间协作构建的全栈项目，前端、网关、平台业务、文件存储、通知、即时通讯和扩展服务均位于同一仓库。
              项目关注数据边界、文件生命周期和团队协作过程中的可维护性。
            </p>
            <p class="mt-4 text-neutral-500 leading-relaxed">
              当前平台已经覆盖文件与文件夹 CURD、上传下载、预览、分享、回收站、收藏、标签、空间成员管理，以及插件、工作流和市场能力。
              官网展示以当前代码、配置、数据库迁移和服务契约为依据，不把未验证的客户数量、性能或认证结果写成产品承诺。
            </p>
            <p class="mt-4 text-neutral-500 leading-relaxed">
              项目支持 Docker Compose 联调，并为 Web、桌面、移动端和原生客户端保留子项目入口；生产部署、备份、监控和技术支持范围需要结合实际环境确认。
            </p>
          </div>
          <div class="grid grid-cols-2 gap-4">
            <div v-for="m in milestones" :key="m.year" class="rounded-2xl border border-neutral-200 p-6 text-center hover:border-primary/20 transition">
              <p class="text-3xl font-extrabold text-primary">{{ m.year }}</p>
              <p class="mt-2 text-sm font-medium text-neutral-700">{{ m.title }}</p>
              <p class="mt-1 text-xs text-neutral-400">{{ m.desc }}</p>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- Values -->
    <section class="border-t border-neutral-100 bg-neutral-50/50 py-20 sm:py-24">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="mx-auto max-w-2xl text-center">
          <h2 class="text-3xl font-bold text-neutral-900">核心价值观</h2>
          <p class="mt-4 text-neutral-500">指引我们前进的核心理念</p>
        </div>
        <div class="mt-12 grid grid-cols-1 gap-8 sm:grid-cols-2 lg:grid-cols-4">
          <div v-for="v in values" :key="v.title" class="rounded-2xl border border-neutral-200 bg-white p-6 text-center transition hover:-translate-y-1 hover:shadow-lg">
            <div class="mx-auto flex h-14 w-14 items-center justify-center rounded-xl" :class="v.bgClass">
              <i :class="[v.icon, 'text-xl', v.iconClass]"></i>
            </div>
            <h3 class="mt-4 text-lg font-semibold text-neutral-800">{{ v.title }}</h3>
            <p class="mt-2 text-sm text-neutral-500">{{ v.desc }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- Team -->
    <section class="py-20 sm:py-24">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="mx-auto max-w-2xl text-center">
          <h2 class="text-3xl font-bold text-neutral-900">核心团队</h2>
          <p class="mt-4 text-neutral-500">汇聚来自全球顶尖公司的技术和产品专家</p>
        </div>
        <div class="mt-12 grid grid-cols-1 gap-8 sm:grid-cols-2 lg:grid-cols-4">
          <div v-for="member in team" :key="member.name" class="rounded-2xl border border-neutral-200 p-6 text-center transition hover:-translate-y-1 hover:shadow-lg">
            <div class="mx-auto flex h-20 w-20 items-center justify-center rounded-full bg-primary/10 text-2xl font-bold text-primary">
              {{ member.name.charAt(0) }}
            </div>
            <h3 class="mt-4 text-lg font-semibold text-neutral-800">{{ member.name }}</h3>
            <p class="text-sm text-primary font-medium">{{ member.role }}</p>
            <p class="mt-2 text-xs text-neutral-400">{{ member.bio }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- Locations -->
    <section class="border-t border-neutral-100 bg-neutral-50/50 py-20 sm:py-24">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <h2 class="text-3xl font-bold text-neutral-900 text-center">全球办公地点</h2>
        <div class="mt-12 grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4">
          <div v-for="loc in locations" :key="loc.city" class="rounded-2xl border border-neutral-200 bg-white p-6 text-center transition hover:-translate-y-1 hover:shadow-lg">
            <i class="fa fa-map-marker text-3xl text-primary"></i>
            <h3 class="mt-3 text-lg font-semibold text-neutral-800">{{ loc.city }}</h3>
            <p class="mt-1 text-xs text-neutral-400">{{ loc.address }}</p>
            <p class="mt-1 text-xs text-neutral-400">{{ loc.type }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- Join Us -->
    <section class="py-20 sm:py-24">
      <div class="mx-auto max-w-3xl px-4 text-center sm:px-6 lg:px-8">
        <div class="rounded-3xl bg-gradient-to-br from-primary to-info p-10 sm:p-16 text-white relative overflow-hidden">
          <div class="absolute -top-20 -right-20 h-64 w-64 rounded-full bg-white/10 blur-3xl"></div>
          <div class="relative">
            <h2 class="text-3xl font-bold">加入我们</h2>
            <p class="mt-4 text-white/80">我们正在寻找充满激情的人才，一起构建下一代企业级云存储平台</p>
            <div class="mt-8 flex flex-wrap justify-center gap-3">
              <span v-for="job in openJobs" :key="job" class="rounded-lg bg-white/10 px-4 py-2 text-sm text-white hover:bg-white/20 cursor-pointer transition">{{ job }}</span>
            </div>
            <a href="#" class="mt-6 inline-flex items-center gap-2 text-sm font-semibold text-white/90 hover:text-white">查看全部职位 <i class="fa fa-arrow-right text-xs"></i></a>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
const milestones = [
  { year: '01', title: '基础文件能力', desc: '文件、目录、上传、下载和预览' },
  { year: '02', title: '空间协作', desc: '成员、角色、权限和资源范围' },
  { year: '03', title: '文件生命周期', desc: '处理、激活、派生资源和事件' },
  { year: '04', title: '插件与工作流', desc: '扩展运行、市场和自动化调度' },
]

const values = [
  { title: '边界清晰', desc: '用服务、空间、权限和事件边界降低系统复杂度', icon: 'fa fa-shield', bgClass: 'bg-success/10', iconClass: 'text-success' },
  { title: '能力真实', desc: '官网和文档只描述可以从实现与配置中核验的能力', icon: 'fa fa-check-circle', bgClass: 'bg-danger/10', iconClass: 'text-danger' },
  { title: '持续演进', desc: '让文件、预览、协作和自动化能力按模块独立演进', icon: 'fa fa-lightbulb-o', bgClass: 'bg-warning/10', iconClass: 'text-warning' },
  { title: '开放扩展', desc: '通过 API、插件、工作流和市场连接具体业务', icon: 'fa fa-handshake-o', bgClass: 'bg-primary/10', iconClass: 'text-primary' },
]

const team = [
  { name: '陈浩', role: '创始人 & CEO', bio: '前阿里巴巴云存储技术负责人，15 年分布式系统经验' },
  { name: '林薇', role: 'CTO', bio: '前腾讯安全实验室负责人，信息安全专家' },
  { name: '刘洋', role: 'CPO', bio: '前字节跳动产品总监，10 年企业级产品设计经验' },
  { name: '王芳', role: 'COO', bio: '前华为云运营副总裁，20 年 IT 行业运营管理经验' },
]

const locations = [
  { city: '北京', address: '海淀区中关村软件园', type: '全球总部' },
  { city: '上海', address: '浦东新区张江高科技园区', type: '研发中心' },
  { city: '深圳', address: '南山区科技园', type: '华南分公司' },
  { city: '成都', address: '高新区天府软件园', type: '研发中心' },
]

const openJobs = ['后端开发工程师', '前端开发工程师', '安全工程师', '产品经理', 'UI/UX 设计师', '运维工程师', '技术文档工程师', '销售经理']
</script>
