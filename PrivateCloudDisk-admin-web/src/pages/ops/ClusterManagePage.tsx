// ============================================================
// Cluster 管理页面
// Kubernetes / 集群总览、节点管理、命名空间、资源配额
// ============================================================
import { useEffect, useState, useCallback, useMemo } from 'react'
import {
  Card, Table, Tag, Space, Button, Descriptions, Tabs, Select,
  Progress, Spin, Alert, Empty, Typography, Statistic, Row, Col, Badge, Tooltip,
} from 'antd'
import {
  ReloadOutlined, ClusterOutlined, NodeIndexOutlined, ApartmentOutlined,
  CheckCircleOutlined, WarningOutlined, CloseCircleOutlined, SyncOutlined,
  DeploymentUnitOutlined,
} from '@ant-design/icons'
import { useOpsMonitorStore } from '@/stores/opsMonitorStore'
import type { ClusterOverview, ClusterNode, ClusterPod, ClusterNamespace } from '@/api/opsMonitor'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'

const { Text } = Typography

function formatBytes(bytes: number): string {
  if (!bytes || bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return `${(bytes / Math.pow(k, i)).toFixed(1)} ${sizes[i]}`
}

function formatCpu(cpu: string): string {
  // Kubernetes CPU format: "250m" or "1"
  if (cpu.endsWith('m')) return `${(parseInt(cpu) / 1000).toFixed(2)} 核`
  return `${cpu} 核`
}

const nodeStatusMap: Record<string, { color: string; icon: React.ReactNode }> = {
  Ready: { color: 'green', icon: <CheckCircleOutlined /> },
  NotReady: { color: 'red', icon: <CloseCircleOutlined /> },
  SchedulingDisabled: { color: 'orange', icon: <WarningOutlined /> },
}

const podStatusMap: Record<string, { color: string }> = {
  Running: { color: 'green' },
  Pending: { color: 'orange' },
  Failed: { color: 'red' },
  Succeeded: { color: 'blue' },
  Unknown: { color: 'default' },
  CrashLoopBackOff: { color: 'red' },
  ImagePullBackOff: { color: 'red' },
  ErrImagePull: { color: 'red' },
  Terminating: { color: 'purple' },
}

export default function ClusterManagePage() {
  const {
    clusterOverview, clusterNodes, clusterNodesTotal,
    clusterNamespaces, clusterPods, clusterPodsTotal, loading, error,
    fetchClusterOverview, fetchClusterNodes, fetchClusterNamespaces, fetchClusterPods,
  } = useOpsMonitorStore()

  const [activeTab, setActiveTab] = useState('overview')
  const [namespaceFilter, setNamespaceFilter] = useState<string | undefined>()

  const loadOverview = useCallback(() => { fetchClusterOverview() }, [fetchClusterOverview])
  const loadNodes = useCallback(() => { fetchClusterNodes({ page: 1, pageSize: 50 }) }, [fetchClusterNodes])
  const loadNamespaces = useCallback(() => { fetchClusterNamespaces() }, [fetchClusterNamespaces])
  const loadPods = useCallback(() => {
    fetchClusterPods({ page: 1, pageSize: 50, namespace: namespaceFilter })
  }, [fetchClusterPods, namespaceFilter])

  useEffect(() => { loadOverview() }, [loadOverview])
  useEffect(() => {
    if (activeTab === 'nodes') loadNodes()
    else if (activeTab === 'namespaces') loadNamespaces()
    else if (activeTab === 'pods') loadPods()
  }, [activeTab, loadNodes, loadNamespaces, loadPods])

  const nodeColumns: ColumnsType<ClusterNode> = [
    {
      title: '节点名称', dataIndex: 'name', key: 'name', width: 180,
      render: (n: string, r: ClusterNode) => (
        <Space>
          <Badge status={r.status === 'Ready' ? 'success' : 'error'} />
          <Text strong>{n}</Text>
        </Space>
      ),
    },
    { title: '角色', dataIndex: 'roles', key: 'roles', width: 130, render: (roles: string[]) => roles.map((r) => <Tag key={r} color="blue">{r}</Tag>) },
    { title: '状态', dataIndex: 'status', key: 'status', width: 100, render: (s: string) => {
      const cfg = nodeStatusMap[s] || { color: 'default', icon: null }
      return <Tag color={cfg.color} icon={cfg.icon}>{s}</Tag>
    }},
    { title: 'K8s 版本', dataIndex: 'kubeletVersion', key: 'kubeletVersion', width: 120 },
    { title: 'OS', dataIndex: 'osImage', key: 'osImage', width: 140, ellipsis: true },
    { title: '内核', dataIndex: 'kernelVersion', key: 'kernelVersion', width: 140, ellipsis: true },
    { title: 'CPU 容量', dataIndex: 'cpuCapacity', key: 'cpuCapacity', width: 100, render: (v: string) => formatCpu(v) },
    { title: '内存容量', dataIndex: 'memoryCapacity', key: 'memoryCapacity', width: 110, render: (v: string) => formatBytes(parseInt(v)) },
    { title: 'Pod 容量', dataIndex: 'podCapacity', key: 'podCapacity', width: 90 },
    { title: '内部 IP', dataIndex: 'internalIp', key: 'internalIp', width: 130, render: (ip: string) => <Text code>{ip}</Text> },
    { title: '创建时间', dataIndex: 'creationTimestamp', key: 'creationTimestamp', width: 160 },
  ]

  const podColumns: ColumnsType<ClusterPod> = [
    { title: '名称', dataIndex: 'name', key: 'name', width: 220, ellipsis: true },
    { title: '命名空间', dataIndex: 'namespace', key: 'namespace', width: 120, render: (ns: string) => <Tag>{ns}</Tag> },
    {
      title: '状态', dataIndex: 'phase', key: 'phase', width: 100,
      render: (s: string) => <Tag color={podStatusMap[s]?.color || 'default'}>{s}</Tag>,
    },
    { title: '就绪', dataIndex: 'ready', key: 'ready', width: 90 },
    { title: '重启', dataIndex: 'restarts', key: 'restarts', width: 70, render: (v: number) => v > 5 ? <Tag color="red">{v}</Tag> : v },
    { title: '节点', dataIndex: 'nodeName', key: 'nodeName', width: 150 },
    { title: 'CPU 请求', dataIndex: 'cpuRequest', key: 'cpuRequest', width: 100, render: (v: string) => formatCpu(v) },
    { title: '内存请求', dataIndex: 'memoryRequest', key: 'memoryRequest', width: 110, render: (v: string) => formatBytes(parseInt(v)) },
    { title: 'IP', dataIndex: 'podIp', key: 'podIp', width: 130, render: (ip: string) => <Text code>{ip}</Text> },
    { title: '启动时间', dataIndex: 'startTime', key: 'startTime', width: 160 },
    {
      title: '容器', key: 'containers', width: 180,
      render: (_: unknown, r: ClusterPod) => r.containers.map((c) => (
        <Tag key={c.name} style={{ marginBottom: 2 }}>
          {c.name}
          {c.ready ? <CheckCircleOutlined style={{ color: '#52c41a', marginLeft: 4 }} /> : <CloseCircleOutlined style={{ color: '#ff4d4f', marginLeft: 4 }} />}
        </Tag>
      )),
    },
  ]

  const nsColumns: ColumnsType<ClusterNamespace> = [
    { title: '名称', dataIndex: 'name', key: 'name', width: 200, render: (n: string) => <Text strong>{n}</Text> },
    {
      title: '状态', dataIndex: 'status', key: 'status', width: 100,
      render: (s: string) => <Tag color={s === 'Active' ? 'green' : 'red'}>{s}</Tag>,
    },
    { title: 'Pod 数', dataIndex: 'podCount', key: 'podCount', width: 90 },
    { title: '资源配额', key: 'resourceQuota', width: 300, render: (_: unknown, r: ClusterNamespace) => (
      <Space split="|" size="small">
        <Text style={{ fontSize: 12 }}>CPU: {formatCpu(r.resourceQuota?.cpuLimit || '0')}</Text>
        <Text style={{ fontSize: 12 }}>内存: {formatBytes(parseInt(r.resourceQuota?.memoryLimit || '0'))}</Text>
        <Text style={{ fontSize: 12 }}>PV: {r.resourceQuota?.pvCount || 0}</Text>
      </Space>
    )},
    { title: '创建时间', dataIndex: 'creationTimestamp', key: 'creationTimestamp', width: 160 },
    { title: '标签', dataIndex: 'labels', key: 'labels', width: 200, render: (labels: Record<string, string>) => (
      Object.entries(labels || {}).map(([k, v]) => <Tag key={k} style={{ marginBottom: 2 }}>{k}: {v}</Tag>)
    )},
  ]

  return (
    <div>
      {/* 集群概览 */}
      {clusterOverview && (
        <>
          <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
            <Col xs={12} sm={6}><Card size="small"><Statistic title="集群名称" value={clusterOverview.clusterName} prefix={<ClusterOutlined />} /></Card></Col>
            <Col xs={12} sm={6}><Card size="small"><Statistic title="节点数" value={clusterOverview.nodeCount} prefix={<NodeIndexOutlined />} /></Card></Col>
            <Col xs={12} sm={6}><Card size="small"><Statistic title="命名空间" value={clusterOverview.namespaceCount} prefix={<ApartmentOutlined />} /></Card></Col>
            <Col xs={12} sm={6}><Card size="small"><Statistic title="Pod 总数" value={clusterOverview.podCount} prefix={<DeploymentUnitOutlined />} /></Card></Col>
          </Row>
          <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
            <Col xs={12} sm={6}>
              <Card size="small"><Statistic title="Pod 运行中" value={clusterOverview.podRunning} valueStyle={{ color: '#3f8600' }} /></Card>
            </Col>
            <Col xs={12} sm={6}>
              <Card size="small"><Statistic title="Pod 异常" value={clusterOverview.podFailed} valueStyle={{ color: clusterOverview.podFailed > 0 ? '#cf1322' : '#3f8600' }} /></Card>
            </Col>
            <Col xs={12} sm={6}>
              <Card size="small"><Statistic title="节点健康" value={clusterOverview.healthyNodes} suffix={`/ ${clusterOverview.nodeCount}`} valueStyle={{ color: clusterOverview.healthyNodes < clusterOverview.nodeCount ? '#d48806' : '#3f8600' }} /></Card>
            </Col>
            <Col xs={12} sm={6}>
              <Card size="small"><Statistic title="K8s 版本" value={clusterOverview.version} /></Card>
            </Col>
          </Row>
          <Card size="small" style={{ marginBottom: 16 }}>
            <Space direction="vertical" style={{ width: '100%' }}>
              <Text>CPU 使用率</Text>
              <Progress percent={clusterOverview.cpuUsagePercent} strokeColor={clusterOverview.cpuUsagePercent > 80 ? '#ff4d4f' : clusterOverview.cpuUsagePercent > 60 ? '#faad14' : '#52c41a'} />
              <Text type="secondary">{formatCpu(clusterOverview.cpuUsed)} / {formatCpu(clusterOverview.cpuTotal)}</Text>
            </Space>
          </Card>
          <Card size="small" style={{ marginBottom: 16 }}>
            <Space direction="vertical" style={{ width: '100%' }}>
              <Text>内存使用率</Text>
              <Progress percent={clusterOverview.memoryUsagePercent} strokeColor={clusterOverview.memoryUsagePercent > 80 ? '#ff4d4f' : clusterOverview.memoryUsagePercent > 60 ? '#faad14' : '#52c41a'} />
              <Text type="secondary">{formatBytes(clusterOverview.memoryUsed)} / {formatBytes(clusterOverview.memoryTotal)}</Text>
            </Space>
          </Card>
        </>
      )}

      {error && <Alert message={error} type="error" showIcon closable style={{ marginBottom: 16 }} />}

      <Tabs activeKey={activeTab} onChange={setActiveTab} items={[
        {
          key: 'overview',
          label: '集群概览',
          children: (
            <Card extra={<Button icon={<ReloadOutlined />} onClick={loadOverview}>刷新</Button>}>
              <Spin spinning={loading}>
                {clusterOverview ? (
                  <Descriptions bordered size="small" column={{ xs: 1, sm: 2, md: 3 }}>
                    <Descriptions.Item label="集群名称">{clusterOverview.clusterName}</Descriptions.Item>
                    <Descriptions.Item label="API Server">{clusterOverview.apiServer}</Descriptions.Item>
                    <Descriptions.Item label="版本">{clusterOverview.version}</Descriptions.Item>
                    <Descriptions.Item label="节点数">{clusterOverview.nodeCount}</Descriptions.Item>
                    <Descriptions.Item label="健康节点">{clusterOverview.healthyNodes}</Descriptions.Item>
                    <Descriptions.Item label="命名空间">{clusterOverview.namespaceCount}</Descriptions.Item>
                    <Descriptions.Item label="Pod 总数">{clusterOverview.podCount}</Descriptions.Item>
                    <Descriptions.Item label="运行中">{clusterOverview.podRunning}</Descriptions.Item>
                    <Descriptions.Item label="异常">{clusterOverview.podFailed}</Descriptions.Item>
                    <Descriptions.Item label="Service 数">{clusterOverview.serviceCount}</Descriptions.Item>
                    <Descriptions.Item label="Deployment 数">{clusterOverview.deploymentCount}</Descriptions.Item>
                    <Descriptions.Item label="PV 数">{clusterOverview.pvCount}</Descriptions.Item>
                  </Descriptions>
                ) : <Empty description="暂无集群数据" />}
              </Spin>
            </Card>
          ),
        },
        {
          key: 'nodes',
          label: `节点 (${clusterNodesTotal})`,
          children: (
            <Card extra={<Button icon={<ReloadOutlined />} onClick={loadNodes}>刷新</Button>}>
              <Spin spinning={loading}>
                {clusterNodes.length === 0 && !loading ? (
                  <Empty description="暂无节点数据" />
                ) : (
                  <Table dataSource={clusterNodes} columns={nodeColumns} rowKey="name" pagination={{ pageSize: 20, showSizeChanger: true }} size="middle" scroll={{ x: 1400 }} />
                )}
              </Spin>
            </Card>
          ),
        },
        {
          key: 'namespaces',
          label: '命名空间',
          children: (
            <Card extra={<Button icon={<ReloadOutlined />} onClick={loadNamespaces}>刷新</Button>}>
              <Spin spinning={loading}>
                {clusterNamespaces.length === 0 && !loading ? (
                  <Empty description="暂无命名空间" />
                ) : (
                  <Table dataSource={clusterNamespaces} columns={nsColumns} rowKey="name" pagination={{ pageSize: 20, showSizeChanger: true }} size="middle" />
                )}
              </Spin>
            </Card>
          ),
        },
        {
          key: 'pods',
          label: `Pods (${clusterPodsTotal})`,
          children: (
            <Card
              extra={
                <Space>
                  <Select
                    placeholder="命名空间"
                    allowClear
                    style={{ width: 150 }}
                    value={namespaceFilter}
                    onChange={(v) => setNamespaceFilter(v)}
                    options={clusterNamespaces.map((ns) => ({ label: ns.name, value: ns.name }))}
                  />
                  <Button icon={<ReloadOutlined />} onClick={loadPods}>刷新</Button>
                </Space>
              }
            >
              <Spin spinning={loading}>
                {clusterPods.length === 0 && !loading ? (
                  <Empty description="暂无 Pod" />
                ) : (
                  <Table dataSource={clusterPods} columns={podColumns} rowKey="name" pagination={{ pageSize: 20, showSizeChanger: true }} size="middle" scroll={{ x: 1400 }} />
                )}
              </Spin>
            </Card>
          ),
        },
      ]} />
    </div>
  )
}