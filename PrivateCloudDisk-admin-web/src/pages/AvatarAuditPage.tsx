// ============================================================
// 头像审核页面 - 企业级 MQ 事件对接
// 展示：审核队列、审核操作、MQ 状态跟踪、风险评分等
// ============================================================
import { useEffect, useState, useMemo } from 'react'
import {
  Row, Col, Card, Typography, Table, Tag, Spin, Button, Space,
  Modal, Image, Descriptions, Statistic, Progress, Badge, Tabs,
  message, Tooltip, Select, Input, DatePicker, Divider, Alert, Empty,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  AuditOutlined, CheckCircleOutlined, CloseCircleOutlined,
  ExclamationCircleOutlined, SafetyCertificateOutlined,
  UserOutlined, ClockCircleOutlined, ReloadOutlined,
  EyeOutlined, PictureOutlined, ThunderboltOutlined,
  ApiOutlined, WarningOutlined, FileSearchOutlined,
  BugOutlined, SendOutlined, SyncOutlined, CloudServerOutlined,
  PieChartOutlined, BarChartOutlined, FilterOutlined,
  ClearOutlined, SwapOutlined, DeleteOutlined, TeamOutlined,
} from '@ant-design/icons'
import type { AvatarAuditItem } from '@/types/api'
import { mockAvatarAudits } from '@/mock/data'
import PageHeader from '@/components/PageHeader'

const { Text, Title, Paragraph } = Typography
const { RangePicker } = DatePicker

// 风险等级颜色
const riskColorMap: Record<string, string> = {
  LOW: 'green',
  MEDIUM: 'orange',
  HIGH: 'red',
}
const riskLabelMap: Record<string, string> = {
  LOW: '低风险',
  MEDIUM: '中风险',
  HIGH: '高风险',
}

// 审核状态
const auditStatusMap: Record<string, { color: string; text: string; icon: React.ReactNode }> = {
  PENDING: { color: 'blue', text: '待审核', icon: <ClockCircleOutlined /> },
  APPROVED: { color: 'green', text: '已通过', icon: <CheckCircleOutlined /> },
  REJECTED: { color: 'red', text: '已拒绝', icon: <CloseCircleOutlined /> },
}

// MQ 状态
const mqStatusMap: Record<string, { color: string; text: string; icon: React.ReactNode }> = {
  SENT: { color: 'blue', text: '已发送', icon: <SendOutlined /> },
  RECEIVED: { color: 'cyan', text: '已接收', icon: <CloudServerOutlined /> },
  PROCESSED: { color: 'green', text: '已处理', icon: <CheckCircleOutlined /> },
  FAILED: { color: 'red', text: '处理失败', icon: <CloseCircleOutlined /> },
}

// 格式分数
function formatScore(score: number): { color: string; level: string } {
  if (score >= 0.7) return { color: '#ff4d4f', level: '高' }
  if (score >= 0.4) return { color: '#faad14', level: '中' }
  return { color: '#52c41a', level: '低' }
}

