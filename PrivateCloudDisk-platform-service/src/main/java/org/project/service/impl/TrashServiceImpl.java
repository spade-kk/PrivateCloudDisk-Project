package org.project.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.config.RabbitMQConifgure;
import org.project.mapper.FileMapper;
import org.project.mapper.TrashFileMapper;
import org.project.model.dto.message.FileDeleteMessageDTO;
import org.project.model.entity.FileEntity;
import org.project.model.entity.TrashTargetEntity;
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
    public void moveToTrash(UUID file_id, UUID user_id, String target_type) {
        // 查询文件信息
        FileEntity file = fileMapper.findUserFileById(file_id, user_id);
        if (file == null) {
            throw new FileNotExistException();
        }
        
        // 创建回收站记录
        TrashTargetEntity trashFile = new TrashTargetEntity();
        trashFile.setTarget_id(file_id);
        trashFile.setUser_id(user_id);
        trashFile.setTarget_name(file.getName());
        trashFile.setTarget_type(TrashTargetEntity.TargetType.valueOf(target_type));
        trashFile.setTarget_size(file.getSize());
        trashFile.setOriginal_node_id(file.getNode_id());
        trashFile.setDeleted_at(LocalDateTime.now());
        trashFile.setExpires_at(LocalDateTime.now().plusDays(30)); // 30天后自动删除
        
        int rows = trashFileMapper.insertTrashTarget(trashFile);
        if (rows != 1) {
            throw new InsertException("移动目标到回收站失败");
        }
        
        // 删除原文件记录
        fileMapper.deleteUserFileById(file_id, user_id);
        
        log.info("目标已移动到回收站: userId={}, targetId={}, targetType={}",
                user_id, trashFile.getTarget_id(), trashFile.getTarget_type());
    }
    
    @Override
    public void restoreFromTrash(Long trash_id, UUID user_id) {
        // 查询回收站记录
        TrashTargetEntity trashFile = trashFileMapper.findTrashTargetById(trash_id, user_id);
        if (trashFile == null) {
            throw new FileNotExistException();
        }
        
        // 恢复文件记录
        FileEntity file = new FileEntity();
        file.setId(trashFile.getTarget_id());
        file.setName(trashFile.getTarget_name());
        file.setType(trashFile.getFile_type());
        file.setSize(trashFile.getTarget_size());
        file.setNode_id(trashFile.getOriginal_node_id());
        file.setUser_id(user_id);
        file.setUploaded_time(LocalDateTime.now());
        
        int rows = fileMapper.insertFile(file);
        if (rows != 1) {
            throw new InsertException("恢复文件失败");
        }
        
        // 删除回收站记录
        trashFileMapper.deleteTrashTarget(trash_id, user_id);

        log.info("目标已从回收站恢复: userId={}, targetId={}, targetType={}",
                user_id, trashFile.getTarget_id(), trashFile.getTarget_type());
    }
    
    @Override
    public void permanentDelete(Long trash_id, UUID user_id) {
        // 查询回收站记录
        TrashTargetEntity trashFile = trashFileMapper.findTrashTargetById(trash_id, user_id);
        if (trashFile == null) {
            throw new FileNotExistException();
        }
        
        // 删除回收站记录
        int rows = trashFileMapper.deleteTrashTarget(trash_id, user_id);
        if (rows != 1) {
            throw new DeleteException("删除回收站记录失败");
        }
        
        // 发送异步删除消息
        FileDeleteMessageDTO message = FileDeleteMessageDTO.builder()
                .messageId(UUID.randomUUID().toString())
                .fileId(trashFile.getTarget_id().toString())
                .userId(user_id.toString())
                .fileSize(trashFile.getTarget_size())
                .fromTrash(true)
                .createdAt(LocalDateTime.now())
                .build();
        
        rabbitTemplate.convertAndSend(
                RabbitMQConifgure.FILE_DELETE_EXCHANGE,
                RabbitMQConifgure.FILE_DELETE_ROUTING_KEY,
                message);
        
        log.info("目标已彻底删除: userId={}, targetId={}, targetType={}",
                user_id, trashFile.getTarget_id(), trashFile.getTarget_type());
    }
    
    @Override
    public void emptyTrash(UUID user_id) {
        List<TrashTargetEntity> trashFiles = trashFileMapper.findTrashTargetsByUserId(user_id, 0, Integer.MAX_VALUE);
        
        for (TrashTargetEntity trashFile : trashFiles) {
            permanentDelete(trashFile.getTrash_id(), user_id);
        }
        
        log.info("回收站已清空: userId={}", user_id);
    }
    
    @Override
    public List<TrashTargetEntity> getTrashFiles(UUID user_id, Integer page, Integer pageSize) {
        int offset = (page - 1) * pageSize;
        return trashFileMapper.findTrashTargetsByUserId(user_id, offset, pageSize);
    }
    
    @Override
    public Integer countTrashFiles(UUID user_id) {
        return trashFileMapper.countTrashTargetsByUserId(user_id);
    }
    
    @Override
    public TrashTargetEntity getTrashFileById(Long trash_id, UUID user_id) {
        TrashTargetEntity trashFile = trashFileMapper.findTrashTargetById(trash_id, user_id);
        if (trashFile == null) {
            throw new FileNotExistException();
        }
        return trashFile;
    }
}
