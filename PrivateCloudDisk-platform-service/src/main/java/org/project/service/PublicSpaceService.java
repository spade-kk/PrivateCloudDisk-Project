package org.project.service;

import org.project.model.vo.PublicSpaceDetailVO;
import org.project.model.vo.PublicSpaceNodeVO;
import org.project.model.vo.PublicUserProfileVO;

import java.util.List;
import java.util.UUID;

/**
 * 公开仓库业务边界。
 * 与 SpaceService 的成员空间逻辑隔离，所有查询显式带 space_id，防止跨空间读取。
 */
public interface PublicSpaceService {
    PublicSpaceDetailVO getRepository(UUID spaceId, UUID visitorId);
    /** [REQ-GIT-SPACE-12.4] 文件上传入口调用，阻止 Git 资源误走文件会话。 */
    void requireFileResource(UUID spaceId, UUID visitorId);
    PublicSpaceNodeVO getRoot(UUID spaceId, UUID visitorId);
    List<PublicSpaceNodeVO> getChildren(UUID spaceId, UUID nodeId, UUID visitorId);
    String getReadme(UUID spaceId, UUID visitorId);
    PublicSpaceDetailVO updateRepository(UUID spaceId, UUID ownerId, String name, String description,
                                         Boolean allowBrowse, Boolean allowDownload, Boolean allowUpload);
    PublicUserProfileVO getUserProfile(String username, UUID visitorId);
    List<PublicSpaceDetailVO> explore(String keyword, UUID visitorId);
}
