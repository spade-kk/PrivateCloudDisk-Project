package org.project.service.resource;

import lombok.RequiredArgsConstructor;
import org.project.context.SpaceContextHolder;
import org.project.mapper.DirectoryClosureMapper;
import org.project.mapper.FolderNodeMapper;
import org.project.model.entity.FolderNodeEntity;
import org.project.model.entity.SpaceEntity;
import org.project.service.ex.InsertException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/** [REQ-GIT-SPACE-2.3] 既有文件空间实现；保留原根目录和闭包表初始化行为。 */
@Component
@RequiredArgsConstructor
public class FileSpaceResourceProvider implements SpaceResourceProvider {
    private final FolderNodeMapper folderNodeMapper;
    private final DirectoryClosureMapper directoryClosureMapper;

    @Override
    public String resourceType() {
        return "file";
    }

    @Override
    public boolean supportsFileTree() {
        return true;
    }

    @Override
    public void initialize(SpaceEntity space, UUID ownerId) {
        FolderNodeEntity rootNode = new FolderNodeEntity();
        rootNode.setNode_id(UUID.randomUUID());
        rootNode.setUser_id(ownerId);
        rootNode.setParent_id(null);
        rootNode.setName(space.getSpaceName());
        rootNode.setCreate_time(LocalDateTime.now().toString());
        rootNode.setStatus(FolderNodeEntity.NodeStatus.active);
        rootNode.setSpace_id(space.getSpaceId());
        if (folderNodeMapper.insertFolderNode(rootNode) != 1) {
            throw new InsertException("空间根目录创建失败");
        }

        SpaceContextHolder.SpaceContext previousContext = SpaceContextHolder.get();
        SpaceContextHolder.set(new SpaceContextHolder.SpaceContext(
                space.getSpaceId(), ownerId, space.getSpaceName(), "owner", true,
                "personal".equals(space.getSpaceType())));
        try {
            directoryClosureMapper.insertSelf(rootNode.getNode_id(), ownerId);
        } finally {
            if (previousContext == null) SpaceContextHolder.clear();
            else SpaceContextHolder.set(previousContext);
        }
    }
}
