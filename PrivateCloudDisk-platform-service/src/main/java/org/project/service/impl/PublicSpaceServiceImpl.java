package org.project.service.impl;

import org.project.mapper.FileMapper;
import org.project.mapper.FolderNodeMapper;
import org.project.mapper.SpaceMapper;
import org.project.mapper.UserMapper;
import org.project.model.entity.FileEntity;
import org.project.model.entity.FolderNodeEntity;
import org.project.model.entity.SpaceEntity;
import org.project.model.entity.UserEntity;
import org.project.model.vo.PublicSpaceDetailVO;
import org.project.model.vo.PublicSpaceNodeVO;
import org.project.model.vo.PublicUserProfileVO;
import org.project.service.PublicSpaceService;
import org.project.service.SpaceService;
import org.project.service.ex.InsertException;
import org.project.service.ex.OverstepAuthorityException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 公开仓库实现：所有入口先做登录、active、public、可见开关校验，再查询资源。 */
@Service
public class PublicSpaceServiceImpl implements PublicSpaceService {
    private final SpaceMapper spaceMapper;
    private final FolderNodeMapper folderNodeMapper;
    private final FileMapper fileMapper;
    private final UserMapper userMapper;
    private final SpaceService spaceService;

    public PublicSpaceServiceImpl(SpaceMapper spaceMapper, FolderNodeMapper folderNodeMapper,
                                  FileMapper fileMapper, UserMapper userMapper, SpaceService spaceService) {
        this.spaceMapper = spaceMapper;
        this.folderNodeMapper = folderNodeMapper;
        this.fileMapper = fileMapper;
        this.userMapper = userMapper;
        this.spaceService = spaceService;
    }

    @Override
    public PublicSpaceDetailVO getRepository(UUID spaceId, UUID visitorId) {
        // 仓库所有者即使关闭公开浏览，也必须能够进入设置页恢复权限；其他登录用户仍严格受 browse 开关约束。
        SpaceEntity space = requirePublicSpace(spaceId);
        if (!space.getSpaceOwnerId().equals(visitorId)) {
            requireBrowsable(space);
        }
        return toDetail(space, visitorId);
    }

    @Override
    public PublicSpaceNodeVO getRoot(UUID spaceId, UUID visitorId) {
        requireFileResource(spaceId, visitorId);
        FolderNodeEntity root = folderNodeMapper.findRootFolderNodeBySpaceId(spaceId);
        if (root == null) throw new InsertException("公开仓库根目录不存在");
        return toNode(root);
    }

    @Override
    public List<PublicSpaceNodeVO> getChildren(UUID spaceId, UUID nodeId, UUID visitorId) {
        requireFileResource(spaceId, visitorId);
        FolderNodeEntity node = folderNodeMapper.findFolderNodeByIdAndSpaceId(nodeId, spaceId);
        if (node == null || node.getStatus() != FolderNodeEntity.NodeStatus.active) {
            throw new InsertException("目录不存在");
        }
        List<PublicSpaceNodeVO> result = new ArrayList<>();
        for (FolderNodeEntity folder : folderNodeMapper.findFolderNodesBySpaceId(nodeId, spaceId)) result.add(toNode(folder));
        for (FileEntity file : fileMapper.findActiveFilesByNodeIdAndSpaceId(nodeId, spaceId)) result.add(toNode(file));
        return result;
    }

    @Override
    public String getReadme(UUID spaceId, UUID visitorId) {
        requireFileResource(spaceId, visitorId);
        FolderNodeEntity root = folderNodeMapper.findRootFolderNodeBySpaceId(spaceId);
        if (root == null) return null;
        FileEntity readme = fileMapper.findActiveFileByNodeIdAndNameAndSpaceId(root.getNode_id(), "README.md", spaceId);
        // README 是可选资源；缺失时由前端渲染友好空状态。
        // 只返回文件 ID；物理 storage_path 属于内部实现，不能通过公开仓库接口泄漏。
        return readme == null ? null : readme.getId().toString();
    }

    @Override
    public void requireFileResource(UUID spaceId, UUID visitorId) {
        SpaceEntity space = requireBrowsable(spaceId);
        String resourceType = space.getResourceType() == null ? "file" : space.getResourceType();
        if (!"file".equals(resourceType)) {
            throw new InsertException("该公开空间由 " + resourceType + " 资源服务提供，不支持文件目录接口");
        }
    }

