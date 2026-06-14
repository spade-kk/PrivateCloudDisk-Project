<template>
  <div>
    <section class="bg-gradient-to-br from-primary/5 via-white to-info/5 py-20 sm:py-28">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="mx-auto max-w-3xl text-center">
          <span class="inline-flex items-center gap-2 rounded-full bg-primary/10 px-3 py-1 text-xs font-medium text-primary">合作伙伴</span>
          <h1 class="mt-4 text-4xl font-extrabold tracking-tight text-neutral-900 sm:text-5xl">携手共建生态</h1>
          <p class="mt-4 text-lg text-neutral-500">CloudDrive 与全球领先的技术和服务伙伴合作，为企业提供全方位的解决方案</p>
        </div>
      </div>
    </section>

    <!-- Partner Categories -->
    <section class="py-20 sm:py-24">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8 space-y-20">
        <div v-for="cat in partnerCategories" :key="cat.title">
          <div class="text-center mb-12">
            <h2 class="text-2xl font-bold text-neutral-900">{{ cat.title }}</h2>
            <p class="mt-2 text-sm text-neutral-400">{{ cat.desc }}</p>
          </div>
          <div class="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4">
            <div v-for="p in cat.partners" :key="p.name" class="rounded-2xl border border-neutral-200 bg-white p-8 text-center transition hover:-translate-y-1 hover:shadow-lg">
              <div class="mx-auto flex h-16 w-16 items-center justify-center rounded-2xl bg-neutral-100 text-2xl font-bold text-neutral-400">{{ p.name.charAt(0) }}</div>
              <h3 class="mt-4 text-base font-semibold text-neutral-800">{{ p.name }}</h3>
              <p class="mt-1 text-xs text-neutral-400">{{ p.desc }}</p>
              <span class="mt-3 inline-block rounded-full bg-neutral-100 px-3 py-1 text-[11px] font-medium text-neutral-500">{{ p.type }}</span>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- Become Partner -->
    <section class="border-t border-neutral-100 bg-neutral-50/50 py-20">
      <div class="mx-auto max-w-3xl px-4 text-center sm:px-6 lg:px-8">
        <h2 class="text-3xl font-bold text-neutral-900">成为 CloudDrive 合作伙伴</h2>
        <p class="mt-4 text-neutral-500">加入 CloudDrive 合作伙伴计划，共享企业级私有云存储市场的巨大机遇</p>
        <div class="mt-10 grid grid-cols-1 gap-6 sm:grid-cols-3">
          <div class="rounded-2xl border border-neutral-200 bg-white p-6 text-center">
            <i class="fa fa-certificate text-3xl text-primary mb-3"></i>
            <h3 class="text-base font-semibold text-neutral-800">认证合作伙伴</h3>
            <p class="mt-2 text-xs text-neutral-500">获得销售和技术认证，共同服务客户</p>
          </div>
          <div class="rounded-2xl border border-neutral-200 bg-white p-6 text-center">
            <i class="fa fa-puzzle-piece text-3xl text-info mb-3"></i>
            <h3 class="text-base font-semibold text-neutral-800">技术合作伙伴</h3>
            <p class="mt-2 text-xs text-neutral-500">产品集成与联合解决方案开发</p>
          </div>
          <div class="rounded-2xl border border-neutral-200 bg-white p-6 text-center">
            <i class="fa fa-globe text-3xl text-success mb-3"></i>
            <h3 class="text-base font-semibold text-neutral-800">渠道合作伙伴</h3>
            <p class="mt-2 text-xs text-neutral-500">区域代理与分销，拓展市场覆盖</p>
          </div>
        </div>
        <router-link to="/contact" class="mt-8 inline-flex items-center gap-2 rounded-xl bg-primary px-8 py-3 text-sm font-semibold text-white hover:bg-primary/90">申请成为合作伙伴 <i class="fa fa-arrow-right"></i></router-link>
      </div>
    </section>
  </div>
</template>

<script setup>
const partnerCategories = [
  {
    title: '云平台合作伙伴', desc: '与全球主流云平台深度集成，提供灵活部署选择',
    partners: [
      { name: '阿里云', desc: '弹性计算与对象存储', type: '深度集成' },
      { name: '华为云', desc: '鲲鹏架构原生支持', type: '战略合作' },
      { name: '腾讯云', desc: 'COS 对象存储集成', type: '技术合作' },
      { name: 'AWS', desc: 'S3 兼容存储后端', type: '技术合作' },
      { name: 'Azure', desc: 'Azure AD 身份集成', type: '认证合作' },
      { name: 'Google Cloud', desc: 'GCS 存储集成', type: '技术合作' },
      { name: 'UCloud', desc: '中立云平台方案', type: '合作集成' },
      { name: '青云 QingCloud', desc: '私有云一体机方案', type: 'OEM 合作' },
    ],
  },
  {
    title: '安全合作伙伴', desc: '联合顶级安全厂商，构建全方位安全防护体系',
    partners: [
      { name: '奇安信', desc: '终端安全与态势感知', type: '战略合作' },
      { name: '深信服', desc: '零信任访问方案', type: '技术合作' },
      { name: '亚信安全', desc: '病毒查杀引擎集成', type: '技术合作' },
      { name: '启明星辰', desc: '安全审计与合规', type: '合作集成' },
    ],
  },
  {
    title: 'ISV 合作伙伴', desc: '与领先的企业软件厂商集成，扩展业务场景',
    partners: [
      { name: '用友', desc: 'ERP 系统文件管理集成', type: '认证合作' },
      { name: '金蝶', desc: '财务文档云端管理', type: '认证合作' },
      { name: '泛微', desc: 'OA 流程附件管理', type: '技术合作' },
      { name: '蓝凌', desc: '知识管理平台集成', type: '技术合作' },
    ],
  },
]
</script>