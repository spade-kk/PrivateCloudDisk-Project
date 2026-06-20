package com.privateclouddisk.android.data.repository;

import com.privateclouddisk.android.data.local.PreferenceManager;
import com.privateclouddisk.android.data.model.*;
import com.privateclouddisk.android.data.remote.ApiClient;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import timber.log.Timber;

/**
 * 认证仓库
 * 管理登录、注册、Token 刷新等
 */
@Singleton
public class AuthRepository {

    private final ApiClient apiClient;
    private final PreferenceManager preferenceManager;

    @Inject
    public AuthRepository(ApiClient apiClient, PreferenceManager preferenceManager) {
        this.apiClient = apiClient;
        this.preferenceManager = preferenceManager;
    }

    /**
     * 登录
     */
    public Single<UserProfile> login(String account, String password) {
        return Single.<UserProfile>create(emitter -> {
            try {
                LoginRequest request = new LoginRequest(account, password);
                retrofit2.Response<ApiResponse<LoginResponse>> response =
                        apiClient.getAuthApi().login(request).execute();

                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<LoginResponse> apiResponse = response.body();
                    if (apiResponse.isSuccess() && apiResponse.getData() != null) {
                        LoginResponse loginData = apiResponse.getData();

                        // 保存 Token
                        if (loginData.getToken() != null) {
                            preferenceManager.saveAuthToken(loginData.getToken());
                        }
                        if (loginData.getUserId() != null) {
                            preferenceManager.saveUserId(loginData.getUserId());
                        }
                        if (loginData.getUserName() != null) {
                            preferenceManager.saveUserName(loginData.getUserName());
                        }

                        // 获取用户信息
                        fetchAndSaveProfile();

                        UserProfile profile = new UserProfile();
                        profile.setUserId(loginData.getUserId());
                        profile.setUserName(loginData.getUserName());
                        emitter.onSuccess(profile);
                    } else {
                        emitter.onError(new ApiException(apiResponse.getCode(),
                                apiResponse.getMessage()));
                    }
                } else {
                    emitter.onError(new ApiException(
                            response.code(), "登录失败: " + response.message()));
                }
            } catch (Exception e) {
                Timber.e(e, "Login failed");
                emitter.onError(e);
            }
        }).subscribeOn(Schedulers.io());
    }

    /**
     * 注册
     */
    public Single<UserProfile> register(String account, String userName, String password) {
        return Single.<UserProfile>create(emitter -> {
            try {
                RegisterRequest request = new RegisterRequest(account, userName, password);
                retrofit2.Response<ApiResponse<LoginResponse>> response =
                        apiClient.getAuthApi().register(request).execute();

                if (response.isSuccessful() && response.body() != null
                        && response.body().isSuccess()) {
                    emitter.onSuccess(new UserProfile());
                } else {
                    ApiResponse<LoginResponse> apiResponse = response.body();
                    emitter.onError(new ApiException(
                            apiResponse != null ? apiResponse.getCode() : response.code(),
                            apiResponse != null ? apiResponse.getMessage() : "注册失败"));
                }
            } catch (Exception e) {
                Timber.e(e, "Register failed");
                emitter.onError(e);
            }
        }).subscribeOn(Schedulers.io());
    }

    /**
     * 登出
     */
    public Single<Boolean> logout() {
        return Single.<Boolean>create(emitter -> {
            try {
                apiClient.getAuthApi().logout().execute();
            } catch (Exception e) {
                Timber.w(e, "Logout API call failed, clearing local data anyway");
            }
            preferenceManager.clearSessionData();
            emitter.onSuccess(true);
        }).subscribeOn(Schedulers.io());
    }

    /**
     * 验证 Token 并获取用户信息
     */
    public Single<UserProfile> validateToken(String token) {
        return Single.<UserProfile>create(emitter -> {
            try {
                retrofit2.Response<ApiResponse<UserProfile>> response =
                        apiClient.getAuthApi().validateToken(new TokenValidationRequest(token)).execute();

                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<UserProfile> apiResponse = response.body();
                    if (apiResponse.isSuccess() && apiResponse.getData() != null) {
                        UserProfile profile = apiResponse.getData();
                        saveProfile(profile);
                        emitter.onSuccess(profile);
                    } else {
                        emitter.onError(new ApiException(apiResponse.getCode(),
                                apiResponse.getMessage()));
                    }
                } else {
                    emitter.onError(new ApiException(401, "Token 无效"));
                }
            } catch (Exception e) {
                Timber.e(e, "Token validation failed");
                emitter.onError(e);
            }
        }).subscribeOn(Schedulers.io());
    }

    /**
     * 刷新 Token
     */
    public Single<String> refreshToken(String refreshToken) {
        return Single.<String>create(emitter -> {
            try {
                retrofit2.Response<ApiResponse<TokenRefreshResponse>> response =
                        apiClient.getAuthApi().refreshToken(
                                new TokenRefreshRequest(refreshToken)).execute();

                if (response.isSuccessful() && response.body() != null
                        && response.body().isSuccess()) {
                    TokenRefreshResponse data = response.body().getData();
                    if (data != null) {
                        if (data.getToken() != null) {
                            preferenceManager.saveAuthToken(data.getToken());
                        }
                        if (data.getRefreshToken() != null) {
                            preferenceManager.saveRefreshToken(data.getRefreshToken());
                        }
                        emitter.onSuccess(data.getToken());
                    } else {
                        emitter.onError(new ApiException(0, "刷新失败"));
                    }
                } else {
                    emitter.onError(new ApiException(401, "刷新 Token 失败"));
                }
            } catch (Exception e) {
                Timber.e(e, "Token refresh failed");
                emitter.onError(e);
            }
        }).subscribeOn(Schedulers.io());
    }

    /**
     * 获取用户信息
     */
    public Single<UserProfile> getProfile() {
        return Single.<UserProfile>create(emitter -> {
            try {
                retrofit2.Response<ApiResponse<UserProfile>> response =
                        apiClient.getAuthApi().getProfile().execute();

                if (response.isSuccessful() && response.body() != null
                        && response.body().isSuccess()) {
                    UserProfile profile = response.body().getData();
                    saveProfile(profile);
                    emitter.onSuccess(profile);
                } else {
                    emitter.onError(new ApiException(response.code(), "获取用户信息失败"));
                }
            } catch (Exception e) {
                Timber.e(e, "Get profile failed");
                emitter.onError(e);
            }
        }).subscribeOn(Schedulers.io());
    }

    /**
     * 是否已登录
     */
    public boolean isLoggedIn() {
        return preferenceManager.getAuthToken() != null
                && !preferenceManager.getAuthToken().isEmpty();
    }

    private void fetchAndSaveProfile() {
        try {
            retrofit2.Response<ApiResponse<UserProfile>> response =
                    apiClient.getAuthApi().getProfile().execute();
            if (response.isSuccessful() && response.body() != null
                    && response.body().isSuccess()) {
                saveProfile(response.body().getData());
            }
        } catch (Exception e) {
            Timber.w(e, "Failed to fetch profile after login");
        }
    }

    private void saveProfile(UserProfile profile) {
        if (profile == null) return;
        if (profile.getUserId() != null) preferenceManager.saveUserId(profile.getUserId());
        if (profile.getUserName() != null) preferenceManager.saveUserName(profile.getUserName());
        if (profile.getAvatarUrl() != null) preferenceManager.saveUserAvatar(profile.getAvatarUrl());
    }
}