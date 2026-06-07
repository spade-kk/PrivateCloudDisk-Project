package org.project.service.impl;

import org.project.model.entity.FileEntity;
import org.project.model.entity.FolderNodeEntity;
import org.project.model.entity.NodeEntity;
import org.project.mapper.FileMapper;
import org.project.mapper.FolderNodeMapper;
import org.project.service.DirectoryTreeService;
import org.project.service.ex.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class DirectoryTreeServiceImpl implements DirectoryTreeService {
    @Autowired
    private FolderNodeMapper folderNodeMapper;
    @Autowired
    private FileMapper fileMapper;

    @Override
    public void createFolderNode(String user_id, String parent_id, String name) {
        FolderNodeEntity folderNodeEntity = new FolderNodeEntity();
        folderNodeEntity.setUser_id(user_id);
        folderNodeEntity.setStatus(FolderNodeEntity.NodeStatus.active);
        folderNodeEntity.setCreate_time(LocalDateTime.now().toString());
        folderNodeEntity.setName(name);
        folderNodeEntity.setNode_id(UUID.randomUUID().toString());
        folderNodeEntity.setParent_id(null);

        if(parent_id != null) {
            FolderNodeEntity parentNode = folderNodeMapper.findFolderNodeById(parent_id);
            if(parentNode == null) {
                throw new ParentNodeNotExistException("父节点不存在");
            }
            //检查父节点与子节点一致性
            if(!parentNode.getUser_id().equals(user_id)) {
                throw new NodeUserNotMatchException("父节点不是当前用户创建");
            }
            folderNodeEntity.setParent_id(parent_id);
            // 待处理状态
            folderNodeEntity.setStatus(FolderNodeEntity.NodeStatus.pending);
            // 锁定父节点
            folderNodeMapper.updateFolderNodeStatusById(
                    FolderNodeEntity.NodeStatus.lock,
                    parent_id
            );
        }
        folderNodeMapper.insertFolderNode(folderNodeEntity);
    }

    @Override
    public FolderNodeEntity queryFolderNodeById(String node_id) {
        return folderNodeMapper.findFolderNodeById(node_id);
    }

    @Override
    public void activeFolderNode(String node_id) {
        // 检查节点是否存在
        FolderNodeEntity node = folderNodeMapper.findFolderNodeById(node_id);
        if(node == null) {
            throw new NodeNotExistException("节点不存在");
        }
        // 检查节点状态
        if(node.getStatus() != FolderNodeEntity.NodeStatus.pending) {
            throw new NodeStatusException("节点状态错误");
        }

        folderNodeMapper.updateFolderNodeStatusById(
                FolderNodeEntity.NodeStatus.active,
                node_id
        );
        // 解锁父节点
        folderNodeMapper.updateFolderNodeStatusById(
                FolderNodeEntity.NodeStatus.active,
                node.getParent_id()
        );
    }

    @Override
    public void deleteFolderNode(String node_id) {
        ;
    }

    @Override
    public List<NodeEntity> findUserNodesByNodeId(String node_id, String user_id) {
        // 检查节点是否存在
        FolderNodeEntity node = folderNodeMapper.findFolderNodeById(node_id);
        if(node == null) {
            throw new NodeNotExistException("节点不存在");
        }
        assertNodeOwner(node, user_id);

        List<NodeEntity> nodeList = new ArrayList<>();
        List<FileEntity> fileDataList =fileMapper.findUserFilesByNodeId(node_id, user_id);
        List<FolderNodeEntity> folderNodeEntityList = folderNodeMapper.findFolderNodesById(node_id);

        for(FolderNodeEntity folderNodeEntity : folderNodeEntityList) {
            NodeEntity nodeData = new NodeEntity();
            nodeData.setNode_id(folderNodeEntity.getNode_id());
            nodeData.setNode_name(folderNodeEntity.getName());
            nodeData.setNode_type(NodeEntity.NodeType.FOLDER);
            nodeList.add(nodeData);
        }
        for(FileEntity fileData : fileDataList) {
            NodeEntity nodeData = new NodeEntity();
            nodeData.setNode_id(fileData.getNode_id());
            nodeData.setNode_name(fileData.getName());
            nodeData.setNode_type(NodeEntity.NodeType.FILE);
            nodeData.setNode_size(fileData.getSize());
            nodeList.add(nodeData);
        }

        return nodeList;
    }
    @Override
    public void moveNodeByNodeId(String node_id, String target_position, String user_id) {
        // 检查节点是否存在
        FolderNodeEntity node = folderNodeMapper.findFolderNodeById(node_id);
        if(node == null) {
            throw new NodeNotExistException("节点不存在");
        }
        assertNodeOwner(node, user_id);
        FolderNodeEntity targetNode = folderNodeMapper.findFolderNodeById(target_position);
        if(targetNode == null) {
            throw new ParentNodeNotExistException("目标父节点不存在");
        }
        assertNodeOwner(targetNode, user_id);
        Integer rows = folderNodeMapper.updateFolderNodeParentIdById(
                target_position,
                node_id );
        if(rows != 1) {
            throw new UpdateException("文件夹移动失败");
        }
    }
    @Override
    public void updateNodeNameByNodeId(String node_id, String new_node_name, String user_id) {
        // 检查节点是否存在
        FolderNodeEntity node = folderNodeMapper.findFolderNodeById(node_id);
        if(node == null) {
            throw new NodeNotExistException("节点不存在");
        }
        assertNodeOwner(node, user_id);
        Integer rows =  folderNodeMapper.updateFolderNodeNameById(
                new_node_name,
                node_id );
        if(rows != 1) {
            throw new UpdateException("文件夹重命名失败");
        }
    }
    @Override
    public void deleteNodeByNodeId(String node_id, String user_id) {
        // 检查节点是否存在
        FolderNodeEntity node = folderNodeMapper.findFolderNodeById(node_id);
        if(node == null) {
            throw new NodeNotExistException("节点不存在");
        }
        assertNodeOwner(node, user_id);
        Integer rows =  folderNodeMapper.deleteFolderNodeById(node_id);
        if(rows != 1) {
            throw new UpdateException("文件夹删除失败");
        }
    }

    private void assertNodeOwner(FolderNodeEntity node, String user_id) {
        if(user_id == null || !user_id.equals(node.getUser_id())) {
            throw new OverstepAuthorityException("您没有权限操作该节点");
        }
    }
}
