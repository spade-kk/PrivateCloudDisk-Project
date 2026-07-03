// ============================================================
// 订单管理页面 - 企业级
// 展示：订单列表、收入统计、支付渠道分布、套餐分析
// ============================================================
import { useEffect, useState, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Row, Col, Card, Typography, Table, Tag, Spin, Button, Space,
  Statistic, Select, Input, DatePicker, Tabs, Badge, Tooltip,
  message, Divider, Empty,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  ShoppingCartOutlined, DollarOutlined, ReloadOutlined,
  SearchOutlined, EyeOutlined, CheckCircleOutlined,
  CloseCircleOutlined, ClockCircleOutlined, ExportOutlined,
  RiseOutlined, FallOutlined, WalletOutlined, CreditCardOutlined,
  BankOutlined, AlipayOutlined, WechatOutlined, CalendarOutlined,
  PieChartOutlined, BarChartOutlined, GiftOutlined, FilterOutlined,
  ClearOutlined, ArrowUpOutlined, ArrowDownOutlined,
} from '@ant-design/icons'
import type { Order } from '@/types/api'
import { mockOrders } from '@/mock/data'
import PageHeader from '@/components/PageHeader'

const { Text, Title } = Typography
const { RangePicker } = DatePicker

// 颜色
const COLORS = ['#1677ff', '#52c41a', '#faad14', '#ff4d4f', '#722ed1', '#13c2c2', '#eb2f96', '#fa8c16']

// 订单状态
const orderStatusMap: Record<string, { color: string; text: string }> = {
  PENDING: { color: 'blue', text: '待支付' },
  PAID: { color: 'green', text: '已支付' },
  CANCELLED: { color: 'default', text: '已取消' },
  REFUNDED: { color: 'orange', text: '已退款' },
  EXPIRED: { color: 'red', text: '已过期' },
}

// 支付渠道
const channelMap: Record<string, { text: string; icon: React.ReactNode }> = {
  ALIPAY: { text: '支付宝', icon: <AlipayOutlined /> },
  WECHAT: { text: '微信支付', icon: <WechatOutlined /> },
  BANK: { text: '银行转账', icon: <BankOutlined /> },
  STRIPE: { text: 'Stripe', icon: <CreditCardOutlined /> },
}

// 周期
const cycleMap: Record<string, string> = {
  MONTHLY: '月付',
  QUARTERLY: '季付',
  YEARLY: '年付',
  LIFETIME: '永久',
}

// 简易柱状图
function SimpleBarChart({ data, color, height = 120 }: {
  data: { label: string; value: number }[]
  color: string
  height?: number
}) {
  const max = Math.max(...data.map((d) => d.value), 1)
  return (
    <div style={{ display: 'flex', alignItems: 'flex-end', gap: 4, height, padding: '0 4px' }}>
      {data.map((item, i) => (
        <div key={i} style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', height: '100%', justifyContent: 'flex-end' }}>
          <Text style={{ fontSize: 9, marginBottom: 2, color: '#8c8c8c' }}>¥{item.value}</Text>
          <div style={{
            width: '100%', maxWidth: 40, height: `${Math.max((item.value / max) * 100, 2)}%`,
            minHeight: 4, background: color, borderRadius: '4px 4px 0 0',
          }} />
          <Text style={{ fontSize: 9, marginTop: 4, color: '#8c8c8c', transform: 'rotate(-30deg)', transformOrigin: 'top left' }}>{item.label}</Text>
        </div>
      ))}
    </div>
  )
}

