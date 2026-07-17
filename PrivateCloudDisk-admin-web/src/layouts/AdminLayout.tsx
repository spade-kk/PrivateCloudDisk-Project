// ============================================================
// 管理后台主布局
// ============================================================
import { useState, useMemo , useCallback } from 'react'
import { Outlet, useNavigate, useLocation } from 'react-router-dom'
import { Layout, Menu, Button, Avatar, Dropdown, Badge, theme, Typography, Space, Breadcrumb } from 'antd'
import {
  DashboardOutlined,
  UserOutlined,
  FileOutlined,
  AuditOutlined,
  SafetyOutlined,
  SettingOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  LogoutOutlined,
  BellOutlined,
  SearchOutlined,
  CloudServerOutlined,
  EyeOutlined,
  ThunderboltOutlined,
  ToolOutlined,
  DatabaseOutlined,
  ApiOutlined,
  ExclamationCircleOutlined,
  GlobalOutlined,
  PictureOutlined,
  // 平台运维图标
  MonitorOutlined,
  ContainerOutlined,
  HddOutlined,
  ClusterOutlined,
  SaveOutlined,
  ControlOutlined,
  // 中间件图标
  DeploymentUnitOutlined,
  MessageOutlined,
  ScheduleOutlined,
  InboxOutlined,
  // 监控图标
  FundOutlined,
  RadarChartOutlined,
  LineChartOutlined,
  // 日志图标
  FileSearchOutlined,
  BarChartOutlined,
  // 开发图标
  BookOutlined,
  CodeOutlined,
  LinkOutlined,
  // 业务后台图标
  AppstoreOutlined,
  ShopOutlined,
} from '@ant-design/icons'
import { useAuthStore } from '@/stores/authStore'

const { Header, Sider, Content } = Layout
const { Text } = Typography
const isDark = false // 暂时不支持暗黑模式，后续可根据主题切换
// 菜单配置 - 三级菜单结构
// Ant Design Menu 支持嵌套 children 实现多级菜单
interface MenuItem {
  key: string
  icon?: React.ReactNode
  label: string
  path?: string
  badge?: number
  children?: MenuItem[]
  // 标记为分组标题（不可点击导航）
  group?: boolean
}

