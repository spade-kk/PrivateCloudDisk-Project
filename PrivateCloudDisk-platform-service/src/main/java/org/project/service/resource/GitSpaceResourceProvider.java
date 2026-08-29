package org.project.service.resource;

import org.project.model.entity.SpaceEntity;
import org.project.service.ex.InsertException;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * [REQ-GIT-SPACE-2.1/3.1] Git 资源占位 Provider。
 *
 * <p>Git 仓库实体由独立 git-service 通过幂等 API 创建；这里不在 Platform 数据库事务中
 * 发起远程调用，避免远端超时造成长事务和半提交。Provider 只固化资源边界并禁止
 * 非公开空间误挂载 Git，实际仓库可由创建页面或资源补偿任务完成 provisioning。</p>
 */
@Component
public class GitSpaceResourceProvider implements SpaceResourceProvider {
    @Override
    public String resourceType() {
        return "git";
    }

    @Override
    public void initialize(SpaceEntity space, UUID ownerId) {
        if (!"public".equals(space.getSpaceType())) {
            throw new InsertException("Git 仓库当前只能挂载到公开空间");
        }
    }
}
