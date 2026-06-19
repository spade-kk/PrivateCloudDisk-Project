package com.privateclouddisk.android.ui.media;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.privateclouddisk.android.R;
import com.privateclouddisk.android.data.repository.FileRepository;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * 媒体播放器 Activity
 * 支持图片预览、视频播放、音频播放
 */
@AndroidEntryPoint
public class MediaPlayerActivity extends AppCompatActivity {

    @Inject FileRepository fileRepository;

    private ImageView ivImage;
    private String fileId, fileName, fileType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_media_player);

        fileId = getIntent().getStringExtra("file_id");
        fileName = getIntent().getStringExtra("file_name");
        fileType = getIntent().getStringExtra("file_type");

        ivImage = findViewById(R.id.iv_image);

        if (fileType != null && fileType.matches("jpg|jpeg|png|gif|webp|bmp")) {
            loadImage();
        } else {
            Toast.makeText(this, "预览功能开发中: " + fileName, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void loadImage() {
        // 获取操作凭证后加载图片
        fileRepository.getOperationToken(fileId, "preview")
                .subscribeOn(io.reactivex.rxjava3.schedulers.Schedulers.io())
                .observeOn(io.reactivex.rxjava3.android.schedulers.AndroidSchedulers.mainThread())
                .subscribe(
                        token -> {
                            String url = "https://your-server.com/files/preview?file_id="
                                    + fileId + "&token=" + token;
                            Glide.with(this)
                                    .load(url)
                                    .placeholder(R.drawable.ic_image)
                                    .error(R.drawable.ic_broken_image)
                                    .into(ivImage);
                        },
                        throwable -> {
                            Toast.makeText(this, "加载失败: " + throwable.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        }
                );
    }
}