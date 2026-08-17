package org.project.model.dto.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文件下载完成事件
 * <p>文件下载完成后由存储服务发布。
 * 消费者：主业务服务 → 记录最近下载
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileDownloadedEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 事件ID（幂等去重） */
    private String eventId;

    /** 文件ID */
    private String fileId;

    /** 文件名称 */
    private String fileName;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 文件类型 */
    private String fileType;

    /** 用户ID */
    private String userId;

    /** 需求五-9：下载记录所属空间。 */
    private String spaceId;

    /** 访问来源：space=普通文件下载，share=公开分享授权下载。 */
    private String accessSource;

    /** 分享资源虚拟标识；share 来源时使用，避免向客户端回传真实 file_id。 */
    private String shareResourceId;

    /** 下载授权 Token */
    private String downloadGrant;

    /** 事件发生时间 */
    private LocalDateTime eventTime;
}
