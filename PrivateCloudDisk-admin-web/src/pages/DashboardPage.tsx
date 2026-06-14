// ============================================================
// 仪表盘页面
// ============================================================
import { useEffect, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { Row, Col, Card, Typography, Table, Tag, List, Space, Progress, Spin, Empty, theme } from 'antd'
import {
  UserOutlined,
  FileOutlined,
  HddOutlined,
  WarningOutlined,
  TeamOutlined,
  ThunderboltOutlined,
  ClockCircleOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
} from '@ant-design/icons'
import { useDashboardStore } from '@/stores/dashboardStore'
import StatCard from '@/components/StatCard'
import PageHeader from '@/components/PageHeader'

const { Text, Title } = Typography

// 格式化字节
function formatBytes(bytes: number): string {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

// 格式化数字
function formatNumber(num: number): string {
  if (num >= 1e9) return (num / 1e9).toFixed(1) + 'B'
  if (num >= 1e6) return (num / 1e6).toFixed(1) + 'M'
  if (num >= 1e3) return (num / 1e3).toFixed(1) + 'K'
  return num.toString()
}

// 简易柱状图组件
function SimpleBarChart({ data, title, color }: { data: { label: string; value: number }[]; title: string; color: string }) {
  const max = Math.max(...data.map((d) => d.value), 1)
  return (
    <div>
      <Text type="secondary" style={{ fontSize: 12, marginBottom: 8, display: 'block' }}>
        {title}
      </Text>
      <div style={{ display: 'flex', alignItems: 'flex-end', gap: 4, height: 120 }}>
        {data.map((item, i) => (
          <div key={i} style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', height: '100%', justifyContent: 'flex-end' }}>
            <Text style={{ fontSize: 10, marginBottom: 2 }}>{item.value}</Text>
            <div
              style={{
                width: '100%',
                maxWidth: 40,
                height: `${(item.value / max) * 100}%`,
                minHeight: 4,
                background: color,
                borderRadius: '4px 4px 0 0',
                transition: 'height 0.3s',
              }}
            />
            <Text style={{ fontSize: 10, marginTop: 4 }}>{item.label}</Text>
          </div>
        ))}
      </div>
    </div>
  )
}

export default function DashboardPage() {
  const { data, loading, fetchDashboard } = useDashboardStore()
  const navigate = useNavigate()
  const { token: themeToken } = theme.useToken()

  useEffect(() => {
    fetchDashboard()
  }, [fetchDashboard])

  // 文件类型分布数据
  const fileTypeChartData = useMemo(() => {
    if (!data?.fileTypeDistribution) return []
    return data.fileTypeDistribution.slice(0, 7).map((item) => ({
      label: item.type,
      value: item.count,
    }))
  }, [data])

  // 告警列表
  const alerts = data?.alerts || []

  // 最近活动表格列
  const activityColumns = [
    { title: '用户', dataIndex: 'userName', key: 'userName', width: 100, ellipsis: true },
    { title: '操作', dataIndex: 'action', key: 'action', width: 120 },
    {
      title: '详情',
      dataIndex: 'detail',
      key: 'detail',
      ellipsis: true,
      render: (text: string) => <Text type="secondary">{text || '-'}</Text>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 80,
      render: (status: string) => (
        <Tag color={status === 'SUCCESS' ? 'green' : 'red'} icon={status === 'SUCCESS' ? <CheckCircleOutlined /> : <CloseCircleOutlined />}>
          {status === 'SUCCESS' ? '成功' : '失败'}
        </Tag>
      ),
    },
    {
      title: '时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 160,
      render: (text: string) => new Date(text).toLocaleString('zh-CN'),
    },
  ]

  return (
    <div>
      <PageHeader
        title="仪表盘"
        subtitle="系统运行概览与关键指标"
        icon={<ThunderboltOutlined style={{ color: themeToken.colorPrimary }} />}
      />

      <Spin spinning={loading}>
        {/* 统计卡片 */}
        <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
          <Col xs={24} sm={12} lg={6}>
            <StatCard
              title="总用户数"
              value={data?.overview?.totalUsers || 0}
              prefix={<TeamOutlined />}
              trend={2.5}
              trendLabel="较上月"
              onClick={() => navigate('/users')}
            />
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <StatCard
              title="活跃用户(24h)"
              value={data?.overview?.activeUsers24h || 0}
              prefix={<UserOutlined />}
              trend={5.1}
              trendLabel="较昨日"
            />
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <StatCard
              title="文件总数"
              value={formatNumber(data?.overview?.totalFiles || 0)}
              prefix={<FileOutlined />}
              trend={3.2}
              trendLabel="较上月"
              onClick={() => navigate('/files')}
            />
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <StatCard
              title="存储用量"
              value={formatBytes(data?.overview?.totalStorageBytes || 0)}
              prefix={<HddOutlined />}
              trend={8.7}
              trendLabel="较上月"
              onClick={() => navigate('/files/storage')}
            />
          </Col>
        </Row>

        {/* 系统资源 */}
        <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
          <Col xs={24} sm={12} lg={6}>
            <Card size="small" title="CPU 使用率" style={{ borderRadius: 8 }}>
              <Progress
                type="circle"
                percent={data?.overview?.cpuUsage || 0}
                size={80}
                strokeColor={themeToken.colorPrimary}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <Card size="small" title="内存使用率" style={{ borderRadius: 8 }}>
              <Progress
                type="circle"
                percent={data?.overview?.memoryUsage || 0}
                size={80}
                strokeColor={
                  (data?.overview?.memoryUsage || 0) > 80 ? '#ff4d4f' : '#52c41a'
                }
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <Card size="small" title="磁盘使用率" style={{ borderRadius: 8 }}>
              <Progress
                type="circle"
                percent={data?.overview?.diskUsage || 0}
                size={80}
                strokeColor={
                  (data?.overview?.diskUsage || 0) > 85 ? '#ff4d4f' : '#faad14'
                }
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <Card size="small" title="系统运行时间" style={{ borderRadius: 8 }}>
              <div style={{ textAlign: 'center', padding: '8px 0' }}>
                <ClockCircleOutlined style={{ fontSize: 24, color: themeToken.colorPrimary }} />
                <Title level={5} style={{ margin: '8px 0 0' }}>
                  {data?.overview?.uptime || '-'}
                </Title>
              </div>
            </Card>
          </Col>
        </Row>

        <Row gutter={[16, 16]}>
          {/* 最近活动 */}
          <Col xs={24} lg={14}>
            <Card
              title="最近活动"
              style={{ borderRadius: 8 }}
              extra={
                <a onClick={() => navigate('/audit')}>查看全部</a>
              }
            >
              <Table
                columns={activityColumns}
                dataSource={data?.recentActivities || []}
                rowKey="id"
                pagination={false}
                size="small"
                locale={{ emptyText: <Empty description="暂无活动" /> }}
                scroll={{ x: 600 }}
              />
            </Card>
          </Col>

          {/* 右侧：告警与文件类型 */}
          <Col xs={24} lg={10}>
            <Row gutter={[0, 16]}>
              {/* 告警 */}
              <Col span={24}>
                <Card
                  title={
                    <Space>
                      <WarningOutlined style={{ color: '#faad14' }} />
                      <span>系统告警</span>
                    </Space>
                  }
                  style={{ borderRadius: 8 }}
                  size="small"
                >
                  {alerts.length > 0 ? (
                    <List
                      size="small"
                      dataSource={alerts}
                      renderItem={(item) => (
                        <List.Item>
                          <List.Item.Meta
                            avatar={
                              <Tag
                                color={
                                  item.severity === 'CRITICAL'
                                    ? 'red'
                                    : item.severity === 'HIGH'
                                    ? 'orange'
                                    : 'blue'
                                }
                              >
                                {item.severity}
                              </Tag>
                            }
                            title={item.type}
                            description={
                              <Text type="secondary" ellipsis>
                                {item.message}
                              </Text>
                            }
                          />
                        </List.Item>
                      )}
                    />
                  ) : (
                    <Empty description="暂无告警" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                  )}
                </Card>
              </Col>

              {/* 文件类型分布 */}
              <Col span={24}>
                <Card title="文件类型分布" style={{ borderRadius: 8 }} size="small">
                  {fileTypeChartData.length > 0 ? (
                    <SimpleBarChart
                      data={fileTypeChartData}
                      title=""
                      color={themeToken.colorPrimary}
                    />
                  ) : (
                    <Empty description="暂无数据" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                  )}
                </Card>
              </Col>
            </Row>
          </Col>
        </Row>
      </Spin>
    </div>
  )
}