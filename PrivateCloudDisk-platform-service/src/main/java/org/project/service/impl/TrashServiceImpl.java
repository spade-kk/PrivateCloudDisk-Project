package org.project.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.config.RabbitMQConifgure;
import org.project.mapper.FileMapper;
import org.project.mapper.TrashFileMapper;
import org.project.model.dto.message.FileDeleteMessageDTO;
import org.project.model.entity.FileEntity;
import org.project.model.entity.TrashFileEntity;
import org.project.service.TrashService;
import org.project.service.ex.DeleteException;
import org.project.service.ex.FileNotExistException;
import org.project.service.ex.InsertException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 回收站服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrashServiceImpl implements TrashService {
    
    private final TrashFileMapper trashFileMapper;
    private final FileMapper fileMapper;
    private final RabbitTemplate rabbitTemplate;
    
    @Override
    public void moveToTrash(String file_id, String user_id) {
        // 查询文件信息
        FileEntity file = fileMapper.findUserFileById(file_id, user_id);
        if (file == null) {
            throw new FileNotExistException();
        }
        
        // 创建回收站记录
        TrashFileEntity trashFile = new TrashFileEntity();
        trashFile.setFile_id(file_id);
        trashFile.setUser_id(user_id);
        trashFile.setFile_name(file.getName());
        trashFile.setFile_type(file.getType());
        trashFile.setFile_size(file.getSize());
        trashFile.setOriginal_node_id(file.getNode_id());
        trashFile.setStorage_path(file.getStorage_path());
        trashFile.setFile_checksum(file.getChecksum());
        trashFile.setDeleted_at(LocalDateTime.now());
        trashFile.setExpires_at(LocalDateTime.now().plusDays(30)); // 30天后自动删除
        
        int rows = trashFileMapper.insertTrashFile(trashFile);
        if (rows != 1) {
            throw new InsertException("移动文件到回收站失败");
        }
        
        // 删除原文件记录
        fileMapper.deleteUserFileById(file_id, user_id);
        
        log.info("文件已移动到回收站: userId={}, fileId={}", user_id, file_id);
    }
    
    @Override
    public void restoreFromTrash(Long trash_id, String user_id) {
        // 查询回收站记录
        TrashFileEntity trashFile = trashFileMapper.findTrashFileById(trash_id, user_id);
        if (trashFile == null) {
            throw new FileNotExistException();
        }
        
        // 恢复文件记录
        FileEntity file = new FileEntity();
        file.setId(trashFile.getFile_id());
        file.setName(trashFile.getFile_name());
        file.setType(trashFile.getFile_type());
        file.setSize(trashFile.getFile_size());
        file.setNode_id(trashFile.getOriginal_node_id());
        file.setStorage_path(trashFile.getStorage_path());
        file.setChecksum(trashFile.getFile_checksum());
        file.setUser_id(user_id);
        file.setUploaded_time(LocalDateTime.now());
        
        int rows = fileMapper.insertFile(file);
        if (rows != 1) {
            throw new InsertException("恢复文件失败");
        }
        
        // 删除回收站记录
        trashFileMapper.deleteTrashFile(trash_id, user_id);
        
        log.info("文件已从回收站恢复: userId={}, fileId={}", user_id, trashFile.getFile_id());
    }
    
    @Override
    public void permanentDelete(Long trash_id, String user_id) {
        // 查询回收站记录
        TrashFileEntity trashFile = trashFileMapper.findTrashFileById(trash_id, user_id);
        if (trashFile == null) {
            throw new FileNotExistException();
        }
        
        // 删除回收站记录
        int rows = trashFileMapper.deleteTrashFile(trash_id, user_id);
        if (rows != 1) {
            throw new DeleteException("删除回收站记录失败");
        }
        
        // 发送异步删除消息
        FileDeleteMessageDTO message = FileDeleteMessageDTO.builder()
                .messageId(UUID.randomUUID().toString())
                .fileId(trashFile.getFile_id())
                .userId(user_id)
                .storagePath(trashFile.getStorage_path())
                .fileSize(trashFile.getFile_size())
                .fromTrash(true)
                .createdAt(LocalDateTime.now())
                .build();
        
        rabbitTemplate.convertAndSend(
                RabbitMQConifgure.FILE_DELETE_EXCHANGE,
                RabbitMQConifgure.FILE_DELETE_ROUTING_KEY,
                message);
        
        log.info("文件已彻底删除: userId={}, fileId={}", user_id, trashFile.getFile_id());
    }
    
    @Override
    public void emptyTrash(String user_id) {
        List<TrashFileEntity> trashFiles = trashFileMapper.findTrashFilesByUserId(user_id, 0, Integer.MAX_VALUE);
        
        for (TrashFileEntity trashFile : trashFiles) {
            permanentDelete(trashFile.getTrash_id(), user_id);
        }
        
        log.info("回收站已清空: userId={}", user_id);
    }
    
    @Override
    public List<TrashFileEntity> getTrashFiles(String user_id, Integer page, Integer pageSize) {
        int offset = (page - 1) * pageSize;
        return trashFileMapper.findTrashFilesByUserId(user_id, offset, pageSize);
    }
    
    @Override
    public Integer countTrashFiles(String user_id) {
        return trashFileMapper.countTrashFilesByUserId(user_id);
    }
    
    @Override
    public TrashFileEntity getTrashFileById(Long trash_id, String user_id) {
        TrashFileEntity trashFile = trashFileMapper.findTrashFileById(trash_id, user_id);
        if (trashFile == null) {
            throw new FileNotExistException();
        }
        return trashFile;
    }
}
