package org.project.control;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.project.control.result.JsonResult;
import org.project.model.dto.ShareCreateRequest;
import org.project.model.entity.ShareLinkEntity;
import org.project.model.entity.ShareResourceEntity;
import org.project.model.vo.ShareAccessInfoVO;
import org.project.model.vo.ShareAccessTokenVO;
import org.project.model.vo.ShareContentItemVO;
import org.project.model.vo.ShareDetailVO;
import org.project.model.vo.ShareLinkVO;
import org.project.model.vo.ShareResourceVO;
import org.project.model.vo.VoMapper;
import org.project.service.ShareService;
import org.project.service.ShareService.ShareLinkDetail;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 分享链接控制器（v2 — 多资源分享模型）
 *
 * <p>API 分为两组：
 * <ul>
 *   <li><b>管理端</b>（/business/shares）— 需要登录认证，用于创建、查看、撤销分享</li>
 *   <li><b>公开访问端</b>（/business/public/shares）— 无需登录，用于访问分享链接内容</li>
 * </ul>
 *
 * <p>安全设计：
 * <ul>
 *   <li>分享内容仅通过 share_token 访问，绝不暴露内部 file_id/node_id</li>
 *   <li>密码保护：明文提取码传入 → 服务端 AES 解密存储值比对</li>
 *   <li>密码验证通过后签发短期 JWT（15 分钟），后续请求凭 JWT 访问</li>
 *   <li>分享访问永为只读，无 CRUD 权限</li>
 *   <li>所有文件/文件夹浏览通过 share_resource_id 进行，杜绝横向越权</li>
 *   <li>公开 info 接口不返回资源列表，需密码验证获取 TOKEN 后才能查看</li>
 *   <li>管理端列表接口不返回资源列表和提取码，需调详情接口查看</li>
 * </ul>
 */
@RestController
@Validated
@RequiredArgsConstructor
public class ShareController extends BaseController {

    private final ShareService shareService;

    // ==================== 参数格式校验正则 ====================

    /** share_id 格式：UUID（管理端内部标识） */
    private static final String UUID_REGEX = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    /** share_token 格式：12 位字母数字（公开接口标识，类似 B 站 BV 号） */
    private static final String SHARE_TOKEN_REGEX = "^[A-Za-z0-9]{12}$";

    /**
     * share_resource_id 格式：UUID 或 Base64URL 编码
     * <ul>
     *   <li>UUID 格式：来自 share_resource 表的主键（如 "550e8400-e29b-41d4-a716-446655440000"）</li>
     *   <li>Base64URL 格式：AES-256-GCM 加密的虚拟资源ID（如 "abc123def456..."），用于子节点浏览</li>
     * </ul>
     */
    private static final String SHARE_RESOURCE_ID_REGEX =
            "^(?:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}|[A-Za-z0-9_-]+)$";

    /**
     * 提取码格式：4-20 位字母数字组合
     * <p>约束：
     * <ul>
     *   <li>最小长度：4 位（防止暴力破解）</li>
     *   <li>最大长度：20 位（用户体验 + 存储限制）</li>
     *   <li>字符集：仅限大小写字母和数字，禁止特殊字符（防止注入和编码问题）</li>
     *   <li>不能为空字符串</li>
     * </ul>
     */
    private static final String PASSWORD_REGEX = "^[A-Za-z0-9]{4,20}$";

    // ═══════════════════════════════════════════════
    // 管理端 API（需要登录认证）
    // ═══════════════════════════════════════════════

