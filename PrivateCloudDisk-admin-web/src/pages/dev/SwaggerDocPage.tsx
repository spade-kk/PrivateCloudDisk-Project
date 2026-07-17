// ============================================================
// Swagger 文档管理页面
// 服务列表、API 文档浏览、Swagger UI 集成
// ============================================================
import { useEffect, useState, useCallback, useMemo } from 'react'
import {
  Card, Table, Tag, Space, Button, Modal, Input, Select, Form,
  Spin, Alert, Empty, Tabs, Typography, message, Statistic, Row, Col, Tooltip, Descriptions, Badge, Collapse,
} from 'antd'
import {
  ReloadOutlined, PlusOutlined, LinkOutlined, SearchOutlined,
  CheckCircleOutlined, WarningOutlined, CloseCircleOutlined, SyncOutlined,
  ApiOutlined, BookOutlined, FullscreenOutlined, CodeOutlined, EyeOutlined, ImportOutlined,
} from '@ant-design/icons'
import { useDevToolsStore } from '@/stores/devToolsStore'
import type { SwaggerService, SwaggerEndpoint } from '@/api/devTools'
import type { ColumnsType } from 'antd/es/table'

const { Text, Paragraph } = Typography
const { Panel } = Collapse

const methodColorMap: Record<string, string> = {
  GET: 'green',
  POST: 'blue',
  PUT: 'orange',
  DELETE: 'red',
  PATCH: 'purple',
  HEAD: 'default',
  OPTIONS: 'cyan',
}

const specVersionMap: Record<string, string> = {
  '2.0': 'Swagger 2.0',
  '3.0': 'OpenAPI 3.0',
  '3.1': 'OpenAPI 3.1',
}

const statusMap: Record<string, { color: string; icon: React.ReactNode }> = {
  AVAILABLE: { color: 'green', icon: <CheckCircleOutlined /> },
  UNAVAILABLE: { color: 'red', icon: <CloseCircleOutlined /> },
  ERROR: { color: 'orange', icon: <WarningOutlined /> },
}

