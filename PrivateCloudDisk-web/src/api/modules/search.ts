// ============================================================
// search.ts — 高级文件搜索 API 模块
// ============================================================
// 封装全文搜索引擎接口，支持关键字搜索、高亮字段、过滤器、
// 排序、分页搜索、深度分页（searchAfter）等高级功能。
// 搜索引擎基于 Elasticsearch / Meilisearch 实现。
// ============================================================

import { get } from '@/utils/request'

/**
 * 默认高亮字段列表
 * 搜索结果中这些字段匹配的内容会以 <em> 标签包裹高亮显示
 */
const DEFAULT_HIGHLIGHT_FIELDS = ['filename', 'content_text', 'ocr_text', 'tags', 'summary']

/** 高级搜索选项 */
export interface AdvancedSearchOptions {
  /** 页码，从 1 开始 */
  page?: number
  /** 每页条目数，默认 12 */
  size?: number
  /** 搜索关键字 */
  keyword?: string
  /** 文件状态过滤 */
  status?: string
  /** 排序字段名 */
  sortField?: string
  /** 是否升序排序 */
  asc?: boolean
  /** 深度分页游标（替代 offset 实现高效滚动） */
  searchAfter?: string
  /** 需要高亮的字段 */
  highlightFields?: string[]
  /** 过滤条件 key-value 映射 */
  filters?: Record<string, string>
}

/**
 * 高级文件搜索
 *
 * 支持全文搜索、多字段过滤、排序、高亮、深度分页等企业搜索功能。
 * 使用 URLSearchParams 构建查询参数，避免手动拼接 URL 字符串。
 *
 * 深度分页说明：
 * 当数据量极大时，使用 searchAfter 游标替代传统 offset 分页，
 * 避免 Elasticsearch 深度分页性能问题。searchAfter 值为上一页
 * 最后一条记录的排序字段值。
 *
 * @param options - 搜索选项配置
 * @returns Promise<{ items: FileItem[], total: number, searchAfter?: string }> 搜索结果
 *
 * @example
 * // 全文搜索
 * advancedFileSearchApi({ keyword: 'report', page: 1, size: 20 })
 *
 * // 过滤搜索 + 排序
 * advancedFileSearchApi({
 *   keyword: 'invoice',
 *   filters: { file_type: 'pdf', year: '2025' },
 *   sortField: 'created_at',
 *   asc: false
 * })
 */
export function advancedFileSearchApi(options: AdvancedSearchOptions = {}): Promise<any> {
  const params = new URLSearchParams()
  const page = Number(options.page || 1)
  const size = Number(options.size || 12)

  params.append('page', String(page))
  params.append('size', String(size))

  if (options.keyword) params.append('keyword', options.keyword)
  if (options.status) params.append('status', options.status)
  if (options.sortField) params.append('sortField', options.sortField)
  if (typeof options.asc === 'boolean') params.append('asc', String(options.asc))
  if (options.searchAfter) params.append('searchAfter', options.searchAfter)

  // 高亮字段：使用默认值或自定义列表
  const highlightFields = options.highlightFields?.length
    ? options.highlightFields
    : DEFAULT_HIGHLIGHT_FIELDS
  highlightFields.forEach((field) => params.append('highlightFields', field))

  // 动态过滤条件：filters[fieldName]=value
  Object.entries(options.filters || {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null && String(value).trim() !== '') {
      params.append(`filters[${key}]`, value)
    }
  })

  return get('business/files/advanced-search', params)
}