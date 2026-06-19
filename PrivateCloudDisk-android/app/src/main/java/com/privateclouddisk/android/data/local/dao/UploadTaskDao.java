package com.privateclouddisk.android.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.privateclouddisk.android.data.local.entity.UploadTaskEntity;

import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;

/**
 * 上传任务 DAO
 */
@Dao
public interface UploadTaskDao {

    @Query("SELECT * FROM upload_tasks ORDER BY createdAt DESC")
    Flowable<List<UploadTaskEntity>> getAllTasks();

    @Query("SELECT * FROM upload_tasks WHERE status = :status ORDER BY createdAt DESC")
    Flowable<List<UploadTaskEntity>> getTasksByStatus(int status);

    @Query("SELECT * FROM upload_tasks WHERE uploadId = :uploadId")
    Single<UploadTaskEntity> getTaskById(String uploadId);

    @Query("SELECT * FROM upload_tasks WHERE status IN (0, 1) ORDER BY createdAt ASC")
    Single<List<UploadTaskEntity>> getPendingAndUploadingTasks();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insert(UploadTaskEntity task);

    @Update
    Completable update(UploadTaskEntity task);

    @Query("DELETE FROM upload_tasks WHERE uploadId = :uploadId")
    Completable deleteById(String uploadId);

    @Query("DELETE FROM upload_tasks WHERE status = 3")
    Completable deleteCompleted();

    @Query("DELETE FROM upload_tasks")
    Completable deleteAll();

    @Query("SELECT COUNT(*) FROM upload_tasks WHERE status IN (0, 1)")
    Flowable<Integer> getActiveTaskCount();
}