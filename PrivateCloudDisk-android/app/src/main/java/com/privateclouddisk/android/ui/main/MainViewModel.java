package com.privateclouddisk.android.ui.main;

import android.app.Activity;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.privateclouddisk.android.data.model.NodeItem;
import com.privateclouddisk.android.data.repository.AuthRepository;
import com.privateclouddisk.android.data.repository.FileRepository;
import com.privateclouddisk.android.util.SingleLiveEvent;

import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import timber.log.Timber;

/**
 * 主界面 ViewModel
 */
@HiltViewModel
public class MainViewModel extends ViewModel {

    private final AuthRepository authRepository;
    private final FileRepository fileRepository;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private final MutableLiveData<List<NodeItem>> fileList = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final SingleLiveEvent<Void> logoutEvent = new SingleLiveEvent<>();

    private String currentParentId = "";

    @Inject
    public MainViewModel(AuthRepository authRepository, FileRepository fileRepository) {
        this.authRepository = authRepository;
        this.fileRepository = fileRepository;
    }

    // ── Getters ──

    public LiveData<List<NodeItem>> getFileList() { return fileList; }
    public LiveData<Boolean> getIsLoading() { return isLoading; }
    public LiveData<String> getErrorMessage() { return errorMessage; }
    public SingleLiveEvent<Void> getLogoutEvent() { return logoutEvent; }
    public String getCurrentParentId() { return currentParentId; }

    // ── 文件操作 ──

    public void loadFiles(String parentId) {
        this.currentParentId = parentId;
        isLoading.setValue(true);

        disposables.add(
                fileRepository.getFileList(parentId, 1, 100)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                items -> {
                                    fileList.setValue(items);
                                    isLoading.setValue(false);
                                },
                                throwable -> {
                                    Timber.e(throwable, "Failed to load files");
                                    errorMessage.setValue(throwable.getMessage());
                                    isLoading.setValue(false);
                                }
                        )
        );
    }

    public void navigateToFolder(String folderId) {
        loadFiles(folderId);
    }

    public void navigateUp() {
        // 简化：回到根目录
        if (currentParentId != null && !currentParentId.isEmpty()) {
            loadFiles("");
        }
    }

    // ── 创建文件夹 ──

    public void showCreateFolderDialog(Activity activity) {
        final EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("输入文件夹名称");

        new AlertDialog.Builder(activity)
                .setTitle("新建文件夹")
                .setView(input)
                .setPositiveButton("确定", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        createFolder(name);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void createFolder(String name) {
        isLoading.setValue(true);
        disposables.add(
                fileRepository.createFolder(currentParentId, name)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                item -> {
                                    loadFiles(currentParentId);
                                },
                                throwable -> {
                                    errorMessage.setValue("创建文件夹失败: " + throwable.getMessage());
                                    isLoading.setValue(false);
                                }
                        )
        );
    }

    // ── 删除/移动/重命名 ──

    public void deleteFile(String fileId) {
        disposables.add(
                fileRepository.deleteFile(fileId)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                () -> loadFiles(currentParentId),
                                throwable -> errorMessage.setValue("删除失败: " + throwable.getMessage())
                        )
        );
    }

    public void renameFile(String fileId, String newName) {
        disposables.add(
                fileRepository.renameFile(fileId, newName)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                () -> loadFiles(currentParentId),
                                throwable -> errorMessage.setValue("重命名失败: " + throwable.getMessage())
                        )
        );
    }

    // ── 收藏/回收站 ──

    public void loadFavorites() {
        isLoading.setValue(true);
        disposables.add(
                fileRepository.getFavorites(1, 100)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                items -> {
                                    fileList.setValue(items);
                                    isLoading.setValue(false);
                                },
                                throwable -> {
                                    errorMessage.setValue(throwable.getMessage());
                                    isLoading.setValue(false);
                                }
                        )
        );
    }

    public void loadTrash() {
        isLoading.setValue(true);
        disposables.add(
                fileRepository.getTrashList(1, 100)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                items -> {
                                    fileList.setValue(items);
                                    isLoading.setValue(false);
                                },
                                throwable -> {
                                    errorMessage.setValue(throwable.getMessage());
                                    isLoading.setValue(false);
                                }
                        )
        );
    }

    // ── 登出 ──

    public void logout() {
        disposables.add(
                authRepository.logout()
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                success -> logoutEvent.call(),
                                throwable -> logoutEvent.call()
                        )
        );
    }

    @Override
    protected void onCleared() {
        disposables.clear();
        super.onCleared();
    }
}