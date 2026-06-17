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
  readonly VITE_TURNSTILE_SITE_KEY: string
  readonly VITE_CHUNK_SIZE: string
  readonly VITE_MAX_CONCURRENT_UPLOADS: string
  readonly VITE_MAX_CONCURRENT_DOWNLOADS: string
  readonly VITE_UPLOAD_THRESHOLD: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}