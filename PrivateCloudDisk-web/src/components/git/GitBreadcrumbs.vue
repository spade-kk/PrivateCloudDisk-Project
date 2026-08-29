<template>
  <nav class="git-breadcrumbs" :class="{ 'is-expanded': expanded }" aria-label="文件路径">
    <button class="git-breadcrumbs__root" type="button" title="仓库根目录" @click="navigate('')" @keydown.enter="navigate('')">
      <i class="fa fa-code-fork" aria-hidden="true"></i>
      <span>{{ repositoryName }}</span>
    </button>
    <template v-if="parts.length">
      <span v-if="hasCollapsedParts" class="git-breadcrumbs__ellipsis-wrap">
        <span class="git-breadcrumbs__separator" aria-hidden="true">/</span>
        <button class="git-breadcrumbs__ellipsis" type="button" aria-label="展开完整路径" @click="expanded = !expanded">…</button>
      </span>
      <template v-for="(part, index) in visibleParts" :key="part.path">
        <span class="git-breadcrumbs__separator" aria-hidden="true">/</span>
        <button
          v-if="!(isFile && index === visibleParts.length - 1 && part.path === path)"
          class="git-breadcrumbs__item"
          type="button"
          :title="part.path"
          @click="navigate(part.path)"
          @keydown.enter="navigate(part.path)"
        >{{ part.name }}</button>
        <span v-else class="git-breadcrumbs__current" :title="part.path" aria-current="page">{{ part.name }}</span>
      </template>
    </template>
    <span v-if="loading" class="git-breadcrumbs__loading" aria-label="正在加载"><i class="fa fa-circle-o-notch fa-spin"></i></span>
  </nav>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'

interface BreadcrumbPart {
  name: string
  path: string
}

const props = withDefaults(defineProps<{
  repositoryName: string
  path?: string
  isFile?: boolean
  loading?: boolean
}>(), { path: '', isFile: false, loading: false })

const emit = defineEmits<{ navigate: [path: string] }>()
const expanded = ref(false)

const parts = computed<BreadcrumbPart[]>(() => props.path.split('/').filter(Boolean).map((name, index, values) => ({ name, path: values.slice(0, index + 1).join('/') })))
const hasCollapsedParts = computed(() => parts.value.length > 5 && !expanded.value)
const visibleParts = computed(() => hasCollapsedParts.value ? [...parts.value.slice(0, 1), ...parts.value.slice(-2)] : parts.value)

watch(() => props.path, () => { expanded.value = false })

function navigate(path: string): void {
  // AUDIT FIX [2.1-2.3,2.15-2.18] Breadcrumb navigation is an explicit event so the
  // workspace can update both its lazy tree and the right-hand directory/file view.
  emit('navigate', path)
}
</script>

<style scoped>
.git-breadcrumbs { display:flex; min-width:0; align-items:center; gap:7px; overflow:hidden; color:var(--git-muted,#57606a); font-size:12px; white-space:nowrap; }
.git-breadcrumbs__root,.git-breadcrumbs__item,.git-breadcrumbs__ellipsis { max-width:220px; overflow:hidden; border:0; background:transparent; padding:3px 2px; color:inherit; cursor:pointer; font:inherit; text-overflow:ellipsis; white-space:nowrap; }
.git-breadcrumbs__root { display:inline-flex; min-width:0; align-items:center; gap:6px; color:var(--git-text,#24292f); font-weight:600; }
.git-breadcrumbs__root span,.git-breadcrumbs__item,.git-breadcrumbs__current { overflow:hidden; text-overflow:ellipsis; }
.git-breadcrumbs__item:hover,.git-breadcrumbs__root:hover,.git-breadcrumbs__ellipsis:hover { color:#0969da; text-decoration:underline; }
.git-breadcrumbs__current { max-width:260px; color:var(--git-text,#24292f); font-weight:600; }
.git-breadcrumbs__separator { color:var(--git-muted,#8c959f); }
.git-breadcrumbs__ellipsis { width:26px; color:#0969da; font-weight:700; text-align:center; }
.git-breadcrumbs__ellipsis-wrap { display:inline-flex; align-items:center; gap:7px; }
.git-breadcrumbs__loading { flex:0 0 auto; color:#0969da; }
:global(.dark) .git-breadcrumbs__current { color:#f0f6fc; }
@media (max-width:767px) {
  .git-breadcrumbs__root { max-width:130px; }
  .git-breadcrumbs__current { max-width:150px; }
  .git-breadcrumbs__item { max-width:130px; }
}
</style>
