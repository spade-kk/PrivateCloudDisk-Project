package org.project.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.mapper.FileMapper;
import org.project.mapper.FolderNodeMapper;
import org.project.mapper.ShareLinkMapper;
import org.project.model.entity.FileEntity;
import org.project.model.entity.FolderNodeEntity;
import org.project.model.entity.ShareLinkEntity;
import org.project.model.entity.ShareLinkEntity.ShareStatus;
import org.project.model.entity.ShareLinkEntity.TargetType;
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

/**
 * 分享链接服务实现
 * <p>
 * 安全设计：
 * 1. share_token 是公开访问的唯一凭证，绝不暴露内部 file_id/node_id
 * 2. 密码 BCrypt 哈希存储
 * 3. 密码验证通过后签发短期 JWT（15 分钟），后续请求凭 JWT 访问
 * 4. 分享访问永为只读，无 CRUD 权限
 * 5. 所有文件/文件夹访问必须通过 share_token 校验，杜绝横向越权
 *
 * <p><b>分层原则</b>：本服务不接收任何 Request DTO，不返回任何 VO。
 * 所有 Entity → VO 的转换由接口层通过 VoMapper 完成。
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
    public ShareLinkEntity createShare(String userId, String shareName, String targetType,
                                        String fileId, String nodeId, String password, Integer expireDays) {
        UUID ownerId = UUID.fromString(userId);

        ShareLinkEntity share = new ShareLinkEntity();
        share.setShare_id(UUID.randomUUID());
        share.setShare_token(UUID.randomUUID().toString());
        share.setShare_owner_id(ownerId);
        share.setShare_name(shareName);

        if ("file".equals(targetType)) {
            if (fileId == null || fileId.isBlank()) {
                throw new ServiceException("分享文件时 file_id 不能为空");
            }
            UUID fid = UUID.fromString(fileId);
            FileEntity file = fileMapper.findUserFileById(fid, ownerId);
            if (file == null) {
                throw new FileNotExistException("文件不存在或无权访问");
            }
            share.setShare_target_type(TargetType.file);
            share.setShare_file_id(fid);
            share.setShare_node_id(null);
            // 填充目标信息
            share.setTarget_name(file.getName());
            share.setTarget_size(file.getSize());
            share.setFile_type(file.getType());
        } else if ("folder".equals(targetType)) {
            if (nodeId == null || nodeId.isBlank()) {
                throw new ServiceException("分享文件夹时 node_id 不能为空");
            }
            UUID nid = UUID.fromString(nodeId);
            FolderNodeEntity folder = folderNodeMapper.findFolderNodeByIdAndUserId(nid, ownerId);
            if (folder == null) {
                throw new NodeNotExistException("文件夹不存在或无权访问");
            }
            share.setShare_target_type(TargetType.folder);
            share.setShare_file_id(null);
            share.setShare_node_id(nid);
            share.setTarget_name(folder.getName());
            share.setTarget_size(0L);
        } else {
            throw new ServiceException("无效的分享目标类型");
        }

        // 密码处理
        if (password != null && !password.isBlank()) {
            share.setShare_password(passwordEncoder.encode(password));
            share.setShare_has_password(true);
        } else {
            share.setShare_password(null);
            share.setShare_has_password(false);
        }

        // 过期时间
        if (expireDays != null && expireDays > 0) {
            share.setShare_expires_at(LocalDateTime.now().plusDays(expireDays));
        } else {
            share.setShare_expires_at(null);
        }

        int rows = shareLinkMapper.insertShare(share);
        if (rows != 1) {
            throw new InsertException("创建分享链接失败");
        }

        log.info("分享链接创建成功: userId={}, token={}, type={}", userId, share.getShare_token(), targetType);
        return share;
    }

    @Override
    public List<ShareLinkEntity> getMyShares(String userId) {
        UUID ownerId = UUID.fromString(userId);
        return shareLinkMapper.findByOwnerId(ownerId);
    }

    @Override
    @Transactional
    public void revokeShare(String userId, String shareId) {
        int rows = shareLinkMapper.revokeShare(UUID.fromString(shareId), UUID.fromString(userId));
        if (rows != 1) {
            throw new ServiceException("分享不存在或无权撤销");
        }
        log.info("分享已撤销: shareId={}, userId={}", shareId, userId);
    }

    @Override
    public ShareLinkEntity getShareAccessInfo(String shareToken) {
        ShareLinkEntity share = shareLinkMapper.findByToken(shareToken);
        if (share == null) {
            throw new ServiceException("分享链接不存在");
        }

        if (share.getShare_status() == ShareStatus.revoked) {
            return share;
        }

        if (share.getShare_expires_at() != null && share.getShare_expires_at().isBefore(LocalDateTime.now())) {
            shareLinkMapper.expireOutdatedShares();
            return share;
        }

        return share;
    }

    @Override
    public String verifyPasswordAndGetToken(String shareToken, String password) {
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
    public ShareLinkEntity getShareByAccessToken(String accessToken) {
        String shareToken = jwtUtil.verifyShareAccessToken(accessToken);
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
    public FileEntity getSharedFile(String shareToken, String fileId) {
        ShareLinkEntity share = shareLinkMapper.findByToken(shareToken);
        if (share == null || share.getShare_status() != ShareStatus.active) {
            throw new ServiceException("分享链接无效");
        }

        if (share.getShare_target_type() != TargetType.file) {
            throw new ServiceException("该分享不是文件类型");
        }

        UUID fid = UUID.fromString(fileId);
        if (!fid.equals(share.getShare_file_id())) {
            throw new OverstepAuthorityException("无权访问该文件");
        }

        return fileMapper.findUserFileById(fid, share.getShare_owner_id());
    }

    @Override
    public List<SharedItem> getSharedFolderContents(String shareToken, String nodeId) {
        ShareLinkEntity share = shareLinkMapper.findByToken(shareToken);
        if (share == null || share.getShare_status() != ShareStatus.active) {
            throw new ServiceException("分享链接无效");
        }

        if (share.getShare_target_type() != TargetType.folder) {
            throw new ServiceException("该分享不是文件夹类型");
        }

        // 确定要浏览的节点ID：没传或传了根节点ID则浏览分享根目录
        UUID browseNodeId;
        if (nodeId == null || nodeId.isBlank() || nodeId.equals(share.getShare_node_id().toString())) {
            browseNodeId = share.getShare_node_id();
        } else {
            browseNodeId = UUID.fromString(nodeId);
        }

        UUID ownerId = share.getShare_owner_id();
        List<SharedItem> items = new ArrayList<>();

        // 查询子文件夹
        List<FolderNodeEntity> subFolders = folderNodeMapper.findFolderNodesByIdAndUserId(browseNodeId, ownerId);
        if (subFolders != null) {
            for (FolderNodeEntity folder : subFolders) {
                items.add(SharedItem.ofFolder(folder));
            }
        }

        // 查询子文件
        List<FileEntity> subFiles = fileMapper.findUserActiveFilesByNodeId(browseNodeId, ownerId);
        if (subFiles != null) {
            for (FileEntity file : subFiles) {
                items.add(SharedItem.ofFile(file));
            }
        }

        return items;
    }
}