export default function AvatarAuditPage() {
  const [loading, setLoading] = useState(true)
  const [audits, setAudits] = useState<AvatarAuditItem[]>([])
  const [selectedAudit, setSelectedAudit] = useState<AvatarAuditItem | null>(null)
  const [previewVisible, setPreviewVisible] = useState(false)
  const [approveLoading, setApproveLoading] = useState(false)
  const [rejectLoading, setRejectLoading] = useState(false)
  const [activeTab, setActiveTab] = useState('all')
  const [filterStatus, setFilterStatus] = useState<string | undefined>(undefined)
  const [filterRisk, setFilterRisk] = useState<string | undefined>(undefined)
  const [searchText, setSearchText] = useState('')

  useEffect(() => {
    // 模拟加载
    const timer = setTimeout(() => {
      setAudits(mockAvatarAudits)
      setLoading(false)
    }, 400)
    return () => clearTimeout(timer)
  }, [])

  // 统计数据
  const stats = useMemo(() => ({
    total: audits.length,
    pending: audits.filter((a) => a.auditStatus === 'PENDING').length,
    approved: audits.filter((a) => a.auditStatus === 'APPROVED').length,
    rejected: audits.filter((a) => a.auditStatus === 'REJECTED').length,
    highRisk: audits.filter((a) => a.overallRisk === 'HIGH').length,
    mqProcessed: audits.filter((a) => a.mqStatus === 'PROCESSED').length,
    mqFailed: audits.filter((a) => a.mqStatus === 'FAILED').length,
  }), [audits])

  // 过滤
  const filteredAudits = useMemo(() => {
    let result = audits
    if (activeTab === 'pending') result = result.filter((a) => a.auditStatus === 'PENDING')
    else if (activeTab === 'approved') result = result.filter((a) => a.auditStatus === 'APPROVED')
    else if (activeTab === 'rejected') result = result.filter((a) => a.auditStatus === 'REJECTED')
    else if (activeTab === 'high-risk') result = result.filter((a) => a.overallRisk === 'HIGH')
    else if (activeTab === 'mq-failed') result = result.filter((a) => a.mqStatus === 'FAILED')

    if (filterStatus) result = result.filter((a) => a.auditStatus === filterStatus)
    if (filterRisk) result = result.filter((a) => a.overallRisk === filterRisk)
    if (searchText) {
      const lower = searchText.toLowerCase()
      result = result.filter((a) =>
        a.userName.toLowerCase().includes(lower) ||
        a.userEmail.toLowerCase().includes(lower) ||
        a.userId.toLowerCase().includes(lower) ||
        a.mqEventId.toLowerCase().includes(lower)
      )
    }
    return result
  }, [audits, activeTab, filterStatus, filterRisk, searchText])

  // 审核通过
  const handleApprove = (record: AvatarAuditItem) => {
    setApproveLoading(true)
    setTimeout(() => {
      setAudits((prev) =>
        prev.map((a) =>
          a.id === record.id
            ? { ...a, auditStatus: 'APPROVED' as const, auditTime: new Date().toISOString(), auditor: '超级管理员', mqStatus: 'PROCESSED' as const }
            : a
        )
      )
      setApproveLoading(false)
      message.success('审核通过，已通过 MQ 通知用户')
      setPreviewVisible(false)
    }, 500)
  }

  // 审核拒绝
  const handleReject = (record: AvatarAuditItem, reason: string) => {
    setRejectLoading(true)
    setTimeout(() => {
      setAudits((prev) =>
        prev.map((a) =>
          a.id === record.id
            ? { ...a, auditStatus: 'REJECTED' as const, auditTime: new Date().toISOString(), auditor: '超级管理员', auditRemark: reason, reason, mqStatus: 'PROCESSED' as const }
            : a
        )
      )
      setRejectLoading(false)
      message.success('已拒绝，已通过 MQ 通知用户')
      setPreviewVisible(false)
    }, 500)
  }

  // 重发 MQ
  const handleResendMQ = (record: AvatarAuditItem) => {
    message.loading('正在重发 MQ 事件...', 0.5).then(() => {
      setAudits((prev) =>
        prev.map((a) =>
          a.id === record.id ? { ...a, mqStatus: 'SENT' as const } : a
        )
      )
      message.success('MQ 事件已重新发送')
    })
  }

  const columns: ColumnsType<AvatarAuditItem> = [
    {
      title: '用户', dataIndex: 'userName', key: 'userName', width: 100, fixed: 'left',
      render: (text: string, record: AvatarAuditItem) => (
        <Space>
          <Avatar size={28} src={record.currentAvatar} icon={<UserOutlined />} />
          <div>
            <div style={{ fontWeight: 500 }}>{text}</div>
            <Text type="secondary" style={{ fontSize: 11 }}>{record.userEmail}</Text>
          </div>
        </Space>
      ),
    },
    {
      title: '头像预览', key: 'preview', width: 80,
      render: (_: unknown, record: AvatarAuditItem) => (
        <Space>
          <Image
            src={record.currentAvatar}
            width={40}
            height={40}
            style={{ borderRadius: 6, objectFit: 'cover', cursor: 'pointer' }}
            preview={{ mask: <EyeOutlined /> }}
          />
          {record.auditStatus === 'PENDING' && (
            <>
              <SwapOutlined style={{ color: '#8c8c8c', fontSize: 12 }} />
              <Image
                src={record.proposedAvatar}
                width={40}
                height={40}
                style={{ borderRadius: 6, objectFit: 'cover', cursor: 'pointer', border: '2px dashed #faad14' }}
                preview={{ mask: <EyeOutlined /> }}
              />
            </>
          )}
        </Space>
      ),
    },
    {
      title: '风险等级', dataIndex: 'overallRisk', key: 'overallRisk', width: 90,
      render: (risk: string) => (
        <Tag color={riskColorMap[risk]} icon={risk === 'HIGH' ? <WarningOutlined /> : undefined}>
          {riskLabelMap[risk]}
        </Tag>
      ),
      sorter: (a, b) => ['LOW', 'MEDIUM', 'HIGH'].indexOf(a.overallRisk) - ['LOW', 'MEDIUM', 'HIGH'].indexOf(b.overallRisk),
    },
    {
      title: 'NSFW', dataIndex: 'nsfwScore', key: 'nsfwScore', width: 90,
      render: (score: number) => {
        const f = formatScore(score)
        return <Progress percent={Math.round(score * 100)} size="small" strokeColor={f.color} format={() => f.level} />
      },
    },
    {
      title: '暴力', dataIndex: 'violenceScore', key: 'violenceScore', width: 90,
      render: (score: number) => {
        const f = formatScore(score)
        return <Progress percent={Math.round(score * 100)} size="small" strokeColor={f.color} format={() => f.level} />
      },
    },
    {
      title: '政治', dataIndex: 'politicalScore', key: 'politicalScore', width: 90,
      render: (score: number) => {
        const f = formatScore(score)
        return <Progress percent={Math.round(score * 100)} size="small" strokeColor={f.color} format={() => f.level} />
      },
    },
    {
      title: '审核状态', dataIndex: 'auditStatus', key: 'auditStatus', width: 100,
      render: (status: string) => {
        const info = auditStatusMap[status]
        return <Tag color={info?.color} icon={info?.icon}>{info?.text}</Tag>
      },
    },
    {
      title: 'MQ 状态', dataIndex: 'mqStatus', key: 'mqStatus', width: 100,
      render: (status: string) => {
        const info = mqStatusMap[status]
        return (
          <Tooltip title={`事件ID: ${status}`}>
            <Tag color={info?.color} icon={info?.icon}>{info?.text}</Tag>
          </Tooltip>
        )
      },
    },
    {
      title: '提交时间', dataIndex: 'submitTime', key: 'submitTime', width: 140,
      render: (text: string) => new Date(text).toLocaleString('zh-CN'),
      sorter: (a, b) => new Date(a.submitTime).getTime() - new Date(b.submitTime).getTime(),
    },
    {
      title: '审核人', dataIndex: 'auditor', key: 'auditor', width: 100,
      render: (text: string | null) => text || <Text type="secondary">-</Text>,
    },
    {
      title: '操作', key: 'action', width: 120, fixed: 'right',
      render: (_: unknown, record: AvatarAuditItem) => (
        <Space direction="vertical" size={2}>
          <Button
            type="link" size="small" icon={<EyeOutlined />}
            onClick={() => { setSelectedAudit(record); setPreviewVisible(true) }}
          >
            详情
          </Button>
          {record.mqStatus === 'FAILED' && (
            <Button
              type="link" size="small" icon={<ReloadOutlined />} danger
              onClick={() => handleResendMQ(record)}
            >
              重发MQ
            </Button>
          )}
        </Space>
      ),
    },
  ]

  return (
    <div>
      <PageHeader
        title="头像审核"
        subtitle="审核用户头像变更，对接 MQ 事件通知系统"
        icon={<AuditOutlined style={{ color: '#1677ff' }} />}
        actions={
          <Button icon={<ReloadOutlined />} onClick={() => {
            setLoading(true)
            setTimeout(() => { setAudits(mockAvatarAudits); setLoading(false) }, 300)
          }}>刷新</Button>
        }
      />

      {/* ── 统计卡片 ── */}
      <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
        <Col xs={24} sm={12} lg={8} xl={4}>
          <Card size="small" style={{ borderRadius: 8 }}>
            <Statistic title="待审核" value={stats.pending} valueStyle={{ color: '#1677ff' }}
              prefix={<ClockCircleOutlined />} suffix={<Text type="secondary" style={{ fontSize: 12 }}>项</Text>} />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={8} xl={4}>
          <Card size="small" style={{ borderRadius: 8 }}>
            <Statistic title="已通过" value={stats.approved} valueStyle={{ color: '#52c41a' }}
              prefix={<CheckCircleOutlined />} />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={8} xl={4}>
          <Card size="small" style={{ borderRadius: 8 }}>
            <Statistic title="已拒绝" value={stats.rejected} valueStyle={{ color: '#ff4d4f' }}
              prefix={<CloseCircleOutlined />} />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={8} xl={4}>
          <Card size="small" style={{ borderRadius: 8 }}>
            <Statistic title="高风险" value={stats.highRisk} valueStyle={{ color: '#ff4d4f' }}
              prefix={<WarningOutlined />} />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={8} xl={4}>
          <Card size="small" style={{ borderRadius: 8 }}>
            <Statistic title="MQ 已处理" value={stats.mqProcessed} valueStyle={{ color: '#52c41a' }}
              prefix={<ApiOutlined />} />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={8} xl={4}>
          <Card size="small" style={{ borderRadius: 8 }}>
            <Statistic title="MQ 失败" value={stats.mqFailed} valueStyle={{ color: stats.mqFailed > 0 ? '#ff4d4f' : '#8c8c8c' }}
              prefix={<BugOutlined />} />
          </Card>
        </Col>
      </Row>

      {/* ── MQ 状态提示 ── */}
      {stats.mqFailed > 0 && (
        <Alert
          message={`${stats.mqFailed} 条 MQ 消息处理失败，请检查消息队列服务状态`}
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          action={
            <Button size="small" type="primary" ghost>批量重试</Button>
          }
        />
      )}

      {/* ── 筛选 Tab ── */}
      <Card size="small" style={{ borderRadius: 8, marginBottom: 16 }}>
        <Row gutter={[16, 12]} align="middle">
          <Col flex="auto">
            <Tabs
              activeKey={activeTab}
              onChange={setActiveTab}
              size="small"
              items={[
                { key: 'all', label: `全部 (${stats.total})` },
                { key: 'pending', label: <Badge count={stats.pending} size="small" offset={[8, -2]}>待审核</Badge> },
                { key: 'approved', label: '已通过' },
                { key: 'rejected', label: '已拒绝' },
                { key: 'high-risk', label: <span style={{ color: '#ff4d4f' }}>高风险</span> },
                { key: 'mq-failed', label: <Badge count={stats.mqFailed} size="small" offset={[8, -2]}><span style={{ color: '#ff4d4f' }}>MQ 失败</span></Badge> },
              ]}
            />
          </Col>
          <Col>
            <Space>
              <Select
                allowClear
                placeholder="审核状态"
                value={filterStatus}
                onChange={setFilterStatus}
                style={{ width: 120 }}
                options={[
                  { value: 'PENDING', label: '待审核' },
                  { value: 'APPROVED', label: '已通过' },
                  { value: 'REJECTED', label: '已拒绝' },
                ]}
              />
              <Select
                allowClear
                placeholder="风险等级"
                value={filterRisk}
                onChange={setFilterRisk}
                style={{ width: 120 }}
                options={[
                  { value: 'LOW', label: '低风险' },
                  { value: 'MEDIUM', label: '中风险' },
                  { value: 'HIGH', label: '高风险' },
                ]}
              />
              <Input.Search
                placeholder="搜索用户/邮箱/事件ID"
                value={searchText}
                onChange={(e) => setSearchText(e.target.value)}
                style={{ width: 220 }}
                allowClear
              />
            </Space>
          </Col>
        </Row>
      </Card>

      {/* ── 审核列表 ── */}
      <Card size="small" style={{ borderRadius: 8 }}>
        <Spin spinning={loading}>
          <Table
            dataSource={filteredAudits}
            columns={columns}
            rowKey="id"
            size="small"
            pagination={{
              pageSize: 15,
              showSizeChanger: true,
              showTotal: (total) => `共 ${total} 条记录`,
            }}
            scroll={{ x: 1300 }}
            locale={{ emptyText: <Empty description="暂无审核记录" /> }}
          />
        </Spin>
      </Card>

      {/* ── 审核详情弹窗 ── */}
      <Modal
        title="头像审核详情"
        open={previewVisible}
        onCancel={() => setPreviewVisible(false)}
        width={720}
        footer={
          selectedAudit?.auditStatus === 'PENDING' ? [
            <Button key="cancel" onClick={() => setPreviewVisible(false)}>关闭</Button>,
            <Button
              key="reject" danger icon={<CloseCircleOutlined />}
              loading={rejectLoading}
              onClick={() => {
                Modal.confirm({
                  title: '拒绝原因',
                  content: (
                    <Select
                      style={{ width: '100%' }}
                      placeholder="请选择拒绝原因"
                      options={[
                        { value: '包含色情内容', label: '包含色情内容' },
                        { value: '包含暴力元素', label: '包含暴力元素' },
                        { value: '包含政治敏感内容', label: '包含政治敏感内容' },
                        { value: '图片不清晰', label: '图片不清晰' },
                        { value: '非本人真实照片', label: '非本人真实照片' },
                        { value: '其他违规内容', label: '其他违规内容' },
                      ]}
                      onChange={(value) => {
                        if (selectedAudit) handleReject(selectedAudit, value)
                      }}
                    />
                  ),
                  okText: '确认拒绝',
                  cancelText: '取消',
                })
              }}
            >
              拒绝
            </Button>,
            <Button
              key="approve" type="primary" icon={<CheckCircleOutlined />}
              loading={approveLoading}
              onClick={() => selectedAudit && handleApprove(selectedAudit)}
            >
              通过
            </Button>,
          ] : [
            <Button key="close" onClick={() => setPreviewVisible(false)}>关闭</Button>,
          ]
        }
      >
        {selectedAudit && (
          <div>
            {/* 头像对比 */}
            <Row gutter={16} style={{ marginBottom: 16 }}>
              <Col span={12}>
                <Card size="small" title="当前头像" style={{ textAlign: 'center' }}>
                  <Image src={selectedAudit.currentAvatar} width={120} height={120} style={{ borderRadius: 8, objectFit: 'cover' }} />
                </Card>
              </Col>
              <Col span={12}>
                <Card size="small" title="新头像" style={{ textAlign: 'center' }}>
                  <Image src={selectedAudit.proposedAvatar} width={120} height={120} style={{ borderRadius: 8, objectFit: 'cover' }} />
                </Card>
              </Col>
            </Row>

            {/* 用户信息 */}
            <Descriptions column={2} size="small" bordered style={{ marginBottom: 16 }}>
              <Descriptions.Item label="用户">{selectedAudit.userName}</Descriptions.Item>
              <Descriptions.Item label="邮箱">{selectedAudit.userEmail}</Descriptions.Item>
              <Descriptions.Item label="用户ID">{selectedAudit.userId}</Descriptions.Item>
              <Descriptions.Item label="提交时间">{new Date(selectedAudit.submitTime).toLocaleString('zh-CN')}</Descriptions.Item>
              <Descriptions.Item label="图片格式">{selectedAudit.imageFormat}</Descriptions.Item>
              <Descriptions.Item label="图片大小">{(selectedAudit.imageSize / 1024).toFixed(1)} KB</Descriptions.Item>
            </Descriptions>

            {/* 风险评分 */}
            <Card size="small" title="AI 风险评分" style={{ marginBottom: 16 }}>
              <Row gutter={[16, 16]}>
                <Col span={8} style={{ textAlign: 'center' }}>
                  <Progress type="circle" percent={Math.round(selectedAudit.nsfwScore * 100)} size={80}
                    strokeColor={formatScore(selectedAudit.nsfwScore).color} />
                  <div style={{ marginTop: 8 }}>NSFW</div>
                </Col>
                <Col span={8} style={{ textAlign: 'center' }}>
                  <Progress type="circle" percent={Math.round(selectedAudit.violenceScore * 100)} size={80}
                    strokeColor={formatScore(selectedAudit.violenceScore).color} />
                  <div style={{ marginTop: 8 }}>暴力</div>
                </Col>
                <Col span={8} style={{ textAlign: 'center' }}>
                  <Progress type="circle" percent={Math.round(selectedAudit.politicalScore * 100)} size={80}
                    strokeColor={formatScore(selectedAudit.politicalScore).color} />
                  <div style={{ marginTop: 8 }}>政治</div>
                </Col>
              </Row>
              <Divider />
              <div style={{ textAlign: 'center' }}>
                <Text strong>综合风险等级：</Text>
                <Tag color={riskColorMap[selectedAudit.overallRisk]} style={{ marginLeft: 8, fontSize: 14, padding: '4px 12px' }}>
                  {riskLabelMap[selectedAudit.overallRisk]}
                </Tag>
              </div>
            </Card>

            {/* MQ 事件信息 */}
            <Card size="small" title="MQ 事件信息" extra={
              <Tag color={mqStatusMap[selectedAudit.mqStatus]?.color} icon={mqStatusMap[selectedAudit.mqStatus]?.icon}>
                {mqStatusMap[selectedAudit.mqStatus]?.text}
              </Tag>
            }>
              <Descriptions column={1} size="small" bordered>
                <Descriptions.Item label="事件ID">
                  <Text code>{selectedAudit.mqEventId}</Text>
                </Descriptions.Item>
                <Descriptions.Item label="MQ 状态">
                  <Tag color={mqStatusMap[selectedAudit.mqStatus]?.color}>
                    {mqStatusMap[selectedAudit.mqStatus]?.text}
                  </Tag>
                </Descriptions.Item>
                <Descriptions.Item label="审核人">{selectedAudit.auditor || '-'}</Descriptions.Item>
                <Descriptions.Item label="审核时间">{selectedAudit.auditTime ? new Date(selectedAudit.auditTime).toLocaleString('zh-CN') : '-'}</Descriptions.Item>
                <Descriptions.Item label="审核备注">{selectedAudit.auditRemark || '-'}</Descriptions.Item>
              </Descriptions>
            </Card>

            {selectedAudit.mqStatus === 'FAILED' && (
              <Alert
                type="error"
                showIcon
                message="MQ 消息处理失败"
                description="请检查消息队列服务是否正常运行，或点击重发按钮重新发送事件。"
                style={{ marginTop: 16 }}
                action={<Button size="small" danger onClick={() => handleResendMQ(selectedAudit)}>重发 MQ</Button>}
              />
            )}
          </div>
        )}
      </Modal>
    </div>
  )
}