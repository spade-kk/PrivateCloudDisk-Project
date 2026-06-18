package org.project.control;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.project.control.result.JsonResult;
import org.project.model.dto.ShareCreateRequest;
import org.project.model.entity.ShareLinkEntity;
import org.project.model.vo.ShareAccessInfoVO;
import org.project.model.vo.ShareContentItemVO;
import org.project.model.vo.ShareLinkVO;
import org.project.model.vo.VoMapper;
import org.project.service.ShareService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 分享链接控制器
 *
 * <p>API 分为两组：
 * <ul>
 *   <li><b>管理端</b>（/business/shares）— 需要登录认证，用于创建、查看、撤销分享</li>
 *   <li><b>公开访问端</b>（/public/shares）— 无需登录，用于访问分享链接内容</li>
 * </ul>
 *
 * <p>安全设计：
 * <ul>
 *   <li>分享内容仅通过 share_token 访问，绝不暴露内部 file_id/node_id</li>
 *   <li>密码保护：客户端 PBKDF2-SHA256 预哈希 → 服务端 BCrypt 验证</li>
 *   <li>密码验证通过后签发短期 JWT（15 分钟），后续请求凭 JWT 访问</li>
 *   <li>分享访问永为只读，无 CRUD 权限</li>
 * </ul>
 */
@RestController
@Validated
@RequiredArgsConstructor
public class ShareController extends BaseController {

    private final ShareService shareService;

    private static final String UUID_REGEX = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    // ═══════════════════════════════════════════════
    // 管理端 API（需要登录认证）
    // ═══════════════════════════════════════════════

    /**
     * 创建分享链接
     * <p>分享文件或文件夹，支持设置密码和有效期
     */
    @PostMapping("/business/shares")
    public JsonResult<ShareLinkVO> createShare(
            @Valid @RequestBody ShareCreateRequest request,
            @RequestHeader("X-User-Id") String user_id) {
        ShareLinkEntity entity = shareService.createShare(
                user_id,
                request.getShare_name(),
                request.getTarget_type(),
                request.getFile_id(),
                request.getNode_id(),
                request.getPassword(),
                request.getExpires_in_days());
        return new JsonResult<>(OK, VoMapper.toShareLinkVO(entity));
    }

    /**
     * 获取我的分享列表
     */
    @GetMapping("/business/shares")
    public JsonResult<List<ShareLinkVO>> getMyShares(
            @RequestHeader("X-User-Id") String user_id) {
        List<ShareLinkEntity> entities = shareService.getMyShares(user_id);
        return new JsonResult<>(OK, VoMapper.toShareLinkVOList(entities));
    }

    /**
     * 撤销分享
     */
    @DeleteMapping("/business/shares/{share_id}")
    public JsonResult<Void> revokeShare(
            @Pattern(regexp = UUID_REGEX, message = "share_id 必须是有效的UUID格式")
            @PathVariable String share_id,
            @RequestHeader("X-User-Id") String user_id) {
        shareService.revokeShare(user_id, share_id);
        return new JsonResult<>(OK);
    }

    // ═══════════════════════════════════════════════
    // 公开访问端 API（无需登录）
    // ═══════════════════════════════════════════════

    /**
     * 获取分享公开信息
     * <p>用于展示分享链接页面，返回分享名称、类型、是否有密码等基本信息
     */
    @GetMapping("/public/shares/{share_token}/info")
    public JsonResult<ShareAccessInfoVO> getShareInfo(
            @PathVariable String share_token) {
        ShareLinkEntity entity = shareService.getShareAccessInfo(share_token);
        return new JsonResult<>(OK, VoMapper.toShareAccessInfoVO(entity));
    }

