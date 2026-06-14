// ============================================================
// 存储统计页面
// ============================================================
import { useEffect } from 'react'
import { Row, Col, Card, Table, Typography, Empty, Spin, Progress, Statistic } from 'antd'
import { HddOutlined, FileOutlined, UserOutlined } from '@ant-design/icons'
import { useFilesStore } from '@/stores/filesStore'
import PageHeader from '@/components/PageHeader'

const { Text } = Typography

function formatBytes(bytes: number): string {
  if (!bytes) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB', 'PB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

export default function StorageStatsPage() {
  const { storageStats, fetchStorageStats, loading } = useFilesStore()

  useEffect(() => {
    fetchStorageStats()
  }, [fetchStorageStats])

  const stats = storageStats
  const usagePercent = stats
    ? Math.round((stats.usedStorageBytes / Math.max(stats.totalStorageBytes, 1)) * 100)
    : 0

  return (
    <div>
      <PageHeader
        title="存储统计"
        subtitle="全局存储使用情况概览"
        icon={<HddOutlined />}
      />

      <Spin spinning={loading}>
        {stats ? (
          <>
            <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
              <Col xs={24} sm={6}>
                <Card style={{ borderRadius: 8, textAlign: 'center' }}>
                  <Statistic
                    title="总存储空间"
                    value={formatBytes(stats.totalStorageBytes)}
                    prefix={<HddOutlined />}
                  />
                </Card>
              </Col>
              <Col xs={24} sm={6}>
                <Card style={{ borderRadius: 8, textAlign: 'center' }}>
                  <Statistic
                    title="已用空间"
                    value={formatBytes(stats.usedStorageBytes)}
                    prefix={<HddOutlined />}
                    valueStyle={{ color: '#faad14' }}
                  />
                </Card>
              </Col>
              <Col xs={24} sm={6}>
                <Card style={{ borderRadius: 8, textAlign: 'center' }}>
                  <Statistic
                    title="文件总数"
                    value={stats.fileCount}
                    prefix={<FileOutlined />}
                  />
                </Card>
              </Col>
              <Col xs={24} sm={6}>
                <Card style={{ borderRadius: 8, textAlign: 'center' }}>
                  <Statistic
                    title="注册用户"
                    value={stats.userCount}
                    prefix={<UserOutlined />}
                  />
                </Card>
              </Col>
            </Row>

            <Row gutter={[16, 16]}>
              <Col xs={24} lg={12}>
                <Card title="存储使用率" style={{ borderRadius: 8 }}>
                  <div style={{ textAlign: 'center' }}>
                    <Progress
                      type="dashboard"
                      percent={usagePercent}
                      strokeColor={usagePercent > 80 ? '#ff4d4f' : usagePercent > 60 ? '#faad14' : '#52c41a'}
                      format={(p) => `${p}%`}
                    />
                    <Text type="secondary" style={{ display: 'block', marginTop: 8 }}>
                      {formatBytes(stats.usedStorageBytes)} / {formatBytes(stats.totalStorageBytes)}
                    </Text>
                  </div>
                </Card>
              </Col>

              <Col xs={24} lg={12}>
                <Card title="各类型存储分布" style={{ borderRadius: 8 }}>
                  {stats.storageByType && stats.storageByType.length > 0 ? (
                    <Table
                      dataSource={stats.storageByType}
                      rowKey="type"
                      pagination={false}
                      size="small"
                      columns={[
                        { title: '类型', dataIndex: 'type', key: 'type' },
                        { title: '大小', dataIndex: 'bytes', key: 'bytes', render: (b: number) => formatBytes(b) },
                        { title: '文件数', dataIndex: 'count', key: 'count', render: (c: number) => c.toLocaleString() },
                      ]}
                    />
                  ) : (
                    <Empty description="暂无数据" />
                  )}
                </Card>
              </Col>
            </Row>

            {/* Top 用户存储 */}
            <Row style={{ marginTop: 16 }}>
              <Col span={24}>
                <Card title="存储占用 Top 用户" style={{ borderRadius: 8 }}>
                  {stats.topUsers && stats.topUsers.length > 0 ? (
                    <Table
                      dataSource={stats.topUsers}
                      rowKey="userId"
                      pagination={false}
                      size="small"
                      columns={[
                        { title: '用户名', dataIndex: 'name', key: 'name' },
                        { title: '存储用量', dataIndex: 'bytes', key: 'bytes', render: (b: number) => formatBytes(b) },
                        { title: '文件数', dataIndex: 'fileCount', key: 'fileCount', render: (c: number) => c.toLocaleString() },
                      ]}
                    />
                  ) : (
                    <Empty description="暂无数据" />
                  )}
                </Card>
              </Col>
            </Row>
          </>
        ) : (
          <Empty description="暂无存储统计数据" />
        )}
      </Spin>
    </div>
  )
}