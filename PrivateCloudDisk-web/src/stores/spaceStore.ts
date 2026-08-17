// ============================================================
// spaceStore.ts — 空间系统状态管理
// ============================================================
// 管理多空间架构：当前空间选择、空间列表、切换逻辑。
// 通过 URL 参数 ?space_id=xxx 与路由双向同步；兼容读取历史 ?space 参数。
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
import { useToastStore } from './toastStore'

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
  const switching = ref(false)
  const revision = ref(0)

  // ==================== 计算属性 ====================

  const currentSpace = computed<SpaceInfo | null>(() => {
    if (!currentSpaceId.value) return null
    return spaces.value.find((s) => s.spaceId === currentSpaceId.value) || null
  })

  const personalSpace = computed<SpaceInfo | null>(() => {
    return spaces.value.find((s) => s.spaceType === 'personal') || null
  })

  const currentSpaceType = computed(() => currentSpace.value?.spaceType || 'personal')
  const currentSpaceName = computed(() => currentSpace.value?.spaceName || '我的网盘')

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
      private: '私有空间',
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

  /**
   * [SPACE-COLLAB-URL-01] 初始化空间上下文。
   * 原行为：先读取后端当前空间，可能覆盖 URL 中用户明确选择的空间。
   * 新行为：优先接受已加载且属于用户的 preferredSpaceId，只有 URL 缺失时才读取后端当前空间。
   * 影响范围：控制台、收藏、标签、回收站等所有依赖 X-Space-Id 的页面。
   */
  async function initSpaces(preferredSpaceId?: string | null) {
    const auth = useAuthStore()
    if (!auth.isLoggedIn) return

    loading.value = true
    try {
      const res = await listSpacesApi()
      if (res.code === 200) {
        spaces.value = res.data || []
        /*
         * 空间管理能力全量集成（需求四-1）：
         * 刷新后若当前空间已被删除或退出，自动回退到“我的网盘”，避免请求头继续携带失效空间。
         */
        if (!spaces.value.some((space) => space.spaceId === currentSpaceId.value) || currentSpace.value?.spaceType === 'public') {
          currentSpaceId.value = personalSpace.value?.spaceId || spaces.value[0]?.spaceId || null
          revision.value += 1
        }
      }

      const preferred = preferredSpaceId && spaces.value.find(
        (space) => space.spaceId === preferredSpaceId && space.spaceType !== 'public',
      )
      if (preferred) {
        currentSpaceId.value = preferred.spaceId
      }

      // 恢复上次选择的当前空间
      if (!preferred) {
        try {
          const currentRes = await getCurrentSpaceApi()
          if (
            currentRes.code === 200
            && currentRes.data
              && spaces.value.some((space) => space.spaceId === currentRes.data && space.spaceType !== 'public')
          ) {
            currentSpaceId.value = currentRes.data
          }
        } catch {
          // 静默失败
        }
      }

      // 如果没有当前空间，默认选择个人空间
      if (!currentSpace.value && spaces.value.length > 0) {
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

  async function switchSpace(spaceId: string): Promise<boolean> {
    if (spaceId === currentSpaceId.value) return true
    const target = spaces.value.find((space) => space.spaceId === spaceId)
    if (!target || switching.value) return false
    // 公开空间是独立仓库，不能通过工作空间切换器切入。
    if (target.spaceType === 'public') return false

    const previousSpaceId = currentSpaceId.value
    const toastStore = useToastStore()
    switching.value = true
    try {
      const response = await setCurrentSpaceApi(spaceId)
      if (response.code !== 200) throw new Error('空间切换未成功')
      /*
       * 空间管理能力全量集成（需求四-1/2）：
       * 原行为先修改 currentSpaceId，持久化失败也不回滚，页面可能显示新空间却读取旧数据；
       * 新行为以后端确认作为切换提交点，再递增 revision 通知文件、配额与业务页统一刷新。
       */
      currentSpaceId.value = spaceId
      revision.value += 1
      await syncSpaceUrl(spaceId)
      toastStore.showToast(`已切换到「${target.spaceName || '我的网盘'}」`, 'success', 1800)
      return true
    } catch (error) {
      currentSpaceId.value = previousSpaceId
      toastStore.showToast('空间切换失败，请稍后重试', 'error')
      return false
    } finally {
      switching.value = false
    }
  }

  /**
   * [SPACE-COLLAB-URL-02] 从 URL 应用空间并校验成员范围。
   * 原行为：任意字符串都写入 currentSpaceId，随后请求头可能携带无权限空间。
   * 新行为：仅接受已加载的非公开空间；无效值自动回退个人空间。
   * 影响范围：防止刷新、复制无效 URL 或空间退出后产生上下文漂移。
   */
  function setCurrentSpaceFromUrl(spaceId: string | null): string | null {
    const requested = spaceId?.trim() || null
    const valid = requested && spaces.value.some(
      (space) => space.spaceId === requested && space.spaceType !== 'public',
    )
    const resolved = valid ? requested : (personalSpace.value?.spaceId || spaces.value[0]?.spaceId || null)
    if (resolved !== currentSpaceId.value) {
      currentSpaceId.value = resolved
      revision.value += 1
    }
    return resolved
  }

  /** [SPACE-COLLAB-URL-03] 统一写入 canonical space_id，删除历史 space 参数。 */
  async function syncSpaceUrl(spaceId: string | null) {
    try {
      const { default: router } = await import('@/router')
      const route = router.currentRoute.value
      if (route.meta.publicRepository || route.meta.previewWorkspace) return
      const query = { ...route.query } as Record<string, string | string[] | undefined>
      delete query.space
      if (spaceId) query.space_id = spaceId
      else delete query.space_id
      await router.replace({ query })
    } catch {
      // 路由初始化阶段可能尚未完成，不能阻断空间切换本身。
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
    switching.value = false
    revision.value = 0
  }

  return {
    spaces,
    currentSpaceId,
    loading,
    initialized,
    switching,
    revision,
    currentSpace,
    personalSpace,
    currentSpaceType,
    currentSpaceName,
    isPersonalSpace,
    isEnterpriseSpace,
    isPublicSpace,
    isTeamSpace,
    spaceTypeLabel,
    spaceTypeIcon,
    initSpaces,
    switchSpace,
    setCurrentSpaceFromUrl,
    syncSpaceUrl,
    refreshSpaces,
    reset,
  }
})