// 简易饼图
function SimplePie({ data }: { data: { label: string; value: number }[] }) {
  const total = data.reduce((sum, d) => sum + d.value, 0)
  let cumulative = 0
  const segments = data.map((d, i) => {
    const start = cumulative
    const pct = (d.value / total) * 100
    cumulative += pct
    return { ...d, pct, start, color: COLORS[i % COLORS.length] }
  })

  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
      <svg viewBox="0 0 100 100" style={{ width: 100, height: 100 }}>
        {segments.map((s, i) => {
          const sa = (s.start / 100) * 360
          const ea = ((s.start + s.pct) / 100) * 360
          const r1 = (sa - 90) * Math.PI / 180
          const r2 = (ea - 90) * Math.PI / 180
          const x1 = 50 + 40 * Math.cos(r1); const y1 = 50 + 40 * Math.sin(r1)
          const x2 = 50 + 40 * Math.cos(r2); const y2 = 50 + 40 * Math.sin(r2)
          const la = s.pct > 50 ? 1 : 0
          return <path key={i} d={`M 50 50 L ${x1} ${y1} A 40 40 0 ${la} 1 ${x2} ${y2} Z`} fill={s.color} stroke="#fff" strokeWidth="0.5" />
        })}
      </svg>
      <div style={{ flex: 1 }}>
        {segments.map((s, i) => (
          <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 4 }}>
            <div style={{ width: 10, height: 10, borderRadius: 2, background: s.color }} />
            <Text style={{ fontSize: 11, flex: 1 }}>{s.label}</Text>
            <Text style={{ fontSize: 11, color: '#8c8c8c' }}>{s.pct.toFixed(1)}%</Text>
          </div>
        ))}
      </div>
    </div>
  )
}

