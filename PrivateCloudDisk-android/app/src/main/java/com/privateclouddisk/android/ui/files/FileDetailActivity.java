package com.privateclouddisk.android.ui.files;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.privateclouddisk.android.R;
import com.privateclouddisk.android.data.model.NodeItem;
import com.privateclouddisk.android.data.repository.FileRepository;
import com.privateclouddisk.android.service.DownloadService;
import com.privateclouddisk.android.util.FileUtils;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * 文件详情 Activity
 */
@AndroidEntryPoint
public class FileDetailActivity extends AppCompatActivity {

    @Inject FileRepository fileRepository;

    private ImageView ivFileIcon;
    private TextView tvFileName, tvFileSize, tvFileType, tvFileDate;
    private Button btnDownload, btnOpen, btnShare;
    private ProgressBar progressBar;

    private String fileId;
    private String fileName;
    private long fileSize;
    private String fileType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_detail);

        Intent intent = getIntent();
        fileId = intent.getStringExtra("file_id");
        fileName = intent.getStringExtra("file_name");
        fileSize = intent.getLongExtra("file_size", 0);
        fileType = intent.getStringExtra("file_type");

        initViews();
        loadFileDetail();
    }

    private void initViews() {
        ivFileIcon = findViewById(R.id.iv_file_icon);
        tvFileName = findViewById(R.id.tv_file_name);
        tvFileSize = findViewById(R.id.tv_file_size);
        tvFileType = findViewById(R.id.tv_file_type);
        tvFileDate = findViewById(R.id.tv_file_date);
        btnDownload = findViewById(R.id.btn_download);
        btnOpen = findViewById(R.id.btn_open);
        btnShare = findViewById(R.id.btn_share);
        progressBar = findViewById(R.id.progress_bar);

        tvFileName.setText(fileName);
        tvFileSize.setText(FileUtils.formatFileSize(fileSize));
        tvFileType.setText(fileType);
        ivFileIcon.setImageResource(getFileIcon());

        btnDownload.setOnClickListener(v -> downloadFile());
        btnOpen.setOnClickListener(v -> openFile());
        btnShare.setOnClickListener(v -> shareFile());
    }

    private void loadFileDetail() {
        if (fileId == null) return;
        progressBar.setVisibility(View.VISIBLE);

        fileRepository.getFileDetail(fileId)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        item -> {
                            progressBar.setVisibility(View.GONE);
                            updateUI(item);
                        },
                        throwable -> {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(this, "加载失败: " + throwable.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        }
                );
    }

    private void updateUI(NodeItem item) {
        tvFileName.setText(item.getEffectiveName());
        tvFileSize.setText(FileUtils.formatFileSize(item.getEffectiveSize()));
        String date = item.getUpdatedTime() != null ? item.getUpdatedTime() : item.getUploadedTime();
        tvFileDate.setText(date != null ? FileUtils.formatDateTime(date) : "");
    }

    private void downloadFile() {
        Toast.makeText(this, "下载功能开发中", Toast.LENGTH_SHORT).show();
    }

    private void openFile() {
        Toast.makeText(this, "打开文件功能开发中", Toast.LENGTH_SHORT).show();
    }

    private void shareFile() {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, "分享文件: " + fileName);
        startActivity(Intent.createChooser(shareIntent, "分享文件"));
    }

    private int getFileIcon() {
        if (fileType == null) return R.drawable.ic_file;
        String ext = fileType.toLowerCase();
        if (ext.matches("jpg|jpeg|png|gif|webp|bmp")) return R.drawable.ic_image;
        if (ext.matches("mp4|avi|mov|mkv|webm")) return R.drawable.ic_video;
        if (ext.matches("mp3|wav|aac|flac|ogg")) return R.drawable.ic_audio;
        if (ext.matches("pdf|doc|docx|xls|xlsx|ppt|pptx")) return R.drawable.ic_document;
        return R.drawable.ic_file;
    }
}