package com.privateclouddisk.android.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 上传任务实体
 * 持久化上传任务，支持应用重启后恢复
 */
@Entity(tableName = "upload_tasks")
public class UploadTaskEntity {

    @PrimaryKey
    @NonNull
    private String uploadId;

    private String fileName;
    private String localPath;
    private String parentId;
    private long totalBytes;
    private long uploadedBytes;
    private int totalChunks;
    private int uploadedChunks;
    private int status;         // 0: pending, 1: uploading, 2: paused, 3: completed, 4: failed
    private String fileChecksum;
    private String fileType;
    private long createdAt;
    private long updatedAt;

    // ── 状态常量 ──
    public static final int STATUS_PENDING = 0;
    public static final int STATUS_UPLOADING = 1;
    public static final int STATUS_PAUSED = 2;
    public static final int STATUS_COMPLETED = 3;
    public static final int STATUS_FAILED = 4;

    // ── Getters and Setters ──

    @NonNull
    public String getUploadId() { return uploadId; }
    public void setUploadId(@NonNull String uploadId) { this.uploadId = uploadId; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getLocalPath() { return localPath; }
    public void setLocalPath(String localPath) { this.localPath = localPath; }

    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; }

    public long getTotalBytes() { return totalBytes; }
    public void setTotalBytes(long totalBytes) { this.totalBytes = totalBytes; }

    public long getUploadedBytes() { return uploadedBytes; }
    public void setUploadedBytes(long uploadedBytes) { this.uploadedBytes = uploadedBytes; }

    public int getTotalChunks() { return totalChunks; }
    public void setTotalChunks(int totalChunks) { this.totalChunks = totalChunks; }

    public int getUploadedChunks() { return uploadedChunks; }
    public void setUploadedChunks(int uploadedChunks) { this.uploadedChunks = uploadedChunks; }

    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }

    public String getFileChecksum() { return fileChecksum; }
    public void setFileChecksum(String fileChecksum) { this.fileChecksum = fileChecksum; }

    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    /** 获取进度百分比 */
    public int getProgress() {
        if (totalBytes == 0) return 0;
        return (int) (uploadedBytes * 100 / totalBytes);
    }
}