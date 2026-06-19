package com.privateclouddisk.android.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.privateclouddisk.android.PrivateCloudDiskApp;
import com.privateclouddisk.android.R;
import com.privateclouddisk.android.data.local.dao.UploadTaskDao;
import com.privateclouddisk.android.data.local.entity.UploadTaskEntity;
import com.privateclouddisk.android.data.remote.ApiClient;
import com.privateclouddisk.android.data.model.*;
import com.privateclouddisk.android.ui.main.MainActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import timber.log.Timber;

/**
 * 上传服务（前台服务）
 *
 * 对应 Windows 的 UploadManager
 * 使用 Android 的前台服务 + WorkManager 保证上传任务在后台持续运行
 */
@AndroidEntryPoint
public class UploadService extends Service {

    private static final int NOTIFICATION_ID = 1001;
    private static final int CHUNK_SIZE = 5 * 1024 * 1024; // 5MB

    @Inject ApiClient apiClient;
    @Inject UploadTaskDao uploadTaskDao;

    private final IBinder binder = new UploadBinder();
    private final CompositeDisposable disposables = new CompositeDisposable();
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final AtomicBoolean isUploading = new AtomicBoolean(false);

    public class UploadBinder extends Binder {
        public UploadService getService() {
            return UploadService.this;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, createNotification("准备上传...", 0));
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Timber.d("UploadService created");
    }

    /**
     * 开始上传文件
     */
    public void uploadFile(String localPath, String parentId, UploadCallback callback) {
        executor.submit(() -> {
            try {
                File file = new File(localPath);
                if (!file.exists()) {
                    callback.onError("文件不存在: " + localPath);
                    return;
                }

                String fileName = file.getName();
                long fileSize = file.length();
                String fileType = getMimeType(fileName);
                int totalChunks = (int) Math.ceil((double) fileSize / CHUNK_SIZE);
                String checksum = calculateMD5(file);

                // ── 1. 创建上传会话 ──
                updateNotification("正在创建上传会话...", 0);
                CreateUploadSessionRequest sessionReq = new CreateUploadSessionRequest(
                        fileName, fileSize, fileType, checksum, parentId,
                        totalChunks, CHUNK_SIZE);

                retrofit2.Response<ApiResponse<UploadSessionResponse>> sessionResp =
                        apiClient.getFileApi().createUploadSession(sessionReq).execute();

                if (!sessionResp.isSuccessful() || sessionResp.body() == null
                        || !sessionResp.body().isSuccess()) {
                    callback.onError("创建上传会话失败");
                    return;
                }

                String uploadId = sessionResp.body().getData().getUploadsId();

                // ── 2. 保存到数据库 ──
                UploadTaskEntity task = new UploadTaskEntity();
                task.setUploadId(uploadId);
                task.setFileName(fileName);
                task.setLocalPath(localPath);
                task.setParentId(parentId);
                task.setTotalBytes(fileSize);
                task.setTotalChunks(totalChunks);
                task.setFileChecksum(checksum);
                task.setFileType(fileType);
                task.setStatus(UploadTaskEntity.STATUS_UPLOADING);
                task.setCreatedAt(System.currentTimeMillis());
                uploadTaskDao.insert(task).subscribeOn(Schedulers.io()).subscribe();

                isUploading.set(true);

                // ── 3. 分块上传 ──
                try (InputStream is = new FileInputStream(file)) {
                    byte[] buffer = new byte[CHUNK_SIZE];
                    int chunkIndex = 0;
                    int bytesRead;
                    long uploadedBytes = 0;

                    while ((bytesRead = is.read(buffer)) > 0 && isUploading.get()) {
                        byte[] chunkData = new byte[bytesRead];
                        System.arraycopy(buffer, 0, chunkData, 0, bytesRead);

                        // 上传分块
                        RequestBody chunkBody = RequestBody.create(
                                MediaType.parse("application/octet-stream"), chunkData);
                        MultipartBody.Part filePart = MultipartBody.Part.createFormData(
                                "file", fileName, chunkBody);

                        retrofit2.Response<ApiResponse<Void>> chunkResp =
                                apiClient.getFileApi().uploadChunk(
                                        RequestBody.create(
                                                MediaType.parse("text/plain"), uploadId),
                                        RequestBody.create(
                                                MediaType.parse("text/plain"), String.valueOf(chunkIndex)),
                                        RequestBody.create(
                                                MediaType.parse("text/plain"), String.valueOf(totalChunks)),
                                        filePart
                                ).execute();

                        if (!chunkResp.isSuccessful()) {
                            task.setStatus(UploadTaskEntity.STATUS_FAILED);
                            task.setUploadedBytes(uploadedBytes);
                            task.setUploadedChunks(chunkIndex);
                            uploadTaskDao.update(task).subscribeOn(Schedulers.io()).subscribe();
                            callback.onError("上传分块 " + chunkIndex + " 失败");
                            return;
                        }

                        uploadedBytes += bytesRead;
                        chunkIndex++;

                        // 更新进度
                        task.setUploadedBytes(uploadedBytes);
                        task.setUploadedChunks(chunkIndex);
                        task.setUpdatedAt(System.currentTimeMillis());
                        uploadTaskDao.update(task).subscribeOn(Schedulers.io()).subscribe();

                        int progress = (int) (uploadedBytes * 100 / fileSize);
                        updateNotification("正在上传: " + fileName, progress);
                        callback.onProgress(uploadedBytes, fileSize, progress);
                    }
                }

                // ── 4. 完成上传 ──
                updateNotification("正在完成上传...", 100);
                retrofit2.Response<ApiResponse<NodeItem>> completeResp =
                        apiClient.getFileApi().completeUpload(
                                new CompleteUploadRequest(uploadId, fileName, parentId)).execute();

                if (completeResp.isSuccessful() && completeResp.body() != null
                        && completeResp.body().isSuccess()) {
                    task.setStatus(UploadTaskEntity.STATUS_COMPLETED);
                    task.setUploadedBytes(fileSize);
                    task.setUploadedChunks(totalChunks);
                    task.setUpdatedAt(System.currentTimeMillis());
                    uploadTaskDao.update(task).subscribeOn(Schedulers.io()).subscribe();

                    NodeItem result = completeResp.body().getData();
                    callback.onSuccess(result);
                    updateNotification("上传完成: " + fileName, 100);
                } else {
                    task.setStatus(UploadTaskEntity.STATUS_FAILED);
                    uploadTaskDao.update(task).subscribeOn(Schedulers.io()).subscribe();
                    callback.onError("完成上传失败");
                }

            } catch (Exception e) {
                Timber.e(e, "Upload failed");
                callback.onError("上传异常: " + e.getMessage());
            } finally {
                isUploading.set(false);
            }
        });
    }

