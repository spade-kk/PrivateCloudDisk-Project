<template>
  <div>
    <section class="bg-gradient-to-br from-primary/5 via-white to-info/5 py-20 sm:py-28">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="mx-auto max-w-3xl text-center">
          <span class="inline-flex items-center gap-2 rounded-full bg-success/10 px-3 py-1 text-xs font-medium text-success">系统状态</span>
          <h1 class="mt-4 text-4xl font-extrabold tracking-tight text-neutral-900 sm:text-5xl">服务状态监控</h1>
          <p class="mt-4 text-lg text-neutral-500">实时了解 CloudDrive 各项服务的运行状态</p>
        </div>
      </div>
    </section>

    <!-- Overall Status -->
    <section class="border-b border-neutral-100">
      <div class="mx-auto max-w-5xl px-4 py-8 sm:px-6 lg:px-8">
        <div class="flex items-center gap-3 rounded-2xl bg-success/5 border border-success/20 p-4">
          <i class="fa fa-check-circle text-2xl text-success"></i>
          <div>
            <p class="text-sm font-semibold text-success">所有系统运行正常</p>
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
        <h2 class="text-2xl font-bold text-neutral-900 mb-8">过去 90 天事件记录</h2>
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
const lastUpdated = '2026-01-15 14:30:00 CST'

const serviceGroups = [
  {
    name: '核心服务',
    services: [
      { name: '文件上传服务', desc: '文件上传、分片上传、断点续传', status: 'operational', statusText: '正常', uptime: '99.997%' },
      { name: '文件下载服务', desc: '文件下载、批量下载、流式预览', status: 'operational', statusText: '正常', uptime: '99.998%' },
      { name: '文件管理 API', desc: '文件列表、搜索、移动、删除', status: 'operational', statusText: '正常', uptime: '99.996%' },
      { name: '用户认证服务', desc: '登录、注册、SSO、2FA', status: 'operational', statusText: '正常', uptime: '99.999%' },
    ],
  },
  {
    name: '存储与基础设施',
    services: [
      { name: 'MinIO 对象存储', desc: '文件对象存储服务', status: 'operational', statusText: '正常', uptime: '99.999%' },
      { name: 'MySQL 数据库', desc: '元数据与业务数据存储', status: 'operational', statusText: '正常', uptime: '99.998%' },
      { name: 'Redis 缓存', desc: '会话与热数据缓存', status: 'operational', statusText: '正常', uptime: '99.999%' },
      { name: 'RabbitMQ 消息队列', desc: '异步任务消息处理', status: 'operational', statusText: '正常', uptime: '99.999%' },
    ],
  },
  {
    name: '安全与增值服务',
    services: [
      { name: '病毒扫描引擎', desc: '多引擎文件病毒扫描', status: 'operational', statusText: '正常', uptime: '99.995%' },
      { name: '文件预览服务', desc: 'Office 文档/图片/视频在线预览', status: 'operational', statusText: '正常', uptime: '99.997%' },
      { name: '全文搜索服务', desc: 'Elasticsearch 文件内容搜索', status: 'operational', statusText: '正常', uptime: '99.996%' },
      { name: '数据防泄漏', desc: '敏感数据识别与保护', status: 'operational', statusText: '正常', uptime: '99.998%' },
    ],
  },
  {
    name: '客户端与 API',
    services: [
      { name: 'Web 管理控制台', desc: 'Web 端管理界面', status: 'operational', statusText: '正常', uptime: '99.997%' },
      { name: 'WebDAV 服务', desc: 'WebDAV 协议挂载', status: 'operational', statusText: '正常', uptime: '99.996%' },
      { name: 'API 网关', desc: 'REST API 统一入口', status: 'operational', statusText: '正常', uptime: '99.998%' },
      { name: 'CDN 加速', desc: '全球 CDN 内容分发', status: 'operational', statusText: '正常', uptime: '99.999%' },
    ],
  },
]

const incidents = [
  {
    id: 1, title: '文件上传服务间歇性超时', date: '2026-01-08 10:30', duration: '1 小时 15 分钟', resolved: true, severity: 'minor',
    desc: '由于上游网络波动，部分用户在上传大文件时遇到超时。网络恢复后所有服务恢复正常。',
  },
  {
    id: 2, title: '数据库连接池耗尽', date: '2025-12-28 15:00', duration: '25 分钟', resolved: true, severity: 'minor',
    desc: '由于突发流量导致数据库连接池短暂耗尽，部分 API 请求失败。自动扩容机制触发后服务恢复。',
  },
  {
    id: 3, title: 'MinIO 存储节点磁盘故障', date: '2025-12-15 08:00', duration: '2 小时', resolved: true, severity: 'minor',
    desc: '单个存储节点磁盘故障，触发自动故障切换。数据已自动从副本恢复，无数据丢失。',
  },
]
</script>