    /**
     * 创建分享链接（v2 — 多资源）
     * <p>支持同时分享多个文件和文件夹，支持设置密码和有效期。
     * 密码为明文传入，服务端 AES-256-GCM 加密存储。
     *
     * <p>请求示例：
     * <pre>{@code
     * {
     *   "share_name": "项目资料",
     *   "resources": [
     *     { "type": "file",   "id": "aaa-bbb-ccc" },
     *     { "type": "folder", "id": "ddd-eee-fff" }
     *   ],
     *   "password": "1234",
     *   "expires_in_days": 7
     * }
     * }</pre>
     */
    @PostMapping("/business/shares")
    public JsonResult<ShareLinkVO> createShare(
            @Valid @RequestBody ShareCreateRequest request,
            @RequestHeader("X-User-Id") String userId) {
        List<ShareService.ResourceItem> resourceItems = request.getResources().stream()
                .map(r -> ShareService.ResourceItem.of(r.getType(), r.getId()))
                .toList();

        ShareLinkEntity entity = shareService.createShare(
                userId,
                request.getShare_name(),
                request.getShare_description(),
                resourceItems,
                request.getPassword(),
                request.getExpires_in_days(),
                request.getAllow_download());
        return new JsonResult<>(OK, VoMapper.toShareLinkVO(entity));
    }

    /**
     * 获取我的分享列表（不含资源列表和提取码）
     * <p>只返回分享链接的基本信息（名称、状态、过期时间等），
     * 不返回资源列表和提取码。如需查看详情，调用 GET /business/shares/{share_id}。
     */
    @GetMapping("/business/shares")
    public JsonResult<List<ShareLinkVO>> getMyShares(
            @RequestHeader("X-User-Id") String userId) {
        List<ShareLinkEntity> entities = shareService.getMyShares(userId);
        return new JsonResult<>(OK, VoMapper.toShareLinkVOList(entities));
    }

    /**
     * 获取分享链接详情（管理端，含资源列表和明文提取码）
     * <p>与列表接口的区别：返回完整的资源列表和解密后的提取码。
     * 用户可以在管理页面查看自己分享链接的提取码。
     */
    @GetMapping("/business/shares/{share_id}")
    public JsonResult<ShareDetailVO> getShareDetail(
            @Pattern(regexp = UUID_REGEX, message = "share_id 必须是有效的UUID格式")
            @PathVariable String share_id,
            @RequestHeader("X-User-Id") String userId) {
        ShareLinkDetail detail = shareService.getShareDetail(userId, share_id);
        return new JsonResult<>(OK, VoMapper.toShareDetailVO(detail.share(), detail.decryptedPassword()));
    }

    /**
     * 撤销分享
     */
    @DeleteMapping("/business/shares/{share_id}")
    public JsonResult<Void> revokeShare(
            @Pattern(regexp = UUID_REGEX, message = "share_id 必须是有效的UUID格式")
            @PathVariable String share_id,
            @RequestHeader("X-User-Id") String userId) {
        shareService.revokeShare(userId, share_id);
        return new JsonResult<>(OK);
    }

    /**
     * 修改分享链接提取码（管理端）
     * <p>用户可以在管理页面修改自己分享链接的提取码。
     * 请求体：{ "password": "新提取码（明文）" }
     */
    @PutMapping("/business/shares/{share_id}/password")
    public JsonResult<Void> updateSharePassword(
            @Pattern(regexp = UUID_REGEX, message = "share_id 必须是有效的UUID格式")
            @PathVariable String share_id,
            @RequestBody java.util.Map<String, String> body,
            @RequestHeader("X-User-Id") String userId) {
        String newPassword = body.get("password");
        // 提取码格式校验：允许为空（表示移除密码），非空时需符合 4-20 位字母数字格式
        if (newPassword != null && !newPassword.isBlank() && !newPassword.matches(PASSWORD_REGEX)) {
            throw new org.project.service.ex.ServiceException("提取码格式错误：必须为4-20位字母数字组合");
        }
        shareService.updateSharePassword(userId, share_id, newPassword);
        return new JsonResult<>(OK);
    }

