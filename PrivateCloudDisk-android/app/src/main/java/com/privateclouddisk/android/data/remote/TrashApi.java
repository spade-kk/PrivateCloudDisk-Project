package com.privateclouddisk.android.data.remote;

import com.privateclouddisk.android.data.model.*;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.*;

/**
 * 回收站 API
 */
public interface TrashApi {

    @GET("trash")
    Call<ApiResponse<List<NodeItem>>> getTrashList(
            @Query("page") int page,
            @Query("page_size") int pageSize
    );

    @POST("trash/{fileId}/restore")
    Call<ApiResponse<Void>> restoreFile(@Path("fileId") String fileId);

    @DELETE("trash/{fileId}")
    Call<ApiResponse<Void>> permanentlyDelete(@Path("fileId") String fileId);

    @DELETE("trash/clear")
    Call<ApiResponse<Void>> clearTrash();
}