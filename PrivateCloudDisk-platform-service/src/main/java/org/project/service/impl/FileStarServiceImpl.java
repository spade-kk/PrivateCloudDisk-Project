package org.project.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.mapper.FileStarMapper;
import org.project.model.entity.FileStarEntity;
import org.project.service.FileStarService;
import org.project.service.ex.FileNotExistException;
import org.project.service.ex.InsertException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文件收藏服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileStarServiceImpl implements FileStarService {
    
    private final FileStarMapper fileStarMapper;
    
    @Override
    public void addFileStar(String user_id, String file_id) {
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
    public void removeFileStar(String user_id, String file_id) {
        int rows = fileStarMapper.deleteFileStar(user_id, file_id);
        if (rows == 0) {
            log.warn("取消收藏失败，收藏不存在: userId={}, fileId={}", user_id, file_id);
        } else {
            log.info("取消文件收藏成功: userId={}, fileId={}", user_id, file_id);
        }
    }
    
    @Override
    public boolean isFileStarred(String user_id, String file_id) {
        FileStarEntity existing = fileStarMapper.findFileStarByUserIdAndFileId(user_id, file_id);
        return existing != null;
    }
    
    @Override
    public List<String> getStarredFileIds(String user_id) {
        return fileStarMapper.findStarredFileIdsByUserId(user_id);
    }
    
    @Override
    public List<FileStarEntity> getStarredFiles(String user_id, Integer page, Integer pageSize) {
        int offset = (page - 1) * pageSize;
        return fileStarMapper.findStarredFilesByUserId(user_id, offset, pageSize);
    }
    
    @Override
    public Integer countStarredFiles(String user_id) {
        return fileStarMapper.countStarredFilesByUserId(user_id);
    }
}
