// ============================================================
// Storage 管理页面
// 存储池与存储卷管理
// ============================================================
import { useEffect, useState, useCallback, useMemo } from 'react'
import {
  Card, Table, Tag, Space, Button, Modal, Input, Select, Form, InputNumber,
  Progress, Spin, Alert, Empty, Tabs, Typography, Popconfirm, message, Statistic, Row, Col, Descriptions,
} from 'antd'
import {
  ReloadOutlined, PlusOutlined, DeleteOutlined, HddOutlined,
  DatabaseOutlined, CheckCircleOutlined, WarningOutlined, CloseCircleOutlined, ToolOutlined,
} from '@ant-design/icons'
import { useOpsMonitorStore } from '@/stores/opsMonitorStore'
import type { StoragePool, StorageVolume } from '@/api/opsMonitor'
import type { ColumnsType } from 'antd/es/table'

const { Text } = Typography

function formatBytes(bytes: number): string {
  if (!bytes || bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB', 'PB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return `${(bytes / Math.pow(k, i)).toFixed(1)} ${sizes[i]}`
}

const poolStatusMap: Record<string, { color: string; icon: React.ReactNode }> = {
  HEALTHY: { color: 'green', icon: <CheckCircleOutlined /> },
  DEGRADED: { color: 'orange', icon: <WarningOutlined /> },
  ERROR: { color: 'red', icon: <CloseCircleOutlined /> },
  MAINTENANCE: { color: 'blue', icon: <ToolOutlined /> },
}

const volumeStatusMap: Record<string, { color: string }> = {
  AVAILABLE: { color: 'green' },
  IN_USE: { color: 'blue' },
  ERROR: { color: 'red' },
  DELETING: { color: 'orange' },
}

export default function StorageManagePage() {
  const {
    pools, poolsTotal, volumes, volumesTotal, loading, error,
    fetchPools, fetchVolumes, doCreateVolume, doDeleteVolume,
  } = useOpsMonitorStore()

  const [activeTab, setActiveTab] = useState('pools')
  const [createModalVisible, setCreateModalVisible] = useState(false)
  const [detailPool, setDetailPool] = useState<StoragePool | null>(null)
  const [form] = Form.useForm()

  const loadPools = useCallback(() => {
    fetchPools({ page: 1, pageSize: 50 })
  }, [fetchPools])

  const loadVolumes = useCallback(() => {
    fetchVolumes({ page: 1, pageSize: 50 })
  }, [fetchVolumes])

  useEffect(() => { loadPools() }, [loadPools])
  useEffect(() => {
    if (activeTab === 'volumes') loadVolumes()
  }, [activeTab, loadVolumes])

  const handleCreateVolume = async () => {
    try {
      const values = await form.validateFields()
      const success = await doCreateVolume(values)
      if (success) {
        message.success('存储卷创建成功')
        setCreateModalVisible(false)
        form.resetFields()
        loadVolumes()
      } else {
        message.error('创建失败')
      }
    } catch {
      // 表单校验失败
    }
  }

  const handleDeleteVolume = async (volumeId: string) => {
    const success = await doDeleteVolume(volumeId)
    if (success) message.success('存储卷已删除')
    else message.error('删除失败')
  }

  const summary = useMemo(() => {
    const healthy = pools.filter((p) => p.status === 'HEALTHY').length
    const degraded = pools.filter((p) => p.status === 'DEGRADED').length
    const errorPools = pools.filter((p) => p.status === 'ERROR').length
    const totalBytes = pools.reduce((s, p) => s + p.totalBytes, 0)
    const usedBytes = pools.reduce((s, p) => s + p.usedBytes, 0)
    return { healthy, degraded, errorPools, totalBytes, usedBytes, usagePercent: totalBytes > 0 ? (usedBytes / totalBytes) * 100 : 0 }
  }, [pools])

  const poolColumns: ColumnsType<StoragePool> = [
    { title: '名称', dataIndex: 'name', key: 'name', width: 150, render: (n: string, r: StoragePool) => <a onClick={() => setDetailPool(r)}>{n}</a> },
    { title: '类型', dataIndex: 'type', key: 'type', width: 100, render: (t: string) => <Tag>{t}</Tag> },
    {
      title: '状态', dataIndex: 'status', key: 'status', width: 100,
      render: (s: string) => {
        const cfg = poolStatusMap[s] || { color: 'default', icon: null }
        return <Tag color={cfg.color} icon={cfg.icon}>{s}</Tag>
      },
    },
    { title: '总容量', dataIndex: 'totalBytes', key: 'totalBytes', width: 110, render: (v: number) => formatBytes(v) },
    { title: '已用', dataIndex: 'usedBytes', key: 'usedBytes', width: 110, render: (v: number) => formatBytes(v) },
    {
      title: '使用率', key: 'usage', width: 150,
      render: (_: unknown, r: StoragePool) => (
        <Progress percent={r.usagePercent} size="small" strokeColor={r.usagePercent > 80 ? '#ff4d4f' : r.usagePercent > 60 ? '#faad14' : '#52c41a'} />
      ),
    },
    { title: '节点数', dataIndex: 'nodeCount', key: 'nodeCount', width: 80 },
    { title: '副本', dataIndex: 'replicationFactor', key: 'replicationFactor', width: 60 },
    { title: 'IOPS', dataIndex: 'iops', key: 'iops', width: 80, render: (v: number) => v.toLocaleString() },
    { title: '吞吐量', dataIndex: 'throughput', key: 'throughput', width: 100, render: (v: number) => `${formatBytes(v)}/s` },
    { title: '延迟', dataIndex: 'latency', key: 'latency', width: 80, render: (v: number) => `${v.toFixed(1)}ms` },
  ]

  const volumeColumns: ColumnsType<StorageVolume> = [
    { title: '名称', dataIndex: 'name', key: 'name', width: 150 },
    { title: '存储池', dataIndex: 'poolName', key: 'poolName', width: 120 },
    { title: '大小', dataIndex: 'size', key: 'size', width: 100, render: (v: number) => formatBytes(v) },
    { title: '已用', dataIndex: 'used', key: 'used', width: 100, render: (v: number) => formatBytes(v) },
    {
      title: '状态', dataIndex: 'status', key: 'status', width: 100,
      render: (s: string) => <Tag color={volumeStatusMap[s]?.color || 'default'}>{s}</Tag>,
    },
    { title: '访问模式', dataIndex: 'accessMode', key: 'accessMode', width: 100 },
    {
      title: '挂载到', dataIndex: 'attachedTo', key: 'attachedTo', width: 150,
      render: (v: string[]) => v.length === 0 ? '-' : v.map((n) => <Tag key={n}>{n}</Tag>),
    },
    { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 160 },
    {
      title: '操作', key: 'actions', width: 100,
      render: (_: unknown, r: StorageVolume) => (
        <Popconfirm title="确定删除此存储卷？" onConfirm={() => handleDeleteVolume(r.volumeId)}>
          <Button size="small" danger icon={<DeleteOutlined />}>删除</Button>
        </Popconfirm>
      ),
    },
  ]

  return (
    <div>
      {/* 汇总卡片 */}
      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="存储池总数" value={poolsTotal} prefix={<DatabaseOutlined />} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="健康" value={summary.healthy} valueStyle={{ color: '#3f8600' }} prefix={<CheckCircleOutlined />} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="降级" value={summary.degraded} valueStyle={{ color: summary.degraded > 0 ? '#d48806' : '#3f8600' }} prefix={<WarningOutlined />} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="总容量" value={formatBytes(summary.totalBytes)} prefix={<HddOutlined />} /></Card>
        </Col>
      </Row>
      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col span={24}>
          <Card size="small">
            <Space direction="vertical" style={{ width: '100%' }}>
              <Text>存储总使用率</Text>
              <Progress percent={summary.usagePercent} strokeColor={summary.usagePercent > 80 ? '#ff4d4f' : summary.usagePercent > 60 ? '#faad14' : '#52c41a'} />
              <Text type="secondary">{formatBytes(summary.usedBytes)} / {formatBytes(summary.totalBytes)}</Text>
            </Space>
          </Card>
        </Col>
      </Row>

      {error && <Alert message={error} type="error" showIcon closable style={{ marginBottom: 16 }} />}

      {/* 详情弹窗 */}
      {detailPool && (
        <Card title={`存储池: ${detailPool.name}`} style={{ marginBottom: 16 }} extra={<Button size="small" onClick={() => setDetailPool(null)}>关闭</Button>}>
          <Descriptions bordered size="small" column={{ xs: 1, sm: 2, md: 3 }}>
            <Descriptions.Item label="名称">{detailPool.name}</Descriptions.Item>
            <Descriptions.Item label="类型"><Tag>{detailPool.type}</Tag></Descriptions.Item>
            <Descriptions.Item label="状态"><Tag color={poolStatusMap[detailPool.status]?.color}>{detailPool.status}</Tag></Descriptions.Item>
            <Descriptions.Item label="总容量">{formatBytes(detailPool.totalBytes)}</Descriptions.Item>
            <Descriptions.Item label="已用">{formatBytes(detailPool.usedBytes)}</Descriptions.Item>
            <Descriptions.Item label="使用率">{detailPool.usagePercent.toFixed(1)}%</Descriptions.Item>
            <Descriptions.Item label="节点数">{detailPool.nodeCount}</Descriptions.Item>
            <Descriptions.Item label="副本数">{detailPool.replicationFactor}</Descriptions.Item>
            <Descriptions.Item label="IOPS">{detailPool.iops.toLocaleString()}</Descriptions.Item>
            <Descriptions.Item label="吞吐量">{formatBytes(detailPool.throughput)}/s</Descriptions.Item>
            <Descriptions.Item label="延迟">{detailPool.latency.toFixed(1)}ms</Descriptions.Item>
            <Descriptions.Item label="节点" span={2}>
              {detailPool.nodes.map((n) => <Tag key={n}>{n}</Tag>)}
            </Descriptions.Item>
          </Descriptions>
        </Card>
      )}

      <Tabs activeKey={activeTab} onChange={setActiveTab} items={[
        {
          key: 'pools',
          label: `存储池 (${poolsTotal})`,
          children: (
            <Card extra={<Button icon={<ReloadOutlined />} onClick={loadPools}>刷新</Button>}>
              <Spin spinning={loading}>
                {pools.length === 0 && !loading ? (
                  <Empty description="暂无存储池" />
                ) : (
                  <Table dataSource={pools} columns={poolColumns} rowKey="poolId" pagination={{ pageSize: 20, showSizeChanger: true }} size="middle" scroll={{ x: 1200 }} />
                )}
              </Spin>
            </Card>
          ),
        },
        {
          key: 'volumes',
          label: `存储卷 (${volumesTotal})`,
          children: (
            <Card
              extra={
                <Space>
                  <Button icon={<PlusOutlined />} type="primary" onClick={() => setCreateModalVisible(true)}>创建卷</Button>
                  <Button icon={<ReloadOutlined />} onClick={loadVolumes}>刷新</Button>
                </Space>
              }
            >
              <Spin spinning={loading}>
                {volumes.length === 0 && !loading ? (
                  <Empty description="暂无存储卷" />
                ) : (
                  <Table dataSource={volumes} columns={volumeColumns} rowKey="volumeId" pagination={{ pageSize: 20, showSizeChanger: true }} size="middle" />
                )}
              </Spin>
            </Card>
          ),
        },
      ]} />

      {/* 创建存储卷弹窗 */}
      <Modal title="创建存储卷" open={createModalVisible} onOk={handleCreateVolume} onCancel={() => { setCreateModalVisible(false); form.resetFields() }} destroyOnClose>
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入名称' }]}>
            <Input placeholder="volume-name" />
          </Form.Item>
          <Form.Item name="poolId" label="存储池" rules={[{ required: true, message: '请选择存储池' }]}>
            <Select placeholder="选择存储池" options={pools.map((p) => ({ label: p.name, value: p.poolId }))} />
          </Form.Item>
          <Form.Item name="size" label="大小 (GB)" rules={[{ required: true, message: '请输入大小' }]}>
            <InputNumber min={1} max={10240} style={{ width: '100%' }} placeholder="10" />
          </Form.Item>
          <Form.Item name="accessMode" label="访问模式" initialValue="RWO">
            <Select options={[
              { label: 'ReadWriteOnce (RWO)', value: 'RWO' },
              { label: 'ReadOnlyMany (ROX)', value: 'ROX' },
              { label: 'ReadWriteMany (RWX)', value: 'RWX' },
            ]} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}