package org.project.model.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class RenameNodeRequest {
    @NotBlank(message = "新节点名称不能为空")
    @Pattern(regexp = "^[^\\\\/:*?\"<>|]{1,128}$",
            message = "节点名称不能包含非法字符，长度必须为1-128")
    @JsonAlias({"new_name", "name"})
    private String new_node_name;
}
