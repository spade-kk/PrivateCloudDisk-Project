const ACCESS_TOKEN_KEY = 'pcd_admin_access_token'
const REFRESH_TOKEN_KEY = 'pcd_admin_refresh_token'
const ADMIN_USER_KEY = 'pcd_admin_user'

export function getAccessToken(): string | null {
  return localStorage.getItem(ACCESS_TOKEN_KEY)
}

export function setAccessToken(token: string): void {
  localStorage.setItem(ACCESS_TOKEN_KEY, token)
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_TOKEN_KEY)
}

export function setRefreshToken(token: string): void {
  localStorage.setItem(REFRESH_TOKEN_KEY, token)
}

export function setAdminUser(user: unknown): void {
  localStorage.setItem(ADMIN_USER_KEY, JSON.stringify(user))
}

export function getAdminUser<T>(): T | null {
  const raw = localStorage.getItem(ADMIN_USER_KEY)
  if (!raw) return null

  try {
    return JSON.parse(raw) as T
  } catch {
    return null
  }
}

export function clearAuthStorage(): void {
  localStorage.removeItem(ACCESS_TOKEN_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
  localStorage.removeItem(ADMIN_USER_KEY)
}