const menuItems: MenuItem[] = [
  {
    key: '/dashboard',
    icon: <DashboardOutlined />,
    label: '仪表盘',
    path: '/dashboard',
  },
  // ===== 企业后台 Portal =====
  {
    key: 'portal-business',
    icon: <AppstoreOutlined />,
    label: '业务后台',
    children: [
      {
        key: 'users-group',
        icon: <UserOutlined />,
        label: '用户管理',
        path: '/users',
        children: [
          { key: '/users', icon: <UserOutlined />, label: '用户列表', path: '/users' },
          { key: '/users/online', icon: <EyeOutlined />, label: '在线用户', path: '/users/online' },
        ],
      },
      {
        key: 'files-group',
        icon: <FileOutlined />,
        label: '文件管理',
        path: '/files',
        children: [
          { key: '/files', icon: <FileOutlined />, label: '所有文件', path: '/files' },
          { key: '/files/metadata', icon: <DatabaseOutlined />, label: '文件元数据', path: '/files/metadata' },
          { key: '/files/quarantined', icon: <ExclamationCircleOutlined />, label: '隔离文件', path: '/files/quarantined' },
          { key: '/files/storage', icon: <DatabaseOutlined />, label: '存储统计', path: '/files/storage' },
        ],
      },
      {
        key: '/orders',
        icon: <ShopOutlined />,
        label: '订单管理',
        path: '/orders',
      },
      {
        key: 'security-group',
        icon: <SafetyOutlined />,
        label: '安全中心',
        path: '/security',
        children: [
          { key: '/security/events', icon: <ThunderboltOutlined />, label: '安全事件', path: '/security/events' },
          { key: '/security/avatar-audit', icon: <PictureOutlined />, label: '头像审核', path: '/security/avatar-audit' },
          { key: '/security/ip-blacklist', icon: <GlobalOutlined />, label: 'IP 黑名单', path: '/security/ip-blacklist' },
        ],
      },
      {
        key: '/audit',
        icon: <AuditOutlined />,
        label: '审计日志',
        path: '/audit',
      },
      {
        key: 'system-group',
        icon: <SettingOutlined />,
        label: '系统设置',
        path: '/system',
        children: [
          { key: '/system/config', icon: <ToolOutlined />, label: '系统配置', path: '/system/config' },
          { key: '/system/resources', icon: <CloudServerOutlined />, label: '系统资源', path: '/system/resources' },
          { key: '/system/api-docs', icon: <ApiOutlined />, label: 'API 文档', path: '/system/api-docs' },
        ],
      },
    ],
  },
  // ===== 平台运维后台 =====
  {
    key: 'portal-ops',
    icon: <ControlOutlined />,
    label: '平台运维后台',
    children: [
      {
        key: 'ops-monitor-group',
        icon: <MonitorOutlined />,
        label: '平台运维',
        children: [
          { key: '/ops/nodes', icon: <MonitorOutlined />, label: 'Node 监控', path: '/ops/nodes' },
          { key: '/ops/docker', icon: <ContainerOutlined />, label: 'Docker 管理', path: '/ops/docker' },
          { key: '/ops/storage', icon: <HddOutlined />, label: 'Storage 管理', path: '/ops/storage' },
          { key: '/ops/cluster', icon: <ClusterOutlined />, label: 'Cluster 管理', path: '/ops/cluster' },
          { key: '/ops/backup', icon: <SaveOutlined />, label: 'Backup 管理', path: '/ops/backup' },
        ],
      },
      {
        key: 'ops-platform-group',
        icon: <ToolOutlined />,
        label: '平台管理',
        path: '/ops',
        children: [
          { key: '/ops/platform', icon: <SettingOutlined />, label: '系统配置', path: '/ops/platform' },
        ],
      },
    ],
  },
  // ===== 第三方中间件后台 =====
  {
    key: 'portal-middleware',
    icon: <DeploymentUnitOutlined />,
    label: '第三方中间件后台',
    children: [
      {
        key: 'middleware-group',
        icon: <DeploymentUnitOutlined />,
        label: '中间件',
        children: [
          { key: '/middleware/nacos', icon: <CloudServerOutlined />, label: 'Nacos 管理', path: '/middleware/nacos' },
          { key: '/middleware/rabbitmq', icon: <MessageOutlined />, label: 'RabbitMQ 管理', path: '/middleware/rabbitmq' },
          { key: '/middleware/xxl-job', icon: <ScheduleOutlined />, label: 'XXL-Job 管理', path: '/middleware/xxl-job' },
          { key: '/middleware/minio', icon: <InboxOutlined />, label: 'MinIO 管理', path: '/middleware/minio' },
          { key: '/middleware/opensearch', icon: <SearchOutlined />, label: 'OpenSearch 管理', path: '/middleware/opensearch' },
        ],
      },
      {
        key: 'monitor-group',
        icon: <FundOutlined />,
        label: '监控',
        children: [
          { key: '/monitor/grafana', icon: <FundOutlined />, label: 'Grafana 集成', path: '/monitor/grafana' },
          { key: '/monitor/skywalking', icon: <RadarChartOutlined />, label: 'SkyWalking 集成', path: '/monitor/skywalking' },
          { key: '/monitor/prometheus', icon: <LineChartOutlined />, label: 'Prometheus 集成', path: '/monitor/prometheus' },
        ],
      },
      {
        key: 'logs-group',
        icon: <FileSearchOutlined />,
        label: '日志',
        children: [
          { key: '/logs/loki', icon: <FileSearchOutlined />, label: 'Loki 集成', path: '/logs/loki' },
          { key: '/logs/kibana', icon: <BarChartOutlined />, label: 'Kibana 集成', path: '/logs/kibana' },
        ],
      },
      {
        key: 'dev-group',
        icon: <CodeOutlined />,
        label: '开发',
        children: [
          { key: '/dev/swagger', icon: <BookOutlined />, label: 'Swagger 文档', path: '/dev/swagger' },
          { key: '/dev/api-manage', icon: <ApiOutlined />, label: 'API 管理', path: '/dev/api-manage' },
          { key: '/dev/openapi', icon: <LinkOutlined />, label: 'OpenAPI 对接', path: '/dev/openapi' },
        ],
      },
    ],
  },
]

