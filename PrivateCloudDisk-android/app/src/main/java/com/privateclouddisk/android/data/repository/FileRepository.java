package com.privateclouddisk.android.data.repository;

import com.privateclouddisk.android.data.local.AppDatabase;
import com.privateclouddisk.android.data.local.entity.FileCacheEntity;
import com.privateclouddisk.android.data.model.*;
import com.privateclouddisk.android.data.remote.ApiClient;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import timber.log.Timber;

/**
 * 文件仓库
 * 管理文件列表、详情、搜索、收藏、回收站等
 */
@Singleton
public class FileRepository {

    private final ApiClient apiClient;
    private final AppDatabase database;

    @Inject
    public FileRepository(ApiClient apiClient, AppDatabase database) {
        this.apiClient = apiClient;
        this.database = database;
    }

    // ── 文件列表 ──

    /**
     * 获取文件列表（本地缓存优先）
     */
    public Flowable<List<NodeItem>> getFileList(String parentId, int page, int pageSize) {
        return Single.<List<NodeItem>>create(emitter -> {
            try {
                retrofit2.Response<ApiResponse<List<NodeItem>>> response =
                        apiClient.getFileApi().getFiles(parentId, page, pageSize).execute();

                if (response.isSuccessful() && response.body() != null
                        && response.body().isSuccess()) {
                    List<NodeItem> items = response.body().getData();
                    if (items == null) items = new ArrayList<>();

                    // 缓存到本地数据库
                    cacheFiles(items, parentId);

                    emitter.onSuccess(items);
                } else {
                    emitter.onError(new ApiException(response.code(), "获取文件列表失败"));
                }
            } catch (Exception e) {
                Timber.e(e, "Failed to get file list");
                emitter.onError(e);
            }
        })
                .toFlowable()
                .onErrorResumeNext(throwable -> {
                    // 网络失败时从本地缓存获取
                    return database.fileCacheDao().getFilesByParent(parentId)
                            .map(entities -> {
                                List<NodeItem> items = new ArrayList<>();
                                for (FileCacheEntity entity : entities) {
                                    items.add(mapToNodeItem(entity));
                                }
                                return items;
                            });
                })
                .subscribeOn(Schedulers.io());
    }

    /**
     * 获取文件详情
     */
    public Single<NodeItem> getFileDetail(String fileId) {
        return Single.<NodeItem>create(emitter -> {
            try {
                retrofit2.Response<ApiResponse<NodeItem>> response =
                        apiClient.getFileApi().getFileDetail(fileId).execute();

                if (response.isSuccessful() && response.body() != null
                        && response.body().isSuccess()) {
                    emitter.onSuccess(response.body().getData());
                } else {
                    emitter.onError(new ApiException(response.code(), "获取文件详情失败"));
                }
            } catch (Exception e) {
                Timber.e(e, "Failed to get file detail");
                emitter.onError(e);
            }
        }).subscribeOn(Schedulers.io());
    }

    // ── 文件操作 ──

    public Single<NodeItem> createFolder(String parentId, String folderName) {
        return Single.<NodeItem>create(emitter -> {
            try {
                retrofit2.Response<ApiResponse<NodeItem>> response =
                        apiClient.getFileApi().createFolder(
                                new CreateFolderRequest(parentId, folderName)).execute();

                if (response.isSuccessful() && response.body() != null
                        && response.body().isSuccess()) {
                    emitter.onSuccess(response.body().getData());
                } else {
                    ApiResponse<NodeItem> apiResponse = response.body();
                    emitter.onError(new ApiException(
                            apiResponse != null ? apiResponse.getCode() : response.code(),
                            apiResponse != null ? apiResponse.getMessage() : "创建文件夹失败"));
                }
            } catch (Exception e) {
                Timber.e(e, "Failed to create folder");
                emitter.onError(e);
            }
        }).subscribeOn(Schedulers.io());
    }

    public Completable renameFile(String fileId, String newName) {
        return Completable.create(emitter -> {
            try {
                retrofit2.Response<ApiResponse<Void>> response =
                        apiClient.getFileApi().renameFile(fileId,
                                new RenameRequest(newName)).execute();

                if (response.isSuccessful()) {
                    emitter.onComplete();
                } else {
                    emitter.onError(new ApiException(response.code(), "重命名失败"));
                }
            } catch (Exception e) {
                Timber.e(e, "Failed to rename file");
                emitter.onError(e);
            }
        }).subscribeOn(Schedulers.io());
    }

