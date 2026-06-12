import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getMyUserInfoApi } from '@/api/modules/users'

const FALLBACK_PROFILE = {
  name: '用户',
  email: '',
  phone_number: '',
  account: '',
  image_path: '',
  file_count: 0,
}

function normalizeProfile(data = {}) {
  return {
    name: data.name || FALLBACK_PROFILE.name,
    email: data.email || '',
    phone_number: data.phone_number || '',
    account: data.account || '',
    image_path: data.image_path || '',
    file_count: data.file_count || 0,
  }
}

export const useUserStore = defineStore('user', () => {
  const profile = ref({ ...FALLBACK_PROFILE })
  const loading = ref(false)
  const loaded = ref(false)
  const error = ref(null)

  const displayName = computed(() => profile.value.name || profile.value.account || FALLBACK_PROFILE.name)
  const subtitle = computed(() => profile.value.email || profile.value.phone_number || profile.value.account || 'CloudDrive 用户')
  const initials = computed(() => {
    const source = displayName.value.trim()
    return source ? source.slice(0, 1).toUpperCase() : 'U'
  })

  async function fetchProfile(options = {}) {
    if (loading.value) return profile.value
    if (loaded.value && !options.force) return profile.value

    loading.value = true
    error.value = null
    try {
      const res = await getMyUserInfoApi()
      if (res.code === 200 && res.data) {
        profile.value = normalizeProfile(res.data)
        loaded.value = true
        return profile.value
      }
      error.value = res.message || '用户信息加载失败'
      loaded.value = true
      return profile.value
    } catch (err) {
      error.value = err.message || '用户信息加载失败'
      loaded.value = true
      return profile.value
    } finally {
      loading.value = false
    }
  }

  function mergeProfile(nextProfile = {}) {
    profile.value = normalizeProfile({ ...profile.value, ...nextProfile })
    loaded.value = true
  }

  function clearProfile() {
    profile.value = { ...FALLBACK_PROFILE }
    loaded.value = false
    error.value = null
  }

  return {
    profile,
    loading,
    loaded,
    error,
    displayName,
    subtitle,
    initials,
    fetchProfile,
    mergeProfile,
    clearProfile,
  }
})
