<template>
  <div class="space-y-5">
    <PageHeader title="插件市场" description="发现经过审核的云插件与多平台客户端扩展">
      <template #actions><router-link class="secondary-button" to="/app/plugins"><i class="fa fa-arrow-left"></i> 我的插件</router-link></template>
    </PageHeader>
    <section class="market-hero">
      <div><p class="text-xs font-bold uppercase tracking-[0.2em] text-primary">Marketplace</p><h2 class="mt-2 text-2xl font-bold text-neutral-900">让每个空间拥有专属工具</h2><p class="mt-2 text-sm text-neutral-500">安装前清晰展示权限，运行时始终受用户和空间权限最小交集约束。</p></div>
      <div class="relative w-full max-w-md"><i class="fa fa-search absolute left-4 top-1/2 -translate-y-1/2 text-neutral-400"></i><input v-model.trim="query" class="market-search" placeholder="搜索插件、能力或开发者" @keyup.enter="load" /></div>
    </section>
    <div class="flex gap-2 overflow-x-auto">
      <button v-for="item in filters" :key="item.value" class="filter-pill" :class="{ active: type === item.value }" @click="type = item.value; load()">{{ item.label }}</button>
    </div>
    <PageState
      v-if="loading || error || !items.length"
      :type="loading ? 'loading' : error ? 'error' : 'empty'"
      :icon="loading ? 'fa fa-spinner fa-spin' : error ? 'fa fa-exclamation-triangle' : 'fa fa-shopping-bag'"
      :title="loading ? '正在加载插件市场' : error ? '插件市场加载失败' : '暂无已审核插件'"
      :description="error || '市场只展示通过安全审核的不可变版本。'"
      :action-text="error ? '重新加载' : ''"
      action-icon="fa fa-refresh"
      @action="load"
    />
    <div v-else class="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
      <article v-for="item in items" :key="item.pluginId" class="market-card">
        <div class="flex items-start gap-3"><span class="market-icon" :class="item.pluginType === 'LOCAL_PLUGIN' ? 'bg-violet-100 text-violet-600' : 'bg-blue-100 text-primary'"><i :class="item.pluginType === 'LOCAL_PLUGIN' ? 'fa fa-desktop' : 'fa fa-cloud'"></i></span><div class="min-w-0 flex-1"><h2 class="truncate font-bold text-neutral-800">{{ item.name }}</h2><p class="text-xs text-neutral-400">{{ item.authorDisplayName }} · v{{ item.latestVersion }}</p></div><span class="rounded-lg bg-neutral-100 px-2 py-1 text-[10px] font-bold text-neutral-500">{{ item.pluginType === 'LOCAL_PLUGIN' ? '本地' : '云端' }}</span></div>
        <router-link class="mt-4 block line-clamp-3 min-h-[60px] text-sm leading-5 text-neutral-500 hover:text-primary" :to="`/developer/marketplace/plugins/${item.pluginId}`">{{ item.description || '暂无描述' }}</router-link>
        <div class="mt-4 flex items-center gap-4 text-xs text-neutral-400"><span class="text-amber-500"><i class="fa fa-star"></i> {{ Number(item.averageRating).toFixed(1) }}</span><span><i class="fa fa-download"></i> {{ item.installationCount }}</span><span><i class="fa fa-comment-o"></i> {{ item.ratingCount }}</span></div>
        <div class="mt-4 flex gap-2 border-t border-neutral-100 pt-4"><router-link class="detail-button" :to="`/developer/marketplace/plugins/${item.pluginId}`">查看详情</router-link><button class="install-button" @click="openInstall(item)"><i class="fa fa-plus-circle"></i> 安装插件</button></div>
      </article>
    </div>

    <div v-if="installingItem" class="fixed inset-0 z-[110] flex items-center justify-center bg-black/40 p-4" @click.self="installingItem = null">
      <section class="w-full max-w-lg rounded-2xl bg-white p-5 shadow-2xl sm:p-6">
        <div class="flex items-start justify-between"><div><h2 class="text-lg font-bold">安装 {{ installingItem.name }}</h2><p class="mt-1 text-xs text-neutral-400">请确认插件申请的权限</p></div><button class="icon-button" @click="installingItem = null"><i class="fa fa-times"></i></button></div>
        <div class="mt-4 max-h-72 space-y-2 overflow-y-auto">
          <label v-for="permission in installPermissions" :key="permission" class="flex items-center gap-3 rounded-xl border border-neutral-200 p-3 text-sm text-neutral-600"><input v-model="grantedPermissions" type="checkbox" :value="permission" class="accent-primary" /><i class="fa fa-check-circle text-success"></i><span>{{ permissionLabel(permission) }}</span></label>
        </div>
        <div class="mt-5 flex flex-col gap-2 sm:flex-row">
          <button class="secondary-button flex-1 justify-center" @click="install(false)">安装到个人</button>
          <button class="primary-button flex-1 justify-center" :disabled="spaceStore.isPersonalSpace" @click="install(true)">安装到当前空间</button>
        </div>
        <p v-if="spaceStore.isPersonalSpace" class="mt-2 text-center text-xs text-neutral-400">选择团队/企业空间后可安装到空间</p>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import PageHeader from '@/components/common/PageHeader.vue'
