<template>
  <div>
    <section class="bg-gradient-to-br from-primary/5 via-white to-info/5 py-20 sm:py-28">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="mx-auto max-w-3xl text-center">
          <span class="inline-flex items-center gap-2 rounded-full bg-primary/10 px-3 py-1 text-xs font-medium text-primary">客户案例</span>
          <h1 class="mt-4 text-4xl font-extrabold tracking-tight text-neutral-900 sm:text-5xl">标杆客户的信赖之选</h1>
          <p class="mt-4 text-lg text-neutral-500">超过 10,000 家企业正在使用 CloudDrive，来看看他们的故事</p>
        </div>
      </div>
    </section>

    <!-- Stats Bar -->
    <section class="border-y border-neutral-100 bg-neutral-50/50 py-8">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="grid grid-cols-2 gap-6 sm:grid-cols-4">
          <div class="text-center"><span class="text-3xl font-extrabold text-primary">10,000+</span><p class="mt-1 text-xs text-neutral-500">服务企业</p></div>
          <div class="text-center"><span class="text-3xl font-extrabold text-primary">500万+</span><p class="mt-1 text-xs text-neutral-500">活跃用户</p></div>
          <div class="text-center"><span class="text-3xl font-extrabold text-primary">200+</span><p class="mt-1 text-xs text-neutral-500">国家/地区</p></div>
          <div class="text-center"><span class="text-3xl font-extrabold text-primary">99.7%</span><p class="mt-1 text-xs text-neutral-500">客户满意度</p></div>
        </div>
      </div>
    </section>

    <!-- Featured Cases -->
    <section class="py-20 sm:py-24">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8 space-y-16">
        <div v-for="(cs, i) in caseStudies" :key="cs.company" class="grid grid-cols-1 items-center gap-10 lg:grid-cols-2 rounded-3xl border border-neutral-200 bg-white p-8 sm:p-10">
          <div :class="i % 2 === 1 ? 'lg:order-2' : ''">
            <div class="flex items-center gap-3 mb-4">
              <div class="flex h-14 w-14 items-center justify-center rounded-2xl bg-neutral-100 text-xl font-bold text-neutral-600">{{ cs.company.charAt(0) }}</div>
              <div>
                <p class="text-lg font-semibold text-neutral-800">{{ cs.company }}</p>
                <p class="text-sm text-neutral-400">{{ cs.industry }} · {{ cs.size }}</p>
              </div>
            </div>
            <h2 class="text-2xl font-bold text-neutral-900">{{ cs.title }}</h2>
            <p class="mt-4 text-neutral-500 leading-relaxed">{{ cs.challenge }}</p>
            <div class="mt-6 rounded-xl bg-neutral-50 p-5">
              <p class="text-xs font-semibold text-success uppercase tracking-wider mb-2">CloudDrive 解决方案</p>
              <p class="text-sm text-neutral-600 leading-relaxed">{{ cs.solution }}</p>
            </div>
            <div class="mt-6 grid grid-cols-3 gap-3">
              <div v-for="m in cs.results" :key="m.label" class="text-center rounded-xl bg-primary/5 p-3">
                <p class="text-xl font-bold text-primary">{{ m.value }}</p>
                <p class="text-[10px] text-neutral-500">{{ m.label }}</p>
              </div>
            </div>
          </div>
          <div :class="i % 2 === 1 ? 'lg:order-1' : ''">
            <div class="rounded-2xl bg-neutral-50 p-6 space-y-3">
              <p class="text-xs font-semibold text-neutral-400 uppercase tracking-wider">客户评价</p>
              <blockquote class="text-sm text-neutral-600 leading-relaxed italic">"{{ cs.testimonial }}"</blockquote>
              <div class="flex items-center gap-3 pt-3 border-t border-neutral-200">
                <div class="flex h-9 w-9 items-center justify-center rounded-full bg-primary/10 text-sm font-bold text-primary">{{ cs.contact.charAt(0) }}</div>
                <div>
                  <p class="text-sm font-semibold text-neutral-700">{{ cs.contact }}</p>
                  <p class="text-xs text-neutral-400">{{ cs.position }}</p>
                </div>
                <div class="ml-auto flex gap-0.5 text-warning">
                  <i v-for="n in 5" :key="n" class="fa fa-star text-xs"></i>
                </div>
              </div>
            </div>
            <div class="mt-4 rounded-2xl bg-neutral-50 p-4">
              <p class="text-xs font-semibold text-neutral-400 mb-2">使用的产品功能</p>
              <div class="flex flex-wrap gap-2">
                <span v-for="tag in cs.tags" :key="tag" class="rounded-md bg-white border border-neutral-200 px-2.5 py-1 text-xs text-neutral-600">{{ tag }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- More Customers -->
    <section class="border-t border-neutral-100 bg-neutral-50/50 py-16">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <h2 class="text-2xl font-bold text-neutral-900 text-center">更多信赖 CloudDrive 的企业</h2>
        <div class="mt-10 flex flex-wrap justify-center gap-6">
          <span v-for="logo in moreCustomers" :key="logo" class="rounded-xl border border-neutral-200 bg-white px-6 py-3 text-sm font-medium text-neutral-400 hover:text-primary hover:border-primary/30 transition cursor-default">{{ logo }}</span>
        </div>
      </div>
    </section>

    <section class="py-20">
      <div class="mx-auto max-w-3xl px-4 text-center sm:px-6 lg:px-8">
        <div class="rounded-3xl bg-gradient-to-br from-primary to-info p-10 sm:p-16 text-white relative overflow-hidden">
          <div class="absolute -top-20 -right-20 h-64 w-64 rounded-full bg-white/10 blur-3xl"></div>
          <div class="relative">
            <h2 class="text-3xl font-bold">成为下一个成功案例</h2>
            <p class="mt-4 text-white/80">免费试用 CloudDrive，体验企业级私有云盘带来的效率提升</p>
            <div class="mt-8 flex justify-center gap-4">
              <router-link to="/register" class="rounded-xl bg-white px-8 py-3 text-sm font-semibold text-primary hover:bg-neutral-50">免费开始使用</router-link>
              <router-link to="/contact" class="rounded-xl border-2 border-white/30 px-8 py-3 text-sm font-semibold text-white hover:bg-white/10">预约演示</router-link>
            </div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
const caseStudies = [
  {
    company: '中国移动', industry: '电信运营商', size: '10万+员工',
    title: '全国 31 省分支机构文件统一管理平台',
    challenge: '中国移动在全国拥有 31 个省级分支机构，文件分散在各地 NAS 和员工个人设备上，缺乏统一的文件管理平台。跨省文件共享效率低下，安全管控困难，合规审计成本高昂。',
    solution: '部署 CloudDrive 企业版，建设全国统一文件管理平台。通过多数据中心分布式部署，实现文件就近访问；基于组织架构的权限体系实现精细化管控；审计日志自动归集满足合规要求。',
    results: [{ label: '效率提升', value: '80%' }, { label: '年省成本', value: '2000万' }, { label: '文件事故', value: '↓95%' }],
    testimonial: 'CloudDrive 不仅解决了我们跨省文件共享的效率问题，其完善的审计和安全体系让我们的合规团队非常满意。部署和维护都非常简单，强烈推荐。',
    contact: '张伟', position: '技术总监 · 信息技术中心',
    tags: ['企业版', '私有部署', '分布式存储', '审计日志', 'SSO集成'],
  },
  {
    company: '清华大学', industry: '高等教育', size: '4万+师生',
    title: '全校统一教学资源与科研数据管理平台',
    challenge: '随着在线教学和科研数字化程度提高，清华大学需要一个能够支撑全校 4 万余名师生使用的文件存储协作平台，要求支持大文件传输、在线预览、跨院系协作，且需确保数据安全。',
    solution: '采用 CloudDrive 旗舰版，为全校师生提供统一云盘服务。对接统一身份认证系统实现单点登录，部署于校园网内网。为每个院系创建独立协作空间，支持课程资源的分类管理与共享。',
    results: [{ label: '师生覆盖', value: '4万+' }, { label: '存储用量', value: '800TB' }, { label: '日均访问', value: '50万+' }],
    testimonial: 'CloudDrive 完美满足了高校在文件管理、教学资源共享和科研协作方面的复杂需求。超大文件的传输速度令人满意，API 接口让我们的教务系统集成非常顺畅。',
    contact: '李明', position: '信息化办公室主任',
    tags: ['旗舰版', '私有部署', 'CAS认证', '科研协作', '在线预览'],
  },
  {
    company: '招商银行', industry: '金融服务', size: '5万+员工',
    title: '金融级安全合规的私有云盘解决方案',
    challenge: '作为金融企业，招商银行对数据安全有着极高的要求。需要一个满足等保三级、数据不出境、操作全程审计的私有云盘解决方案。同时也需要支持跨部门、跨地域的安全协作。',
    solution: '部署 CloudDrive 企业版，采用金融专区部署方案。实现数据全链路加密，支持国密 SM2/SM4 算法。所有操作记录实时写入审计日志并支持监管报送。提供敏感数据自动识别与保护功能。',
    results: [{ label: '合规达标', value: '100%' }, { label: '安全事件', value: '0' }, { label: '审计效率', value: '+90%' }],
    testimonial: '在金融行业，数据安全不是可选项而是生命线。CloudDrive 的安全能力经过了我们的严格评估，在加密强度、审计完整性、访问控制精细化方面都达到了极高的水准。',
    contact: '王静', position: '信息安全总监',
    tags: ['企业版', '金融专区', '国密算法', '审计日志', '数据防泄漏'],
  },
  {
    company: '华为制造', industry: '电子制造', size: '3万+员工',
    title: '全球供应链设计文件安全协作平台',
    challenge: '华为制造拥有全球化的供应链网络，设计图纸和工艺文件需要在内部设计团队与外部供应商之间安全流转。传统邮件和 FTP 方式既不安全也不高效，需要专业的文件安全外发平台。',
    solution: '采用 CloudDrive 企业版 + 文件安全外发模块。实现设计文件的安全外发管控，支持下载权限、水印、有效期设置。集成 PLM 系统实现产品文档全生命周期管理。',
    results: [{ label: '外发效率', value: '+70%' }, { label: '图纸泄露', value: '0' }, { label: '供应商覆盖', value: '2000+' }],
    testimonial: 'CloudDrive 的文件安全外发功能完美满足了我们对供应商文件管控的严格要求。权限颗粒度精细化，操作全程可追溯，有效杜绝了图纸泄露风险。',
    contact: '赵鹏', position: 'IT 基础设施总监',
    tags: ['企业版', '安全外发', 'PLM集成', '水印防护', '全球加速'],
  },
]

const moreCustomers = [
  '阿里巴巴', '腾讯', '百度', '京东', '美团', '字节跳动', '中国石化', '国家电网', '中信银行', '万科集团',
  '恒大集团', '比亚迪', '格力电器', '小米科技', '联想集团', '中国联通', '苏宁易购', '网易', '滴滴出行', '蚂蚁集团',
]
</script>