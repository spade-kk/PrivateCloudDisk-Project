import { unzipSync, strFromU8 } from 'fflate'
import { get } from '@/utils/request'
import { signedWebClientRequest } from '@/runtime/webClientIdentity'

export interface LocalPluginDistribution {
  installationId: string
  installationScope: 'USER' | 'SPACE'
  pluginId: string
  name: string
  versionId: string
  version: string
  entrypoint: string
  permissionConfig: string
  configJson: string
  packageSha256: string
  packageSize: number
  signature: string
  signingKeyId: string
  downloadUrl: string
  expiresAt: string
}

export interface LocalPluginHost {
  mount: HTMLElement
  grantedPermissions: Set<string>
  invoke(capability: string, payload: unknown): Promise<unknown>
  onLog?(level: string, message: string): void
}

interface RuntimeMessage {
  type: 'ready' | 'result' | 'error' | 'sdk' | 'log'
  requestId?: string
  capability?: string
  payload?: unknown
  result?: unknown
  message?: string
  level?: string
}

/** 单个入口脚本的浏览器端解压上限，作为服务端验签与包扫描之后的第二道内存保护。 */
const MAX_ENTRYPOINT_BYTES = 1024 * 1024

/** 浏览器本地插件运行时：签名验包后在无同源权限 iframe 内执行。 */
export class WebLocalPluginRuntime {
  private iframe: HTMLIFrameElement | null = null
  private port: MessagePort | null = null
  private readyReject: ((error: Error) => void) | null = null
  private pending = new Map<string, {
    resolve(value: unknown): void
    reject(error: Error): void
    timer: ReturnType<typeof setTimeout>
  }>()

  constructor(
    private readonly distribution: LocalPluginDistribution,
    private readonly host: LocalPluginHost,
  ) {}

  async start(): Promise<void> {
    this.stop()
    const archive = await signedWebClientRequest<ArrayBuffer>(
      this.distribution.downloadUrl,
      { responseType: 'arraybuffer', timeout: 60_000 },
    )
    const bytes = new Uint8Array(archive)
    await this.verifyPackage(bytes)
    const entries = unzipSync(bytes)
    const source = entries[this.distribution.entrypoint]
    if (!source) throw new Error('本地插件入口脚本不存在')
    if (source.byteLength > MAX_ENTRYPOINT_BYTES) {
      throw new Error('本地插件入口脚本超过浏览器沙箱限制')
    }

    const channel = new MessageChannel()
    this.port = channel.port1
    this.port.onmessage = (event: MessageEvent<RuntimeMessage>) => this.handleMessage(event.data)
    this.port.start()

    const iframe = document.createElement('iframe')
    iframe.title = `${this.distribution.name} 插件沙箱`
    iframe.sandbox.add('allow-scripts')
    iframe.referrerPolicy = 'no-referrer'
    iframe.style.cssText = 'width:100%;height:100%;border:0;background:transparent'
    iframe.srcdoc = sandboxDocument(toBase64(source))
    this.host.mount.replaceChildren(iframe)
    this.iframe = iframe
    await new Promise<void>((resolve, reject) => {
      this.readyReject = reject
      const timer = setTimeout(() => {
        this.readyReject = null
        reject(new Error('插件沙箱初始化超时'))
      }, 10_000)
      iframe.addEventListener('load', () => {
        iframe.contentWindow?.postMessage({ type: 'pcd-init' }, '*', [channel.port2])
      }, { once: true })
      const originalHandler = this.port!.onmessage
      this.port!.onmessage = (event: MessageEvent<RuntimeMessage>) => {
        if (event.data.type === 'ready') {
          clearTimeout(timer)
          this.readyReject = null
          this.port!.onmessage = originalHandler
          resolve()
          return
        }
        if (event.data.type === 'error' && !event.data.requestId) {
          clearTimeout(timer)
          this.readyReject = null
          this.port!.onmessage = originalHandler
          reject(new Error(event.data.message || '插件沙箱初始化失败'))
          return
        }
        this.handleMessage(event.data)
      }
    })
  }

