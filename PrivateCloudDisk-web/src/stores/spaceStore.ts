// ============================================================
// spaceStore.ts — 空间系统状态管理
// ============================================================
// 管理多空间架构：当前空间选择、空间列表、切换逻辑。
// 通过 URL 参数 ?space=xxx 与路由双向同步。
// ============================================================

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  listSpacesApi,
  getCurrentSpaceApi,
  setCurrentSpaceApi,
  type SpaceInfo,
} from '@/api/modules/space'
import { useAuthStore } from './authStore'

export interface SpaceState {
  spaces: SpaceInfo[]
  currentSpaceId: string | null
  loading: boolean
  initialized: boolean
}

export const useSpaceStore = defineStore('space', () => {
  const spaces = ref<SpaceInfo[]>([])
  const currentSpaceId = ref<string | null>(null)
  const loading = ref(false)
  const initialized = ref(false)

  // ==================== 计算属性 ====================

  const currentSpace = computed<SpaceInfo | null>(() => {
    if (!currentSpaceId.value) return null
    return spaces.value.find((s) => s.spaceId === currentSpaceId.value) || null
  })

  const personalSpace = computed<SpaceInfo | null>(() => {
    return spaces.value.find((s) => s.spaceType === 'personal') || null
  })

  const currentSpaceType = computed(() => currentSpace.value?.spaceType || 'personal')

  const isPersonalSpace = computed(() => currentSpaceType.value === 'personal')
  const isEnterpriseSpace = computed(() => currentSpaceType.value === 'enterprise')
  const isPublicSpace = computed(() => currentSpaceType.value === 'public')
  const isTeamSpace = computed(() => currentSpaceType.value === 'team')

  const spaceTypeLabel = computed(() => {
    const labels: Record<string, string> = {
      personal: '个人空间',
      enterprise: '企业空间',
      public: '公共空间',
      team: '团队空间',
    }
    return labels[currentSpaceType.value] || '空间'
  })

  const spaceTypeIcon = computed(() => {
    const icons: Record<string, string> = {
      personal: 'i-lucide-user',
      enterprise: 'i-lucide-building-2',
      public: 'i-lucide-globe',
      team: 'i-lucide-users',
    }
    return icons[currentSpaceType.value] || 'i-lucide-folder'
  })

  // ==================== 初始化 ====================

  async function initSpaces() {
    const auth = useAuthStore()
    if (!auth.isLoggedIn) return

    loading.value = true
    try {
      const res = await listSpacesApi()
      if (res.code === 200) {
        spaces.value = res.data || []
      }

      // 恢复上次选择的当前空间
      try {
        const currentRes = await getCurrentSpaceApi()
        if (currentRes.code === 200 && currentRes.data) {
          currentSpaceId.value = currentRes.data
        }
      } catch {
        // 静默失败
      }

      // 如果没有当前空间，默认选择个人空间
      if (!currentSpaceId.value && spaces.value.length > 0) {
        const personal = spaces.value.find((s) => s.spaceType === 'personal')
        currentSpaceId.value = personal?.spaceId || spaces.value[0].spaceId
      }

      initialized.value = true
    } catch {
      // 静默失败
    } finally {
      loading.value = false
    }
  }

  // ==================== 空间切换 ====================

  async function switchSpace(spaceId: string) {
    if (spaceId === currentSpaceId.value) return

    currentSpaceId.value = spaceId
    try {
      await setCurrentSpaceApi(spaceId)
    } catch {
      // 静默失败
    }
  }

  function setCurrentSpaceFromUrl(spaceId: string | null) {
    if (spaceId && spaceId !== currentSpaceId.value) {
      currentSpaceId.value = spaceId
    }
  }

  // ==================== 空间刷新 ====================

  async function refreshSpaces() {
    const auth = useAuthStore()
    if (!auth.isLoggedIn) return

    loading.value = true
    try {
      const res = await listSpacesApi()
      if (res.code === 200) {
        spaces.value = res.data || []
      }
    } finally {
      loading.value = false
    }
  }

  // ==================== 重置 ====================

  function reset() {
    spaces.value = []
    currentSpaceId.value = null
    loading.value = false
    initialized.value = false
  }

  return {
    spaces,
    currentSpaceId,
    loading,
    initialized,
    currentSpace,
    personalSpace,
    currentSpaceType,
    isPersonalSpace,
    isEnterpriseSpace,
    isPublicSpace,
    isTeamSpace,
    spaceTypeLabel,
    spaceTypeIcon,
    initSpaces,
    switchSpace,
    setCurrentSpaceFromUrl,
    refreshSpaces,
    reset,
  }
})