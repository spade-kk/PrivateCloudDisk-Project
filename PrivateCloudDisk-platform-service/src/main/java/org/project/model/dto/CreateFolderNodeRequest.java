package org.project.model.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
}
