package org.project.control;

import org.project.control.result.JsonResult;
import org.project.data.FolderNodeData;
import org.project.data.NodeData;
import org.project.service.DirectoryTreeService;
import org.project.service.UserService;
import org.project.service.ex.OverstepAuthorityException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/business/nodes")
public class NodeController extends BaseController {
    @Autowired
    private DirectoryTreeService directoryTreeService;
    @Autowired
    private UserService userService;

    @GetMapping("/root")
    public JsonResult<FolderNodeData> findRootNode(@RequestHeader("X-User-Id") String user_id) {
        FolderNodeData rootFolderNode = userService.findRootFolderNodeByUserId(user_id);
        // 隐藏节点状态和用户ID
        rootFolderNode.setStatus(null);
        rootFolderNode.setUser_id(null);

        return new JsonResult<FolderNodeData>(OK, rootFolderNode);
    }

    @GetMapping("/{node_id}/children")
    public JsonResult<List<NodeData>> findNodesByNodeId(@PathVariable String node_id,
                                                        @RequestHeader("X-User-Id") String user_id ) {
        FolderNodeData folderNodeData = directoryTreeService.queryFolderNodeById(node_id);
        if(user_id == null || !folderNodeData.getUser_id().equals(user_id)) {
            throw new OverstepAuthorityException("您没有权限查询该节点");
        }

        List<NodeData> nodeList = directoryTreeService.findUserNodesByNodeId(node_id, user_id);
        return new JsonResult<List<NodeData>>(OK, nodeList);
    }

    @PostMapping("/")
    public JsonResult<Void> createFolderNode(String folder_name, String node_id, @RequestHeader("X-User-Id") String user_id) {

        directoryTreeService.createFolderNode(
                user_id,
                node_id,
                folder_name
        );
        return new JsonResult<Void>(OK);
    }

    @DeleteMapping("/{node_id}/")
    public JsonResult<Void> deleteNodeByNodeId( @PathVariable String node_id,
                                        @RequestHeader("X-User-Id") String user_id ) {
        directoryTreeService.deleteNodeByNodeId(node_id, user_id);

        return new JsonResult<Void>(OK);
    }

    @PatchMapping("/{node_id}/position")
    public JsonResult<Void> moveNodeByNodeId( @PathVariable String node_id,
                                              String target_position,
                                              @RequestHeader("X-User-Id") String  user_id ) {
        directoryTreeService.moveNodeByNodeId(node_id, target_position, user_id);
        return new JsonResult<Void>(OK);
    }

    @PatchMapping("/{node_id}/name")
    public JsonResult<Void> updateNodeNameByNodeId( @PathVariable String node_id,
                                                    String new_node_name,
                                                    @RequestHeader("X-User-Id") String  user_id ) {
        directoryTreeService.updateNodeNameByNodeId(node_id, new_node_name, user_id);
        return new JsonResult<>(OK);
    }
}
