export interface CookieOptions {
  days?: number
  path?: string
}

function set(name: string, value: string, options: CookieOptions = {}): void {
  const { days = 7, path = '/' } = options
  let cookieStr = `${encodeURIComponent(name)}=${encodeURIComponent(value)}`
  cookieStr += `; path=${path}`
  cookieStr += `; max-age=${days * 86400}`

  if (window.location.protocol === 'https:') {
    cookieStr += '; Secure; SameSite=Strict'
  } else {
    cookieStr += '; SameSite=Lax'
  }

  document.cookie = cookieStr
}

function get(name: string): string | null {
  const encoded = encodeURIComponent(name) + '='
  const cookies = document.cookie.split(';')
  for (let i = 0; i < cookies.length; i++) {
    let c = cookies[i].trim()
    if (c.indexOf(encoded) === 0) {
      return decodeURIComponent(c.substring(encoded.length))
    }
  }
  return null
}

function remove(name: string, path: string = '/'): void {
  document.cookie = `${encodeURIComponent(name)}=; path=${path}; max-age=0`
}

function has(name: string): boolean {
  return get(name) !== null
}

export const cookie = { set, get, remove, has }