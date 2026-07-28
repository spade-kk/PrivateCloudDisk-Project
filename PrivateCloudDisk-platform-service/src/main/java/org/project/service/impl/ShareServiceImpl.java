package org.project.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.mapper.FileMapper;
import org.project.mapper.FolderNodeMapper;
import org.project.mapper.ShareLinkMapper;
import org.project.mapper.ShareResourceMapper;
import org.project.model.entity.FileEntity;
import org.project.model.entity.FolderNodeEntity;
import org.project.model.entity.ShareLinkEntity;
import org.project.model.entity.ShareLinkEntity.ShareStatus;
import org.project.model.entity.ShareResourceEntity;
import org.project.model.entity.ShareResourceEntity.ResourceType;
import org.project.service.ShareService;
import org.project.service.ex.*;
import org.project.util.AesUtil;
import org.project.util.JwtUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 分享链接服务实现（v2 — 多资源分享模型）
 *
 * <p>关键安全变更：
 * <ol>
 *   <li>提取码用 AES-256-GCM 加密存储（可逆，允许管理端查看明文）</li>
 *   <li>公开端 info 接口不返回资源列表</li>
 *   <li>管理端列表接口不返回资源列表，需单独调详情接口查看</li>
 *   <li>文件夹浏览通过 share_resource_id 而非内部 node_id</li>
 *   <li>文件下载通过 share_resource_id 而非内部 file_id</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShareServiceImpl implements ShareService {

    private final ShareLinkMapper shareLinkMapper;
    private final ShareResourceMapper shareResourceMapper;
    private final FileMapper fileMapper;
    private final FolderNodeMapper folderNodeMapper;
    private final AesUtil aesUtil;
    private final JwtUtil jwtUtil;

    // ==================== 分享令牌生成常量 ====================

    /** 分享令牌字符集：大写字母 + 小写字母 + 数字（62 个字符） */
    private static final String TOKEN_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    /** 分享令牌长度：12 位（类似 B 站 BV 号，用户友好、可读性强） */
    private static final int TOKEN_LENGTH = 12;
    /** 安全随机数生成器（用于生成分享令牌，比 Random 熵更高） */
    private static final SecureRandom TOKEN_RANDOM = new SecureRandom();

    // ==================== 虚拟资源ID 类型标记常量 ====================

    /** 虚拟资源ID 类型标记：文件 */
    private static final byte VIRTUAL_TYPE_FILE = 0x01;
    /** 虚拟资源ID 类型标记：文件夹 */
    private static final byte VIRTUAL_TYPE_FOLDER = 0x02;

    // ==================== 管理端 ====================

    @Override
    @Transactional
    public ShareLinkEntity createShare(String userId, String shareName, String shareDescription,
                                        List<ResourceItem> resources,
                                        String password, Integer expireDays) {
        if (resources == null || resources.isEmpty()) {
            throw new ServiceException("分享资源列表不能为空");
        }

        UUID ownerId = UUID.fromString(userId);

        // 1. 创建分享链接
        ShareLinkEntity share = new ShareLinkEntity();
        share.setShare_id(UUID.randomUUID());
        // 分享令牌：12 位随机字母数字（类似 B 站 BV 号，用户友好、可读性强）
        // 使用 SecureRandom 生成，62^12 ≈ 3.2×10^21 种组合，碰撞概率极低
        share.setShare_token(generateShareToken());
        share.setShare_owner_id(ownerId);
        share.setShare_name(shareName);
        share.setShare_description(shareDescription == null || shareDescription.isBlank() ? null : shareDescription.trim());
        share.setShare_status(ShareLinkEntity.ShareStatus.active);

        // 密码 AES 加密（可逆，管理端可查看）
        if (password != null && !password.isBlank()) {
            share.setShare_password(aesUtil.encrypt(password));
            share.setShare_has_password(true);
        } else {
            share.setShare_password(null);
            share.setShare_has_password(false);
        }

        if (expireDays != null && expireDays > 0) {
            share.setShare_expires_at(LocalDateTime.now().plusDays(expireDays));
        } else {
            share.setShare_expires_at(null);
        }

        int rows = shareLinkMapper.insertShare(share);
        if (rows != 1) {
            throw new InsertException("创建分享链接失败");
        }

        // 2. 校验资源并构建资源实体
        List<ShareResourceEntity> resourceEntities = new ArrayList<>();
        for (ResourceItem item : resources) {
            ShareResourceEntity res = new ShareResourceEntity();
            res.setShare_resource_id(UUID.randomUUID());
            res.setShare_id(share.getShare_id());

            if ("file".equals(item.type())) {
                UUID fid = UUID.fromString(item.id());
                FileEntity file = fileMapper.findUserFileById(fid, ownerId);
                if (file == null) {
                    throw new FileNotExistException("文件不存在或无权访问: " + item.id());
                }
                res.setResource_type(ResourceType.file);
                res.setFile_id(fid);
                res.setNode_id(null);
                res.setResource_name(file.getName());
                res.setResource_size(file.getSize());
                res.setFile_type(file.getType());
            } else if ("folder".equals(item.type())) {
                UUID nid = UUID.fromString(item.id());
                FolderNodeEntity folder = folderNodeMapper.findFolderNodeByIdAndUserId(nid, ownerId);
                if (folder == null) {
                    throw new NodeNotExistException("文件夹不存在或无权访问: " + item.id());
                }
                res.setResource_type(ResourceType.folder);
                res.setFile_id(null);
                res.setNode_id(nid);
                res.setResource_name(folder.getName());
                res.setResource_size(0L);
            } else {
                throw new ServiceException("无效的资源类型: " + item.type());
            }
            resourceEntities.add(res);
        }

        int resRows = shareResourceMapper.insertBatch(resourceEntities);
        if (resRows != resourceEntities.size()) {
            throw new InsertException("创建分享资源失败");
        }

        share.setResources(resourceEntities);
        share.setResource_count(resourceEntities.size());

        log.info("分享链接创建成功: userId={}, token={}, resourceCount={}",
                userId, share.getShare_token(), resourceEntities.size());
        return share;
    }

    @Override
    public List<ShareLinkEntity> getMyShares(String userId) {
        UUID ownerId = UUID.fromString(userId);
        return shareLinkMapper.findByOwnerId(ownerId);
    }

    @Override
    public ShareLinkDetail getShareDetail(String userId, String shareId) {
        UUID sid = UUID.fromString(shareId);
        UUID ownerId = UUID.fromString(userId);
        ShareLinkEntity share = shareLinkMapper.findById(sid);
        if (share == null || !share.getShare_owner_id().equals(ownerId)) {
            throw new ServiceException("分享不存在或无权查看");
        }
        // 填充资源列表
        List<ShareResourceEntity> resources = shareResourceMapper.findByShareId(sid);
        share.setResources(resources);
        share.setResource_count(resources != null ? resources.size() : 0);

        // 解密提取码
        String decryptedPassword = null;
        if (share.getShare_has_password() && share.getShare_password() != null) {
            try {
                decryptedPassword = aesUtil.decrypt(share.getShare_password());
            } catch (Exception e) {
                log.warn("提取码解密失败: shareId={}", shareId);
            }
        }
        return new ShareLinkDetail(share, decryptedPassword);
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

    // ==================== 公开访问端 ====================

    @Override
    public ShareLinkEntity getShareAccessInfo(String shareToken) {
        ShareLinkEntity share = shareLinkMapper.findByToken(shareToken);
        if (share == null) {
            throw new ServiceException("分享链接不存在");
        }
        if (share.getShare_status() == ShareStatus.revoked) {
            return share;
        }
        if (share.getShare_expires_at() != null
                && share.getShare_expires_at().isBefore(LocalDateTime.now())) {
            shareLinkMapper.expireOutdatedShares();
            return share;
        }
        // 不返回资源列表 — 需密码验证后获取
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
        if (share.getShare_expires_at() != null
                && share.getShare_expires_at().isBefore(LocalDateTime.now())) {
            shareLinkMapper.expireOutdatedShares();
            throw new ServiceException("分享链接已过期");
        }

        // 验证密码：AES 解密后对比
        if (share.getShare_has_password() && share.getShare_password() != null) {
            if (password == null || password.isBlank()) {
                throw new PasswordNotMatchException("提取码不能为空");
            }
            String storedPassword;
            try {
                storedPassword = aesUtil.decrypt(share.getShare_password());
            } catch (Exception e) {
                throw new PasswordNotMatchException("提取码验证失败");
            }
            if (!password.equals(storedPassword)) {
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
        if (share.getShare_expires_at() != null
                && share.getShare_expires_at().isBefore(LocalDateTime.now())) {
            shareLinkMapper.expireOutdatedShares();
            throw new ServiceException("分享链接已过期");
        }
        // 填充资源列表
        List<ShareResourceEntity> resources = shareResourceMapper.findByShareId(share.getShare_id());
        share.setResources(resources);
        share.setResource_count(resources != null ? resources.size() : 0);
        return share;
    }

    @Override
    public List<ShareResourceEntity> getShareResources(String shareToken) {
        ShareLinkEntity share = shareLinkMapper.findByToken(shareToken);
        if (share == null || share.getShare_status() != ShareStatus.active) {
            throw new ServiceException("分享链接无效");
        }
        return shareResourceMapper.findByShareId(share.getShare_id());
    }

    @Override
    public List<SharedItem> getShareResourceChildren(String shareToken, String shareResourceId) {
        // 1. 验证分享有效性
        ShareLinkEntity share = shareLinkMapper.findByToken(shareToken);
        if (share == null || share.getShare_status() != ShareStatus.active) {
            throw new ServiceException("分享链接无效");
        }

        // 2. 通过 share_resource_id 查询资源（不暴露内部 node_id）
        ShareResourceEntity resource = shareResourceMapper.findById(UUID.fromString(shareResourceId));
        if (resource == null || !resource.getShare_id().equals(share.getShare_id())) {
            throw new OverstepAuthorityException("该资源不在分享范围内");
        }
        if (resource.getResource_type() != ResourceType.folder) {
            throw new ServiceException("该资源不是文件夹，无法浏览子内容");
        }

        UUID nodeId = resource.getNode_id();
        UUID ownerId = share.getShare_owner_id();
        List<SharedItem> items = new ArrayList<>();

        List<FolderNodeEntity> subFolders = folderNodeMapper.findFolderNodesByIdAndUserId(nodeId, ownerId);
        if (subFolders != null) {
            for (FolderNodeEntity folder : subFolders) {
                items.add(SharedItem.ofFolder(folder));
            }
        }

        List<FileEntity> subFiles = fileMapper.findUserActiveFilesByNodeId(nodeId, ownerId);
        if (subFiles != null) {
            for (FileEntity file : subFiles) {
                items.add(SharedItem.ofFile(file));
            }
        }

        return items;
    }

    @Override
    public FileEntity getSharedFileByResourceId(String shareToken, String shareResourceId) {
        // 1. 验证分享有效性
        ShareLinkEntity share = shareLinkMapper.findByToken(shareToken);
        if (share == null || share.getShare_status() != ShareStatus.active) {
            throw new ServiceException("分享链接无效");
        }

        // 2. 尝试通过真实 share_resource_id 查找
        try {
            UUID realId = UUID.fromString(shareResourceId);
            ShareResourceEntity resource = shareResourceMapper.findById(realId);
            if (resource != null && resource.getShare_id().equals(share.getShare_id())) {
                if (resource.getResource_type() != ResourceType.file) {
                    throw new ServiceException("该资源不是文件，无法下载");
                }
                return fileMapper.findUserFileById(resource.getFile_id(), share.getShare_owner_id());
            }
        } catch (IllegalArgumentException ignored) {
            // 不是 UUID 格式，尝试作为虚拟 ID 解析
        }

        // 3. 尝试解析虚拟 share_resource_id
        String[] decoded = decodeVirtualResourceId(shareResourceId);
        if (decoded != null && decoded.length == 3 && shareToken.equals(decoded[0]) && "file".equals(decoded[1])) {
            UUID fileId = UUID.fromString(decoded[2]);
            FileEntity file = fileMapper.findUserFileById(fileId, share.getShare_owner_id());
            if (file == null) {
                throw new OverstepAuthorityException("文件不在分享范围内");
            }
            return file;
        }

        throw new OverstepAuthorityException("该资源不在分享范围内");
    }

    @Override
    @Transactional
    public void updateSharePassword(String userId, String shareId, String newPassword) {
        UUID sid = UUID.fromString(shareId);
        UUID ownerId = UUID.fromString(userId);
        ShareLinkEntity share = shareLinkMapper.findById(sid);
        if (share == null || !share.getShare_owner_id().equals(ownerId)) {
            throw new ServiceException("分享不存在或无权修改");
        }
        if (newPassword != null && !newPassword.isBlank()) {
            share.setShare_password(aesUtil.encrypt(newPassword));
            share.setShare_has_password(true);
        } else {
            share.setShare_password(null);
            share.setShare_has_password(false);
        }
        shareLinkMapper.updateShare(share);
        log.info("分享提取码已更新: shareId={}, userId={}", shareId, userId);
    }

    @Override
    public String encodeVirtualResourceId(String shareToken, String type, String internalId) {
        // ============================================================
        // 虚拟资源ID 编码（紧凑二进制格式）
        // ============================================================
        // 格式：Base64URL( AES-256-GCM( [1B类型标记][12B分享令牌][16B UUID] ) )
        //
        // 设计原理：
        //   1. 将 UUID 字符串转为 16 字节二进制，比字符串形式（36 字符）缩短 55%
        //   2. 类型标记仅 1 字节（0x01=file, 0x02=folder），替代字符串前缀
        //   3. 将 shareToken 一并加密，虚拟资源 ID 无法跨分享复用
        //   4. Base64URL 无 + / = 字符，可直接作为 URL 路径参数，无冲突
        //
        // 无状态设计：无需额外数据库表或 Redis 缓存，解密即可还原内部 ID
        // ============================================================
        UUID uuid = UUID.fromString(internalId);
        byte typeFlag = "file".equals(type) ? VIRTUAL_TYPE_FILE : VIRTUAL_TYPE_FOLDER;
        byte[] tokenBytes = shareToken.getBytes(StandardCharsets.US_ASCII);
        if (tokenBytes.length != TOKEN_LENGTH) {
            throw new ServiceException("分享令牌格式错误");
        }

        // 构建 29 字节明文：[1B 类型标记][12B 分享令牌][16B UUID]
        byte[] plainBytes = new byte[1 + TOKEN_LENGTH + 16];
        plainBytes[0] = typeFlag;
        System.arraycopy(tokenBytes, 0, plainBytes, 1, TOKEN_LENGTH);
        ByteBuffer uuidBuf = ByteBuffer.wrap(plainBytes, 1 + TOKEN_LENGTH, 16);
        uuidBuf.putLong(uuid.getMostSignificantBits());
        uuidBuf.putLong(uuid.getLeastSignificantBits());

        return aesUtil.encryptBytesToBase64Url(plainBytes);
    }

    @Override
    public String[] decodeVirtualResourceId(String virtualId) {
        // ============================================================
        // 虚拟资源ID 解码（紧凑二进制格式）
        // ============================================================
        // 逆过程：Base64URL 解码 → AES-GCM 解密 → 还原 [类型标记, UUID]
        //
        // 兼容说明：
        //   该方法同时支持新旧两种格式的虚拟资源ID：
        //   - 安全格式（二进制）：解密后为 [类型][分享令牌][UUID] 共 29 字节
        //   - 旧格式不再接受，因为其中没有分享边界，可能导致跨分享复用
        // ============================================================
        try {
            // 优先尝试新格式：二进制解码
            byte[] plainBytes = aesUtil.decryptBytesFromBase64Url(virtualId);
            if (plainBytes != null && plainBytes.length == 1 + TOKEN_LENGTH + 16) {
                byte typeFlag = plainBytes[0];
                String shareToken = new String(plainBytes, 1, TOKEN_LENGTH, StandardCharsets.US_ASCII);
                ByteBuffer uuidBuf = ByteBuffer.wrap(plainBytes, 1 + TOKEN_LENGTH, 16);
                long high = uuidBuf.getLong();
                long low = uuidBuf.getLong();
                UUID uuid = new UUID(high, low);

                String type = (typeFlag == VIRTUAL_TYPE_FILE) ? "file" : "folder";
                return new String[]{shareToken, type, uuid.toString()};
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    @Override
    public List<SharedItem> getShareResourceChildrenByVirtualId(String shareToken, String virtualResourceId) {
        // 1. 先尝试作为真实 share_resource_id 查找
        try {
            UUID realId = UUID.fromString(virtualResourceId);
            ShareResourceEntity resource = shareResourceMapper.findById(realId);
            if (resource != null) {
                // 真实 ID，使用原有逻辑
                return getShareResourceChildren(shareToken, virtualResourceId);
            }
        } catch (IllegalArgumentException ignored) {
            // 不是 UUID 格式，尝试作为虚拟 ID 解析
        }

        // 2. 尝试解析虚拟 share_resource_id
        String[] decoded = decodeVirtualResourceId(virtualResourceId);
        if (decoded == null || decoded.length != 3 || !shareToken.equals(decoded[0])) {
            throw new ServiceException("无效的分享资源标识");
        }
        String type = decoded[1];
        String internalId = decoded[2];

        if (!"folder".equals(type)) {
            throw new ServiceException("该资源不是文件夹，无法浏览子内容");
        }

        // 3. 验证分享有效性
        ShareLinkEntity share = shareLinkMapper.findByToken(shareToken);
        if (share == null || share.getShare_status() != ShareStatus.active) {
            throw new ServiceException("分享链接无效");
        }

        UUID nodeId = UUID.fromString(internalId);
        UUID ownerId = share.getShare_owner_id();
        List<SharedItem> items = new ArrayList<>();

        // 查询子文件夹
        List<FolderNodeEntity> subFolders = folderNodeMapper.findFolderNodesByIdAndUserId(nodeId, ownerId);
        if (subFolders != null) {
            for (FolderNodeEntity folder : subFolders) {
                items.add(SharedItem.ofFolder(folder));
            }
        }

        // 查询子文件
        List<FileEntity> subFiles = fileMapper.findUserActiveFilesByNodeId(nodeId, ownerId);
        if (subFiles != null) {
            for (FileEntity file : subFiles) {
                items.add(SharedItem.ofFile(file));
            }
        }

        return items;
    }

    // ==================== 私有工具方法 ====================

    /**
     * 生成分享令牌（12 位随机字母数字）
     *
     * <p>设计参考：
     * <ul>
     *   <li>B 站 BV 号：12 位字母数字组合（如 BV1tz4zzVEs4），简洁、用户友好、可读性强</li>
     *   <li>百度网盘：短链格式（如 1abcDEF），使用数据库存储映射关系</li>
     *   <li>夸克网盘：类似短链格式</li>
     * </ul>
     *
     * <p>本实现采用无状态生成方案：
     * <ul>
     *   <li>字符集：62 个字符（A-Z, a-z, 0-9）</li>
     *   <li>长度：12 位</li>
     *   <li>熵：62^12 ≈ 3.2×10^21 种组合，碰撞概率极低</li>
     *   <li>随机源：{@link SecureRandom}，密码学安全</li>
     *   <li>碰撞检测：最多重试 5 次，防止极小概率的令牌冲突</li>
     * </ul>
     *
     * @return 12 位随机字母数字字符串（如 "aB3xK9mP2qR7"）
     * @throws ServiceException 连续 5 次碰撞后抛出
     */
    private String generateShareToken() {
        for (int attempt = 0; attempt < 5; attempt++) {
            StringBuilder sb = new StringBuilder(TOKEN_LENGTH);
            for (int i = 0; i < TOKEN_LENGTH; i++) {
                sb.append(TOKEN_CHARS.charAt(TOKEN_RANDOM.nextInt(TOKEN_CHARS.length())));
            }
            String token = sb.toString();
            // 检查令牌是否已存在（防止极小概率的碰撞）
            if (shareLinkMapper.findByToken(token) == null) {
                return token;
            }
            log.warn("分享令牌碰撞，重试中... (第 {} 次)", attempt + 1);
        }
        throw new ServiceException("生成分享令牌失败，请重试");
    }
}
