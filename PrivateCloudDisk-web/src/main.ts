import 'core-js/actual'
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { getFingerprint } from './utils/fingerprint'
import { vSafeHtml } from './utils/sanitize'
import './style.css'

// 应用启动时预初始化浏览器指纹
// 确保首次请求前 visitorId 已就绪
getFingerprint().catch(() => {
  // 指纹采集失败不影响应用启动
})

const app = createApp(App)

// 注册全局指令：v-safe-html（XSS 安全渲染，替代 v-html）
app.directive('safe-html', vSafeHtml)

app.use(createPinia())
app.use(router)
app.mount('#app')