import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { advancedFileSearchApi } from '@/api/index'
import { useToastStore } from './toastStore'

const DEFAULT_FILTERS = {
  file_category: '',
  tags: '',
}

export const useSearchStore = defineStore('search', () => {
  const toastStore = useToastStore()
  const keyword = ref('')
  const filters = ref({ ...DEFAULT_FILTERS })
  const sortField = ref('_score')
  const asc = ref(false)
  const page = ref(1)
  const size = ref(12)
  const loading = ref(false)
  const error = ref(null)
  const total = ref(0)
  const hits = ref([])
  const aggregations = ref({})
  const recentKeywords = ref(JSON.parse(localStorage.getItem('cloudDriveSearchRecent') || '[]'))

  const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size.value)))
  const hasQuery = computed(() => keyword.value.trim() || Object.values(filters.value).some(Boolean))

  function persistRecent(term) {
    const normalized = term.trim()
    if (!normalized) return
    recentKeywords.value = [normalized, ...recentKeywords.value.filter(item => item !== normalized)].slice(0, 8)
    localStorage.setItem('cloudDriveSearchRecent', JSON.stringify(recentKeywords.value))
  }

  function setFilters(nextFilters = {}) {
    filters.value = { ...filters.value, ...nextFilters }
  }

  function resetFilters() {
    filters.value = { ...DEFAULT_FILTERS }
    sortField.value = '_score'
    asc.value = false
    page.value = 1
  }

  function buildRequest(overrides = {}) {
    const selectedSort = overrides.sortField ?? sortField.value
    const request = {
      keyword: overrides.keyword ?? keyword.value.trim(),
      page: overrides.page ?? page.value,
      size: overrides.size ?? size.value,
      status: overrides.status ?? 'active',
      filters: overrides.filters ?? Object.fromEntries(
        Object.entries(filters.value).filter(([, value]) => value)
      ),
      highlightFields: ['filename', 'content_text', 'ocr_text', 'tags', 'summary'],
    }

    if (selectedSort && selectedSort !== '_score') {
      request.sortField = selectedSort
      request.asc = overrides.asc ?? asc.value
    }

    return request
  }

  async function search(overrides = {}) {
    loading.value = true
    error.value = null
    if (overrides.keyword !== undefined) keyword.value = overrides.keyword
    if (overrides.page !== undefined) page.value = overrides.page
    if (overrides.size !== undefined) size.value = overrides.size
    if (overrides.sortField !== undefined) sortField.value = overrides.sortField
    if (overrides.asc !== undefined) asc.value = overrides.asc
    if (overrides.filters !== undefined) filters.value = { ...DEFAULT_FILTERS, ...overrides.filters }

    try {
      const res = await advancedFileSearchApi(buildRequest())
      if (res.code === 200) {
        total.value = res.data?.total || 0
        hits.value = res.data?.hits || []
        aggregations.value = res.data?.aggregations || {}
        persistRecent(keyword.value)
        return { success: true }
      }
      error.value = res.message || '搜索失败'
      toastStore.showToast(error.value, 'error')
      return { success: false, message: error.value }
    } catch (err) {
      error.value = err.message || '搜索失败'
      toastStore.showToast(error.value, 'error')
      return { success: false, message: error.value }
    } finally {
      loading.value = false
    }
  }

  async function goToPage(nextPage) {
    page.value = Math.min(Math.max(1, nextPage), totalPages.value)
    return search()
  }

  return {
    keyword,
    filters,
    sortField,
    asc,
    page,
    size,
    loading,
    error,
    total,
    hits,
    aggregations,
    recentKeywords,
    totalPages,
    hasQuery,
    setFilters,
    resetFilters,
    search,
    goToPage,
  }
})
