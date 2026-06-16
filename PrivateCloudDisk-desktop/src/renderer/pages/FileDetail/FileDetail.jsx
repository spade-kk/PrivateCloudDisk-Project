/**
 * pages/FileDetail/FileDetail.jsx - 文件详情页面
 *
 * 后端: Spring Boot FileController
 * 返回: FileVO { file_id, file_name, file_size, file_type, uploaded_time, user_id, node_id, storage_path, ... }
 */
import React, { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { Card, Button, Descriptions, Image, message, Spin, Empty, Space } from 'antd'
import { DownloadOutlined, ArrowLeftOutlined, FileOutlined, PlayCircleOutlined } from '@ant-design/icons'
import { getFileDetail } from '@/api/file'
import { requestOperationToken, getThumbnailUrl } from '@/api/download'
import { formatFileSize, formatTime, getFileCategory, getFileExtension } from '@/utils/helper'
import './FileDetail.css'

export default function FileDetailPage() {
  const { fileId } = useParams()
  const navigate = useNavigate()
  const [fileInfo, setFileInfo] = useState(null)
  const [loading, setLoading] = useState(true)
  const [downloading, setDownloading] = useState(false)
  const [downloadProgress, setDownloadProgress] = useState(0)

  useEffect(() => {
    if (!fileId) return
    setLoading(true)
    getFileDetail(fileId)
      .then(res => {
        const data = res.data
        setFileInfo({
          ...data,
          category: getFileCategory(data.file_name || data.name),
          ext: getFileExtension(data.file_name || data.name),
          sizeFormatted: formatFileSize(data.file_size || data.size),
          timeFormatted: formatTime(data.uploaded_time || data.updated_at)
        })
      })
      .catch(() => message.error('加载文件信息失败'))
      .finally(() => setLoading(false))
  }, [fileId])

  const handleDownload = async () => {
    if (!fileInfo) return
    setDownloading(true)
    setDownloadProgress(0)
    try {
      const tokenRes = await requestOperationToken({
        file_id: fileId,
        operation_type: 'download'
      })
      const opToken = tokenRes.data?.operation_token || tokenRes.data
      const { downloadFile } = await import('@/api/download')
      const savePath = await downloadFile(fileId, opToken, fileInfo.file_name || fileInfo.name, (pct) => {
        setDownloadProgress(pct)
      })
      message.success(`下载完成: ${savePath}`)
      try { window.electronAPI?.showItemInFolder?.(savePath) } catch { /* ignore */ }
    } catch (e) {
      message.error(e.message || '下载失败')
    } finally {
      setDownloading(false)
      setDownloadProgress(0)
    }
  }

  const isImage = fileInfo?.category?.key === 'IMAGE'
  const isVideo = fileInfo?.category?.key === 'VIDEO'

  if (loading) {
    return <div className="detail-loading"><Spin size="large" /></div>
  }

  if (!fileInfo) {
    return <div className="detail-empty"><Empty description="文件不存在" /></div>
  }

  const name = fileInfo.file_name || fileInfo.name || '未知文件'

  return (
    <div className="file-detail-page">
      <div className="detail-header">
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate(-1)}>返回</Button>
        <h2 className="detail-title">{name}</h2>
      </div>

      <div className="detail-layout">
        <Card bordered={false} className="detail-main-card">
          {/* 文件图标或预览 */}
          <div className="detail-file-icon">
            {isImage ? (
              <Image
                src={getThumbnailUrl(fileInfo.node_id || 'root', name)}
                alt={name}
                style={{ maxWidth: 400, maxHeight: 300, objectFit: 'contain' }}
                fallback="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII="
              />
            ) : (
              <FileOutlined style={{ fontSize: 96, color: '#5f6368' }} />
            )}
          </div>

          <div className="detail-actions">
            <Space>
              <Button
                type="primary"
                icon={<DownloadOutlined />}
                size="large"
                loading={downloading}
                onClick={handleDownload}
              >
                {downloading ? `下载中 ${downloadProgress}%` : '下载文件'}
              </Button>
              {isVideo && (
                <Button
                  icon={<PlayCircleOutlined />}
                  size="large"
                  onClick={() => navigate(`/video/${fileId}`)}
                >
                  播放视频
                </Button>
              )}
            </Space>
          </div>

          <Descriptions column={2} bordered size="small" className="detail-info">
            <Descriptions.Item label="文件名">{name}</Descriptions.Item>
            <Descriptions.Item label="大小">{fileInfo.sizeFormatted}</Descriptions.Item>
            <Descriptions.Item label="类型">{fileInfo.ext?.toUpperCase() || '-'}</Descriptions.Item>
            <Descriptions.Item label="分类">{fileInfo.category?.label || '其他'}</Descriptions.Item>
            <Descriptions.Item label="上传时间">{fileInfo.timeFormatted}</Descriptions.Item>
            <Descriptions.Item label="文件 ID" span={2}>{fileId}</Descriptions.Item>
          </Descriptions>
        </Card>
      </div>
    </div>
  )
}