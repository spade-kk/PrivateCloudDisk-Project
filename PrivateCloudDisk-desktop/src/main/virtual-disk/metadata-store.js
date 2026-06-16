/**
 * virtual-disk/metadata-store.js - 本地元数据缓存
 *
 * 使用 SQLite 存储云盘文件/目录的元数据，(路径 → Node ID → 属性) 映射。
 * 作用：避免每次 FUSE 操作都请求后端 API，减少延迟。
 *
 * 表结构:
 *   nodes: 文件/目录节点信息
 *   children: 父子关系 (目录结构)
 *   sync_state: 文件同步状态跟踪
 */

const Database = require('better-sqlite3')
const path = require('path')
const { logger } = require('./utils')

class MetadataStore {
  constructor(dbPath) {
    this.db = new Database(dbPath)
    this.db.pragma('journal_mode = WAL')       // 并发写入优化
    this.db.pragma('synchronous = NORMAL')     // 性能平衡
    this.db.pragma('cache_size = -8000')         // 8MB 缓存
    this._initTables()
    this._initPreparedStatements()
    logger.info('MetadataStore', `数据库已初始化: ${dbPath}`)
  }

  // ==================== 表初始化 ====================

  _initTables() {
    this.db.exec(`
      CREATE TABLE IF NOT EXISTS nodes (
        node_id      TEXT PRIMARY KEY,
        parent_id    TEXT NOT NULL,
        name         TEXT NOT NULL,
        is_folder    INTEGER NOT NULL DEFAULT 0,
        size         INTEGER NOT NULL DEFAULT 0,
        file_type    TEXT DEFAULT 'other',
        mime_type    TEXT,
        hash         TEXT,
        created_at   INTEGER,
        updated_at   INTEGER,
        remote_node_id TEXT,       -- 后端返回的原始 node_id
        etag         TEXT,          -- 用于增量同步比较
        version      INTEGER DEFAULT 1,
        UNIQUE(parent_id, name)
      );

      CREATE INDEX IF NOT EXISTS idx_nodes_parent_id ON nodes(parent_id);
      CREATE INDEX IF NOT EXISTS idx_nodes_name ON nodes(name);
      CREATE INDEX IF NOT EXISTS idx_nodes_remote ON nodes(remote_node_id);

      CREATE TABLE IF NOT EXISTS sync_state (
        node_id      TEXT PRIMARY KEY REFERENCES nodes(node_id),
        status       TEXT NOT NULL DEFAULT 'synced',  -- synced | dirty | syncing | conflict | error
        local_mtime  INTEGER,
        remote_mtime INTEGER,
        last_sync_at INTEGER,
        error_msg    TEXT
      );

      CREATE INDEX IF NOT EXISTS idx_sync_status ON sync_state(status);

      CREATE TABLE IF NOT EXISTS mount_state (
        key          TEXT PRIMARY KEY,
        value        TEXT
      );
    `)
  }

  _initPreparedStatements() {
    // 节点 CRUD
    this.stmt_upsertNode = this.db.prepare(`
      INSERT INTO nodes (node_id, parent_id, name, is_folder, size, file_type, mime_type, hash, created_at, updated_at, remote_node_id, etag, version)
      VALUES (@node_id, @parent_id, @name, @is_folder, @size, @file_type, @mime_type, @hash, @created_at, @updated_at, @remote_node_id, @etag, @version)
      ON CONFLICT(parent_id, name) DO UPDATE SET
        size = excluded.size,
        updated_at = excluded.updated_at,
        remote_node_id = COALESCE(excluded.remote_node_id, nodes.remote_node_id),
        etag = excluded.etag,
        version = nodes.version + 1
    `)

    this.stmt_getNode = this.db.prepare('SELECT * FROM nodes WHERE node_id = ?')
    this.stmt_getNodeByPath = this.db.prepare(`
      SELECT n.* FROM nodes n
      WHERE n.parent_id = ? AND n.name = ?
    `)

    this.stmt_getChildren = this.db.prepare(`
      SELECT * FROM nodes WHERE parent_id = ? ORDER BY is_folder DESC, name ASC
    `)

    this.stmt_deleteNode = this.db.prepare('DELETE FROM nodes WHERE node_id = ?')
    this.stmt_deleteChildren = this.db.prepare('DELETE FROM nodes WHERE parent_id = ?')

    this.stmt_updateNodeName = this.db.prepare(`
      UPDATE nodes SET name = @new_name, updated_at = @updated_at WHERE node_id = @node_id
    `)

    this.stmt_updateNodeParent = this.db.prepare(`
      UPDATE nodes SET parent_id = @new_parent_id, updated_at = @updated_at WHERE node_id = @node_id
    `)

    this.stmt_updateNodeSize = this.db.prepare(`
      UPDATE nodes SET size = ? WHERE node_id = ?
    `)

    // 同步状态
    this.stmt_upsertSync = this.db.prepare(`
      INSERT INTO sync_state (node_id, status, local_mtime, remote_mtime, last_sync_at, error_msg)
      VALUES (@node_id, @status, @local_mtime, @remote_mtime, @last_sync_at, @error_msg)
      ON CONFLICT(node_id) DO UPDATE SET
        status = excluded.status,
        local_mtime = excluded.local_mtime,
        remote_mtime = excluded.remote_mtime,
        last_sync_at = excluded.last_sync_at,
        error_msg = excluded.error_msg
    `)

    this.stmt_getSyncState = this.db.prepare('SELECT * FROM sync_state WHERE node_id = ?')
    this.stmt_getDirtyNodes = this.db.prepare("SELECT * FROM sync_state WHERE status = 'dirty'")

    // 挂载状态
    this.stmt_setMountState = this.db.prepare(`
      INSERT INTO mount_state (key, value) VALUES (?, ?)
      ON CONFLICT(key) DO UPDATE SET value = excluded.value
    `)
    this.stmt_getMountState = this.db.prepare('SELECT value FROM mount_state WHERE key = ?')
  }

