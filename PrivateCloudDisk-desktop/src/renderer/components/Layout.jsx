/**
 * components/Layout.jsx - 应用主布局组件
 *
 * 结构:
 *  ┌──────────────────────────────────────┐
 *  │  Titlebar (macOS 拖拽区域)            │
 *  ├────────┬─────────────────────────────┤
 *  │        │  Header (面包屑 + 搜索)      │
 *  │ Sidebar│─────────────────────────────│
 *  │        │                             │
 *  │        │  Content (Outlet)           │
 *  │        │                             │
 *  └────────┴─────────────────────────────┘
 */
import React, { useEffect } from 'react'
import { Outlet, useNavigate, useLocation } from 'react-router-dom'
import { Layout as AntLayout, message } from 'antd'
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

    // 拉取用户信息 & 配额
    fetchProfile().catch(() => {})
    getQuota()
      .then(res => setQuota(res.data))
      .catch(() => {})
  }, [])

  return (
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
  )
}