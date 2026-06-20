/**
 * components/UploadDialog.jsx - 文件上传弹窗组件
 *
 * 流程:
 * 1. 创建上传会话 → platform service POST /business/uploads/
 * 2. 逐片上传 → file service POST /files/uploads/{uploads_id}/chunks
 * 3. 合并分片 → file service POST /files/uploads/{uploads_id}/merge
 * 4. 轮询任务状态 → file service GET /files/tasks/{task_id}
 */
import React, { useState, useRef, useCallback } from 'react'
import { Modal, Button, Progress, Space, message, Upload } from 'antd'
import { InboxOutlined, UploadOutlined } from '@ant-design/icons'
import { useAppStore } from '@/store/appStore'
import { createUploadSession, uploadChunk, mergeChunks } from '@/api/upload'
import { getTaskStatus } from '@/api/task'
import { CHUNK_SIZE, TASK_STATUS } from '@/utils/const'
import { formatFileSize } from '@/utils/helper'
import './UploadDialog.css'

const { Dragger } = Upload

export default function UploadDialog({ visible, onClose }) {
  const { currentNodeId, addUploadTask, updateUploadTask } = useAppStore()
  const [file, setFile] = useState(null)
  const [uploading, setUploading] = useState(false)
  const [progress, setProgress] = useState(0)
  const [currentChunk, setCurrentChunk] = useState(0)
  const [totalChunks, setTotalChunks] = useState(0)
  const [speed, setSpeed] = useState('')
  const [estimated, setEstimated] = useState('')
  const [taskId, setTaskId] = useState('')
  const [taskStatus, setTaskStatus] = useState(null)
  const abortRef = useRef(false)

  const reset = () => {
    setFile(null)
    setUploading(false)
    setProgress(0)
    setCurrentChunk(0)
    setTotalChunks(0)
    setSpeed('')
    setEstimated('')
    setTaskId('')
    setTaskStatus(null)
    abortRef.current = false
  }

  const handleClose = () => {
    if (uploading && !taskId) {
      abortRef.current = true
    }
    reset()
    onClose()
  }

  const handleFileSelect = (fileObj) => {
    const f = fileObj.file || fileObj
    setFile({
      name: f.name,
      size: f.size,
      path: f.path || (f.webkitRelativePath || ''),
      fileObj: f
    })
    return false
  }

  const startUpload = async () => {
    if (!file) return
    setUploading(true)
    abortRef.current = false

    const total = Math.ceil(file.size / CHUNK_SIZE)
    setTotalChunks(total)
    const startTime = Date.now()
    const taskId = Date.now().toString(36)
    addUploadTask({ id: taskId, name: file.name, progress: 0, status: 'uploading' })

    try {
      // 1. 创建上传会话 (platform service)
      const sessionRes = await createUploadSession({
        total_chunks: total,
        file_size: file.size,
        file_checksum: '',
        chunks_max_size: CHUNK_SIZE,
        file_name: file.name,
        file_type: file.name.split('.').pop() || 'unknown',
        node_id: currentNodeId || 'root'
      })
      // 后端返回: { code: 200, data: 'uploads_id_string' }
      const uploadsId = sessionRes.data

      // 2. 逐片上传 (file service)
      for (let i = 1; i <= total; i++) {
        if (abortRef.current) {
          message.warning('上传已取消')
          return
        }

        setCurrentChunk(i)
        const start = (i - 1) * CHUNK_SIZE
        const end = Math.min(start + CHUNK_SIZE, file.size)

        await uploadChunk(uploadsId, i, file.path, start, end)

        const pct = Math.round((i / total) * 100)
        setProgress(pct)
        updateUploadTask(taskId, { progress: pct })

        // 速度 & 剩余时间
        const elapsed = (Date.now() - startTime) / 1000
        if (elapsed > 0.1) {
          const uploaded = i * CHUNK_SIZE
          const spd = uploaded / elapsed
          setSpeed(`${formatFileSize(spd)}/s`)
          const remain = (file.size - uploaded) / spd
          setEstimated(remain < 60 ? `${Math.ceil(remain)}秒` : `${Math.ceil(remain / 60)}分钟`)
        }
      }

      // 3. 合并分片 (file service)
      message.loading({ content: '正在合并文件...', key: 'merge' })
      const mergeRes = await mergeChunks(uploadsId)
      // 后端返回: { code: 200, data: { task_id: 'xxx' } }
      const tId = mergeRes.data?.task_id || mergeRes.data
      setTaskId(tId)
      updateUploadTask(taskId, { status: 'processing' })
      message.success({ content: '上传完成，正在处理...', key: 'merge' })

      // 4. 轮询任务状态
      const interval = setInterval(async () => {
        try {
          const res = await getTaskStatus(tId)
          const status = res.data?.status || res.data
          setTaskStatus({ status, steps: res.data?.steps || [] })
          if ([TASK_STATUS.COMPLETED, TASK_STATUS.FAILED, TASK_STATUS.CANCELLED].includes(status)) {
            clearInterval(interval)
            updateUploadTask(taskId, {
              status: status === TASK_STATUS.COMPLETED ? 'completed' : 'failed'
            })
            if (status === TASK_STATUS.FAILED) {
              message.error('文件处理失败')
            }
          }
        } catch {
          clearInterval(interval)
        }
      }, 2000)
    } catch (e) {
      message.error('上传失败: ' + (e.message || '未知错误'))
      updateUploadTask(taskId, { status: 'failed' })
    }
  }

  const steps = taskStatus?.steps || []

  return (
    <Modal
      title="上传文件"
      open={visible}
      onCancel={handleClose}
      footer={null}
      width={560}
      destroyOnClose
    >
      <div className="upload-dialog">
        {!uploading && !taskId && (
          <>
            <Dragger
              multiple={false}
              beforeUpload={handleFileSelect}
              showUploadList={false}
              accept="*"
            >
              <p className="ant-upload-drag-icon">
                <InboxOutlined />
              </p>
              <p className="ant-upload-text">点击或拖拽文件到此处上传</p>
              <p className="ant-upload-hint">支持单文件最大 5GB</p>
            </Dragger>

            {file && (
              <div className="file-preview">
                <span className="file-preview-name">{file.name}</span>
                <span className="file-preview-size">{formatFileSize(file.size)}</span>
              </div>
            )}

            <Button
              type="primary"
              block
              icon={<UploadOutlined />}
              onClick={startUpload}
              disabled={!file}
              style={{ marginTop: 16, height: 40 }}
            >
              开始上传
            </Button>
          </>
        )}

        {uploading && !taskId && (
          <div className="upload-progress-area">
            <h4>正在上传: {file?.name}</h4>
            <Progress percent={progress} strokeColor="#1a73e8" />
            <p className="chunk-info">分片 {currentChunk}/{totalChunks}</p>
            <div className="upload-stats">
              <span>{speed}</span>
              <span>预计剩余 {estimated}</span>
            </div>
          </div>
        )}

        {taskId && (
          <div className="task-status-area">
            <h4>任务处理中</h4>
            <p className="task-id">任务 ID: {taskId}</p>
            {taskStatus && (
              <>
                <p className={`task-status-text status-${taskStatus.status}`}>
                  {taskStatus.status === TASK_STATUS.COMPLETED ? '处理完成'
                    : taskStatus.status === TASK_STATUS.PROCESSING ? '处理中...'
                    : taskStatus.status === TASK_STATUS.FAILED ? '处理失败'
                    : taskStatus.status === TASK_STATUS.CANCELLED ? '已取消'
                    : '等待处理'}
                </p>
                <div className="task-steps">
                  {steps.map((step, idx) => (
                    <div key={idx} className="task-step">
                      <span className={`step-dot ${step.status}`} />
                      <span className="step-name">{step.step}</span>
                      <span className={`step-status ${step.status}`}>{step.status}</span>
                    </div>
                  ))}
                </div>
              </>
            )}
          </div>
        )}
      </div>
    </Modal>
  )
}