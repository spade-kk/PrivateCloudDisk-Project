import { fileURLToPath, URL } from 'node:url'

import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig(({ command, mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const apiProxyTarget = env.VITE_API_PROXY_TARGET || 'http://127.0.0.1:8080'
  const isDevelopment = command === 'serve'
  return {
    plugins: [
      vue(),
      isDevelopment && vueDevTools(),
    ].filter(Boolean),
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url))
      },
    },
    server: {
      host: '0.0.0.0', // 或设置为 true，效果相同
      port: 5500,
      proxy: {
          '/api': {
            target: apiProxyTarget,
            changeOrigin: true,
          },
        },
    },
  }
})
