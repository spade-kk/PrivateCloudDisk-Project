// ============================================================
// Prometheus 集成页面
// PromQL 查询、目标管理、告警规则、控制台集成
// ============================================================
import { useEffect, useState, useCallback, useMemo } from 'react'
import {
  Card, Table, Tag, Space, Button, Modal, Input, Select, Form,
  Spin, Alert, Empty, Tabs, Typography, message, Statistic, Row, Col, Tooltip, Badge, Descriptions,
} from 'antd'
import {
  ReloadOutlined, LinkOutlined, SearchOutlined, ThunderboltOutlined,
  CheckCircleOutlined, WarningOutlined, CloseCircleOutlined, SyncOutlined,
  LineChartOutlined, AimOutlined, BellOutlined, CodeOutlined,
} from '@ant-design/icons'
import { useMonitorIntegrationStore } from '@/stores/monitorIntegrationStore'
import type { PrometheusTarget, PrometheusRule } from '@/api/monitor'
import type { ColumnsType } from 'antd/es/table'

const { Text, Paragraph } = Typography

const targetHealthMap: Record<string, { color: string; icon: React.ReactNode }> = {
  UP: { color: 'green', icon: <CheckCircleOutlined /> },
  DOWN: { color: 'red', icon: <CloseCircleOutlined /> },
  UNKNOWN: { color: 'default', icon: <WarningOutlined /> },
}

const ruleHealthMap: Record<string, { color: string }> = {
  OK: { color: 'green' },
  FIRING: { color: 'red' },
  PENDING: { color: 'orange' },
}

// 预置的 PromQL 示例查询
const PRESET_QUERIES = [
  { label: 'CPU 使用率', query: '100 - (avg by(instance)(rate(node_cpu_seconds_total{mode="idle"}[5m])) * 100)' },
  { label: '内存使用率', query: '(1 - (node_memory_MemAvailable_bytes / node_memory_MemTotal_bytes)) * 100' },
  { label: '磁盘使用率', query: '(1 - (node_filesystem_avail_bytes{mountpoint="/"} / node_filesystem_size_bytes{mountpoint="/"})) * 100' },
  { label: 'HTTP 请求速率', query: 'rate(http_requests_total[5m])' },
  { label: 'JVM 堆内存', query: 'jvm_memory_used_bytes{area="heap"}' },
  { label: 'Pod CPU', query: 'rate(container_cpu_usage_seconds_total[5m])' },
  { label: 'Pod 内存', query: 'container_memory_usage_bytes' },
  { label: '网络流量', query: 'rate(node_network_receive_bytes_total[5m])' },
]

