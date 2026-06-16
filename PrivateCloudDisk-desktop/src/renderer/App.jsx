/**
 * App.jsx - 应用根组件
 *
 * 职责:
 * - 初始化应用状态
 * - 恢复会话
 * - 监听主进程菜单事件
 * - 渲染路由
 */
import React, { useEffect } from 'react'
import { RouterProvider } from 'react-router-dom'
import { ConfigProvider, App as AntApp, theme } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import router from './router'
import { useUserStore } from '@/store/userStore'
import { useAppStore } from '@/store/appStore'
import '@/styles/global.css'

export default function App() {
  const restoreSession = useUserStore(s => s.restoreSession)

  useEffect(() => {
    // 应用启动时恢复登录状态
    restoreSession()
  }, [restoreSession])

  useEffect(() => {
    // 监听主进程菜单事件
    const cleanupFns = []

    const uploadCleanup = window.electronAPI?.on?.('menu:upload', () => {
      const el = document.querySelector('[data-action="upload"]')
      if (el) el.click()
    })
    if (uploadCleanup) cleanupFns.push(uploadCleanup)

    const navCleanup = window.electronAPI?.on?.('menu:navigate', (path) => {
      router.navigate(`/${path}`)
    })
    if (navCleanup) cleanupFns.push(navCleanup)

    return () => cleanupFns.forEach(fn => fn())
  }, [])

  return (
    <ConfigProvider
      locale={zhCN}
      theme={{
        token: {
          colorPrimary: '#1a73e8',
          borderRadius: 6,
          fontFamily: `-apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif`
        }
      }}
    >
      <AntApp>
        <RouterProvider router={router} />
      </AntApp>
    </ConfigProvider>
  )
}