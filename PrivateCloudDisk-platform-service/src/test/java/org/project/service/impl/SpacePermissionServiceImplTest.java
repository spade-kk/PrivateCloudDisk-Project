package org.project.service.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.project.context.SpaceContextHolder;
import org.project.mapper.SpaceMapper;
import org.project.mapper.SpaceMemberMapper;
import org.project.mapper.SpacePermissionMapper;
import org.project.mapper.SpaceResourceScopeMapper;
import org.project.model.entity.SpaceEntity;
import org.project.model.entity.SpaceMemberEntity;
import org.project.service.SpaceOperation;
import org.project.service.ex.OverstepAuthorityException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 空间管理能力全量集成（需求七）：公共权限矩阵与默认空间降维单元测试。
 */
class SpacePermissionServiceImplTest {

    private final UUID userId = UUID.randomUUID();
    private final UUID spaceId = UUID.randomUUID();
    private SpaceMapper spaceMapper;
    private SpaceMemberMapper memberMapper;
    private SpacePermissionMapper permissionMapper;
    private SpaceResourceScopeMapper resourceMapper;
    private SpacePermissionServiceImpl service;

    @BeforeEach
    void setUp() {
        spaceMapper = mock(SpaceMapper.class);
        memberMapper = mock(SpaceMemberMapper.class);
        permissionMapper = mock(SpacePermissionMapper.class);
        resourceMapper = mock(SpaceResourceScopeMapper.class);
        service = new SpacePermissionServiceImpl(
                spaceMapper, memberMapper, permissionMapper, resourceMapper);
    }

    @AfterEach
    void clearContext() {
        SpaceContextHolder.clear();
    }

    @Test
    void missingHeaderFallsBackToPersonalSpace() {
        SpaceEntity personal = activeSpace("personal");
        SpaceMemberEntity owner = member("owner");
        when(spaceMapper.findPersonalByOwnerId(userId)).thenReturn(personal);
        when(memberMapper.findBySpaceAndUser(spaceId, userId)).thenReturn(owner);

        SpaceContextHolder.SpaceContext context = service.resolveContext(userId, null);

        assertEquals(spaceId, context.spaceId());
        assertTrue(context.personalSpace());
        assertFalse(context.explicitSpaceId());
        verify(spaceMapper).findPersonalByOwnerId(userId);
    }

    @Test
    void explicitSpaceRejectsNonMember() {
        when(spaceMapper.findById(spaceId)).thenReturn(activeSpace("team"));
        when(memberMapper.findBySpaceAndUser(spaceId, userId)).thenReturn(null);

        assertThrows(
                OverstepAuthorityException.class,
                () -> service.resolveContext(userId, spaceId.toString()));
    }

    @Test
    void viewerCanReadButCannotEdit() {
        SpaceContextHolder.SpaceContext context = new SpaceContextHolder.SpaceContext(
                spaceId, userId, "只读协作空间", "viewer", true, false);

        assertDoesNotThrow(() -> service.requireOperation(context, SpaceOperation.READ));
        assertThrows(
                OverstepAuthorityException.class,
                () -> service.requireOperation(context, SpaceOperation.EDIT));
    }

    @Test
    void roleMatrixCoversAllResourceOperations() {
        SpaceContextHolder.SpaceContext viewer = new SpaceContextHolder.SpaceContext(
                spaceId, userId, "只读协作空间", "viewer", true, false);
        for (SpaceOperation operation : new SpaceOperation[]{
                SpaceOperation.VIEW, SpaceOperation.READ, SpaceOperation.DOWNLOAD}) {
            assertDoesNotThrow(() -> service.requireOperation(viewer, operation));
        }
        for (SpaceOperation operation : new SpaceOperation[]{
                SpaceOperation.UPLOAD, SpaceOperation.EDIT, SpaceOperation.DELETE,
                SpaceOperation.SHARE, SpaceOperation.MANAGE}) {
            assertThrows(
                    OverstepAuthorityException.class,
                    () -> service.requireOperation(viewer, operation));
        }

        SpaceContextHolder.SpaceContext editor = new SpaceContextHolder.SpaceContext(
                spaceId, userId, "编辑协作空间", "editor", true, false);
        // [SPACE-COLLAB-TEST-01] 新增管理维度不能沿用旧“editor 全部通过”断言；
        // 编辑者保留文件编辑能力，但成员/插件/设置管理必须显式授权。
        for (SpaceOperation operation : new SpaceOperation[]{
                SpaceOperation.VIEW, SpaceOperation.READ, SpaceOperation.DOWNLOAD,
                SpaceOperation.UPLOAD, SpaceOperation.EDIT, SpaceOperation.DELETE,
                SpaceOperation.SHARE, SpaceOperation.MANAGE}) {
            if (operation == SpaceOperation.MANAGE) {
                assertThrows(
                        OverstepAuthorityException.class,
                        () -> service.requireOperation(editor, operation));
            } else {
                assertDoesNotThrow(() -> service.requireOperation(editor, operation));
            }
        }
        for (SpaceOperation operation : new SpaceOperation[]{
                SpaceOperation.MANAGE_MEMBERS, SpaceOperation.MANAGE_PLUGINS, SpaceOperation.MANAGE_SETTINGS}) {
            assertThrows(OverstepAuthorityException.class, () -> service.requireOperation(editor, operation));
        }

        SpaceContextHolder.SpaceContext owner = new SpaceContextHolder.SpaceContext(
                spaceId, userId, "所有者空间", "owner", true, false);
        for (SpaceOperation operation : SpaceOperation.values()) {
            assertDoesNotThrow(() -> service.requireOperation(owner, operation));
        }
    }

