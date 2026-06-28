package com.privateclouddisk.android.update;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.privateclouddisk.android.MainActivity;
import com.privateclouddisk.android.R;
import com.privateclouddisk.android.remote.ApiClient;
import com.privateclouddisk.android.remote.RetrofitClient;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.http.Body;
import retrofit2.http.POST;

/**
 * 企业级 Android 更新管理器
 *
 * 功能:
 * 1. 大版本更新 → 通知栏 + 弹窗提醒，引导到下载页面
 * 2. 热更新 → 后台静默下载补丁，替换资源文件
 * 3. 版本自检 → 启动时自动检查 + 定期检查
 * 4. 强制更新 → 阻塞式弹窗，必须更新
 * 5. 断点下载 → 支持断点续传
 */
public class UpdateManager {

    private static final String TAG = "UpdateManager";
    private static final String CHANNEL_ID = "update_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final String PREFS_NAME = "update_settings";
    private static final long CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L; // 24小时

    private final Context context;
    private final Gson gson;
    private final OkHttpClient httpClient;
    private final ExecutorService executor;
    private final Handler mainHandler;
    private final SharedPreferences prefs;

    private UpdateInfo currentUpdateInfo;
    private boolean isDownloading = false;
    private boolean isCancelled = false;
    private UpdateListener listener;

    // ==================== 数据模型 ====================

    public static class UpdateInfo {
        @SerializedName("has_update")
        public boolean hasUpdate;

        @SerializedName("latest_version")
        public String latestVersion;

        @SerializedName("update_type")
        public String updateType; // major, minor, patch

        @SerializedName("force_update")
        public boolean forceUpdate;

        @SerializedName("download_url")
        public String downloadUrl;

        @SerializedName("release_notes")
        public String releaseNotes;

        @SerializedName("package_size")
        public long packageSize;

        @SerializedName("package_hash")
        public String packageHash;

        public boolean isMajorUpdate() {
            return "major".equals(updateType);
        }

        public boolean isHotfix() {
            return "patch".equals(updateType);
        }
    }

    public static class VersionCheckRequest {
        @SerializedName("current_version")
        public String currentVersion;

        @SerializedName("platform")
        public String platform = "android";

        @SerializedName("arch")
        public String arch;

        @SerializedName("channel")
        public String channel = "stable";
    }

    public static class VersionCheckResult {
        public boolean success;
        public boolean hasUpdate;
        public UpdateInfo updateInfo;
        public String errorMessage;
    }

    public interface UpdateListener {
        void onUpdateAvailable(UpdateInfo info);
        void onDownloadProgress(int percent, long downloaded, long total, double speed);
        void onDownloadComplete(File apkFile);
        void onDownloadError(String error);
        void onHotfixApplied(boolean success);
    }

    // ==================== 构造函数 ====================

    public UpdateManager(Context context) {
        this.context = context.getApplicationContext();
        this.gson = new Gson();
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.MINUTES)
                .build();