    public void pauseUpload() {
        isUploading.set(false);
    }

    public boolean isUploading() {
        return isUploading.get();
    }

    private Notification createNotification(String content, int progress) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, PrivateCloudDiskApp.CHANNEL_UPLOAD)
                .setContentTitle("私有云上传")
                .setContentText(content)
                .setSmallIcon(R.drawable.ic_upload)
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

    private String calculateMD5(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        try (InputStream is = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) > 0) {
                md.update(buffer, 0, bytesRead);
            }
        }
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String getMimeType(String fileName) {
        String ext = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        switch (ext) {
            case "jpg": case "jpeg": return "image/jpeg";
            case "png": return "image/png";
            case "gif": return "image/gif";
            case "webp": return "image/webp";
            case "mp4": return "video/mp4";
            case "mp3": return "audio/mpeg";
            case "pdf": return "application/pdf";
            case "doc": return "application/msword";
            case "docx": return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls": return "application/vnd.ms-excel";
            case "xlsx": return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "txt": return "text/plain";
            case "zip": return "application/zip";
            default: return "application/octet-stream";
        }
    }

    @Override
    public void onDestroy() {
        disposables.clear();
        executor.shutdown();
        super.onDestroy();
    }

    public interface UploadCallback {
        void onProgress(long uploadedBytes, long totalBytes, int percent);
        void onSuccess(NodeItem result);
        void onError(String error);
    }
}