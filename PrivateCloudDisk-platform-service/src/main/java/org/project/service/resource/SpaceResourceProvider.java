package org.project.service.resource;

import org.project.model.entity.SpaceEntity;

import java.util.UUID;

/**
 * [REQ-GIT-SPACE-2.3/14.1] 空间资源实现的稳定扩展边界。
 *
 * <p>原行为：SpaceService 直接创建文件目录，导致公开空间无法挂载其他资源。
 * 新行为：Space 只管理身份、租户和权限，具体资源初始化交给按 resource_type 注册的 Provider。
 * 未来 dataset/docker/model 只需新增 Provider，不需要修改空间核心状态机。</p>
 */
public interface SpaceResourceProvider {
    String resourceType();

    void initialize(SpaceEntity space, UUID ownerId);

    default boolean supportsFileTree() {
        return false;
    }
}
