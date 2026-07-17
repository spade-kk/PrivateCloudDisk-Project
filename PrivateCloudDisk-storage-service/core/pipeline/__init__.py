"""
文件处理流水线模块
每个流水线负责一个处理步骤，返回统一的结果结构

流水线清单:
  - MergePipeline       : 分块合并
  - HashPipeline        : 哈希计算
  - VirusScanPipeline   : 病毒扫描
  - ThumbnailPipeline   : 缩略图生成
  - TranscodePipeline   : 视频转码
  - HlsTranscodePipeline: HLS 流媒体转码
  - MarkActivePipeline  : 标记活跃
  - ContentIndexPipeline: 全文索引
  - OfficeToPdfPipeline : Office 文件转 PDF 预览资源
"""