using System.Net.Http.Json;
using System.Text;
using System.Text.Json;
using PrivateCloudDisk.Models;

namespace PrivateCloudDisk.Services.Implementations;

/// <summary>
/// API 服务基类 — 提供通用 HTTP 请求方法和认证头注入
/// </summary>
public abstract class BaseApiService
{
    protected readonly IHttpClientFactory HttpFactory;
    protected readonly IAuthService Auth;

    protected BaseApiService(IHttpClientFactory httpFactory, IAuthService authService)
    {
        HttpFactory = httpFactory;
        Auth = authService;
    }

    protected HttpClient CreateClient(string service = "platform")
    {
        var client = HttpFactory.CreateClient(service == "file" ? "FileService" : "PlatformService");
        if (!string.IsNullOrEmpty(Auth.CurrentToken))
            client.DefaultRequestHeaders.Authorization =
                new System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", Auth.CurrentToken);
        return client;
    }

    protected static async Task<ApiResponse<T>> ParseAsync<T>(HttpResponseMessage response)
    {
        var json = await response.Content.ReadAsStringAsync();
        var result = JsonSerializer.Deserialize<ApiResponse<T>>(json,
            new JsonSerializerOptions { PropertyNameCaseInsensitive = true });
        if (result == null)
            throw new ApiException((int)response.StatusCode, "响应解析失败");
        return result;
    }

    protected static StringContent JsonContent<T>(T data) =>
        new(JsonSerializer.Serialize(data), Encoding.UTF8, "application/json");
}

/// <summary>
/// 文件服务 — 对接后端 FileController
/// </summary>
public class FileService : BaseApiService, IFileService
{
    public FileService(IHttpClientFactory httpFactory, IAuthService authService)
        : base(httpFactory, authService) { }

    public async Task<PagedResult<NodeItem>> GetFileListAsync(string nodeId, int page = 1, int size = 50)
    {
        var client = CreateClient();
        var response = await client.GetAsync($"/files?node_id={nodeId}&page={page}&size={size}");
        var apiResp = await ParseAsync<PagedResult<NodeItem>>(response);
        return apiResp.GetDataOrThrow();
    }

    public async Task<FileDetail> GetFileDetailAsync(string fileId)
    {
        var client = CreateClient();
        var response = await client.GetAsync($"/files/{fileId}");
        var apiResp = await ParseAsync<FileDetail>(response);
        return apiResp.GetDataOrThrow();
    }

    public async Task RenameFileAsync(string fileId, string newName)
    {
        var client = CreateClient();
        var body = new { file_new_name = newName };
        var response = await client.PatchAsync($"/files/{fileId}/name", JsonContent(body));
        await ParseAsync<object>(response);
    }

    public async Task MoveFileAsync(string fileId, string targetNodeId)
    {
        var client = CreateClient();
        var body = new { target_node_id = targetNodeId };
        var response = await client.PatchAsync($"/files/{fileId}/position", JsonContent(body));
        await ParseAsync<object>(response);
    }

    public async Task DeleteFileAsync(string fileId)
    {
        var client = CreateClient();
        var response = await client.PostAsync($"/trash/files/{fileId}", null);
        await ParseAsync<object>(response);
    }

    public async Task<SearchResult> AdvancedSearchAsync(SearchRequest request)
    {
        var client = CreateClient();
        var queryParams = new List<string>
        {
            $"keyword={Uri.EscapeDataString(request.Keyword)}",
            $"page={request.Page}",
            $"size={request.Size}"
        };
        if (!string.IsNullOrEmpty(request.SortField))
            queryParams.Add($"sortField={Uri.EscapeDataString(request.SortField)}");
        queryParams.Add($"asc={request.Asc.ToString().ToLower()}");

        var url = $"/files/advanced-search?{string.Join("&", queryParams)}";
        var response = await client.GetAsync(url);
        var apiResp = await ParseAsync<SearchResult>(response);
        return apiResp.GetDataOrThrow();
    }
}

/// <summary>
/// 文件夹节点服务 — 对接后端 NodeController
/// </summary>
public class NodeService : BaseApiService, INodeService
{
    public NodeService(IHttpClientFactory httpFactory, IAuthService authService)
        : base(httpFactory, authService) { }

    public async Task<NodeItem> GetRootNodeAsync()
    {
        var client = CreateClient();
        var response = await client.GetAsync("/nodes/root");
        var apiResp = await ParseAsync<NodeItem>(response);
        return apiResp.GetDataOrThrow();
    }

    public async Task<PagedResult<NodeItem>> GetChildNodesAsync(string nodeId, int page = 1, int size = 50)
    {
        var client = CreateClient();
        var response = await client.GetAsync($"/nodes?parent_id={nodeId}&page={page}&size={size}");
        var apiResp = await ParseAsync<PagedResult<NodeItem>>(response);
        return apiResp.GetDataOrThrow();
    }

    public async Task<NodeItem> CreateFolderAsync(string folderName, string parentNodeId)
    {
        var client = CreateClient();
        var body = new { folder_name = folderName, node_id = parentNodeId };
        var response = await client.PostAsync("/nodes", JsonContent(body));
        var apiResp = await ParseAsync<NodeItem>(response);
        return apiResp.GetDataOrThrow();
    }

    public async Task RenameNodeAsync(string nodeId, string newName)
    {
        var client = CreateClient();
        var body = new { new_node_name = newName };
        var response = await client.PatchAsync($"/nodes/{nodeId}/name", JsonContent(body));
        await ParseAsync<object>(response);
    }

