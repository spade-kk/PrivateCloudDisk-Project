/**
 * pages/Search/Search.jsx - 全局搜索页面
 *
 * 后端: Spring Boot FileController GET /business/files/advanced-search
 * 参数: keyword, page, size, sortField, asc, filters, highlightFields, searchAfter
 * 返回: FileSearchVo { total, hits, aggregations, searchAfter }
 */
import React, { useState, useEffect, useCallback } from 'react'
import { useSearchParams, useNavigate } from 'react-router-dom'
import { Input, Tabs, Table, message, Empty, Tag, Select, Space } from 'antd'
import { SearchOutlined, FileOutlined } from '@ant-design/icons'
import { advancedSearch } from '@/api/file'
import { formatFileSize, formatTime } from '@/utils/helper'
import './Search.css'

const FILE_TYPE_TABS = [
  { key: '', label: '全部' },
  { key: 'IMAGE', label: '图片' },
  { key: 'VIDEO', label: '视频' },
  { key: 'DOCUMENT', label: '文档' },
  { key: 'ARCHIVE', label: '压缩包' }
]

export default function SearchPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [keyword, setKeyword] = useState(searchParams.get('keyword') || '')
  const [currentTab, setCurrentTab] = useState('')
  const [sortField, setSortField] = useState('_score')
  const [sortAsc, setSortAsc] = useState(false)
  const [results, setResults] = useState([])
  const [totalHits, setTotalHits] = useState(0)
  const [loading, setLoading] = useState(false)
  const [searched, setSearched] = useState(false)
  const [page, setPage] = useState(1)

  const doSearch = useCallback(async (p = 1) => {
    if (!keyword.trim()) return
    setLoading(true)
    setSearched(true)
    try {
      const params = {
        keyword: keyword.trim(),
        page: p,
        size: 20,
        sortField,
        asc: sortAsc,
        highlightFields: ['name', 'content']
      }
      if (currentTab) {
        params.filters = { fileCategory: currentTab }
      }

      const res = await advancedSearch(params)
      // 后端返回: { code: 200, data: FileSearchVo { total, hits: [...], aggregations, searchAfter } }
      const searchData = res.data
      const hits = searchData?.hits || []
      const items = hits.map(item => ({
        key: item._source?.file_id || item._source?.id || item._id || item.id,
        id: item._source?.file_id || item._source?.id || item._id || item.id,
        name: item._source?.file_name || item._source?.name || item.name,
        sizeFormatted: formatFileSize(item._source?.file_size || item._source?.size || item.size),
        timeFormatted: formatTime(item._source?.uploaded_time || item._source?.time || item.uploaded_time),
        highlight: item.highlight
      }))
      setResults(items)
      setTotalHits(searchData?.total || 0)
      setPage(p)
    } catch (e) {
      message.error('搜索失败')
    } finally {
      setLoading(false)
    }
  }, [keyword, currentTab, sortField, sortAsc])

  useEffect(() => {
    if (keyword.trim()) doSearch(1)
  }, [])

  const columns = [
    {
      title: '名称',
      dataIndex: 'name',
      key: 'name',
      ellipsis: true,
      render: (text, record) => (
        <div className="search-name-cell">
          <FileOutlined className="search-icon" />
          <span dangerouslySetInnerHTML={{ __html: record.highlight?.name?.[0] || text }} />
        </div>
      )
    },
    { title: '大小', dataIndex: 'sizeFormatted', key: 'size', width: 100 },
    { title: '时间', dataIndex: 'timeFormatted', key: 'time', width: 170 }
  ]

  return (
    <div className="search-page">
      <div className="search-page-header">
        <Input.Search
          className="search-main-input"
          placeholder="搜索文件名、内容..."
          value={keyword}
          onChange={e => setKeyword(e.target.value)}
          onSearch={() => doSearch(1)}
          size="large"
          enterButton
        />

        <div className="search-filters">
          <Tabs
            activeKey={currentTab}
            onChange={key => { setCurrentTab(key); searched && doSearch(1) }}
            items={FILE_TYPE_TABS.map(t => ({ key: t.key, label: t.label }))}
            size="small"
          />
          <Space>
            <Select
              value={sortField}
              onChange={v => { setSortField(v); searched && doSearch(1) }}
              size="small"
              style={{ width: 100 }}
              options={[
                { value: '_score', label: '相关性' },
                { value: 'uploaded_time', label: '时间' },
                { value: 'size', label: '大小' }
              ]}
            />
          </Space>
        </div>
      </div>

      <div className="search-results">
        {searched && (
          <div className="result-summary">
            找到 <strong>{totalHits}</strong> 个结果
          </div>
        )}
        <Table
          columns={columns}
          dataSource={results}
          loading={loading}
          rowKey="key"
          size="middle"
          className="search-table"
          pagination={{
            current: page, pageSize: 20, total: totalHits,
            showSizeChanger: false,
            showTotal: t => `共 ${t} 项`,
            onChange: p => doSearch(p)
          }}
          locale={{ emptyText: <Empty description={searched ? '未找到相关文件' : '输入关键词搜索'} /> }}
          onRow={(record) => ({
            onClick: () => navigate(`/file/${record.id}`),
            style: { cursor: 'pointer' }
          })}
        />
      </div>
    </div>
  )
}