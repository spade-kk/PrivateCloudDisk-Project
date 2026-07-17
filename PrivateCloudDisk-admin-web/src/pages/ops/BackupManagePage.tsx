// ============================================================
// Backup 管理页面
// 备份任务管理、备份记录、恢复操作
// ============================================================
import { useEffect, useState, useCallback, useMemo } from 'react'
import {
  Card, Table, Tag, Space, Button, Modal, Input, Select, Form, InputNumber,
  Progress, Spin, Alert, Empty, Tabs, Typography, Popconfirm, message, Statistic, Row, Col, Descriptions, Switch,
  DatePicker, Tooltip, Badge, Timeline,
} from 'antd'
import {
  ReloadOutlined, PlusOutlined, PlayCircleOutlined, DeleteOutlined,
  CloudUploadOutlined, ClockCircleOutlined, CheckCircleOutlined,
  WarningOutlined, CloseCircleOutlined, SyncOutlined, HistoryOutlined,
  RollbackOutlined, PauseCircleOutlined, FileProtectOutlined,
} from '@ant-design/icons'
import { useOpsMonitorStore } from '@/stores/opsMonitorStore'
import type { BackupJob, BackupRecord, BackupSchedule } from '@/api/opsMonitor'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'

const { Text } = Typography

function formatBytes(bytes: number): string {
  if (!bytes || bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return `${(bytes / Math.pow(k, i)).toFixed(1)} ${sizes[i]}`
}

const jobStatusMap: Record<string, { color: string; icon: React.ReactNode }> = {
  RUNNING: { color: 'blue', icon: <SyncOutlined spin /> },
  SUCCESS: { color: 'green', icon: <CheckCircleOutlined /> },
  FAILED: { color: 'red', icon: <CloseCircleOutlined /> },
  PENDING: { color: 'default', icon: <ClockCircleOutlined /> },
  CANCELLED: { color: 'orange', icon: <WarningOutlined /> },
}

const scheduleTypeMap: Record<string, string> = {
  DAILY: '每日',
  WEEKLY: '每周',
  MONTHLY: '每月',
  HOURLY: '每小时',
  MANUAL: '手动',
  CRON: 'Cron',
}

export default function BackupManagePage() {
  const {
    backupJobs, backupJobsTotal, backupRecords, backupRecordsTotal,
    backupSchedules, loading, error,
    fetchBackupJobs, fetchBackupRecords, fetchBackupSchedules,
    doRunBackupJob, doRestoreBackup, doDeleteBackupRecord,
    doCreateBackupJob, doUpdateBackupSchedule,
  } = useOpsMonitorStore()

  const [activeTab, setActiveTab] = useState('jobs')
  const [createModalVisible, setCreateModalVisible] = useState(false)
  const [scheduleModalVisible, setScheduleModalVisible] = useState(false)
  const [restoreModalVisible, setRestoreModalVisible] = useState(false)
  const [selectedRecord, setSelectedRecord] = useState<BackupRecord | null>(null)
  const [jobForm] = Form.useForm()
  const [scheduleForm] = Form.useForm()

  const loadJobs = useCallback(() => { fetchBackupJobs({ page: 1, pageSize: 50 }) }, [fetchBackupJobs])
  const loadRecords = useCallback(() => { fetchBackupRecords({ page: 1, pageSize: 50 }) }, [fetchBackupRecords])
  const loadSchedules = useCallback(() => { fetchBackupSchedules() }, [fetchBackupSchedules])

  useEffect(() => { loadJobs() }, [loadJobs])
  useEffect(() => {
    if (activeTab === 'records') loadRecords()
    else if (activeTab === 'schedules') loadSchedules()
  }, [activeTab, loadRecords, loadSchedules])

  const handleCreateJob = async () => {
    try {
      const values = await jobForm.validateFields()
      const success = await doCreateBackupJob(values)
      if (success) { message.success('备份任务创建成功'); setCreateModalVisible(false); jobForm.resetFields(); loadJobs() }
      else message.error('创建失败')
    } catch {}
  }

  const handleRunJob = async (jobId: string) => {
    const success = await doRunBackupJob(jobId)
    if (success) { message.success('备份任务已触发'); loadJobs() }
    else message.error('触发失败')
  }

  const handleRestore = async (recordId: string) => {
    const success = await doRestoreBackup(recordId)
    if (success) { message.success('恢复任务已启动'); setRestoreModalVisible(false) }
    else message.error('恢复失败')
  }

  const handleUpdateSchedule = async () => {
    try {
      const values = await scheduleForm.validateFields()
      const success = await doUpdateBackupSchedule(values)
      if (success) { message.success('调度策略已更新'); setScheduleModalVisible(false); loadSchedules() }
      else message.error('更新失败')
    } catch {}
  }

  const summary = useMemo(() => {
    const success = backupRecords.filter((r) => r.status === 'SUCCESS').length
    const failed = backupRecords.filter((r) => r.status === 'FAILED').length
    const totalSize = backupRecords.reduce((s, r) => s + r.size, 0)
    return { success, failed, totalSize }
  }, [backupRecords])

  const jobColumns: ColumnsType<BackupJob> = [
    { title: '名称', dataIndex: 'name', key: 'name', width: 180 },
    { title: '类型', dataIndex: 'type', key: 'type', width: 100, render: (t: string) => <Tag>{t}</Tag> },
    { title: '目标', dataIndex: 'target', key: 'target', width: 150, ellipsis: true },
    { title: '调度', dataIndex: 'schedule', key: 'schedule', width: 100, render: (s: string) => scheduleTypeMap[s] || s },
    {
      title: '启用', dataIndex: 'enabled', key: 'enabled', width: 80,
      render: (v: boolean) => <Badge status={v ? 'success' : 'default'} text={v ? '是' : '否'} />,
    },
    {
      title: '最近状态', dataIndex: 'lastStatus', key: 'lastStatus', width: 110,
      render: (s: string) => s ? <Tag color={jobStatusMap[s]?.color} icon={jobStatusMap[s]?.icon}>{s}</Tag> : '-',
    },
    { title: '最近运行', dataIndex: 'lastRunTime', key: 'lastRunTime', width: 160, render: (v: string) => v || '-' },
    { title: '下次运行', dataIndex: 'nextRunTime', key: 'nextRunTime', width: 160, render: (v: string) => v || '-' },
    { title: '保留天数', dataIndex: 'retentionDays', key: 'retentionDays', width: 90 },
    {
      title: '操作', key: 'actions', width: 120,
      render: (_: unknown, r: BackupJob) => (
        <Space size="small">
          <Tooltip title="立即运行"><Button size="small" icon={<PlayCircleOutlined />} onClick={() => handleRunJob(r.jobId)} /></Tooltip>
        </Space>
      ),
    },
  ]

  const recordColumns: ColumnsType<BackupRecord> = [
    { title: '备份ID', dataIndex: 'backupId', key: 'backupId', width: 120, ellipsis: true },
    { title: '任务名称', dataIndex: 'jobName', key: 'jobName', width: 150 },
    { title: '类型', dataIndex: 'type', key: 'type', width: 100, render: (t: string) => <Tag>{t}</Tag> },
    {
      title: '状态', dataIndex: 'status', key: 'status', width: 100,
      render: (s: string) => <Tag color={jobStatusMap[s]?.color} icon={jobStatusMap[s]?.icon}>{s}</Tag>,
    },
    { title: '大小', dataIndex: 'size', key: 'size', width: 100, render: (v: number) => formatBytes(v) },
    { title: '开始时间', dataIndex: 'startTime', key: 'startTime', width: 160 },
    { title: '耗时', dataIndex: 'duration', key: 'duration', width: 100, render: (v: number) => `${Math.floor(v / 60)}分${v % 60}秒` },
    {
      title: '进度', dataIndex: 'progress', key: 'progress', width: 130,
      render: (v: number) => <Progress percent={v} size="small" />,
    },
    { title: '存储位置', dataIndex: 'location', key: 'location', width: 120, ellipsis: true },
    {
      title: '操作', key: 'actions', width: 140,
      render: (_: unknown, r: BackupRecord) => (
        <Space size="small">
          {r.status === 'SUCCESS' && (
            <Tooltip title="恢复"><Button size="small" icon={<RollbackOutlined />} onClick={() => { setSelectedRecord(r); setRestoreModalVisible(true) }} /></Tooltip>
          )}
          <Tooltip title="删除"><Popconfirm title="确定删除此备份记录？" onConfirm={() => doDeleteBackupRecord(r.backupId)}><Button size="small" danger icon={<DeleteOutlined />} /></Popconfirm></Tooltip>
        </Space>
      ),
    },
  ]

  return (
    <div>
      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="备份任务" value={backupJobsTotal} prefix={<FileProtectOutlined />} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="备份记录" value={backupRecordsTotal} prefix={<HistoryOutlined />} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="成功备份" value={summary.success} valueStyle={{ color: '#3f8600' }} prefix={<CheckCircleOutlined />} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="总备份大小" value={formatBytes(summary.totalSize)} prefix={<CloudUploadOutlined />} /></Card>
        </Col>
      </Row>

      {error && <Alert message={error} type="error" showIcon closable style={{ marginBottom: 16 }} />}

      <Tabs activeKey={activeTab} onChange={setActiveTab} items={[
        {
          key: 'jobs',
          label: `备份任务 (${backupJobsTotal})`,
          children: (
            <Card
              extra={
                <Space>
                  <Button icon={<PlusOutlined />} type="primary" onClick={() => setCreateModalVisible(true)}>创建任务</Button>
                  <Button icon={<ReloadOutlined />} onClick={loadJobs}>刷新</Button>
                </Space>
              }
            >
              <Spin spinning={loading}>
                {backupJobs.length === 0 && !loading ? (
                  <Empty description="暂无备份任务" />
                ) : (
                  <Table dataSource={backupJobs} columns={jobColumns} rowKey="jobId" pagination={{ pageSize: 20, showSizeChanger: true }} size="middle" />
                )}
              </Spin>
            </Card>
          ),
        },
        {
          key: 'records',
          label: `备份记录 (${backupRecordsTotal})`,
          children: (
            <Card extra={<Button icon={<ReloadOutlined />} onClick={loadRecords}>刷新</Button>}>
              <Spin spinning={loading}>
                {backupRecords.length === 0 && !loading ? (
                  <Empty description="暂无备份记录" />
                ) : (
                  <Table dataSource={backupRecords} columns={recordColumns} rowKey="backupId" pagination={{ pageSize: 20, showSizeChanger: true }} size="middle" scroll={{ x: 1200 }} />
                )}
              </Spin>
            </Card>
          ),
        },
        {
          key: 'schedules',
          label: '调度策略',
          children: (
            <Card
              extra={
                <Space>
                  <Button icon={<PlusOutlined />} onClick={() => setScheduleModalVisible(true)}>配置调度</Button>
                  <Button icon={<ReloadOutlined />} onClick={loadSchedules}>刷新</Button>
                </Space>
              }
            >
              <Spin spinning={loading}>
                {backupSchedules.length === 0 && !loading ? (
                  <Empty description="暂无调度策略" />
                ) : (
                  <Table
                    dataSource={backupSchedules}
                    rowKey="scheduleId"
                    pagination={false}
                    size="middle"
                    columns={[
                      { title: '名称', dataIndex: 'name', key: 'name', width: 180 },
                      { title: '类型', dataIndex: 'type', key: 'type', width: 100, render: (t: string) => scheduleTypeMap[t] || t },
                      { title: 'Cron 表达式', dataIndex: 'cron', key: 'cron', width: 140, render: (v: string) => <Text code>{v}</Text> },
                      { title: '下次执行', dataIndex: 'nextRun', key: 'nextRun', width: 160 },
                      { title: '上次执行', dataIndex: 'lastRun', key: 'lastRun', width: 160 },
                      {
                        title: '启用', dataIndex: 'enabled', key: 'enabled', width: 80,
                        render: (v: boolean) => <Badge status={v ? 'success' : 'default'} text={v ? '是' : '否'} />,
                      },
                    ]}
                  />
                )}
              </Spin>
            </Card>
          ),
        },
      ]} />

      {/* 创建备份任务弹窗 */}
      <Modal title="创建备份任务" open={createModalVisible} onOk={handleCreateJob} onCancel={() => { setCreateModalVisible(false); jobForm.resetFields() }} destroyOnClose>
        <Form form={jobForm} layout="vertical">
          <Form.Item name="name" label="任务名称" rules={[{ required: true }]}>
            <Input placeholder="backup-task-name" />
          </Form.Item>
          <Form.Item name="type" label="备份类型" rules={[{ required: true }]}>
            <Select options={[
              { label: '全量备份', value: 'FULL' },
              { label: '增量备份', value: 'INCREMENTAL' },
              { label: '差异备份', value: 'DIFFERENTIAL' },
            ]} />
          </Form.Item>
          <Form.Item name="target" label="备份目标" rules={[{ required: true }]}>
            <Input placeholder="数据库名称 / 文件路径" />
          </Form.Item>
          <Form.Item name="schedule" label="调度方式" initialValue="MANUAL">
            <Select options={[
              { label: '手动', value: 'MANUAL' },
              { label: '每小时', value: 'HOURLY' },
              { label: '每日', value: 'DAILY' },
              { label: '每周', value: 'WEEKLY' },
              { label: '每月', value: 'MONTHLY' },
              { label: 'Cron', value: 'CRON' },
            ]} />
          </Form.Item>
          <Form.Item name="retentionDays" label="保留天数" initialValue={30}>
            <InputNumber min={1} max={365} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="enabled" label="启用" valuePropName="checked" initialValue={true}>
            <Switch />
          </Form.Item>
        </Form>
      </Modal>

      {/* 恢复弹窗 */}
      <Modal title="恢复备份" open={restoreModalVisible} onOk={() => selectedRecord && handleRestore(selectedRecord.backupId)} onCancel={() => setRestoreModalVisible(false)}>
        {selectedRecord && (
          <Descriptions bordered size="small" column={1}>
            <Descriptions.Item label="备份ID">{selectedRecord.backupId}</Descriptions.Item>
            <Descriptions.Item label="任务名称">{selectedRecord.jobName}</Descriptions.Item>
            <Descriptions.Item label="备份时间">{selectedRecord.startTime}</Descriptions.Item>
            <Descriptions.Item label="大小">{formatBytes(selectedRecord.size)}</Descriptions.Item>
          </Descriptions>
        )}
        <Text type="warning">注意：恢复操作将覆盖当前数据，请谨慎操作。</Text>
      </Modal>

      {/* 调度策略弹窗 */}
      <Modal title="配置调度策略" open={scheduleModalVisible} onOk={handleUpdateSchedule} onCancel={() => { setScheduleModalVisible(false); scheduleForm.resetFields() }} destroyOnClose>
        <Form form={scheduleForm} layout="vertical">
          <Form.Item name="name" label="策略名称" rules={[{ required: true }]}>
            <Input placeholder="schedule-name" />
          </Form.Item>
          <Form.Item name="type" label="调度类型" rules={[{ required: true }]}>
            <Select options={[
              { label: '每日', value: 'DAILY' },
              { label: '每周', value: 'WEEKLY' },
              { label: '每月', value: 'MONTHLY' },
              { label: '每小时', value: 'HOURLY' },
            ]} />
          </Form.Item>
          <Form.Item name="cron" label="Cron 表达式">
            <Input placeholder="0 2 * * *" />
          </Form.Item>
          <Form.Item name="enabled" label="启用" valuePropName="checked" initialValue={true}>
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}