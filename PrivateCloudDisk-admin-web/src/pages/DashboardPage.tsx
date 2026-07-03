// ============================================================
// 仪表盘页面 - 企业级扩展版
// 展示系统全貌：用户、文件、订单、存储、安全等
// ============================================================
import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Row, Col, Card, Typography, Table, Tag, List, Space, Progress,
  Spin, theme, Statistic, Button, Tooltip, Segmented, Badge,
} from 'antd'
import {
  UserOutlined, FileOutlined, HddOutlined, WarningOutlined,
  TeamOutlined, ThunderboltOutlined, ClockCircleOutlined,
  CheckCircleOutlined, CloseCircleOutlined, RiseOutlined,
  FallOutlined, ShoppingCartOutlined, DollarOutlined,
  SafetyCertificateOutlined, CloudUploadOutlined,
  CloudDownloadOutlined, ArrowUpOutlined, ArrowDownOutlined,
  ReloadOutlined, EyeOutlined, BarChartOutlined, PieChartOutlined,
  ExclamationCircleOutlined, AuditOutlined, DatabaseOutlined,
  ApiOutlined, GlobalOutlined, MobileOutlined, LaptopOutlined,
  BugOutlined, FireOutlined, SettingOutlined,
} from '@ant-design/icons'
import { useDashboardStore } from '@/stores/dashboardStore'
import StatCard from '@/components/StatCard'
import PageHeader from '@/components/PageHeader'

const { Text, Title } = Typography

// 格式化字节
function formatBytes(bytes: number): string {
  if (!bytes || bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB', 'PB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

function formatNumber(num: number): string {
  if (num >= 1e9) return (num / 1e9).toFixed(1) + 'B'
  if (num >= 1e6) return (num / 1e6).toFixed(1) + 'M'
  if (num >= 1e3) return (num / 1e3).toFixed(1) + 'K'
  return num.toLocaleString()
}

// 颜色常量
const COLORS = [
  '#1677ff', '#52c41a', '#faad14', '#ff4d4f', '#722ed1',
  '#13c2c2', '#eb2f96', '#fa8c16', '#2f54eb', '#a0d911',
]

// ── 简易柱状图 ─────────────────────────────────────────────
function SimpleBarChart({ data, title, color, height = 120 }: {
  data: { label: string; value: number }[]
  title: string
  color: string
  height?: number
}) {
  const max = Math.max(...data.map((d) => d.value), 1)
  return (
    <div>
      <Text type="secondary" style={{ fontSize: 12, marginBottom: 8, display: 'block' }}>{title}</Text>
      <div style={{ display: 'flex', alignItems: 'flex-end', gap: 3, height }}>
        {data.map((item, i) => (
          <div key={i} style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', height: '100%', justifyContent: 'flex-end' }}>
            <Text style={{ fontSize: 9, marginBottom: 2, color: '#8c8c8c' }}>{item.value > 999 ? formatNumber(item.value) : item.value}</Text>
            <div style={{
              width: '100%', maxWidth: 36, height: `${Math.max((item.value / max) * 100, 2)}%`,
              minHeight: 4, background: color, borderRadius: '4px 4px 0 0', transition: 'height 0.3s',
            }} />
            <Text style={{ fontSize: 9, marginTop: 4, color: '#8c8c8c', transform: 'rotate(-30deg)', transformOrigin: 'top left' }}>{item.label}</Text>
          </div>
        ))}
      </div>
    </div>
  )
}

// ── 简易折线图（CSS实现） ──────────────────────────────────
function SimpleLineChart({ data, title, color, height = 100 }: {
  data: { label: string; value: number }[]
  title: string
  color: string
  height?: number
}) {
  const max = Math.max(...data.map((d) => d.value), 1)
  const min = Math.min(...data.map((d) => d.value), 0)
  const range = max - min || 1
  const points = data.map((d, i) => `${(i / (data.length - 1)) * 100},${100 - ((d.value - min) / range) * 100}`).join(' ')

  return (
    <div>
      <Text type="secondary" style={{ fontSize: 12, marginBottom: 8, display: 'block' }}>{title}</Text>
      <div style={{ position: 'relative', height, width: '100%' }}>
        <svg viewBox="0 0 100 100" style={{ width: '100%', height: '100%' }} preserveAspectRatio="none">
          <defs>
            <linearGradient id={`grad-${title.replace(/\s/g, '')}`} x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor={color} stopOpacity="0.3" />
              <stop offset="100%" stopColor={color} stopOpacity="0.05" />
            </linearGradient>
          </defs>
          <polygon
            points={`0,100 ${points} 100,100`}
            fill={`url(#grad-${title.replace(/\s/g, '')})`}
          />
          <polyline
            points={points}
            fill="none"
            stroke={color}
            strokeWidth="1.5"
            vectorEffect="non-scaling-stroke"
          />
        </svg>
      </div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 4 }}>
        {data.filter((_, i) => i % 7 === 0 || i === data.length - 1).map((d, i) => (
          <Text key={i} style={{ fontSize: 9, color: '#8c8c8c' }}>{d.label.slice(5)}</Text>
        ))}
      </div>
    </div>
  )
}

