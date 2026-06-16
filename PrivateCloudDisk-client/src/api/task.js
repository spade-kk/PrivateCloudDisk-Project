/**
 * api/task.js - 任务状态 API
 *
 * 后端: FastAPI tasks endpoint -> /files/tasks/{task_id}
 */
import { get } from '@/utils/request'

/**
 * 查询任务状态
 * @param {string} taskId 任务 ID
 */
export function getTaskStatus(taskId) {
  return get(`/files/tasks/${taskId}`, {}, { service: 'file' })
}