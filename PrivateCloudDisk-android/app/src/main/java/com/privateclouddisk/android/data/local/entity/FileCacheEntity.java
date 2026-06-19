package com.privateclouddisk.android.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 文件缓存实体
 * 用于离线浏览和快速访问
 */
@Entity(tableName = "file_cache")
public class FileCacheEntity {

    @PrimaryKey
    @NonNull
    private String id;

    private String nodeId;
    private String fileId;
    private String name;
    private String parentId;
    private long size;
    private String type;
    private boolean isFile;
    private boolean isFavorite;
    private String fileType;
    private String uploadedTime;
    private String updatedTime;
    private String localPath;       // 本地缓存路径
    private long cachedAt;          // 缓存时间戳
    private boolean isSynced;       // 是否已同步到云端

    // ── Getters and Setters ──

    @NonNull
    public String getId() { return id; }
    public void setId(@NonNull String id) { this.id = id; }

    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }

    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; }

    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public boolean isFile() { return isFile; }
    public void setFile(boolean file) { isFile = file; }

    public boolean isFavorite() { return isFavorite; }
    public void setFavorite(boolean favorite) { isFavorite = favorite; }

    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }

    public String getUploadedTime() { return uploadedTime; }
    public void setUploadedTime(String uploadedTime) { this.uploadedTime = uploadedTime; }

    public String getUpdatedTime() { return updatedTime; }
    public void setUpdatedTime(String updatedTime) { this.updatedTime = updatedTime; }

    public String getLocalPath() { return localPath; }
    public void setLocalPath(String localPath) { this.localPath = localPath; }

    public long getCachedAt() { return cachedAt; }
    public void setCachedAt(long cachedAt) { this.cachedAt = cachedAt; }

    public boolean isSynced() { return isSynced; }
    public void setSynced(boolean synced) { isSynced = synced; }
}