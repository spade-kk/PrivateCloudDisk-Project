// ============================================================
// 系统资源监控页面
// ============================================================
import { useEffect } from 'react'
import { Row, Col, Card, Typography, Progress, Empty, Spin, Statistic, Button } from 'antd'
import { CloudServerOutlined, ReloadOutlined, ThunderboltOutlined } from '@ant-design/icons'
import { useSystemStore } from '@/stores/systemStore'
import PageHeader from '@/components/PageHeader'

const { Text } = Typography

function formatBytes(bytes: number): string {
  if (!bytes) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

export default function SystemResourcesPage() {
  const { resources, overview, fetchResources, fetchOverview, loading } = useSystemStore()

  useEffect(() => {
    fetchResources()
    fetchOverview()
  }, [fetchResources, fetchOverview])

  const cpuColor = (resources?.cpu?.usage || 0) > 80 ? '#ff4d4f' : (resources?.cpu?.usage || 0) > 60 ? '#faad14' : '#52c41a'
  const memColor = (resources?.memory?.usage || 0) > 80 ? '#ff4d4f' : (resources?.memory?.usage || 0) > 60 ? '#faad14' : '#52c41a'
  const diskColor = (resources?.disk?.usage || 0) > 85 ? '#ff4d4f' : (resources?.disk?.usage || 0) > 60 ? '#faad14' : '#52c41a'

  return (
    <div>
      <PageHeader
        title="系统资源"
        subtitle="实时系统资源使用情况"
        icon={<CloudServerOutlined />}
        actions={
          <Button icon={<ReloadOutlined />} onClick={() => { fetchResources(); fetchOverview(); }}>
            刷新
          </Button>
        }
      />

      <Spin spinning={loading.resources || loading.overview}>
        {resources ? (
          <>
            {/* 概览统计 */}
            <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
              <Col xs={24} sm={6}>
                <Card style={{ borderRadius: 8 }}>
                  <Statistic
                    title="CPU 核心数"
                    value={resources.cpu.cores}
                    prefix={<ThunderboltOutlined />}
                  />
                </Card>
              </Col>
              <Col xs={24} sm={6}>
                <Card style={{ borderRadius: 8 }}>
                  <Statistic
                    title="总内存"
                    value={formatBytes(resources.memory.total)}
                  />
                </Card>
              </Col>
              <Col xs={24} sm={6}>
                <Card style={{ borderRadius: 8 }}>
                  <Statistic
                    title="总磁盘"
                    value={formatBytes(resources.disk.total)}
                  />
                </Card>
              </Col>
              <Col xs={24} sm={6}>
                <Card style={{ borderRadius: 8 }}>
                  <Statistic
                    title="系统版本"
                    value={overview?.version || '-'}
                  />
                </Card>
              </Col>
            </Row>

            {/* 资源使用率 */}
            <Row gutter={[16, 16]}>
              <Col xs={24} md={8}>
                <Card title="CPU 使用率" style={{ borderRadius: 8 }}>
                  <div style={{ textAlign: 'center' }}>
                    <Progress
                      type="dashboard"
                      percent={Math.round(resources.cpu.usage)}
                      strokeColor={cpuColor}
                      size={140}
                    />
                    <Text type="secondary" style={{ display: 'block', marginTop: 8 }}>
                      {resources.cpu.cores} 核心
                    </Text>
                  </div>
                </Card>
              </Col>
              <Col xs={24} md={8}>
                <Card title="内存使用率" style={{ borderRadius: 8 }}>
                  <div style={{ textAlign: 'center' }}>
                    <Progress
                      type="dashboard"
                      percent={Math.round(resources.memory.usage)}
                      strokeColor={memColor}
                      size={140}
                    />
                    <Text type="secondary" style={{ display: 'block', marginTop: 8 }}>
                      {formatBytes(resources.memory.used)} / {formatBytes(resources.memory.total)}
                    </Text>
                  </div>
                </Card>
              </Col>
              <Col xs={24} md={8}>
                <Card title="磁盘使用率" style={{ borderRadius: 8 }}>
                  <div style={{ textAlign: 'center' }}>
                    <Progress
                      type="dashboard"
                      percent={Math.round(resources.disk.usage)}
                      strokeColor={diskColor}
                      size={140}
                    />
                    <Text type="secondary" style={{ display: 'block', marginTop: 8 }}>
                      {formatBytes(resources.disk.used)} / {formatBytes(resources.disk.total)}
                    </Text>
                  </div>
                </Card>
              </Col>
            </Row>

            {/* JVM 信息 */}
            {resources.jvm && (
              <Row style={{ marginTop: 16 }}>
                <Col span={24}>
                  <Card title="JVM 信息" style={{ borderRadius: 8 }}>
                    <Row gutter={16}>
                      <Col span={8}>
                        <Statistic title="堆内存已用" value={formatBytes(resources.jvm.heapUsed)} />
                      </Col>
                      <Col span={8}>
                        <Statistic title="堆内存最大" value={formatBytes(resources.jvm.heapMax)} />
                      </Col>
                      <Col span={8}>
                        <Statistic title="非堆内存" value={formatBytes(resources.jvm.nonHeapUsed)} />
                      </Col>
                    </Row>
                  </Card>
                </Col>
              </Row>
            )}

            {/* 网络信息 */}
            {resources.network && (
              <Row style={{ marginTop: 16 }}>
                <Col span={24}>
                  <Card title="网络流量" style={{ borderRadius: 8 }}>
                    <Row gutter={16}>
                      <Col span={12}>
                        <Statistic
                          title="入站流量"
                          value={formatBytes(resources.network.in) + '/s'}
                          valueStyle={{ color: '#1890ff' }}
                        />
                      </Col>
                      <Col span={12}>
                        <Statistic
                          title="出站流量"
                          value={formatBytes(resources.network.out) + '/s'}
                          valueStyle={{ color: '#52c41a' }}
                        />
                      </Col>
                    </Row>
                  </Card>
                </Col>
              </Row>
            )}
          </>
        ) : (
          <Empty description="暂无资源数据" />
        )}
      </Spin>
    </div>
  )
}