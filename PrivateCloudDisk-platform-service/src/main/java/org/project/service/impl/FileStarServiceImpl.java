package org.project.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.mapper.FileStarMapper;
import org.project.model.entity.FileStarEntity;
import org.project.service.FileStarService;
import org.project.service.ex.FileNotExistException;
import org.project.service.ex.InsertException;
import org.project.service.ex.NodeNotExistException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 文件/文件夹收藏服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileStarServiceImpl implements FileStarService {

    private final FileStarMapper fileStarMapper;

    // ═══════════════════════════════════════════════
    // 文件收藏
    // ═══════════════════════════════════════════════

    @Override
    public void addFileStar(UUID user_id, UUID file_id) {
        // 检查是否已收藏
        FileStarEntity existing = fileStarMapper.findFileStarByUserIdAndFileId(user_id, file_id);
        if (existing != null) {
            log.info("文件已被收藏: userId={}, fileId={}", user_id, file_id);
            return;
        }

        FileStarEntity fileStar = new FileStarEntity();
        fileStar.setUser_id(user_id);
        fileStar.setFile_id(file_id);
        fileStar.setStarred_at(LocalDateTime.now());

        int rows = fileStarMapper.insertFileStar(fileStar);
        if (rows != 1) {
            throw new InsertException("添加文件收藏失败");
        }
        log.info("文件收藏成功: userId={}, fileId={}", user_id, file_id);
    }

    @Override
    public void removeFileStar(UUID user_id, UUID file_id) {
        int rows = fileStarMapper.deleteFileStar(user_id, file_id);
        if (rows == 0) {
            log.warn("取消文件收藏失败，收藏不存在: userId={}, fileId={}", user_id, file_id);
        } else {
            log.info("取消文件收藏成功: userId={}, fileId={}", user_id, file_id);
        }
    }

    @Override
    public boolean isFileStarred(UUID user_id, UUID file_id) {
        return fileStarMapper.findFileStarByUserIdAndFileId(user_id, file_id) != null;
    }

    // ═══════════════════════════════════════════════
    // 文件夹收藏
    // ═══════════════════════════════════════════════

    @Override
    public void addFolderStar(UUID user_id, UUID node_id) {
        FileStarEntity existing = fileStarMapper.findFolderStarByUserIdAndNodeId(user_id, node_id);
        if (existing != null) {
            log.info("文件夹已被收藏: userId={}, nodeId={}", user_id, node_id);
            return;
        }

        FileStarEntity fileStar = new FileStarEntity();
        fileStar.setUser_id(user_id);
        fileStar.setNode_id(node_id);
        fileStar.setStarred_at(LocalDateTime.now());

        int rows = fileStarMapper.insertFolderStar(fileStar);
        if (rows != 1) {
            throw new InsertException("添加文件夹收藏失败");
        }
        log.info("文件夹收藏成功: userId={}, nodeId={}", user_id, node_id);
    }

    @Override
    public void removeFolderStar(UUID user_id, UUID node_id) {
        int rows = fileStarMapper.deleteFolderStar(user_id, node_id);
        if (rows == 0) {
            log.warn("取消文件夹收藏失败，收藏不存在: userId={}, nodeId={}", user_id, node_id);
        } else {
            log.info("取消文件夹收藏成功: userId={}, nodeId={}", user_id, node_id);
        }
    }

    @Override
    public boolean isFolderStarred(UUID user_id, UUID node_id) {
        return fileStarMapper.findFolderStarByUserIdAndNodeId(user_id, node_id) != null;
    }

    // ═══════════════════════════════════════════════
    // 通用查询
    // ═══════════════════════════════════════════════

    @Override
    public List<String> getStarredFileIds(UUID user_id) {
        return fileStarMapper.findStarredFileIdsByUserId(user_id);
    }

    @Override
    public List<String> getStarredNodeIds(UUID user_id) {
        return fileStarMapper.findStarredNodeIdsByUserId(user_id);
    }

    @Override
    public List<FileStarEntity> getStarredItems(UUID user_id, Integer page, Integer pageSize) {
        int offset = (page - 1) * pageSize;
        return fileStarMapper.findStarredItemsByUserId(user_id, offset, pageSize);
    }

    @Override
    public Integer countStarredItems(UUID user_id) {
        return fileStarMapper.countStarredItemsByUserId(user_id);
    }
}