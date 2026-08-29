// VS Code Icons 的本地离线注册器。
// AUDIT FIX [2.4-2.6]：直接使用 npm 安装的完整 icons.json；不复制维护第二份图标数据，也不访问 CDN。
import { addCollection } from '@iconify/vue/offline'
import type { IconifyJSON } from '@iconify/types'
import vscodeFileIconCollection from '@iconify-json/vscode-icons/icons.json'

let registered = false

export function registerVscodeFileIcons() {
  if (registered) return
  addCollection(vscodeFileIconCollection as IconifyJSON)
  registered = true
}

export { vscodeFileIconCollection }

export function vscodeIconName(name: string): string {
  return `vscode-icons:${name}`
}
