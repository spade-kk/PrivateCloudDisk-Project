package com.privateclouddisk.android.data.local;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import com.privateclouddisk.android.data.local.dao.FileCacheDao;
import com.privateclouddisk.android.data.local.dao.UploadTaskDao;
import com.privateclouddisk.android.data.local.entity.FileCacheEntity;
import com.privateclouddisk.android.data.local.entity.UploadTaskEntity;

/**
 * Room 数据库
 * 提供本地持久化存储，支持离线访问
 */
@Database(
        entities = {
                FileCacheEntity.class,
                UploadTaskEntity.class
        },
        version = 1,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    public abstract FileCacheDao fileCacheDao();
    public abstract UploadTaskDao uploadTaskDao();
}