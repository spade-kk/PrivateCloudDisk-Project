package org.project.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建空间请求 DTO。
 */
@Data
public class CreateSpaceRequest {

    /** 空间名称，1-64 字符 */
    @NotBlank(message = "空间名称不能为空")
    @Size(min = 1, max = 64, message = "空间名称长度必须为1-64个字符")
    @Pattern(regexp = "^[^\\\\/:*?\"<>|]+$", message = "空间名称不能包含非法字符")
    private String spaceName;

    /** 空间类型：personal / private / enterprise / public / team */
    @NotBlank(message = "空间类型不能为空")
    @Pattern(regexp = "^(personal|private|enterprise|public|team)$", message = "空间类型无效")
    private String spaceType;

    /**
     * [REQ-GIT-SPACE-2.2/12.1] 资源实现类型；旧客户端不传时保持 file。
     * 当前只开放 file/git，dataset/docker/model 由后续 Provider 注册后再放开。
     */
    @Pattern(regexp = "^(file|git)$", message = "资源类型无效")
    private String resourceType = "file";

    /** 空间描述，最长 500 字符 */
    @Size(max = 500, message = "空间描述长度不能超过500个字符")
    private String spaceDescription;

    /** 可见性：private / public / whitelist / blacklist */
    @Pattern(regexp = "^(private|public|visible|hidden|whitelist|blacklist)$", message = "可见性类型无效")
    private String spaceVisibility = "private";

    /** [SPACE-COLLAB-DTO-01] 加入策略；个人/公开仓库由服务端强制固定。 */
    @Pattern(regexp = "^(open|approval_required|invite_only)$", message = "加入策略无效")
    private String joinPolicy;

    /** 公开仓库默认可浏览；仅 public 类型读取。 */
    private Boolean allowPublicBrowse;
    /** 公开仓库默认可下载；仅 public 类型读取。 */
    private Boolean allowPublicDownload;
    /** 公开仓库默认禁止公开上传；仅 public 类型读取。 */
    private Boolean allowPublicUpload;
}
