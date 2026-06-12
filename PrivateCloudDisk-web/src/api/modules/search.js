import { get } from '@/utils/request'

const DEFAULT_HIGHLIGHT_FIELDS = ['filename', 'content_text', 'ocr_text', 'tags', 'summary']

export function advancedFileSearchApi(options = {}) {
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

  const highlightFields = options.highlightFields?.length ? options.highlightFields : DEFAULT_HIGHLIGHT_FIELDS
  highlightFields.forEach(field => params.append('highlightFields', field))

  Object.entries(options.filters || {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null && String(value).trim() !== '') {
      params.append(`filters[${key}]`, value)
    }
  })

  return get('business/files/advanced-search', params)
}
