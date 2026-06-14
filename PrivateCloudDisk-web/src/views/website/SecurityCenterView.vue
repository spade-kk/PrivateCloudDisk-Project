<template>
  <div>
    <section class="bg-gradient-to-br from-primary/5 via-white to-info/5 py-20 sm:py-28">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="mx-auto max-w-3xl text-center">
          <span class="inline-flex items-center gap-2 rounded-full bg-danger/10 px-3 py-1 text-xs font-medium text-danger">安全中心</span>
          <h1 class="mt-4 text-4xl font-extrabold tracking-tight text-neutral-900 sm:text-5xl">全方位数据安全保障</h1>
          <p class="mt-4 text-lg text-neutral-500">CloudDrive 将安全融入产品基因，从传输、存储到访问控制，每一层都经过精心设计</p>
        </div>
      </div>
    </section>

    <!-- Security Architecture Overview -->
    <section class="py-20 sm:py-24">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="mb-16 text-center">
          <h2 class="text-3xl font-bold text-neutral-900">安全架构一览</h2>
          <p class="mt-4 text-neutral-500">4 层防护体系，守护数据安全</p>
        </div>
        <div class="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4">
          <div class="rounded-2xl border border-neutral-200 bg-white p-6 text-center hover:shadow-lg transition">
            <div class="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-primary/10">
              <i class="fa fa-lock text-2xl text-primary"></i>
            </div>
            <h3 class="mt-4 text-base font-semibold text-neutral-800">加密传输</h3>
            <p class="mt-2 text-xs text-neutral-500 leading-relaxed">TLS 1.3 + 国密 SM2/SM4 双通道加密，确保数据在传输过程中不被窃取或篡改</p>
            <ul class="mt-4 space-y-1 text-left text-xs text-neutral-500">
              <li class="flex items-start gap-2"><i class="fa fa-check text-success mt-0.5"></i> TLS 1.3 / 国密加密</li>
              <li class="flex items-start gap-2"><i class="fa fa-check text-success mt-0.5"></i> 证书链验证</li>
              <li class="flex items-start gap-2"><i class="fa fa-check text-success mt-0.5"></i> 双向认证</li>
            </ul>
          </div>
          <div class="rounded-2xl border border-neutral-200 bg-white p-6 text-center hover:shadow-lg transition">
            <div class="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-success/10">
              <i class="fa fa-database text-2xl text-success"></i>
            </div>
            <h3 class="mt-4 text-base font-semibold text-neutral-800">存储加密</h3>
            <p class="mt-2 text-xs text-neutral-500 leading-relaxed">AES-256-GCM 端到端加密，密钥由客户自主管理，数据落盘即加密</p>
            <ul class="mt-4 space-y-1 text-left text-xs text-neutral-500">
              <li class="flex items-start gap-2"><i class="fa fa-check text-success mt-0.5"></i> AES-256-GCM 加密</li>
              <li class="flex items-start gap-2"><i class="fa fa-check text-success mt-0.5"></i> 客户自主密钥</li>
              <li class="flex items-start gap-2"><i class="fa fa-check text-success mt-0.5"></i> 硬件安全模块</li>
            </ul>
          </div>
          <div class="rounded-2xl border border-neutral-200 bg-white p-6 text-center hover:shadow-lg transition">
            <div class="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-warning/10">
              <i class="fa fa-shield text-2xl text-warning"></i>
            </div>
            <h3 class="mt-4 text-base font-semibold text-neutral-800">访问控制</h3>
            <p class="mt-2 text-xs text-neutral-500 leading-relaxed">多层权限体系 + 行为分析，异常检测，访问控制精细化</p>
            <ul class="mt-4 space-y-1 text-left text-xs text-neutral-500">
              <li class="flex items-start gap-2"><i class="fa fa-check text-success mt-0.5"></i> RBAC 权限体系</li>
              <li class="flex items-start gap-2"><i class="fa fa-check text-success mt-0.5"></i> 2FA / SSO 认证</li>
              <li class="flex items-start gap-2"><i class="fa fa-check text-success mt-0.5"></i> IP 白名单</li>
            </ul>
          </div>
          <div class="rounded-2xl border border-neutral-200 bg-white p-6 text-center hover:shadow-lg transition">
            <div class="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-info/10">
              <i class="fa fa-file-text text-2xl text-info"></i>
            </div>
            <h3 class="mt-4 text-base font-semibold text-neutral-800">审计合规</h3>
            <p class="mt-2 text-xs text-neutral-500 leading-relaxed">完整操作日志，实时异常告警，满足各类合规审计要求</p>
            <ul class="mt-4 space-y-1 text-left text-xs text-neutral-500">
              <li class="flex items-start gap-2"><i class="fa fa-check text-success mt-0.5"></i> 操作审计日志</li>
              <li class="flex items-start gap-2"><i class="fa fa-check text-success mt-0.5"></i> 实时异常告警</li>
              <li class="flex items-start gap-2"><i class="fa fa-check text-success mt-0.5"></i> 合规报告生成</li>
            </ul>
          </div>
        </div>
      </div>
    </section>

    <!-- Certifications -->
    <section class="border-t border-neutral-100 bg-neutral-50/50 py-20 sm:py-24">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="mb-16 text-center">
          <h2 class="text-3xl font-bold text-neutral-900">安全认证与合规</h2>
          <p class="mt-4 text-neutral-500">通过多项国际和国内权威安全认证</p>
        </div>
        <div class="grid grid-cols-2 gap-4 sm:grid-cols-4">
          <div v-for="cert in certifications" :key="cert.name" class="rounded-2xl border border-neutral-200 bg-white p-8 text-center hover:shadow-lg transition">
            <i :class="[cert.icon, 'text-3xl', cert.color]"></i>
            <h3 class="mt-3 text-base font-semibold text-neutral-800">{{ cert.name }}</h3>
            <p class="mt-1 text-xs text-neutral-400">{{ cert.desc }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- Security Features Detail -->
    <section class="py-20 sm:py-24">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="mb-16 text-center">
          <h2 class="text-3xl font-bold text-neutral-900">深度安全功能</h2>
        </div>
        <div class="grid grid-cols-1 gap-8 md:grid-cols-2">
          <div v-for="f in securityFeatures" :key="f.title" class="rounded-2xl border border-neutral-200 bg-white p-8">
            <div class="flex items-center gap-3 mb-4">
              <div class="flex h-10 w-10 items-center justify-center rounded-xl" :class="f.bgClass">
                <i :class="[f.icon, f.iconClass]"></i>
              </div>
              <h3 class="text-lg font-semibold text-neutral-800">{{ f.title }}</h3>
            </div>
            <p class="text-sm text-neutral-500 leading-relaxed">{{ f.desc }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- Trust -->
    <section class="border-t border-neutral-100 bg-neutral-50/50 py-20">
      <div class="mx-auto max-w-3xl px-4 text-center sm:px-6 lg:px-8">
        <h2 class="text-3xl font-bold text-neutral-900">我们对待安全的态度</h2>
        <p class="mt-4 text-neutral-500 leading-relaxed">CloudDrive 将安全视为产品的生命线。我们建立了完整的安全研发流程（SDL），所有代码在上线前必须经过安全审查和渗透测试。我们设有独立的安全响应中心（SRC），欢迎安全研究人员通过负责任的方式报告漏洞。</p>
        <p class="mt-4 text-sm text-neutral-400">如发现安全漏洞，请发送邮件至 security@clouddrive.com</p>
      </div>
    </section>
  </div>
</template>

<script setup>
const certifications = [
  { name: 'ISO 27001', desc: '信息安全管理体系', icon: 'fa fa-certificate', color: 'text-primary' },
  { name: '等保三级', desc: '国家信息安全等级保护', icon: 'fa fa-shield', color: 'text-success' },
  { name: 'SOC 2 Type II', desc: '服务组织控制报告', icon: 'fa fa-check-circle', color: 'text-info' },
  { name: 'GDPR 合规', desc: '欧盟通用数据保护条例', icon: 'fa fa-globe', color: 'text-purple-500' },
  { name: 'HIPAA', desc: '医疗数据隐私保护', icon: 'fa fa-heartbeat', color: 'text-danger' },
  { name: '国密认证', desc: 'SM2/SM3/SM4 算法', icon: 'fa fa-key', color: 'text-warning' },
  { name: 'CSA STAR', desc: '云安全联盟认证', icon: 'fa fa-star', color: 'text-primary' },
  { name: 'PCI DSS', desc: '支付卡行业数据安全', icon: 'fa fa-credit-card', color: 'text-info' },
]

const securityFeatures = [
  { title: '端到端加密', desc: '采用 AES-256-GCM 算法进行端到端加密，加密密钥由客户自主管理，CloudDrive 无法解密用户数据。支持国密 SM2/SM4 算法，满足国内合规要求。', icon: 'fa fa-lock', bgClass: 'bg-primary/10', iconClass: 'text-primary' },
  { title: '病毒扫描', desc: '内置多引擎病毒扫描能力，支持 ClamAV 和第三方商业杀毒引擎。文件上传时自动扫描，发现病毒自动隔离到安全隔离区，并向管理员发送告警通知。', icon: 'fa fa-bug', bgClass: 'bg-danger/10', iconClass: 'text-danger' },
  { title: '数据防泄漏 (DLP)', desc: '基于内容识别和规则引擎的数据防泄漏系统。自动检测敏感数据（如身份证号、银行卡号、手机号等），支持自定义敏感数据规则，防止数据外泄。', icon: 'fa fa-eye-slash', bgClass: 'bg-warning/10', iconClass: 'text-warning' },
  { title: '访问控制', desc: '支持基于角色的访问控制 (RBAC)、属性访问控制 (ABAC)、IP 白名单、时间窗口控制等多种访问策略。支持与 LDAP/AD 目录服务集成。', icon: 'fa fa-users', bgClass: 'bg-info/10', iconClass: 'text-info' },
  { title: '操作审计', desc: '完整记录所有操作日志，包括文件上传/下载/删除/分享、权限变更、登录登出等。日志不可篡改，支持实时告警和定期审计报告生成。', icon: 'fa fa-history', bgClass: 'bg-purple-50', iconClass: 'text-purple-500' },
  { title: '双因素认证', desc: '支持 TOTP、短信验证码、硬件密钥 (U2F/WebAuthn) 等多种双因素认证方式。可设置特定 IP 段免 2FA，平衡安全性与易用性。', icon: 'fa fa-mobile', bgClass: 'bg-success/10', iconClass: 'text-success' },
  { title: '安全外发', desc: '文件外发支持设置访问密码、有效期、下载次数限制、水印等安全策略。外发链接加密传输，支持随时撤回。', icon: 'fa fa-share-square-o', bgClass: 'bg-primary/10', iconClass: 'text-primary' },
  { title: '数据备份', desc: '支持自动备份策略配置，3 副本冗余存储 + 异地灾备。支持文件版本历史，可恢复到任意历史版本。回收站 30 天数据保护。', icon: 'fa fa-copy', bgClass: 'bg-info/10', iconClass: 'text-info' },
]
</script>