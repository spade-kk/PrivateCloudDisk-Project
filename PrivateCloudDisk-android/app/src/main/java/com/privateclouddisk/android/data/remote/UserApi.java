package com.privateclouddisk.android.data.remote;

import com.privateclouddisk.android.data.model.*;

import java.util.Map;

import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.*;

/**
 * 用户 API
 */
public interface UserApi {

    @PUT("users/profile")
    Call<ApiResponse<UserProfile>> updateProfile(@Body Map<String, String> fields);

    @Multipart
    @POST("users/avatar")
    Call<ApiResponse<String>> uploadAvatar(@Part MultipartBody.Part avatar);

    @POST("users/password")
    Call<ApiResponse<Void>> changePassword(@Body Map<String, String> body);

    @GET("users/storage")
    Call<ApiResponse<Map<String, Object>>> getStorageInfo();
}