export default function PrometheusIntegrationPage() {
  const {
    prometheusQueryResult, prometheusTargets, prometheusRules,
    prometheusConsoleUrl, loading, error,
    doQueryPrometheus, fetchPrometheusTargets, fetchPrometheusRules, fetchPrometheusConsoleUrl,
  } = useMonitorIntegrationStore()

  const [activeTab, setActiveTab] = useState('query')
  const [query, setQuery] = useState('')
  const [queryHistory, setQueryHistory] = useState<string[]>([])

  useEffect(() => {
    fetchPrometheusConsoleUrl()
  }, [fetchPrometheusConsoleUrl])

  useEffect(() => {
    if (activeTab === 'targets') fetchPrometheusTargets()
    else if (activeTab === 'rules') fetchPrometheusRules()
  }, [activeTab, fetchPrometheusTargets, fetchPrometheusRules])

  const handleExecuteQuery = async () => {
    if (!query.trim()) return
    await doQueryPrometheus(query)
    setQueryHistory((prev) => [query, ...prev.filter((q) => q !== query)].slice(0, 20))
  }

  const handlePresetQuery = (q: string) => {
    setQuery(q)
    doQueryPrometheus(q)
  }

  const handleOpenConsole = () => {
    if (prometheusConsoleUrl) window.open(prometheusConsoleUrl, '_blank')
  }

  const targetColumns: ColumnsType<PrometheusTarget> = [
    { title: 'Job', dataIndex: 'job', key: 'job', width: 150, render: (j: string) => <Tag color="blue">{j}</Tag> },
    { title: 'Instance', dataIndex: 'instance', key: 'instance', width: 200, render: (v: string) => <Text code>{v}</Text> },
    {
      title: '健康', dataIndex: 'health', key: 'health', width: 90,
      render: (h: string) => {
        const cfg = targetHealthMap[h] || { color: 'default', icon: null }
        return <Tag color={cfg.color} icon={cfg.icon}>{h}</Tag>
      },
    },
    { title: '最后采集', dataIndex: 'lastScrape', key: 'lastScrape', width: 160 },
    { title: '采集耗时', dataIndex: 'scrapeDuration', key: 'scrapeDuration', width: 100, render: (v: number) => `${v.toFixed(3)}s` },
    { title: '错误', dataIndex: 'lastError', key: 'lastError', width: 200, ellipsis: true, render: (v: string) => v ? <Text type="danger">{v}</Text> : '-' },
    { title: '标签', dataIndex: 'labels', key: 'labels', width: 200, render: (l: Record<string, string>) => (
      Object.entries(l || {}).filter(([k]) => k !== 'job' && k !== 'instance').map(([k, v]) => (
        <Tag key={k} style={{ marginBottom: 2 }}>{k}: {v}</Tag>
      ))
    )},
  ]

  const ruleColumns: ColumnsType<PrometheusRule> = [
    { title: '规则名称', dataIndex: 'name', key: 'name', width: 200, ellipsis: true },
    { title: '分组', dataIndex: 'group', key: 'group', width: 150, render: (g: string) => <Tag>{g}</Tag> },
    { title: '类型', dataIndex: 'type', key: 'type', width: 80, render: (t: string) => <Tag color="blue">{t}</Tag> },
    {
      title: '健康', dataIndex: 'health', key: 'health', width: 90,
      render: (h: string) => <Tag color={ruleHealthMap[h]?.color || 'default'}>{h}</Tag>,
    },
    { title: '表达式', dataIndex: 'expression', key: 'expression', width: 300, ellipsis: true, render: (v: string) => <Text code>{v}</Text> },
    { title: '持续时间', dataIndex: 'duration', key: 'duration', width: 90 },
    { title: '严重级别', dataIndex: 'severity', key: 'severity', width: 100, render: (s: string) => {
      const colorMap: Record<string, string> = { critical: 'red', warning: 'orange', info: 'blue' }
      return <Tag color={colorMap[s] || 'default'}>{s}</Tag>
    }},
    { title: '摘要', dataIndex: 'summary', key: 'summary', width: 200, ellipsis: true },
  ]

  return (
    <div>
      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="Targets" value={prometheusTargets?.activeTargets?.length || 0} prefix={<AimOutlined />} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="告警规则" value={prometheusRules.length} prefix={<BellOutlined />} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="Down" value={prometheusTargets?.activeTargets?.filter((t) => t.health === 'DOWN').length || 0} valueStyle={{ color: '#cf1322' }} prefix={<CloseCircleOutlined />} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small">
            <Tooltip title="打开 Prometheus 控制台">
              <Statistic title="控制台" value="打开" prefix={<LinkOutlined />} valueStyle={{ fontSize: 16, cursor: 'pointer' }} onClick={handleOpenConsole} />
            </Tooltip>
          </Card>
        </Col>
      </Row>

      {error && <Alert message={error} type="error" showIcon closable style={{ marginBottom: 16 }} />}

      <Tabs activeKey={activeTab} onChange={setActiveTab} items={[
        {
          key: 'query',
          label: 'PromQL 查询',
          children: (
            <>
              <Card style={{ marginBottom: 16 }}>
                <Space direction="vertical" style={{ width: '100%' }}>
                  <Space>
                    <Input.TextArea
                      placeholder="输入 PromQL 查询，例如: up"
                      value={query}
                      onChange={(e) => setQuery(e.target.value)}
                      rows={2}
                      style={{ width: 500 }}
                      onPressEnter={(e) => { e.preventDefault(); handleExecuteQuery() }}
                    />
                    <Button type="primary" icon={<ThunderboltOutlined />} onClick={handleExecuteQuery} loading={loading}>
                      执行
                    </Button>
                  </Space>
                  <div>
                    <Text type="secondary" style={{ marginRight: 8 }}>预置查询:</Text>
                    {PRESET_QUERIES.map((pq) => (
                      <Tag
                        key={pq.label}
                        style={{ cursor: 'pointer', marginBottom: 4 }}
                        color="blue"
                        onClick={() => handlePresetQuery(pq.query)}
                      >
                        {pq.label}
                      </Tag>
                    ))}
                  </div>
                </Space>
              </Card>

              {/* 查询结果 */}
              {prometheusQueryResult && (
                <Card title="查询结果">
                  <Descriptions bordered size="small" column={{ xs: 1, sm: 2 }}>
                    <Descriptions.Item label="结果类型">
                      <Tag color="blue">{prometheusQueryResult.resultType}</Tag>
                    </Descriptions.Item>
                    <Descriptions.Item label="结果数">{prometheusQueryResult.result?.length || 0}</Descriptions.Item>
                  </Descriptions>
                  {prometheusQueryResult.result && prometheusQueryResult.result.length > 0 && (
                    <Table
                      dataSource={prometheusQueryResult.result}
                      rowKey={(record, index) => `${record.metric?.__name__ || 'unknown'}-${index}`}
                      pagination={{ pageSize: 20 }}
                      size="small"
                      style={{ marginTop: 16 }}
                      columns={[
                        { title: '指标', key: 'metric', width: 300, render: (_: unknown, r: { metric: Record<string, string> }) => (
                          Object.entries(r.metric || {}).map(([k, v]) => <Tag key={k} style={{ marginBottom: 2 }}>{k}: {v}</Tag>)
                        )},
                        { title: '值', key: 'value', width: 200, render: (_: unknown, r: { value: [number, string] }) => {
                          if (!r.value) return '-'
                          return <Text code>{r.value[1]}</Text>
                        }},
                        { title: '时间戳', key: 'timestamp', width: 200, render: (_: unknown, r: { value: [number, string] }) => {
                          if (!r.value) return '-'
                          return new Date(r.value[0] * 1000).toLocaleString()
                        }},
                      ]}
                    />
                  )}
                </Card>
              )}

              {/* 查询历史 */}
              {queryHistory.length > 0 && (
                <Card title="查询历史" size="small" style={{ marginTop: 16 }}>
                  {queryHistory.map((h, i) => (
                    <Tag key={i} style={{ cursor: 'pointer', marginBottom: 4 }} onClick={() => setQuery(h)}>
                      {h.substring(0, 80)}{h.length > 80 ? '...' : ''}
                    </Tag>
                  ))}
                </Card>
              )}
            </>
          ),
        },
        {
          key: 'targets',
          label: `Targets (${prometheusTargets?.activeTargets?.length || 0})`,
          children: (
            <Card extra={<Button icon={<ReloadOutlined />} onClick={fetchPrometheusTargets}>刷新</Button>}>
              <Spin spinning={loading}>
                {!prometheusTargets || prometheusTargets.activeTargets.length === 0 ? (
                  <Empty description="暂无 Target" />
                ) : (
                  <Table dataSource={prometheusTargets.activeTargets} columns={targetColumns} rowKey="instance" pagination={{ pageSize: 20, showSizeChanger: true }} size="middle" scroll={{ x: 1200 }} />
                )}
              </Spin>
            </Card>
          ),
        },
        {
          key: 'rules',
          label: `告警规则 (${prometheusRules.length})`,
          children: (
            <Card extra={<Button icon={<ReloadOutlined />} onClick={fetchPrometheusRules}>刷新</Button>}>
              <Spin spinning={loading}>
                {prometheusRules.length === 0 && !loading ? (
                  <Empty description="暂无规则" />
                ) : (
                  <Table dataSource={prometheusRules} columns={ruleColumns} rowKey="name" pagination={{ pageSize: 20, showSizeChanger: true }} size="middle" scroll={{ x: 1200 }} />
                )}
              </Spin>
            </Card>
          ),
        },
      ]} />
    </div>
  )
}