// PDF.js 不经过项目 Axios 拦截器，请求转换资源时需要显式附加登录凭证。
import { cookie } from '@/utils/cookie'
import { TOKEN_COOKIE_KEY } from '@/utils/request'

export function buildAuthenticatedPdfSource(url: string) {
  const token = cookie.get(TOKEN_COOKIE_KEY)
  return {
    url,
    withCredentials: true,
    httpHeaders: token ? { Authorization: `Bearer ${token}` } : {},
  }
}
