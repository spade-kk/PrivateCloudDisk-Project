export const CHUNK_SIZE = parseInt(import.meta.env.VITE_CHUNK_SIZE) || 5 * 1024 * 1024
export const MAX_CONCURRENT_UPLOADS = parseInt(import.meta.env.VITE_MAX_CONCURRENT_UPLOADS) || 3
export const MAX_CONCURRENT_DOWNLOADS = parseInt(import.meta.env.VITE_MAX_CONCURRENT_DOWNLOADS) || 4
export const UPLOAD_THRESHOLD = parseInt(import.meta.env.VITE_UPLOAD_THRESHOLD) || 10 * 1024 * 1024
export const MAX_RETRIES = 3