    public Completable moveFile(String fileId, String targetParentId) {
        return Completable.create(emitter -> {
            try {
                retrofit2.Response<ApiResponse<Void>> response =
                        apiClient.getFileApi().moveFile(fileId,
                                new MoveFileRequest(targetParentId)).execute();

                if (response.isSuccessful()) {
                    emitter.onComplete();
                } else {
                    emitter.onError(new ApiException(response.code(), "移动失败"));
                }
            } catch (Exception e) {
                Timber.e(e, "Failed to move file");
                emitter.onError(e);
            }
        }).subscribeOn(Schedulers.io());
    }

    public Completable deleteFile(String fileId) {
        return Completable.create(emitter -> {
            try {
                retrofit2.Response<ApiResponse<Void>> response =
                        apiClient.getFileApi().deleteFile(fileId).execute();

                if (response.isSuccessful()) {
                    emitter.onComplete();
                } else {
                    emitter.onError(new ApiException(response.code(), "删除失败"));
                }
            } catch (Exception e) {
                Timber.e(e, "Failed to delete file");
                emitter.onError(e);
            }
        }).subscribeOn(Schedulers.io());
    }

    public Completable batchDelete(List<String> ids) {
        return Completable.create(emitter -> {
            try {
                retrofit2.Response<ApiResponse<Void>> response =
                        apiClient.getFileApi().batchDelete(
                                new BatchOperationRequest(ids)).execute();

                if (response.isSuccessful()) {
                    emitter.onComplete();
                } else {
                    emitter.onError(new ApiException(response.code(), "批量删除失败"));
                }
            } catch (Exception e) {
                Timber.e(e, "Failed to batch delete");
                emitter.onError(e);
            }
        }).subscribeOn(Schedulers.io());
    }

    // ── 搜索 ──

    public Single<List<NodeItem>> searchFiles(String keyword, String fileType,
                                               int page, int pageSize) {
        return Single.<List<NodeItem>>create(emitter -> {
            try {
                retrofit2.Response<ApiResponse<List<NodeItem>>> response =
                        apiClient.getFileApi().searchFiles(keyword, fileType, page, pageSize).execute();

                if (response.isSuccessful() && response.body() != null
                        && response.body().isSuccess()) {
                    emitter.onSuccess(response.body().getData());
                } else {
                    emitter.onError(new ApiException(response.code(), "搜索失败"));
                }
            } catch (Exception e) {
                Timber.e(e, "Failed to search files");
                emitter.onError(e);
            }
        }).subscribeOn(Schedulers.io());
    }

    // ── 收藏 ──

    public Single<List<NodeItem>> getFavorites(int page, int pageSize) {
        return Single.<List<NodeItem>>create(emitter -> {
            try {
                retrofit2.Response<ApiResponse<List<NodeItem>>> response =
                        apiClient.getFavoritesApi().getFavorites(page, pageSize).execute();

                if (response.isSuccessful() && response.body() != null
                        && response.body().isSuccess()) {
                    emitter.onSuccess(response.body().getData());
                } else {
                    emitter.onError(new ApiException(response.code(), "获取收藏列表失败"));
                }
            } catch (Exception e) {
                Timber.e(e, "Failed to get favorites");
                emitter.onError(e);
            }
        }).subscribeOn(Schedulers.io());
    }

    public Completable addFavorite(String fileId) {
        return Completable.create(emitter -> {
            try {
                retrofit2.Response<ApiResponse<Void>> response =
                        apiClient.getFavoritesApi().addFavorite(fileId).execute();

                if (response.isSuccessful()) {
                    emitter.onComplete();
                } else {
                    emitter.onError(new ApiException(response.code(), "添加收藏失败"));
                }
            } catch (Exception e) {
                Timber.e(e, "Failed to add favorite");
                emitter.onError(e);
            }
        }).subscribeOn(Schedulers.io());
    }

    public Completable removeFavorite(String fileId) {
        return Completable.create(emitter -> {
            try {
                retrofit2.Response<ApiResponse<Void>> response =
                        apiClient.getFavoritesApi().removeFavorite(fileId).execute();

                if (response.isSuccessful()) {
                    emitter.onComplete();
                } else {
                    emitter.onError(new ApiException(response.code(), "取消收藏失败"));
                }
            } catch (Exception e) {
                Timber.e(e, "Failed to remove favorite");
                emitter.onError(e);
            }
        }).subscribeOn(Schedulers.io());
    }

    // ── 回收站 ──

    public Single<List<NodeItem>> getTrashList(int page, int pageSize) {
        return Single.<List<NodeItem>>create(emitter -> {
            try {
                retrofit2.Response<ApiResponse<List<NodeItem>>> response =
                        apiClient.getTrashApi().getTrashList(page, pageSize).execute();

                if (response.isSuccessful() && response.body() != null
                        && response.body().isSuccess()) {
                    emitter.onSuccess(response.body().getData());
                } else {
                    emitter.onError(new ApiException(response.code(), "获取回收站失败"));
                }
            } catch (Exception e) {
                Timber.e(e, "Failed to get trash list");
                emitter.onError(e);
            }
        }).subscribeOn(Schedulers.io());
    }

