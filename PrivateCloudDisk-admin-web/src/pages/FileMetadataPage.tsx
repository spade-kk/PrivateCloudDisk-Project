// ============================================================
// 文件元数据统计页面 - 企业级
// 展示：文件总数、类型分布、加密统计、用户分布、MIME 分析
// ============================================================
import { useEffect, useState, useMemo } from 'react'
import {
  Row, Col, Card, Typography, Table, Tag, Spin, Button, Space,
  Statistic, Progress, Tabs, Tooltip, Empty,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  FileOutlined, FolderOutlined, HddOutlined, ReloadOutlined,
  PieChartOutlined, BarChartOutlined, LockOutlined, UnlockOutlined,
  GlobalOutlined, TeamOutlined, CloudOutlined, DatabaseOutlined,
  SafetyCertificateOutlined, FilePdfOutlined, FileImageOutlined,
  FileTextOutlined, FileExcelOutlined, FilePptOutlined,
  VideoCameraOutlined, AudioOutlined, CodeOutlined,
  FileZipOutlined, FileUnknownOutlined, ArrowUpOutlined,
  ArrowDownOutlined, FilterOutlined, CheckCircleOutlined,
  StopOutlined, EyeOutlined, CloudUploadOutlined,
} from '@ant-design/icons'
import { mockUsers } from '@/mock/data'
import { formatBytes, formatNumber } from '@/utils/format'
import PageHeader from '@/components/PageHeader'

const { Text, Title } = Typography

// 颜色
const COLORS = ['#1677ff', '#52c41a', '#faad14', '#ff4d4f', '#722ed1', '#13c2c2', '#eb2f96', '#fa8c16', '#2f54eb', '#a0d911']

// 文件扩展名 -> 图标和颜色
const extMeta: Record<string, { icon: React.ReactNode; color: string; category: string }> = {
  '.pdf': { icon: <FilePdfOutlined />, color: '#ff4d4f', category: '文档' },
  '.doc': { icon: <FileTextOutlined />, color: '#1677ff', category: '文档' },
  '.docx': { icon: <FileTextOutlined />, color: '#1677ff', category: '文档' },
  '.xls': { icon: <FileExcelOutlined />, color: '#52c41a', category: '文档' },
  '.xlsx': { icon: <FileExcelOutlined />, color: '#52c41a', category: '文档' },
  '.ppt': { icon: <FilePptOutlined />, color: '#fa8c16', category: '文档' },
  '.pptx': { icon: <FilePptOutlined />, color: '#fa8c16', category: '文档' },
  '.txt': { icon: <FileTextOutlined />, color: '#8c8c8c', category: '文档' },
  '.md': { icon: <FileTextOutlined />, color: '#8c8c8c', category: '文档' },
  '.jpg': { icon: <FileImageOutlined />, color: '#eb2f96', category: '图片' },
  '.jpeg': { icon: <FileImageOutlined />, color: '#eb2f96', category: '图片' },
  '.png': { icon: <FileImageOutlined />, color: '#eb2f96', category: '图片' },
  '.gif': { icon: <FileImageOutlined />, color: '#eb2f96', category: '图片' },
  '.webp': { icon: <FileImageOutlined />, color: '#eb2f96', category: '图片' },
  '.svg': { icon: <FileImageOutlined />, color: '#eb2f96', category: '图片' },
  '.mp4': { icon: <VideoCameraOutlined />, color: '#722ed1', category: '视频' },
  '.avi': { icon: <VideoCameraOutlined />, color: '#722ed1', category: '视频' },
  '.mov': { icon: <VideoCameraOutlined />, color: '#722ed1', category: '视频' },
  '.mkv': { icon: <VideoCameraOutlined />, color: '#722ed1', category: '视频' },
  '.mp3': { icon: <AudioOutlined />, color: '#13c2c2', category: '音频' },
  '.wav': { icon: <AudioOutlined />, color: '#13c2c2', category: '音频' },
  '.flac': { icon: <AudioOutlined />, color: '#13c2c2', category: '音频' },
  '.zip': { icon: <FileZipOutlined />, color: '#faad14', category: '压缩包' },
  '.rar': { icon: <FileZipOutlined />, color: '#faad14', category: '压缩包' },
  '.7z': { icon: <FileZipOutlined />, color: '#faad14', category: '压缩包' },
  '.tar': { icon: <FileZipOutlined />, color: '#faad14', category: '压缩包' },
  '.gz': { icon: <FileZipOutlined />, color: '#faad14', category: '压缩包' },
  '.js': { icon: <CodeOutlined />, color: '#f0db4f', category: '代码' },
  '.ts': { icon: <CodeOutlined />, color: '#3178c6', category: '代码' },
  '.py': { icon: <CodeOutlined />, color: '#3572A5', category: '代码' },
  '.java': { icon: <CodeOutlined />, color: '#b07219', category: '代码' },
  '.go': { icon: <CodeOutlined />, color: '#00ADD8', category: '代码' },
  '.rs': { icon: <CodeOutlined />, color: '#dea584', category: '代码' },
  '.json': { icon: <CodeOutlined />, color: '#5B5B5B', category: '代码' },
  '.xml': { icon: <CodeOutlined />, color: '#5B5B5B', category: '代码' },
  '.yaml': { icon: <CodeOutlined />, color: '#5B5B5B', category: '代码' },
  '.yml': { icon: <CodeOutlined />, color: '#5B5B5B', category: '代码' },
  '.html': { icon: <CodeOutlined />, color: '#e34c26', category: '代码' },
  '.css': { icon: <CodeOutlined />, color: '#563d7c', category: '代码' },
  '.sql': { icon: <CodeOutlined />, color: '#5B5B5B', category: '代码' },
  '.sh': { icon: <CodeOutlined />, color: '#5B5B5B', category: '代码' },
  '.dockerfile': { icon: <CodeOutlined />, color: '#384d54', category: '代码' },
}

