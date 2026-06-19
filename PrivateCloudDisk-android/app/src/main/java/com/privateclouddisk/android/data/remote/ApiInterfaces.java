package com.privateclouddisk.android.data.remote;

import com.privateclouddisk.android.data.model.*;

import java.util.List;
import java.util.Map;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.*;

/**
 * 认证 API
 * 对应 Windows 的 AuthService
 */
public interface AuthApi {

    /** 登录 */
    @POST("users/login")
    Call<ApiResponse<LoginResponse>> login(@Body LoginRequest request);

    /** 注册 */
    @POST("users/register")
    Call<ApiResponse<LoginResponse>> register(@Body RegisterRequest request);

    /** 登出 */
    @POST("users/logout")
    Call<ApiResponse<Void>> logout();

    /** 验证 Token */
    @POST("users/validate")
    Call<ApiResponse<UserProfile>> validateToken(@Body TokenValidationRequest request);

    /** 刷新 Token */
    @POST("users/refresh")
    Call<ApiResponse<TokenRefreshResponse>> refreshToken(@Body TokenRefreshRequest request);

    /** 获取当前用户信息 */
    @GET("users/profile")
    Call<ApiResponse<UserProfile>> getProfile();
}

/**
 * 文件 API
 */
interface FileApi {

    /** 获取文件列表 */
    @GET("files")
    Call<ApiResponse<List<NodeItem>>> getFiles(
            @Query("parent_id") String parentId,
            @Query("page") int page,
            @Query("page_size") int pageSize
    );

    /** 获取文件详情 */
    @GET("files/{fileId}")
    Call<ApiResponse<NodeItem>> getFileDetail(@Path("fileId") String fileId);

    /** 创建文件夹 */
    @POST("files/folder")
    Call<ApiResponse<NodeItem>> createFolder(@Body CreateFolderRequest request);

    /** 重命名文件 */
    @PUT("files/{fileId}/rename")
    Call<ApiResponse<Void>> renameFile(@Path("fileId") String fileId, @Body RenameRequest request);

    /** 移动文件 */
    @PUT("files/{fileId}/move")
    Call<ApiResponse<Void>> moveFile(@Path("fileId") String fileId, @Body MoveFileRequest request);

    /** 删除文件 */
    @DELETE("files/{fileId}")
    Call<ApiResponse<Void>> deleteFile(@Path("fileId") String fileId);

    /** 批量删除 */
    @HTTP(method = "DELETE", path = "files/batch", hasBody = true)
    Call<ApiResponse<Void>> batchDelete(@Body BatchOperationRequest request);

    /** 搜索文件 */
    @GET("files/search")
    Call<ApiResponse<List<NodeItem>>> searchFiles(
            @Query("keyword") String keyword,
            @Query("file_type") String fileType,
            @Query("page") int page,
            @Query("page_size") int pageSize
    );

    /** 获取操作凭证（下载/预览） */
    @POST("files/{fileId}/operation-token")
    Call<ApiResponse<OperationTokenResponse>> getOperationToken(
            @Path("fileId") String fileId,
            @Body OperationTokenRequest request
    );

    /** 创建上传会话 */
    @POST("files/upload/init")
    Call<ApiResponse<UploadSessionResponse>> createUploadSession(@Body CreateUploadSessionRequest request);

    /** 上传分块 */
    @Multipart
    @POST("files/upload/chunk")
    Call<ApiResponse<Void>> uploadChunk(
            @Part("upload_id") RequestBody uploadId,
            @Part("chunk_index") RequestBody chunkIndex,
            @Part("total_chunks") RequestBody totalChunks,
            @Part MultipartBody.Part file
    );

    /** 完成上传 */
    @POST("files/upload/complete")
    Call<ApiResponse<NodeItem>> completeUpload(@Body CompleteUploadRequest request);

    /** 下载文件（使用操作凭证） */
    @Streaming
    @GET("files/download")
    Call<ResponseBody> downloadFile(
            @Query("file_id") String fileId,
            @Query("token") String operationToken
    );
}

/**
 * 收藏 API
 */
interface FavoritesApi {

    /** 获取收藏列表 */
    @GET("favorites")
    Call<ApiResponse<List<NodeItem>>> getFavorites(
            @Query("page") int page,
            @Query("page_size") int pageSize
    );

    /** 添加收藏 */
    @POST("favorites/{fileId}")
    Call<ApiResponse<Void>> addFavorite(@Path("fileId") String fileId);

    /** 取消收藏 */
    @DELETE("favorites/{fileId}")
    Call<ApiResponse<Void>> removeFavorite(@Path("fileId") String fileId);
}

/**
 * 回收站 API
 */
interface TrashApi {

    /** 获取回收站列表 */
    @GET("trash")
    Call<ApiResponse<List<NodeItem>>> getTrashList(
            @Query("page") int page,
            @Query("page_size") int pageSize
    );

    /** 恢复文件 */
    @POST("trash/{fileId}/restore")
    Call<ApiResponse<Void>> restoreFile(@Path("fileId") String fileId);

    /** 永久删除 */
    @DELETE("trash/{fileId}")
    Call<ApiResponse<Void>> permanentlyDelete(@Path("fileId") String fileId);

    /** 清空回收站 */
    @DELETE("trash/clear")
    Call<ApiResponse<Void>> clearTrash();
}

/**
 * 用户 API
 */
interface UserApi {

    /** 更新用户信息 */
    @PUT("users/profile")
    Call<ApiResponse<UserProfile>> updateProfile(@Body Map<String, String> fields);

    /** 上传头像 */
    @Multipart
    @POST("users/avatar")
    Call<ApiResponse<String>> uploadAvatar(@Part MultipartBody.Part avatar);

    /** 修改密码 */
    @POST("users/password")
    Call<ApiResponse<Void>> changePassword(@Body Map<String, String> body);

    /** 获取存储空间信息 */
    @GET("users/storage")
    Call<ApiResponse<Map<String, Object>>> getStorageInfo();
}