<template>
  <div>
    <section class="bg-gradient-to-br from-primary/5 via-white to-info/5 py-20 sm:py-28">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="mx-auto max-w-3xl text-center">
          <span class="inline-flex items-center gap-2 rounded-full bg-danger/10 px-3 py-1 text-xs font-medium text-danger">安全中心</span>
          <h1 class="mt-4 text-4xl font-extrabold tracking-tight text-neutral-900 sm:text-5xl">全方位数据安全保障</h1>
          <p class="mt-4 text-lg text-neutral-500">PrivateCloudDisk 将认证、空间权限、文件凭证、内部服务和异步处理拆成可检查的安全边界</p>
        </div>
      </div>
    </section>

    <!-- Security Architecture Overview -->
    <section class="py-20 sm:py-24">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="mb-16 text-center">
          <h2 class="text-3xl font-bold text-neutral-900">安全架构一览</h2>
          <p class="mt-4 text-neutral-500">从入口认证到文件生命周期，按服务边界建立防护与追踪</p>
        </div>
        <div class="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4">
          <div class="rounded-2xl border border-neutral-200 bg-white p-6 text-center hover:shadow-lg transition">
            <div class="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-primary/10">
              <i class="fa fa-lock text-2xl text-primary"></i>
            </div>
            <h3 class="mt-4 text-base font-semibold text-neutral-800">入口与请求边界</h3>
            <p class="mt-2 text-xs text-neutral-500 leading-relaxed">网关负责认证、路由、请求头清理和服务入口控制；传输加密按实际部署验证</p>
            <ul class="mt-4 space-y-1 text-left text-xs text-neutral-500">
              <li class="flex items-start gap-2"><i class="fa fa-check text-success mt-0.5"></i> JWT 与客户端身份</li>
              <li class="flex items-start gap-2"><i class="fa fa-check text-success mt-0.5"></i> 内部服务校验</li>
              <li class="flex items-start gap-2"><i class="fa fa-check text-success mt-0.5"></i> 具体证书策略按配置</li>
            </ul>
          </div>
          <div class="rounded-2xl border border-neutral-200 bg-white p-6 text-center hover:shadow-lg transition">
            <div class="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-success/10">
              <i class="fa fa-database text-2xl text-success"></i>
            </div>
            <h3 class="mt-4 text-base font-semibold text-neutral-800">存储加密</h3>
            <p class="mt-2 text-xs text-neutral-500 leading-relaxed">文件访问通过操作凭证、空间上下文和服务边界控制，实际加密策略按部署验证</p>
            <ul class="mt-4 space-y-1 text-left text-xs text-neutral-500">
              <li class="flex items-start gap-2"><i class="fa fa-check text-success mt-0.5"></i> 预览与下载操作凭证</li>
              <li class="flex items-start gap-2"><i class="fa fa-check text-success mt-0.5"></i> 内部服务请求校验</li>
              <li class="flex items-start gap-2"><i class="fa fa-check text-success mt-0.5"></i> 存储加密按部署验证</li>
            </ul>
          </div>
          <div class="rounded-2xl border border-neutral-200 bg-white p-6 text-center hover:shadow-lg transition">
            <div class="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-warning/10">
              <i class="fa fa-shield text-2xl text-warning"></i>
            </div>
            <h3 class="mt-4 text-base font-semibold text-neutral-800">访问控制</h3>
            <p class="mt-2 text-xs text-neutral-500 leading-relaxed">空间成员、角色、资源范围和临时操作凭证共同控制访问</p>
            <ul class="mt-4 space-y-1 text-left text-xs text-neutral-500">
              <li class="flex items-start gap-2"><i class="fa fa-check text-success mt-0.5"></i> 空间成员与角色</li>
              <li class="flex items-start gap-2"><i class="fa fa-check text-success mt-0.5"></i> JWT 与客户端身份校验</li>
              <li class="flex items-start gap-2"><i class="fa fa-check text-success mt-0.5"></i> 资源范围校验</li>
            </ul>
          </div>
          <div class="rounded-2xl border border-neutral-200 bg-white p-6 text-center hover:shadow-lg transition">
            <div class="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-info/10">
              <i class="fa fa-file-text text-2xl text-info"></i>
            </div>
            <h3 class="mt-4 text-base font-semibold text-neutral-800">审计合规</h3>
            <p class="mt-2 text-xs text-neutral-500 leading-relaxed">事件、执行状态和部署侧日志可用于追踪；合规结果需要实际环境验证</p>
            <ul class="mt-4 space-y-1 text-left text-xs text-neutral-500">
              <li class="flex items-start gap-2"><i class="fa fa-check text-success mt-0.5"></i> 操作审计日志</li>
              <li class="flex items-start gap-2"><i class="fa fa-check text-success mt-0.5"></i> 事件与执行记录</li>
              <li class="flex items-start gap-2"><i class="fa fa-check text-success mt-0.5"></i> 部署侧监控接入</li>
            </ul>
          </div>
        </div>
      </div>
    </section>

    <!-- Certifications -->
    <section class="border-t border-neutral-100 bg-neutral-50/50 py-20 sm:py-24">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="mb-16 text-center">
          <h2 class="text-3xl font-bold text-neutral-900">安全能力与部署责任</h2>
          <p class="mt-4 text-neutral-500">页面只展示代码中可核验的机制；认证、合规和备份结果需要结合实际部署验证</p>
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
        <p class="mt-4 text-neutral-500 leading-relaxed">安全能力由网关认证、空间上下文、操作凭证、内部服务令牌、文件状态和事件幂等共同组成。实际加密、备份、网络隔离和合规状态取决于部署环境与运维配置。</p>
        <p class="mt-4 text-sm text-neutral-400">如需反馈安全问题，请通过项目仓库的 Issue 或维护团队约定的安全渠道联系。</p>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
