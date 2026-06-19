package com.privateclouddisk.android.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.privateclouddisk.android.R;
import com.privateclouddisk.android.data.model.NodeItem;
import com.privateclouddisk.android.ui.files.FileDetailActivity;
import com.privateclouddisk.android.ui.media.MediaPlayerActivity;

import java.util.List;

/**
 * 文件列表 Fragment
 */
public class FileListFragment extends Fragment {

    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private View emptyView;
    private FileListAdapter adapter;
    private MainViewModel viewModel;

    private boolean isFavorites = false;
    private boolean isTrash = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                              @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_file_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        if (args != null) {
            isFavorites = args.getBoolean("favorites", false);
            isTrash = args.getBoolean("trash", false);
        }

        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        initViews(view);
        setupRecyclerView();
        setupObservers();
        loadData();
    }

    private void initViews(View view) {
        recyclerView = view.findViewById(R.id.recycler_view);
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh);
        emptyView = view.findViewById(R.id.empty_view);

        swipeRefreshLayout.setOnRefreshListener(this::loadData);
    }

    private void setupRecyclerView() {
        adapter = new FileListAdapter(item -> {
            onFileClick(item);
        }, item -> {
            onFileLongClick(item);
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.addItemDecoration(new DividerItemDecoration(requireContext(),
                DividerItemDecoration.VERTICAL));
        recyclerView.setAdapter(adapter);
    }

    private void setupObservers() {
        viewModel.getFileList().observe(getViewLifecycleOwner(), items -> {
            if (items != null) {
                adapter.submitList(items);
                emptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                recyclerView.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
            }
            swipeRefreshLayout.setRefreshing(false);
        });

        viewModel.getIsLoading().observe(getViewLifecycleOwner(), loading -> {
            if (loading != null && !loading) {
                swipeRefreshLayout.setRefreshing(false);
            }
        });

        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadData() {
        if (isFavorites) {
            viewModel.loadFavorites();
        } else if (isTrash) {
            viewModel.loadTrash();
        } else {
            viewModel.loadFiles(viewModel.getCurrentParentId());
        }
    }

    private void onFileClick(NodeItem item) {
        if (item.isDirectory()) {
            // 进入文件夹
            viewModel.navigateToFolder(item.getEffectiveId());
        } else {
            // 打开文件
            if (item.isImageFile() || item.isVideoFile() || item.isAudioFile()) {
                // 媒体文件用内置播放器
                Intent intent = new Intent(requireContext(), MediaPlayerActivity.class);
                intent.putExtra("file_id", item.getEffectiveId());
                intent.putExtra("file_name", item.getEffectiveName());
                intent.putExtra("file_type", item.getFileType());
                startActivity(intent);
            } else {
                // 通用文件详情
                Intent intent = new Intent(requireContext(), FileDetailActivity.class);
                intent.putExtra("file_id", item.getEffectiveId());
                intent.putExtra("file_name", item.getEffectiveName());
                intent.putExtra("file_size", item.getEffectiveSize());
                intent.putExtra("file_type", item.getFileType());
                startActivity(intent);
            }
        }
    }

    private void onFileLongClick(NodeItem item) {
        // 显示上下文菜单（删除、重命名、移动、收藏等）
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(item.getEffectiveName())
                .setItems(new String[]{"重命名", "删除", "移动", "收藏", "分享"}, (dialog, which) -> {
                    switch (which) {
                        case 0: // 重命名
                            showRenameDialog(item);
                            break;
                        case 1: // 删除
                            showDeleteDialog(item);
                            break;
                        case 2: // 移动
                            Toast.makeText(requireContext(), "移动功能开发中", Toast.LENGTH_SHORT).show();
                            break;
                        case 3: // 收藏
                            Toast.makeText(requireContext(), "收藏功能开发中", Toast.LENGTH_SHORT).show();
                            break;
                        case 4: // 分享
                            shareFile(item);
                            break;
                    }
                })
                .show();
    }

    private void showRenameDialog(NodeItem item) {
        final android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setText(item.getEffectiveName());
        input.setSelectAllOnFocus(true);

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("重命名")
                .setView(input)
                .setPositiveButton("确定", (dialog, which) -> {
                    String newName = input.getText().toString().trim();
                    if (!newName.isEmpty() && !newName.equals(item.getEffectiveName())) {
                        viewModel.renameFile(item.getEffectiveId(), newName);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showDeleteDialog(NodeItem item) {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("删除文件")
                .setMessage("确定要删除 \"" + item.getEffectiveName() + "\" 吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    viewModel.deleteFile(item.getEffectiveId());
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void shareFile(NodeItem item) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("*/*");
        shareIntent.putExtra(Intent.EXTRA_TEXT, "分享文件: " + item.getEffectiveName());
        startActivity(Intent.createChooser(shareIntent, "分享文件"));
    }
}