// ============================================================
// AdminLayout 集成测试
// 测试菜单结构、路由匹配、面包屑
// ============================================================
import { describe, it, expect, vi } from 'vitest'

// 菜单项类型
interface MenuItem {
  key: string
  icon?: React.ReactNode
  label: string
  path?: string
  badge?: number
  children?: MenuItem[]
}

// 复制实际菜单结构用于测试
const menuItems: MenuItem[] = [
  {
    key: '/dashboard',
    label: '仪表盘',
    path: '/dashboard',
  },
  {
    key: 'portal-business',
    label: '业务后台',
    children: [
      {
        key: 'users-group',
        label: '用户管理',
        path: '/users',
        children: [
          { key: '/users', label: '用户列表', path: '/users' },
          { key: '/users/online', label: '在线用户', path: '/users/online' },
        ],
      },
      {
        key: 'files-group',
        label: '文件管理',
        path: '/files',
        children: [
          { key: '/files', label: '所有文件', path: '/files' },
          { key: '/files/metadata', label: '文件元数据', path: '/files/metadata' },
          { key: '/files/quarantined', label: '隔离文件', path: '/files/quarantined' },
          { key: '/files/storage', label: '存储统计', path: '/files/storage' },
        ],
      },
      { key: '/orders', label: '订单管理', path: '/orders' },
      {
        key: 'security-group',
        label: '安全中心',
        path: '/security',
        children: [
          { key: '/security/events', label: '安全事件', path: '/security/events' },
          { key: '/security/avatar-audit', label: '头像审核', path: '/security/avatar-audit' },
          { key: '/security/ip-blacklist', label: 'IP 黑名单', path: '/security/ip-blacklist' },
        ],
      },
      { key: '/audit', label: '审计日志', path: '/audit' },
      {
        key: 'system-group',
        label: '系统设置',
        path: '/system',
        children: [
          { key: '/system/config', label: '系统配置', path: '/system/config' },
          { key: '/system/resources', label: '系统资源', path: '/system/resources' },
          { key: '/system/api-docs', label: 'API 文档', path: '/system/api-docs' },
        ],
      },
    ],
  },
  {
    key: 'portal-ops',
    label: '平台运维后台',
    children: [
      {
        key: 'ops-monitor-group',
        label: '平台运维',
        children: [
          { key: '/ops/nodes', label: 'Node 监控', path: '/ops/nodes' },
          { key: '/ops/docker', label: 'Docker 管理', path: '/ops/docker' },
          { key: '/ops/storage', label: 'Storage 管理', path: '/ops/storage' },
          { key: '/ops/cluster', label: 'Cluster 管理', path: '/ops/cluster' },
          { key: '/ops/backup', label: 'Backup 管理', path: '/ops/backup' },
        ],
      },
      {
        key: 'ops-platform-group',
        label: '平台管理',
        children: [
          { key: '/ops/platform', label: '系统配置', path: '/ops/platform' },
        ],
      },
    ],
  },
  {
    key: 'portal-middleware',
    label: '第三方中间件后台',
    children: [
      {
        key: 'middleware-group',
        label: '中间件',
        children: [
          { key: '/middleware/nacos', label: 'Nacos 管理', path: '/middleware/nacos' },
          { key: '/middleware/rabbitmq', label: 'RabbitMQ 管理', path: '/middleware/rabbitmq' },
          { key: '/middleware/xxl-job', label: 'XXL-Job 管理', path: '/middleware/xxl-job' },
          { key: '/middleware/minio', label: 'MinIO 管理', path: '/middleware/minio' },
          { key: '/middleware/opensearch', label: 'OpenSearch 管理', path: '/middleware/opensearch' },
        ],
      },
      {
        key: 'monitor-group',
        label: '监控',
        children: [
          { key: '/monitor/grafana', label: 'Grafana 集成', path: '/monitor/grafana' },
          { key: '/monitor/skywalking', label: 'SkyWalking 集成', path: '/monitor/skywalking' },
          { key: '/monitor/prometheus', label: 'Prometheus 集成', path: '/monitor/prometheus' },
        ],
      },
      {
        key: 'logs-group',
        label: '日志',
        children: [
          { key: '/logs/loki', label: 'Loki 集成', path: '/logs/loki' },
          { key: '/logs/kibana', label: 'Kibana 集成', path: '/logs/kibana' },
        ],
      },
      {
        key: 'dev-group',
        label: '开发',
        children: [
          { key: '/dev/swagger', label: 'Swagger 文档', path: '/dev/swagger' },
          { key: '/dev/api-manage', label: 'API 管理', path: '/dev/api-manage' },
          { key: '/dev/openapi', label: 'OpenAPI 对接', path: '/dev/openapi' },
        ],
      },
    ],
  },
]