    public async Task MoveNodeAsync(string nodeId, string targetPosition)
    {
        var client = CreateClient();
        var body = new { target_position = targetPosition };
        var response = await client.PatchAsync($"/nodes/{nodeId}/position", JsonContent(body));
        await ParseAsync<object>(response);
    }

    public async Task DeleteNodeAsync(string nodeId)
    {
        var client = CreateClient();
        var response = await client.PostAsync($"/trash/folders/{nodeId}", null);
        await ParseAsync<object>(response);
    }

    public async Task<List<NodeItem>> GetBreadcrumbAsync(string nodeId)
    {
        var client = CreateClient();
        var response = await client.GetAsync($"/nodes/{nodeId}/path");
        var apiResp = await ParseAsync<List<NodeItem>>(response);
        return apiResp.GetDataOrThrow();
    }
}

/// <summary>
/// 回收站服务 — 对接后端 TrashController
/// </summary>
public class TrashService : BaseApiService, ITrashService
{
    public TrashService(IHttpClientFactory httpFactory, IAuthService authService)
        : base(httpFactory, authService) { }

    public async Task<PagedResult<TrashItem>> GetTrashListAsync(int page = 1, int size = 50)
    {
        var client = CreateClient();
        var response = await client.GetAsync($"/trash/list?page={page}&size={size}");
        var apiResp = await ParseAsync<PagedResult<TrashItem>>(response);
        return apiResp.GetDataOrThrow();
    }

    public async Task RestoreAsync(string trashId)
    {
        var client = CreateClient();
        var response = await client.PostAsync($"/trash/{trashId}/restore", null);
        await ParseAsync<object>(response);
    }

    public async Task PermanentDeleteAsync(string trashId)
    {
        var client = CreateClient();
        var response = await client.DeleteAsync($"/trash/{trashId}");
        await ParseAsync<object>(response);
    }

    public async Task EmptyTrashAsync()
    {
        var client = CreateClient();
        var response = await client.DeleteAsync("/trash/empty");
        await ParseAsync<object>(response);
    }
}

/// <summary>
/// 收藏服务 — 对接后端 FileStarController
/// </summary>
public class StarService : BaseApiService, IStarService
{
    public StarService(IHttpClientFactory httpFactory, IAuthService authService)
        : base(httpFactory, authService) { }

    public async Task<PagedResult<StarItem>> GetStarListAsync(int page = 1, int size = 50)
    {
        var client = CreateClient();
        var response = await client.GetAsync($"/stars?page={page}&size={size}");
        var apiResp = await ParseAsync<PagedResult<StarItem>>(response);
        return apiResp.GetDataOrThrow();
    }

    public async Task AddStarAsync(string fileId)
    {
        var client = CreateClient();
        var response = await client.PostAsync($"/stars/{fileId}", null);
        await ParseAsync<object>(response);
    }

    public async Task RemoveStarAsync(string fileId)
    {
        var client = CreateClient();
        var response = await client.DeleteAsync($"/stars/{fileId}");
        await ParseAsync<object>(response);
    }

    public async Task<bool> IsStarredAsync(string fileId)
    {
        var client = CreateClient();
        var response = await client.GetAsync($"/stars/{fileId}/status");
        var apiResp = await ParseAsync<Dictionary<string, bool>>(response);
        var data = apiResp.GetDataOrThrow();
        return data.GetValueOrDefault("is_starred", false);
    }
}

/// <summary>
/// 配额服务
/// </summary>
public class QuotaService : BaseApiService, IQuotaService
{
    public QuotaService(IHttpClientFactory httpFactory, IAuthService authService)
        : base(httpFactory, authService) { }

    public async Task<QuotaInfo> GetQuotaAsync()
    {
        var client = CreateClient();
        var response = await client.GetAsync("/quotas");
        var apiResp = await ParseAsync<QuotaInfo>(response);
        return apiResp.GetDataOrThrow();
    }
}

/// <summary>
/// 用户服务
/// </summary>
public class UserService : BaseApiService, IUserService
{
    public UserService(IHttpClientFactory httpFactory, IAuthService authService)
        : base(httpFactory, authService) { }

    public async Task<UserProfile> GetUserInfoAsync(string userId)
    {
        var client = CreateClient();
        var response = await client.GetAsync($"/users/{userId}");
        var apiResp = await ParseAsync<UserProfile>(response);
        return apiResp.GetDataOrThrow();
    }

    public async Task<UserProfile> UpdateUserInfoAsync(UpdateUserInfoRequest request)
    {
        var client = CreateClient();
        var response = await client.PatchAsync("/users/me", JsonContent(request));
        var apiResp = await ParseAsync<UserProfile>(response);
        return apiResp.GetDataOrThrow();
    }

    public async Task ChangePasswordAsync(ChangePasswordRequest request)
    {
        var client = CreateClient();
        var response = await client.PostAsync("/users/me/password", JsonContent(request));
        await ParseAsync<object>(response);
    }

    public async Task<string> UploadAvatarAsync(Stream fileStream, string fileName)
    {
        var client = CreateClient();
        using var content = new MultipartFormDataContent();
        var streamContent = new StreamContent(fileStream);
        streamContent.Headers.ContentType = new System.Net.Http.Headers.MediaTypeHeaderValue("image/png");
        content.Add(streamContent, "avator_file", fileName);

        var response = await client.PutAsync($"/users/{Auth.CurrentUserId}/avatar", content);
        var apiResp = await ParseAsync<Dictionary<string, string>>(response);
        return apiResp.GetDataOrThrow().GetValueOrDefault("avatar_url", string.Empty);
    }
}