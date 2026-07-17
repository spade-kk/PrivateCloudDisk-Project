// ============================================================
// API 管理页面
// API 网关路由、上游、插件、消费者、SSL 证书管理
// ============================================================
import { useEffect, useState, useCallback, useMemo } from 'react'
import {
  Card, Table, Tag, Space, Button, Modal, Input, Select, Form, Switch,
  Spin, Alert, Empty, Tabs, Typography, Popconfirm, message, Statistic, Row, Col, Descriptions, Tooltip, Badge,
} from 'antd'
import {
  ReloadOutlined, PlusOutlined, DeleteOutlined, EditOutlined,
  LinkOutlined, SearchOutlined, CheckCircleOutlined, WarningOutlined,
  CloseCircleOutlined, SyncOutlined, ApiOutlined, CloudServerOutlined,
  SettingOutlined, SafetyCertificateOutlined, UserOutlined, ThunderboltOutlined,
  PlayCircleOutlined, PauseCircleOutlined,
} from '@ant-design/icons'
import { useDevToolsStore } from '@/stores/devToolsStore'
import type { ApiRoute, ApiUpstream, ApiPlugin, ApiConsumer, ApiSSL } from '@/api/devTools'
import type { ColumnsType } from 'antd/es/table'

const { Text, Paragraph } = Typography
const { TextArea } = Input

const methodColorMap: Record<string, string> = {
  GET: 'green', POST: 'blue', PUT: 'orange', DELETE: 'red', PATCH: 'purple',
}