import PageState from '@/components/common/PageState.vue'
import { installPluginForSpaceApi, installPluginForUserApi, listPluginMarketplaceApi, type MarketplacePlugin } from '@/api/modules/plugins'
import { useSpaceStore } from '@/stores/spaceStore'
import { useToastStore } from '@/stores/toastStore'

const spaceStore = useSpaceStore()
const toast = useToastStore()
const items = ref<MarketplacePlugin[]>([])
const loading = ref(false)
const error = ref('')
const query = ref('')
const type = ref('')
const installingItem = ref<MarketplacePlugin | null>(null)
const installPermissions = ref<string[]>([])
const grantedPermissions = ref<string[]>([])
const filters = [{ label: '全部', value: '' }, { label: '云插件', value: 'CLOUD_PLUGIN' }, { label: '本地插件', value: 'LOCAL_PLUGIN' }]
async function load() {
  loading.value = true; error.value = ''
  try { items.value = (await listPluginMarketplaceApi(type.value, query.value)).data }
  catch (err: any) { error.value = err?.message || '市场加载失败' }
  finally { loading.value = false }
}
function openInstall(item: MarketplacePlugin) {
  installingItem.value = item
  try { installPermissions.value = JSON.parse(item.permissionConfig || '[]') } catch { installPermissions.value = [] }
  grantedPermissions.value = [...installPermissions.value]
}
async function install(toSpace: boolean) {
  if (!installingItem.value || !grantedPermissions.value.length) {
    toast.showToast('至少授权一项插件权限', 'warning'); return
  }
  try {
    if (toSpace) await installPluginForSpaceApi(installingItem.value.pluginId, installingItem.value.latestVersion, grantedPermissions.value)
    else await installPluginForUserApi(installingItem.value.pluginId, installingItem.value.latestVersion, grantedPermissions.value)
    toast.showToast(toSpace ? '插件已安装到当前空间' : '插件已安装到个人账号', 'success')
    installingItem.value = null
  } catch (err: any) { toast.showToast(err?.message || '安装失败', 'error') }
}
function permissionLabel(value: string) {
  const labels: Record<string, string> = {
    'file.content.read': '读取已激活文件内容', 'file.metadata.read': '读取文件元数据',
    'file.metadata.write': '修改文件元数据', 'file.content.write_pre_activation': '激活前修改文件内容',
    'client.file.read': '读取用户选择的本地文件', 'client.file.upload': '上传文件到网盘',
    'client.ui.show': '显示插件界面', 'client.system.notify': '发送系统通知',
    'plugin.log.write': '记录脱敏执行日志',
  }
  return labels[value] || value
}
onMounted(load)
</script>

<style scoped>
.market-hero { @apply flex flex-col gap-5 overflow-hidden rounded-3xl border border-primary/10 bg-gradient-to-br from-blue-50 via-white to-violet-50 p-6 sm:flex-row sm:items-center sm:justify-between lg:p-8; }
.market-search { @apply w-full rounded-2xl border border-white bg-white/90 py-3.5 pl-11 pr-4 text-sm shadow-lg shadow-primary/5 outline-none focus:border-primary/30; }
.market-card { @apply rounded-2xl border border-neutral-200 bg-white p-5 shadow-sm transition hover:-translate-y-0.5 hover:border-primary/20 hover:shadow-lg; }
.market-icon { @apply flex h-11 w-11 shrink-0 items-center justify-center rounded-xl text-lg; }
.filter-pill { @apply whitespace-nowrap rounded-full border border-neutral-200 bg-white px-4 py-2 text-xs font-semibold text-neutral-500; }
.filter-pill.active { @apply border-primary bg-primary text-white; }
.install-button { @apply inline-flex min-h-10 w-full items-center justify-center gap-2 rounded-xl bg-neutral-900 text-sm font-semibold text-white transition hover:bg-primary; }
.detail-button { @apply inline-flex min-h-10 items-center justify-center rounded-xl border border-neutral-200 px-3 text-xs font-semibold text-neutral-600 hover:border-primary hover:text-primary; }
.primary-button,.secondary-button { @apply inline-flex min-h-10 items-center gap-2 rounded-xl px-4 text-sm font-semibold disabled:opacity-50; }
.primary-button { @apply bg-primary text-white; }.secondary-button { @apply border border-neutral-200 bg-white text-neutral-600; }
</style>