    /**
     * 验证提取码并获取访问令牌
     * <p>密码验证通过后返回短期 JWT 访问令牌，后续请求凭此令牌访问分享内容
     * <p>请求体：{ "password": "客户端预哈希后的密码" }
     */
    @PostMapping("/public/shares/{share_token}/verify")
    public JsonResult<String> verifyPassword(
            @PathVariable String share_token,
            @RequestBody java.util.Map<String, String> body) {
        String password = body.get("password");
        String accessToken = shareService.verifyPasswordAndGetToken(share_token, password);
        return new JsonResult<>(OK, accessToken);
    }

    /**
     * 获取分享内容（文件或文件夹信息）
     */
    @GetMapping("/public/shares/{share_token}/content")
    public JsonResult<ShareLinkEntity> getShareContent(
            @PathVariable String share_token,
            @RequestHeader("X-Share-Access-Token") String access_token) {
        // 验证访问令牌的有效性
        ShareLinkEntity share = shareService.getShareByAccessToken(access_token);
        // 额外校验：访问令牌中的 share_token 必须与请求路径一致
        if (!share.getShare_token().equals(share_token)) {
            throw new org.project.service.ex.OverstepAuthorityException("无权访问该分享");
        }
        // 清除敏感信息
        share.setShare_password(null);
        return new JsonResult<>(OK, share);
    }

    /**
     * 下载分享的文件
     * <p>需要携带有效的访问令牌
     */
    @GetMapping("/public/shares/{share_token}/files/{file_id}/download")
    public JsonResult<org.project.model.entity.FileEntity> downloadSharedFile(
            @PathVariable String share_token,
            @Pattern(regexp = UUID_REGEX, message = "file_id 必须是有效的UUID格式")
            @PathVariable String file_id,
            @RequestHeader("X-Share-Access-Token") String access_token) {
        // 验证访问令牌
        shareService.getShareByAccessToken(access_token);
        // 通过 share_token 获取文件，防止横向越权
        org.project.model.entity.FileEntity file = shareService.getSharedFile(share_token, file_id);
        return new JsonResult<>(OK, file);
    }

    /**
     * 浏览分享文件夹的子内容
     * <p>需要携带有效的访问令牌。
     * <p>安全：通过 share_token 确定分享范围，使用分享者 user_id 查询文件，
     * 杜绝通过 file_id/node_id 横向越权。
     */
    @GetMapping("/public/shares/{share_token}/folders/{node_id}/children")
    public JsonResult<List<ShareContentItemVO>> getSharedFolderChildren(
            @PathVariable String share_token,
            @PathVariable String node_id,
            @RequestHeader("X-Share-Access-Token") String access_token) {
        // 验证访问令牌
        shareService.getShareByAccessToken(access_token);
        List<ShareService.SharedItem> items = shareService.getSharedFolderContents(share_token, node_id);
        return new JsonResult<>(OK, toShareContentItemVOList(items));
    }

    /**
     * 浏览分享文件夹根目录
     */
    @GetMapping("/public/shares/{share_token}/children")
    public JsonResult<List<ShareContentItemVO>> getSharedRootChildren(
            @PathVariable String share_token,
            @RequestHeader("X-Share-Access-Token") String access_token) {
        shareService.getShareByAccessToken(access_token);
        List<ShareService.SharedItem> items = shareService.getSharedFolderContents(share_token, null);
        return new JsonResult<>(OK, toShareContentItemVOList(items));
    }

    // ==================== 私有转换方法 ====================

    private List<ShareContentItemVO> toShareContentItemVOList(List<ShareService.SharedItem> items) {
        List<ShareContentItemVO> vos = new ArrayList<>();
        for (ShareService.SharedItem item : items) {
            ShareContentItemVO vo = new ShareContentItemVO();
            vo.setItem_type(item.itemType());
            if (item.file() != null) {
                vo.setFile_id(item.file().getId().toString());
                vo.setName(item.file().getName());
                vo.setSize(item.file().getSize());
                vo.setFile_type(item.file().getType());
            } else if (item.folder() != null) {
                vo.setNode_id(item.folder().getNode_id().toString());
                vo.setName(item.folder().getName());
                vo.setSize(0L);
            }
            vos.add(vo);
        }
        return vos;
    }
}