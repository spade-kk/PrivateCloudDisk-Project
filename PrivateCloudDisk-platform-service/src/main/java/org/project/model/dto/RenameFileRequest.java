package org.project.model.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class RenameFileRequest {
    @NotBlank(message = "新文件名不能为空")
    @Pattern(regexp = "^[^\\\\/:*?\"<>|]{1,255}$",
            message = "文件名不能包含非法字符，长度必须为1-255")
    @JsonAlias({"new_name", "name"})
    private String file_new_name;
}
