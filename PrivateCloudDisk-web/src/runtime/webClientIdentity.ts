import request from '@/utils/request'

/**
 * Web 本地插件客户端身份。
 *
 * 浏览器只能提供 WebCrypto 软件密钥，服务端会固定评估为 low。
 * 私钥以 non-extractable CryptoKey 存入 IndexedDB，不写入 localStorage，也不向插件 iframe 暴露。
 */
interface StoredWebClientIdentity {
  id: 'primary'
  clientId: string
  deviceId: string
  publicKeyBase64: string
  privateKey: CryptoKey
  integrityLevel: 'low'
  createdAt: number
}

interface ClientApiResponse<T> {
  code: number
  message: string
  data: T
}

interface SignedRequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  data?: unknown
  params?: Record<string, unknown>
  responseType?: 'json' | 'arraybuffer' | 'blob'
  timeout?: number
}

const DATABASE_NAME = 'pcd-web-client-identity'
const STORE_NAME = 'identities'
const APP_ID = import.meta.env.VITE_WEB_PLUGIN_APP_ID || 'web.privateclouddisk.app'
const APP_VERSION = import.meta.env.VITE_APP_VERSION || 'web-1.0.0'
const API_PREFIX = normalizeApiPrefix(import.meta.env.VITE_API_BASE_URL || '/api/v1')
let identityPromise: Promise<StoredWebClientIdentity> | null = null

/** 获取或注册 Web 客户端身份，并确保它已和当前登录用户绑定。 */
export async function ensureWebClientIdentity(): Promise<StoredWebClientIdentity> {
  if (!window.isSecureContext || !crypto?.subtle || !window.indexedDB) {
    throw new Error('当前浏览器环境不支持安全的 Web 插件运行时，请使用 HTTPS 与现代浏览器')
  }
  identityPromise ||= loadOrRegisterIdentity()
  try {
    const identity = await identityPromise
    try {
      await bindCurrentUser(identity)
    } catch (error: any) {
      if (error?.status !== 401) throw error
      // Redis 公钥缓存可能已过期；使用同一不可导出私钥重新完成挑战证明以回填可信缓存。
      const refreshedClientId = await registerWebIdentity(
        identity.privateKey,
        identity.publicKeyBase64,
        identity.deviceId,
      )
      if (refreshedClientId !== identity.clientId) {
        identity.clientId = refreshedClientId
        await saveIdentity(identity)
      }
      await bindCurrentUser(identity)
    }
    return identity
  } catch (error) {
    identityPromise = null
    throw error
  }
}

/** 使用设备私钥为请求签名；只供本地插件分发、包下载和执行日志接口使用。 */
export async function signedWebClientRequest<T>(
  url: string,
  options: SignedRequestOptions = {},
): Promise<T> {
  const identity = await ensureWebClientIdentity()
  return signedRequestWithIdentity<T>(identity, url, options)
}

export function webPluginAppVersion(): string {
  return APP_VERSION
}

async function loadOrRegisterIdentity(): Promise<StoredWebClientIdentity> {
  const existing = await loadIdentity()
  if (existing?.privateKey && existing.clientId) return existing

  const generated = await generateNonExtractableKeyPair()
  const deviceId = `web-${crypto.randomUUID()}`
  const clientId = await registerWebIdentity(
    generated.privateKey,
    generated.publicKeyBase64,
    deviceId,
  )
  const identity: StoredWebClientIdentity = {
    id: 'primary',
    clientId,
    deviceId,
    publicKeyBase64: generated.publicKeyBase64,
    privateKey: generated.privateKey,
    integrityLevel: 'low',
    createdAt: Date.now(),
  }
  await saveIdentity(identity)
  return identity
}

async function registerWebIdentity(
  privateKey: CryptoKey,
  publicKeyBase64: string,
  deviceId: string,
): Promise<string> {
  const challengeResponse = await request.post<any, ClientApiResponse<{
    challenge: string
    expires_at: number
  }>>(`${API_PREFIX}/client/register-challenge`, {
    platform: 'Web',
    public_key: publicKeyBase64,
    key_algorithm: 'ECDSA-P256',
  })
  const timestamp = Math.floor(Date.now() / 1000)
  const signingPayload = [
    challengeResponse.data.challenge,
    APP_ID,
    deviceId,
    publicKeyBase64,
    String(timestamp),
  ].join('\n')
  const signature = await sign(privateKey, signingPayload)
  const registerResponse = await request.post<any, ClientApiResponse<{
    client_id: string
    integrity_level: 'low'
  }>>(`${API_PREFIX}/client/register`, {
    platform: 'Web',
    app_version: APP_VERSION,
    attestation: {
      version: '1',
      app_id: APP_ID,
      platform: 'Web',
      device_id: deviceId,
      public_key: publicKeyBase64,
      key_algorithm: 'ECDSA-P256',
      token_id: 'WebCrypto-P256',
      integrity_level: 'low',
      os_version: navigator.userAgent.slice(0, 240),
      hostname: location.hostname.slice(0, 240),
      timestamp,
      challenge: challengeResponse.data.challenge,
      signature,
      signing_payload: signingPayload,
      apple_attestation: '',
      apple_attest_key_id: '',
    },
  })
  return registerResponse.data.client_id
}

async function bindCurrentUser(identity: StoredWebClientIdentity): Promise<void> {
  await signedRequestWithIdentity(identity, `client/${identity.clientId}/bind`, {
    method: 'POST',
    data: {
      client_type: 'web',
      platform: 'web',
      app_version: APP_VERSION,
      capabilities: [
        'client.file.read',
        'client.file.upload',
        'client.ui.show',
        'client.clipboard.write',
        'client.system.notify',
        'plugin.log.write',
      ],
    },
  })
}