  // ==================== 节点操作 ====================

  /** 创建或更新节点 */
  upsertNode(node) {
    const {
      node_id, parent_id, name, is_folder = false, size = 0,
      file_type = 'other', mime_type = null, hash = null,
      created_at = Math.floor(Date.now() / 1000),
      updated_at = Math.floor(Date.now() / 1000),
      remote_node_id = null, etag = null, version = 1
    } = node
    return this.stmt_upsertNode.run({
      node_id, parent_id, name,
      is_folder: is_folder ? 1 : 0,
      size, file_type, mime_type, hash,
      created_at, updated_at,
      remote_node_id, etag, version
    })
  }

  /** 获取节点 */
  getNode(nodeId) {
    return this.stmt_getNode.get(nodeId) || null
  }

  /** 根据父路径和名称获取节点 */
  getNodeByPath(parentId, name) {
    return this.stmt_getNodeByPath.get(parentId, name) || null
  }

  /** 获取子节点列表 */
  getChildren(parentId) {
    return this.stmt_getChildren.all(parentId)
  }

  /** 删除节点 */
  deleteNode(nodeId) {
    this.stmt_deleteNode.run(nodeId)
    this.db.prepare('DELETE FROM sync_state WHERE node_id = ?').run(nodeId)
  }

  /** 批量删除子节点 */
  deleteChildren(parentId) {
    this.stmt_deleteChildren.run(parentId)
  }

  /** 重命名节点 */
  renameNode(nodeId, newName) {
    return this.stmt_updateNodeName.run({
      node_id: nodeId,
      new_name: newName,
      updated_at: Math.floor(Date.now() / 1000)
    })
  }

  /** 移动节点 */
  moveNode(nodeId, newParentId) {
    return this.stmt_updateNodeParent.run({
      node_id: nodeId,
      new_parent_id: newParentId,
      updated_at: Math.floor(Date.now() / 1000)
    })
  }

  /** 更新节点大小 */
  updateNodeSize(nodeId, size) {
    return this.stmt_updateNodeSize.run(size, nodeId)
  }

  // ==================== 同步状态 ====================

  /** 设置同步状态 */
  setSyncState(nodeId, status, opts = {}) {
    return this.stmt_upsertSync.run({
      node_id: nodeId,
      status,
      local_mtime: opts.localMtime || null,
      remote_mtime: opts.remoteMtime || null,
      last_sync_at: opts.lastSyncAt || null,
      error_msg: opts.errorMsg || null
    })
  }

  /** 获取同步状态 */
  getSyncState(nodeId) {
    return this.stmt_getSyncState.get(nodeId) || null
  }

  /** 获取所有脏节点 (需要同步) */
  getDirtyNodes() {
    return this.stmt_getDirtyNodes.all()
  }

  // ==================== 挂载状态 ====================

  setMountState(key, value) {
    this.stmt_setMountState.run(key, JSON.stringify(value))
  }

  getMountState(key) {
    const row = this.stmt_getMountState.get(key)
    return row ? JSON.parse(row.value) : null
  }

  // ==================== 批量操作 ====================

  /** 批量同步节点 (从后端 API 拉取的目录树) */
  bulkSyncNodes(parentId, remoteNodes) {
    const transaction = this.db.transaction(() => {
      // 先删除旧子节点
      this.stmt_deleteChildren.run(parentId)

      for (const remote of remoteNodes) {
        this.upsertNode({
          node_id: remote.node_id || remote.id,
          parent_id: parentId,
          name: remote.name,
          is_folder: remote.is_folder || remote.isFolder || false,
          size: remote.size || 0,
          file_type: remote.file_type || remote.fileType || 'other',
          mime_type: remote.mime_type || remote.mimeType || null,
          hash: remote.hash || null,
          created_at: remote.created_at || remote.createdAt || Math.floor(Date.now() / 1000),
          updated_at: remote.updated_at || remote.updatedAt || Math.floor(Date.now() / 1000),
          remote_node_id: remote.node_id || remote.id || null,
          etag: remote.etag || null
        })
      }
    })
    transaction()
    logger.info('MetadataStore', `批量同步: parent=${parentId}, count=${remoteNodes.length}`)
  }

  /** 清空所有数据 */
  clearAll() {
    this.db.exec('DELETE FROM nodes; DELETE FROM sync_state; DELETE FROM mount_state;')
    logger.info('MetadataStore', '所有数据已清空')
  }

  /** 获取统计信息 */
  getStats() {
    const nodeCount = this.db.prepare('SELECT COUNT(*) as count FROM nodes').get()
    const folderCount = this.db.prepare('SELECT COUNT(*) as count FROM nodes WHERE is_folder = 1').get()
    const dirtyCount = this.db.prepare("SELECT COUNT(*) as count FROM sync_state WHERE status = 'dirty'").get()
    const syncingCount = this.db.prepare("SELECT COUNT(*) as count FROM sync_state WHERE status = 'syncing'").get()
    return {
      totalNodes: nodeCount.count,
      totalFolders: folderCount.count,
      dirtyNodes: dirtyCount.count,
      syncingNodes: syncingCount.count
    }
  }

  /** 关闭数据库 */
  close() {
    this.db.close()
    logger.info('MetadataStore', '数据库已关闭')
  }
}

module.exports = { MetadataStore }