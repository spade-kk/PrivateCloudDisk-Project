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
import UserDetailPage from '@/pages/UserDetailPage'
import AvatarAuditPage from '@/pages/AvatarAuditPage'
import OrdersPage from '@/pages/OrdersPage'
import FileMetadataPage from '@/pages/FileMetadataPage'

// 平台运维后台 - 平台运维
import NodeMonitorPage from '@/pages/ops/NodeMonitorPage'
import DockerManagePage from '@/pages/ops/DockerManagePage'
import StorageManagePage from '@/pages/ops/StorageManagePage'
import ClusterManagePage from '@/pages/ops/ClusterManagePage'
import BackupManagePage from '@/pages/ops/BackupManagePage'

// 平台运维后台 - 平台管理
import PlatformManagePage from '@/pages/ops/PlatformManagePage'

// 第三方中间件后台 - 中间件
import NacosManagePage from '@/pages/middleware/NacosManagePage'
import RabbitMQManagePage from '@/pages/middleware/RabbitMQManagePage'
import XXLJobManagePage from '@/pages/middleware/XXLJobManagePage'
import MinIOManagePage from '@/pages/middleware/MinIOManagePage'
import OpenSearchManagePage from '@/pages/middleware/OpenSearchManagePage'

// 第三方中间件后台 - 监控
import GrafanaIntegrationPage from '@/pages/monitor/GrafanaIntegrationPage'
import SkyWalkingIntegrationPage from '@/pages/monitor/SkyWalkingIntegrationPage'
import PrometheusIntegrationPage from '@/pages/monitor/PrometheusIntegrationPage'

// 第三方中间件后台 - 日志
import LokiIntegrationPage from '@/pages/logs/LokiIntegrationPage'
import KibanaIntegrationPage from '@/pages/logs/KibanaIntegrationPage'

// 第三方中间件后台 - 开发
import SwaggerDocPage from '@/pages/dev/SwaggerDocPage'
import ApiManagePage from '@/pages/dev/ApiManagePage'
import OpenAPIPage from '@/pages/dev/OpenAPIPage'

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
        <Route path="users/:userId" element={<UserDetailPage />} />
        <Route path="users/online" element={<OnlineUsersPage />} />

        {/* 文件管理 */}
        <Route path="files" element={<FilesPage />} />
        <Route path="files/metadata" element={<FileMetadataPage />} />
        <Route path="files/quarantined" element={<QuarantinedFilesPage />} />
        <Route path="files/storage" element={<StorageStatsPage />} />

        {/* 安全中心 */}
        <Route path="security/events" element={<SecurityEventsPage />} />
        <Route path="security/ip-blacklist" element={<IPBlacklistPage />} />
        <Route path="security/avatar-audit" element={<AvatarAuditPage />} />

        {/* 订单管理 */}
        <Route path="orders" element={<OrdersPage />} />

        {/* 审计日志 */}
        <Route path="audit" element={<AuditLogsPage />} />

        {/* 系统设置 */}
        <Route path="system/config" element={<SystemConfigPage />} />
        <Route path="system/resources" element={<SystemResourcesPage />} />
        <Route path="system/api-docs" element={<ApiDocsPage />} />

        {/* ===== 平台运维后台 - 平台运维 ===== */}
        <Route path="ops/nodes" element={<NodeMonitorPage />} />
        <Route path="ops/docker" element={<DockerManagePage />} />
        <Route path="ops/storage" element={<StorageManagePage />} />
        <Route path="ops/cluster" element={<ClusterManagePage />} />
        <Route path="ops/backup" element={<BackupManagePage />} />

        {/* ===== 平台运维后台 - 平台管理 ===== */}
        <Route path="ops/platform" element={<PlatformManagePage />} />

        {/* ===== 第三方中间件后台 - 中间件 ===== */}
        <Route path="middleware/nacos" element={<NacosManagePage />} />
        <Route path="middleware/rabbitmq" element={<RabbitMQManagePage />} />
        <Route path="middleware/xxl-job" element={<XXLJobManagePage />} />
        <Route path="middleware/minio" element={<MinIOManagePage />} />
        <Route path="middleware/opensearch" element={<OpenSearchManagePage />} />

        {/* ===== 第三方中间件后台 - 监控 ===== */}
        <Route path="monitor/grafana" element={<GrafanaIntegrationPage />} />
        <Route path="monitor/skywalking" element={<SkyWalkingIntegrationPage />} />
        <Route path="monitor/prometheus" element={<PrometheusIntegrationPage />} />

        {/* ===== 第三方中间件后台 - 日志 ===== */}
        <Route path="logs/loki" element={<LokiIntegrationPage />} />
        <Route path="logs/kibana" element={<KibanaIntegrationPage />} />

        {/* ===== 第三方中间件后台 - 开发 ===== */}
        <Route path="dev/swagger" element={<SwaggerDocPage />} />
        <Route path="dev/api-manage" element={<ApiManagePage />} />
        <Route path="dev/openapi" element={<OpenAPIPage />} />

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