<template>
  <div>
    <section class="bg-gradient-to-br from-primary/5 via-white to-info/5 py-20 sm:py-28">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="mx-auto max-w-3xl text-center">
          <span class="inline-flex items-center gap-2 rounded-full bg-primary/10 px-3 py-1 text-xs font-medium text-primary">更新日志</span>
          <h1 class="mt-4 text-4xl font-extrabold tracking-tight text-neutral-900 sm:text-5xl">产品更新日志</h1>
          <p class="mt-4 text-lg text-neutral-500">持续迭代，不断进化，记录 CloudDrive 的每一个重要更新</p>
        </div>
      </div>
    </section>

    <section class="py-20 sm:py-24">
      <div class="mx-auto max-w-4xl px-4 sm:px-6 lg:px-8">
        <!-- Subscribe -->
        <div class="mb-16 flex flex-col items-center justify-between gap-4 rounded-2xl border border-neutral-200 bg-neutral-50/50 p-6 sm:flex-row">
          <div>
            <p class="text-sm font-semibold text-neutral-700">订阅更新通知</p>
            <p class="text-xs text-neutral-400">通过邮件接收最新版本更新通知</p>
          </div>
          <form @submit.prevent class="flex gap-2">
            <input type="email" placeholder="输入邮箱" class="rounded-xl border border-neutral-200 px-4 py-2 text-sm focus:border-primary focus:outline-none w-48" />
            <button class="rounded-xl bg-primary px-4 py-2 text-sm font-semibold text-white hover:bg-primary/90">订阅</button>
          </form>
        </div>

        <!-- Timeline -->
        <div class="relative">
          <div class="absolute left-4 top-0 bottom-0 w-px bg-neutral-200 sm:left-8" aria-hidden></div>
          <div class="space-y-12">
            <div v-for="release in releases" :key="release.version" class="relative pl-12 sm:pl-16">
              <div class="absolute left-0 top-1 flex h-8 w-8 items-center justify-center rounded-full border-4 border-white bg-primary text-white sm:left-4">
                <i class="fa fa-tag text-xs"></i>
              </div>
              <div>
                <div class="flex flex-wrap items-center gap-3">
                  <span class="inline-flex items-center rounded-lg bg-primary/10 px-3 py-1 text-sm font-bold text-primary">v{{ release.version }}</span>
                  <span class="text-sm text-neutral-400">{{ release.date }}</span>
                  <span v-if="release.latest" class="rounded-full bg-success/10 px-2 py-0.5 text-[10px] font-medium text-success">Latest</span>
                </div>
                <p class="mt-2 text-lg font-semibold text-neutral-800">{{ release.title }}</p>
                <p class="mt-1 text-sm text-neutral-500">{{ release.summary }}</p>
                <div class="mt-5 space-y-4">
                  <div v-for="section in release.sections" :key="section.label">
                    <p class="text-xs font-semibold uppercase tracking-wider text-neutral-400 mb-2">{{ section.label }}</p>
                    <ul class="space-y-1.5">
                      <li v-for="item in section.items" :key="item" class="flex items-start gap-2 text-sm">
                        <i class="fa fa-plus-circle text-[10px] text-success mt-1 shrink-0" v-if="section.label === '新增功能'"></i>
                        <i class="fa fa-wrench text-[10px] text-info mt-1 shrink-0" v-else-if="section.label === '优化改进'"></i>
                        <i class="fa fa-bug text-[10px] text-danger mt-1 shrink-0" v-else></i>
                        <span class="text-neutral-600">{{ item }}</span>
                      </li>
                    </ul>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Pagination -->
        <div class="mt-12 flex justify-center">
          <button class="rounded-xl border border-neutral-200 px-6 py-2.5 text-sm font-medium text-neutral-600 hover:border-primary hover:text-primary transition">加载更多版本</button>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
const releases = [
  {
    version: '3.2.0', date: '2026-01-10', latest: true,
    title: 'AI 智能搜索与全新协作体验',
    summary: '全新 AI 驱动搜索引擎，支持自然语言查询和 OCR 图片文字搜索。新增团队协作空间和实时协同编辑功能。',
    sections: [
      { label: '新增功能', items: ['AI 智能搜索引擎，支持自然语言查询', 'OCR 图片文字搜索能力', '团队协作空间功能', '实时协同编辑 Office 文档', '智能文件标签与自动分类', '文件多维度搜索筛选'] },
      { label: '优化改进', items: ['大幅优化文件搜索性能，提升 10 倍', '重新设计文件预览界面', '优化 WebDAV 协议兼容性', '改进移动端文件上传体验', 'SSO 单点登录支持 SAML 2.0'] },
      { label: '问题修复', items: ['修复大文件下载偶发中断问题', '修复文件历史版本恢复权限校验', '修复回收站批量删除偶发报错', '修复 iOS 客户端文档预览崩溃', '修复 LDAP 同步用户组异常'] },
    ],
  },
  {
    version: '3.1.0', date: '2025-12-15',
    title: '数据防泄漏与安全增强',
    summary: '新增数据防泄漏（DLP）功能，支持敏感数据自动识别与保护。国密算法支持正式上线。',
    sections: [
      { label: '新增功能', items: ['数据防泄漏（DLP）功能', '国密 SM2/SM4 加密算法支持', '文件安全外发水印功能', '异常登录检测与告警', 'IP 白名单与访问时间窗口'] },
      { label: '优化改进', items: ['安全审计报告导出功能', '用户操作日志查询优化', '系统性能监控仪表盘', '大文件上传分片并发数优化'] },
      { label: '问题修复', items: ['修复文件分享链接安全性问题', '修复批量操作权限校验漏洞', '修复 MinIO 存储兼容性问题'] },
    ],
  },
  {
    version: '3.0.0', date: '2025-11-01',
    title: '全新架构，性能飞跃',
    summary: 'CloudDrive 3.0 采用全新微服务架构，性能提升 300%。新增 MinIO 对象存储支持，全新的 UI 设计。',
    sections: [
      { label: '新增功能', items: ['全新微服务架构', 'MinIO 对象存储支持', '全新的 Web 管理控制台', '多引擎病毒扫描能力', '文件版本管理系统', 'API 开放平台'] },
      { label: '优化改进', items: ['文件上传速度提升 300%', '系统启动时间缩短 80%', '内存占用降低 40%', '全新 UI 设计语言'] },
      { label: '问题修复', items: ['修复大量已知问题', '改进系统稳定性', '优化数据库查询性能'] },
    ],
  },
  {
    version: '2.5.0', date: '2025-09-01',
    title: '移动端全面升级',
    summary: 'iOS 和 Android 客户端全面升级，支持离线文件、自动备份、文件扫描等功能。',
    sections: [
      { label: '新增功能', items: ['移动端离线文件支持', '手机相册自动备份', '文件扫描 OCR 功能', '移动端文件元数据编辑'] },
      { label: '优化改进', items: ['移动端启动速度优化', '列表加载性能优化', '图片预览体验优化', '电量消耗优化'] },
      { label: '问题修复', items: ['修复 iOS 后台上传崩溃', '修复 Android 大文件下载', '修复移动端横屏适配'] },
    ],
  },
]
</script>