// 模拟文件扩展名统计数据
const mockExtStats = [
  { ext: '.jpg', count: 12450, size: 125 * 1024 * 1024 * 1024 },
  { ext: '.png', count: 8930, size: 68 * 1024 * 1024 * 1024 },
  { ext: '.pdf', count: 5670, size: 234 * 1024 * 1024 * 1024 },
  { ext: '.mp4', count: 3420, size: 892 * 1024 * 1024 * 1024 },
  { ext: '.docx', count: 2890, size: 45 * 1024 * 1024 * 1024 },
  { ext: '.xlsx', count: 2150, size: 28 * 1024 * 1024 * 1024 },
  { ext: '.zip', count: 1890, size: 156 * 1024 * 1024 * 1024 },
  { ext: '.js', count: 1560, size: 3.2 * 1024 * 1024 * 1024 },
  { ext: '.ts', count: 1340, size: 2.8 * 1024 * 1024 * 1024 },
  { ext: '.py', count: 1120, size: 1.5 * 1024 * 1024 * 1024 },
  { ext: '.mp3', count: 980, size: 12.5 * 1024 * 1024 * 1024 },
  { ext: '.pptx', count: 870, size: 18.2 * 1024 * 1024 * 1024 },
  { ext: '.gif', count: 760, size: 4.5 * 1024 * 1024 * 1024 },
  { ext: '.txt', count: 650, size: 0.5 * 1024 * 1024 * 1024 },
  { ext: '.json', count: 540, size: 0.8 * 1024 * 1024 * 1024 },
  { ext: '.svg', count: 430, size: 0.3 * 1024 * 1024 * 1024 },
  { ext: '.sql', count: 380, size: 1.2 * 1024 * 1024 * 1024 },
  { ext: '.md', count: 350, size: 0.2 * 1024 * 1024 * 1024 },
  { ext: '.mov', count: 280, size: 120 * 1024 * 1024 * 1024 },
  { ext: '.webp', count: 250, size: 1.8 * 1024 * 1024 * 1024 },
]

