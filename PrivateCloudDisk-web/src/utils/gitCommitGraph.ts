// ============================================================
// gitCommitGraph.ts — Commit DAG layout and stable branch colors
// ============================================================
// AUDIT FIX [1.1-1.25] The old implementation assigned colors from lane indexes.
// Lane indexes change whenever a merge is inserted, so the same branch changed color
// after refresh. The graph now carries a branch key on every lane and edge, while the
// color registry is stable for a repository and reserves GitHub blue for HEAD/main.

import type { GitCommit, GitRef } from '@/api/modules/git'

export interface GitGraphEdge { from: number; to: number; branchKey: string; remote: boolean }

export interface GitGraphRow {
  commit: GitCommit
  lane: number
  laneCount: number
  activeLanes: string[]
  laneKeys: string[]
  branchKey: string
  edges: GitGraphEdge[]
  labels: GitRef[]
  isMerge: boolean
}

export const HEAD_BRANCH_COLOR = '#0969da'
export const BRANCH_COLOR_PALETTE = [
  '#0969da', '#8250df', '#1a7f37', '#bf8700', '#cf222e', '#0a7ea4', '#bc4c00', '#6639ba',
  '#218bff', '#238636', '#d29922', '#f85149', '#a475f9', '#39c5cf', '#db61a2', '#8b5a2b',
  '#6e7781', '#2da44e', '#e5534b', '#b08800', '#5e6ad2', '#007c83', '#c2410c', '#be185d',
]

function remoteBranch(name: string): boolean { return /^(origin|upstream|remote)\//i.test(name) }
function branchBase(name: string): string { return name.replace(/^(origin|upstream|remote)\//i, '') }

function branchLookup(commits: GitCommit[], refs: GitRef[], headRef = ''): Map<string, string> {
  const commitsByHash = new Map(commits.map((commit) => [commit.hash, commit]))
  const branches = refs.filter((ref) => ref.type === 'BRANCH').sort((left, right) => {
    if (left.name === headRef) return -1
    if (right.name === headRef) return 1
    if (left.name === 'main' || left.name === 'refs/heads/main') return -1
    if (right.name === 'main' || right.name === 'refs/heads/main') return 1
    return left.name.localeCompare(right.name)
  })
  const result = new Map<string, string>()
  for (const branch of branches) {
    const queue = [branch.objectHash]
    const visited = new Set<string>()
    while (queue.length) {
      const hash = queue.shift() as string
      if (visited.has(hash)) continue
      visited.add(hash)
      if (!result.has(hash)) result.set(hash, branch.name)
      const commit = commitsByHash.get(hash)
      if (commit) queue.push(...commit.parents)
    }
  }
  return result
}

function fallbackLaneKey(hash: string): string { return `lane:${hash}` }

export function buildGitGraphRows(commits: GitCommit[], refs: GitRef[], headRef = ''): GitGraphRow[] {
  const refsByHash = new Map<string, GitRef[]>()
  refs.forEach((ref) => refsByHash.set(ref.objectHash, [...(refsByHash.get(ref.objectHash) || []), ref]))
  const commitsToBranches = branchLookup(commits, refs, headRef)
  const lanes: string[] = []
  return commits.map((commit) => {
    let lane = lanes.indexOf(commit.hash)
    if (lane < 0) { lane = lanes.length; lanes.push(commit.hash) }
    const before = [...lanes]
    const beforeKeys = before.map((hash) => commitsToBranches.get(hash) || fallbackLaneKey(hash))
    const commitKey = commitsToBranches.get(commit.hash) || beforeKeys[lane] || fallbackLaneKey(commit.hash)
    const parents = commit.parents.filter(Boolean)
    if (parents.length === 0) lanes.splice(lane, 1)
    else {
      lanes[lane] = parents[0]
      parents.slice(1).forEach((parent, index) => { if (!lanes.includes(parent)) lanes.splice(lane + 1 + index, 0, parent) })
    }
    const deduplicated: string[] = []
    lanes.forEach((hash) => { if (!deduplicated.includes(hash)) deduplicated.push(hash) })
    lanes.splice(0, lanes.length, ...deduplicated)
    const afterKeys = lanes.map((hash) => commitsToBranches.get(hash) || fallbackLaneKey(hash))
    const edges = parents.map((parent, index) => {
      const target = Math.max(0, lanes.indexOf(parent))
      return { from: lane, to: target, branchKey: index === 0 ? commitKey : afterKeys[target] || commitKey, remote: remoteBranch(commitKey) }
    })
    return {
      commit,
      lane,
      laneCount: Math.max(before.length, lanes.length, lane + 1),
      activeLanes: before,
      laneKeys: beforeKeys,
      branchKey: commitKey,
      edges,
      labels: refsByHash.get(commit.hash) || [],
      isMerge: parents.length > 1,
    }
  })
}

export function stableBranchColors(refs: GitRef[], headRef = '', storageKey = 'pcd.git.graph.branch-colors'): Record<string, string> {
  const branches = refs.filter((ref) => ref.type === 'BRANCH')
  const ordered = [...branches].sort((left, right) => {
    if (left.name === headRef) return -1
    if (right.name === headRef) return 1
    if (branchBase(left.name) === 'main') return -1
    if (branchBase(right.name) === 'main') return 1
    return left.name.localeCompare(right.name)
  })
  let stored: Record<string, string> = {}
  try { stored = JSON.parse(localStorage.getItem(storageKey) || '{}') as Record<string, string> } catch { stored = {} }
  const result: Record<string, string> = { ...stored }
  const used = new Set(Object.values(result))
  const head = ordered.find((ref) => ref.name === headRef) || ordered.find((ref) => branchBase(ref.name) === 'main')
  if (head) result[head.name] = HEAD_BRANCH_COLOR
  for (const ref of ordered) {
    if (result[ref.name]) continue
    const sameBase = ordered.find((candidate) => candidate.name !== ref.name && branchBase(candidate.name) === branchBase(ref.name) && result[candidate.name])
    if (sameBase) { result[ref.name] = result[sameBase.name]; continue }
    const available = BRANCH_COLOR_PALETTE.find((color) => color !== HEAD_BRANCH_COLOR && !used.has(color))
    result[ref.name] = available || BRANCH_COLOR_PALETTE[(Object.keys(result).length + 1) % BRANCH_COLOR_PALETTE.length]
    used.add(result[ref.name])
  }
  try { localStorage.setItem(storageKey, JSON.stringify(result)) } catch { /* private browsing can disable localStorage */ }
  return result
}

export function isRemoteBranch(name: string): boolean { return remoteBranch(name) }

export const GRAPH_LANE_WIDTH = 22
export const GRAPH_NODE_Y = 31
export const GRAPH_ROW_HEIGHT = 72

export function graphWidth(row: GitGraphRow, laneWidth = GRAPH_LANE_WIDTH): number { return Math.max(64, (row.laneCount + 1) * laneWidth) }
export function graphX(lane: number, laneWidth = GRAPH_LANE_WIDTH): number { return 14 + lane * laneWidth }