// ── 简易饼图 ───────────────────────────────────────────────
function SimplePieChart({ data, title }: {
  data: { label: string; value: number }[]
  title: string
}) {
  const total = data.reduce((sum, d) => sum + d.value, 0)
  let cumulative = 0
  const segments = data.map((d, i) => {
    const start = cumulative
    const percentage = (d.value / total) * 100
    cumulative += percentage
    return { ...d, percentage, start, color: COLORS[i % COLORS.length] }
  })

  const gradientParts = segments.map((s) => {
    const startAngle = (s.start / 100) * 360
    const endAngle = ((s.start + s.percentage) / 100) * 360
    const startRad = (startAngle - 90) * Math.PI / 180
    const endRad = (endAngle - 90) * Math.PI / 180
    const x1 = 50 + 40 * Math.cos(startRad)
    const y1 = 50 + 40 * Math.sin(startRad)
    const x2 = 50 + 40 * Math.cos(endRad)
    const y2 = 50 + 40 * Math.sin(endRad)
    const largeArc = s.percentage > 50 ? 1 : 0
    return { ...s, d: `M 50 50 L ${x1} ${y1} A 40 40 0 ${largeArc} 1 ${x2} ${y2} Z` }
  })

  return (
    <div>
      <Text type="secondary" style={{ fontSize: 12, marginBottom: 8, display: 'block' }}>{title}</Text>
      <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
        <svg viewBox="0 0 100 100" style={{ width: 100, height: 100 }}>
          {gradientParts.map((part, i) => (
            <path key={i} d={part.d} fill={part.color} stroke="#fff" strokeWidth="0.5" />
          ))}
        </svg>
        <div style={{ flex: 1 }}>
          {segments.slice(0, 6).map((s, i) => (
            <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 4 }}>
              <div style={{ width: 10, height: 10, borderRadius: 2, background: s.color, flexShrink: 0 }} />
              <Text style={{ fontSize: 11, flex: 1 }}>{s.label}</Text>
              <Text style={{ fontSize: 11, color: '#8c8c8c' }}>{s.percentage.toFixed(1)}%</Text>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}

export default function DashboardPage() {
  const { data, loading, fetchDashboard } = useDashboardStore()
  const navigate = useNavigate()
  const { token: themeToken } = theme.useToken()
  const [chartRange, setChartRange] = useState<string>('7')

  useEffect(() => {
    fetchDashboard()
  }, [fetchDashboard])

  const overview = data?.overview

  // 文件类型分布
  const fileTypeChartData = useMemo(() => {
    if (!data?.fileTypeDistribution) return []
    return data.fileTypeDistribution.slice(0, 7).map((item) => ({
      label: item.type,
      value: item.count,
    }))
  }, [data])

  // 存储趋势 (截取最近天数)
  const storageTrendData = useMemo(() => {
    if (!data?.storageTrend) return []
    const days = Number(chartRange)
    return data.storageTrend.slice(-days).map((d) => ({
      label: d.date,
      value: Math.round(d.bytes / (1024 * 1024 * 1024 * 1024) * 100) / 100,
    }))
  }, [data, chartRange])

  // 用户增长趋势
  const userGrowthData = useMemo(() => {
    if (!data?.userGrowth) return []
    const days = Number(chartRange)
    return data.userGrowth.slice(-days).map((d) => ({
      label: d.date,
      value: d.count,
    }))
  }, [data, chartRange])

  const alerts = data?.alerts || []
  const topUsers = data?.topUsers || []
  const recentActivities = data?.recentActivities || []

  // 活动列
  const activityColumns = [
    { title: '用户', dataIndex: 'userName', key: 'userName', width: 100, ellipsis: true },
    {
      title: '操作', dataIndex: 'action', key: 'action', width: 100,
      render: (text: string) => <Tag>{text}</Tag>,
    },
    {
      title: '详情', dataIndex: 'detail', key: 'detail', ellipsis: true,
      render: (text: string) => <Text type="secondary" style={{ fontSize: 12 }}>{text || '-'}</Text>,
    },
    {
      title: '状态', dataIndex: 'status', key: 'status', width: 80,
      render: (status: string) => (
        <Badge status={status === 'SUCCESS' ? 'success' : 'error'} text={status === 'SUCCESS' ? '成功' : '失败'} />
      ),
    },
    {
      title: '时间', dataIndex: 'createdAt', key: 'createdAt', width: 140,
      render: (text: string) => {
        const d = new Date(text)
        return `${d.getMonth() + 1}/${d.getDate()} ${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}`
      },
    },
  ]

  // Top 用户列
  const topUserColumns = [
    { title: '排名', key: 'rank', width: 50, render: (_: unknown, __: unknown, i: number) => (
      <Text strong style={{ color: i < 3 ? COLORS[i] : undefined }}>#{i + 1}</Text>
    )},
    { title: '用户', dataIndex: 'name', key: 'name', ellipsis: true },
    {
      title: '存储用量', dataIndex: 'storageUsed', key: 'storageUsed', width: 100,
      render: (v: number) => formatBytes(v),
    },
    {
      title: '文件数', dataIndex: 'fileCount', key: 'fileCount', width: 80,
      render: (v: number) => formatNumber(v),
    },
    {
      title: '操作', key: 'action', width: 60,
      render: (_: unknown, record: { userId: string }) => (
        <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => navigate(`/users/${record.userId}`)} />
      ),
    },
  ]

  return (
    <div>
      <PageHeader
        title="仪表盘"
        subtitle="系统运行概览与关键指标"
        icon={<ThunderboltOutlined style={{ color: themeToken.colorPrimary }} />}
        actions={
          <Button icon={<ReloadOutlined />} onClick={fetchDashboard}>刷新</Button>
        }
      />

      <Spin spinning={loading}>
        {/* ── 第一行：核心统计卡片 ── */}
        <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
          <Col xs={24} sm={12} lg={8} xl={4}>
            <StatCard title="总用户数" value={overview?.totalUsers || 0}
              prefix={<TeamOutlined />} trend={2.5} trendLabel="较上月"
              onClick={() => navigate('/users')} />
          </Col>
          <Col xs={24} sm={12} lg={8} xl={4}>
            <StatCard title="活跃用户(24h)" value={overview?.activeUsers24h || 0}
              prefix={<UserOutlined />} trend={5.1} trendLabel="较昨日" />
          </Col>
          <Col xs={24} sm={12} lg={8} xl={4}>
            <StatCard title="文件总数" value={formatNumber(overview?.totalFiles || 0)}
              prefix={<FileOutlined />} trend={3.2} trendLabel="较上月"
              onClick={() => navigate('/files')} />
          </Col>
          <Col xs={24} sm={12} lg={8} xl={4}>
            <StatCard title="存储用量" value={formatBytes(overview?.totalStorageBytes || 0)}
              prefix={<HddOutlined />} trend={8.7} trendLabel="较上月"
              onClick={() => navigate('/files/storage')} />
          </Col>
          <Col xs={24} sm={12} lg={8} xl={4}>
            <StatCard title="总下载量" value={formatNumber(overview?.totalDownloads || 0)}
              prefix={<CloudDownloadOutlined />} trend={12.3} trendLabel="较上月" />
          </Col>
          <Col xs={24} sm={12} lg={8} xl={4}>
            <StatCard title="系统运行" value={overview?.uptime || '-'}
              prefix={<ClockCircleOutlined />} />
          </Col>
        </Row>

        {/* ── 第二行：系统资源 + 告警 ── */}
        <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
          <Col xs={24} sm={12} lg={6}>
            <Card size="small" title="CPU" style={{ borderRadius: 8 }}>
              <div style={{ textAlign: 'center' }}>
                <Progress type="circle" percent={overview?.cpuUsage || 0} size={80}
                  strokeColor={themeToken.colorPrimary} />
                <Text type="secondary" style={{ display: 'block', marginTop: 8, fontSize: 12 }}>
                  {overview?.cpuUsage || 0}% 已使用
                </Text>
              </div>
            </Card>
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <Card size="small" title="内存" style={{ borderRadius: 8 }}>
              <div style={{ textAlign: 'center' }}>
                <Progress type="circle" percent={overview?.memoryUsage || 0} size={80}
                  strokeColor={(overview?.memoryUsage || 0) > 80 ? '#ff4d4f' : '#52c41a'} />
                <Text type="secondary" style={{ display: 'block', marginTop: 8, fontSize: 12 }}>
                  {overview?.memoryUsage || 0}% 已使用
                </Text>
              </div>
            </Card>
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <Card size="small" title="磁盘" style={{ borderRadius: 8 }}>
              <div style={{ textAlign: 'center' }}>
                <Progress type="circle" percent={overview?.diskUsage || 0} size={80}
                  strokeColor={(overview?.diskUsage || 0) > 80 ? '#ff4d4f' : '#faad14'} />
                <Text type="secondary" style={{ display: 'block', marginTop: 8, fontSize: 12 }}>
                  {overview?.diskUsage || 0}% 已使用
                </Text>
              </div>
            </Card>
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <Card size="small" title="系统告警" style={{ borderRadius: 8 }}
              extra={<Badge count={alerts.length} size="small" />}>
              <List
                size="small"
                dataSource={alerts.slice(0, 4)}
                renderItem={(alert) => (
                  <List.Item style={{ padding: '4px 0' }}>
                    <Space>
                      <Tag color={alert.severity === 'HIGH' ? 'red' : alert.severity === 'MEDIUM' ? 'orange' : 'blue'}>
                        {alert.severity}
                      </Tag>
                      <Text style={{ fontSize: 12 }} ellipsis={{ tooltip: alert.message }}>
                        {alert.message}
                      </Text>
                    </Space>
                  </List.Item>
                )}
                locale={{ emptyText: '暂无告警' }}
              />
            </Card>
          </Col>
        </Row>

        {/* ── 第三行：趋势图表 ── */}
        <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
          <Col xs={24} lg={12}>
            <Card size="small" title="存储趋势 (TB)" style={{ borderRadius: 8 }}
              extra={
                <Segmented size="small" value={chartRange} onChange={(v) => setChartRange(v as string)}
                  options={[{ label: '7天', value: '7' }, { label: '14天', value: '14' }, { label: '30天', value: '30' }]} />
              }>
              <SimpleLineChart data={storageTrendData} title="" color={themeToken.colorPrimary} height={180} />
            </Card>
          </Col>
          <Col xs={24} lg={12}>
            <Card size="small" title="每日新增用户" style={{ borderRadius: 8 }}
              extra={
                <Segmented size="small" value={chartRange} onChange={(v) => setChartRange(v as string)}
                  options={[{ label: '7天', value: '7' }, { label: '14天', value: '14' }, { label: '30天', value: '30' }]} />}>
              <SimpleBarChart data={userGrowthData} title="" color="#52c41a" height={180} />
            </Card>
          </Col>
        </Row>

        {/* ── 第四行：文件类型分布 + Top 用户 ── */}
        <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
          <Col xs={24} lg={10}>
            <Card size="small" title="文件类型分布" style={{ borderRadius: 8 }}>
              <SimplePieChart data={fileTypeChartData} title="" />
            </Card>
          </Col>
          <Col xs={24} lg={14}>
            <Card size="small" title="存储用量 Top 10" style={{ borderRadius: 8 }}
              extra={<Button type="link" size="small" onClick={() => navigate('/users')}>查看全部</Button>}>
              <Table
                dataSource={topUsers}
                columns={topUserColumns}
                rowKey="userId"
                size="small"
                pagination={false}
                scroll={{ x: 400 }}
              />
            </Card>
          </Col>
        </Row>

        {/* ── 第五行：最近活动 ── */}
        <Row gutter={[16, 16]}>
          <Col span={24}>
            <Card size="small" title="最近活动" style={{ borderRadius: 8 }}
              extra={<Button type="link" size="small" onClick={() => navigate('/audit')}>查看全部</Button>}>
              <Table
                dataSource={recentActivities.slice(0, 15)}
                columns={activityColumns}
                rowKey="id"
                size="small"
                pagination={false}
                scroll={{ x: 600 }}
              />
            </Card>
          </Col>
        </Row>
      </Spin>
    </div>
  )
}