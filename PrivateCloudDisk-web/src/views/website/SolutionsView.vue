<template>
  <div>
    <section class="bg-gradient-to-br from-primary/5 via-white to-info/5 py-20 sm:py-28">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="mx-auto max-w-3xl text-center">
          <span class="inline-flex items-center gap-2 rounded-full bg-primary/10 px-3 py-1 text-xs font-medium text-primary">解决方案</span>
          <h1 class="mt-4 text-4xl font-extrabold tracking-tight text-neutral-900 sm:text-5xl">为每个行业量身定制</h1>
          <p class="mt-4 text-lg text-neutral-500">从金融到教育，从医疗到制造，CloudDrive 为各行各业提供专业的企业级文件管理解决方案</p>
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
                <router-link to="/register" class="rounded-xl bg-primary px-6 py-2.5 text-sm font-semibold text-white hover:bg-primary/90">免费试用</router-link>
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
                <p class="text-xs font-medium text-neutral-500 mb-2">典型客户</p>
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
          <h2 class="text-3xl font-bold text-neutral-900">按场景选择解决方案</h2>
          <p class="mt-4 text-neutral-500">不同业务场景，我们提供针对性的解决方案</p>
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
        <h2 class="text-3xl font-bold text-neutral-900">寻找定制化解决方案？</h2>
        <p class="mt-4 text-neutral-500">我们的专家团队将根据您的业务需求，提供一对一的定制化解决方案</p>
        <div class="mt-8 flex justify-center gap-4">
          <router-link to="/contact" class="rounded-xl bg-primary px-8 py-3 text-sm font-semibold text-white hover:bg-primary/90">联系解决方案专家</router-link>
          <router-link to="/register" class="rounded-xl border border-neutral-200 px-8 py-3 text-sm font-semibold text-neutral-600 hover:border-primary hover:text-primary">免费试用</router-link>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
const solutions = [
  {
    tag: '金融行业', title: '金融级数据安全与合规', icon: 'fa fa-bank', bgClass: 'bg-primary/10', iconClass: 'text-primary',
    desc: '满足等保三级、ISO 27001 等金融行业监管要求。端到端加密、完整审计日志、数据不出境，为银行、证券、保险等金融机构提供安全可靠的私有云存储方案。',
    features: ['满足等保三级/ISO 27001/SOC 2 等合规要求', '完整操作审计日志，支持监管报送', '敏感数据自动识别与分类保护', '数据跨境传输管控与审批流程', '金融级同城双活和异地灾备', '与核心银行系统无缝集成'],
    highlights: [{ label: '合规达标', value: '100%' }, { label: '服务金融机构', value: '200+' }, { label: '年可用率', value: '99.997%' }, { label: '审计记录', value: '100亿+' }],
    customers: ['招商银行', '中信证券', '中国平安', '华泰证券', '中国银行'],
  },
  {
    tag: '教育行业', title: '智慧教育资源管理平台', icon: 'fa fa-graduation-cap', bgClass: 'bg-success/10', iconClass: 'text-success',
    desc: '为高校和科研机构提供教学资源统一管理、课件在线预览、师生协作空间一体化解决方案，支撑在线教学、科研数据管理与跨院系合作。',
    features: ['教学资源统一管理与分类标签', '课件/视频在线预览，支持大并发访问', '师生共享空间与科研协作', '与 LMS/教务系统集成', '科研数据安全管理与长期保存', '校园网内网高速访问'],
    highlights: [{ label: '覆盖高校', value: '500+' }, { label: '师生用户', value: '200万+' }, { label: '存储用量', value: '5PB' }, { label: '合作院校', value: '985/211' }],
    customers: ['清华大学', '北京大学', '浙江大学', '上海交大', '复旦大学'],
  },
  {
    tag: '医疗行业', title: '医疗影像与病历管理', icon: 'fa fa-heartbeat', bgClass: 'bg-danger/10', iconClass: 'text-danger',
    desc: '符合 HIPAA 和国内医疗数据保护法规的专业医疗数据存储方案。支持 DICOM 影像在线阅片、电子病历安全共享、跨院区协作。',
    features: ['DICOM 医学影像在线阅片与存储', '电子病历安全共享与访问控制', '符合 HIPAA/医疗数据保护法规', '跨院区/医联体数据协同', 'PACS/RIS/HIS 系统集成', '患者数据脱敏与隐私保护'],
    highlights: [{ label: '服务医院', value: '1200+' }, { label: '影像存储', value: '80PB' }, { label: '合规达标', value: '100%' }, { label: '阅片速度', value: '<1s' }],
    customers: ['协和医院', '华西医院', '瑞金医院', '中山医院', '301医院'],
  },
  {
    tag: '制造业', title: '工业设计与供应链协同', icon: 'fa fa-industry', bgClass: 'bg-warning/10', iconClass: 'text-warning',
    desc: '为制造业企业提供设计图纸安全管控、供应链文件协同、多地工厂数据同步解决方案。支持大文件传输、版本管理、权限精细化控制。',
    features: ['CAD/CAM 设计图纸安全管控', '供应商/外协厂文件安全外发', '多地工厂数据实时同步', '支持超大文件（>50GB）传输', '产品全生命周期文档管理', '与 PLM/ERP/MES 系统集成'],
    highlights: [{ label: '服务制造企业', value: '3000+' }, { label: '日均文件传输', value: '500万+' }, { label: '效率提升', value: '60%' }, { label: '图纸零泄露', value: '100%' }],
    customers: ['华为制造', '比亚迪', '三一重工', '格力电器', '富士康'],
  },
]

const scenarios = [
  { title: '远程办公', desc: '支持全球分布式团队随时随地安全访问文件，实时同步、在线协作，打破地域限制。', icon: 'fa fa-laptop', bgClass: 'bg-primary/10', iconClass: 'text-primary' },
  { title: '合规存档', desc: '满足各行业合规要求的长期数据存档方案，支持电子签章、审计追踪、自动归档策略。', icon: 'fa fa-archive', bgClass: 'bg-purple-50', iconClass: 'text-purple-500' },
  { title: '知识管理', desc: '构建企业知识库，自动分类整理、全文搜索、智能推荐，沉淀企业知识资产。', icon: 'fa fa-lightbulb-o', bgClass: 'bg-warning/10', iconClass: 'text-warning' },
  { title: '对外协作', desc: '安全地与外部合作伙伴共享文件，可设置访问密码、有效期、下载权限，全链路追踪。', icon: 'fa fa-share-alt', bgClass: 'bg-success/10', iconClass: 'text-success' },
  { title: '数据迁移', desc: '从传统 NAS、公有云盘平滑迁移到 CloudDrive，提供专业迁移工具和技术支持。', icon: 'fa fa-exchange', bgClass: 'bg-info/10', iconClass: 'text-info' },
  { title: '灾备恢复', desc: '多地冗余备份、异地灾备、快照恢复，确保业务连续性和数据零丢失。', icon: 'fa fa-life-ring', bgClass: 'bg-danger/10', iconClass: 'text-danger' },
]
</script>