export default function ApiManagePage() {
  const {
    apiGateway, apiRoutes, apiRoutesTotal, apiCurrentRoute,
    apiUpstreams, apiUpstreamsTotal, apiPlugins, apiPluginsTotal,
    apiConsumers, apiConsumersTotal, apiSSLs, apiSSLsTotal,
    loading, error,
    fetchApiGateway, fetchApiRoutes, fetchApiRouteDetail,
    doCreateApiRoute, doUpdateApiRoute, doDeleteApiRoute, doToggleApiRoute,
    fetchApiUpstreams, fetchApiPlugins, fetchApiConsumers, fetchApiSSLs,
  } = useDevToolsStore()

  const [activeTab, setActiveTab] = useState('routes')
  const [routeModalVisible, setRouteModalVisible] = useState(false)
  const [detailModalVisible, setDetailModalVisible] = useState(false)
  const [editingRouteId, setEditingRouteId] = useState<string | null>(null)
  const [routeForm] = Form.useForm()
  const [searchRoute, setSearchRoute] = useState('')

  useEffect(() => { fetchApiGateway() }, [fetchApiGateway])
  useEffect(() => {
    fetchApiRoutes({ page: 1, pageSize: 50, name: searchRoute || undefined })
  }, [fetchApiRoutes, searchRoute])

  useEffect(() => {
    if (activeTab === 'upstreams') fetchApiUpstreams({ page: 1, pageSize: 50 })
    else if (activeTab === 'plugins') fetchApiPlugins({ page: 1, pageSize: 50 })
    else if (activeTab === 'consumers') fetchApiConsumers({ page: 1, pageSize: 50 })
    else if (activeTab === 'ssl') fetchApiSSLs({ page: 1, pageSize: 50 })
  }, [activeTab, fetchApiUpstreams, fetchApiPlugins, fetchApiConsumers, fetchApiSSLs])

  const handleCreateRoute = () => {
    setEditingRouteId(null)
    routeForm.resetFields()
    setRouteModalVisible(true)
  }

  const handleEditRoute = async (routeId: string) => {
    setEditingRouteId(routeId)
    await fetchApiRouteDetail(routeId)
    const route = useDevToolsStore.getState().apiCurrentRoute
    if (route) {
      routeForm.setFieldsValue({
        name: route.name,
        description: route.description,
        uri: route.uri,
        uris: route.uris,
        methods: route.methods,
        hosts: route.hosts,
        priority: route.priority,
        upstreamId: route.upstreamId,
        serviceId: route.serviceId,
        status: route.status === 'ENABLED',
      })
    }
    setRouteModalVisible(true)
  }

  const handleSaveRoute = async () => {
    try {
      const values = await routeForm.validateFields()
      const data = {
        ...values,
        status: values.status ? 'ENABLED' : 'DISABLED',
      }
      let success: boolean
      if (editingRouteId) {
        success = await doUpdateApiRoute(editingRouteId, data)
      } else {
        success = await doCreateApiRoute(data)
      }
      if (success) {
        message.success(editingRouteId ? '路由已更新' : '路由已创建')
        setRouteModalVisible(false)
        fetchApiRoutes({ page: 1, pageSize: 50 })
      }
    } catch {}
  }

  const handleToggleRoute = async (routeId: string, enabled: boolean) => {
    const success = await doToggleApiRoute(routeId, !enabled)
    if (success) { message.success(`${enabled ? '已禁用' : '已启用'}路由`); fetchApiRoutes({ page: 1, pageSize: 50 }) }
  }

  const handleDeleteRoute = async (routeId: string) => {
    const success = await doDeleteApiRoute(routeId)
    if (success) message.success('路由已删除')
    else message.error('删除失败')
  }

  const routeColumns: ColumnsType<ApiRoute> = [
    { title: '名称', dataIndex: 'name', key: 'name', width: 180 },
    { title: 'URI', dataIndex: 'uri', key: 'uri', width: 200, render: (v: string) => <Text code>{v}</Text> },
    { title: '方法', dataIndex: 'methods', key: 'methods', width: 150, render: (methods: string[]) => methods.map((m) => (
      <Tag key={m} color={methodColorMap[m] || 'default'}>{m}</Tag>
    ))},
    { title: 'Host', dataIndex: 'host', key: 'host', width: 150, ellipsis: true },
    { title: '优先级', dataIndex: 'priority', key: 'priority', width: 70 },
    {
      title: '状态', dataIndex: 'status', key: 'status', width: 80,
      render: (s: string) => <Badge status={s === 'ENABLED' ? 'success' : 'default'} text={s === 'ENABLED' ? '启用' : '禁用'} />,
    },
    { title: '上游', dataIndex: 'upstreamName', key: 'upstreamName', width: 120 },
    { title: '插件数', key: 'pluginCount', width: 70, render: (_: unknown, r: ApiRoute) => r.plugins?.length || 0 },
    {
      title: '操作', key: 'actions', width: 220,
      render: (_: unknown, r: ApiRoute) => (
        <Space size="small">
          <Button size="small" icon={<EditOutlined />} onClick={() => handleEditRoute(r.id)} />
          <Button size="small" icon={r.status === 'ENABLED' ? <PauseCircleOutlined /> : <PlayCircleOutlined />}
            onClick={() => handleToggleRoute(r.id, r.status === 'ENABLED')} />
          <Popconfirm title="确定删除？" onConfirm={() => handleDeleteRoute(r.id)}>
            <Button size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ]

  const upstreamColumns: ColumnsType<ApiUpstream> = [
    { title: '名称', dataIndex: 'name', key: 'name', width: 180 },
    { title: '描述', dataIndex: 'description', key: 'description', width: 200, ellipsis: true },
    { title: '类型', dataIndex: 'type', key: 'type', width: 100, render: (t: string) => <Tag>{t}</Tag> },
    { title: '节点', key: 'nodes', width: 300, render: (_: unknown, r: ApiUpstream) => (
      r.nodes?.map((n) => <Tag key={n.host} color="blue">{n.host}:{n.port} (权重: {n.weight})</Tag>)
    )},
    { title: '超时(s)', key: 'timeout', width: 100, render: (_: unknown, r: ApiUpstream) => `${r.timeout?.connect}/${r.timeout?.send}/${r.timeout?.read}` },
    { title: '重试', dataIndex: 'retries', key: 'retries', width: 60 },
    { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 160 },
  ]

  const pluginColumns: ColumnsType<ApiPlugin> = [
    { title: '名称', dataIndex: 'name', key: 'name', width: 200 },
    { title: '描述', dataIndex: 'description', key: 'description', width: 300, ellipsis: true },
    { title: '版本', dataIndex: 'version', key: 'version', width: 80, render: (v: string) => <Tag>{v}</Tag> },
    {
      title: '启用', dataIndex: 'enabled', key: 'enabled', width: 80,
      render: (v: boolean) => v ? <CheckCircleOutlined style={{ color: '#52c41a' }} /> : <CloseCircleOutlined style={{ color: '#ff4d4f' }} />,
    },
    { title: '优先级', dataIndex: 'priority', key: 'priority', width: 70 },
    { title: '配置', dataIndex: 'config', key: 'config', width: 200, ellipsis: true, render: (v: Record<string, unknown>) => (
      <Text code style={{ fontSize: 11 }}>{JSON.stringify(v).substring(0, 60)}</Text>
    )},
  ]

  const consumerColumns: ColumnsType<ApiConsumer> = [
    { title: '用户名', dataIndex: 'username', key: 'username', width: 150 },
    { title: '描述', dataIndex: 'description', key: 'description', width: 200, ellipsis: true },
    { title: '插件数', key: 'pluginCount', width: 70, render: (_: unknown, r: ApiConsumer) => r.plugins?.length || 0 },
    { title: '标签', dataIndex: 'labels', key: 'labels', width: 200, render: (labels: Record<string, string>) => (
      Object.entries(labels || {}).map(([k, v]) => <Tag key={k}>{k}: {v}</Tag>)
    )},
    { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 160 },
  ]

  const sslColumns: ColumnsType<ApiSSL> = [
    { title: 'SNI', dataIndex: 'sni', key: 'sni', width: 200, render: (v: string) => <Text code>{v}</Text> },
    { title: '证书域名', dataIndex: 'certDomain', key: 'certDomain', width: 200 },
    { title: '过期时间', dataIndex: 'expireTime', key: 'expireTime', width: 160 },
    { title: '状态', key: 'status', width: 80, render: (_: unknown, r: ApiSSL) => {
      const expired = new Date(r.expireTime) < new Date()
      return <Tag color={expired ? 'red' : 'green'}>{expired ? '已过期' : '有效'}</Tag>
    }},
    { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 160 },
  ]

  return (
    <div>
      {/* API 网关概览 */}
      {apiGateway && (
        <Card title="API 网关" size="small" style={{ marginBottom: 16 }} extra={<Button size="small" icon={<ReloadOutlined />} onClick={fetchApiGateway} />}>
          <Descriptions bordered size="small" column={{ xs: 1, sm: 2, md: 4 }}>
            <Descriptions.Item label="名称">{apiGateway.name}</Descriptions.Item>
            <Descriptions.Item label="版本">{apiGateway.version}</Descriptions.Item>
            <Descriptions.Item label="路由数">{apiGateway.routeCount}</Descriptions.Item>
            <Descriptions.Item label="上游数">{apiGateway.upstreamCount}</Descriptions.Item>
            <Descriptions.Item label="插件数">{apiGateway.pluginCount}</Descriptions.Item>
            <Descriptions.Item label="消费者数">{apiGateway.consumerCount}</Descriptions.Item>
            <Descriptions.Item label="SSL 证书数">{apiGateway.sslCount}</Descriptions.Item>
            <Descriptions.Item label="运行时间">{apiGateway.uptime ? `${Math.floor(apiGateway.uptime / 3600)}h` : '-'}</Descriptions.Item>
          </Descriptions>
        </Card>
      )}

      {error && <Alert message={error} type="error" showIcon closable style={{ marginBottom: 16 }} />}

      <Tabs activeKey={activeTab} onChange={setActiveTab} items={[
        {
          key: 'routes',
          label: `路由 (${apiRoutesTotal})`,
          children: (
            <Card
              extra={
                <Space>
                  <Input placeholder="搜索路由" prefix={<SearchOutlined />} allowClear style={{ width: 200 }}
                    value={searchRoute} onChange={(e) => setSearchRoute(e.target.value)} />
                  <Button icon={<PlusOutlined />} type="primary" onClick={handleCreateRoute}>创建路由</Button>
                  <Button icon={<ReloadOutlined />} onClick={() => fetchApiRoutes({ page: 1, pageSize: 50 })}>刷新</Button>
                </Space>
              }
            >
              <Spin spinning={loading}>
                {apiRoutes.length === 0 && !loading ? (
                  <Empty description="暂无路由" />
                ) : (
                  <Table dataSource={apiRoutes} columns={routeColumns} rowKey="id" pagination={{ pageSize: 20, showSizeChanger: true }} size="middle" scroll={{ x: 1200 }} />
                )}
              </Spin>
            </Card>
          ),
        },
        {
          key: 'upstreams',
          label: `上游 (${apiUpstreamsTotal})`,
          children: (
            <Card extra={<Button icon={<ReloadOutlined />} onClick={() => fetchApiUpstreams({ page: 1, pageSize: 50 })}>刷新</Button>}>
              <Spin spinning={loading}>
                {apiUpstreams.length === 0 && !loading ? (
                  <Empty description="暂无上游" />
                ) : (
                  <Table dataSource={apiUpstreams} columns={upstreamColumns} rowKey="id" pagination={{ pageSize: 20, showSizeChanger: true }} size="middle" />
                )}
              </Spin>
            </Card>
          ),
        },
        {
          key: 'plugins',
          label: `插件 (${apiPluginsTotal})`,
          children: (
            <Card extra={<Button icon={<ReloadOutlined />} onClick={() => fetchApiPlugins({ page: 1, pageSize: 50 })}>刷新</Button>}>
              <Spin spinning={loading}>
                {apiPlugins.length === 0 && !loading ? (
                  <Empty description="暂无插件" />
                ) : (
                  <Table dataSource={apiPlugins} columns={pluginColumns} rowKey="id" pagination={{ pageSize: 20, showSizeChanger: true }} size="middle" />
                )}
              </Spin>
            </Card>
          ),
        },
        {
          key: 'consumers',
          label: `消费者 (${apiConsumersTotal})`,
          children: (
            <Card extra={<Button icon={<ReloadOutlined />} onClick={() => fetchApiConsumers({ page: 1, pageSize: 50 })}>刷新</Button>}>
              <Spin spinning={loading}>
                {apiConsumers.length === 0 && !loading ? (
                  <Empty description="暂无消费者" />
                ) : (
                  <Table dataSource={apiConsumers} columns={consumerColumns} rowKey="id" pagination={{ pageSize: 20, showSizeChanger: true }} size="middle" />
                )}
              </Spin>
            </Card>
          ),
        },
        {
          key: 'ssl',
          label: `SSL (${apiSSLsTotal})`,
          children: (
            <Card extra={<Button icon={<ReloadOutlined />} onClick={() => fetchApiSSLs({ page: 1, pageSize: 50 })}>刷新</Button>}>
              <Spin spinning={loading}>
                {apiSSLs.length === 0 && !loading ? (
                  <Empty description="暂无 SSL 证书" />
                ) : (
                  <Table dataSource={apiSSLs} columns={sslColumns} rowKey="id" pagination={{ pageSize: 20, showSizeChanger: true }} size="middle" />
                )}
              </Spin>
            </Card>
          ),
        },
      ]} />

      {/* 路由创建/编辑弹窗 */}
      <Modal title={editingRouteId ? '编辑路由' : '创建路由'} open={routeModalVisible} onOk={handleSaveRoute} onCancel={() => setRouteModalVisible(false)} width={600} destroyOnClose>
        <Form form={routeForm} layout="vertical">
          <Form.Item name="name" label="路由名称" rules={[{ required: true }]}>
            <Input placeholder="route-name" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input placeholder="路由描述" />
          </Form.Item>
          <Form.Item name="uri" label="URI" rules={[{ required: true }]}>
            <Input placeholder="/api/v1/*" />
          </Form.Item>
          <Form.Item name="uris" label="URIs (多值)">
            <Select mode="tags" placeholder="输入 URI 后按回车" />
          </Form.Item>
          <Form.Item name="methods" label="HTTP 方法">
            <Select mode="multiple" placeholder="选择方法" options={[
              { label: 'GET', value: 'GET' }, { label: 'POST', value: 'POST' },
              { label: 'PUT', value: 'PUT' }, { label: 'DELETE', value: 'DELETE' },
              { label: 'PATCH', value: 'PATCH' },
            ]} />
          </Form.Item>
          <Form.Item name="hosts" label="Hosts">
            <Select mode="tags" placeholder="输入 Host 后按回车" />
          </Form.Item>
          <Form.Item name="upstreamId" label="上游">
            <Select placeholder="选择上游" allowClear options={apiUpstreams.map((u) => ({ label: u.name, value: u.id }))} />
          </Form.Item>
          <Form.Item name="priority" label="优先级" initialValue={0}>
            <Input type="number" />
          </Form.Item>
          <Form.Item name="status" label="启用" valuePropName="checked" initialValue={true}>
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}