// 面包屑映射
const breadcrumbMap: Record<string, string> = {
  '/dashboard': '仪表盘',
  // 业务后台
  '/users': '用户列表',
  '/users/online': '在线用户',
  '/files': '所有文件',
  '/files/metadata': '文件元数据',
  '/files/quarantined': '隔离文件',
  '/files/storage': '存储统计',
  '/orders': '订单管理',
  '/security': '安全中心',
  '/security/events': '安全事件',
  '/security/avatar-audit': '头像审核',
  '/security/ip-blacklist': 'IP 黑名单',
  '/audit': '审计日志',
  '/system': '系统设置',
  '/system/config': '系统配置',
  '/system/resources': '系统资源',
  '/system/api-docs': 'API 文档',
  // 平台运维后台
  '/ops': '平台运维',
  '/ops/nodes': 'Node 监控',
  '/ops/docker': 'Docker 管理',
  '/ops/storage': 'Storage 管理',
  '/ops/cluster': 'Cluster 管理',
  '/ops/backup': 'Backup 管理',
  '/ops/platform': '平台管理',
  // 第三方中间件后台
  '/middleware/nacos': 'Nacos 管理',
  '/middleware/rabbitmq': 'RabbitMQ 管理',
  '/middleware/xxl-job': 'XXL-Job 管理',
  '/middleware/minio': 'MinIO 管理',
  '/middleware/opensearch': 'OpenSearch 管理',
  '/monitor/grafana': 'Grafana 集成',
  '/monitor/skywalking': 'SkyWalking 集成',
  '/monitor/prometheus': 'Prometheus 集成',
  '/logs/loki': 'Loki 集成',
  '/logs/kibana': 'Kibana 集成',
  '/dev/swagger': 'Swagger 文档',
  '/dev/api-manage': 'API 管理',
  '/dev/openapi': 'OpenAPI 对接',
}

