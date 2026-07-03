package org.project.model.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateFolderNodeRequest {
    @NotBlank(message = "父节点ID不能为空")
    @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
            message = "父节点ID必须是有效的UUID格式")
    @JsonAlias({"position", "parent_id"})
    private String node_id;

    @NotBlank(message = "文件夹名称不能为空")
    @Pattern(regexp = "^[^\\\\/:*?\"<>|]{1,128}$",
            message = "文件夹名称不能包含非法字符，长度必须为1-128")
    @JsonAlias({"name", "node_name"})
    private String folder_name;

    /** 相对路径，最长 1024 字符 */
    @Size(max = 1024, message = "路径长度不能超过1024个字符")
    private String relative_path;

    /** 纯面包屑路径，最长 1024 字符 */
    @Size(max = 1024, message = "路径长度不能超过1024个字符")
    private String breadcrumb_path;
}
