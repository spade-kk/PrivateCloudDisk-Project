package com.privateclouddisk.android.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.content.ContextCompat;

import com.privateclouddisk.android.data.local.PreferenceManager;
import com.privateclouddisk.android.service.SyncService;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import timber.log.Timber;

/**
 * 开机启动接收器
 * 设备启动后自动启动同步服务
 */
@AndroidEntryPoint
public class BootReceiver extends BroadcastReceiver {

    @Inject PreferenceManager preferenceManager;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Timber.d("Boot completed, starting sync service");

            if (preferenceManager.isAutoSyncEnabled()) {
                Intent syncIntent = new Intent(context, SyncService.class);
                ContextCompat.startForegroundService(context, syncIntent);
            }
        }
    }
}