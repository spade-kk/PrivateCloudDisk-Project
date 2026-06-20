/**
 * hooks/useAuth.js - 认证 Hook
 */
import { useEffect } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import { useUserStore } from '@/store/userStore'

/**
 * 认证守卫 Hook
 * 未登录时自动跳转到登录页
 */
export function useAuthGuard() {
  const navigate = useNavigate()
  const location = useLocation()
  const { isLoggedIn, restoreSession } = useUserStore()

  useEffect(() => {
    const hasSession = restoreSession()
    if (!hasSession && !isLoggedIn) {
      // 未登录且无有效会话, 跳转到登录页
      if (location.pathname !== '/login' && location.pathname !== '/register') {
        navigate('/login', { replace: true })
      }
    }
  }, [isLoggedIn, navigate, location.pathname, restoreSession])

  return { isLoggedIn }
}