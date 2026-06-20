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

    @POST("users/login")
    Call<ApiResponse<LoginResponse>> login(@Body LoginRequest request);

    @POST("users/register")
    Call<ApiResponse<LoginResponse>> register(@Body RegisterRequest request);

    @POST("users/logout")
    Call<ApiResponse<Void>> logout();

    @POST("users/validate")
    Call<ApiResponse<UserProfile>> validateToken(@Body TokenValidationRequest request);

    @POST("users/refresh")
    Call<ApiResponse<TokenRefreshResponse>> refreshToken(@Body TokenRefreshRequest request);

    @GET("users/profile")
    Call<ApiResponse<UserProfile>> getProfile();
}