    public Completable restoreFile(String fileId) {
        return Completable.create(emitter -> {
            try {
                retrofit2.Response<ApiResponse<Void>> response =
                        apiClient.getTrashApi().restoreFile(fileId).execute();

                if (response.isSuccessful()) {
                    emitter.onComplete();
                } else {
                    emitter.onError(new ApiException(response.code(), "恢复失败"));
                }
            } catch (Exception e) {
                Timber.e(e, "Failed to restore file");
                emitter.onError(e);
            }
        }).subscribeOn(Schedulers.io());
    }

    public Completable permanentlyDelete(String fileId) {
        return Completable.create(emitter -> {
            try {
                retrofit2.Response<ApiResponse<Void>> response =
                        apiClient.getTrashApi().permanentlyDelete(fileId).execute();

                if (response.isSuccessful()) {
                    emitter.onComplete();
                } else {
                    emitter.onError(new ApiException(response.code(), "永久删除失败"));
                }
            } catch (Exception e) {
                Timber.e(e, "Failed to permanently delete");
                emitter.onError(e);
            }
        }).subscribeOn(Schedulers.io());
    }

    public Completable clearTrash() {
        return Completable.create(emitter -> {
            try {
                retrofit2.Response<ApiResponse<Void>> response =
                        apiClient.getTrashApi().clearTrash().execute();

                if (response.isSuccessful()) {
                    emitter.onComplete();
                } else {
                    emitter.onError(new ApiException(response.code(), "清空回收站失败"));
                }
            } catch (Exception e) {
                Timber.e(e, "Failed to clear trash");
                emitter.onError(e);
            }
        }).subscribeOn(Schedulers.io());
    }

    // ── 操作凭证 ──

    public Single<String> getOperationToken(String fileId, String operationType) {
        return Single.<String>create(emitter -> {
            try {
                retrofit2.Response<ApiResponse<OperationTokenResponse>> response =
                        apiClient.getFileApi().getOperationToken(fileId,
                                new OperationTokenRequest(fileId, operationType)).execute();

                if (response.isSuccessful() && response.body() != null
                        && response.body().isSuccess() && response.body().getData() != null) {
                    emitter.onSuccess(response.body().getData().getOperationToken());
                } else {
                    emitter.onError(new ApiException(response.code(), "获取操作凭证失败"));
                }
            } catch (Exception e) {
                Timber.e(e, "Failed to get operation token");
                emitter.onError(e);
            }
        }).subscribeOn(Schedulers.io());
    }

    // ── 本地缓存 ──

    private void cacheFiles(List<NodeItem> items, String parentId) {
        if (items == null) return;
        List<FileCacheEntity> entities = new ArrayList<>();
        for (NodeItem item : items) {
            FileCacheEntity entity = mapToEntity(item);
            entity.setParentId(parentId != null ? parentId : "");
            entities.add(entity);
        }
        database.fileCacheDao().deleteByParentId(parentId != null ? parentId : "")
                .andThen(database.fileCacheDao().insertAll(entities))
                .subscribeOn(Schedulers.io())
                .subscribe(() -> {}, throwable ->
                        Timber.e(throwable, "Failed to cache files"));
    }

    private FileCacheEntity mapToEntity(NodeItem item) {
        FileCacheEntity entity = new FileCacheEntity();
        entity.setId(item.getEffectiveId());
        entity.setNodeId(item.getNodeId());
        entity.setFileId(item.getFileId());
        entity.setName(item.getEffectiveName());
        entity.setSize(item.getEffectiveSize());
        entity.setFile(item.isFile());
        entity.setFavorite(item.isFavorite());
        entity.setFileType(item.getFileType());
        entity.setUploadedTime(item.getUploadedTime());
        entity.setUpdatedTime(item.getUpdatedTime());
        entity.setCachedAt(System.currentTimeMillis());
        return entity;
    }

    private NodeItem mapToNodeItem(FileCacheEntity entity) {
        NodeItem item = new NodeItem();
        item.setId(entity.getId());
        item.setNodeId(entity.getNodeId());
        item.setFileId(entity.getFileId());
        item.setName(entity.getName());
        item.setSize(entity.getSize());
        item.setFile(entity.isFile());
        item.setFavorite(entity.isFavorite());
        item.setFileType(entity.getFileType());
        item.setUploadedTime(entity.getUploadedTime());
        item.setUpdatedTime(entity.getUpdatedTime());
        return item;
    }
}