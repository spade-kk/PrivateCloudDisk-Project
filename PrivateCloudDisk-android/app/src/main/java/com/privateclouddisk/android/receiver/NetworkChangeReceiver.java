package com.privateclouddisk.android.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import org.greenrobot.eventbus.EventBus;

import timber.log.Timber;

/**
 * 网络变化接收器
 * 监听网络状态变化，WiFi/移动网络切换时通知
 */
public class NetworkChangeReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        ConnectivityManager cm = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return;

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();
        boolean isWifi = activeNetwork != null
                && activeNetwork.getType() == ConnectivityManager.TYPE_WIFI;

        Timber.d("Network changed: connected=%b, wifi=%b", isConnected, isWifi);

        // 通过 EventBus 通知网络状态变化
        EventBus.getDefault().post(new NetworkChangeEvent(isConnected, isWifi));
    }

    /**
     * 网络变化事件
     */
    public static class NetworkChangeEvent {
        private final boolean isConnected;
        private final boolean isWifi;

        public NetworkChangeEvent(boolean isConnected, boolean isWifi) {
            this.isConnected = isConnected;
            this.isWifi = isWifi;
        }

        public boolean isConnected() { return isConnected; }
        public boolean isWifi() { return isWifi; }
    }
}