export default function AdminLayout() {
  const [collapsed, setCollapsed] = useState(false)
  const navigate = useNavigate()
  const location = useLocation()
  const { admin, logout } = useAuthStore()
  const { token: themeToken } = theme.useToken()

  // 递归收集所有拥有 path 属性的菜单叶子节点 key
  const allLeafKeys = useMemo(() => {
    const keys: string[] = []
    const walk = (items: MenuItem[]) => {
      for (const item of items) {
        if (item.path && !item.children) {
          keys.push(item.key)
        }
        if (item.children) walk(item.children)
      }
    }
    walk(menuItems)
    return keys
  }, [])

  // 当前选中的菜单 key
  const selectedKeys = useMemo(() => {
    const path = location.pathname
    if (breadcrumbMap[path]) return [path]
    return ['/dashboard']
  }, [location.pathname])

  // 递归查找当前路径的所有祖先 key（用于展开三级菜单）
  const findOpenKeys = (items: MenuItem[], targetPath: string, ancestors: string[] = []): string[] | null => {
    for (const item of items) {
      const currentAncestors = [...ancestors, item.key]
      if (item.key === targetPath || item.path === targetPath) {
        return ancestors
      }
      if (item.children) {
        const result = findOpenKeys(item.children, targetPath, currentAncestors)
        if (result !== null) return result
      }
    }
    return null
  }

  // 当前展开的子菜单（所有祖先层级）
  const openKeys = useMemo(() => {
    const path = location.pathname
    return findOpenKeys(menuItems, path) || []
  }, [location.pathname])

  // 面包屑
  const breadcrumbs = useMemo(() => {
    const path = location.pathname
    const crumbs: { title: string; path?: string }[] = [{ title: '首页', path: '/dashboard' }]

    if (breadcrumbMap[path]) {
      // 递归查找面包屑路径
      const collectCrumbs = (items: MenuItem[], targetPath: string, ancestors: { title: string; path?: string }[]): { title: string; path?: string }[] | null => {
        for (const item of items) {
          const current = [...ancestors, { title: item.label, path: item.path }]
          if (item.key === targetPath || item.path === targetPath) {
            return current
          }
          if (item.children) {
            const result = collectCrumbs(item.children, targetPath, current)
            if (result) return result
          }
        }
        return null
      }

      const result = collectCrumbs(menuItems, path, [])
      if (result) {
        crumbs.push(...result.slice(1)) // 跳过第一个（仪表盘已在 crumbs 中）
        // 最后一个元素是当前页面，不可点击
        const last = crumbs[crumbs.length - 1]
        if (last) {
          last.path = undefined
        }
      }
    }

    return crumbs
  }, [location.pathname])

  // 递归构建 Ant Design Menu 的 items 结构（支持三级菜单）
  const buildMenuItems = useCallback((items: MenuItem[]): any[] => {
    return items.map((item) => {
      const menuItem: any = {
        key: item.key,
        icon: item.icon,
        label: item.label,
      }
      if (item.children) {
        menuItem.children = buildMenuItems(item.children)
      }
      return menuItem
    })
  }, [])

  // 处理菜单点击
  const handleMenuClick = ({ key }: { key: string }) => {
    // 递归查找菜单项的 path
    const findPath = (items: MenuItem[], targetKey: string): string | undefined => {
      for (const item of items) {
        if (item.key === targetKey) return item.path
        if (item.children) {
          const found = findPath(item.children, targetKey)
          if (found) return found
        }
      }
      return undefined
    }
    const path = findPath(menuItems, key)
    if (path) navigate(path)
  }

  // 用户下拉菜单
  const userMenuItems = [
    {
      key: 'profile',
      icon: <UserOutlined />,
      label: '个人信息',
    },
    { type: 'divider' as const },
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: '退出登录',
      danger: true,
    },
  ]

  const handleUserMenuClick = async ({ key }: { key: string }) => {
    if (key === 'logout') {
      await logout()
      navigate('/login', { replace: true })
    }
  }

  return (
    <Layout style={{ minHeight: '100vh' }}>
      {/* 侧边栏 */}
      <Sider
        trigger={null}
        collapsible
        collapsed={collapsed}
        width={260}
        style={{
          background: isDark ? '#141414' : '#001529',
          borderRight: 'none',
        }}
        theme={isDark ? 'dark' : 'dark'}
      >
        {/* Logo 区域 */}
        <div
          style={{
            height: 64,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '0 16px',
            borderBottom: '1px solid rgba(255,255,255,0.1)',
          }}
        >
          {collapsed ? (
            <CloudServerOutlined style={{ fontSize: 24, color: '#fff' }} />
          ) : (
            <Space>
              <CloudServerOutlined style={{ fontSize: 20, color: '#1890ff' }} />
              <Text strong style={{ color: '#fff', fontSize: 16, whiteSpace: 'nowrap' }}>
                PCD Admin
              </Text>
            </Space>
          )}
        </div>

        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={selectedKeys}
          defaultOpenKeys={openKeys}
          items={buildMenuItems(menuItems)}
          onClick={handleMenuClick}
          style={{ borderInlineEnd: 'none' }}
        />
      </Sider>

      {/* 主体区域 */}
      <Layout>
        {/* 顶部导航栏 */}
        <Header
          style={{
            background: themeToken.colorBgContainer,
            padding: '0 24px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            borderBottom: `1px solid ${themeToken.colorBorderSecondary}`,
            height: 64,
            lineHeight: '64px',
          }}
        >
          <Space>
            <Button
              type="text"
              icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
              onClick={() => setCollapsed(!collapsed)}
              style={{ fontSize: 16, width: 40, height: 40 }}
            />
            <Breadcrumb
              items={breadcrumbs.map((crumb) => ({
                title: crumb.path ? (
                  <a onClick={() => navigate(crumb.path!)}>{crumb.title}</a>
                ) : (
                  crumb.title
                ),
              }))}
            />
          </Space>

          <Space size="middle">
            <Button
              type="text"
              icon={<SearchOutlined />}
              style={{ fontSize: 16 }}
              onClick={() => {
                // 全局搜索快捷键 Ctrl+K
              }}
            />
            <Badge count={3} size="small">
              <Button type="text" icon={<BellOutlined />} style={{ fontSize: 16 }} />
            </Badge>

            <Dropdown
              menu={{
                items: userMenuItems,
                onClick: handleUserMenuClick,
              }}
              placement="bottomRight"
            >
              <Space style={{ cursor: 'pointer' }}>
                <Avatar
                  size="small"
                  icon={<UserOutlined />}
                  src={admin?.imagePath}
                  style={{ backgroundColor: '#1890ff' }}
                />
                <Text style={{ maxWidth: 120, overflow: 'hidden', textOverflow: 'ellipsis' }}>
                  {admin?.name || admin?.account || '管理员'}
                </Text>
              </Space>
            </Dropdown>
          </Space>
        </Header>

        {/* 内容区域 */}
        <Content
          style={{
            margin: 24,
            padding: 24,
            background: themeToken.colorBgContainer,
            borderRadius: themeToken.borderRadiusLG,
            minHeight: 280,
            overflow: 'auto',
          }}
        >
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  )
}