    /**
     * 需求二-2：管理端可将分享切换为“仅浏览”或“允许下载”。
     * 原有密码接口保持不变，权限开关独立更新，避免误改提取码。
     */
    @PutMapping("/business/shares/{share_id}/download-permission")
    public JsonResult<Void> updateShareDownloadPermission(
            @Pattern(regexp = UUID_REGEX, message = "share_id 必须是有效的UUID格式")
            @PathVariable String share_id,
            @RequestBody java.util.Map<String, Boolean> body,
            @RequestHeader("X-User-Id") String userId) {
        Boolean allowDownload = body.get("allow_download");
        if (allowDownload == null) {
            throw new org.project.service.ex.ServiceException("allow_download 不能为空");
        }
        shareService.updateShareDownloadPermission(userId, share_id, allowDownload);
        return new JsonResult<>(OK);
    }

    // ═══════════════════════════════════════════════
    // 公开访问端 API（无需登录）
    // ═══════════════════════════════════════════════

    /**
     * 获取分享公开信息（不含资源列表）
     * <p>用于展示分享链接页面，返回分享名称、创建者、是否需要密码等基本信息。
     * 不返回资源列表 — 资源列表需通过提取码验证获取 TOKEN 后才能查看。
     */
    @GetMapping("/business/public/shares/{share_token}/info")
    public JsonResult<ShareAccessInfoVO> getShareInfo(
            @Pattern(regexp = SHARE_TOKEN_REGEX, message = "share_token 必须是12位字母数字组合")
            @PathVariable String share_token) {
        ShareLinkEntity entity = shareService.getShareAccessInfo(share_token);
        return new JsonResult<>(OK, VoMapper.toShareAccessInfoVO(entity));
    }

    /**
     * 验证提取码并获取访问令牌
     * <p>密码验证通过后返回短期 JWT 访问令牌（15 分钟），后续请求凭此令牌访问分享内容。
     * <p>请求体：{ "password": "明文提取码" }
     * <p>注意：密码为明文，因为提取码不同于登录密码，不需要客户端哈希。
     */
    @PostMapping("/business/public/shares/{share_token}/verify")
    public JsonResult<ShareAccessTokenVO> verifyPassword(
            @Pattern(regexp = SHARE_TOKEN_REGEX, message = "share_token 必须是12位字母数字组合")
            @PathVariable String share_token,
            @RequestBody java.util.Map<String, String> body) {
        String password = body.get("password");
        // 无密码分享也需通过此接口签发只读访问令牌。
        // 仅当客户端实际提交了提取码时校验格式；是否必填由服务层根据分享配置判断。
        if (password != null && !password.isBlank() && !password.matches(PASSWORD_REGEX)) {
            throw new org.project.service.ex.ServiceException("提取码格式错误：必须为4-20位字母数字组合");
        }
        String accessToken = shareService.verifyPasswordAndGetToken(share_token, password);
        ShareLinkEntity share = shareService.getShareAccessInfo(share_token);
        ShareAccessTokenVO vo = new ShareAccessTokenVO();
        vo.setAccess_token(accessToken);
        vo.setShare_name(share.getShare_name());
        vo.setResource_count(share.getResource_count());
        return new JsonResult<>(OK, vo);
    }

    /**
     * 获取分享内容（根资源列表）
     * <p>需要携带有效的 X-Share-Access-Token 请求头。
     * 返回分享链接中的顶层资源列表，每个资源包含 share_resource_id 用于后续操作。
     */
    @GetMapping("/business/public/shares/{share_token}/content")
    public JsonResult<List<ShareResourceVO>> getShareContent(
            @Pattern(regexp = SHARE_TOKEN_REGEX, message = "share_token 必须是12位字母数字组合")
            @PathVariable String share_token,
            @RequestHeader("X-Share-Access-Token") String accessToken) {
        // 验证访问令牌的有效性
        ShareLinkEntity share = shareService.getShareByAccessToken(accessToken);
        // 额外校验：访问令牌中的 share_token 必须与请求路径一致
        if (!share.getShare_token().equals(share_token)) {
            throw new org.project.service.ex.OverstepAuthorityException("无权访问该分享");
        }
        List<ShareResourceEntity> resources = shareService.getShareResources(share_token);
        return new JsonResult<>(OK, VoMapper.toShareResourceVOList(resources));
    }

