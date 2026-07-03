// ============================================================
// 用户详情页 - 企业级
// 展示：用户信息、配额、空间、订单、文件、登录日志等
// ============================================================
import { useEffect, useState, useMemo } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import {
  Row, Col, Card, Typography, Descriptions, Table, Tag, Progress,
  Spin, Button, Space, Tabs, Statistic, Avatar, Badge, Divider,
  Modal, message, Tooltip, Timeline, Segmented, Dropdown,
} from 'antd'
import type { MenuProps } from 'antd'
import {
  UserOutlined, MailOutlined, PhoneOutlined, ClockCircleOutlined,
  EnvironmentOutlined, LaptopOutlined, GlobalOutlined,
  HddOutlined, FileOutlined, ShoppingCartOutlined,
  SafetyCertificateOutlined, CloudUploadOutlined,
  CloudDownloadOutlined, EditOutlined, DeleteOutlined,
  LockOutlined, UnlockOutlined, ArrowLeftOutlined, ExclamationCircleOutlined,
  ReloadOutlined, BarChartOutlined, TeamOutlined, ApiOutlined,
  CheckCircleOutlined, CloseCircleOutlined, WarningOutlined,
  DollarOutlined, CalendarOutlined, IdcardOutlined, KeyOutlined,
  MobileOutlined, MonitorOutlined,
} from '@ant-design/icons'
import type { User, Order } from '@/types/api'
import { mockUsers, mockOrders } from '@/mock/data'
import PageHeader from '@/components/PageHeader'
import { formatBytes } from '@/utils/format'

const { Text, Title } = Typography

// 颜色常量
const COLORS = ['#1677ff', '#52c41a', '#faad14', '#ff4d4f', '#722ed1', '#13c2c2', '#eb2f96', '#fa8c16']

// 格式化数字
function formatNumber(num: number): string {
  if (num >= 1e9) return (num / 1e9).toFixed(1) + 'B'
  if (num >= 1e6) return (num / 1e6).toFixed(1) + 'M'
  if (num >= 1e3) return (num / 1e3).toFixed(1) + 'K'
  return num.toLocaleString()
}

// 状态标签
const statusMap: Record<string, { color: string; text: string; icon: React.ReactNode }> = {
  ACTIVE: { color: 'green', text: '正常', icon: <CheckCircleOutlined /> },
  DISABLED: { color: 'red', text: '已禁用', icon: <CloseCircleOutlined /> },
  SUSPENDED: { color: 'orange', text: '已暂停', icon: <WarningOutlined /> },
}

// 订单状态
const orderStatusMap: Record<string, { color: string; text: string }> = {
  PENDING: { color: 'blue', text: '待支付' },
  PAID: { color: 'green', text: '已支付' },
  CANCELLED: { color: 'default', text: '已取消' },
  REFUNDED: { color: 'orange', text: '已退款' },
  EXPIRED: { color: 'red', text: '已过期' },
}

// 支付渠道
const channelMap: Record<string, string> = {
  ALIPAY: '支付宝',
  WECHAT: '微信支付',
  BANK: '银行转账',
  STRIPE: 'Stripe',
}

// 模拟登录日志
interface LoginLog {
  id: string
  ip: string
  location: string
  device: string
  browser: string
  os: string
  success: boolean
  failReason: string | null
  loginAt: string
}

// 模拟共享文件
interface SharedFile {
  id: string
  fileName: string
  fileSize: number
  sharedWith: number
  sharedAt: string
  permission: string
  expiresAt: string | null
}

// 生成模拟登录日志
function generateLoginLogs(userId: string): LoginLog[] {
  const locations = ['北京', '上海', '深圳', '广州', '杭州', '成都', '南京', '武汉']
  const devices = ['Windows 11', 'macOS 14', 'iPhone 15', 'Android 14', 'iPad Pro']
  const browsers = ['Chrome 120', 'Edge 120', 'Safari 17', 'Firefox 121']
  const osList = ['Windows', 'macOS', 'iOS', 'Android']
  const ips = Array.from({ length: 8 }, () =>
    `${Math.floor(Math.random() * 223) + 1}.${Math.floor(Math.random() * 255)}.${Math.floor(Math.random() * 255)}.${Math.floor(Math.random() * 255)}`
  )

  return Array.from({ length: 12 }, (_, i) => ({
    id: `log-${userId}-${i}`,
    ip: ips[i % ips.length],
    location: locations[i % locations.length],
    device: devices[i % devices.length],
    browser: browsers[i % browsers.length],
    os: osList[i % osList.length],
    success: i < 10 || Math.random() > 0.3,
    failReason: i >= 10 && Math.random() > 0.3 ? '密码错误' : null,
    loginAt: new Date(Date.now() - i * 3600000 * 3).toISOString(),
  }))
}