// 需求编号：REQ-WEB-CONTENT-2026-07
// 改动点：将未提供认证依据的合规认证和安全承诺替换为网关、空间、凭证、内部服务与生命周期机制。
// 原有行为：把虚构认证、加密、备份和审计结果作为既成事实；新行为：区分代码能力与部署侧需要验证的安全结论。
// 影响范围：仅影响官网安全页文字和静态条目，不改变布局或安全实现。
const certifications = [
  { name: '网关认证', desc: '统一入口、JWT 与路由校验', icon: 'fa fa-certificate', color: 'text-primary' },
  { name: '空间上下文', desc: '成员、角色与资源边界', icon: 'fa fa-users', color: 'text-success' },
  { name: '操作凭证', desc: '预览、下载等临时授权', icon: 'fa fa-key', color: 'text-info' },
  { name: '内部服务令牌', desc: '服务间调用校验', icon: 'fa fa-lock', color: 'text-purple-500' },
  { name: '生命周期状态', desc: '文件处理阶段与状态机', icon: 'fa fa-refresh', color: 'text-danger' },
  { name: '事件幂等', desc: '异步处理的去重与恢复', icon: 'fa fa-history', color: 'text-warning' },
  { name: '安全配置', desc: '部署侧网络与密钥管理', icon: 'fa fa-cog', color: 'text-primary' },
  { name: '审计依据', desc: '以实际日志和监控为准', icon: 'fa fa-list-alt', color: 'text-info' },
]

const securityFeatures = [
  { title: '统一认证', desc: '请求经网关进入平台，认证与路由配置由网关服务集中处理。', icon: 'fa fa-lock', bgClass: 'bg-primary/10', iconClass: 'text-primary' },
  { title: '文件处理校验', desc: '上传会话、分片、合并、校验和激活由业务与存储服务分工处理。', icon: 'fa fa-refresh', bgClass: 'bg-danger/10', iconClass: 'text-danger' },
  { title: '空间访问控制', desc: '空间上下文参与成员、角色、资源范围和插件授权判断。', icon: 'fa fa-users', bgClass: 'bg-warning/10', iconClass: 'text-warning' },
  { title: '内部调用保护', desc: '内部 API 使用服务令牌、来源策略和明确的控制器边界。', icon: 'fa fa-filter', bgClass: 'bg-info/10', iconClass: 'text-info' },
  { title: '操作追踪', desc: '分享、预览、下载、上传和自动化执行均可结合状态与日志追踪。', icon: 'fa fa-history', bgClass: 'bg-purple-50', iconClass: 'text-purple-500' },
  { title: '扩展运行隔离', desc: '插件包校验、权限声明和运行时沙箱共同限制扩展能力。', icon: 'fa fa-shield', bgClass: 'bg-success/10', iconClass: 'text-success' },
  { title: '安全外发', desc: '分享资源和访问凭证分开管理，支持按资源范围授权和撤回。', icon: 'fa fa-share-square-o', bgClass: 'bg-primary/10', iconClass: 'text-primary' },
  { title: '部署侧保护', desc: '密钥、网络、备份、对象存储和监控需要由部署方按环境配置。', icon: 'fa fa-copy', bgClass: 'bg-info/10', iconClass: 'text-info' },
]
</script>
