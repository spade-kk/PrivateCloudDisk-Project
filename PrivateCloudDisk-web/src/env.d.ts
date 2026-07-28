/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  // eslint-disable-next-line @typescript-eslint/no-empty-object-type
  const component: DefineComponent<{}, {}, any>
  export default component
}

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL: string
  readonly VITE_API_PROXY_TARGET: string
  readonly VITE_IM_WS_URL: string
  readonly VITE_TURNSTILE_SITE_KEY: string
  readonly VITE_CHUNK_SIZE: string
  readonly VITE_MAX_CONCURRENT_UPLOADS: string
  readonly VITE_MAX_CONCURRENT_DOWNLOADS: string
  readonly VITE_UPLOAD_THRESHOLD: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

// Cloudflare Turnstile 全局类型声明
interface Turnstile {
  render(container: string | HTMLElement, options: TurnstileOptions): string
  execute(widgetId: string): void
  reset(widgetId: string): void
  remove(widgetId: string): void
  getResponse(widgetId: string): string | undefined
}

interface TurnstileOptions {
  sitekey: string
  action?: string
  theme?: 'light' | 'dark' | 'auto'
  size?: 'normal' | 'compact' | 'flexible'
  execution?: 'render' | 'execute'
  appearance?: 'always' | 'execute' | 'interaction-only'
  tabindex?: number
  callback?: (token: string) => void
  'error-callback'?: () => void
  'expired-callback'?: () => void
  'timeout-callback'?: () => void
}

interface Window {
  turnstile?: Turnstile
}