export default function SwaggerDocPage() {
  const {
    swaggerServices, swaggerEndpoints, swaggerCurrentEndpoint, swaggerUIUrl,
    loading, error,
    fetchSwaggerServices, fetchSwaggerEndpoints, fetchSwaggerEndpointDetail,
    doRefreshSwaggerService, fetchSwaggerUIUrl,
  } = useDevToolsStore()

  const [activeTab, setActiveTab] = useState('services')
  const [selectedServiceId, setSelectedServiceId] = useState<string | null>(null)
  const [endpointSearch, setEndpointSearch] = useState('')
  const [swaggerUIModalVisible, setSwaggerUIModalVisible] = useState(false)
  const [endpointDetailVisible, setEndpointDetailVisible] = useState(false)

  useEffect(() => { fetchSwaggerServices() }, [fetchSwaggerServices])

  useEffect(() => {
    if (selectedServiceId && activeTab === 'endpoints') {
      fetchSwaggerEndpoints(selectedServiceId, { search: endpointSearch || undefined })
    }
  }, [selectedServiceId, activeTab, endpointSearch, fetchSwaggerEndpoints])

  const handleRefreshService = async (serviceId: string) => {
    const success = await doRefreshSwaggerService(serviceId)
    if (success) { message.success('刷新成功'); fetchSwaggerServices() }
    else message.error('刷新失败')
  }

  const handleViewSwaggerUI = async (serviceId: string) => {
    await fetchSwaggerUIUrl(serviceId)
    setSwaggerUIModalVisible(true)
  }

  const handleViewEndpoint = async (serviceId: string, path: string, method: string) => {
    await fetchSwaggerEndpointDetail(serviceId, path, method)
    setEndpointDetailVisible(true)
  }

  const filteredEndpoints = useMemo(() => {
    if (!endpointSearch) return swaggerEndpoints
    return swaggerEndpoints.filter((ep) =>
      ep.path.toLowerCase().includes(endpointSearch.toLowerCase()) ||
      ep.summary?.toLowerCase().includes(endpointSearch.toLowerCase())
    )
  }, [swaggerEndpoints, endpointSearch])

  const serviceColumns: ColumnsType<SwaggerService> = [
    { title: '名称', dataIndex: 'name', key: 'name', width: 180, render: (n: string, r: SwaggerService) => (
      <a onClick={() => { setSelectedServiceId(r.id); setActiveTab('endpoints') }}>{n}</a>
    )},
    { title: '版本', dataIndex: 'version', key: 'version', width: 80, render: (v: string) => <Tag>{v}</Tag> },
    { title: '规范', dataIndex: 'specVersion', key: 'specVersion', width: 120, render: (v: string) => <Tag color="blue">{specVersionMap[v] || v}</Tag> },
    {
      title: '状态', dataIndex: 'status', key: 'status', width: 100,
      render: (s: string) => {
        const cfg = statusMap[s] || { color: 'default', icon: null }
        return <Tag color={cfg.color} icon={cfg.icon}>{s}</Tag>
      },
    },
    { title: '接口数', dataIndex: 'pathCount', key: 'pathCount', width: 80 },
    { title: 'Schema 数', dataIndex: 'schemaCount', key: 'schemaCount', width: 90 },
    { title: 'Base URL', dataIndex: 'baseUrl', key: 'baseUrl', width: 200, ellipsis: true, render: (v: string) => <Text code>{v}</Text> },
    { title: '描述', dataIndex: 'description', key: 'description', width: 200, ellipsis: true },
    { title: '标签', dataIndex: 'tags', key: 'tags', width: 150, render: (tags: string[]) => tags.map((t) => <Tag key={t}>{t}</Tag>) },
    {
      title: '操作', key: 'actions', width: 220,
      render: (_: unknown, r: SwaggerService) => (
        <Space size="small">
          <Button size="small" icon={<EyeOutlined />} onClick={() => { setSelectedServiceId(r.id); setActiveTab('endpoints') }}>接口</Button>
          <Button size="small" icon={<FullscreenOutlined />} onClick={() => handleViewSwaggerUI(r.id)}>Swagger UI</Button>
          <Button size="small" icon={<SyncOutlined />} onClick={() => handleRefreshService(r.id)}>刷新</Button>
        </Space>
      ),
    },
  ]

  const endpointColumns: ColumnsType<SwaggerEndpoint> = [
    {
      title: '方法', dataIndex: 'method', key: 'method', width: 80,
      render: (m: string) => <Tag color={methodColorMap[m] || 'default'} style={{ fontWeight: 'bold' }}>{m}</Tag>,
    },
    { title: '路径', dataIndex: 'path', key: 'path', width: 250, render: (p: string) => <Text code>{p}</Text> },
    { title: '摘要', dataIndex: 'summary', key: 'summary', width: 200, ellipsis: true },
    { title: '标签', dataIndex: 'tags', key: 'tags', width: 150, render: (tags: string[]) => tags.map((t) => <Tag key={t}>{t}</Tag>) },
    { title: '操作ID', dataIndex: 'operationId', key: 'operationId', width: 180, ellipsis: true },
    {
      title: '已弃用', dataIndex: 'deprecated', key: 'deprecated', width: 80,
      render: (v: boolean) => v ? <Tag color="red">是</Tag> : '-',
    },
    {
      title: '操作', key: 'actions', width: 100,
      render: (_: unknown, r: SwaggerEndpoint) => (
        <Button size="small" icon={<CodeOutlined />} onClick={() => handleViewEndpoint(selectedServiceId!, r.path, r.method)}>详情</Button>
      ),
    },
  ]

  return (
    <div>
      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="服务数" value={swaggerServices.length} prefix={<ApiOutlined />} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="总接口数" value={swaggerServices.reduce((s, svc) => s + svc.pathCount, 0)} prefix={<BookOutlined />} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="可用" value={swaggerServices.filter((s) => s.status === 'AVAILABLE').length} valueStyle={{ color: '#3f8600' }} prefix={<CheckCircleOutlined />} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="不可用" value={swaggerServices.filter((s) => s.status !== 'AVAILABLE').length} valueStyle={{ color: '#cf1322' }} prefix={<CloseCircleOutlined />} /></Card>
        </Col>
      </Row>

      {error && <Alert message={error} type="error" showIcon closable style={{ marginBottom: 16 }} />}

      <Tabs activeKey={activeTab} onChange={setActiveTab} items={[
        {
          key: 'services',
          label: `服务 (${swaggerServices.length})`,
          children: (
            <Card extra={<Button icon={<ReloadOutlined />} onClick={fetchSwaggerServices}>刷新</Button>}>
              <Spin spinning={loading}>
                {swaggerServices.length === 0 && !loading ? (
                  <Empty description="暂无 Swagger 服务" />
                ) : (
                  <Table dataSource={swaggerServices} columns={serviceColumns} rowKey="id" pagination={false} size="middle" scroll={{ x: 1300 }} />
                )}
              </Spin>
            </Card>
          ),
        },
        {
          key: 'endpoints',
          label: `接口${selectedServiceId ? ' (' + swaggerEndpoints.length + ')' : ''}`,
          children: (
            <Card
              extra={
                <Space>
                  {selectedServiceId ? (
                    <>
                      <Input placeholder="搜索接口" prefix={<SearchOutlined />} allowClear style={{ width: 250 }}
                        value={endpointSearch} onChange={(e) => setEndpointSearch(e.target.value)} />
                      <Button icon={<ReloadOutlined />} onClick={() => fetchSwaggerEndpoints(selectedServiceId)}>刷新</Button>
                      <Button onClick={() => { setSelectedServiceId(null); setActiveTab('services') }}>返回服务</Button>
                    </>
                  ) : (
                    <Text type="secondary">请从服务列表中选择一个服务</Text>
                  )}
                </Space>
              }
            >
              <Spin spinning={loading}>
                {!selectedServiceId ? (
                  <Empty description="请选择一个服务查看接口" />
                ) : filteredEndpoints.length === 0 && !loading ? (
                  <Empty description="暂无接口" />
                ) : (
                  <Table dataSource={filteredEndpoints} columns={endpointColumns} rowKey="operationId" pagination={{ pageSize: 20, showSizeChanger: true }} size="middle" />
                )}
              </Spin>
            </Card>
          ),
        },
      ]} />

      {/* Swagger UI 弹窗 */}
      <Modal title="Swagger UI" open={swaggerUIModalVisible} onCancel={() => setSwaggerUIModalVisible(false)} footer={null} width={900} destroyOnClose>
        {swaggerUIUrl ? (
          <iframe src={swaggerUIUrl} width="100%" height="550" style={{ border: 'none', borderRadius: 4 }} title="Swagger UI" />
        ) : (
          <Spin tip="加载 Swagger UI..." style={{ display: 'block', textAlign: 'center', padding: 100 }} />
        )}
      </Modal>

      {/* 接口详情弹窗 */}
      <Modal title="接口详情" open={endpointDetailVisible} onCancel={() => setEndpointDetailVisible(false)} footer={null} width={700} destroyOnClose>
        {swaggerCurrentEndpoint && (
          <Descriptions bordered size="small" column={1}>
            <Descriptions.Item label="方法">
              <Tag color={methodColorMap[swaggerCurrentEndpoint.method] || 'default'}>{swaggerCurrentEndpoint.method}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="路径"><Text code>{swaggerCurrentEndpoint.path}</Text></Descriptions.Item>
            <Descriptions.Item label="摘要">{swaggerCurrentEndpoint.summary}</Descriptions.Item>
            <Descriptions.Item label="描述">{swaggerCurrentEndpoint.description || '-'}</Descriptions.Item>
            <Descriptions.Item label="操作ID">{swaggerCurrentEndpoint.operationId}</Descriptions.Item>
            <Descriptions.Item label="标签">{swaggerCurrentEndpoint.tags.map((t) => <Tag key={t}>{t}</Tag>)}</Descriptions.Item>
            <Descriptions.Item label="已弃用">{swaggerCurrentEndpoint.deprecated ? <Tag color="red">是</Tag> : '否'}</Descriptions.Item>
            {swaggerCurrentEndpoint.parameters && swaggerCurrentEndpoint.parameters.length > 0 && (
              <Descriptions.Item label="参数">
                <Table
                  dataSource={swaggerCurrentEndpoint.parameters}
                  rowKey="name"
                  pagination={false}
                  size="small"
                  columns={[
                    { title: '名称', dataIndex: 'name', key: 'name' },
                    { title: '位置', dataIndex: 'in', key: 'in', render: (v: string) => <Tag>{v}</Tag> },
                    { title: '类型', dataIndex: 'type', key: 'type' },
                    { title: '必填', dataIndex: 'required', key: 'required', render: (v: boolean) => v ? <Tag color="red">是</Tag> : '否' },
                    { title: '描述', dataIndex: 'description', key: 'description', ellipsis: true },
                  ]}
                />
              </Descriptions.Item>
            )}
            {swaggerCurrentEndpoint.requestBody && (
              <Descriptions.Item label="请求体">
                <pre style={{ maxHeight: 200, overflow: 'auto', background: '#f5f5f5', padding: 12, borderRadius: 4, fontSize: 12 }}>
                  {JSON.stringify(swaggerCurrentEndpoint.requestBody, null, 2)}
                </pre>
              </Descriptions.Item>
            )}
            {swaggerCurrentEndpoint.responses && (
              <Descriptions.Item label="响应">
                <pre style={{ maxHeight: 200, overflow: 'auto', background: '#f5f5f5', padding: 12, borderRadius: 4, fontSize: 12 }}>
                  {JSON.stringify(swaggerCurrentEndpoint.responses, null, 2)}
                </pre>
              </Descriptions.Item>
            )}
          </Descriptions>
        )}
      </Modal>
    </div>
  )
}