async function signedRequestWithIdentity<T>(
  identity: StoredWebClientIdentity,
  url: string,
  options: SignedRequestOptions,
): Promise<T> {
  const method = (options.method || 'GET').toUpperCase()
  const body = options.data === undefined ? '' : JSON.stringify(options.data)
  const timestamp = String(Date.now())
  const nonce = crypto.randomUUID()
  const canonicalPath = canonicalApiPath(url)
  const bodyHash = body ? await sha256Hex(new TextEncoder().encode(body)) : ''
  const payload = [
    method,
    canonicalPath,
    identity.clientId,
    timestamp,
    nonce,
    bodyHash,
  ].join('\n')
  const signature = await sign(identity.privateKey, payload)

  return request.request<any, T>({
    url: url.replace(/^\/?api\/v1\//, ''),
    method,
    params: options.params,
    data: body || undefined,
    responseType: options.responseType || 'json',
    timeout: options.timeout || 30_000,
    transformRequest: [(value) => value],
    skipAuthRedirect: true,
    suppressToast: true,
    authErrorMessage: 'Web 插件客户端身份已过期，请重新验证',
    headers: {
      ...(body ? { 'Content-Type': 'application/json;charset=utf-8' } : {}),
      'X-Client-ID': identity.clientId,
      'X-Request-Time': timestamp,
      'X-Request-Nonce': nonce,
      'X-Request-Sign': signature,
      'X-Sign-Algorithm': 'ECDSA-P256-SHA256',
      // 网关不会信任该值；真正的完整性等级从注册服务缓存中读取并覆盖。
      'X-Integrity-Level': identity.integrityLevel,
    },
  })
}

async function generateNonExtractableKeyPair(): Promise<{
  privateKey: CryptoKey
  publicKeyBase64: string
}> {
  const generated = await crypto.subtle.generateKey(
    { name: 'ECDSA', namedCurve: 'P-256' },
    true,
    ['sign', 'verify'],
  ) as CryptoKeyPair
  const [publicDer, privateDer] = await Promise.all([
    crypto.subtle.exportKey('spki', generated.publicKey),
    crypto.subtle.exportKey('pkcs8', generated.privateKey),
  ])
  const privateKey = await crypto.subtle.importKey(
    'pkcs8',
    privateDer,
    { name: 'ECDSA', namedCurve: 'P-256' },
    false,
    ['sign'],
  )
  new Uint8Array(privateDer).fill(0)
  return {
    privateKey,
    publicKeyBase64: bytesToBase64(new Uint8Array(publicDer)),
  }
}

async function sign(privateKey: CryptoKey, payload: string): Promise<string> {
  const signature = await crypto.subtle.sign(
    { name: 'ECDSA', hash: 'SHA-256' },
    privateKey,
    new TextEncoder().encode(payload),
  )
  return bytesToBase64(new Uint8Array(signature))
}

async function sha256Hex(bytes: Uint8Array): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', exactArrayBuffer(bytes))
  return Array.from(new Uint8Array(digest))
    .map((value) => value.toString(16).padStart(2, '0'))
    .join('')
}

function canonicalApiPath(url: string): string {
  if (/^https?:\/\//i.test(url)) return new URL(url).pathname
  const stripped = url.replace(/^\/?api\/v1\//, '').replace(/^\/+/, '')
  return `${API_PREFIX}/${stripped}`.replace(/\/{2,}/g, '/')
}

function normalizeApiPrefix(value: string): string {
  if (/^https?:\/\//i.test(value)) return new URL(value).pathname.replace(/\/$/, '')
  return `/${value}`.replace(/\/{2,}/g, '/').replace(/\/$/, '')
}

function bytesToBase64(bytes: Uint8Array): string {
  let binary = ''
  for (let index = 0; index < bytes.length; index += 0x8000) {
    binary += String.fromCharCode(...bytes.subarray(index, index + 0x8000))
  }
  return btoa(binary)
}

function exactArrayBuffer(bytes: Uint8Array): ArrayBuffer {
  return bytes.buffer.slice(
    bytes.byteOffset,
    bytes.byteOffset + bytes.byteLength,
  ) as ArrayBuffer
}

function openIdentityDatabase(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const open = indexedDB.open(DATABASE_NAME, 1)
    open.onupgradeneeded = () => {
      if (!open.result.objectStoreNames.contains(STORE_NAME)) {
        open.result.createObjectStore(STORE_NAME, { keyPath: 'id' })
      }
    }
    open.onsuccess = () => resolve(open.result)
    open.onerror = () => reject(open.error || new Error('无法打开 Web 插件身份存储'))
  })
}

async function loadIdentity(): Promise<StoredWebClientIdentity | null> {
  const database = await openIdentityDatabase()
  try {
    return await new Promise((resolve, reject) => {
      const request = database.transaction(STORE_NAME, 'readonly')
        .objectStore(STORE_NAME).get('primary')
      request.onsuccess = () => resolve(request.result || null)
      request.onerror = () => reject(request.error)
    })
  } finally {
    database.close()
  }
}

async function saveIdentity(identity: StoredWebClientIdentity): Promise<void> {
  const database = await openIdentityDatabase()
  try {
    await new Promise<void>((resolve, reject) => {
      const transaction = database.transaction(STORE_NAME, 'readwrite')
      transaction.objectStore(STORE_NAME).put(identity)
      transaction.oncomplete = () => resolve()
      transaction.onerror = () => reject(transaction.error)
      transaction.onabort = () => reject(transaction.error || new Error('Web 插件身份保存失败'))
    })
  } finally {
    database.close()
  }
}
