package org.project.model.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class MoveFileRequest {
    @NotBlank(message = "目标节点ID不能为空")
    @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
            message = "目标节点ID必须是有效的UUID格式")
    @JsonAlias({"target_position", "new_position"})
    private String target_node_id;
}
