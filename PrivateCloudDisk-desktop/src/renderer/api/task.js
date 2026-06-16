/**
 * api/task.js - 异步任务状态查询 API
 *
 * 后端: FastAPI file service
 * 端点: GET /files/tasks/{task_id}
 */
import { get } from '@/utils/request'

/** 查询任务状态
 *  GET /files/tasks/{taskId}
 *  返回: { task_id, status, steps, progress, result }
 */
export function getTaskStatus(taskId) {
  return get(`/files/tasks/${taskId}`, {}, 'file')
}