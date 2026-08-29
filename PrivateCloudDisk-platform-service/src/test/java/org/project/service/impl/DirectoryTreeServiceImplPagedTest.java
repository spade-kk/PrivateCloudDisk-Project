package org.project.service.impl;

import org.junit.jupiter.api.Test;
import org.project.mapper.DirectoryClosureMapper;
import org.project.mapper.FileMapper;
import org.project.mapper.FolderNodeMapper;
import org.project.model.entity.FileEntity;
import org.project.model.entity.FolderNodeEntity;
import org.project.model.vo.PageResultVO;
import org.project.model.entity.NodeEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DirectoryTreeServiceImplPagedTest {
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID PARENT = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void pagedChildrenReturnsNonNullFilteredPageForCapabilityDataPlane() {
        FolderNodeMapper folders = mock(FolderNodeMapper.class);
        FileMapper files = mock(FileMapper.class);
        DirectoryTreeServiceImpl service = new DirectoryTreeServiceImpl();
        ReflectionTestUtils.setField(service, "folderNodeMapper", folders);
        ReflectionTestUtils.setField(service, "fileMapper", files);
        ReflectionTestUtils.setField(service, "directoryClosureMapper", mock(DirectoryClosureMapper.class));

        FolderNodeEntity parent = new FolderNodeEntity();
        parent.setNode_id(PARENT);
        parent.setStatus(FolderNodeEntity.NodeStatus.active);
        when(folders.findFolderNodeByIdAndUserId(PARENT, USER)).thenReturn(parent);
        when(folders.isFolderDeleted(PARENT, USER)).thenReturn(false);

        FolderNodeEntity folder = new FolderNodeEntity();
        folder.setNode_id(UUID.fromString("00000000-0000-0000-0000-000000000003"));
        folder.setName("reports");
        folder.setStatus(FolderNodeEntity.NodeStatus.active);
        when(folders.findFolderNodesByIdAndUserId(PARENT, USER)).thenReturn(List.of(folder));

        FileEntity file = new FileEntity();
        file.setId(UUID.fromString("00000000-0000-0000-0000-000000000004"));
        file.setName("weekly.md");
        file.setSize(42L);
        when(files.findUserActiveFilesByNodeId(PARENT, USER)).thenReturn(List.of(file));

        PageResultVO<NodeEntity> result = service.findUserNodesByNodeIdPaged(
                PARENT.toString(), "weekly", "file", "name", "asc", 1, 20, USER);

        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getItems().size());
        assertEquals("weekly.md", result.getItems().get(0).getNode_name());
        assertEquals(1, result.getPage());
    }
}