// 生成模拟共享文件
function generateSharedFiles(): SharedFile[] {
  const fileNames = ['项目方案.pptx', '设计稿.fig', '周报.docx', '财务报表.xlsx', '合同.pdf', '产品原型.sketch']
  return Array.from({ length: 6 }, (_, i) => ({
    id: `sf-${i}`,
    fileName: fileNames[i],
    fileSize: Math.floor(Math.random() * 50 * 1024 * 1024) + 1024 * 1024,
    sharedWith: Math.floor(Math.random() * 20) + 1,
    sharedAt: new Date(Date.now() - i * 86400000 * 2).toISOString(),
    permission: ['READ', 'WRITE', 'ADMIN'][i % 3],
    expiresAt: i % 2 === 0 ? new Date(Date.now() + 86400000 * 7 * (i + 1)).toISOString() : null,
  }))
}

export default function UserDetailPage() {
  const { userId } = useParams<{ userId: string }>()
  const navigate = useNavigate()
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)
  const [activeTab, setActiveTab] = useState('overview')

  // 模拟数据
  const loginLogs = useMemo(() => (userId ? generateLoginLogs(userId) : []), [userId])
  const sharedFiles = useMemo(() => generateSharedFiles(), [])
  const userOrders = useMemo(() => {
    if (!userId) return []
    return mockOrders.filter((o) => o.userId === userId)
  }, [userId])

  useEffect(() => {
    setLoading(true)
    // 模拟 API 请求
    const timer = setTimeout(() => {
      const found = mockUsers.find((u) => u.userId === userId)
      if (found) {
        setUser({
          ...found,
          location: ['北京', '上海', '深圳', '广州', '杭州'][Math.floor(Math.random() * 5)],
          lastLoginIp: '192.168.1.100',
          registerIp: '203.0.113.45',
          totalDownloadBytes: Math.floor(Math.random() * 500 * 1024 * 1024 * 1024),
          totalUploadBytes: Math.floor(Math.random() * 200 * 1024 * 1024 * 1024),
          deviceCount: Math.floor(Math.random() * 5) + 1,
          sharedFileCount: Math.floor(Math.random() * 20) + 5,
        })
      }
      setLoading(false)
    }, 300)
    return () => clearTimeout(timer)
  }, [userId])

  if (!user && !loading) {
    return (
      <div style={{ textAlign: 'center', padding: 80 }}>
        <ExclamationCircleOutlined style={{ fontSize: 48, color: '#faad14' }} />
        <Title level={4} style={{ marginTop: 16 }}>用户不存在</Title>
        <Button type="primary" onClick={() => navigate('/users')}>返回用户列表</Button>
      </div>
    )
  }

  const statusInfo = user ? statusMap[user.status] : null
  const storagePercent = user ? Math.round((user.storageUsed / user.storageLimit) * 100) : 0

  // 登录日志列
  const loginLogColumns = [
    {
      title: '状态', dataIndex: 'success', key: 'success', width: 60,
      render: (v: boolean) => v
        ? <CheckCircleOutlined style={{ color: '#52c41a' }} />
        : <CloseCircleOutlined style={{ color: '#ff4d4f' }} />,
    },
    { title: 'IP', dataIndex: 'ip', key: 'ip', width: 130, render: (v: string) => <Text code>{v}</Text> },
    { title: '位置', dataIndex: 'location', key: 'location', width: 80 },
    { title: '设备', dataIndex: 'device', key: 'device', width: 110, ellipsis: true },
    { title: '浏览器', dataIndex: 'browser', key: 'browser', width: 110, ellipsis: true },
    {
      title: '失败原因', dataIndex: 'failReason', key: 'failReason', width: 100,
      render: (v: string | null) => v ? <Tag color="red">{v}</Tag> : '-',
    },
    {
      title: '时间', dataIndex: 'loginAt', key: 'loginAt', width: 140,
      render: (v: string) => new Date(v).toLocaleString('zh-CN'),
    },
  ]

  // 订单列
  const orderColumns = [
    { title: '订单号', dataIndex: 'orderNo', key: 'orderNo', width: 160, render: (v: string) => <Text code>{v}</Text> },
    { title: '套餐', dataIndex: 'planName', key: 'planName', width: 100 },
    {
      title: '周期', dataIndex: 'planCycle', key: 'planCycle', width: 70,
      render: (v: string) => ({ MONTHLY: '月', QUARTERLY: '季', YEARLY: '年', LIFETIME: '永久' }[v] || v),
    },
    {
      title: '金额', dataIndex: 'finalPrice', key: 'finalPrice', width: 80,
      render: (v: number) => <Text strong>¥{v.toFixed(2)}</Text>,
    },
    {
      title: '状态', dataIndex: 'status', key: 'status', width: 80,
      render: (v: string) => {
        const info = orderStatusMap[v]
        return <Tag color={info?.color}>{info?.text || v}</Tag>
      },
    },
    {
      title: '支付方式', dataIndex: 'paymentChannel', key: 'paymentChannel', width: 90,
      render: (v: string) => channelMap[v] || v,
    },
    {
      title: '支付时间', dataIndex: 'paymentTime', key: 'paymentTime', width: 140,
      render: (v: string | null) => v ? new Date(v).toLocaleString('zh-CN') : <Text type="secondary">-</Text>,
    },
    {
      title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 140,
      render: (v: string) => new Date(v).toLocaleString('zh-CN'),
    },
  ]

  // 共享文件列
  const sharedFileColumns = [
    { title: '文件名', dataIndex: 'fileName', key: 'fileName', ellipsis: true },
    {
      title: '大小', dataIndex: 'fileSize', key: 'fileSize', width: 80,
      render: (v: number) => formatBytes(v),
    },
    {
      title: '分享人数', dataIndex: 'sharedWith', key: 'sharedWith', width: 80,
      render: (v: number) => <Text>{v} 人</Text>,
    },
    {
      title: '权限', dataIndex: 'permission', key: 'permission', width: 80,
      render: (v: string) => {
        const m: Record<string, { color: string; label: string }> = {
          READ: { color: 'blue', label: '只读' },
          WRITE: { color: 'orange', label: '可写' },
          ADMIN: { color: 'red', label: '管理' },
        }
        return <Tag color={m[v]?.color}>{m[v]?.label}</Tag>
      },
    },
    {
      title: '过期时间', dataIndex: 'expiresAt', key: 'expiresAt', width: 120,
      render: (v: string | null) => v ? new Date(v).toLocaleDateString('zh-CN') : <Text type="secondary">永久</Text>,
    },
    {
      title: '分享时间', dataIndex: 'sharedAt', key: 'sharedAt', width: 120,
      render: (v: string) => new Date(v).toLocaleDateString('zh-CN'),
    },
  ]

  // 操作菜单
  const actionItems: MenuProps['items'] = [
    { key: 'edit', icon: <EditOutlined />, label: '编辑信息' },
    { key: 'resetPwd', icon: <KeyOutlined />, label: '重置密码' },
    { type: 'divider' },
    user?.status === 'ACTIVE'
      ? { key: 'disable', icon: <LockOutlined />, label: '禁用账号', danger: true }
      : { key: 'enable', icon: <UnlockOutlined />, label: '启用账号' },
    { type: 'divider' },
    { key: 'delete', icon: <DeleteOutlined />, label: '删除用户', danger: true },
  ]

  // 选项卡
  const tabItems = [
    {
      key: 'overview',
      label: (<span><IdcardOutlined />概览</span>),
      children: (
        <Row gutter={[16, 16]}>
          <Col xs={24} lg={16}>
            <Card size="small" title="基本信息" style={{ borderRadius: 8 }}>
              <Descriptions column={{ xs: 1, sm: 2 }} size="small" bordered>
                <Descriptions.Item label="用户ID">{user?.userId}</Descriptions.Item>
                <Descriptions.Item label="账号">{user?.account}</Descriptions.Item>
                <Descriptions.Item label="姓名">{user?.name}</Descriptions.Item>
                <Descriptions.Item label="邮箱">
                  <Space>
                    {user?.email}
                    {user?.emailVerified ? <Tag color="green">已验证</Tag> : <Tag color="orange">未验证</Tag>}
                  </Space>
                </Descriptions.Item>
                <Descriptions.Item label="手机号">
                  <Space>
                    {user?.phoneNumber}
                    {user?.phoneVerified ? <Tag color="green">已验证</Tag> : <Tag color="orange">未验证</Tag>}
                  </Space>
                </Descriptions.Item>
                <Descriptions.Item label="双因素认证">
                  {user?.twoFactorEnabled ? <Tag color="green">已开启</Tag> : <Tag color="default">未开启</Tag>}
                </Descriptions.Item>
                <Descriptions.Item label="角色">{user?.role}</Descriptions.Item>
                <Descriptions.Item label="状态">
                  {statusInfo && <Tag color={statusInfo.color} icon={statusInfo.icon}>{statusInfo.text}</Tag>}
                </Descriptions.Item>
                <Descriptions.Item label="所在地">{user?.location || '-'}</Descriptions.Item>
                <Descriptions.Item label="注册IP">{user?.registerIp || '-'}</Descriptions.Item>
                <Descriptions.Item label="最后登录IP">{user?.lastLoginIp || '-'}</Descriptions.Item>
                <Descriptions.Item label="最近登录">{user?.lastLoginAt ? new Date(user.lastLoginAt).toLocaleString('zh-CN') : '-'}</Descriptions.Item>
                <Descriptions.Item label="注册时间">{user?.createdAt ? new Date(user.createdAt).toLocaleString('zh-CN') : '-'}</Descriptions.Item>
                <Descriptions.Item label="绑定设备">{user?.deviceCount ?? '-'} 台</Descriptions.Item>
              </Descriptions>
            </Card>
          </Col>
          <Col xs={24} lg={8}>
            <Card size="small" title="存储配额" style={{ borderRadius: 8 }}>
              <div style={{ textAlign: 'center', marginBottom: 16 }}>
                <Progress
                  type="dashboard"
                  percent={isNaN(storagePercent) ? 0 : storagePercent}
                  strokeColor={storagePercent > 80 ? '#ff4d4f' : storagePercent > 60 ? '#faad14' : '#1677ff'}
                  size={160}
                />
              </div>
              <Descriptions column={1} size="small" bordered>
                <Descriptions.Item label="已用空间">{user ? formatBytes(user.storageUsed) : '-'}</Descriptions.Item>
                <Descriptions.Item label="总配额">{user ? formatBytes(user.storageLimit) : '-'}</Descriptions.Item>
                <Descriptions.Item label="文件数量">{user?.fileCount ?? 0} 个</Descriptions.Item>
                <Descriptions.Item label="已分享文件">{user?.sharedFileCount ?? 0} 个</Descriptions.Item>
              </Descriptions>
            </Card>
            <Card size="small" title="流量统计" style={{ borderRadius: 8, marginTop: 16 }}>
              <Descriptions column={1} size="small" bordered>
                <Descriptions.Item label="总上传量">
                  <CloudUploadOutlined style={{ color: '#1677ff', marginRight: 4 }} />
                  {user ? formatBytes(user.totalUploadBytes || 0) : '-'}
                </Descriptions.Item>
                <Descriptions.Item label="总下载量">
                  <CloudDownloadOutlined style={{ color: '#52c41a', marginRight: 4 }} />
                  {user ? formatBytes(user.totalDownloadBytes || 0) : '-'}
                </Descriptions.Item>
              </Descriptions>
            </Card>
          </Col>
        </Row>
      ),
    },
    {
      key: 'orders',
      label: (<span><ShoppingCartOutlined />订单记录 ({userOrders.length})</span>),
      children: (
        <Card size="small" title="购买历史" style={{ borderRadius: 8 }}>
          <Table
            dataSource={userOrders}
            columns={orderColumns}
            rowKey="orderId"
            size="small"
            pagination={{ pageSize: 10, showSizeChanger: true }}
            scroll={{ x: 900 }}
            locale={{ emptyText: '暂无订单记录' }}
          />
        </Card>
      ),
    },
    {
      key: 'login',
      label: (<span><MonitorOutlined />登录日志</span>),
      children: (
        <Card size="small" title="最近登录记录" style={{ borderRadius: 8 }}>
          <Table
            dataSource={loginLogs}
            columns={loginLogColumns}
            rowKey="id"
            size="small"
            pagination={{ pageSize: 10 }}
            scroll={{ x: 800 }}
          />
        </Card>
      ),
    },
    {
      key: 'shared',
      label: (<span><TeamOutlined />共享文件</span>),
      children: (
        <Card size="small" title="已分享文件" style={{ borderRadius: 8 }}>
          <Table
            dataSource={sharedFiles}
            columns={sharedFileColumns}
            rowKey="id"
            size="small"
            pagination={{ pageSize: 10 }}
            scroll={{ x: 700 }}
            locale={{ emptyText: '暂无共享文件' }}
          />
        </Card>
      ),
    },
  ]

  return (
    <div>
      <PageHeader
        title="用户详情"
        subtitle={user?.name || ''}
        icon={<UserOutlined style={{ color: '#1677ff' }} />}
        actions={
          <Space>
            <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/users')}>返回列表</Button>
            <Dropdown menu={{ items: actionItems, onClick: ({ key }) => {
              if (key === 'delete') {
                Modal.confirm({
                  title: '确认删除',
                  content: `确定要删除用户 ${user?.name} 吗？此操作不可撤销。`,
                  okButtonProps: { danger: true },
                  onOk: () => message.success('删除成功（Mock）'),
                })
              } else {
                message.info(`操作 "${key}" 已触发（Mock）`)
              }
            }}}>
              <Button type="primary" icon={<EditOutlined />}>操作</Button>
            </Dropdown>
          </Space>
        }
      />

      <Spin spinning={loading}>
        {user && (
          <Tabs activeKey={activeTab} onChange={setActiveTab} items={tabItems} />
        )}
      </Spin>
    </div>
  )
}