// 模拟 MIME 类型统计
const mockMimeStats = [
  { mimeType: 'image/jpeg', count: 12450, size: 125 * 1024 * 1024 * 1024 },
  { mimeType: 'image/png', count: 8930, size: 68 * 1024 * 1024 * 1024 },
  { mimeType: 'application/pdf', count: 5670, size: 234 * 1024 * 1024 * 1024 },
  { mimeType: 'video/mp4', count: 3420, size: 892 * 1024 * 1024 * 1024 },
  { mimeType: 'application/zip', count: 1890, size: 156 * 1024 * 1024 * 1024 },
  { mimeType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', count: 2890, size: 45 * 1024 * 1024 * 1024 },
  { mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', count: 2150, size: 28 * 1024 * 1024 * 1024 },
  { mimeType: 'text/javascript', count: 1560, size: 3.2 * 1024 * 1024 * 1024 },
  { mimeType: 'text/typescript', count: 1340, size: 2.8 * 1024 * 1024 * 1024 },
  { mimeType: 'audio/mpeg', count: 980, size: 12.5 * 1024 * 1024 * 1024 },
]

// 简易柱状图
function SimpleHBar({ data, valueFormatter, color }: {
  data: { label: string; value: number; meta?: { icon?: React.ReactNode; color?: string } }[]
  valueFormatter: (v: number) => string
  color: string
}) {
  const max = Math.max(...data.map((d) => d.value), 1)
  return (
    <div style={{ maxHeight: 400, overflow: 'auto' }}>
      {data.map((item, i) => (
        <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6 }}>
          {item.meta?.icon && <span style={{ width: 20, textAlign: 'center' }}>{item.meta.icon}</span>}
          <Text style={{ width: 50, fontSize: 12, flexShrink: 0 }}>{item.label}</Text>
          <div style={{ flex: 1, height: 20, background: '#f5f5f5', borderRadius: 4, position: 'relative', overflow: 'hidden' }}>
            <div style={{
              width: `${(item.value / max) * 100}%`, height: '100%',
              background: item.meta?.color || color, borderRadius: 4, transition: 'width 0.3s',
              minWidth: item.value > 0 ? 4 : 0,
            }} />
          </div>
          <Text style={{ width: 80, fontSize: 12, textAlign: 'right', flexShrink: 0 }}>{valueFormatter(item.value)}</Text>
        </div>
      ))}
    </div>
  )
}

