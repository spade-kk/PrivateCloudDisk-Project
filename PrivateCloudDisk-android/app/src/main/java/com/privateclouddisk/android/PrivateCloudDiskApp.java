package com.privateclouddisk.android;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

import dagger.hilt.android.HiltAndroidApp;
import timber.log.Timber;

/**
 * PrivateCloudDisk Android 应用入口
 *
 * 对应 Windows 的 App.xaml.cs，负责：
 * - 全局初始化（Hilt DI、日志、通知渠道）
 * - 应用级配置
 * - 崩溃捕获
 */
@HiltAndroidApp
public class PrivateCloudDiskApp extends Application {

    // ── 通知渠道 ID ──
    public static final String CHANNEL_UPLOAD = "channel_upload";
    public static final String CHANNEL_DOWNLOAD = "channel_download";
    public static final String CHANNEL_SYNC = "channel_sync";
    public static final String CHANNEL_IM = "channel_im";
    public static final String CHANNEL_GENERAL = "channel_general";

    @Override
    public void onCreate() {
        super.onCreate();

        // ── 1. 初始化日志 ──
        if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree());
        }

        // ── 2. 创建通知渠道 ──
        createNotificationChannels();

        Timber.i("PrivateCloudDisk Application initialized");
    }

    /**
     * 创建通知渠道（Android 8.0+）
     */
    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);

            // 上传渠道
            NotificationChannel uploadChannel = new NotificationChannel(
                    CHANNEL_UPLOAD,
                    getString(R.string.channel_upload),
                    NotificationManager.IMPORTANCE_LOW
            );
            uploadChannel.setDescription(getString(R.string.channel_upload_desc));
            uploadChannel.setShowBadge(false);
            nm.createNotificationChannel(uploadChannel);

            // 下载渠道
            NotificationChannel downloadChannel = new NotificationChannel(
                    CHANNEL_DOWNLOAD,
                    getString(R.string.channel_download),
                    NotificationManager.IMPORTANCE_LOW
            );
            downloadChannel.setDescription(getString(R.string.channel_download_desc));
            downloadChannel.setShowBadge(false);
            nm.createNotificationChannel(downloadChannel);

            // 同步渠道
            NotificationChannel syncChannel = new NotificationChannel(
                    CHANNEL_SYNC,
                    getString(R.string.channel_sync),
                    NotificationManager.IMPORTANCE_LOW
            );
            syncChannel.setDescription(getString(R.string.channel_sync_desc));
            syncChannel.setShowBadge(false);
            nm.createNotificationChannel(syncChannel);

            // IM 渠道
            NotificationChannel imChannel = new NotificationChannel(
                    CHANNEL_IM,
                    getString(R.string.channel_im),
                    NotificationManager.IMPORTANCE_HIGH
            );
            imChannel.setDescription(getString(R.string.channel_im_desc));
            imChannel.enableVibration(true);
            nm.createNotificationChannel(imChannel);

            // 通用渠道
            NotificationChannel generalChannel = new NotificationChannel(
                    CHANNEL_GENERAL,
                    getString(R.string.channel_general),
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            generalChannel.setDescription(getString(R.string.channel_general_desc));
            nm.createNotificationChannel(generalChannel);
        }
    }
}