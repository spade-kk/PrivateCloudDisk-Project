// ============================================================
// 管理后台主布局
// ============================================================
import { useState, useMemo } from 'react'
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
} from '@ant-design/icons'
import { useAuthStore } from '@/stores/authStore'

const { Header, Sider, Content } = Layout
const { Text } = Typography

// 菜单配置
interface MenuItem {
  key: string
  icon: React.ReactNode
  label: string
  path: string
  badge?: number
  children?: MenuItem[]
}

const menuItems: MenuItem[] = [
  {
    key: '/dashboard',
    icon: <DashboardOutlined />,
    label: '仪表盘',
    path: '/dashboard',
  },
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
      { key: '/files/quarantined', icon: <ExclamationCircleOutlined />, label: '隔离文件', path: '/files/quarantined' },
      { key: '/files/storage', icon: <DatabaseOutlined />, label: '存储统计', path: '/files/storage' },
    ],
  },
  {
    key: 'security-group',
    icon: <SafetyOutlined />,
    label: '安全中心',
    path: '/security',
    children: [
      { key: '/security/events', icon: <ThunderboltOutlined />, label: '安全事件', path: '/security/events' },
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
]

// 面包屑映射
const breadcrumbMap: Record<string, string> = {
  '/dashboard': '仪表盘',
  '/users': '用户列表',
  '/users/online': '在线用户',
  '/files': '所有文件',
  '/files/quarantined': '隔离文件',
  '/files/storage': '存储统计',
  '/security': '安全中心',
  '/security/events': '安全事件',
  '/security/ip-blacklist': 'IP 黑名单',
  '/audit': '审计日志',
  '/system': '系统设置',
  '/system/config': '系统配置',
  '/system/resources': '系统资源',
  '/system/api-docs': 'API 文档',
}

export default function AdminLayout() {
  const [collapsed, setCollapsed] = useState(false)
  const navigate = useNavigate()
  const location = useLocation()
  const { admin, logout } = useAuthStore()
  const { token: themeToken } = theme.useToken()

  // 判断当前是否为深色模式（根据 antd 主题或系统偏好）
  const isDark = false // 可扩展为从 theme 配置读取

  // 当前选中的菜单 key
  const selectedKeys = useMemo(() => {
    const path = location.pathname
    // 精确匹配
    if (breadcrumbMap[path]) return [path]
    // 前缀匹配
    const parent = menuItems.find((item) =>
      item.children?.some((child) => path.startsWith(child.key))
    )
    if (parent) return [path]
    return ['/dashboard']
  }, [location.pathname])

  // 当前展开的子菜单
  const openKeys = useMemo(() => {
    const path = location.pathname
    const parent = menuItems.find((item) =>
      item.children?.some((child) => path.startsWith(child.key))
    )
    return parent ? [parent.key] : []
  }, [location.pathname])

  // 面包屑
  const breadcrumbs = useMemo(() => {
    const path = location.pathname
    const crumbs: { title: string; path?: string }[] = [{ title: '首页', path: '/dashboard' }]

    if (breadcrumbMap[path]) {
      // 检查是否有父级
      const parent = menuItems.find((item) =>
        item.children?.some((child) => child.key === path)
      )
      if (parent) {
        crumbs.push({ title: parent.label, path: parent.path })
      }
      crumbs.push({ title: breadcrumbMap[path] })
    }

    return crumbs
  }, [location.pathname])

  // 处理菜单点击
  const handleMenuClick = ({ key }: { key: string }) => {
    navigate(key)
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
        width={240}
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
          items={menuItems.map((item) => ({
            key: item.key,
            icon: item.icon,
            label: item.label,
            children: item.children?.map((child) => ({
              key: child.key,
              icon: child.icon,
              label: (
                <Space>
                  {child.label}
                  {child.badge && <Badge count={child.badge} size="small" />}
                </Space>
              ),
            })),
          }))}
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