<template>
  <div>
    <section class="bg-gradient-to-br from-primary/5 via-white to-info/5 py-16 sm:py-20">
      <div class="mx-auto max-w-3xl px-4 sm:px-6 lg:px-8 text-center">
        <h1 class="text-3xl font-extrabold tracking-tight text-neutral-900 sm:text-4xl">隐私政策</h1>
        <p class="mt-4 text-neutral-500">最后更新于 2026 年 1 月 1 日</p>
      </div>
    </section>

    <section class="py-16 sm:py-20">
      <div class="mx-auto max-w-3xl px-4 sm:px-6 lg:px-8">
        <article class="prose prose-neutral max-w-none text-sm leading-relaxed space-y-8">
          <div>
            <h2 class="text-xl font-bold text-neutral-900">1. 引言</h2>
            <p class="mt-3 text-neutral-600">PrivateCloudDisk（以下简称"项目"）的部署方应依据适用法律和实际业务场景制定隐私规则。本页面说明代码和部署中可能涉及的信息类型，不替代部署方的正式隐私政策。</p>
          </div>

          <div>
            <h2 class="text-xl font-bold text-neutral-900">2. 我们收集的信息</h2>
            <p class="mt-3 text-neutral-600">在使用 PrivateCloudDisk 的过程中，实际部署可能处理以下类型的信息：</p>
            <ul class="mt-2 space-y-1 text-neutral-600 list-disc list-inside">
              <li><strong>账户信息：</strong>注册时提供的用户名、邮箱地址、手机号码等。</li>
              <li><strong>使用数据：</strong>您存储的文件元数据（文件名、大小、类型等），不包括文件内容本身。</li>
              <li><strong>日志信息：</strong>IP 地址、访问时间、操作行为等操作日志。</li>
              <li><strong>设备信息：</strong>设备型号、操作系统版本、浏览器类型等。</li>
            </ul>
          </div>

          <div>
            <h2 class="text-xl font-bold text-neutral-900">3. 信息的使用</h2>
            <p class="mt-3 text-neutral-600">我们收集的信息将用于以下目的：</p>
            <ul class="mt-2 space-y-1 text-neutral-600 list-disc list-inside">
              <li>提供、维护和改进我们的服务</li>
              <li>保障服务安全和用户数据安全</li>
              <li>向您发送服务相关的通知和更新</li>
              <li>进行数据分析和研究，优化用户体验</li>
              <li>遵守法律法规要求</li>
            </ul>
          </div>

          <div>
            <h2 class="text-xl font-bold text-neutral-900">4. 数据存储与安全</h2>
            <p class="mt-3 text-neutral-600">代码提供的访问和处理机制包括：</p>
            <ul class="mt-2 space-y-1 text-neutral-600 list-disc list-inside">
              <li>平台通过网关认证、空间上下文和操作凭证控制访问边界</li>
              <li>服务间使用内部请求校验和服务令牌进行边界控制</li>
              <li>文件上传、下载、预览和处理状态由服务接口协同管理</li>
              <li>实际传输加密、存储加密、备份和审计策略取决于部署配置</li>
              <li>部署方应结合实际环境完成安全审计、备份验证和合规评估</li>
            </ul>
          </div>

          <div>
            <h2 class="text-xl font-bold text-neutral-900">5. 信息共享</h2>
            <p class="mt-3 text-neutral-600">我们不会将您的个人信息出售给第三方。在以下情况下，我们可能会共享您的信息：</p>
            <ul class="mt-2 space-y-1 text-neutral-600 list-disc list-inside">
              <li>获得您的明确同意</li>
              <li>法律法规要求或政府部门强制要求</li>
              <li>与我们的服务提供商共享，以支持服务运行（如云服务商）</li>
              <li>在涉及合并、收购或资产出售时</li>
            </ul>
          </div>

          <div>
            <h2 class="text-xl font-bold text-neutral-900">6. 您的权利</h2>
            <p class="mt-3 text-neutral-600">根据适用的数据保护法律，您拥有以下权利：</p>
            <ul class="mt-2 space-y-1 text-neutral-600 list-disc list-inside">
              <li>访问您的个人信息</li>
              <li>更正不准确的个人信息</li>
              <li>删除您的个人信息（在特定条件下）</li>
              <li>限制或反对我们处理您的个人信息</li>
              <li>数据可携带权</li>
              <li>撤回同意</li>
            </ul>
          </div>

          <div>
            <h2 class="text-xl font-bold text-neutral-900">7. Cookie 和类似技术</h2>
            <p class="mt-3 text-neutral-600">我们使用 Cookie 和类似技术来提升用户体验、分析网站流量和提供个性化内容。您可以通过浏览器设置管理 Cookie 偏好。</p>
          </div>

          <div>
            <h2 class="text-xl font-bold text-neutral-900">8. 政策更新</h2>
            <p class="mt-3 text-neutral-600">我们可能会不时更新本隐私政策。如有重大变更，我们将通过网站公告、邮件或其他适当方式通知您。</p>
          </div>

          <div>
            <h2 class="text-xl font-bold text-neutral-900">9. 联系我们</h2>
            <p class="mt-3 text-neutral-600">如果您对本隐私政策有任何疑问或意见，请通过以下方式联系我们：</p>
            <p class="mt-2 text-neutral-600">本页面不固化未经确认的邮箱或办公地址</p>
            <p class="text-neutral-600">联系渠道：以项目仓库和实际部署方公布的信息为准</p>
          </div>
        </article>
      </div>
    </section>
  </div>
</template>
