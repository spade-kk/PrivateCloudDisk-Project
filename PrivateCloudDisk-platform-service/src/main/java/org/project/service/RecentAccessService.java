package org.project.service;

import org.project.model.vo.RecentAccessVO;

import java.util.List;
import java.util.UUID;

/**
 * 最近访问记录服务接口
 */
public interface RecentAccessService {

    /** 最近访问保留数量上限 */
    int MAX_RECORDS_PER_TYPE = 100;

    /**
     * 记录最近访问
     * @param user_id 用户ID
     * @param target_id 目标ID（文件ID 或 节点ID）
     * @param target_type 目标类型：file / folder
     * @param access_type 访问类型：upload / download / open
     * @param file_name 文件名称
     * @param file_size 文件大小
     * @param file_type 文件类型
     */
    void recordAccess(UUID user_id, String target_id, String target_type,
                      String access_type, String file_name, Long file_size, String file_type);

    /**
     * 记录分享授权下载，不保存真实 file_id，避免分享资源 ID 与平台内部 ID 混淆。
     */
    void recordShareDownloadAccess(UUID user_id, String shareResourceId,
                                   String fileName, Long fileSize, String fileType);

    /**
     * 获取用户最近访问列表（按类型筛选）
     */
    List<RecentAccessVO> getRecentAccess(UUID user_id, String access_type, int page, int pageSize);

    /**
     * 获取用户所有最近访问（混合类型）
     */
    List<RecentAccessVO> getAllRecentAccess(UUID user_id, int page, int pageSize);
}
