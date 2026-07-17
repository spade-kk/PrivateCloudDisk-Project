// ============================================================
// SkyWalking 集成页面
// 服务拓扑、链路追踪、指标、告警
// ============================================================
import { useEffect, useState, useCallback, useMemo } from 'react'
import {
  Card, Table, Tag, Space, Button, Modal, Input, Select, Form,
  Spin, Alert, Empty, Tabs, Typography, message, Statistic, Row, Col, Tooltip, Badge, Descriptions, Tree,
} from 'antd'
import {
  ReloadOutlined, LinkOutlined, SearchOutlined, FullscreenOutlined,
  CheckCircleOutlined, WarningOutlined, CloseCircleOutlined, SyncOutlined,
  ApartmentOutlined, NodeIndexOutlined, ThunderboltOutlined, BellOutlined,
  ClockCircleOutlined, EyeOutlined, BranchesOutlined,
} from '@ant-design/icons'
import { useMonitorIntegrationStore } from '@/stores/monitorIntegrationStore'
import type { SkyWalkingService, SkyWalkingTrace, SkyWalkingAlarm } from '@/api/monitor'
import type { ColumnsType } from 'antd/es/table'

const { Text, Paragraph } = Typography

const traceStateMap: Record<string, { color: string; icon: React.ReactNode }> = {
  SUCCESS: { color: 'green', icon: <CheckCircleOutlined /> },
  ERROR: { color: 'red', icon: <CloseCircleOutlined /> },
  LATENCY: { color: 'orange', icon: <WarningOutlined /> },
}

