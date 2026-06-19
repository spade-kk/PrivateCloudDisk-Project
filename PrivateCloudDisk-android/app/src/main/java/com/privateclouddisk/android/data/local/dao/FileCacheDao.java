package com.privateclouddisk.android.data.local.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.privateclouddisk.android.data.local.entity.FileCacheEntity;

import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;

/**
 * 文件缓存 DAO
 */
@Dao
public interface FileCacheDao {

    @Query("SELECT * FROM file_cache WHERE parentId = :parentId ORDER BY isFile ASC, name ASC")
    Flowable<List<FileCacheEntity>> getFilesByParent(String parentId);

    @Query("SELECT * FROM file_cache WHERE id = :id")
    Single<FileCacheEntity> getFileById(String id);

    @Query("SELECT * FROM file_cache WHERE name LIKE '%' || :query || '%'")
    Flowable<List<FileCacheEntity>> searchFiles(String query);

    @Query("SELECT * FROM file_cache WHERE isFavorite = 1")
    Flowable<List<FileCacheEntity>> getFavorites();

    @Query("SELECT * FROM file_cache WHERE isSynced = 0")
    Single<List<FileCacheEntity>> getUnsyncedFiles();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insertAll(List<FileCacheEntity> files);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insert(FileCacheEntity file);

    @Update
    Completable update(FileCacheEntity file);

    @Delete
    Completable delete(FileCacheEntity file);

    @Query("DELETE FROM file_cache WHERE parentId = :parentId")
    Completable deleteByParentId(String parentId);

    @Query("DELETE FROM file_cache")
    Completable deleteAll();

    @Query("SELECT COUNT(*) FROM file_cache")
    Single<Integer> getCount();
}