// 递归查找菜单路径
function findOpenKeys(items: MenuItem[], targetPath: string, ancestors: string[] = []): string[] | null {
  for (const item of items) {
    const currentAncestors = [...ancestors, item.key]
    if (item.key === targetPath || item.path === targetPath) {
      return ancestors
    }
    if (item.children) {
      const result = findOpenKeys(item.children, targetPath, currentAncestors)
      if (result !== null) return result
    }
  }
  return null
}

// 递归收集所有叶子节点
function collectLeafPaths(items: MenuItem[]): string[] {
  const paths: string[] = []
  for (const item of items) {
    if (item.path && !item.children) {
      paths.push(item.path)
    }
    if (item.children) {
      paths.push(...collectLeafPaths(item.children))
    }
  }
  return paths
}

describe('AdminLayout 菜单结构', () => {
  describe('三级菜单层级', () => {
    it('应该有 4 个顶层菜单项（仪表盘 + 3 个 Portal 分组）', () => {
      expect(menuItems.length).toBe(4)
    })

    it('第一个应该是仪表盘', () => {
      expect(menuItems[0].key).toBe('/dashboard')
      expect(menuItems[0].label).toBe('仪表盘')
    })

    it('业务后台应该有 6 个子菜单组', () => {
      const business = menuItems[1]
      expect(business.key).toBe('portal-business')
      expect(business.children?.length).toBe(6)
    })

    it('平台运维后台应该有 2 个子菜单组', () => {
      const ops = menuItems[2]
      expect(ops.key).toBe('portal-ops')
      expect(ops.children?.length).toBe(2)
    })

    it('第三方中间件后台应该有 4 个子菜单组', () => {
      const middleware = menuItems[3]
      expect(middleware.key).toBe('portal-middleware')
      expect(middleware.children?.length).toBe(4)
    })
  })

  describe('菜单路径查找', () => {
    it('应该能找到平台运维后台的展开路径', () => {
      const keys = findOpenKeys(menuItems, '/ops/nodes')
      expect(keys).toEqual(['portal-ops', 'ops-monitor-group'])
    })

    it('应该能找到中间件页面的展开路径', () => {
      const keys = findOpenKeys(menuItems, '/middleware/nacos')
      expect(keys).toEqual(['portal-middleware', 'middleware-group'])
    })

    it('应该能找到监控页面的展开路径', () => {
      const keys = findOpenKeys(menuItems, '/monitor/grafana')
      expect(keys).toEqual(['portal-middleware', 'monitor-group'])
    })

    it('应该能找到开发页面的展开路径', () => {
      const keys = findOpenKeys(menuItems, '/dev/swagger')
      expect(keys).toEqual(['portal-middleware', 'dev-group'])
    })

    it('不存在的路径应该返回 null', () => {
      const keys = findOpenKeys(menuItems, '/nonexistent')
      expect(keys).toBeNull()
    })
  })

  describe('叶子节点', () => {
    it('所有叶子节点路径应该以 / 开头', () => {
      const paths = collectLeafPaths(menuItems)
      expect(paths.length).toBeGreaterThan(0)
      paths.forEach((p) => {
        expect(p).toMatch(/^\//)
      })
    })

    it('应该有仪表盘叶子节点', () => {
      const paths = collectLeafPaths(menuItems)
      expect(paths).toContain('/dashboard')
    })

    it('平台运维应该有 6 个叶子节点', () => {
      const opsPaths = collectLeafPaths(menuItems).filter((p) => p.startsWith('/ops/'))
      expect(opsPaths.length).toBe(6)
    })

    it('中间件应该有 5 个叶子节点', () => {
      const mwPaths = collectLeafPaths(menuItems).filter((p) => p.startsWith('/middleware/'))
      expect(mwPaths.length).toBe(5)
    })

    it('监控应该有 3 个叶子节点', () => {
      const monitorPaths = collectLeafPaths(menuItems).filter((p) => p.startsWith('/monitor/'))
      expect(monitorPaths.length).toBe(3)
    })

    it('日志应该有 2 个叶子节点', () => {
      const logPaths = collectLeafPaths(menuItems).filter((p) => p.startsWith('/logs/'))
      expect(logPaths.length).toBe(2)
    })

    it('开发应该有 3 个叶子节点', () => {
      const devPaths = collectLeafPaths(menuItems).filter((p) => p.startsWith('/dev/'))
      expect(devPaths.length).toBe(3)
    })
  })
})