export default function FileMetadataPage() {
  const [loading, setLoading] = useState(true)
  const [activeTab, setActiveTab] = useState('overview')

  // 总统计
  const totalStats = useMemo(() => {
    const totalFiles = mockExtStats.reduce((s, e) => s + e.count, 0)
    const totalSize = mockExtStats.reduce((s, e) => s + e.size, 0)
    const totalFolders = Math.floor(totalFiles * 0.12)
    return {
      totalFiles,
      totalFolders,
      totalSize,
      averageSize: totalFiles > 0 ? totalSize / totalFiles : 0,
      largestFile: 4.5 * 1024 * 1024 * 1024, // 4.5GB
      encrypted: Math.floor(totalFiles * 0.35),
      unencrypted: Math.floor(totalFiles * 0.65),
      publicFiles: Math.floor(totalFiles * 0.18),
      privateFiles: Math.floor(totalFiles * 0.82),
    }
  }, [])

  // 按分类汇总
  const categoryStats = useMemo(() => {
    const map: Record<string, { count: number; size: number }> = {}
    for (const ext of mockExtStats) {
      const meta = extMeta[ext.ext]
      const cat = meta?.category || '其他'
      if (!map[cat]) map[cat] = { count: 0, size: 0 }
      map[cat].count += ext.count
      map[cat].size += ext.size
    }
    return Object.entries(map).map(([label, v]) => ({
      label,
      count: v.count,
      size: v.size,
      color: COLORS[Object.keys(map).indexOf(label) % COLORS.length],
    }))
  }, [])

  // 用户文件分布
  const userFileStats = useMemo(() => {
    return mockUsers.slice(0, 10).map((u) => ({
      userId: u.userId,
      userName: u.name,
      fileCount: u.fileCount || Math.floor(Math.random() * 5000) + 100,
      totalSize: u.storageUsed || Math.floor(Math.random() * 500 * 1024 * 1024 * 1024),
    })).sort((a, b) => b.totalSize - a.totalSize)
  }, [])

  useEffect(() => {
    const timer = setTimeout(() => setLoading(false), 400)
    return () => clearTimeout(timer)
  }, [])

  const extColumns: ColumnsType<(typeof mockExtStats)[0]> = [
    {
      title: '扩展名', dataIndex: 'ext', key: 'ext', width: 100,
      render: (v: string) => {
        const meta = extMeta[v]
        return <Space>{meta?.icon || <FileUnknownOutlined />}<Text code>{v}</Text></Space>
      },
    },
    { title: '分类', key: 'cat', width: 80, render: (_: unknown, r: (typeof mockExtStats)[0]) => (
      <Tag>{extMeta[r.ext]?.category || '其他'}</Tag>
    )},
    { title: '文件数', dataIndex: 'count', key: 'count', width: 100, render: (v: number) => formatNumber(v) },
    {
      title: '占比', key: 'pct', width: 100,
      render: (_: unknown, r: (typeof mockExtStats)[0]) => (
        <Progress percent={Math.round((r.count / totalStats.totalFiles) * 10000) / 100} size="small" />
      ),
    },
    { title: '总大小', dataIndex: 'size', key: 'size', width: 100, render: (v: number) => formatBytes(v) },
    {
      title: '大小占比', key: 'sizePct', width: 100,
      render: (_: unknown, r: (typeof mockExtStats)[0]) => (
        <Progress percent={Math.round((r.size / totalStats.totalSize) * 10000) / 100} size="small" strokeColor="#52c41a" />
      ),
    },
  ]

  const mimeColumns: ColumnsType<(typeof mockMimeStats)[0]> = [
    { title: 'MIME 类型', dataIndex: 'mimeType', key: 'mimeType', ellipsis: true, render: (v: string) => <Text code style={{ fontSize: 11 }}>{v}</Text> },
    { title: '文件数', dataIndex: 'count', key: 'count', width: 90, render: (v: number) => formatNumber(v) },
    { title: '总大小', dataIndex: 'size', key: 'size', width: 100, render: (v: number) => formatBytes(v) },
  ]

  const userColumns: ColumnsType<(typeof userFileStats)[0]> = [
    { title: '排名', key: 'rank', width: 50, render: (_: unknown, __: unknown, i: number) => (
      <Text strong style={{ color: i < 3 ? COLORS[i] : undefined }}>#{i + 1}</Text>
    )},
    { title: '用户', dataIndex: 'userName', key: 'userName' },
    { title: '文件数', dataIndex: 'fileCount', key: 'fileCount', width: 100, render: (v: number) => formatNumber(v) },
    {
      title: '存储用量', dataIndex: 'totalSize', key: 'totalSize', width: 120,
      render: (v: number) => formatBytes(v),
    },
    {
      title: '占比', key: 'pct', width: 100,
      render: (_: unknown, r: (typeof userFileStats)[0]) => (
        <Progress percent={Math.round((r.totalSize / totalStats.totalSize) * 10000) / 100} size="small" />
      ),
    },
  ]

  return (
    <div>
      <PageHeader
        title="文件元数据"
        subtitle="系统全量文件统计与元数据分析"
        icon={<DatabaseOutlined style={{ color: '#1677ff' }} />}
        actions={
          <Button icon={<ReloadOutlined />} onClick={() => { setLoading(true); setTimeout(() => setLoading(false), 300) }}>刷新</Button>
        }
      />

      <Spin spinning={loading}>
        {/* ── 概览统计 ── */}
        <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
          <Col xs={24} sm={12} lg={6}>
            <Card size="small" style={{ borderRadius: 8 }}>
              <Statistic title="文件总数" value={totalStats.totalFiles} prefix={<FileOutlined />}
                formatter={(v) => formatNumber(Number(v))} />
            </Card>
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <Card size="small" style={{ borderRadius: 8 }}>
              <Statistic title="文件夹数" value={totalStats.totalFolders} prefix={<FolderOutlined />}
                formatter={(v) => formatNumber(Number(v))} />
            </Card>
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <Card size="small" style={{ borderRadius: 8 }}>
              <Statistic title="总存储量" value={totalStats.totalSize} prefix={<HddOutlined />}
                formatter={(v) => formatBytes(Number(v))} />
            </Card>
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <Card size="small" style={{ borderRadius: 8 }}>
              <Statistic title="平均文件大小" value={totalStats.averageSize} prefix={<CloudOutlined />}
                formatter={(v) => formatBytes(Number(v))} />
            </Card>
          </Col>
        </Row>

        {/* ── 加密 + 公开统计 ── */}
        <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
          <Col xs={24} sm={12} lg={6}>
            <Card size="small" title="加密状态" style={{ borderRadius: 8 }}>
              <div style={{ textAlign: 'center' }}>
                <Progress type="circle" percent={Math.round((totalStats.encrypted / totalStats.totalFiles) * 100)}
                  size={100} strokeColor="#722ed1" />
                <div style={{ marginTop: 8 }}>
                  <Space>
                    <Tag icon={<LockOutlined />} color="purple">加密 {formatNumber(totalStats.encrypted)}</Tag>
                    <Tag icon={<UnlockOutlined />}>未加密 {formatNumber(totalStats.unencrypted)}</Tag>
                  </Space>
                </div>
              </div>
            </Card>
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <Card size="small" title="公开状态" style={{ borderRadius: 8 }}>
              <div style={{ textAlign: 'center' }}>
                <Progress type="circle" percent={Math.round((totalStats.publicFiles / totalStats.totalFiles) * 100)}
                  size={100} strokeColor="#13c2c2" />
                <div style={{ marginTop: 8 }}>
                  <Space>
                    <Tag icon={<GlobalOutlined />} color="cyan">公开 {formatNumber(totalStats.publicFiles)}</Tag>
                    <Tag icon={<StopOutlined />}>私有 {formatNumber(totalStats.privateFiles)}</Tag>
                  </Space>
                </div>
              </div>
            </Card>
          </Col>
          <Col xs={24} lg={12}>
            <Card size="small" title="文件分类分布" style={{ borderRadius: 8 }}>
              <SimpleHBar
                data={categoryStats.map((c) => ({
                  label: c.label,
                  value: c.count,
                  meta: { color: c.color },
                }))}
                valueFormatter={formatNumber}
                color="#1677ff"
              />
            </Card>
          </Col>
        </Row>

        {/* ── 详细数据 Tabs ── */}
        <Card size="small" style={{ borderRadius: 8 }}>
          <Tabs activeKey={activeTab} onChange={setActiveTab}
            items={[
              {
                key: 'overview',
                label: (<span><PieChartOutlined />扩展名分布</span>),
                children: (
                  <Table
                    dataSource={mockExtStats}
                    columns={extColumns}
                    rowKey="ext"
                    size="small"
                    pagination={{ pageSize: 15, showSizeChanger: true, showTotal: (t) => `共 ${t} 种扩展名` }}
                    scroll={{ x: 600 }}
                  />
                ),
              },
              {
                key: 'mime',
                label: (<span><DatabaseOutlined />MIME 类型</span>),
                children: (
                  <Table
                    dataSource={mockMimeStats}
                    columns={mimeColumns}
                    rowKey="mimeType"
                    size="small"
                    pagination={false}
                    scroll={{ x: 600 }}
                  />
                ),
              },
              {
                key: 'users',
                label: (<span><TeamOutlined />用户分布</span>),
                children: (
                  <Table
                    dataSource={userFileStats}
                    columns={userColumns}
                    rowKey="userId"
                    size="small"
                    pagination={false}
                    scroll={{ x: 500 }}
                  />
                ),
              },
            ]}
          />
        </Card>
      </Spin>
    </div>
  )
}