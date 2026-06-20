package com.privateclouddisk.android.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.privateclouddisk.android.PrivateCloudDiskApp;
import com.privateclouddisk.android.R;
import com.privateclouddisk.android.data.remote.ApiClient;
import com.privateclouddisk.android.ui.main.MainActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import okhttp3.ResponseBody;
import timber.log.Timber;

/**
 * 下载服务（前台服务）
 *
 * 对应 Windows 的 DownloadManager
 * 支持大文件流式下载，带进度通知
 */
@AndroidEntryPoint
public class DownloadService extends Service {

    private static final int NOTIFICATION_ID = 1002;

    @Inject ApiClient apiClient;

    private final IBinder binder = new DownloadBinder();
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final AtomicBoolean isDownloading = new AtomicBoolean(false);

    public class DownloadBinder extends Binder {
        public DownloadService getService() {
            return DownloadService.this;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, createNotification("准备下载...", 0));
        return START_STICKY;
    }

    /**
     * 下载文件
     */
    public void downloadFile(String fileId, String operationToken,
                              String savePath, String fileName,
                              DownloadCallback callback) {
        executor.submit(() -> {
            isDownloading.set(true);
            try {
                updateNotification("正在下载: " + fileName, 0);

                // 获取下载流
                retrofit2.Response<ResponseBody> response =
                        apiClient.getFileApi().downloadFile(fileId, operationToken).execute();

                if (!response.isSuccessful() || response.body() == null) {
                    callback.onError("下载请求失败: " + response.code());
                    return;
                }

                ResponseBody body = response.body();
                long totalBytes = body.contentLength();

                // 保存到本地
                File dir = new File(savePath);
                if (!dir.exists()) dir.mkdirs();
                File outFile = new File(dir, fileName);

                try (InputStream is = body.byteStream();
                     FileOutputStream fos = new FileOutputStream(outFile)) {

                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    long downloadedBytes = 0;
                    long lastUpdate = System.currentTimeMillis();

                    while ((bytesRead = is.read(buffer)) > 0 && isDownloading.get()) {
                        fos.write(buffer, 0, bytesRead);
                        downloadedBytes += bytesRead;

                        // 限制更新频率
                        long now = System.currentTimeMillis();
                        if (now - lastUpdate > 500) {
                            int progress = totalBytes > 0
                                    ? (int) (downloadedBytes * 100 / totalBytes) : -1;
                            updateNotification("正在下载: " + fileName, progress);
                            callback.onProgress(downloadedBytes, totalBytes, progress);
                            lastUpdate = now;
                        }
                    }
                }

                if (isDownloading.get()) {
                    updateNotification("下载完成: " + fileName, 100);
                    callback.onSuccess(outFile.getAbsolutePath());
                } else {
                    outFile.delete(); // 取消下载，删除部分文件
                    callback.onError("下载已取消");
                }

            } catch (Exception e) {
                Timber.e(e, "Download failed");
                callback.onError("下载异常: " + e.getMessage());
            } finally {
                isDownloading.set(false);
            }
        });
    }

    public void cancelDownload() {
        isDownloading.set(false);
    }

    private Notification createNotification(String content, int progress) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, PrivateCloudDiskApp.CHANNEL_DOWNLOAD)
                .setContentTitle("私有云下载")
                .setContentText(content)
                .setSmallIcon(R.drawable.ic_download)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .setProgress(100, progress, progress < 0)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void updateNotification(String content, int progress) {
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID,
                createNotification(content, progress));
    }

    @Override
    public void onDestroy() {
        executor.shutdown();
        super.onDestroy();
    }

    public interface DownloadCallback {
        void onProgress(long downloadedBytes, long totalBytes, int percent);
        void onSuccess(String localPath);
        void onError(String error);
    }
}