export default function OrdersPage() {
  const navigate = useNavigate()
  const [loading, setLoading] = useState(true)
  const [orders, setOrders] = useState<Order[]>([])
  const [activeTab, setActiveTab] = useState('all')
  const [filterStatus, setFilterStatus] = useState<string | undefined>(undefined)
  const [filterChannel, setFilterChannel] = useState<string | undefined>(undefined)
  const [searchText, setSearchText] = useState('')

  useEffect(() => {
    const timer = setTimeout(() => {
      setOrders(mockOrders)
      setLoading(false)
    }, 400)
    return () => clearTimeout(timer)
  }, [])

  // 统计数据
  const stats = useMemo(() => {
    const paid = orders.filter((o) => o.status === 'PAID')
    const totalRevenue = paid.reduce((sum, o) => sum + o.finalPrice, 0)
    const today = new Date().toISOString().slice(0, 10)
    const todayRevenue = paid.filter((o) => o.paymentTime?.startsWith(today)).reduce((sum, o) => sum + o.finalPrice, 0)
    return {
      total: orders.length,
      revenue: totalRevenue,
      pending: orders.filter((o) => o.status === 'PENDING').length,
      paid: paid.length,
      refunded: orders.filter((o) => o.status === 'REFUNDED').length,
      todayRevenue,
    }
  }, [orders])

  // 月度收入
  const monthlyRevenue = useMemo(() => {
    const paid = orders.filter((o) => o.status === 'PAID' && o.paymentTime)
    const map: Record<string, number> = {}
    for (const o of paid) {
      const month = o.paymentTime!.slice(0, 7)
      map[month] = (map[month] || 0) + o.finalPrice
    }
    return Object.entries(map).sort().map(([label, value]) => ({ label: label.slice(5), value: Math.round(value) }))
  }, [orders])

  // 渠道分布
  const channelDist = useMemo(() => {
    const map: Record<string, number> = {}
    for (const o of orders) {
      map[o.paymentChannel] = (map[o.paymentChannel] || 0) + 1
    }
    return Object.entries(map).map(([label, value]) => ({ label: channelMap[label]?.text || label, value }))
  }, [orders])

  // 套餐分布
  const planDist = useMemo(() => {
    const map: Record<string, number> = {}
    for (const o of orders) {
      map[o.planName] = (map[o.planName] || 0) + 1
    }
    return Object.entries(map).map(([label, value]) => ({ label, value }))
  }, [orders])

  // 过滤
  const filteredOrders = useMemo(() => {
    let result = orders
    if (activeTab === 'pending') result = result.filter((o) => o.status === 'PENDING')
    else if (activeTab === 'paid') result = result.filter((o) => o.status === 'PAID')
    else if (activeTab === 'refunded') result = result.filter((o) => o.status === 'REFUNDED')
    if (filterStatus) result = result.filter((o) => o.status === filterStatus)
    if (filterChannel) result = result.filter((o) => o.paymentChannel === filterChannel)
    if (searchText) {
      const lower = searchText.toLowerCase()
      result = result.filter((o) =>
        o.orderNo.toLowerCase().includes(lower) ||
        o.userName.toLowerCase().includes(lower) ||
        o.userId.toLowerCase().includes(lower)
      )
    }
    return result
  }, [orders, activeTab, filterStatus, filterChannel, searchText])

  const columns: ColumnsType<Order> = [
    { title: '订单号', dataIndex: 'orderNo', key: 'orderNo', width: 180, fixed: 'left',
      render: (v: string) => <Text code style={{ fontSize: 12 }}>{v}</Text> },
    {
      title: '用户', dataIndex: 'userName', key: 'userName', width: 100,
      render: (text: string, record: Order) => (
        <a onClick={() => navigate(`/users/${record.userId}`)}>{text}</a>
      ),
    },
    { title: '套餐', dataIndex: 'planName', key: 'planName', width: 100 },
    {
      title: '周期', dataIndex: 'planCycle', key: 'planCycle', width: 70,
      render: (v: string) => <Tag>{cycleMap[v] || v}</Tag>,
    },
    {
      title: '原价', dataIndex: 'originalPrice', key: 'originalPrice', width: 80,
      render: (v: number) => <Text>¥{v.toFixed(2)}</Text>,
    },
    {
      title: '折扣', dataIndex: 'discountAmount', key: 'discountAmount', width: 80,
      render: (v: number) => v > 0 ? <Text type="success">-¥{v.toFixed(2)}</Text> : <Text type="secondary">-</Text>,
    },
    {
      title: '实付', dataIndex: 'finalPrice', key: 'finalPrice', width: 90,
      render: (v: number) => <Text strong style={{ color: '#52c41a' }}>¥{v.toFixed(2)}</Text>,
    },
    {
      title: '状态', dataIndex: 'status', key: 'status', width: 80,
      render: (v: string) => {
        const info = orderStatusMap[v]
        return <Tag color={info?.color}>{info?.text}</Tag>
      },
    },
    {
      title: '支付方式', dataIndex: 'paymentChannel', key: 'paymentChannel', width: 100,
      render: (v: string) => {
        const info = channelMap[v]
        return <Space size={4}>{info?.icon}<Text style={{ fontSize: 12 }}>{info?.text}</Text></Space>
      },
    },
    {
      title: '支付时间', dataIndex: 'paymentTime', key: 'paymentTime', width: 140,
      render: (v: string | null) => v ? new Date(v).toLocaleString('zh-CN') : <Text type="secondary">-</Text>,
    },
    { title: '存储配额', dataIndex: 'storageQuota', key: 'storageQuota', width: 90,
      render: (v: number) => `${(v / (1024 * 1024 * 1024)).toFixed(0)}GB` },
    {
      title: '过期时间', dataIndex: 'expiredAt', key: 'expiredAt', width: 120,
      render: (v: string | null) => v ? new Date(v).toLocaleDateString('zh-CN') : <Text type="secondary">永久</Text>,
    },
    {
      title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 140,
      render: (v: string) => new Date(v).toLocaleString('zh-CN'),
    },
    { title: '备注', dataIndex: 'remark', key: 'remark', width: 120, ellipsis: true,
      render: (v: string) => v || '-' },
  ]

  return (
    <div>
      <PageHeader
        title="订单管理"
        subtitle="管理所有支付订单与收入分析"
        icon={<ShoppingCartOutlined style={{ color: '#1677ff' }} />}
        actions={
          <Space>
            <Button icon={<ExportOutlined />}>导出</Button>
            <Button icon={<ReloadOutlined />} onClick={() => { setLoading(true); setTimeout(() => { setOrders(mockOrders); setLoading(false) }, 300) }}>刷新</Button>
          </Space>
        }
      />

      {/* ── 统计卡片 ── */}
      <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
        <Col xs={24} sm={12} lg={6}>
          <Card size="small" style={{ borderRadius: 8 }}>
            <Statistic title="总订单数" value={stats.total} prefix={<ShoppingCartOutlined />} />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card size="small" style={{ borderRadius: 8 }}>
            <Statistic title="总营收" value={stats.revenue} precision={2} prefix="¥" valueStyle={{ color: '#52c41a' }} />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card size="small" style={{ borderRadius: 8 }}>
            <Statistic title="今日营收" value={stats.todayRevenue} precision={2} prefix="¥" valueStyle={{ color: '#1677ff' }} />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card size="small" style={{ borderRadius: 8 }}>
            <Statistic title="待支付" value={stats.pending} valueStyle={{ color: '#faad14' }} prefix={<ClockCircleOutlined />} />
          </Card>
        </Col>
      </Row>

      {/* ── 图表行 ── */}
      <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
        <Col xs={24} lg={12}>
          <Card size="small" title="月度收入趋势" style={{ borderRadius: 8 }}>
            <SimpleBarChart data={monthlyRevenue} color="#1677ff" height={160} />
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Row gutter={[16, 16]}>
            <Col span={12}>
              <Card size="small" title="支付渠道" style={{ borderRadius: 8 }}>
                <SimplePie data={channelDist} />
              </Card>
            </Col>
            <Col span={12}>
              <Card size="small" title="套餐分布" style={{ borderRadius: 8 }}>
                <SimplePie data={planDist} />
              </Card>
            </Col>
          </Row>
        </Col>
      </Row>

      {/* ── 筛选 + 列表 ── */}
      <Card size="small" style={{ borderRadius: 8, marginBottom: 16 }}>
        <Row gutter={[16, 12]} align="middle">
          <Col flex="auto">
            <Tabs activeKey={activeTab} onChange={setActiveTab} size="small"
              items={[
                { key: 'all', label: `全部 (${stats.total})` },
                { key: 'pending', label: <Badge count={stats.pending} size="small" offset={[8, -2]}>待支付</Badge> },
                { key: 'paid', label: '已支付' },
                { key: 'refunded', label: '已退款' },
              ]}
            />
          </Col>
          <Col>
            <Space>
              <Select allowClear placeholder="订单状态" value={filterStatus} onChange={setFilterStatus} style={{ width: 120 }}
                options={[
                  { value: 'PENDING', label: '待支付' }, { value: 'PAID', label: '已支付' },
                  { value: 'CANCELLED', label: '已取消' }, { value: 'REFUNDED', label: '已退款' },
                  { value: 'EXPIRED', label: '已过期' },
                ]} />
              <Select allowClear placeholder="支付渠道" value={filterChannel} onChange={setFilterChannel} style={{ width: 120 }}
                options={[
                  { value: 'ALIPAY', label: '支付宝' }, { value: 'WECHAT', label: '微信支付' },
                  { value: 'BANK', label: '银行转账' }, { value: 'STRIPE', label: 'Stripe' },
                ]} />
              <Input.Search placeholder="搜索订单号/用户" value={searchText}
                onChange={(e) => setSearchText(e.target.value)} style={{ width: 220 }} allowClear />
            </Space>
          </Col>
        </Row>
      </Card>

      <Card size="small" style={{ borderRadius: 8 }}>
        <Spin spinning={loading}>
          <Table
            dataSource={filteredOrders}
            columns={columns}
            rowKey="orderId"
            size="small"
            pagination={{ pageSize: 15, showSizeChanger: true, showTotal: (t) => `共 ${t} 条` }}
            scroll={{ x: 1600 }}
            locale={{ emptyText: <Empty description="暂无订单记录" /> }}
          />
        </Spin>
      </Card>
    </div>
  )
}