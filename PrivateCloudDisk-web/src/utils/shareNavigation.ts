export interface ShareBreadcrumbItem {
  id: string
  name: string
}

export interface ShareDirectoryChild {
  id: string
  name: string
}

export interface ShareDirectoryNode extends ShareBreadcrumbItem {
  depth: number
  kind: 'root' | 'ancestor' | 'current' | 'child'
  isCurrent: boolean
}

/**
 * 构建公开分享目录导航的确定性层级数据。
 *
 * AUDIT FIX [1.2]（目录导航需求）：原重复 DOM 结构会在递归进入五级以上目录时压平层级；
 * 新行为把 depth 固化在数据模型中，祖先、当前目录和直属子目录始终按真实路径缩进。
 */
export function buildShareDirectoryNodes(
  breadcrumbs: ShareBreadcrumbItem[],
  children: ShareDirectoryChild[],
): ShareDirectoryNode[] {
  const nodes: ShareDirectoryNode[] = [
    {
      id: '__share_root__',
      name: '全部资源',
      depth: 0,
      kind: 'root',
      isCurrent: breadcrumbs.length === 0,
    },
  ]

  breadcrumbs.forEach((item, index) => {
    const isCurrent = index === breadcrumbs.length - 1
    nodes.push({
      ...item,
      depth: index + 1,
      kind: isCurrent ? 'current' : 'ancestor',
      isCurrent,
    })
  })

  children.forEach((item) => {
    nodes.push({
      ...item,
      depth: breadcrumbs.length + 1,
      kind: 'child',
      isCurrent: false,
    })
  })

  return nodes
}

/**
 * 返回以目标目录结束的新路径：点击现有祖先时截断路径，进入子目录时追加节点。
 * 使用不可变数组避免 Vue 在深层导航时因原数组被就地修改而丢失更新。
 */
export function resolveShareBreadcrumb(
  breadcrumbs: ShareBreadcrumbItem[],
  target: ShareBreadcrumbItem,
): ShareBreadcrumbItem[] {
  const existingIndex = breadcrumbs.findIndex((item) => item.id === target.id)
  if (existingIndex >= 0) return breadcrumbs.slice(0, existingIndex + 1)
  return [...breadcrumbs, target]
}