    /**
     * 获取单个分享资源详情；即使资源 ID 为真实 UUID，也必须绑定当前 share_token。
     * 虚拟子节点 ID 不在此接口解析，避免把内部 file_id/node_id 重新暴露出去。
     */
    @GetMapping("/business/public/shares/{share_token}/resources/{share_resource_id}")
    public JsonResult<ShareResourceVO> getShareResourceDetail(
            @Pattern(regexp = SHARE_TOKEN_REGEX, message = "share_token 必须是12位字母数字组合")
            @PathVariable String share_token,
            @Pattern(regexp = SHARE_RESOURCE_ID_REGEX, message = "share_resource_id 格式错误：必须是UUID或Base64URL格式")
            @PathVariable String share_resource_id,
            @RequestHeader("X-Share-Access-Token") String accessToken) {
        ShareLinkEntity share = shareService.getShareByAccessToken(accessToken);
        if (!share.getShare_token().equals(share_token)) {
            throw new org.project.service.ex.OverstepAuthorityException("无权访问该分享");
        }
        ShareResourceEntity resource = shareService.getShareResourceDetail(share_token, share_resource_id);
        return new JsonResult<>(OK, VoMapper.toShareResourceVO(resource));
    }

    /**
     * 浏览分享文件夹的子内容（通过 share_resource_id，支持真实和虚拟 ID）
     * <p>需要携带有效的访问令牌。
     * <p>安全：先尝试真实 share_resource_id（来自 share_resource 表），
     * 再尝试解析虚拟 share_resource_id（AES 加密的内部 ID），
     * 杜绝通过 file_id/node_id 横向越权。
     */
    @GetMapping("/business/public/shares/{share_token}/resources/{share_resource_id}/children")
    public JsonResult<List<ShareContentItemVO>> getShareResourceChildren(
            @Pattern(regexp = SHARE_TOKEN_REGEX, message = "share_token 必须是12位字母数字组合")
            @PathVariable String share_token,
            @Pattern(regexp = SHARE_RESOURCE_ID_REGEX, message = "share_resource_id 格式错误：必须是UUID或Base64URL格式")
            @PathVariable String share_resource_id,
            @RequestHeader("X-Share-Access-Token") String accessToken) {
        // 访问令牌必须与 URL 中的分享严格绑定，防止跨分享复用。
        ShareLinkEntity share = shareService.getShareByAccessToken(accessToken);
        if (!share.getShare_token().equals(share_token)) {
            throw new org.project.service.ex.OverstepAuthorityException("无权访问该分享");
        }
        List<ShareService.SharedItem> items = shareService.getShareResourceChildrenByVirtualId(share_token, share_resource_id);
        return new JsonResult<>(OK, toShareContentItemVOList(share_token, items));
    }

    // ==================== 私有转换方法 ====================

    /**
     * 将 SharedItem 列表转换为 ShareContentItemVO 列表
     * <p>为每个子项生成虚拟 share_resource_id（AES 加密的内部 ID），
     * 不暴露 file_id/node_id 给客户端。
     */
    private List<ShareContentItemVO> toShareContentItemVOList(String shareToken, List<ShareService.SharedItem> items) {
        List<ShareContentItemVO> vos = new ArrayList<>();
        for (ShareService.SharedItem item : items) {
            ShareContentItemVO vo = new ShareContentItemVO();
            vo.setItem_type(item.itemType());
            if (item.file() != null) {
                vo.setShare_resource_id(shareService.encodeVirtualResourceId(shareToken, "file", item.file().getId().toString()));
                vo.setName(item.file().getName());
                vo.setSize(item.file().getSize());
                vo.setFile_type(item.file().getType());
            } else if (item.folder() != null) {
                vo.setShare_resource_id(shareService.encodeVirtualResourceId(shareToken, "folder", item.folder().getNode_id().toString()));
                vo.setName(item.folder().getName());
                vo.setSize(0L);
            }
            vos.add(vo);
        }
        return vos;
    }
}
