package org.project.service;

import org.project.context.SpaceContextHolder;

import java.util.UUID;

/**
 * 公共空间上下文与权限校验服务。
 *
 * <p>需求：空间管理能力全量集成（三-1/4）。
 * 所有空间有效性、成员关系、操作权限和资源归属判断集中维护于此，
 * 禁止业务模块复制角色判断逻辑。</p>
 */
public interface SpacePermissionService {

    SpaceContextHolder.SpaceContext resolveContext(UUID userId, String requestedSpaceId);

    void requireOperation(SpaceContextHolder.SpaceContext context, SpaceOperation operation);

    void requireFileInCurrentSpace(UUID fileId);

    /**
     * 需求 3.1：以 (user_id, space_id, file_id) 三元组校验资源归属。
     * 返回 false 而不泄漏具体资源存在性，适用于内部服务和分享授权解析。
     */
    boolean validateFileInSpace(UUID userId, UUID spaceId, UUID fileId);

    void requireNodeInCurrentSpace(UUID nodeId);

    void requireUploadSessionInCurrentSpace(UUID uploadsId);
}