        createNotificationChannel();
    }

    // ==================== 版本自检 ====================

    /**
     * 启动时版本自检
     */
    public void performStartupCheck() {
        executor.execute(() -> {
            Log.d(TAG, "执行启动版本自检...");

            try {
                VersionCheckResult result = checkUpdateSync();

                if (result.success && result.hasUpdate) {
                    UpdateInfo info = result.updateInfo;
                    Log.d(TAG, "发现更新: v" + info.latestVersion + " (" + info.updateType + ")");

                    mainHandler.post(() -> {
                        if (info.forceUpdate) {
                            handleForceUpdate(info);
                        } else if (info.isMajorUpdate()) {
                            handleMajorUpdate(info);
                        } else if (info.isHotfix() && isHotUpdateEnabled()) {
                            handleHotfix(info);
                        } else {
                            handleMinorUpdate(info);
                        }
                    });
                } else {
                    Log.d(TAG, "当前已是最新版本");
                }
            } catch (Exception e) {
                Log.e(TAG, "版本检查失败: " + e.getMessage());
            }
        });
    }

    /**
     * 手动检查更新
     */
    public void checkForUpdates(UpdateListener listener) {
        this.listener = listener;

        executor.execute(() -> {
            try {
                VersionCheckResult result = checkUpdateSync();

                mainHandler.post(() -> {
                    if (result.success && result.hasUpdate) {
                        currentUpdateInfo = result.updateInfo;
                        if (listener != null) {
                            listener.onUpdateAvailable(result.updateInfo);
                        }
                    } else if (!result.success) {
                        if (listener != null) {
                            listener.onDownloadError(result.errorMessage);
                        }
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onDownloadError(e.getMessage());
                    }
                });
            }
        });
    }

    /**
     * 同步检查更新
     */
    private VersionCheckResult checkUpdateSync() throws IOException {
        VersionCheckResult result = new VersionCheckResult();

        try {
            String currentVersion = getCurrentVersion();
            String arch = Build.CPU_ABI;

            VersionCheckRequest request = new VersionCheckRequest();
            request.currentVersion = currentVersion;
            request.arch = arch;
            request.channel = getUpdateChannel();

            String json = gson.toJson(request);
            String url = ApiClient.getBaseUrl() + "/api/v1/version/check";

            okhttp3.RequestBody body = okhttp3.RequestBody.create(
                    json, okhttp3.MediaType.parse("application/json"));
            Request httpRequest = new Request.Builder()
                    .url(url)
                    .post(body)
                    .addHeader("Authorization", "Bearer " + ApiClient.getToken())
                    .build();

            try (Response response = httpClient.newCall(httpRequest).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String respJson = response.body().string();
                    ApiResponse apiResp = gson.fromJson(respJson, ApiResponse.class);

                    if (apiResp != null && apiResp.code == 200 && apiResp.data != null) {
                        result.success = true;
                        result.hasUpdate = apiResp.data.hasUpdate;
                        result.updateInfo = apiResp.data;
                    }
                } else {
                    result.errorMessage = "服务器返回错误: " + response.code();
                }
            }
        } catch (Exception e) {
            result.errorMessage = e.getMessage();
        }

        return result;
    }

    // ==================== 更新处理策略 ====================

    /**
     * 强制更新 — 必须更新才能使用
     */
    private void handleForceUpdate(UpdateInfo info) {
        showForceUpdateNotification(info);
        // 通知 Activity 弹出强制更新对话框
        if (listener != null) {
            listener.onUpdateAvailable(info);
        }
    }

    /**
     * 大版本更新 — 通知用户
     */
    private void handleMajorUpdate(UpdateInfo info) {
        showUpdateNotification(info, "发现大版本更新");
        if (listener != null) {
            listener.onUpdateAvailable(info);
        }
    }

    /**
     * 热修复 — 静默下载应用
     */
    private void handleHotfix(UpdateInfo info) {
        Log.d(TAG, "静默下载热修复 v" + info.latestVersion);
        downloadUpdate(info, null);
    }

    /**
     * 小版本更新 — 后台下载
     */
    private void handleMinorUpdate(UpdateInfo info) {
        if (isAutoDownloadEnabled()) {
            Log.d(TAG, "后台下载小版本更新 v" + info.latestVersion);
            downloadUpdate(info, null);
        } else {
            showUpdateNotification(info, "新版本可用");
        }
    }

    // ==================== 下载更新 ====================

    /**
     * 下载更新 APK
     */
    public void downloadUpdate(UpdateInfo info, UpdateListener downloadListener) {
        if (isDownloading) return;
        isDownloading = true;
        isCancelled = false;

        if (downloadListener != null) {
            this.listener = downloadListener;
        }
        currentUpdateInfo = info;

        executor.execute(() -> {
            try {
                File downloadDir = new File(context.getExternalFilesDir(null), "updates");
                if (!downloadDir.exists()) {
                    downloadDir.mkdirs();
                }

                File apkFile = new File(downloadDir, "update-" + info.latestVersion + ".apk");

                // 检查是否已有完整下载
                if (apkFile.exists() && verifyFileHash(apkFile, info.packageHash)) {
                    mainHandler.post(() -> {
                        isDownloading = false;
                        if (listener != null) {
                            listener.onDownloadComplete(apkFile);
                        }
                    });
                    return;
                }

                // 断点续传
                long downloadedBytes = apkFile.exists() ? apkFile.length() : 0;

                Request.Builder requestBuilder = new Request.Builder()
                        .url(info.downloadUrl);
                if (downloadedBytes > 0) {
                    requestBuilder.addHeader("Range", "bytes=" + downloadedBytes + "-");
                }

                try (Response response = httpClient.newCall(requestBuilder.build()).execute()) {
                    if (!response.isSuccessful()) {
                        throw new IOException("下载失败: " + response.code());
                    }

                    InputStream inputStream = response.body().byteStream();
                    FileOutputStream outputStream = new FileOutputStream(apkFile, downloadedBytes > 0);

                    long totalBytes = response.body().contentLength() + downloadedBytes;
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    long lastUpdateTime = System.currentTimeMillis();
                    long lastDownloadedBytes = downloadedBytes;
                    long currentDownloaded = downloadedBytes;

                    while ((bytesRead = inputStream.read(buffer)) != -1 && !isCancelled) {
                        outputStream.write(buffer, 0, bytesRead);
                        currentDownloaded += bytesRead;

                        long now = System.currentTimeMillis();
                        if (now - lastUpdateTime > 500) {
                            double speed = (currentDownloaded - lastDownloadedBytes) /
                                    ((now - lastUpdateTime) / 1000.0);
                            int percent = totalBytes > 0
                                    ? (int) (currentDownloaded * 100 / totalBytes) : 0;

                            mainHandler.post(() -> {
                                if (listener != null) {
                                    listener.onDownloadProgress(percent,
                                            currentDownloaded, totalBytes, speed);
                                }
                                updateDownloadNotification(percent, currentDownloaded, totalBytes, speed);
                            });

                            lastUpdateTime = now;
                            lastDownloadedBytes = currentDownloaded;
                        }
                    }

                    outputStream.close();
                    inputStream.close();

                    if (isCancelled) {
                        apkFile.delete();
                        return;
                    }

                    // 验证哈希
                    if (!verifyFileHash(apkFile, info.packageHash)) {
                        apkFile.delete();
                        throw new IOException("更新包校验失败");
                    }

                    mainHandler.post(() -> {
                        isDownloading = false;
                        showDownloadCompleteNotification(apkFile);
                        if (listener != null) {
                            listener.onDownloadComplete(apkFile);
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "下载失败: " + e.getMessage());
                mainHandler.post(() -> {
                    isDownloading = false;
                    if (listener != null) {
                        listener.onDownloadError(e.getMessage());
                    }
                });
            }
        });
    }

    /**
     * 取消下载
     */
    public void cancelDownload() {
        isCancelled = true;
        cancelNotification();
    }

    // ==================== 安装 APK ====================

    /**
     * 安装 APK
     */
    public void installApk(File apkFile) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Uri apkUri = FileProvider.getUriForFile(context,
                    context.getPackageName() + ".fileprovider", apkFile);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            intent.setDataAndType(Uri.fromFile(apkFile),
                    "application/vnd.android.package-archive");
        }

        context.startActivity(intent);
    }

    // ==================== 通知管理 ====================

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "更新通知",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("应用更新相关通知");
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void showUpdateNotification(UpdateInfo info, String title) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;

        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText("PrivateCloudDisk v" + info.latestVersion + " 可用")
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(info.releaseNotes))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        manager.notify(NOTIFICATION_ID, builder.build());
    }

    private void showForceUpdateNotification(UpdateInfo info) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("必须更新")
                .setContentText("必须更新到 v" + info.latestVersion + " 才能继续使用")
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        manager.notify(NOTIFICATION_ID + 1, builder.build());
    }

    private void updateDownloadNotification(int percent, long downloaded, long total, double speed) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("正在下载更新")
                .setContentText(percent + "% - " + formatSpeed(speed) + "/s")
                .setProgress(100, percent, false)
                .setOngoing(true);

        manager.notify(NOTIFICATION_ID, builder.build());
    }

    private void showDownloadCompleteNotification(File apkFile) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;

        Intent intent = new Intent(Intent.ACTION_VIEW);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Uri apkUri = FileProvider.getUriForFile(context,
                    context.getPackageName() + ".fileprovider", apkFile);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            intent.setDataAndType(Uri.fromFile(apkFile),
                    "application/vnd.android.package-archive");
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("更新已下载")
                .setContentText("点击安装 v" + currentUpdateInfo.latestVersion)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        manager.notify(NOTIFICATION_ID, builder.build());
    }

    private void cancelNotification() {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.cancel(NOTIFICATION_ID);
        }
    }

    // ==================== 定期检查 ====================

    private Handler periodicHandler = new Handler(Looper.getMainLooper());
    private Runnable periodicRunnable;

    /**
     * 启动定期检查
     */
    public void startPeriodicCheck() {
        stopPeriodicCheck();

        if (!isAutoCheckEnabled()) return;

        periodicRunnable = new Runnable() {
            @Override
            public void run() {
                executor.execute(() -> {
                    try {
                        VersionCheckResult result = checkUpdateSync();
                        if (result.success && result.hasUpdate) {
                            Log.d(TAG, "定期检查发现更新: v" + result.updateInfo.latestVersion);
                            if (result.updateInfo.isHotfix() && isHotUpdateEnabled()) {
                                mainHandler.post(() -> handleHotfix(result.updateInfo));
                            } else if (!result.updateInfo.forceUpdate) {
                                mainHandler.post(() ->
                                        showUpdateNotification(result.updateInfo, "新版本可用"));
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "定期检查失败: " + e.getMessage());
                    }
                });
                periodicHandler.postDelayed(this, CHECK_INTERVAL_MS);
            }
        };

        periodicHandler.postDelayed(periodicRunnable, CHECK_INTERVAL_MS);
        Log.d(TAG, "定期检查已启动");
    }

    /**
     * 停止定期检查
     */
    public void stopPeriodicCheck() {
        if (periodicRunnable != null) {
            periodicHandler.removeCallbacks(periodicRunnable);
            periodicRunnable = null;
        }
    }

    // ==================== 设置管理 ====================

    public boolean isAutoCheckEnabled() {
        return prefs.getBoolean("auto_check", true);
    }

    public void setAutoCheckEnabled(boolean enabled) {
        prefs.edit().putBoolean("auto_check", enabled).apply();
        if (enabled) {
            startPeriodicCheck();
        } else {
            stopPeriodicCheck();
        }
    }

    public boolean isAutoDownloadEnabled() {
        return prefs.getBoolean("auto_download", false);
    }

    public void setAutoDownloadEnabled(boolean enabled) {
        prefs.edit().putBoolean("auto_download", enabled).apply();
    }

    public boolean isHotUpdateEnabled() {
        return prefs.getBoolean("hot_update", true);
    }

    public void setHotUpdateEnabled(boolean enabled) {
        prefs.edit().putBoolean("hot_update", enabled).apply();
    }

    public String getUpdateChannel() {
        return prefs.getString("channel", "stable");
    }

    public void setUpdateChannel(String channel) {
        prefs.edit().putString("channel", channel).apply();
    }

    public String getCurrentVersion() {
        try {
            return context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "0.0.0";
        }
    }

    // ==================== 工具方法 ====================

    private boolean verifyFileHash(File file, String expectedHash) {
        if (expectedHash == null || expectedHash.isEmpty()) return true;

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            java.io.FileInputStream fis = new java.io.FileInputStream(file);
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
            fis.close();

            byte[] hashBytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }

            return sb.toString().equalsIgnoreCase(expectedHash);
        } catch (Exception e) {
            Log.e(TAG, "哈希校验失败: " + e.getMessage());
            return false;
        }
    }

    private String formatSpeed(double bytesPerSecond) {
        if (bytesPerSecond < 1024) return String.format("%.0f B", bytesPerSecond);
        if (bytesPerSecond < 1024 * 1024) return String.format("%.1f KB", bytesPerSecond / 1024);
        return String.format("%.1f MB", bytesPerSecond / (1024 * 1024));
    }

    public UpdateInfo getCurrentUpdateInfo() {
        return currentUpdateInfo;
    }

    public boolean isDownloading() {
        return isDownloading;
    }

    public void setUpdateListener(UpdateListener listener) {
        this.listener = listener;
    }

    public void destroy() {
        stopPeriodicCheck();
        executor.shutdown();
    }

    // ==================== API 响应模型 ====================

    private static class ApiResponse {
        int code;
        String message;
        UpdateInfo data;
    }
}