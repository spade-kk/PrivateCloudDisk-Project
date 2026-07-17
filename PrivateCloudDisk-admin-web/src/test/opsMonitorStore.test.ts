// ============================================================
// 平台运维监控 Store 单元测试
// ============================================================
import { describe, it, expect, beforeEach } from 'vitest'
import { useOpsMonitorStore } from '@/stores/opsMonitorStore'

describe('opsMonitorStore', () => {
  beforeEach(() => {
    useOpsMonitorStore.getState().reset()
  })

  describe('初始状态', () => {
    it('节点监控应该初始化为空数组和默认值', () => {
      const state = useOpsMonitorStore.getState()
      expect(state.nodes).toEqual([])
      expect(state.nodesTotal).toBe(0)
      expect(state.currentNode).toBeNull()
      expect(state.nodeHistory).toEqual([])
    })

    it('Docker 管理应该初始化为空数组和默认值', () => {
      const state = useOpsMonitorStore.getState()
      expect(state.containers).toEqual([])
      expect(state.containersTotal).toBe(0)
      expect(state.currentContainer).toBeNull()
      expect(state.containerLogs).toEqual([])
      expect(state.images).toEqual([])
      expect(state.imagesTotal).toBe(0)
    })

    it('Storage 管理应该初始化为空数组和默认值', () => {
      const state = useOpsMonitorStore.getState()
      expect(state.pools).toEqual([])
      expect(state.poolsTotal).toBe(0)
      expect(state.volumes).toEqual([])
      expect(state.volumesTotal).toBe(0)
    })

    it('Cluster 管理应该初始化为空数组和默认值', () => {
      const state = useOpsMonitorStore.getState()
      expect(state.clusters).toEqual([])
      expect(state.currentClusterNodes).toEqual([])
      expect(state.clusterNodesTotal).toBe(0)
    })

    it('Backup 管理应该初始化为空数组和默认值', () => {
      const state = useOpsMonitorStore.getState()
      expect(state.jobs).toEqual([])
      expect(state.jobsTotal).toBe(0)
      expect(state.restoreJobs).toEqual([])
      expect(state.restoreJobsTotal).toBe(0)
    })

    it('loading 和 error 应该为默认值', () => {
      const state = useOpsMonitorStore.getState()
      expect(state.loading).toBe(false)
      expect(state.error).toBeNull()
    })
  })

  describe('API 方法存在性', () => {
    it('fetchNodes 应该是一个函数', () => {
      expect(typeof useOpsMonitorStore.getState().fetchNodes).toBe('function')
    })

    it('fetchNodeDetail 应该是一个函数', () => {
      expect(typeof useOpsMonitorStore.getState().fetchNodeDetail).toBe('function')
    })

    it('fetchNodeHistory 应该是一个函数', () => {
      expect(typeof useOpsMonitorStore.getState().fetchNodeHistory).toBe('function')
    })

    it('fetchContainers 应该是一个函数', () => {
      expect(typeof useOpsMonitorStore.getState().fetchContainers).toBe('function')
    })

    it('doContainerAction 应该是一个函数', () => {
      expect(typeof useOpsMonitorStore.getState().doContainerAction).toBe('function')
    })

    it('doCreateVolume 应该是一个函数', () => {
      expect(typeof useOpsMonitorStore.getState().doCreateVolume).toBe('function')
    })

    it('fetchClusters 应该是一个函数', () => {
      expect(typeof useOpsMonitorStore.getState().fetchClusters).toBe('function')
    })

    it('fetchJobs 应该是一个函数', () => {
      expect(typeof useOpsMonitorStore.getState().fetchJobs).toBe('function')
    })

    it('reset 应该是一个函数', () => {
      expect(typeof useOpsMonitorStore.getState().reset).toBe('function')
    })
  })

  describe('reset', () => {
    it('应该将所有状态重置为初始值', () => {
      useOpsMonitorStore.setState({
        nodes: [{ nodeId: '1', nodeName: 'test', status: 'ONLINE', ip: '127.0.0.1', cpu: { cores: 4, usagePercent: 50 }, memory: { totalBytes: 8000000000, usedBytes: 4000000000, usagePercent: 50 }, disk: { totalBytes: 100000000000, usedBytes: 50000000000, usagePercent: 50 }, network: { rxBytes: 1000, txBytes: 1000 }, containerCount: 0, podCount: 0, os: 'Linux', kernelVersion: '5.10', uptime: 86400, lastHeartbeat: '2026-01-01T00:00:00Z' }],
        loading: true,
        error: 'test error',
      })
      useOpsMonitorStore.getState().reset()
      const newState = useOpsMonitorStore.getState()
      expect(newState.nodes).toEqual([])
      expect(newState.loading).toBe(false)
      expect(newState.error).toBeNull()
    })
  })
})