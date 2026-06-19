package com.privateclouddisk.android.data.remote;

import com.privateclouddisk.android.BuildConfig;
import com.privateclouddisk.android.data.local.PreferenceManager;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import timber.log.Timber;

/**
 * API 客户端管理器
 * 使用 OkHttp + Retrofit，支持 Token 自动注入和刷新
 */
@Singleton
public class ApiClient {

    private final PreferenceManager preferenceManager;
    private final OkHttpClient okHttpClient;
    private final Retrofit retrofit;

    private AuthApi authApi;
    private FileApi fileApi;
    private FavoritesApi favoritesApi;
    private TrashApi trashApi;
    private UserApi userApi;

    @Inject
    public ApiClient(PreferenceManager preferenceManager) {
        this.preferenceManager = preferenceManager;

        // ── 日志拦截器 ──
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor(message ->
                Timber.tag("OkHttp").d(message)
        );
        loggingInterceptor.setLevel(BuildConfig.DEBUG ?
                HttpLoggingInterceptor.Level.BODY : HttpLoggingInterceptor.Level.BASIC);

        // ── Token 注入拦截器 ──
        Interceptor authInterceptor = new Interceptor() {
            @Override
            public Response intercept(Chain chain) throws IOException {
                Request original = chain.request();
                String token = preferenceManager.getAuthToken();

                Request.Builder builder = original.newBuilder()
                        .header("Accept", "application/json")
                        .header("Content-Type", "application/json")
                        .header("X-Client-Type", "android")
                        .header("X-Client-Version", BuildConfig.VERSION_NAME);

                if (token != null && !token.isEmpty()) {
                    builder.header("Authorization", "Bearer " + token);
                }

                return chain.proceed(builder.build());
            }
        };

        // ── 构建 OkHttpClient ──
        this.okHttpClient = new OkHttpClient.Builder()
                .addInterceptor(authInterceptor)
                .addInterceptor(loggingInterceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();

        // ── 构建 Retrofit ──
        this.retrofit = new Retrofit.Builder()
                .baseUrl(BuildConfig.API_BASE_URL + "/")
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }

    // ── API 实例（懒加载） ──

    public AuthApi getAuthApi() {
        if (authApi == null) {
            authApi = retrofit.create(AuthApi.class);
        }
        return authApi;
    }

    public FileApi getFileApi() {
        if (fileApi == null) {
            fileApi = retrofit.create(FileApi.class);
        }
        return fileApi;
    }

    public FavoritesApi getFavoritesApi() {
        if (favoritesApi == null) {
            favoritesApi = retrofit.create(FavoritesApi.class);
        }
        return favoritesApi;
    }

    public TrashApi getTrashApi() {
        if (trashApi == null) {
            trashApi = retrofit.create(TrashApi.class);
        }
        return trashApi;
    }

    public UserApi getUserApi() {
        if (userApi == null) {
            userApi = retrofit.create(UserApi.class);
        }
        return userApi;
    }

    public OkHttpClient getOkHttpClient() {
        return okHttpClient;
    }
}