    @Override
    @Transactional
    public PublicSpaceDetailVO updateRepository(UUID spaceId, UUID ownerId, String name, String description,
                                                 Boolean allowBrowse, Boolean allowDownload, Boolean allowUpload) {
        SpaceEntity updated = spaceService.updatePublicRepository(spaceId, ownerId, name, description,
                allowBrowse, allowDownload, allowUpload);
        return toDetail(updated, ownerId);
    }

    @Override
    public PublicUserProfileVO getUserProfile(String username, UUID visitorId) {
        UserEntity user = userMapper.findUserByNameOrAccount(username);
        if (user == null) throw new InsertException("用户不存在");
        PublicUserProfileVO profile = new PublicUserProfileVO();
        profile.setUserId(user.getId().toString());
        profile.setUsername(user.getAccount() == null ? user.getName() : user.getAccount());
        profile.setDisplayName(user.getName());
        profile.setAvatar(user.getImage_path());
        profile.setRepositories(spaceMapper.findPublicSpacesByOwnerId(user.getId()).stream()
                .map(space -> toDetail(space, visitorId)).toList());
        return profile;
    }

    @Override
    public List<PublicSpaceDetailVO> explore(String keyword, UUID visitorId) {
        return spaceMapper.findPublicSpaces(keyword).stream().map(space -> toDetail(space, visitorId)).toList();
    }

    private SpaceEntity requireBrowsable(UUID spaceId) {
        return requireBrowsable(requirePublicSpace(spaceId));
    }

    private SpaceEntity requireBrowsable(SpaceEntity space) {
        if (!Boolean.TRUE.equals(space.getAllowPublicBrowse())) throw new OverstepAuthorityException("该仓库暂不允许公开浏览");
        return space;
    }

    private SpaceEntity requirePublicSpace(UUID spaceId) {
        SpaceEntity space = spaceMapper.findById(spaceId);
        if (space == null || !"active".equals(space.getSpaceStatus()) || !"public".equals(space.getSpaceType())
                || !"public".equals(space.getSpaceVisibility())) {
            throw new InsertException("公开仓库不存在");
        }
        return space;
    }

    private PublicSpaceDetailVO toDetail(SpaceEntity space, UUID visitorId) {
        UserEntity owner = userMapper.findUserById(space.getSpaceOwnerId());
        PublicSpaceDetailVO vo = new PublicSpaceDetailVO();
        vo.setSpaceId(space.getSpaceId().toString());
        vo.setSpaceName(space.getSpaceName());
        vo.setResourceType(space.getResourceType() == null ? "file" : space.getResourceType());
        vo.setDescription(space.getSpaceDescription());
        vo.setOwnerId(space.getSpaceOwnerId().toString());
        vo.setOwnerName(owner == null ? "未知用户" : owner.getName());
        vo.setOwnerAvatar(owner == null ? null : owner.getImage_path());
        vo.setAllowPublicBrowse(Boolean.TRUE.equals(space.getAllowPublicBrowse()));
        vo.setAllowPublicDownload(Boolean.TRUE.equals(space.getAllowPublicDownload()));
        vo.setAllowPublicUpload(Boolean.TRUE.equals(space.getAllowPublicUpload()));
        vo.setFileCount(space.getSpaceFileCount());
        vo.setUsedBytes(space.getSpaceUsed());
        vo.setCreatedAt(space.getSpaceCreatedAt());
        vo.setUpdatedAt(space.getSpaceUpdatedAt());
        return vo;
    }

    private PublicSpaceNodeVO toNode(FolderNodeEntity node) {
        PublicSpaceNodeVO vo = new PublicSpaceNodeVO();
        vo.setId(node.getNode_id().toString());
        vo.setName(node.getName());
        vo.setType("folder");
        vo.setUpdatedAt(null);
        return vo;
    }

    private PublicSpaceNodeVO toNode(FileEntity file) {
        PublicSpaceNodeVO vo = new PublicSpaceNodeVO();
        vo.setId(file.getId().toString());
        vo.setName(file.getName());
        vo.setType("file");
        vo.setSize(file.getSize());
        vo.setFileType(file.getType());
        vo.setUpdatedAt(file.getUploaded_time());
        return vo;
    }
}
