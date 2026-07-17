// ============================================================
// XXL-Job 管理页面
// 任务管理、执行日志、调度器、控制台集成
// ============================================================
import { useEffect, useState, useCallback, useMemo } from 'react'
import {
  Card, Table, Tag, Space, Button, Modal, Input, Select, Form,
  Spin, Alert, Empty, Tabs, Typography, Popconfirm, message, Statistic, Row, Col, Descriptions, Tooltip, Switch, Badge,
} from 'antd'
import {
  ReloadOutlined, PlusOutlined, DeleteOutlined, EditOutlined,
  LinkOutlined, SearchOutlined, PlayCircleOutlined, PauseCircleOutlined,
  CheckCircleOutlined, WarningOutlined, CloseCircleOutlined, SyncOutlined,
  ClockCircleOutlined, ScheduleOutlined, HistoryOutlined,
} from '@ant-design/icons'
import { useMiddlewareStore } from '@/stores/middlewareStore'
import type { XXLJob, XXLJobLog, XXLJobGroup } from '@/api/middleware'
import type { ColumnsType } from 'antd/es/table'

const { Text } = Typography

const jobStatusMap: Record<string, { color: string; icon: React.ReactNode }> = {
  RUNNING: { color: 'blue', icon: <SyncOutlined spin /> },
  STOPPED: { color: 'default', icon: <PauseCircleOutlined /> },
  PENDING: { color: 'orange', icon: <ClockCircleOutlined /> },
  SUCCESS: { color: 'green', icon: <CheckCircleOutlined /> },
  FAILED: { color: 'red', icon: <CloseCircleOutlined /> },
}

const triggerTypeMap: Record<string, string> = {
  CRON: 'Cron',
  MANUAL: '手动',
  API: 'API',
  PARENT: '父任务',
  RETRY: '重试',
}

const executorRouteMap: Record<string, string> = {
  FIRST: '第一个',
  LAST: '最后一个',
  ROUND: '轮询',
  RANDOM: '随机',
  CONSISTENT_HASH: '一致性哈希',
  LEAST_FREQUENTLY_USED: '最不经常使用',
  LEAST_RECENTLY_USED: '最近最久未使用',
  FAILOVER: '故障转移',
  BUSYOVER: '忙碌转移',
  SHARDING_BROADCAST: '分片广播',
}

