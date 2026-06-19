package com.privateclouddisk.android.data.model;

import com.google.gson.annotations.SerializedName;

/**
 * 创建上传会话请求
 */
class CreateUploadSessionRequest {

    @SerializedName("total_chunks")
    private int totalChunks;

    @SerializedName("file_size")
    private long fileSize;

    @SerializedName("file_checksum")
    private String fileChecksum;

    @SerializedName("chunks_max_size")
    private int chunksMaxSize;

    @SerializedName("file_name")
    private String fileName;

    @SerializedName("file_type")
    private String fileType;

    @SerializedName("node_id")
    private String nodeId;

    public CreateUploadSessionRequest(String fileName, long fileSize, String fileType,
                                       String fileChecksum, String nodeId,
                                       int totalChunks, int chunksMaxSize) {
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.fileType = fileType;
        this.fileChecksum = fileChecksum;
        this.nodeId = nodeId;
        this.totalChunks = totalChunks;
        this.chunksMaxSize = chunksMaxSize;
    }

    public String getFileName() { return fileName; }
    public long getFileSize() { return fileSize; }
    public String getFileType() { return fileType; }
    public String getFileChecksum() { return fileChecksum; }
    public String getNodeId() { return nodeId; }
    public int getTotalChunks() { return totalChunks; }
    public int getChunksMaxSize() { return chunksMaxSize; }
}

/**
 * 上传会话响应
 */
class UploadSessionResponse {

    @SerializedName("uploads_id")
    private String uploadsId;

    public String getUploadsId() { return uploadsId; }
}

/**
 * 操作凭证请求
 */
class OperationTokenRequest {

    @SerializedName("file_id")
    private String fileId;

    @SerializedName("operation_type")
    private String operationType;

    public OperationTokenRequest(String fileId, String operationType) {
        this.fileId = fileId;
        this.operationType = operationType;
    }
}

/**
 * 操作凭证响应
 */
class OperationTokenResponse {

    @SerializedName("operation_token")
    private String operationToken;

    public String getOperationToken() { return operationToken; }
}

/**
 * 完成上传请求
 */
class CompleteUploadRequest {

    @SerializedName("upload_id")
    private String uploadId;

    @SerializedName("file_name")
    private String fileName;

    @SerializedName("parent_id")
    private String parentId;

    public CompleteUploadRequest(String uploadId, String fileName, String parentId) {
        this.uploadId = uploadId;
        this.fileName = fileName;
        this.parentId = parentId;
    }
}

/**
 * 创建文件夹请求
 */
class CreateFolderRequest {

    @SerializedName("parent_id")
    private String parentId;

    @SerializedName("folder_name")
    private String folderName;

    public CreateFolderRequest(String parentId, String folderName) {
        this.parentId = parentId;
        this.folderName = folderName;
    }
}

/**
 * 重命名请求
 */
class RenameRequest {

    @SerializedName("new_name")
    private String newName;

    public RenameRequest(String newName) {
        this.newName = newName;
    }
}

/**
 * 移动文件请求
 */
class MoveFileRequest {

    @SerializedName("target_parent_id")
    private String targetParentId;

    public MoveFileRequest(String targetParentId) {
        this.targetParentId = targetParentId;
    }
}

/**
 * 批量操作请求
 */
class BatchOperationRequest {

    @SerializedName("ids")
    private java.util.List<String> ids;

    public BatchOperationRequest(java.util.List<String> ids) {
        this.ids = ids;
    }
}

/**
 * 搜索请求
 */
class SearchRequest {

    @SerializedName("keyword")
    private String keyword;

    @SerializedName("file_type")
    private String fileType;

    @SerializedName("page")
    private int page;

    @SerializedName("page_size")
    private int pageSize;

    public SearchRequest(String keyword, String fileType, int page, int pageSize) {
        this.keyword = keyword;
        this.fileType = fileType;
        this.page = page;
        this.pageSize = pageSize;
    }
}