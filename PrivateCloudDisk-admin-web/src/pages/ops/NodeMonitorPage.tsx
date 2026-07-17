// ============================================================
// 节点监控页面 - CPU / Memory / Disk / Network 实时监控
// 同时提供 Grafana / Prometheus 深度集成入口
// ============================================================
import { useEffect, useState, useCallback, useMemo } from 'react'
import {
  Card, Table, Tag, Select, Space, Statistic, Row, Col, Progress,
  Spin, Alert, Button, Tooltip, Badge, Tabs, Empty, Typography, Descriptions,
  DatePicker, Segmented,
} from 'antd'
import {
  ReloadOutlined, DashboardOutlined, CloudServerOutlined,
  WarningOutlined, CheckCircleOutlined, MinusCircleOutlined,
  LineChartOutlined, ClusterOutlined,
} from '@ant-design/icons'
import { useOpsMonitorStore } from '@/stores/opsMonitorStore'
import type { NodeMetrics } from '@/api/opsMonitor'
import type { ColumnsType } from 'antd/es/table'

const { Text } = Typography
const { RangePicker } = DatePicker

// 自动刷新间隔（毫秒）
const AUTO_REFRESH_INTERVAL = 10000

// 格式化字节
function formatBytes(bytes: number): string {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return `${(bytes / Math.pow(k, i)).toFixed(1)} ${sizes[i]}`
}

