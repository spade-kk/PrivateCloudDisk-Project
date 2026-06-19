/**
 * main.js - 应用入口
 * 注册 Pinia 状态管理 & uView Plus UI 组件库
 */
import App from './App.vue'
import { createSSRApp } from 'vue'
import { createPinia } from 'pinia'
import uviewPlus from 'uview-plus'

export function createApp() {
  const app = createSSRApp(App)
  const pinia = createPinia()

  app.use(pinia)
  app.use(uviewPlus)

  return { app }
}