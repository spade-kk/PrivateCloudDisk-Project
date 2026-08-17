<template>
  <div class="space-y-5">
    <PageHeader
      title="空间工具"
      :description="spaceStore.currentSpaceName ? `管理「${spaceStore.currentSpaceName}」共享的插件与自动化能力` : '请选择团队或企业空间'"
    >
      <template #actions>
        <CurrentSpaceBadge />
        <button class="secondary-button" type="button" :disabled="spaceStore.isPersonalSpace" @click="installCustom"><i class="fa fa-upload"></i> 自定义安装</button>
        <router-link class="primary-button" to="/app/plugin-market"><i class="fa fa-plus"></i> 添加空间插件</router-link>
      </template>
    </PageHeader>
    <div v-if="spaceStore.isPersonalSpace" class="rounded-2xl border border-amber-200 bg-amber-50 p-6 text-sm text-amber-700">
      当前是默认个人空间。请从左侧空间选择器切换到团队、项目或企业空间后管理共享插件。
    </div>
    <PageState
      v-else-if="loading || error || !spaceInstallations.length"
      :type="loading ? 'loading' : error ? 'error' : 'empty'"
      :icon="loading ? 'fa fa-spinner fa-spin' : error ? 'fa fa-exclamation-triangle' : 'fa fa-briefcase'"
      :title="loading ? '正在加载空间工具' : error ? '空间工具加载失败' : '当前空间还没有插件'"
      :description="error || '空间管理员可从插件市场安装团队共享工具。'"
      :action-text="error ? '重新加载' : ''"
      action-icon="fa fa-refresh"
      @action="load"
    />
    <div v-else class="overflow-hidden rounded-2xl border border-neutral-200 bg-white shadow-sm">
      <article v-for="item in spaceInstallations" :key="item.installationId" class="installation-row">
        <span class="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl" :class="item.pluginType === 'LOCAL_PLUGIN' ? 'bg-violet-100 text-violet-600' : 'bg-blue-100 text-primary'"><i :class="item.pluginType === 'LOCAL_PLUGIN' ? 'fa fa-desktop' : 'fa fa-cloud'"></i></span>
        <div class="min-w-0"><h2 class="truncate font-bold text-neutral-800">{{ item.pluginName }}</h2><p class="text-xs text-neutral-400">v{{ item.version }} · {{ item.pluginType === 'LOCAL_PLUGIN' ? '客户端分发' : '服务端沙箱' }}</p></div>
        <span class="hidden rounded-lg bg-neutral-100 px-2 py-1 text-xs text-neutral-500 sm:inline">{{ permissionCount(item) }} 项权限</span>
        <label class="flex items-center gap-2 text-xs text-neutral-500"><input type="checkbox" class="peer sr-only" :checked="item.enabled" @change="toggle(item, ($event.target as HTMLInputElement).checked)" /><span class="relative h-6 w-11 rounded-full bg-neutral-300 transition peer-checked:bg-primary after:absolute after:left-1 after:top-1 after:h-4 after:w-4 after:rounded-full after:bg-white after:transition peer-checked:after:translate-x-5"></span>{{ item.enabled ? '已启用' : '已停用' }}</label>
        <button class="icon-button text-danger" title="从空间移除" @click="remove(item)"><i class="fa fa-trash-o"></i></button>
      </article>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import PageHeader from '@/components/common/PageHeader.vue'
import PageState from '@/components/common/PageState.vue'
import CurrentSpaceBadge from '@/components/space/CurrentSpaceBadge.vue'
import { installPluginForSpaceApi, listPluginInstallationsApi, setSpacePluginEnabledApi, uninstallSpacePluginApi, type PluginInstallation } from '@/api/modules/plugins'
import { useSpaceStore } from '@/stores/spaceStore'
import { useToastStore } from '@/stores/toastStore'
const spaceStore = useSpaceStore(); const toast = useToastStore()
const installations = ref<PluginInstallation[]>([]); const loading = ref(false); const error = ref('')
const spaceInstallations = computed(() => installations.value.filter((item) => item.scopeType === 'SPACE'))
async function load() { if (spaceStore.isPersonalSpace) return; loading.value = true; error.value=''; try { installations.value=(await listPluginInstallationsApi()).data } catch(err:any){ error.value=err?.message||'空间插件加载失败'} finally {loading.value=false} }
async function toggle(item: PluginInstallation, enabled: boolean) { try { await setSpacePluginEnabledApi(item.installationId, enabled); item.enabled=enabled; toast.showToast(enabled?'空间插件已启用':'空间插件已停用','success') } catch(err:any){ toast.showToast(err?.message||'状态更新失败','error'); await load() } }
async function remove(item: PluginInstallation) { if(!confirm(`确定从当前空间移除“${item.pluginName}”吗？`))return; try{await uninstallSpacePluginApi(item.installationId);installations.value=installations.value.filter(v=>v.installationId!==item.installationId);toast.showToast('空间插件已移除','success')}catch(err:any){toast.showToast(err?.message||'移除失败','error')} }
async function installCustom() {
  if (spaceStore.isPersonalSpace) return
  const pluginId = prompt('请输入已审核插件 ID'); const version = prompt('请输入安装版本号', '1.0.0')
  if (!pluginId || !version) return
  try { await installPluginForSpaceApi(pluginId.trim(), version.trim(), ['plugin.log.write']); toast.showToast('自定义插件已提交安装', 'success'); await load() } catch (err: any) { toast.showToast(err?.message || '自定义安装失败', 'error') }
}
function permissionCount(item: PluginInstallation){try{return JSON.parse(item.grantedPermissionsJson||'[]').length}catch{return 0}}
watch(()=>spaceStore.currentSpaceId,load);onMounted(load)
</script>
<style scoped>
.installation-row { @apply grid grid-cols-[44px_minmax(0,1fr)_auto_auto] items-center gap-3 border-b border-neutral-100 p-4 last:border-0 sm:grid-cols-[44px_minmax(0,1fr)_auto_auto_auto]; }
.primary-button { @apply inline-flex min-h-10 items-center gap-2 rounded-xl bg-primary px-4 text-sm font-semibold text-white; }
.secondary-button { @apply inline-flex min-h-10 items-center gap-2 rounded-xl border border-neutral-200 bg-white px-4 text-sm font-semibold text-neutral-600 disabled:opacity-50; }
</style>