    @Test
    void resourceOutsideCurrentSpaceIsReportedAsMissing() {
        UUID fileId = UUID.randomUUID();
        SpaceContextHolder.set(new SpaceContextHolder.SpaceContext(
                spaceId, userId, "协作空间", "editor", true, false));
        when(resourceMapper.countFileInSpace(fileId, spaceId, userId, false)).thenReturn(0);

        assertThrows(
                org.project.service.ex.FileNotExistException.class,
                () -> service.requireFileInCurrentSpace(fileId));
    }

    @Test
    void resourceScopeIncludesCurrentUserForLegacyPersonalRows() {
        UUID fileId = UUID.randomUUID();
        SpaceContextHolder.SpaceContext context = new SpaceContextHolder.SpaceContext(
                spaceId, userId, "我的网盘", "owner", false, true);
        SpaceContextHolder.set(context);
        when(resourceMapper.countFileInSpace(fileId, spaceId, userId, true)).thenReturn(1);

        assertDoesNotThrow(() -> service.requireFileInCurrentSpace(fileId));
        verify(resourceMapper).countFileInSpace(fileId, spaceId, userId, true);
    }

    @Test
    void publicRepositoryVisitorUsesRepositorySwitchesInsteadOfMembership() {
        SpaceEntity repository = activeSpace("public");
        repository.setSpaceVisibility("public");
        repository.setAllowPublicBrowse(true);
        repository.setAllowPublicDownload(false);
        repository.setAllowPublicUpload(false);
        when(spaceMapper.findById(spaceId)).thenReturn(repository);
        when(memberMapper.findBySpaceAndUser(spaceId, userId)).thenReturn(null);

        SpaceContextHolder.SpaceContext context = service.resolveContext(userId, spaceId.toString());
        assertEquals("public_viewer", context.role());
        assertDoesNotThrow(() -> service.requireOperation(context, SpaceOperation.READ));
        assertThrows(OverstepAuthorityException.class,
                () -> service.requireOperation(context, SpaceOperation.DOWNLOAD));
        assertThrows(OverstepAuthorityException.class,
                () -> service.requireOperation(context, SpaceOperation.UPLOAD));
    }

    @Test
    void publicRepositoryUploaderCannotManageOrDelete() {
        SpaceEntity repository = activeSpace("public");
        repository.setSpaceVisibility("public");
        repository.setAllowPublicBrowse(true);
        repository.setAllowPublicDownload(true);
        repository.setAllowPublicUpload(true);
        when(spaceMapper.findById(spaceId)).thenReturn(repository);
        when(memberMapper.findBySpaceAndUser(spaceId, userId)).thenReturn(null);

        SpaceContextHolder.SpaceContext context = service.resolveContext(userId, spaceId.toString());
        assertEquals("public_uploader", context.role());
        assertDoesNotThrow(() -> service.requireOperation(context, SpaceOperation.UPLOAD));
        assertThrows(OverstepAuthorityException.class,
                () -> service.requireOperation(context, SpaceOperation.DELETE));
    }

    private SpaceEntity activeSpace(String type) {
        SpaceEntity space = new SpaceEntity();
        space.setSpaceId(spaceId);
        space.setSpaceName("测试空间");
        space.setSpaceType(type);
        space.setSpaceStatus("active");
        return space;
    }

    private SpaceMemberEntity member(String role) {
        SpaceMemberEntity member = new SpaceMemberEntity();
        member.setSpaceId(spaceId);
        member.setUserId(userId);
        member.setRole(role);
        return member;
    }
}
