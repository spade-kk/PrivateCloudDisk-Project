// ============================================================
// Cloudflare Turnstile 验证码组件
// 参考 Vue 前端的实现，封装为 React 组件
// ============================================================
import { useEffect, useRef, useCallback, useState } from 'react'

const TURNSTILE_SCRIPT_ID = 'cloudflare-turnstile-script'
const TURNSTILE_SCRIPT_SRC =
  'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit'

// 扩展 window 类型
declare global {
  interface Window {
    turnstile?: {
      render: (
        container: HTMLElement | string,
        options: TurnstileOptions
      ) => string | null
      reset: (widgetId: string) => void
      remove: (widgetId: string) => void
    }
  }
}

interface TurnstileOptions {
  sitekey: string
  action?: string
  theme?: 'light' | 'dark' | 'auto'
  size?: 'normal' | 'compact'
  callback?: (token: string) => void
  'expired-callback'?: () => void
  'error-callback'?: () => void
}

export interface TurnstileWidgetProps {
  /** Turnstile Site Key */
  siteKey: string
  /** 动作标识，用于区分不同场景 */
  action?: string
  /** 主题 */
  theme?: 'light' | 'dark' | 'auto'
  /** 尺寸 */
  size?: 'normal' | 'compact'
  /** 验证通过回调 */
  onVerify?: (token: string) => void
  /** 验证过期回调 */
  onExpired?: () => void
  /** 验证出错回调 */
  onError?: (error: string) => void
}

/**
 * 加载 Turnstile 脚本（全局单例）
 */
function loadTurnstileScript(): Promise<void> {
  if (window.turnstile) return Promise.resolve()

  const existingScript = document.getElementById(TURNSTILE_SCRIPT_ID)
  if (existingScript) {
    return new Promise((resolve, reject) => {
      existingScript.addEventListener('load', () => resolve(), { once: true })
      existingScript.addEventListener(
        'error',
        () => reject(new Error('Turnstile script load failed')),
        { once: true }
      )
    })
  }

  return new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.id = TURNSTILE_SCRIPT_ID
    script.src = TURNSTILE_SCRIPT_SRC
    script.async = true
    script.defer = true
    script.addEventListener('load', () => resolve(), { once: true })
    script.addEventListener(
      'error',
      () => reject(new Error('Turnstile script load failed')),
      { once: true }
    )
    document.head.appendChild(script)
  })
}

export default function TurnstileWidget({
  siteKey,
  action = 'login',
  theme = 'light',
  size = 'normal',
  onVerify,
  onExpired,
  onError,
}: TurnstileWidgetProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const widgetIdRef = useRef<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const renderWidget = useCallback(async () => {
    if (!siteKey || !containerRef.current) return

    setLoading(true)
    setError('')

    try {
      await loadTurnstileScript()

      if (!window.turnstile || !containerRef.current) {
        throw new Error('Turnstile unavailable')
      }

      // 移除旧 widget
      if (widgetIdRef.current !== null) {
        window.turnstile.remove(widgetIdRef.current)
      }

      widgetIdRef.current = window.turnstile.render(containerRef.current, {
        sitekey: siteKey,
        action,
        theme,
        size,
        callback: (token: string) => {
          onVerify?.(token)
        },
        'expired-callback': () => {
          onExpired?.()
        },
        'error-callback': () => {
          const msg = '验证组件加载失败，请刷新后重试'
          setError(msg)
          onError?.(msg)
        },
      })
    } catch(e) {
      const msg = '验证组件加载失败，请刷新后重试'
      console.log(e)
      setError(msg)
      onError?.(msg)
    } finally {
      setLoading(false)
    }
  }, [siteKey, action, theme, size, onVerify, onExpired, onError])

  // 挂载时渲染 widget
  useEffect(() => {
    renderWidget()
  }, [renderWidget])

  // 卸载时清理
  useEffect(() => {
    return () => {
      if (window.turnstile && widgetIdRef.current !== null) {
        window.turnstile.remove(widgetIdRef.current)
        widgetIdRef.current = null
      }
    }
  }, [])

  /** 重置验证码 */
  const reset = useCallback(() => {
    if (window.turnstile && widgetIdRef.current !== null) {
      window.turnstile.reset(widgetIdRef.current)
    }
    setError('')
  }, [])

  // 暴露 reset 方法给父组件
  if (typeof window !== 'undefined') {
    ;(containerRef.current as unknown as Record<string, unknown>) || {}
  }

  // 使用 ref 回调方式暴露 reset
  useEffect(() => {
    if (containerRef.current) {
      ;(containerRef.current as HTMLDivElement & { _turnstileReset?: () => void })._turnstileReset = reset
    }
  }, [reset])

  if (!siteKey) {
    return (
      <div style={{ textAlign: 'center', padding: '16px 0', color: '#ff4d4f', fontSize: 13 }}>
        未配置 Turnstile Site Key
      </div>
    )
  }

  if (loading) {
    return (
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: 65, gap: 8, color: '#999', fontSize: 13 }}>
        <span>正在加载验证组件...</span>
      </div>
    )
  }

  if (error) {
    return (
      <div style={{ textAlign: 'center', padding: '16px 0', color: '#ff4d4f', fontSize: 13 }}>
        {error}
      </div>
    )
  }

  return (
    <div
      ref={containerRef}
      style={{
        minHeight: 65,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
      }}
    />
  )
}