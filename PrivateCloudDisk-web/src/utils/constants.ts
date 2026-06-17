// ============================================================
// constants.ts — 全局常量配置
// ============================================================
// 集中管理所有业务常量，来源优先级：
//   环境变量 (VITE_*) > 默认值
// 这样部署时可通过环境变量覆盖而无需修改代码。
// ============================================================

// ============================================================
// 上传相关常量
// ============================================================

/** 文件分片上传的每片大小（字节），默认 5MB */
export const CHUNK_SIZE: number = parseInt(import.meta.env.VITE_CHUNK_SIZE) || 5 * 1024 * 1024

/** 最大并发上传数，默认 3 个分片同时上传 */
export const MAX_CONCURRENT_UPLOADS: number = parseInt(import.meta.env.VITE_MAX_CONCURRENT_UPLOADS) || 3

/** 最大并发下载数，默认 4 个文件同时下载 */
export const MAX_CONCURRENT_DOWNLOADS: number = parseInt(import.meta.env.VITE_MAX_CONCURRENT_DOWNLOADS) || 4

/** 触发分片上传的文件大小阈值（字节），默认 10MB。小于此值的文件直接整体上传 */
export const UPLOAD_THRESHOLD: number = parseInt(import.meta.env.VITE_UPLOAD_THRESHOLD) || 10 * 1024 * 1024

/** 上传失败最大重试次数 */
export const MAX_RETRIES: number = 3