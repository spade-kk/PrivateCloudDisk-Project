package org.project.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.mapper.FileMapper;
import org.project.mapper.FolderNodeMapper;
import org.project.mapper.ShareLinkMapper;
import org.project.model.dto.ShareCreateRequest;
import org.project.model.entity.FileEntity;
import org.project.model.entity.FolderNodeEntity;
import org.project.model.entity.ShareLinkEntity;
import org.project.model.entity.ShareLinkEntity.ShareStatus;
import org.project.model.entity.ShareLinkEntity.TargetType;
import org.project.model.vo.ShareAccessInfoVO;
import org.project.model.vo.ShareContentItemVO;
import org.project.model.vo.ShareLinkVO;
import org.project.service.DirectoryTreeService;
import org.project.service.ShareService;
import org.project.service.ex.*;
import org.project.util.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 分享链接服务实现
 * <p>
 * 安全设计：
 * 1. share_token 是公开访问的唯一凭证，绝不暴露内部 file_id/node_id
 * 2. 密码 BCrypt 哈希存储
 * 3. 密码验证通过后签发短期 JWT（15 分钟），后续请求凭 JWT 访问
 * 4. 分享访问永为只读，无 CRUD 权限
 * 5. 所有文件/文件夹访问必须通过 share_token 校验，杜绝横向越权
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShareServiceImpl implements ShareService {

    private final ShareLinkMapper shareLinkMapper;
    private final FileMapper fileMapper;
    private final FolderNodeMapper folderNodeMapper;
    private final DirectoryTreeService directoryTreeService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Override
    @Transactional
    public ShareLinkVO createShare(String user_id, ShareCreateRequest request) {
        UUID ownerId = UUID.fromString(user_id);

        ShareLinkEntity share = new ShareLinkEntity();
        share.setShare_id(UUID.randomUUID());
        share.setShare_token(UUID.randomUUID().toString());
        share.setShare_owner_id(ownerId);
        share.setShare_name(request.getShare_name());

        if ("file".equals(request.getTarget_type())) {
            if (request.getFile_id() == null || request.getFile_id().isBlank()) {
                throw new ServiceException("分享文件时 file_id 不能为空");
            }
            UUID fileId = UUID.fromString(request.getFile_id());
            FileEntity file = fileMapper.findUserFileById(fileId, ownerId);
            if (file == null) {
                throw new FileNotExistException("文件不存在或无权访问");
            }
            share.setShare_target_type(TargetType.file);
            share.setShare_file_id(fileId);
            share.setShare_node_id(null);
        } else if ("folder".equals(request.getTarget_type())) {
            if (request.getNode_id() == null || request.getNode_id().isBlank()) {
                throw new ServiceException("分享文件夹时 node_id 不能为空");
            }
            UUID nodeId = UUID.fromString(request.getNode_id());
            FolderNodeEntity folder = folderNodeMapper.findFolderNodeByIdAndUserId(nodeId, ownerId);
            if (folder == null) {
                throw new NodeNotExistException("文件夹不存在或无权访问");
            }
            share.setShare_target_type(TargetType.folder);
            share.setShare_file_id(null);
            share.setShare_node_id(nodeId);
        } else {
            throw new ServiceException("无效的分享目标类型");
        }

        // 密码处理
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            share.setShare_password(passwordEncoder.encode(request.getPassword()));
            share.setShare_has_password(true);
        } else {
            share.setShare_password(null);
            share.setShare_has_password(false);
        }

        // 过期时间
        if (request.getExpires_in_days() != null && request.getExpires_in_days() > 0) {
            share.setShare_expires_at(LocalDateTime.now().plusDays(request.getExpires_in_days()));
        } else {
            share.setShare_expires_at(null);
        }

        int rows = shareLinkMapper.insertShare(share);
        if (rows != 1) {
            throw new InsertException("创建分享链接失败");
        }

        log.info("分享链接创建成功: userId={}, token={}, type={}", user_id, share.getShare_token(), request.getTarget_type());
        return toShareLinkVO(share);
    }

    @Override
    public List<ShareLinkVO> getMyShares(String user_id) {
        UUID ownerId = UUID.fromString(user_id);
        List<ShareLinkEntity> shares = shareLinkMapper.findByOwnerId(ownerId);
        return shares.stream().map(this::toShareLinkVO).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void revokeShare(String user_id, String share_id) {
        int rows = shareLinkMapper.revokeShare(UUID.fromString(share_id), UUID.fromString(user_id));
        if (rows != 1) {
            throw new ServiceException("分享不存在或无权撤销");
        }
        log.info("分享已撤销: shareId={}, userId={}", share_id, user_id);
    }

    @Override
    public ShareAccessInfoVO getShareAccessInfo(String share_token) {
        ShareLinkEntity share = shareLinkMapper.findByToken(share_token);
        if (share == null) {
            throw new ServiceException("分享链接不存在");
        }

        if (share.getShare_status() == ShareStatus.revoked) {
            ShareAccessInfoVO vo = buildAccessInfoVO(share);
            vo.setIs_revoked(true);
            return vo;
        }

        if (share.getShare_expires_at() != null && share.getShare_expires_at().isBefore(LocalDateTime.now())) {
            shareLinkMapper.expireOutdatedShares();
            ShareAccessInfoVO vo = buildAccessInfoVO(share);
            vo.setIs_expired(true);
            return vo;
        }

        if (share.getShare_status() == ShareStatus.expired) {
            ShareAccessInfoVO vo = buildAccessInfoVO(share);
            vo.setIs_expired(true);
            return vo;
        }

        return buildAccessInfoVO(share);
    }

    @Override
    public String verifyPasswordAndGetToken(String share_token, String password) {
        ShareLinkEntity share = shareLinkMapper.findByToken(share_token);
        if (share == null) {
            throw new ServiceException("分享链接不存在");
        }

        if (share.getShare_status() != ShareStatus.active) {
            throw new ServiceException("分享链接已失效");
        }

        if (share.getShare_expires_at() != null && share.getShare_expires_at().isBefore(LocalDateTime.now())) {
            shareLinkMapper.expireOutdatedShares();
            throw new ServiceException("分享链接已过期");
        }

        // 验证密码
        if (share.getShare_has_password() && share.getShare_password() != null) {
            if (password == null || !passwordEncoder.matches(password, share.getShare_password())) {
                throw new PasswordNotMatchException("提取码错误");
            }
        }

        // 增加浏览次数
        shareLinkMapper.incrementViewCount(share.getShare_id());

        // 生成短期访问令牌
        return jwtUtil.generateShareAccessToken(share.getShare_token());
    }

    @Override
    public ShareLinkEntity getShareByAccessToken(String access_token) {
        String shareToken = jwtUtil.verifyShareAccessToken(access_token);
        if (shareToken == null) {
            throw new ServiceException("访问令牌无效或已过期，请重新验证");
        }

        ShareLinkEntity share = shareLinkMapper.findByToken(shareToken);
        if (share == null) {
            throw new ServiceException("分享链接不存在");
        }

        if (share.getShare_status() != ShareStatus.active) {
            throw new ServiceException("分享链接已失效");
        }

        if (share.getShare_expires_at() != null && share.getShare_expires_at().isBefore(LocalDateTime.now())) {
            shareLinkMapper.expireOutdatedShares();
            throw new ServiceException("分享链接已过期");
        }

        return share;
    }

    @Override
    public FileEntity getSharedFile(String share_token, String file_id) {
        ShareLinkEntity share = shareLinkMapper.findByToken(share_token);
        if (share == null || share.getShare_status() != ShareStatus.active) {
            throw new ServiceException("分享链接无效");
        }

        if (share.getShare_target_type() != TargetType.file) {
            throw new ServiceException("该分享不是文件类型");
        }

        UUID fid = UUID.fromString(file_id);
        if (!fid.equals(share.getShare_file_id())) {
            throw new OverstepAuthorityException("无权访问该文件");
        }

        return fileMapper.findUserFileById(fid, share.getShare_owner_id());
    }

    @Override
    public List<ShareContentItemVO> getSharedFolderContents(String share_token, String node_id) {
        ShareLinkEntity share = shareLinkMapper.findByToken(share_token);
        if (share == null || share.getShare_status() != ShareStatus.active) {
            throw new ServiceException("分享链接无效");
        }

        if (share.getShare_target_type() != TargetType.folder) {
            throw new ServiceException("该分享不是文件夹类型");
        }

        // 确定要浏览的节点ID：没传或传了根节点ID则浏览分享根目录
        UUID browseNodeId;
        if (node_id == null || node_id.isBlank() || node_id.equals(share.getShare_node_id().toString())) {
            browseNodeId = share.getShare_node_id();
        } else {
            browseNodeId = UUID.fromString(node_id);
        }

        UUID ownerId = share.getShare_owner_id();
        List<ShareContentItemVO> items = new ArrayList<>();

        // 查询子文件夹
        List<FolderNodeEntity> subFolders = folderNodeMapper.findFolderNodesByIdAndUserId(browseNodeId, ownerId);
        if (subFolders != null) {
            for (FolderNodeEntity folder : subFolders) {
                items.add(ShareContentItemVO.builder()
                        .item_type("folder")
                        .node_id(folder.getNode_id().toString())
                        .name(folder.getName())
                        .size(0L)
                        .build());
            }
        }

        // 查询子文件
        List<FileEntity> subFiles = fileMapper.findUserActiveFilesByNodeId(browseNodeId, ownerId);
        if (subFiles != null) {
            for (FileEntity file : subFiles) {
                items.add(ShareContentItemVO.builder()
                        .item_type("file")
                        .file_id(file.getId().toString())
                        .name(file.getName())
                        .size(file.getSize())
                        .file_type(file.getType())
                        .build());
            }
        }

        return items;
    }

    // ==================== 私有方法 ====================

    private ShareLinkVO toShareLinkVO(ShareLinkEntity entity) {
        ShareLinkVO vo = new ShareLinkVO();
        vo.setShare_id(entity.getShare_id().toString());
        vo.setShare_token(entity.getShare_token());
        vo.setShare_url("/share/" + entity.getShare_token());
        vo.setShare_target_type(entity.getShare_target_type().name());
        vo.setShare_name(entity.getShare_name());
        vo.setTarget_name(entity.getTarget_name());
        vo.setTarget_size(entity.getTarget_size());
        vo.setFile_type(entity.getFile_type());
        vo.setShare_has_password(entity.getShare_has_password());
        vo.setShare_expires_at(entity.getShare_expires_at());
        vo.setShare_view_count(entity.getShare_view_count());
        vo.setShare_status(entity.getShare_status().name());
        vo.setShare_created_at(entity.getShare_created_at());
        return vo;
    }

    private ShareAccessInfoVO buildAccessInfoVO(ShareLinkEntity share) {
        ShareAccessInfoVO vo = new ShareAccessInfoVO();
        vo.setShare_token(share.getShare_token());
        vo.setShare_name(share.getShare_name());
        vo.setShare_target_type(share.getShare_target_type().name());
        vo.setTarget_name(share.getTarget_name());
        vo.setTarget_size(share.getTarget_size());
        vo.setFile_type(share.getFile_type());
        vo.setOwner_name(share.getOwner_name());
        vo.setHas_password(share.getShare_has_password());
        vo.setIs_expired(false);
        vo.setIs_revoked(false);
        vo.setExpires_at(share.getShare_expires_at());
        vo.setCreated_at(share.getShare_created_at());
        return vo;
    }
}