package com.privateclouddisk.android.data.model;

import com.google.gson.annotations.SerializedName;

/**
 * 文件/文件夹节点（对应后端 FileVO / NodeVO）
 */
public class NodeItem {

    @SerializedName("id")
    private String id;

    @SerializedName("node_id")
    private String nodeId;

    @SerializedName("file_id")
    private String fileId;

    @SerializedName("name")
    private String name;

    @SerializedName("node_name")
    private String nodeName;

    @SerializedName("file_name")
    private String fileName;

    @SerializedName("size")
    private long size;

    @SerializedName("node_size")
    private Long nodeSize;

    @SerializedName("file_size")
    private Long fileSize;

    @SerializedName("type")
    private String type;

    @SerializedName("node_type")
    private String nodeType;

    @SerializedName("isFile")
    private boolean isFile;

    @SerializedName("file_type")
    private String fileType;

    @SerializedName("is_folder")
    private Boolean isFolder;

    @SerializedName("uploaded_time")
    private String uploadedTime;

    @SerializedName("created_time")
    private String createdTime;

    @SerializedName("updated_time")
    private String updatedTime;

    // ── 收藏/回收站 扩展字段 ──
    @SerializedName("is_favorite")
    private boolean isFavorite;

    @SerializedName("favorite_id")
    private String favoriteId;

    @SerializedName("deleted_time")
    private String deletedTime;

    // ── Getters ──
    public String getId() { return id; }
    public String getNodeId() { return nodeId; }
    public String getFileId() { return fileId; }
    public String getName() { return name; }
    public String getNodeName() { return nodeName; }
    public String getFileName() { return fileName; }
    public long getSize() { return size; }
    public Long getNodeSize() { return nodeSize; }
    public Long getFileSize() { return fileSize; }
    public String getType() { return type; }
    public String getNodeType() { return nodeType; }
    public boolean isFile() { return isFile; }
    public String getFileType() { return fileType; }
    public Boolean getIsFolder() { return isFolder; }
    public String getUploadedTime() { return uploadedTime; }
    public String getCreatedTime() { return createdTime; }
    public String getUpdatedTime() { return updatedTime; }
    public boolean isFavorite() { return isFavorite; }
    public void setFavorite(boolean favorite) { isFavorite = favorite; }
    public String getFavoriteId() { return favoriteId; }
    public String getDeletedTime() { return deletedTime; }

    // ── Setters ──
    public void setId(String id) { this.id = id; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }
    public void setFileId(String fileId) { this.fileId = fileId; }
    public void setName(String name) { this.name = name; }
    public void setNodeName(String nodeName) { this.nodeName = nodeName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public void setSize(long size) { this.size = size; }
    public void setNodeSize(Long nodeSize) { this.nodeSize = nodeSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public void setType(String type) { this.type = type; }
    public void setNodeType(String nodeType) { this.nodeType = nodeType; }
    public void setFile(boolean file) { isFile = file; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    public void setIsFolder(Boolean isFolder) { this.isFolder = isFolder; }
    public void setUploadedTime(String uploadedTime) { this.uploadedTime = uploadedTime; }
    public void setCreatedTime(String createdTime) { this.createdTime = createdTime; }
    public void setUpdatedTime(String updatedTime) { this.updatedTime = updatedTime; }
    public void setFavoriteId(String favoriteId) { this.favoriteId = favoriteId; }
    public void setDeletedTime(String deletedTime) { this.deletedTime = deletedTime; }

    // ── 计算属性 ──

    /** 有效 ID（兼容多种后端字段名） */
    public String getEffectiveId() {
        if (id != null && !id.isEmpty()) return id;
        if (nodeId != null && !nodeId.isEmpty()) return nodeId;
        if (fileId != null && !fileId.isEmpty()) return fileId;
        return "";
    }

    /** 有效名称 */
    public String getEffectiveName() {
        if (name != null && !name.isEmpty()) return name;
        if (nodeName != null && !nodeName.isEmpty()) return nodeName;
        if (fileName != null && !fileName.isEmpty()) return fileName;
        return "";
    }

    /** 有效大小 */
    public long getEffectiveSize() {
        if (size > 0) return size;
        if (nodeSize != null && nodeSize > 0) return nodeSize;
        if (fileSize != null && fileSize > 0) return fileSize;
        return 0;
    }

    /** 是否为目录 */
    public boolean isDirectory() {
        return !isFile && (isFolder == null || isFolder);
    }

    // ── 文件类型判断 ──

    public boolean isImageFile() {
        String ext = getExtension().toLowerCase();
        return ext.equals("jpg") || ext.equals("jpeg") || ext.equals("png")
                || ext.equals("gif") || ext.equals("webp") || ext.equals("bmp")
                || ext.equals("heic") || ext.equals("heif") || ext.equals("svg");
    }

    public boolean isVideoFile() {
        String ext = getExtension().toLowerCase();
        return ext.equals("mp4") || ext.equals("avi") || ext.equals("mov")
                || ext.equals("mkv") || ext.equals("wmv") || ext.equals("flv")
                || ext.equals("webm") || ext.equals("3gp");
    }

    public boolean isAudioFile() {
        String ext = getExtension().toLowerCase();
        return ext.equals("mp3") || ext.equals("wav") || ext.equals("aac")
                || ext.equals("flac") || ext.equals("ogg") || ext.equals("wma")
                || ext.equals("m4a");
    }

    public boolean isDocumentFile() {
        String ext = getExtension().toLowerCase();
        return ext.equals("pdf") || ext.equals("doc") || ext.equals("docx")
                || ext.equals("xls") || ext.equals("xlsx") || ext.equals("ppt")
                || ext.equals("pptx") || ext.equals("txt") || ext.equals("csv");
    }

    /** 获取文件扩展名 */
    public String getExtension() {
        String name = getEffectiveName();
        int dotIndex = name.lastIndexOf('.');
        return dotIndex > 0 ? name.substring(dotIndex + 1) : "";
    }
}