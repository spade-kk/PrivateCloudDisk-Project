package org.project.service;

import org.project.context.SpaceContextHolder;

import java.util.UUID;
import java.util.Set;

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

    /**
     * 返回自动化能力可使用的当前权限快照。
     * <p>空间角色与权限列仍由本服务唯一解释；Workflow/Plugin 服务只能消费结果，不能自行推导
     * owner/editor 等角色规则，避免把工作流声明权限误当成实际授予权限。</p>
     */
    Set<String> resolveAutomationPermissions(SpaceContextHolder.SpaceContext context);

    void requireFileInCurrentSpace(UUID fileId);

    /**
     * 需求 3.1：以 (user_id, space_id, file_id) 三元组校验资源归属。
     * 返回 false 而不泄漏具体资源存在性，适用于内部服务和分享授权解析。
     */
    boolean validateFileInSpace(UUID userId, UUID spaceId, UUID fileId);

    void requireNodeInCurrentSpace(UUID nodeId);

    void requireUploadSessionInCurrentSpace(UUID uploadsId);
}
