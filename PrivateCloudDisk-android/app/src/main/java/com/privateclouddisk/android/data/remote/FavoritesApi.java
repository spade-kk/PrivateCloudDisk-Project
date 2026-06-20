package com.privateclouddisk.android.data.remote;

import com.privateclouddisk.android.data.model.*;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.*;

/**
 * 收藏 API
 */
public interface FavoritesApi {

    @GET("favorites")
    Call<ApiResponse<List<NodeItem>>> getFavorites(
            @Query("page") int page,
            @Query("page_size") int pageSize
    );

    @POST("favorites/{fileId}")
    Call<ApiResponse<Void>> addFavorite(@Path("fileId") String fileId);

    @DELETE("favorites/{fileId}")
    Call<ApiResponse<Void>> removeFavorite(@Path("fileId") String fileId);
}