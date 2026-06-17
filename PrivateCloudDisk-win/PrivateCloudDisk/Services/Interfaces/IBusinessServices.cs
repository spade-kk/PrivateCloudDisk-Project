using PrivateCloudDisk.Models;

namespace PrivateCloudDisk.Services.Interfaces;

/// <summary>
/// 文件服务接口 — 对接后端 FileController
/// </summary>
public interface IFileService
{
    /// <summary>获取文件列表 (某个目录下的文件)</summary>
    Task<PagedResult<NodeItem>> GetFileListAsync(string nodeId, int page = 1, int size = 50);

    /// <summary>获取文件详情</summary>
    Task<FileDetail> GetFileDetailAsync(string fileId);

    /// <summary>重命名文件 PATCH /files/{fileId}/name</summary>
    Task RenameFileAsync(string fileId, string newName);

    /// <summary>移动文件 PATCH /files/{fileId}/position</summary>
    Task MoveFileAsync(string fileId, string targetNodeId);

    /// <summary>删除文件 (移入回收站) POST /trash/files/{fileId}</summary>
    Task DeleteFileAsync(string fileId);

    /// <summary>高级搜索 GET /files/advanced-search</summary>
    Task<SearchResult> AdvancedSearchAsync(SearchRequest request);
}

/// <summary>
/// 文件夹节点服务接口 — 对接后端 NodeController
/// </summary>
public interface INodeService
{
    /// <summary>获取根节点</summary>
    Task<NodeItem> GetRootNodeAsync();

    /// <summary>获取子节点列表</summary>
    Task<PagedResult<NodeItem>> GetChildNodesAsync(string nodeId, int page = 1, int size = 50);

    /// <summary>创建文件夹</summary>
    Task<NodeItem> CreateFolderAsync(string folderName, string parentNodeId);

    /// <summary>重命名节点 PATCH /nodes/{nodeId}/name</summary>
    Task RenameNodeAsync(string nodeId, string newName);

    /// <summary>移动节点 PATCH /nodes/{nodeId}/position</summary>
    Task MoveNodeAsync(string nodeId, string targetPosition);

    /// <summary>删除节点 (移入回收站) POST /trash/folders/{nodeId}</summary>
    Task DeleteNodeAsync(string nodeId);

    /// <summary>获取面包屑路径</summary>
    Task<List<NodeItem>> GetBreadcrumbAsync(string nodeId);
}

/// <summary>
/// 回收站服务接口 — 对接后端 TrashController
/// </summary>
public interface ITrashService
{
    /// <summary>获取回收站列表</summary>
    Task<PagedResult<TrashItem>> GetTrashListAsync(int page = 1, int size = 50);

    /// <summary>恢复文件/文件夹</summary>
    Task RestoreAsync(string trashId);

    /// <summary>彻底删除</summary>
    Task PermanentDeleteAsync(string trashId);

    /// <summary>清空回收站</summary>
    Task EmptyTrashAsync();
}

/// <summary>
/// 收藏服务接口 — 对接后端 FileStarController
/// </summary>
public interface IStarService
{
    /// <summary>获取收藏列表</summary>
    Task<PagedResult<StarItem>> GetStarListAsync(int page = 1, int size = 50);

    /// <summary>添加收藏 POST /stars/{fileId}</summary>
    Task AddStarAsync(string fileId);

    /// <summary>取消收藏 DELETE /stars/{fileId}</summary>
    Task RemoveStarAsync(string fileId);

    /// <summary>检查收藏状态</summary>
    Task<bool> IsStarredAsync(string fileId);
}

/// <summary>
/// 配额服务接口
/// </summary>
public interface IQuotaService
{
    /// <summary>获取当前用户配额</summary>
    Task<QuotaInfo> GetQuotaAsync();
}

/// <summary>
/// 用户服务接口
/// </summary>
public interface IUserService
{
    /// <summary>获取用户信息</summary>
    Task<UserProfile> GetUserInfoAsync(string userId);

    /// <summary>更新用户信息 PATCH /users/me</summary>
    Task<UserProfile> UpdateUserInfoAsync(UpdateUserInfoRequest request);

    /// <summary>修改密码 POST /users/me/password</summary>
    Task ChangePasswordAsync(ChangePasswordRequest request);

    /// <summary>上传头像</summary>
    Task<string> UploadAvatarAsync(Stream fileStream, string fileName);
}