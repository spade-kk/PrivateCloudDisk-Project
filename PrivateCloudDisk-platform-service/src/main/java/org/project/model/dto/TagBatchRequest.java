package org.project.model.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 文件列表标签批量查询请求。
 *
 * <p>文件与目录分开传递，避免依赖名称或前端猜测类型；单次最多各查询 500 个目标，
 * 防止异常请求生成过大的 IN 条件。</p>
 */
@Data
public class TagBatchRequest {
    @Size(max = 500)
    private List<UUID> file_ids = new ArrayList<>();

    @Size(max = 500)
    private List<UUID> folder_ids = new ArrayList<>();
}