  async execute(entrypoint: 'activate' | 'run', context: Record<string, unknown>): Promise<unknown> {
    if (!this.port) throw new Error('插件沙箱尚未启动')
    const requestId = crypto.randomUUID()
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        this.pending.delete(requestId)
        this.stop()
        reject(new Error('本地插件执行超时，沙箱已终止'))
      }, 30_000)
      this.pending.set(requestId, { resolve, reject, timer })
      this.port!.postMessage({
        type: 'execute',
        requestId,
        payload: {
          entrypoint,
          // 只传递可结构化克隆的最小上下文，禁止 Token、密码和宿主对象进入插件。
          context: structuredClone(context),
          plugin: { id: this.distribution.pluginId, name: this.distribution.name },
        },
      })
    })
  }

  stop(): void {
    this.pending.forEach(({ reject, timer }) => {
      clearTimeout(timer)
      reject(new Error('插件沙箱已关闭'))
    })
    this.pending.clear()
    this.readyReject?.(new Error('插件沙箱已关闭'))
    this.readyReject = null
    this.port?.close()
    this.port = null
    this.iframe?.remove()
    this.iframe = null
  }

  private async handleMessage(message: RuntimeMessage): Promise<void> {
    if (message.type === 'log') {
      this.host.onLog?.(message.level || 'info', String(message.message || ''))
      return
    }
    if (message.type === 'error' && !message.requestId) {
      this.readyReject?.(new Error(message.message || '插件沙箱初始化失败'))
      this.readyReject = null
      return
    }
    if (message.type === 'sdk' && message.requestId && message.capability) {
      if (!this.host.grantedPermissions.has(message.capability)) {
        this.port?.postMessage({
          type: 'sdk-result',
          requestId: message.requestId,
          error: '插件未获得该能力权限',
        })
        return
      }
      try {
        const result = await this.host.invoke(message.capability, message.payload)
        this.port?.postMessage({ type: 'sdk-result', requestId: message.requestId, result })
      } catch (error: any) {
        this.port?.postMessage({
          type: 'sdk-result',
          requestId: message.requestId,
          error: error?.message || '宿主能力调用失败',
        })
      }
      return
    }
    if (!message.requestId) return
    const request = this.pending.get(message.requestId)
    if (!request) return
    clearTimeout(request.timer)
    this.pending.delete(message.requestId)
    if (message.type === 'result') request.resolve(message.result)
    else request.reject(new Error(message.message || '插件执行失败'))
  }

  private async verifyPackage(bytes: Uint8Array): Promise<void> {
    if (bytes.byteLength !== this.distribution.packageSize) {
      throw new Error('插件包大小与签名清单不一致')
    }
    const digest = await crypto.subtle.digest('SHA-256', exactArrayBuffer(bytes))
    const actualHash = Array.from(new Uint8Array(digest))
      .map((value) => value.toString(16).padStart(2, '0')).join('')
    if (!constantTimeTextEqual(actualHash, this.distribution.packageSha256.toLowerCase())) {
      throw new Error('插件包 SHA-256 校验失败')
    }
    const keyResponse: any = await get(
      `plugins/local/signing-keys/${encodeURIComponent(this.distribution.signingKeyId)}`,
    )
    const publicKey = fromBase64(keyResponse.data.public_key_base64)
    const key = await crypto.subtle.importKey(
      'spki',
      exactArrayBuffer(publicKey),
      { name: 'Ed25519' },
      false,
      ['verify'],
    )
    const payload = new TextEncoder().encode([
      'PCD-PLUGIN-PACKAGE-V1',
      this.distribution.pluginId,
      this.distribution.versionId,
      this.distribution.version,
      this.distribution.packageSha256.toLowerCase(),
      String(this.distribution.packageSize),
    ].join('\n'))
    const valid = await crypto.subtle.verify(
      { name: 'Ed25519' },
      key,
      exactArrayBuffer(fromBase64(this.distribution.signature)),
      exactArrayBuffer(payload),
    )
    if (!valid) throw new Error('插件包平台签名校验失败')

    // 在验签后才解析清单，避免攻击者利用未认证压缩包触发解压路径。
    const archive = unzipSync(bytes)
    const manifest = archive['manifest.yaml']
    if (!manifest || !strFromU8(manifest).includes(this.distribution.pluginId)) {
      throw new Error('插件包清单与分发记录不一致')
    }
  }
}

