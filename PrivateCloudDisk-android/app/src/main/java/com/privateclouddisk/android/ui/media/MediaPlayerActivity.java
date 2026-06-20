package com.privateclouddisk.android.ui.media;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.privateclouddisk.android.R;
import com.privateclouddisk.android.data.repository.FileRepository;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * 企业级媒体播放器
 * 参考 YouTube/B站 播放器设计，统一品牌色调
 */
@AndroidEntryPoint
public class MediaPlayerActivity extends AppCompatActivity {

    @Inject FileRepository fileRepository;

    private ImageView ivImage;
    private VideoView videoView;
    private SeekBar seekBar;
    private TextView tvCurrentTime, tvDuration;
    private String fileId, fileName, fileType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_media_player);

        fileId = getIntent().getStringExtra("file_id");
        fileName = getIntent().getStringExtra("file_name");
        fileType = getIntent().getStringExtra("file_type");

        ivImage = findViewById(R.id.iv_file_icon);
        videoView = findViewById(R.id.video_view);
        seekBar = findViewById(R.id.seek_bar);
        tvCurrentTime = findViewById(R.id.tv_current_time);
        tvDuration = findViewById(R.id.tv_duration);

        if (fileName != null) {
            TextView tvTitle = findViewById(R.id.tv_video_title);
            tvTitle.setText(fileName);
        }

        if (fileType != null && fileType.matches("jpg|jpeg|png|gif|webp|bmp")) {
            loadImage();
        } else if (fileType != null && fileType.matches("mp4|mkv|webm|avi|mov")) {
            loadVideo();
        } else {
            Toast.makeText(this, "预览功能开发中: " + fileName, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void loadImage() {
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

    private void loadVideo() {
        // 视频播放功能占位，后续集成完整播放器
        Toast.makeText(this, "视频播放功能开发中: " + fileName, Toast.LENGTH_SHORT).show();
    }
}