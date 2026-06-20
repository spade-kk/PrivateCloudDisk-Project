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
import com.privateclouddisk.android.data.local.PreferenceManager;
import com.privateclouddisk.android.data.repository.FileRepository;
import com.privateclouddisk.android.ui.main.MainActivity;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import timber.log.Timber;

/**
 * 同步服务（前台服务）
 *
 * 对应 Windows 的 SyncService
 * 定时同步云端文件变更，支持自动备份
 */
@AndroidEntryPoint
public class SyncService extends Service {

    private static final int NOTIFICATION_ID = 1003;
    private static final long SYNC_INTERVAL_MINUTES = 5;

    @Inject FileRepository fileRepository;
    @Inject PreferenceManager preferenceManager;

    private final IBinder binder = new SyncBinder();
    private final CompositeDisposable disposables = new CompositeDisposable();
    private ScheduledExecutorService scheduler;
    private final AtomicBoolean isSyncing = new AtomicBoolean(false);

    public class SyncBinder extends Binder {
        public SyncService getService() {
            return SyncService.this;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, createNotification("同步服务运行中", false));
        startPeriodicSync();
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Timber.d("SyncService created");
    }

    /**
     * 启动定时同步
     */
    private void startPeriodicSync() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
        scheduler = Executors.newSingleThreadScheduledExecutor();

        // 立即执行一次
        scheduler.execute(this::performSync);

        // 定时执行
        scheduler.scheduleWithFixedDelay(
                this::performSync,
                SYNC_INTERVAL_MINUTES,
                SYNC_INTERVAL_MINUTES,
                TimeUnit.MINUTES
        );
    }

    /**
     * 执行同步
     */
    private void performSync() {
        if (!preferenceManager.isAutoSyncEnabled()) return;
        if (isSyncing.get()) return;

        isSyncing.set(true);
        updateNotification("正在同步...", true);

        disposables.add(
                fileRepository.getFileList("", 1, 50)
                        .subscribe(
                                items -> {
                                    Timber.d("Sync completed: %d items", items != null ? items.size() : 0);
                                    updateNotification("同步完成", false);
                                    isSyncing.set(false);
                                },
                                throwable -> {
                                    Timber.e(throwable, "Sync failed");
                                    updateNotification("同步失败", false);
                                    isSyncing.set(false);
                                }
                        )
        );
    }

    /**
     * 手动触发同步
     */
    public void triggerSync() {
        scheduler.execute(this::performSync);
    }

    public boolean isSyncing() {
        return isSyncing.get();
    }

    private Notification createNotification(String content, boolean syncing) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, PrivateCloudDiskApp.CHANNEL_SYNC)
                .setContentTitle("私有云同步")
                .setContentText(content)
                .setSmallIcon(R.drawable.ic_sync)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build();
    }

    private void updateNotification(String content, boolean syncing) {
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID,
                createNotification(content, syncing));
    }

    @Override
    public void onDestroy() {
        disposables.clear();
        if (scheduler != null) {
            scheduler.shutdown();
        }
        super.onDestroy();
    }
}