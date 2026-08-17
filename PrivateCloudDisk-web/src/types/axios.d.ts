import 'axios'

declare module 'axios' {
  /**
   * PrivateCloudDisk 请求扩展项。
   *
   * 业务短期授权或设备签名失效不等于登录 JWT 失效，可跳过全局登出；
   * 静默轮询、可信客户端自恢复也可关闭重复 Toast。
   */
  interface AxiosRequestConfig {
    suppressToast?: boolean
    silent?: boolean
    skipAuthRedirect?: boolean
    authErrorMessage?: string
  }

  interface InternalAxiosRequestConfig {
    suppressToast?: boolean
    silent?: boolean
    skipAuthRedirect?: boolean
    authErrorMessage?: string
  }
}

