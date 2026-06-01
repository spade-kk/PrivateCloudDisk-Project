package org.project.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建上传会话请求体参数Json对象
 * total_chunks 总分块数
 * file_size 文件大小
 * file_checksum 文件校验和
 * chunks_max_size 分块最大大小
 * file_name 文件名称
 * file_type 文件类型
 * node_id 目录节点ID
 * user_id 用户登陆的uid
 */
@Data
public class CreateUploadsSessionRequest {
    @NotNull
    Integer total_chunks;
    @NotNull
    Long file_size;
    @NotBlank
    String file_checksum;
    @NotNull
    Integer chunks_max_size;
    @NotBlank
    String file_name;
    @NotBlank
    String file_type;
    @NotBlank
    String node_id;
}
