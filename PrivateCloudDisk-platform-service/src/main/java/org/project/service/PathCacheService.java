package org.project.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * 路径缓存服务 — 混合模型路径 → node_id 映射缓存。
 *
 * <p>两种缓存模式：
 * <ul>
 *   <li><b>node_id + 相对路径：</b>key = "path:rel:{userId}:{parentNodeId}:{relativePath}" → value = resolved node_id</li>
 *   <li><b>纯面包屑路径：</b>key = "path:abs:{userId}:{breadcrumbPath}" → value = resolved node_id</li>
 * </ul>
 *
 * <p>缓存失效场景：
 * <ul>
 *   <li>节点被移动 → 使该节点及其所有子孙节点相关的缓存失效</li>
 *   <li>节点被重命名 → 使该节点及其所有子孙节点相关的缓存失效</li>
 *   <li>节点被删除 → 使该节点及其所有子孙节点相关的缓存失效</li>
 * </ul>
 *
 * <p>缓存 TTL：30 分钟（足够覆盖一次上传会话，同时避免过时缓存长期存在）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PathCacheService {

    private final StringRedisTemplate stringRedisTemplate;

    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    // ==================== 缓存 Key 前缀 ====================

    /** 相对路径缓存前缀：path:rel:{userId}:{parentNodeId}:{relativePath} */
    private static final String KEY_PREFIX_REL = "pcd:path:rel:";
    /** 绝对路径缓存前缀：path:abs:{userId}:{breadcrumbPath} */
    private static final String KEY_PREFIX_ABS = "pcd:path:abs:";
    /** 子树失效标记 Set 前缀：path:subtree:{userId}:{nodeId} → Set of cache keys */
    private static final String KEY_PREFIX_SUBTREE = "pcd:path:subtree:";

    // ==================== 相对路径缓存 ====================

    /**
     * 缓存 node_id + 相对路径 → node_id 映射
     *
     * @param userId       用户 ID
     * @param parentNodeId 父节点 ID
     * @param relativePath 相对路径（如 "subfolder1/subfolder2"）
     * @param resolvedId   解析后的节点 ID
     */
    public void cacheRelativePath(UUID userId, UUID parentNodeId, String relativePath, UUID resolvedId) {
        if (relativePath == null || relativePath.isEmpty()) return;
        String key = buildRelKey(userId, parentNodeId, relativePath);
        stringRedisTemplate.opsForValue().set(key, resolvedId.toString(), CACHE_TTL);
        log.debug("Cached relative path: {} -> {}", key, resolvedId);
    }

    /**
     * 查询 node_id + 相对路径 → node_id 映射
     *
     * @return 解析后的节点 ID，若未命中返回 null
     */
    public UUID getRelativePath(UUID userId, UUID parentNodeId, String relativePath) {
        String key = buildRelKey(userId, parentNodeId, relativePath);
        String value = stringRedisTemplate.opsForValue().get(key);
        if (value != null) {
            log.debug("Relative path cache hit: {}", key);
            return UUID.fromString(value);
        }
        return null;
    }

    // ==================== 绝对路径（面包屑）缓存 ====================

    /**
     * 缓存纯面包屑路径 → node_id 映射
     *
     * @param userId         用户 ID
     * @param breadcrumbPath 面包屑路径（如 "/root/folder1/subfolder2"）
     * @param resolvedId     解析后的节点 ID
     */
    public void cacheAbsolutePath(UUID userId, String breadcrumbPath, UUID resolvedId) {
        if (breadcrumbPath == null || breadcrumbPath.isEmpty()) return;
        String key = buildAbsKey(userId, breadcrumbPath);
        stringRedisTemplate.opsForValue().set(key, resolvedId.toString(), CACHE_TTL);
        log.debug("Cached absolute path: {} -> {}", key, resolvedId);
    }

    /**
     * 查询纯面包屑路径 → node_id 映射
     *
     * @return 解析后的节点 ID，若未命中返回 null
     */
    public UUID getAbsolutePath(UUID userId, String breadcrumbPath) {
        String key = buildAbsKey(userId, breadcrumbPath);
        String value = stringRedisTemplate.opsForValue().get(key);
        if (value != null) {
            log.debug("Absolute path cache hit: {}", key);
            return UUID.fromString(value);
        }
        return null;
    }

    // ==================== 缓存失效 ====================

    /**
     * 使指定节点的所有缓存失效（移动/重命名/删除时调用）。
     * <p>
     * 注意：此方法只删除该节点直接的缓存 key。
     * 子树级别的缓存失效由调用方负责（通过遍历子孙节点逐个调用）。
     * 简化实现：使用 Redis 的 SCAN + pattern 匹配删除。
     *
     * @param userId 用户 ID
     * @param nodeId 节点 ID
     */
    public void invalidateNode(UUID userId, UUID nodeId) {
        // 删除相对路径缓存（pattern: pcd:path:rel:{userId}:*）
        // 删除绝对路径缓存（pattern: pcd:path:abs:{userId}:*）
        // 简化方案：删除所有该用户的路径缓存
        // 生产环境应使用 SCAN + pipeline 批量删除
        String relPattern = "pcd:path:rel:" + userId + ":*";
        String absPattern = "pcd:path:abs:" + userId + ":*";
        int relCount = deleteByPattern(relPattern);
        int absCount = deleteByPattern(absPattern);
        log.info("Invalidated path caches for user {}: {} rel, {} abs", userId, relCount, absCount);
    }

    /**
     * 使指定用户的所有路径缓存失效。
     * 在节点被移动/重命名/删除时，保守策略：清除该用户全部缓存。
     */
    public void invalidateUser(UUID userId) {
        invalidateNode(userId, null);
    }

    // ==================== 内部方法 ====================

    private String buildRelKey(UUID userId, UUID parentNodeId, String relativePath) {
        return KEY_PREFIX_REL + userId + ":" + parentNodeId + ":" + relativePath;
    }

    private String buildAbsKey(UUID userId, String breadcrumbPath) {
        // 标准化路径：去除首尾空格，确保以 / 开头
        String normalized = breadcrumbPath.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        return KEY_PREFIX_ABS + userId + ":" + normalized;
    }

    private int deleteByPattern(String pattern) {
        try {
            var keys = stringRedisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                Long deleted = stringRedisTemplate.delete(keys);
                return deleted != null ? deleted.intValue() : 0;
            }
        } catch (Exception e) {
            log.warn("Failed to delete cache keys by pattern {}: {}", pattern, e.getMessage());
        }
        return 0;
    }
}