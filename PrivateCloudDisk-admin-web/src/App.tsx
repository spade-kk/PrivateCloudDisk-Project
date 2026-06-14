// ============================================================
// App 主入口 - 路由配置
// ============================================================
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { ConfigProvider, App as AntApp } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import { useEffect, useState } from 'react'
import { useAuthStore } from '@/stores/authStore'
import AdminLayout from '@/layouts/AdminLayout'
import LoginPage from '@/pages/LoginPage'
import RegisterPage from '@/pages/RegisterPage'
import DashboardPage from '@/pages/DashboardPage'
import UsersPage from '@/pages/UsersPage'
import OnlineUsersPage from '@/pages/OnlineUsersPage'
import FilesPage from '@/pages/FilesPage'
import QuarantinedFilesPage from '@/pages/QuarantinedFilesPage'
import StorageStatsPage from '@/pages/StorageStatsPage'
import SecurityEventsPage from '@/pages/SecurityEventsPage'
import IPBlacklistPage from '@/pages/IPBlacklistPage'
import AuditLogsPage from '@/pages/AuditLogsPage'
import SystemConfigPage from '@/pages/SystemConfigPage'
import SystemResourcesPage from '@/pages/SystemResourcesPage'
import ApiDocsPage from '@/pages/ApiDocsPage'
import NotFoundPage from '@/pages/NotFoundPage'

/** 路由守卫组件 - 未登录跳转登录页 */
function AuthGuard({ children }: { children: React.ReactNode }) {
  const { isLoggedIn, initialize, loading } = useAuthStore()
  const [ready, setReady] = useState(false)

  useEffect(() => {
    initialize().finally(() => setReady(true))
  }, [initialize])

  if (!ready || loading) {
    return (
      <div style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: '#f5f5f5',
      }}>
        <span>加载中...</span>
      </div>
    )
  }

  if (!isLoggedIn) {
    return <Navigate to="/login" replace />
  }

  return <>{children}</>
}

function AppRoutes() {
  return (
    <Routes>
      {/* 登录页面 - 公开 */}
      <Route path="/login" element={<LoginPage />} />

      {/* 注册页面 - 公开 */}
      <Route path="/register" element={<RegisterPage />} />

      {/* 管理后台 - 需认证 */}
      <Route
        path="/"
        element={
          <AuthGuard>
            <AdminLayout />
          </AuthGuard>
        }
      >
        {/* 默认跳转仪表盘 */}
        <Route index element={<Navigate to="/dashboard" replace />} />

        {/* 仪表盘 */}
        <Route path="dashboard" element={<DashboardPage />} />

        {/* 用户管理 */}
        <Route path="users" element={<UsersPage />} />
        <Route path="users/online" element={<OnlineUsersPage />} />

        {/* 文件管理 */}
        <Route path="files" element={<FilesPage />} />
        <Route path="files/quarantined" element={<QuarantinedFilesPage />} />
        <Route path="files/storage" element={<StorageStatsPage />} />

        {/* 安全中心 */}
        <Route path="security/events" element={<SecurityEventsPage />} />
        <Route path="security/ip-blacklist" element={<IPBlacklistPage />} />

        {/* 审计日志 */}
        <Route path="audit" element={<AuditLogsPage />} />

        {/* 系统设置 */}
        <Route path="system/config" element={<SystemConfigPage />} />
        <Route path="system/resources" element={<SystemResourcesPage />} />
        <Route path="system/api-docs" element={<ApiDocsPage />} />

        {/* 404 */}
        <Route path="*" element={<NotFoundPage />} />
      </Route>

      {/* 全局 404 */}
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}

export default function App() {
  return (
    <ConfigProvider
      locale={zhCN}
      theme={{
        token: {
          colorPrimary: '#1677ff',
          borderRadius: 6,
        },
      }}
    >
      <AntApp>
        <BrowserRouter>
          <AppRoutes />
        </BrowserRouter>
      </AntApp>
    </ConfigProvider>
  )
}