// 格式化运行时间
function formatUptime(seconds: number): string {
  const d = Math.floor(seconds / 86400)
  const h = Math.floor((seconds % 86400) / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  if (d > 0) return `${d}d ${h}h ${m}m`
  return `${h}h ${m}m`
}

// 节点状态映射
const statusMap: Record<string, { color: string; icon: React.ReactNode; text: string }> = {
  ONLINE: { color: 'green', icon: <CheckCircleOutlined />, text: '在线' },
  OFFLINE: { color: 'red', icon: <MinusCircleOutlined />, text: '离线' },
  MAINTENANCE: { color: 'orange', icon: <WarningOutlined />, text: '维护中' },
  DEGRADED: { color: 'gold', icon: <WarningOutlined />, text: '降级' },
}

export default function NodeMonitorPage() {
  const {
    nodes, nodesTotal, currentNode, loading, error,
    fetchNodes, fetchNodeDetail,
  } = useOpsMonitorStore()

  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null)
  const [autoRefresh, setAutoRefresh] = useState(true)
  const [detailTab, setDetailTab] = useState('overview')

  // 加载节点列表
  const loadNodes = useCallback(() => {
    fetchNodes({ page: 1, pageSize: 50 })
  }, [fetchNodes])

  useEffect(() => {
    loadNodes()
  }, [loadNodes])

  // 自动刷新
  useEffect(() => {
    if (!autoRefresh) return
    const timer = setInterval(loadNodes, AUTO_REFRESH_INTERVAL)
    return () => clearInterval(timer)
  }, [autoRefresh, loadNodes])

  // 选中节点时加载详情
  useEffect(() => {
    if (selectedNodeId) {
      fetchNodeDetail(selectedNodeId)
    }
  }, [selectedNodeId, fetchNodeDetail])

  // 汇总统计
  const summary = useMemo(() => {
    const online = nodes.filter((n) => n.status === 'ONLINE').length
    const offline = nodes.filter((n) => n.status === 'OFFLINE').length
    const degraded = nodes.filter((n) => n.status === 'DEGRADED').length
    const avgCpu = nodes.length > 0
      ? nodes.reduce((sum, n) => sum + n.cpu.usagePercent, 0) / nodes.length
      : 0
    const avgMem = nodes.length > 0
      ? nodes.reduce((sum, n) => sum + n.memory.usagePercent, 0) / nodes.length
      : 0
    return { online, offline, degraded, avgCpu, avgMem }
  }, [nodes])

  // 表格列定义
  const columns: ColumnsType<NodeMetrics> = [
    {
      title: '节点',
      dataIndex: 'nodeName',
      key: 'nodeName',
      width: 180,
      render: (name: string, record: NodeMetrics) => (
        <Space>
          <Badge status={record.status === 'ONLINE' ? 'success' : record.status === 'DEGRADED' ? 'warning' : 'error'} />
          <a onClick={() => setSelectedNodeId(record.nodeId)}>{name}</a>
        </Space>
      ),
    },
    {
      title: 'IP 地址',
      dataIndex: 'ipAddress',
      key: 'ipAddress',
      width: 140,
      render: (ip: string) => <Text code>{ip}</Text>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status: string) => {
        const cfg = statusMap[status] || { color: 'default', icon: null, text: status }
        return <Tag color={cfg.color} icon={cfg.icon}>{cfg.text}</Tag>
      },
    },
    {
      title: 'CPU',
      key: 'cpu',
      width: 160,
      sorter: (a: NodeMetrics, b: NodeMetrics) => a.cpu.usagePercent - b.cpu.usagePercent,
      render: (_: unknown, record: NodeMetrics) => (
        <Space direction="vertical" size={0} style={{ width: '100%' }}>
          <Text style={{ fontSize: 12 }}>{record.cpu.usagePercent.toFixed(1)}% ({record.cpu.cores} 核)</Text>
          <Progress
            percent={record.cpu.usagePercent}
            size="small"
            strokeColor={record.cpu.usagePercent > 80 ? '#ff4d4f' : record.cpu.usagePercent > 60 ? '#faad14' : '#52c41a'}
            showInfo={false}
          />
        </Space>
      ),
    },
    {
      title: '内存',
      key: 'memory',
      width: 160,
      sorter: (a: NodeMetrics, b: NodeMetrics) => a.memory.usagePercent - b.memory.usagePercent,
      render: (_: unknown, record: NodeMetrics) => (
        <Space direction="vertical" size={0} style={{ width: '100%' }}>
          <Text style={{ fontSize: 12 }}>
            {record.memory.usagePercent.toFixed(1)}% ({formatBytes(record.memory.usedBytes)}/{formatBytes(record.memory.totalBytes)})
          </Text>
          <Progress
            percent={record.memory.usagePercent}
            size="small"
            strokeColor={record.memory.usagePercent > 80 ? '#ff4d4f' : record.memory.usagePercent > 60 ? '#faad14' : '#52c41a'}
            showInfo={false}
          />
        </Space>
      ),
    },
    {
      title: '磁盘',
      key: 'disk',
      width: 160,
      render: (_: unknown, record: NodeMetrics) => {
        const maxUsage = record.disk.partitions.length > 0
          ? Math.max(...record.disk.partitions.map((p) => p.usagePercent))
          : 0
        const maxPart = record.disk.partitions.length > 0
          ? record.disk.partitions.reduce((a, b) => (a.usagePercent > b.usagePercent ? a : b))
          : null
        return (
          <Space direction="vertical" size={0} style={{ width: '100%' }}>
            <Text style={{ fontSize: 12 }}>
              {maxPart ? `${maxPart.mountPoint} ${maxUsage.toFixed(1)}%` : 'N/A'}
            </Text>
            <Progress
              percent={maxUsage}
              size="small"
              strokeColor={maxUsage > 80 ? '#ff4d4f' : maxUsage > 60 ? '#faad14' : '#52c41a'}
              showInfo={false}
            />
          </Space>
        )
      },
    },
    {
      title: '运行时间',
      dataIndex: 'uptime',
      key: 'uptime',
      width: 120,
      render: (uptime: number) => formatUptime(uptime),
    },
    {
      title: '操作系统',
      dataIndex: 'osInfo',
      key: 'osInfo',
      width: 180,
      ellipsis: true,
    },
  ]

  return (
    <div>
      {/* 头部操作栏 */}
      <Space style={{ marginBottom: 16, width: '100%', justifyContent: 'space-between' }}>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={loadNodes} loading={loading}>
            刷新
          </Button>
          <Segmented
            options={[
              { label: '自动刷新', value: true },
              { label: '手动刷新', value: false },
            ]}
            value={autoRefresh}
            onChange={(val) => setAutoRefresh(val as boolean)}
          />
        </Space>
        <Space>
          <Text type="secondary">共 {nodesTotal} 个节点</Text>
        </Space>
      </Space>

      {/* 汇总统计卡片 */}
      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={24} sm={12} md={4}>
          <Card size="small">
            <Statistic
              title="在线节点"
              value={summary.online}
              suffix={`/ ${nodes.length}`}
              valueStyle={{ color: '#3f8600' }}
              prefix={<CheckCircleOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={4}>
          <Card size="small">
            <Statistic
              title="离线节点"
              value={summary.offline}
              valueStyle={{ color: summary.offline > 0 ? '#cf1322' : undefined }}
              prefix={<MinusCircleOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={4}>
          <Card size="small">
            <Statistic
              title="降级节点"
              value={summary.degraded}
              valueStyle={{ color: summary.degraded > 0 ? '#d48806' : undefined }}
              prefix={<WarningOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={4}>
          <Card size="small">
            <Statistic
              title="平均 CPU"
              value={summary.avgCpu}
              precision={1}
              suffix="%"
              valueStyle={{ color: summary.avgCpu > 80 ? '#cf1322' : summary.avgCpu > 60 ? '#d48806' : '#3f8600' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={4}>
          <Card size="small">
            <Statistic
              title="平均内存"
              value={summary.avgMem}
              precision={1}
              suffix="%"
              valueStyle={{ color: summary.avgMem > 80 ? '#cf1322' : summary.avgMem > 60 ? '#d48806' : '#3f8600' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={4}>
          <Card size="small">
            <Tooltip title="前往 Grafana 查看完整仪表盘">
              <Statistic
                title="Grafana"
                value="仪表盘"
                prefix={<DashboardOutlined />}
                valueStyle={{ fontSize: 16 }}
              />
            </Tooltip>
          </Card>
        </Col>
      </Row>

      {/* 错误提示 */}
      {error && (
        <Alert message={error} type="error" showIcon closable style={{ marginBottom: 16 }} />
      )}

      {/* 节点详情（选中节点时展示） */}
      {selectedNodeId && currentNode && (
        <Card
          title={
            <Space>
              <CloudServerOutlined />
              <span>{currentNode.nodeName} 详情</span>
              <Tag color={statusMap[currentNode.status]?.color}>
                {statusMap[currentNode.status]?.text}
              </Tag>
            </Space>
          }
          extra={<Button size="small" onClick={() => setSelectedNodeId(null)}>关闭</Button>}
          style={{ marginBottom: 16 }}
        >
          <Tabs activeKey={detailTab} onChange={setDetailTab} items={[
            {
              key: 'overview',
              label: '概览',
              children: (
                <Descriptions bordered size="small" column={{ xs: 1, sm: 2, md: 3 }}>
                  <Descriptions.Item label="主机名">{currentNode.hostname}</Descriptions.Item>
                  <Descriptions.Item label="IP 地址">{currentNode.ipAddress}</Descriptions.Item>
                  <Descriptions.Item label="操作系统">{currentNode.osInfo}</Descriptions.Item>
                  <Descriptions.Item label="CPU 核心数">{currentNode.cpu.cores} 核</Descriptions.Item>
                  <Descriptions.Item label="CPU 使用率">{currentNode.cpu.usagePercent.toFixed(1)}%</Descriptions.Item>
                  <Descriptions.Item label="Load Average">
                    {currentNode.cpu.loadAverage1m.toFixed(2)} / {currentNode.cpu.loadAverage5m.toFixed(2)} / {currentNode.cpu.loadAverage15m.toFixed(2)}
                  </Descriptions.Item>
                  <Descriptions.Item label="内存总量">{formatBytes(currentNode.memory.totalBytes)}</Descriptions.Item>
                  <Descriptions.Item label="内存已用">{formatBytes(currentNode.memory.usedBytes)}</Descriptions.Item>
                  <Descriptions.Item label="内存使用率">{currentNode.memory.usagePercent.toFixed(1)}%</Descriptions.Item>
                  <Descriptions.Item label="Swap 总量">{formatBytes(currentNode.memory.swapTotalBytes)}</Descriptions.Item>
                  <Descriptions.Item label="Swap 已用">{formatBytes(currentNode.memory.swapUsedBytes)}</Descriptions.Item>
                  <Descriptions.Item label="运行时间">{formatUptime(currentNode.uptime)}</Descriptions.Item>
                  <Descriptions.Item label="最后心跳">{currentNode.lastHeartbeat}</Descriptions.Item>
                </Descriptions>
              ),
            },
            {
              key: 'disk',
              label: '磁盘',
              children: (
                <Table
                  dataSource={currentNode.disk.partitions}
                  rowKey="mountPoint"
                  pagination={false}
                  size="small"
                  columns={[
                    { title: '挂载点', dataIndex: 'mountPoint', key: 'mountPoint' },
                    { title: '设备', dataIndex: 'device', key: 'device' },
                    { title: '文件系统', dataIndex: 'filesystem', key: 'filesystem' },
                    { title: '总容量', dataIndex: 'totalBytes', key: 'totalBytes', render: (v: number) => formatBytes(v) },
                    { title: '已用', dataIndex: 'usedBytes', key: 'usedBytes', render: (v: number) => formatBytes(v) },
                    { title: '可用', dataIndex: 'availableBytes', key: 'availableBytes', render: (v: number) => formatBytes(v) },
                    {
                      title: '使用率', dataIndex: 'usagePercent', key: 'usagePercent',
                      render: (v: number) => (
                        <Progress percent={v} size="small" strokeColor={v > 80 ? '#ff4d4f' : v > 60 ? '#faad14' : '#52c41a'} />
                      ),
                    },
                  ]}
                />
              ),
            },
            {
              key: 'network',
              label: '网络',
              children: (
                <Table
                  dataSource={currentNode.network.interfaces}
                  rowKey="name"
                  pagination={false}
                  size="small"
                  columns={[
                    { title: '接口', dataIndex: 'name', key: 'name' },
                    { title: 'MAC', dataIndex: 'macAddress', key: 'macAddress', render: (v: string) => <Text code>{v}</Text> },
                    { title: '接收', dataIndex: 'rxBytes', key: 'rxBytes', render: (v: number) => formatBytes(v) },
                    { title: '发送', dataIndex: 'txBytes', key: 'txBytes', render: (v: number) => formatBytes(v) },
                    { title: 'RX 包', dataIndex: 'rxPackets', key: 'rxPackets', render: (v: number) => v.toLocaleString() },
                    { title: 'TX 包', dataIndex: 'txPackets', key: 'txPackets', render: (v: number) => v.toLocaleString() },
                    { title: 'RX 错误', dataIndex: 'rxErrors', key: 'rxErrors', render: (v: number) => v > 0 ? <Tag color="red">{v}</Tag> : <Tag color="green">0</Tag> },
                    { title: 'TX 错误', dataIndex: 'txErrors', key: 'txErrors', render: (v: number) => v > 0 ? <Tag color="red">{v}</Tag> : <Tag color="green">0</Tag> },
                  ]}
                />
              ),
            },
          ]} />
        </Card>
      )}

      {/* 节点列表 */}
      <Card title="节点列表" extra={
        <Space>
          <Select
            placeholder="筛选状态"
            allowClear
            style={{ width: 120 }}
            options={[
              { label: '在线', value: 'ONLINE' },
              { label: '离线', value: 'OFFLINE' },
              { label: '维护中', value: 'MAINTENANCE' },
              { label: '降级', value: 'DEGRADED' },
            ]}
          />
        </Space>
      }>
        <Spin spinning={loading}>
          {nodes.length === 0 && !loading ? (
            <Empty description="暂无节点数据" />
          ) : (
            <Table
              dataSource={nodes}
              columns={columns}
              rowKey="nodeId"
              pagination={{ pageSize: 20, showSizeChanger: true, showTotal: (total) => `共 ${total} 个节点` }}
              size="middle"
              scroll={{ x: 1000 }}
            />
          )}
        </Spin>
      </Card>
    </div>
  )
}