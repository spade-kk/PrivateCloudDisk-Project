package com.privateclouddisk.android.data.model;

import com.google.gson.annotations.SerializedName;

/**
 * 创建上传会话请求
 */
public class CreateUploadSessionRequest {

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