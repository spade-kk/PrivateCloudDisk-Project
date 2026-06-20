package com.privateclouddisk.android.data.remote;

import com.privateclouddisk.android.data.model.*;

import java.util.List;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.*;

/**
 * 文件 API
 */
public interface FileApi {

    @GET("files")
    Call<ApiResponse<List<NodeItem>>> getFiles(
            @Query("parent_id") String parentId,
            @Query("page") int page,
            @Query("page_size") int pageSize
    );

    @GET("files/{fileId}")
    Call<ApiResponse<NodeItem>> getFileDetail(@Path("fileId") String fileId);

    @POST("files/folder")
    Call<ApiResponse<NodeItem>> createFolder(@Body CreateFolderRequest request);

    @PUT("files/{fileId}/rename")
    Call<ApiResponse<Void>> renameFile(@Path("fileId") String fileId, @Body RenameRequest request);

    @PUT("files/{fileId}/move")
    Call<ApiResponse<Void>> moveFile(@Path("fileId") String fileId, @Body MoveFileRequest request);

    @DELETE("files/{fileId}")
    Call<ApiResponse<Void>> deleteFile(@Path("fileId") String fileId);

    @HTTP(method = "DELETE", path = "files/batch", hasBody = true)
    Call<ApiResponse<Void>> batchDelete(@Body BatchOperationRequest request);

    @GET("files/search")
    Call<ApiResponse<List<NodeItem>>> searchFiles(
            @Query("keyword") String keyword,
            @Query("file_type") String fileType,
            @Query("page") int page,
            @Query("page_size") int pageSize
    );

    @POST("files/{fileId}/operation-token")
    Call<ApiResponse<OperationTokenResponse>> getOperationToken(
            @Path("fileId") String fileId,
            @Body OperationTokenRequest request
    );

    @POST("files/upload/init")
    Call<ApiResponse<UploadSessionResponse>> createUploadSession(@Body CreateUploadSessionRequest request);

    @Multipart
    @POST("files/upload/chunk")
    Call<ApiResponse<Void>> uploadChunk(
            @Part("upload_id") RequestBody uploadId,
            @Part("chunk_index") RequestBody chunkIndex,
            @Part("total_chunks") RequestBody totalChunks,
            @Part MultipartBody.Part file
    );

    @POST("files/upload/complete")
    Call<ApiResponse<NodeItem>> completeUpload(@Body CompleteUploadRequest request);

    @Streaming
    @GET("files/download")
    Call<ResponseBody> downloadFile(
            @Query("file_id") String fileId,
            @Query("token") String operationToken
    );
}