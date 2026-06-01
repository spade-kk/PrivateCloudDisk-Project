package org.project.service.impl;

import org.project.data.FileData;
import org.project.data.FolderNodeData;
import org.project.data.NodeData;
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
        FolderNodeData folderNodeData = new FolderNodeData();
        folderNodeData.setUser_id(user_id);
        folderNodeData.setStatus(FolderNodeData.NodeStatus.active);
        folderNodeData.setCreate_time(LocalDateTime.now().toString());
        folderNodeData.setName(name);
        folderNodeData.setNode_id(UUID.randomUUID().toString());
        folderNodeData.setParent_id(null);

        if(parent_id != null) {
            FolderNodeData parentNode = folderNodeMapper.findFolderNodeById(parent_id);
            if(parentNode == null) {
                throw new ParentNodeNotExistException("父节点不存在");
            }
            //检查父节点与子节点一致性
            if(!parentNode.getUser_id().equals(user_id)) {
                throw new NodeUserNotMatchException("父节点不是当前用户创建");
            }
            folderNodeData.setParent_id(parent_id);
            // 待处理状态
            folderNodeData.setStatus(FolderNodeData.NodeStatus.pending);
            // 锁定父节点
            folderNodeMapper.updateFolderNodeStatusById(
                    FolderNodeData.NodeStatus.lock,
                    parent_id
            );
        }
        folderNodeMapper.insertFolderNode(folderNodeData);
    }

    @Override
    public FolderNodeData queryFolderNodeById(String node_id) {
        return folderNodeMapper.findFolderNodeById(node_id);
    }

    @Override
    public void activeFolderNode(String node_id) {
        // 检查节点是否存在
        FolderNodeData node = folderNodeMapper.findFolderNodeById(node_id);
        if(node == null) {
            throw new NodeNotExistException("节点不存在");
        }
        // 检查节点状态
        if(node.getStatus() != FolderNodeData.NodeStatus.pending) {
            throw new NodeStatusException("节点状态错误");
        }

        folderNodeMapper.updateFolderNodeStatusById(
                FolderNodeData.NodeStatus.active,
                node_id
        );
        // 解锁父节点
        folderNodeMapper.updateFolderNodeStatusById(
                FolderNodeData.NodeStatus.active,
                node.getParent_id()
        );
    }

    @Override
    public void deleteFolderNode(String node_id) {
        ;
    }

    @Override
    public List<NodeData> findUserNodesByNodeId(String node_id, String user_id) {
        // 检查节点是否存在
        FolderNodeData node = folderNodeMapper.findFolderNodeById(node_id);
        if(node == null) {
            throw new NodeNotExistException("节点不存在");
        }

        List<NodeData> nodeList = new ArrayList<>();
        List<FileData> fileDataList =fileMapper.findUserFilesByNodeId(node_id, user_id);
        List<FolderNodeData> folderNodeDataList = folderNodeMapper.findFolderNodesById(node_id);

        for(FolderNodeData folderNodeData : folderNodeDataList) {
            NodeData nodeData = new NodeData();
            nodeData.setNode_id(folderNodeData.getNode_id());
            nodeData.setNode_name(folderNodeData.getName());
            nodeData.setNode_type(NodeData.NodeType.FOLDER);
            nodeList.add(nodeData);
        }
        for(FileData fileData : fileDataList) {
            NodeData nodeData = new NodeData();
            nodeData.setNode_id(fileData.getNode_id());
            nodeData.setNode_name(fileData.getName());
            nodeData.setNode_type(NodeData.NodeType.FILE);
            nodeData.setNode_size(fileData.getSize());
            nodeList.add(nodeData);
        }

        return nodeList;
    }
    @Override
    public void moveNodeByNodeId(String node_id, String target_position, String user_id) {
        // 检查节点是否存在
        FolderNodeData node = folderNodeMapper.findFolderNodeById(node_id);
        if(node == null) {
            throw new NodeNotExistException("节点不存在");
        }
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
        FolderNodeData node = folderNodeMapper.findFolderNodeById(node_id);
        if(node == null) {
            throw new NodeNotExistException("节点不存在");
        }
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
        FolderNodeData node = folderNodeMapper.findFolderNodeById(node_id);
        if(node == null) {
            throw new NodeNotExistException("节点不存在");
        }
        Integer rows =  folderNodeMapper.deleteFolderNodeById(node_id);
        if(rows != 1) {
            throw new UpdateException("文件夹删除失败");
        }
    }
}
