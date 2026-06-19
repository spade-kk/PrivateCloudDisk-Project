package com.privateclouddisk.android.di;

import android.content.Context;

import androidx.room.Room;

import com.privateclouddisk.android.data.local.AppDatabase;
import com.privateclouddisk.android.data.local.PreferenceManager;
import com.privateclouddisk.android.data.local.SecurePreferenceManager;
import com.privateclouddisk.android.data.local.dao.FileCacheDao;
import com.privateclouddisk.android.data.local.dao.UploadTaskDao;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;

/**
 * Hilt 依赖注入模块
 * 提供应用级单例依赖
 */
@Module
@InstallIn(SingletonComponent.class)
public class AppModule {

    @Provides
    @Singleton
    public PreferenceManager providePreferenceManager(@ApplicationContext Context context) {
        return new SecurePreferenceManager(context);
    }

    @Provides
    @Singleton
    public AppDatabase provideAppDatabase(@ApplicationContext Context context) {
        return Room.databaseBuilder(
                context.getApplicationContext(),
                AppDatabase.class,
                "privateclouddisk.db"
        )
                .fallbackToDestructiveMigration()
                .build();
    }

    @Provides
    @Singleton
    public FileCacheDao provideFileCacheDao(AppDatabase database) {
        return database.fileCacheDao();
    }

    @Provides
    @Singleton
    public UploadTaskDao provideUploadTaskDao(AppDatabase database) {
        return database.uploadTaskDao();
    }
}