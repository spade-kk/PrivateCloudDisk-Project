package org.project.service.impl;

import jakarta.annotation.Resource;
import org.project.model.entity.FileEntity;
import org.project.model.entity.FolderNodeEntity;
import org.project.mapper.FileMapper;
import org.project.mapper.FolderNodeMapper;
import org.project.service.FileService;
import org.project.service.ex.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class FileServiceImpl implements FileService {
    @Autowired
    private FileMapper fileMapper;
    @Autowired
    private FolderNodeMapper folderNodeMapper;
    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Override
    public String createFile(String file_name, String file_type, long file_size, String user_id, String node_id, String file_checksum, int total_chunks, String storage_path) {
        FolderNodeEntity node = folderNodeMapper.findFolderNodeById(node_id);
        if(node == null) {
            throw new NodeNotExistException("节点不存在");
        }
        assertNodeOwner(node, user_id);
        // 实现文件创建的逻辑
        FileEntity fileData = new FileEntity();
        fileData.setName(file_name);
        fileData.setType(file_type);
        fileData.setUser_id(user_id);
        fileData.setSize(file_size);
        fileData.setChecksum(file_checksum);
        fileData.setNode_id(node_id);
        fileData.setTotal_chunks(total_chunks);
        fileData.setStorage_path(storage_path);
        //设置上传时间
        fileData.setUploaded_time(LocalDateTime.now());
        // 生成文件的ID
        String file_id = UUID.randomUUID().toString();
        fileData.setId(file_id);
        // 调用Mapper插入数据
        Integer rows = fileMapper.insertFile(fileData);
        if(rows!= 1) {
            throw new InsertException();
        }

        return file_id;
    }

    @Override
    public List<FileEntity> queryUserFilesByNodeId(String node_id, String user_id) {
        // 检查节点是否存在
        FolderNodeEntity node = folderNodeMapper.findFolderNodeById(node_id);
        if(node == null) {
            throw new NodeNotExistException("节点不存在");
        }
        assertNodeOwner(node, user_id);

        return fileMapper.findUserFilesByNodeId(node_id, user_id);
    }

    @Override
    public FileEntity queryUserFileByNodeIdAndName(String node_id, String file_name, String user_id) {
        // 检查节点是否存在
        FolderNodeEntity node = folderNodeMapper.findFolderNodeById(node_id);
        if(node == null) {
            throw new NodeNotExistException("节点不存在");
        }
        assertNodeOwner(node, user_id);

        FileEntity fileData = fileMapper.findUserFileByNodeIdAndName(node_id, file_name, user_id);
        if(fileData == null || !fileData.getUser_id().equals(user_id)) {
            throw new FileNotExistException();
        }
        return fileData;
    }

    @Override
    public FileEntity queryUserFileById(String file_id, String user_id) {
        FileEntity fileData = fileMapper.findFileById(file_id);
        if(fileData == null || !fileData.getUser_id().equals(user_id)) {
            throw new FileNotExistException();
        }
        return fileData;
    }

    @Override
    public void updateFileName(String file_id, String file_new_name, String user_id) {
        // 检查文件是否存在
        FileEntity fileData = fileMapper.findFileById(file_id);
        if(fileData == null) {
            throw new FileNotExistException();
        }
        assertFileOwner(fileData, user_id);
        // 实现文件名称更新的逻辑
        Integer rows = fileMapper.updateUserFileNameById(file_id, file_new_name, user_id);
        if(rows!= 1) {
            throw new UpdateException("文件名称更新失败");
        }
    }

    @Override
    public void moveFileByFileId(String file_id, String target_node_id, String user_id) {
        // 检查文件是否存在
        FileEntity fileData = fileMapper.findFileById(file_id);
        if(fileData == null) {
            throw new FileNotExistException();
        }
        assertFileOwner(fileData, user_id);
        FolderNodeEntity targetNode = folderNodeMapper.findFolderNodeById(target_node_id);
        if(targetNode == null) {
            throw new NodeNotExistException("目标节点不存在");
        }
        assertNodeOwner(targetNode, user_id);
        // 实现文件移动的逻辑
        Integer rows = fileMapper.updateUserFileParentNodeIdById(file_id, target_node_id, user_id);
        if(rows!= 1) {
            throw new UpdateException("文件移动失败");
        }
    }

    @Override
    public void deleteFileByFileId(String file_id, String user_id) {
        // 检查文件是否存在
        FileEntity fileData = fileMapper.findFileById(file_id);
        if(fileData == null) {
            throw new FileNotExistException();
        }
        assertFileOwner(fileData, user_id);
        // 实现文件删除的逻辑
        Integer rows = fileMapper.deleteUserFileById(file_id, user_id);
        if(rows!= 1) {
            throw new DeleteException("文件删除失败");
        }
    }

    private void assertFileOwner(FileEntity fileData, String user_id) {
        if(user_id == null || !user_id.equals(fileData.getUser_id())) {
            throw new OverstepAuthorityException("您没有权限操作该文件");
        }
    }

    private void assertNodeOwner(FolderNodeEntity node, String user_id) {
        if(user_id == null || !user_id.equals(node.getUser_id())) {
            throw new OverstepAuthorityException("您没有权限操作该节点");
        }
    }
}