function formatDuration(ms: number): string {
  if (ms < 1000) return `${ms}ms`
  if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`
  return `${(ms / 60000).toFixed(1)}min`
}

export default function SkyWalkingIntegrationPage() {
  const {
    skywalkingServices, skywalkingTraces, skywalkingTracesTotal,
    skywalkingCurrentTrace, skywalkingMetrics, skywalkingAlarms,
    skywalkingAlarmsTotal, skywalkingConsoleUrl, loading, error,
    fetchSkyWalkingServices, fetchSkyWalkingTraces, fetchSkyWalkingTrace,
    fetchSkyWalkingMetrics, fetchSkyWalkingAlarms, fetchSkyWalkingConsoleUrl,
  } = useMonitorIntegrationStore()

  const [activeTab, setActiveTab] = useState('services')
  const [selectedService, setSelectedService] = useState<string | undefined>()
  const [traceModalVisible, setTraceModalVisible] = useState(false)
  const [searchEndpoint, setSearchEndpoint] = useState('')

  useEffect(() => {
    fetchSkyWalkingServices()
    fetchSkyWalkingConsoleUrl()
  }, [fetchSkyWalkingServices, fetchSkyWalkingConsoleUrl])

  useEffect(() => {
    if (activeTab === 'traces') {
      fetchSkyWalkingTraces({ serviceId: selectedService, pageNum: 1, pageSize: 20 })
    } else if (activeTab === 'alarms') {
      fetchSkyWalkingAlarms({ pageNum: 1, pageSize: 20 })
    }
  }, [activeTab, selectedService, fetchSkyWalkingTraces, fetchSkyWalkingAlarms])

  const handleViewTrace = async (traceId: string) => {
    await fetchSkyWalkingTrace(traceId)
    setTraceModalVisible(true)
  }

  const handleOpenConsole = () => {
    if (skywalkingConsoleUrl) window.open(skywalkingConsoleUrl, '_blank')
  }

  const serviceColumns: ColumnsType<SkyWalkingService> = [
    { title: '服务名', dataIndex: 'name', key: 'name', width: 200, render: (n: string) => <Text strong>{n}</Text> },
    { title: '分组', dataIndex: 'group', key: 'group', width: 120, render: (g: string) => <Tag>{g}</Tag> },
    {
      title: '健康状态', dataIndex: 'healthStatus', key: 'healthStatus', width: 100,
      render: (s: string) => {
        const colorMap: Record<string, string> = { GREEN: 'green', YELLOW: 'orange', RED: 'red' }
        return <Tag color={colorMap[s] || 'default'}>{s}</Tag>
      },
    },
    { title: '实例数', dataIndex: 'instanceCount', key: 'instanceCount', width: 80 },
    { title: '平均延迟', dataIndex: 'avgResponseTime', key: 'avgResponseTime', width: 110, render: (v: number) => `${v}ms` },
    { title: '吞吐量', dataIndex: 'throughput', key: 'throughput', width: 100, render: (v: number) => `${v.toFixed(1)}/s` },
    { title: '成功率', dataIndex: 'successRate', key: 'successRate', width: 100, render: (v: number) => (
      <Text style={{ color: v >= 99 ? '#3f8600' : v >= 95 ? '#d48806' : '#cf1322' }}>{v.toFixed(1)}%</Text>
    )},
    { title: 'Apdex', dataIndex: 'apdex', key: 'apdex', width: 80, render: (v: number) => v?.toFixed(2) },
  ]

  const traceColumns: ColumnsType<SkyWalkingTrace> = [
    { title: 'Trace ID', dataIndex: 'traceId', key: 'traceId', width: 200, ellipsis: true, render: (v: string) => <Text code>{v?.substring(0, 18)}...</Text> },
    { title: 'Endpoint', dataIndex: 'endpointName', key: 'endpointName', width: 200, ellipsis: true },
    { title: '耗时', dataIndex: 'duration', key: 'duration', width: 100, render: (v: number) => formatDuration(v) },
    { title: '开始时间', dataIndex: 'start', key: 'start', width: 160 },
    {
      title: '状态', dataIndex: 'isError', key: 'isError', width: 80,
      render: (v: boolean) => v ? <Tag color="red">错误</Tag> : <Tag color="green">正常</Tag>,
    },
    { title: 'Span 数', key: 'spanCount', width: 80, render: (_: unknown, r: SkyWalkingTrace) => r.spans?.length || 0 },
    {
      title: '操作', key: 'actions', width: 100,
      render: (_: unknown, r: SkyWalkingTrace) => (
        <Button size="small" icon={<EyeOutlined />} onClick={() => handleViewTrace(r.traceId)}>详情</Button>
      ),
    },
  ]

  const alarmColumns: ColumnsType<SkyWalkingAlarm> = [
    { title: '规则名称', dataIndex: 'ruleName', key: 'ruleName', width: 200, ellipsis: true },
    { title: '消息', dataIndex: 'message', key: 'message', width: 300, ellipsis: true },
    { title: '服务', dataIndex: 'serviceName', key: 'serviceName', width: 150 },
    { title: '指标', dataIndex: 'metricName', key: 'metricName', width: 120 },
    { title: '阈值', dataIndex: 'threshold', key: 'threshold', width: 80 },
    { title: '当前值', dataIndex: 'currentValue', key: 'currentValue', width: 80 },
    { title: '触发时间', dataIndex: 'startTime', key: 'startTime', width: 160 },
    { title: '严重级别', dataIndex: 'severity', key: 'severity', width: 100, render: (s: string) => {
      const colorMap: Record<string, string> = { CRITICAL: 'red', WARNING: 'orange', INFO: 'blue' }
      return <Tag color={colorMap[s] || 'default'}>{s}</Tag>
    }},
  ]

  return (
    <div>
      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="服务数" value={skywalkingServices.length} prefix={<ApartmentOutlined />} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="链路数" value={skywalkingTracesTotal} prefix={<BranchesOutlined />} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="告警数" value={skywalkingAlarmsTotal} prefix={<BellOutlined />} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small">
            <Tooltip title="打开 SkyWalking 控制台">
              <Statistic title="控制台" value="打开" prefix={<LinkOutlined />} valueStyle={{ fontSize: 16, cursor: 'pointer' }} onClick={handleOpenConsole} />
            </Tooltip>
          </Card>
        </Col>
      </Row>

      {error && <Alert message={error} type="error" showIcon closable style={{ marginBottom: 16 }} />}

      <Tabs activeKey={activeTab} onChange={setActiveTab} items={[
        {
          key: 'services',
          label: `服务 (${skywalkingServices.length})`,
          children: (
            <Card extra={<Button icon={<ReloadOutlined />} onClick={fetchSkyWalkingServices}>刷新</Button>}>
              <Spin spinning={loading}>
                {skywalkingServices.length === 0 && !loading ? (
                  <Empty description="暂无服务" />
                ) : (
                  <Table dataSource={skywalkingServices} columns={serviceColumns} rowKey="id" pagination={false} size="middle" />
                )}
              </Spin>
            </Card>
          ),
        },
        {
          key: 'traces',
          label: `链路追踪 (${skywalkingTracesTotal})`,
          children: (
            <Card
              extra={
                <Space>
                  <Select placeholder="选择服务" allowClear style={{ width: 200 }} value={selectedService} onChange={setSelectedService}
                    options={skywalkingServices.map((s) => ({ label: s.name, value: s.id }))} />
                  <Button icon={<ReloadOutlined />} onClick={() => fetchSkyWalkingTraces({ serviceId: selectedService, pageNum: 1, pageSize: 20 })}>刷新</Button>
                </Space>
              }
            >
              <Spin spinning={loading}>
                {skywalkingTraces.length === 0 && !loading ? (
                  <Empty description="暂无链路数据" />
                ) : (
                  <Table dataSource={skywalkingTraces} columns={traceColumns} rowKey="traceId" pagination={{ pageSize: 20, showSizeChanger: true }} size="middle" />
                )}
              </Spin>
            </Card>
          ),
        },
        {
          key: 'alarms',
          label: `告警 (${skywalkingAlarmsTotal})`,
          children: (
            <Card extra={<Button icon={<ReloadOutlined />} onClick={() => fetchSkyWalkingAlarms({ pageNum: 1, pageSize: 20 })}>刷新</Button>}>
              <Spin spinning={loading}>
                {skywalkingAlarms.length === 0 && !loading ? (
                  <Empty description="暂无告警" />
                ) : (
                  <Table dataSource={skywalkingAlarms} columns={alarmColumns} rowKey="id" pagination={{ pageSize: 20, showSizeChanger: true }} size="middle" scroll={{ x: 1200 }} />
                )}
              </Spin>
            </Card>
          ),
        },
      ]} />

      {/* Trace 详情弹窗 */}
      <Modal title="Trace 详情" open={traceModalVisible} onCancel={() => setTraceModalVisible(false)} footer={null} width={800} destroyOnClose>
        {skywalkingCurrentTrace ? (
          <Descriptions bordered size="small" column={{ xs: 1, sm: 2 }}>
            <Descriptions.Item label="Trace ID">{skywalkingCurrentTrace.traceId}</Descriptions.Item>
            <Descriptions.Item label="Endpoint">{skywalkingCurrentTrace.endpointName}</Descriptions.Item>
            <Descriptions.Item label="耗时">{formatDuration(skywalkingCurrentTrace.duration)}</Descriptions.Item>
            <Descriptions.Item label="开始时间">{skywalkingCurrentTrace.start}</Descriptions.Item>
            <Descriptions.Item label="状态">
              <Tag color={skywalkingCurrentTrace.isError ? 'red' : 'green'}>
                {skywalkingCurrentTrace.isError ? '错误' : '正常'}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="Span 数">{skywalkingCurrentTrace.spans?.length || 0}</Descriptions.Item>
          </Descriptions>
        ) : (
          <Spin />
        )}
        {skywalkingCurrentTrace?.spans && (
          <Card title="Span 列表" size="small" style={{ marginTop: 16 }}>
            <Table
              dataSource={skywalkingCurrentTrace.spans}
              rowKey="spanId"
              pagination={false}
              size="small"
              columns={[
                { title: 'Span ID', dataIndex: 'spanId', key: 'spanId', width: 120, render: (v: string) => <Text code>{v}</Text> },
                { title: '操作', dataIndex: 'operationName', key: 'operationName', width: 200, ellipsis: true },
                { title: '服务', dataIndex: 'serviceCode', key: 'serviceCode', width: 150 },
                { title: '耗时', dataIndex: 'duration', key: 'duration', width: 100, render: (v: number) => `${v}ms` },
                { title: '开始时间', dataIndex: 'startTime', key: 'startTime', width: 160 },
                { title: '状态', dataIndex: 'isError', key: 'isError', width: 80, render: (v: boolean) => v ? <Tag color="red">错误</Tag> : <Tag color="green">正常</Tag> },
              ]}
            />
          </Card>
        )}
      </Modal>
    </div>
  )
}