package com.privateclouddisk.android.util;

import android.util.Log;

import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 单次事件 LiveData
 * 用于一次性事件（如导航、Toast），避免旋转屏幕时重复触发
 */
public class SingleLiveEvent<T> extends MutableLiveData<T> {

    private static final String TAG = "SingleLiveEvent";
    private final AtomicBoolean pending = new AtomicBoolean(false);

    @MainThread
    public void observe(LifecycleOwner owner, final Observer<? super T> observer) {
        if (hasActiveObservers()) {
            Log.w(TAG, "Multiple observers registered but only one will be notified of changes.");
        }

        super.observe(owner, t -> {
            if (pending.compareAndSet(true, false)) {
                observer.onChanged(t);
            }
        });
    }

    @MainThread
    public void setValue(@Nullable T value) {
        pending.set(true);
        super.setValue(value);
    }

    /**
     * 调用此方法触发事件
     */
    @MainThread
    public void call() {
        setValue(null);
    }

    /**
     * 检查事件是否已被处理
     */
    public boolean isHandled() {
        return !pending.get();
    }

    /**
     * 标记事件已处理
     */
    public void handle() {
        pending.set(false);
    }
}