export default function XXLJobManagePage() {
  const {
    xxlJobs, xxlJobsTotal, xxlJobLogs, xxlJobLogsTotal,
    xxlJobGroups, xxlJobConsoleUrl, loading, error,
    fetchXXLJobs, fetchXXLJobLogs, fetchXXLJobGroups,
    doRunXXLJob, doStopXXLJob, doCreateXXLJob, doUpdateXXLJob, doDeleteXXLJob,
    fetchXXLJobConsoleUrl,
  } = useMiddlewareStore()

  const [activeTab, setActiveTab] = useState('jobs')
  const [jobModalVisible, setJobModalVisible] = useState(false)
  const [logDetailVisible, setLogDetailVisible] = useState(false)
  const [editingJobId, setEditingJobId] = useState<string | null>(null)
  const [currentLog, setCurrentLog] = useState<XXLJobLog | null>(null)
  const [jobForm] = Form.useForm()
  const [searchJob, setSearchJob] = useState('')
  const [groupFilter, setGroupFilter] = useState<number | undefined>()

  const loadJobs = useCallback(() => {
    fetchXXLJobs({ page: 1, pageSize: 50, jobDesc: searchJob || undefined, jobGroup: groupFilter })
  }, [fetchXXLJobs, searchJob, groupFilter])
  const loadLogs = useCallback(() => {
    fetchXXLJobLogs({ page: 1, pageSize: 50 })
  }, [fetchXXLJobLogs])
  const loadGroups = useCallback(() => { fetchXXLJobGroups() }, [fetchXXLJobGroups])

  useEffect(() => { loadJobs(); loadGroups() }, [loadJobs, loadGroups])
  useEffect(() => {
    if (activeTab === 'logs') loadLogs()
  }, [activeTab, loadLogs])

  const handleRunJob = async (jobId: number) => {
    const success = await doRunXXLJob(jobId)
    if (success) message.success('任务已触发')
    else message.error('触发失败')
  }

  const handleStopJob = async (jobId: number) => {
    const success = await doStopXXLJob(jobId)
    if (success) message.success('任务已停止')
    else message.error('停止失败')
  }

  const handleCreateJob = () => {
    setEditingJobId(null)
    jobForm.resetFields()
    setJobModalVisible(true)
  }

  const handleEditJob = (job: XXLJob) => {
    setEditingJobId(String(job.id))
    jobForm.setFieldsValue({
      jobGroup: job.jobGroup,
      jobDesc: job.jobDesc,
      author: job.author,
      scheduleType: job.scheduleType,
      scheduleConf: job.scheduleConf,
      executorHandler: job.executorHandler,
      executorParam: job.executorParam,
      executorRouteStrategy: job.executorRouteStrategy,
      executorBlockStrategy: job.executorBlockStrategy,
      timeout: job.timeout,
      failRetryCount: job.failRetryCount,
      triggerStatus: job.triggerStatus,
    })
    setJobModalVisible(true)
  }

  const handleSaveJob = async () => {
    try {
      const values = await jobForm.validateFields()
      let success: boolean
      if (editingJobId) {
        success = await doUpdateXXLJob(editingJobId, values)
      } else {
        success = await doCreateXXLJob(values)
      }
      if (success) {
        message.success(editingJobId ? '任务已更新' : '任务已创建')
        setJobModalVisible(false)
        loadJobs()
      }
    } catch {}
  }

  const handleDeleteJob = async (jobId: number) => {
    const success = await doDeleteXXLJob(jobId)
    if (success) { message.success('任务已删除'); loadJobs() }
    else message.error('删除失败')
  }

  const handleViewLog = (log: XXLJobLog) => {
    setCurrentLog(log)
    setLogDetailVisible(true)
  }

  const handleOpenConsole = async () => {
    await fetchXXLJobConsoleUrl()
    if (xxlJobConsoleUrl) window.open(xxlJobConsoleUrl, '_blank')
  }

  const summary = useMemo(() => {
    const running = xxlJobs.filter((j) => j.triggerStatus === 1).length
    const stopped = xxlJobs.filter((j) => j.triggerStatus === 0).length
    const successLogs = xxlJobLogs.filter((l) => l.handleCode === 200).length
    const failedLogs = xxlJobLogs.filter((l) => l.handleCode !== 200 && l.handleCode !== 0).length
    return { running, stopped, successLogs, failedLogs }
  }, [xxlJobs, xxlJobLogs])

  const jobColumns: ColumnsType<XXLJob> = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 60 },
    { title: '任务描述', dataIndex: 'jobDesc', key: 'jobDesc', width: 200, ellipsis: true },
    { title: '执行器', dataIndex: 'jobGroup', key: 'jobGroup', width: 120,
      render: (g: number) => {
        const group = xxlJobGroups.find((grp) => grp.id === g)
        return <Tag>{group?.title || g}</Tag>
      },
    },
    { title: '负责人', dataIndex: 'author', key: 'author', width: 100 },
    { title: '调度类型', dataIndex: 'scheduleType', key: 'scheduleType', width: 100, render: (t: string) => <Tag color="blue">{t}</Tag> },
    { title: '调度配置', dataIndex: 'scheduleConf', key: 'scheduleConf', width: 120, render: (v: string) => <Text code>{v}</Text> },
    { title: '路由策略', dataIndex: 'executorRouteStrategy', key: 'executorRouteStrategy', width: 120, render: (v: string) => executorRouteMap[v] || v },
    { title: 'Handler', dataIndex: 'executorHandler', key: 'executorHandler', width: 150, ellipsis: true },
    {
      title: '状态', dataIndex: 'triggerStatus', key: 'triggerStatus', width: 80,
      render: (v: number) => <Badge status={v === 1 ? 'success' : 'default'} text={v === 1 ? '运行' : '停止'} />,
    },
    { title: '超时(s)', dataIndex: 'timeout', key: 'timeout', width: 80 },
    { title: '重试', dataIndex: 'failRetryCount', key: 'failRetryCount', width: 60 },
    {
      title: '操作', key: 'actions', width: 220,
      render: (_: unknown, r: XXLJob) => (
        <Space size="small">
          {r.triggerStatus === 1 ? (
            <Tooltip title="停止"><Button size="small" icon={<PauseCircleOutlined />} onClick={() => handleStopJob(r.id)} /></Tooltip>
          ) : (
            <Tooltip title="执行一次"><Button size="small" icon={<PlayCircleOutlined />} onClick={() => handleRunJob(r.id)} /></Tooltip>
          )}
          <Button size="small" icon={<EditOutlined />} onClick={() => handleEditJob(r)} />
          <Popconfirm title="确定删除？" onConfirm={() => handleDeleteJob(r.id)}>
            <Button size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ]

  const logColumns: ColumnsType<XXLJobLog> = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 60 },
    { title: '任务ID', dataIndex: 'jobId', key: 'jobId', width: 70 },
    { title: '执行器', dataIndex: 'executorAddress', key: 'executorAddress', width: 150, render: (v: string) => <Text code>{v}</Text> },
    { title: '触发方式', dataIndex: 'triggerType', key: 'triggerType', width: 90, render: (v: string) => triggerTypeMap[v] || v },
    {
      title: '状态', dataIndex: 'handleCode', key: 'handleCode', width: 80,
      render: (code: number) => {
        if (code === 0) return <Tag>执行中</Tag>
        if (code === 200) return <Tag color="green">成功</Tag>
        return <Tag color="red">失败({code})</Tag>
      },
    },
    { title: '触发时间', dataIndex: 'triggerTime', key: 'triggerTime', width: 160 },
    { title: '调度结果', dataIndex: 'triggerMsg', key: 'triggerMsg', width: 120, ellipsis: true },
    { title: '执行时间', dataIndex: 'handleTime', key: 'handleTime', width: 160 },
    { title: '执行耗时', dataIndex: 'handleDuration', key: 'handleDuration', width: 90, render: (v: number) => `${v}ms` },
    {
      title: '操作', key: 'actions', width: 100,
      render: (_: unknown, r: XXLJobLog) => (
        <Button size="small" onClick={() => handleViewLog(r)}>详情</Button>
      ),
    },
  ]

  return (
    <div>
      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="任务总数" value={xxlJobsTotal} prefix={<ScheduleOutlined />} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="运行中" value={summary.running} valueStyle={{ color: '#3f8600' }} prefix={<PlayCircleOutlined />} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="执行成功" value={summary.successLogs} valueStyle={{ color: '#3f8600' }} prefix={<CheckCircleOutlined />} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small">
            <Tooltip title="打开 XXL-Job 控制台">
              <Statistic title="控制台" value="打开" prefix={<LinkOutlined />} valueStyle={{ fontSize: 16, cursor: 'pointer' }} onClick={handleOpenConsole} />
            </Tooltip>
          </Card>
        </Col>
      </Row>

      {error && <Alert message={error} type="error" showIcon closable style={{ marginBottom: 16 }} />}

      <Tabs activeKey={activeTab} onChange={setActiveTab} items={[
        {
          key: 'jobs',
          label: `任务管理 (${xxlJobsTotal})`,
          children: (
            <Card
              extra={
                <Space>
                  <Input placeholder="搜索任务" prefix={<SearchOutlined />} allowClear style={{ width: 200 }}
                    value={searchJob} onChange={(e) => setSearchJob(e.target.value)} onPressEnter={loadJobs} />
                  <Select placeholder="执行器" allowClear style={{ width: 150 }} value={groupFilter} onChange={setGroupFilter}
                    options={xxlJobGroups.map((g) => ({ label: g.title, value: g.id }))} />
                  <Button icon={<PlusOutlined />} type="primary" onClick={handleCreateJob}>创建任务</Button>
                  <Button icon={<ReloadOutlined />} onClick={loadJobs}>刷新</Button>
                </Space>
              }
            >
              <Spin spinning={loading}>
                {xxlJobs.length === 0 && !loading ? (
                  <Empty description="暂无任务" />
                ) : (
                  <Table dataSource={xxlJobs} columns={jobColumns} rowKey="id" pagination={{ pageSize: 20, showSizeChanger: true }} size="middle" scroll={{ x: 1400 }} />
                )}
              </Spin>
            </Card>
          ),
        },
        {
          key: 'logs',
          label: `调度日志 (${xxlJobLogsTotal})`,
          children: (
            <Card extra={<Button icon={<ReloadOutlined />} onClick={loadLogs}>刷新</Button>}>
              <Spin spinning={loading}>
                {xxlJobLogs.length === 0 && !loading ? (
                  <Empty description="暂无日志" />
                ) : (
                  <Table dataSource={xxlJobLogs} columns={logColumns} rowKey="id" pagination={{ pageSize: 20, showSizeChanger: true }} size="middle" scroll={{ x: 1200 }} />
                )}
              </Spin>
            </Card>
          ),
        },
      ]} />

      {/* 创建/编辑任务弹窗 */}
      <Modal title={editingJobId ? '编辑任务' : '创建任务'} open={jobModalVisible} onOk={handleSaveJob} onCancel={() => setJobModalVisible(false)} width={600} destroyOnClose>
        <Form form={jobForm} layout="vertical">
          <Form.Item name="jobGroup" label="执行器" rules={[{ required: true }]}>
            <Select placeholder="选择执行器" options={xxlJobGroups.map((g) => ({ label: g.title, value: g.id }))} />
          </Form.Item>
          <Form.Item name="jobDesc" label="任务描述" rules={[{ required: true }]}>
            <Input placeholder="任务描述" />
          </Form.Item>
          <Form.Item name="author" label="负责人" rules={[{ required: true }]}>
            <Input placeholder="负责人" />
          </Form.Item>
          <Form.Item name="scheduleType" label="调度类型" initialValue="CRON">
            <Select options={[
              { label: 'CRON', value: 'CRON' },
              { label: '固定速度', value: 'FIX_RATE' },
              { label: '固定延迟', value: 'FIX_DELAY' },
            ]} />
          </Form.Item>
          <Form.Item name="scheduleConf" label="调度配置" rules={[{ required: true }]}>
            <Input placeholder="0 0/1 * * * ?" />
          </Form.Item>
          <Form.Item name="executorHandler" label="Handler" rules={[{ required: true }]}>
            <Input placeholder="demoJobHandler" />
          </Form.Item>
          <Form.Item name="executorParam" label="任务参数">
            <Input placeholder="参数" />
          </Form.Item>
          <Form.Item name="executorRouteStrategy" label="路由策略" initialValue="FIRST">
            <Select options={Object.entries(executorRouteMap).map(([k, v]) => ({ label: v, value: k }))} />
          </Form.Item>
          <Form.Item name="executorBlockStrategy" label="阻塞策略" initialValue="SERIAL_EXECUTION">
            <Select options={[
              { label: '单机串行', value: 'SERIAL_EXECUTION' },
              { label: '丢弃后续调度', value: 'DISCARD_LATER' },
              { label: '覆盖之前调度', value: 'COVER_EARLY' },
            ]} />
          </Form.Item>
          <Form.Item name="timeout" label="超时时间(s)" initialValue={0}>
            <Input type="number" placeholder="0表示不限制" />
          </Form.Item>
          <Form.Item name="failRetryCount" label="失败重试次数" initialValue={0}>
            <Input type="number" placeholder="0" />
          </Form.Item>
          <Form.Item name="triggerStatus" label="启用" valuePropName="checked" initialValue={true}>
            <Switch />
          </Form.Item>
        </Form>
      </Modal>

      {/* 日志详情弹窗 */}
      <Modal title="日志详情" open={logDetailVisible} onCancel={() => setLogDetailVisible(false)} footer={null} width={700} destroyOnClose>
        {currentLog && (
          <Descriptions bordered size="small" column={1}>
            <Descriptions.Item label="日志ID">{currentLog.id}</Descriptions.Item>
            <Descriptions.Item label="任务ID">{currentLog.jobId}</Descriptions.Item>
            <Descriptions.Item label="执行器">{currentLog.executorAddress}</Descriptions.Item>
            <Descriptions.Item label="触发方式">{triggerTypeMap[currentLog.triggerType] || currentLog.triggerType}</Descriptions.Item>
            <Descriptions.Item label="触发时间">{currentLog.triggerTime}</Descriptions.Item>
            <Descriptions.Item label="触发结果">{currentLog.triggerMsg}</Descriptions.Item>
            <Descriptions.Item label="执行时间">{currentLog.handleTime}</Descriptions.Item>
            <Descriptions.Item label="执行耗时">{currentLog.handleDuration}ms</Descriptions.Item>
            <Descriptions.Item label="执行结果">
              <Tag color={currentLog.handleCode === 200 ? 'green' : 'red'}>{currentLog.handleMsg}</Tag>
            </Descriptions.Item>
          </Descriptions>
        )}
      </Modal>
    </div>
  )
}