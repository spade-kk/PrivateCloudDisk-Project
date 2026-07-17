// ============================================================
// RabbitMQ 管理页面
// 概览、队列、交换机、连接、节点、控制台集成
// ============================================================
import { useEffect, useState, useCallback, useMemo } from 'react'
import {
  Card, Table, Tag, Space, Button, Modal, Input, Select, Form,
  Spin, Alert, Empty, Tabs, Typography, Popconfirm, message, Statistic, Row, Col, Descriptions, Tooltip, Badge,
} from 'antd'
import {
  ReloadOutlined, PlusOutlined, DeleteOutlined,
  LinkOutlined, SearchOutlined, ApiOutlined,
  CheckCircleOutlined, WarningOutlined, CloseCircleOutlined, SyncOutlined,
  SendOutlined, InboxOutlined, NodeIndexOutlined, SwapOutlined,
} from '@ant-design/icons'
import { useMiddlewareStore } from '@/stores/middlewareStore'
import type { RabbitMQQueue, RabbitMQExchange, RabbitMQConnection, RabbitMQNode } from '@/api/middleware'
import type { ColumnsType } from 'antd/es/table'

const { Text, Paragraph } = Typography

function formatBytes(bytes: number): string {
  if (!bytes || bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return `${(bytes / Math.pow(k, i)).toFixed(1)} ${sizes[i]}`
}

const queueStateMap: Record<string, { color: string; icon: React.ReactNode }> = {
  RUNNING: { color: 'green', icon: <CheckCircleOutlined /> },
  IDLE: { color: 'blue', icon: <SyncOutlined /> },
  FLOW: { color: 'orange', icon: <WarningOutlined /> },
  DOWN: { color: 'red', icon: <CloseCircleOutlined /> },
}

const nodeStatusMap: Record<string, { color: string }> = {
  RUNNING: { color: 'green' },
  DOWN: { color: 'red' },
  MAINTENANCE: { color: 'orange' },
}

export default function RabbitMQManagePage() {
  const {
    rabbitmqOverview, rabbitmqQueues, rabbitmqQueuesTotal,
    rabbitmqExchanges, rabbitmqConnections, rabbitmqNodes,
    rabbitmqConsoleUrl, loading, error,
    fetchRabbitMQOverview, fetchRabbitMQQueues, fetchRabbitMQExchanges,
    fetchRabbitMQConnections, fetchRabbitMQNodes,
    doPurgeRabbitMQQueue, doDeleteRabbitMQQueue, fetchRabbitMQConsoleUrl,
  } = useMiddlewareStore()

  const [activeTab, setActiveTab] = useState('overview')
  const [searchQueue, setSearchQueue] = useState('')
  const [vhostFilter, setVhostFilter] = useState<string | undefined>()

  const loadOverview = useCallback(() => { fetchRabbitMQOverview() }, [fetchRabbitMQOverview])
  const loadQueues = useCallback(() => {
    fetchRabbitMQQueues({ page: 1, pageSize: 50, name: searchQueue || undefined, vhost: vhostFilter })
  }, [fetchRabbitMQQueues, searchQueue, vhostFilter])
  const loadExchanges = useCallback(() => { fetchRabbitMQExchanges({ page: 1, pageSize: 50 }) }, [fetchRabbitMQExchanges])
  const loadConnections = useCallback(() => { fetchRabbitMQConnections() }, [fetchRabbitMQConnections])
  const loadNodes = useCallback(() => { fetchRabbitMQNodes() }, [fetchRabbitMQNodes])

  useEffect(() => { loadOverview() }, [loadOverview])
  useEffect(() => {
    if (activeTab === 'queues') loadQueues()
    else if (activeTab === 'exchanges') loadExchanges()
    else if (activeTab === 'connections') loadConnections()
    else if (activeTab === 'nodes') loadNodes()
  }, [activeTab, loadQueues, loadExchanges, loadConnections, loadNodes])

  const handlePurgeQueue = async (queueName: string, vhost: string) => {
    const success = await doPurgeRabbitMQQueue(queueName, vhost)
    if (success) { message.success('队列已清空'); loadQueues() }
    else message.error('清空失败')
  }

  const handleDeleteQueue = async (queueName: string, vhost: string) => {
    const success = await doDeleteRabbitMQQueue(queueName, vhost)
    if (success) { message.success('队列已删除'); loadQueues() }
    else message.error('删除失败')
  }

  const handleOpenConsole = async () => {
    await fetchRabbitMQConsoleUrl()
    if (rabbitmqConsoleUrl) window.open(rabbitmqConsoleUrl, '_blank')
  }

  const queueColumns: ColumnsType<RabbitMQQueue> = [
    { title: '名称', dataIndex: 'name', key: 'name', width: 200, ellipsis: true },
    { title: 'VHost', dataIndex: 'vhost', key: 'vhost', width: 80, render: (v: string) => <Tag>{v}</Tag> },
    { title: '节点', dataIndex: 'node', key: 'node', width: 150, ellipsis: true },
    {
      title: '状态', dataIndex: 'state', key: 'state', width: 90,
      render: (s: string) => <Tag color={queueStateMap[s]?.color} icon={queueStateMap[s]?.icon}>{s}</Tag>,
    },
    { title: 'Ready', dataIndex: 'ready', key: 'ready', width: 70 },
    { title: 'Unacked', dataIndex: 'unacknowledged', key: 'unacknowledged', width: 80 },
    { title: 'Total', dataIndex: 'total', key: 'total', width: 70 },
    { title: '消息速率', dataIndex: 'messageRate', key: 'messageRate', width: 90, render: (v: number) => `${v.toFixed(1)}/s` },
    { title: '消费者', dataIndex: 'consumerCount', key: 'consumerCount', width: 80 },
    { title: '消费者利用率', dataIndex: 'consumerUtilisation', key: 'consumerUtilisation', width: 110, render: (v: number) => `${(v * 100).toFixed(1)}%` },
    { title: '内存', dataIndex: 'memory', key: 'memory', width: 90, render: (v: number) => formatBytes(v) },
    {
      title: '操作', key: 'actions', width: 140,
      render: (_: unknown, r: RabbitMQQueue) => (
        <Space size="small">
          <Popconfirm title="确定清空此队列？" onConfirm={() => handlePurgeQueue(r.name, r.vhost)}>
            <Button size="small">清空</Button>
          </Popconfirm>
          <Popconfirm title="确定删除此队列？" onConfirm={() => handleDeleteQueue(r.name, r.vhost)}>
            <Button size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ]

  const exchangeColumns: ColumnsType<RabbitMQExchange> = [
    { title: '名称', dataIndex: 'name', key: 'name', width: 200, ellipsis: true },
    { title: 'VHost', dataIndex: 'vhost', key: 'vhost', width: 80, render: (v: string) => <Tag>{v}</Tag> },
    { title: '类型', dataIndex: 'type', key: 'type', width: 100, render: (t: string) => <Tag color="blue">{t}</Tag> },
    { title: '持久化', dataIndex: 'durable', key: 'durable', width: 80, render: (v: boolean) => v ? <CheckCircleOutlined style={{ color: '#52c41a' }} /> : <CloseCircleOutlined style={{ color: '#ff4d4f' }} /> },
    { title: '自动删除', dataIndex: 'autoDelete', key: 'autoDelete', width: 80, render: (v: boolean) => v ? '是' : '否' },
    { title: '内部', dataIndex: 'internal', key: 'internal', width: 60, render: (v: boolean) => v ? '是' : '否' },
    { title: '绑定数', dataIndex: 'bindings', key: 'bindings', width: 80 },
    { title: '消息速率(入)', dataIndex: 'messageRateIn', key: 'messageRateIn', width: 110, render: (v: number) => `${v.toFixed(1)}/s` },
    { title: '消息速率(出)', dataIndex: 'messageRateOut', key: 'messageRateOut', width: 110, render: (v: number) => `${v.toFixed(1)}/s` },
  ]

  const connectionColumns: ColumnsType<RabbitMQConnection> = [
    { title: '名称', dataIndex: 'name', key: 'name', width: 200, ellipsis: true },
    { title: 'VHost', dataIndex: 'vhost', key: 'vhost', width: 80, render: (v: string) => <Tag>{v}</Tag> },
    { title: '用户', dataIndex: 'user', key: 'user', width: 100 },
    { title: '状态', dataIndex: 'state', key: 'state', width: 90, render: (s: string) => <Badge status={s === 'RUNNING' ? 'success' : 'error'} text={s} /> },
    { title: '协议', dataIndex: 'protocol', key: 'protocol', width: 80 },
    { title: '客户端', dataIndex: 'client', key: 'client', width: 200, ellipsis: true },
    { title: 'Peer Host', dataIndex: 'peerHost', key: 'peerHost', width: 130, render: (h: string) => <Text code>{h}</Text> },
    { title: 'Peer Port', dataIndex: 'peerPort', key: 'peerPort', width: 80 },
    { title: 'Channel 数', dataIndex: 'channels', key: 'channels', width: 90 },
    { title: '超时', dataIndex: 'timeout', key: 'timeout', width: 70, render: (v: number) => `${v}s` },
  ]

  const nodeColumns: ColumnsType<RabbitMQNode> = [
    { title: '名称', dataIndex: 'name', key: 'name', width: 200, ellipsis: true },
    { title: '类型', dataIndex: 'type', key: 'type', width: 80, render: (t: string) => <Tag>{t}</Tag> },
    { title: '状态', dataIndex: 'status', key: 'status', width: 100, render: (s: string) => <Tag color={nodeStatusMap[s]?.color}>{s}</Tag> },
    { title: '运行中', dataIndex: 'running', key: 'running', width: 80, render: (v: boolean) => v ? <CheckCircleOutlined style={{ color: '#52c41a' }} /> : <CloseCircleOutlined style={{ color: '#ff4d4f' }} /> },
    { title: '进程数', dataIndex: 'procUsed', key: 'procUsed', width: 80 },
    { title: '内存使用', dataIndex: 'memUsed', key: 'memUsed', width: 100, render: (v: number) => formatBytes(v) },
    { title: '内存限制', dataIndex: 'memLimit', key: 'memLimit', width: 100, render: (v: number) => formatBytes(v) },
    { title: 'FD 使用', dataIndex: 'fdUsed', key: 'fdUsed', width: 80 },
    { title: 'Socket 使用', dataIndex: 'socketsUsed', key: 'socketsUsed', width: 90 },
    { title: 'Erlang 进程', dataIndex: 'procTotal', key: 'procTotal', width: 90 },
    { title: '运行时间', dataIndex: 'uptime', key: 'uptime', width: 120, render: (v: number) => `${Math.floor(v / 3600000)}h ${Math.floor((v % 3600000) / 60000)}m` },
  ]

  return (
    <div>
      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="队列数" value={rabbitmqQueuesTotal} prefix={<InboxOutlined />} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="交换机数" value={rabbitmqExchanges.length} prefix={<SwapOutlined />} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="连接数" value={rabbitmqConnections.length} prefix={<ApiOutlined />} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small">
            <Tooltip title="打开 RabbitMQ 控制台">
              <Statistic title="控制台" value="打开" prefix={<LinkOutlined />} valueStyle={{ fontSize: 16, cursor: 'pointer' }} onClick={handleOpenConsole} />
            </Tooltip>
          </Card>
        </Col>
      </Row>

      {error && <Alert message={error} type="error" showIcon closable style={{ marginBottom: 16 }} />}

      <Tabs activeKey={activeTab} onChange={setActiveTab} items={[
        {
          key: 'overview',
          label: '概览',
          children: (
            <Card extra={<Button icon={<ReloadOutlined />} onClick={loadOverview}>刷新</Button>}>
              <Spin spinning={loading}>
                {rabbitmqOverview ? (
                  <Descriptions bordered size="small" column={{ xs: 1, sm: 2, md: 3 }}>
                    <Descriptions.Item label="版本">{rabbitmqOverview.version}</Descriptions.Item>
                    <Descriptions.Item label="集群名称">{rabbitmqOverview.clusterName}</Descriptions.Item>
                    <Descriptions.Item label="节点数">{rabbitmqOverview.nodeCount}</Descriptions.Item>
                    <Descriptions.Item label="队列数">{rabbitmqOverview.queueCount}</Descriptions.Item>
                    <Descriptions.Item label="消息总数">{rabbitmqOverview.totalMessages.toLocaleString()}</Descriptions.Item>
                    <Descriptions.Item label="消息速率">{rabbitmqOverview.messageRate.toFixed(1)}/s</Descriptions.Item>
                    <Descriptions.Item label="消费者数">{rabbitmqOverview.consumerCount}</Descriptions.Item>
                    <Descriptions.Item label="连接数">{rabbitmqOverview.connectionCount}</Descriptions.Item>
                    <Descriptions.Item label="Channel 数">{rabbitmqOverview.channelCount}</Descriptions.Item>
                  </Descriptions>
                ) : <Empty description="暂无数据" />}
              </Spin>
            </Card>
          ),
        },
        {
          key: 'queues',
          label: `队列 (${rabbitmqQueuesTotal})`,
          children: (
            <Card
              extra={
                <Space>
                  <Input placeholder="搜索队列" prefix={<SearchOutlined />} allowClear style={{ width: 200 }}
                    value={searchQueue} onChange={(e) => setSearchQueue(e.target.value)} onPressEnter={loadQueues} />
                  <Select placeholder="VHost" allowClear style={{ width: 120 }} value={vhostFilter} onChange={setVhostFilter}
                    options={(() => {
                      const vhosts = [...new Set(rabbitmqQueues.map((q) => q.vhost))]
                      return vhosts.map((v) => ({ label: v, value: v }))
                    })()} />
                  <Button icon={<ReloadOutlined />} onClick={loadQueues}>刷新</Button>
                </Space>
              }
            >
              <Spin spinning={loading}>
                {rabbitmqQueues.length === 0 && !loading ? (
                  <Empty description="暂无队列" />
                ) : (
                  <Table dataSource={rabbitmqQueues} columns={queueColumns} rowKey="name" pagination={{ pageSize: 20, showSizeChanger: true }} size="middle" scroll={{ x: 1300 }} />
                )}
              </Spin>
            </Card>
          ),
        },
        {
          key: 'exchanges',
          label: `交换机 (${rabbitmqExchanges.length})`,
          children: (
            <Card extra={<Button icon={<ReloadOutlined />} onClick={loadExchanges}>刷新</Button>}>
              <Spin spinning={loading}>
                {rabbitmqExchanges.length === 0 && !loading ? (
                  <Empty description="暂无交换机" />
                ) : (
                  <Table dataSource={rabbitmqExchanges} columns={exchangeColumns} rowKey="name" pagination={{ pageSize: 20, showSizeChanger: true }} size="middle" />
                )}
              </Spin>
            </Card>
          ),
        },
        {
          key: 'connections',
          label: `连接 (${rabbitmqConnections.length})`,
          children: (
            <Card extra={<Button icon={<ReloadOutlined />} onClick={loadConnections}>刷新</Button>}>
              <Spin spinning={loading}>
                {rabbitmqConnections.length === 0 && !loading ? (
                  <Empty description="暂无连接" />
                ) : (
                  <Table dataSource={rabbitmqConnections} columns={connectionColumns} rowKey="name" pagination={{ pageSize: 20, showSizeChanger: true }} size="middle" scroll={{ x: 1000 }} />
                )}
              </Spin>
            </Card>
          ),
        },
        {
          key: 'nodes',
          label: `节点 (${rabbitmqNodes.length})`,
          children: (
            <Card extra={<Button icon={<ReloadOutlined />} onClick={loadNodes}>刷新</Button>}>
              <Spin spinning={loading}>
                {rabbitmqNodes.length === 0 && !loading ? (
                  <Empty description="暂无节点" />
                ) : (
                  <Table dataSource={rabbitmqNodes} columns={nodeColumns} rowKey="name" pagination={false} size="middle" scroll={{ x: 1200 }} />
                )}
              </Spin>
            </Card>
          ),
        },
      ]} />
    </div>
  )
}