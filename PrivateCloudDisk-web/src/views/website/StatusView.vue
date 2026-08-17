<template>
  <div>
    <section class="bg-gradient-to-br from-primary/5 via-white to-info/5 py-20 sm:py-28">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="mx-auto max-w-3xl text-center">
          <span class="inline-flex items-center gap-2 rounded-full bg-success/10 px-3 py-1 text-xs font-medium text-success">系统状态</span>
          <h1 class="mt-4 text-4xl font-extrabold tracking-tight text-neutral-900 sm:text-5xl">服务状态监控</h1>
          <p class="mt-4 text-lg text-neutral-500">展示服务清单与监控接入边界；当前页面不伪造实时可用性或 SLA 指标</p>
        </div>
      </div>
    </section>

    <!-- Overall Status -->
    <section class="border-b border-neutral-100">
      <div class="mx-auto max-w-5xl px-4 py-8 sm:px-6 lg:px-8">
        <div class="flex items-center gap-3 rounded-2xl bg-success/5 border border-success/20 p-4">
          <i class="fa fa-info-circle text-2xl text-info"></i>
          <div>
            <p class="text-sm font-semibold text-info">状态探针未连接</p>
            <p class="text-xs text-neutral-400">最后更新于 {{ lastUpdated }}</p>
          </div>
        </div>
      </div>
    </section>

    <section class="py-16 sm:py-20">
      <div class="mx-auto max-w-5xl px-4 sm:px-6 lg:px-8">
        <div class="space-y-4">
          <div v-for="group in serviceGroups" :key="group.name">
            <h3 class="text-sm font-semibold text-neutral-500 mb-3 uppercase tracking-wider">{{ group.name }}</h3>
            <div class="space-y-2">
              <div v-for="svc in group.services" :key="svc.name" class="flex items-center justify-between rounded-xl border border-neutral-200 p-4 hover:border-neutral-300 transition">
                <div>
                  <p class="text-sm font-semibold text-neutral-700">{{ svc.name }}</p>
                  <p class="text-xs text-neutral-400">{{ svc.desc }}</p>
                </div>
                <div class="flex items-center gap-3">
                  <span class="text-xs text-neutral-400">{{ svc.uptime }}</span>
                  <div class="flex items-center gap-1.5">
                    <span class="h-2 w-2 rounded-full" :class="svc.status === 'operational' ? 'bg-success' : svc.status === 'degraded' ? 'bg-warning' : 'bg-danger'"></span>
                    <span class="text-xs font-medium" :class="svc.status === 'operational' ? 'text-success' : svc.status === 'degraded' ? 'text-warning' : 'text-danger'">{{ svc.statusText }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- Incident History -->
    <section class="border-t border-neutral-100 bg-neutral-50/50 py-16 sm:py-20">
      <div class="mx-auto max-w-5xl px-4 sm:px-6 lg:px-8">
        <h2 class="text-2xl font-bold text-neutral-900 mb-8">事件记录</h2>
        <div class="space-y-4">
          <div v-for="incident in incidents" :key="incident.id" class="rounded-xl border border-neutral-200 bg-white p-5">
            <div class="flex items-start gap-4">
              <div class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full" :class="incident.severity === 'minor' ? 'bg-warning/10 text-warning' : 'bg-danger/10 text-danger'">
                <i :class="incident.severity === 'minor' ? 'fa fa-exclamation' : 'fa fa-exclamation-triangle'"></i>
              </div>
              <div class="flex-1">
                <div class="flex items-center gap-2 flex-wrap">
                  <p class="text-sm font-semibold text-neutral-700">{{ incident.title }}</p>
                  <span class="rounded-full px-2 py-0.5 text-[10px] font-medium" :class="incident.resolved ? 'bg-success/10 text-success' : 'bg-warning/10 text-warning'">{{ incident.resolved ? '已解决' : '处理中' }}</span>
                </div>
                <p class="mt-1 text-xs text-neutral-400">{{ incident.date }} · {{ incident.duration }}</p>
                <p class="mt-2 text-sm text-neutral-500">{{ incident.desc }}</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- Subscribe -->
    <section class="py-16">
      <div class="mx-auto max-w-3xl px-4 text-center sm:px-6 lg:px-8">
        <h2 class="text-2xl font-bold text-neutral-900">订阅状态更新</h2>
        <p class="mt-2 text-neutral-500">通过邮件、Slack 或 Webhook 接收服务状态变更通知</p>
        <div class="mt-6 flex justify-center gap-3">
          <button class="rounded-xl border border-neutral-200 px-5 py-2.5 text-sm font-medium text-neutral-600 hover:border-primary hover:text-primary"><i class="fa fa-envelope mr-1"></i> 邮件</button>
          <button class="rounded-xl border border-neutral-200 px-5 py-2.5 text-sm font-medium text-neutral-600 hover:border-primary hover:text-primary"><i class="fa fa-slack mr-1"></i> Slack</button>
          <button class="rounded-xl border border-neutral-200 px-5 py-2.5 text-sm font-medium text-neutral-600 hover:border-primary hover:text-primary"><i class="fa fa-rss mr-1"></i> RSS</button>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
// 需求编号：REQ-WEB-CONTENT-2026-07
// 改动点：移除未连接监控系统时的虚构可用率、事件和“全部正常”结论。
// 原有行为：页面静态伪造实时状态和 SLA 数值；新行为：明确当前页面仅为服务清单，状态需接入真实探针。
// 影响范围：仅影响官网状态页展示数据，不改变监控接入接口或页面布局。
const lastUpdated = '页面文案更新于 2026-07-29 CST'

const serviceGroups = [
  {
    name: '核心服务',
    services: [
      { name: '文件上传服务', desc: '文件上传、分片上传、内容处理', status: 'unknown', statusText: '待探针', uptime: '—' },
      { name: '文件下载服务', desc: '文件下载、Range 访问、媒体播放', status: 'unknown', statusText: '待探针', uptime: '—' },
      { name: '文件管理 API', desc: '文件、文件夹、搜索、移动和删除', status: 'unknown', statusText: '待探针', uptime: '—' },
      { name: '用户认证服务', desc: '登录、注册、用户设置和会话', status: 'unknown', statusText: '待探针', uptime: '—' },
    ],
  },
  {
    name: '存储与基础设施',
    services: [
      { name: 'MinIO / 文件存储', desc: '文件对象与派生资源存储', status: 'unknown', statusText: '待探针', uptime: '—' },
      { name: 'MySQL 数据库', desc: '业务、空间、插件和工作流数据', status: 'unknown', statusText: '待探针', uptime: '—' },
      { name: 'Redis 缓存', desc: '会话、限流和临时凭证', status: 'unknown', statusText: '待探针', uptime: '—' },
      { name: 'RabbitMQ 消息队列', desc: '文件生命周期与异步任务消息', status: 'unknown', statusText: '待探针', uptime: '—' },
    ],
  },
  {
    name: '安全与增值服务',
    services: [
      { name: '文件预览能力', desc: '图片、PDF、Office、代码、压缩包和媒体预览', status: 'unknown', statusText: '按配置', uptime: '—' },
      { name: '插件服务', desc: '插件版本、权限、安装和市场能力', status: 'unknown', statusText: '待探针', uptime: '—' },
      { name: '工作流服务', desc: '工作流校验、发布、执行和市场', status: 'unknown', statusText: '待探针', uptime: '—' },
      { name: '自动化与调度', desc: '事件触发、任务恢复和定时调度', status: 'unknown', statusText: '待探针', uptime: '—' },
    ],
  },
  {
    name: '客户端与 API',
    services: [
      { name: 'Web 控制台', desc: '官网、文件管理、空间和扩展页面', status: 'unknown', statusText: '待探针', uptime: '—' },
      { name: 'API 网关', desc: 'REST API 统一入口、认证和路由', status: 'unknown', statusText: '待探针', uptime: '—' },
      { name: '即时通讯服务', desc: '消息、群组、通知和音视频通话', status: 'unknown', statusText: '待探针', uptime: '—' },
      { name: '客户端注册服务', desc: '客户端身份、挑战和扩展绑定', status: 'unknown', statusText: '待探针', uptime: '—' },
    ],
  },
]

const incidents = [
  {
    id: 1, title: '事件数据待接入真实监控', date: '—', duration: '—', resolved: true, severity: 'minor',
    desc: '当前页面只提供服务清单展示，未连接生产探针、事件数据库或 SLA 统计，不对运行状态作事实承诺。',
  },
]
</script>
