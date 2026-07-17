// ============================================================
// Grafana 集成页面
// 仪表盘嵌入、告警查看、控制台集成
// ============================================================
import { useEffect, useState, useCallback, useMemo } from 'react'
import {
  Card, Table, Tag, Space, Button, Modal, Input, Select, Form,
  Spin, Alert, Empty, Tabs, Typography, message, Statistic, Row, Col, Tooltip, Badge,
} from 'antd'
import {
  ReloadOutlined, LinkOutlined, SearchOutlined, StarOutlined,
  DashboardOutlined, BellOutlined, CheckCircleOutlined, WarningOutlined,
  CloseCircleOutlined, FullscreenOutlined, AppstoreOutlined,
} from '@ant-design/icons'
import { useMonitorIntegrationStore } from '@/stores/monitorIntegrationStore'
import type { GrafanaDashboard, GrafanaAlert } from '@/api/monitor'
import type { ColumnsType } from 'antd/es/table'

const { Text, Paragraph } = Typography

const alertStateMap: Record<string, { color: string; icon: React.ReactNode }> = {
  OK: { color: 'green', icon: <CheckCircleOutlined /> },
  ALERTING: { color: 'red', icon: <WarningOutlined /> },
  PENDING: { color: 'orange', icon: <WarningOutlined /> },
  NO_DATA: { color: 'default', icon: <CloseCircleOutlined /> },
  ERROR: { color: 'red', icon: <CloseCircleOutlined /> },
}

