/**
 * components/Layout.jsx - 应用主布局组件
 *
 * 结构:
 *  ┌──────────────────────────────────────────────────────────────┐
 *  │  TitleBar (自定义标题栏: Logo + 菜单栏 + 窗口控制按钮)        │
 *  ├──────────┬───────────────────────────────────────────────────┤
 *  │          │  Header (面包屑 + 搜索)                            │
 *  │  Sidebar │───────────────────────────────────────────────────│
 *  │          │                                                   │
 *  │          │  Content (Outlet)                                 │
 *  │          │                                                   │
 *  └──────────┴───────────────────────────────────────────────────┘
 */
import React, { useEffect } from 'react'
import { Outlet, useNavigate, useLocation } from 'react-router-dom'
import { Layout as AntLayout, message } from 'antd'
import TitleBar from './TitleBar'
import Sidebar from './Sidebar'
import Breadcrumb from './Breadcrumb'
import SearchBar from './SearchBar'
import { useUserStore } from '@/store/userStore'
import { useAppStore } from '@/store/appStore'
import { getQuota } from '@/api/quota'
import './Layout.css'

const { Header, Content, Sider } = AntLayout

export default function Layout() {
  const navigate = useNavigate()
  const location = useLocation()
  const { isLoggedIn, fetchProfile } = useUserStore()
  const { sidebarCollapsed, setQuota } = useAppStore()

  useEffect(() => {
    // 认证守卫
    const token = useUserStore.getState().token
    if (!token && !isLoggedIn) {
      navigate('/login', { replace: true })
      return
    }

    // 进入主界面：恢复窗口可调整大小和可最大化
    if (window.electronAPI) {
      window.electronAPI.setResizable(true)
      window.electronAPI.setMaximizable(true)
    }

    // 拉取用户信息 & 配额
    fetchProfile().catch(() => {})
    getQuota()
      .then(res => setQuota(res.data))
      .catch(() => {})
  }, [])

  // ==================== 监听标题栏菜单事件 ====================
  useEffect(() => {
    const handleNavigate = (e) => {
      navigate(e.detail)
    }
    const handleGoBack = () => {
      window.dispatchEvent(new CustomEvent('menu:go-back'))
    }
    const handleUpload = () => {
      const el = document.querySelector('[data-action="upload"]')
      if (el) el.click()
    }
    const handleNewFolder = () => {
      window.dispatchEvent(new CustomEvent('menu:new-folder'))
    }
    const handleCheckUpdate = () => {
      window.dispatchEvent(new CustomEvent('menu:check-update'))
    }

    window.addEventListener('titlebar:navigate', handleNavigate)
    window.addEventListener('titlebar:go-back', handleGoBack)
    window.addEventListener('titlebar:upload', handleUpload)
    window.addEventListener('titlebar:new-folder', handleNewFolder)
    window.addEventListener('titlebar:check-update', handleCheckUpdate)

    return () => {
      window.removeEventListener('titlebar:navigate', handleNavigate)
      window.removeEventListener('titlebar:go-back', handleGoBack)
      window.removeEventListener('titlebar:upload', handleUpload)
      window.removeEventListener('titlebar:new-folder', handleNewFolder)
      window.removeEventListener('titlebar:check-update', handleCheckUpdate)
    }
  }, [navigate])

  return (
    <div className="app-root">
      {/* 自定义标题栏 */}
      <TitleBar title="私有云" />

      <AntLayout className="app-layout">
        <Sider
          width={240}
          collapsedWidth={64}
          collapsed={sidebarCollapsed}
          className="app-sider"
          trigger={null}
        >
          <Sidebar />
        </Sider>

        <AntLayout className="app-main">
          <Header className="app-header">
            <div className="header-left">
              <Breadcrumb />
            </div>
            <div className="header-right">
              <SearchBar />
            </div>
          </Header>

          <Content className="app-content">
            <Outlet />
          </Content>
        </AntLayout>
      </AntLayout>
    </div>
  )
}