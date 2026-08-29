// ============================================================
// imAttachmentAccess.ts — IM 附件临时授权访问
// ============================================================
// [IM-WEB-ENTERPRISE-20260809 / 5.6-5.9 / 12.2 / 14.17]
// 改动原因：消息协议持久化的是稳定的 diskFileId，预览与下载授权属于短期敏感凭证，
// 不能写入消息 payload 或历史记录。旧行为直接读取 payload.url，上传端传空字符串时
// 接收端无法预览或下载。新行为按操作临时申请授权，并在成功或失败后主动释放。
// 影响范围：消息中心图片、音频、视频和普通文件访问；不会改变网盘原有下载流程。
// ============================================================

import {
  cancelDownloadGrantApi,
  createDownloadGrantApi,
  getFileContentApi,
  releaseDownloadGrantApi,
} from '@/api/modules/downloads'
import { fetchPreviewContentBlob } from '@/api/modules/previewContent'
import { downloadBlob } from '@/utils/helpers'

export interface ImAttachmentDescriptor {
  diskFileId?: string
  fileName?: string
  fileSize?: number
}

function responseGrant(response: any): string {
  return String(response?.data?.download_grant || '')
}

export async function loadImImageObjectUrl(attachment: ImAttachmentDescriptor): Promise<string> {
  if (!attachment.diskFileId) throw new Error('消息缺少附件文件 ID，无法预览')
  const blob = await fetchPreviewContentBlob(attachment.diskFileId)
  return URL.createObjectURL(blob)
}

export async function loadImMediaObjectUrl(attachment: ImAttachmentDescriptor): Promise<string> {
  if (!attachment.diskFileId) throw new Error('消息缺少附件文件 ID，无法播放')
  let downloadGrant = ''
  let completed = false
  try {
    const grantResponse = await createDownloadGrantApi(attachment.diskFileId)
    downloadGrant = responseGrant(grantResponse)
    if (grantResponse?.code !== 200 || !downloadGrant) {
      throw new Error(grantResponse?.message || '无法获取附件播放授权')
    }
    const content = await getFileContentApi(attachment.diskFileId, downloadGrant)
    const blob = content instanceof Blob ? content : new Blob([content])
    completed = true
    return URL.createObjectURL(blob)
  } finally {
    if (downloadGrant) {
      const release = completed ? releaseDownloadGrantApi : cancelDownloadGrantApi
      await release(downloadGrant).catch(() => {
        // 授权服务仍有 TTL 兜底；释放失败不能覆盖已经完成的媒体读取结果。
      })
    }
  }
}

export async function downloadImAttachment(attachment: ImAttachmentDescriptor): Promise<void> {
  if (!attachment.diskFileId) throw new Error('消息缺少附件文件 ID，无法下载')
  let downloadGrant = ''
  let completed = false
  try {
    const grantResponse = await createDownloadGrantApi(attachment.diskFileId)
    downloadGrant = responseGrant(grantResponse)
    if (grantResponse?.code !== 200 || !downloadGrant) {
      throw new Error(grantResponse?.message || '无法获取附件下载授权')
    }
    const content = await getFileContentApi(attachment.diskFileId, downloadGrant)
    const blob = content instanceof Blob ? content : new Blob([content])
    downloadBlob(blob, attachment.fileName || '聊天附件')
    completed = true
  } finally {
    if (downloadGrant) {
      const release = completed ? releaseDownloadGrantApi : cancelDownloadGrantApi
      await release(downloadGrant).catch(() => {
        // 下载结果优先；释放失败由服务端授权 TTL 回收。
      })
    }
  }
}