export default function GrafanaIntegrationPage() {
  const {
    grafanaDashboards, grafanaAlerts, grafanaEmbedUrl, grafanaConsoleUrl,
    loading, error,
    fetchGrafanaDashboards, fetchGrafanaAlerts, fetchGrafanaEmbedUrl, fetchGrafanaConsoleUrl,
  } = useMonitorIntegrationStore()

  const [activeTab, setActiveTab] = useState('dashboards')
  const [embedModalVisible, setEmbedModalVisible] = useState(false)
  const [embedTitle, setEmbedTitle] = useState('')
  const [searchDash, setSearchDash] = useState('')

  useEffect(() => {
    fetchGrafanaDashboards()
    fetchGrafanaConsoleUrl()
  }, [fetchGrafanaDashboards, fetchGrafanaConsoleUrl])

  useEffect(() => {
    if (activeTab === 'alerts') fetchGrafanaAlerts()
  }, [activeTab, fetchGrafanaAlerts])

  const handleEmbedDashboard = async (dashboard: GrafanaDashboard) => {
    setEmbedTitle(dashboard.title)
    await fetchGrafanaEmbedUrl(dashboard.uid)
    setEmbedModalVisible(true)
  }

  const handleOpenConsole = () => {
    if (grafanaConsoleUrl) window.open(grafanaConsoleUrl, '_blank')
  }

  const filteredDashboards = useMemo(() => {
    if (!searchDash) return grafanaDashboards
    return grafanaDashboards.filter((d) => d.title.toLowerCase().includes(searchDash.toLowerCase()))
  }, [grafanaDashboards, searchDash])

  const dashboardColumns: ColumnsType<GrafanaDashboard> = [
    {
      title: '标题', dataIndex: 'title', key: 'title', width: 250,
      render: (t: string, r: GrafanaDashboard) => (
        <Space>
          {r.isStarred && <StarOutlined style={{ color: '#faad14' }} />}
          <Text>{t}</Text>
        </Space>
      ),
    },
    { title: 'UID', dataIndex: 'uid', key: 'uid', width: 120, render: (v: string) => <Text code>{v}</Text> },
    { title: '类型', dataIndex: 'type', key: 'type', width: 100, render: (t: string) => <Tag>{t}</Tag> },
    { title: '文件夹', dataIndex: 'folderTitle', key: 'folderTitle', width: 150, render: (v: string) => v || 'General' },
    { title: '标签', dataIndex: 'tags', key: 'tags', width: 200, render: (tags: string[]) => tags.map((t) => <Tag key={t}>{t}</Tag>) },
    { title: '更新时间', dataIndex: 'updated', key: 'updated', width: 160 },
    {
      title: '操作', key: 'actions', width: 160,
      render: (_: unknown, r: GrafanaDashboard) => (
        <Space size="small">
          <Tooltip title="嵌入查看"><Button size="small" icon={<FullscreenOutlined />} onClick={() => handleEmbedDashboard(r)}>嵌入</Button></Tooltip>
          <Tooltip title="在新窗口打开"><Button size="small" icon={<LinkOutlined />} onClick={() => window.open(r.url, '_blank')} /></Tooltip>
        </Space>
      ),
    },
  ]

  const alertColumns: ColumnsType<GrafanaAlert> = [
    { title: '名称', dataIndex: 'name', key: 'name', width: 250, ellipsis: true },
    {
      title: '状态', dataIndex: 'state', key: 'state', width: 100,
      render: (s: string) => {
        const cfg = alertStateMap[s] || { color: 'default', icon: null }
        return <Tag color={cfg.color} icon={cfg.icon}>{s}</Tag>
      },
    },
    { title: '规则', dataIndex: 'ruleName', key: 'ruleName', width: 200, ellipsis: true },
    { title: '仪表盘', dataIndex: 'dashboardName', key: 'dashboardName', width: 180, ellipsis: true },
    { title: '面板', dataIndex: 'panelName', key: 'panelName', width: 150, ellipsis: true },
    { title: '严重级别', dataIndex: 'severity', key: 'severity', width: 100, render: (s: string) => {
      const colorMap: Record<string, string> = { critical: 'red', warning: 'orange', info: 'blue' }
      return <Tag color={colorMap[s] || 'default'}>{s}</Tag>
    }},
    { title: '活跃时间', dataIndex: 'activeAt', key: 'activeAt', width: 160 },
  ]

  return (
    <div>
      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="仪表盘数" value={grafanaDashboards.length} prefix={<DashboardOutlined />} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="告警数" value={grafanaAlerts.length} prefix={<BellOutlined />} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="告警中" value={grafanaAlerts.filter((a) => a.state === 'ALERTING').length} valueStyle={{ color: '#cf1322' }} prefix={<WarningOutlined />} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small">
            <Tooltip title="打开 Grafana 控制台">
              <Statistic title="控制台" value="打开" prefix={<LinkOutlined />} valueStyle={{ fontSize: 16, cursor: 'pointer' }} onClick={handleOpenConsole} />
            </Tooltip>
          </Card>
        </Col>
      </Row>

      {error && <Alert message={error} type="error" showIcon closable style={{ marginBottom: 16 }} />}

      <Tabs activeKey={activeTab} onChange={setActiveTab} items={[
        {
          key: 'dashboards',
          label: `仪表盘 (${grafanaDashboards.length})`,
          children: (
            <Card
              extra={
                <Space>
                  <Input placeholder="搜索仪表盘" prefix={<SearchOutlined />} allowClear style={{ width: 250 }}
                    value={searchDash} onChange={(e) => setSearchDash(e.target.value)} />
                  <Button icon={<ReloadOutlined />} onClick={fetchGrafanaDashboards}>刷新</Button>
                </Space>
              }
            >
              <Spin spinning={loading}>
                {filteredDashboards.length === 0 && !loading ? (
                  <Empty description="暂无仪表盘" />
                ) : (
                  <Table dataSource={filteredDashboards} columns={dashboardColumns} rowKey="uid" pagination={{ pageSize: 20, showSizeChanger: true }} size="middle" />
                )}
              </Spin>
            </Card>
          ),
        },
        {
          key: 'alerts',
          label: `告警 (${grafanaAlerts.length})`,
          children: (
            <Card extra={<Button icon={<ReloadOutlined />} onClick={fetchGrafanaAlerts}>刷新</Button>}>
              <Spin spinning={loading}>
                {grafanaAlerts.length === 0 && !loading ? (
                  <Empty description="暂无告警" />
                ) : (
                  <Table dataSource={grafanaAlerts} columns={alertColumns} rowKey="id" pagination={{ pageSize: 20, showSizeChanger: true }} size="middle" />
                )}
              </Spin>
            </Card>
          ),
        },
      ]} />

      {/* 仪表盘嵌入弹窗 */}
      <Modal
        title={`嵌入仪表盘: ${embedTitle}`}
        open={embedModalVisible}
        onCancel={() => setEmbedModalVisible(false)}
        footer={null}
        width={900}
        destroyOnClose
      >
        {grafanaEmbedUrl ? (
          <iframe src={grafanaEmbedUrl} width="100%" height="500" style={{ border: 'none', borderRadius: 4 }} title={embedTitle} />
        ) : (
          <Spin tip="加载仪表盘..." style={{ display: 'block', textAlign: 'center', padding: 100 }} />
        )}
      </Modal>
    </div>
  )
}