// ============================================================
// Mock 系统入口
// 通过 VITE_ENABLE_MOCK=true 环境变量启用
// ============================================================
import { request } from '@/utils/request'
import { setupMock } from './handlers'

const isMockEnabled = import.meta.env.VITE_ENABLE_MOCK === 'true'

if (isMockEnabled) {
  console.log(
    '%c[Mock] 模拟数据系统已启用 %c| 所有接口数据来自本地 Mock',
    'color: #52c41a; font-weight: bold;',
    'color: #8c8c8c;'
  )
  setupMock(request)
}

export { isMockEnabled }