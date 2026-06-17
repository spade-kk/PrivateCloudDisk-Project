// ============================================================
// callApi.ts — 通话记录 HTTP REST API 模块
// ============================================================
// 封装通话记录相关的 HTTP REST API 调用。
//
// 后端对应：
//   org.project.im.platform.controller.CallRecordController
// ============================================================

import { del, get } from '@/utils/request'
import type { CallRecordDTO, Result } from './types'

const CALL_BASE = 'im'

/** 查询用户通话记录列表 */
export function getCallHistoryApi(
  userId: string,
  page: number = 1,
  size: number = 20,
): Promise<Result<CallRecordDTO[]>> {
  return get(`${CALL_BASE}/api/v1/calls/history`, {
    userId,
    page,
    size,
  }, { silent: true })
}

/** 查询通话记录详情 */
export function getCallDetailApi(callId: string): Promise<Result<CallRecordDTO>> {
  return get(`${CALL_BASE}/api/v1/calls/${callId}`, {}, { silent: true })
}

/** 批量删除通话记录 */
export function deleteCallRecordsApi(ids: number[]): Promise<Result<void>> {
  return del(`${CALL_BASE}/api/v1/calls/batch`, { data: ids, silent: true })
}