function sandboxDocument(sourceBase64: string): string {
  return `<!doctype html><meta charset="utf-8">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline' data:; connect-src 'none'; img-src data:; style-src 'unsafe-inline'">
<script>
let hostPort; let pluginModule; const pending = new Map();
const serializeLog = values => values.map(value => {
  try { return typeof value === 'string' ? value : JSON.stringify(value); }
  catch { return String(value); }
}).join(' ').slice(0, 4000);
for (const level of ['debug', 'info', 'log', 'warn', 'error']) {
  console[level] = (...values) => hostPort?.postMessage({
    type: 'log',
    level: level === 'log' ? 'info' : level,
    message: serializeLog(values)
  });
}
const invokeHost = (capability, payload) => new Promise((resolve, reject) => {
  const requestId = crypto.randomUUID();
  pending.set(requestId, { resolve, reject });
  hostPort.postMessage({ type: 'sdk', requestId, capability, payload });
});
globalThis.plugin = Object.freeze({
  file: Object.freeze({
    read: payload => invokeHost('client.file.read', payload),
    upload: payload => invokeHost('client.file.upload', payload)
  }),
  ui: Object.freeze({ show: payload => invokeHost('client.ui.show', payload) }),
  clipboard: Object.freeze({ write: payload => invokeHost('client.clipboard.write', payload) }),
  system: Object.freeze({ notify: (title, body) => invokeHost('client.system.notify', { title, body }) })
});
addEventListener('message', async event => {
  if (event.data?.type !== 'pcd-init' || !event.ports[0]) return;
  hostPort = event.ports[0];
  hostPort.onmessage = async ({ data }) => {
    if (data.type === 'sdk-result') {
      const item = pending.get(data.requestId); if (!item) return;
      pending.delete(data.requestId);
      data.error ? item.reject(new Error(data.error)) : item.resolve(data.result); return;
    }
    if (data.type !== 'execute') return;
    try {
      const fn = pluginModule?.[data.payload.entrypoint];
      if (typeof fn !== 'function') throw new Error('插件未导出指定入口函数');
      const result = await fn(Object.freeze(data.payload.context));
      hostPort.postMessage({ type: 'result', requestId: data.requestId, result });
    } catch (error) {
      hostPort.postMessage({ type: 'error', requestId: data.requestId, message: String(error?.message || error) });
    }
  };
  hostPort.start();
  try {
    // 插件包已经在宿主侧完成哈希与 Ed25519 验签，直接使用已认证的 Base64，
    // 避免大脚本二次展开为参数列表造成浏览器调用栈溢出。
    const moduleUrl = 'data:text/javascript;base64,${sourceBase64}';
    pluginModule = await import(moduleUrl);
    hostPort.postMessage({ type: 'ready' });
  } catch (error) {
    hostPort.postMessage({ type: 'error', message: String(error?.message || error) });
  }
});
</script>`
}

function fromBase64(value: string): Uint8Array {
  const binary = atob(value.replace(/\s+/g, ''))
  return Uint8Array.from(binary, (character) => character.charCodeAt(0))
}

function toBase64(bytes: Uint8Array): string {
  let binary = ''
  for (let index = 0; index < bytes.length; index += 0x8000) {
    binary += String.fromCharCode(...bytes.subarray(index, index + 0x8000))
  }
  return btoa(binary)
}

function constantTimeTextEqual(left: string, right: string): boolean {
  if (left.length !== right.length) return false
  let difference = 0
  for (let index = 0; index < left.length; index++) {
    difference |= left.charCodeAt(index) ^ right.charCodeAt(index)
  }
  return difference === 0
}

function exactArrayBuffer(bytes: Uint8Array): ArrayBuffer {
  return bytes.buffer.slice(
    bytes.byteOffset,
    bytes.byteOffset + bytes.byteLength,
  ) as ArrayBuffer
}
