package com.privateclouddisk.android.ui.im;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.privateclouddisk.android.R;

/**
 * 企业级通话界面
 * 品牌渐变背景，简约清晰的通话控件
 */
public class CallActivity extends AppCompatActivity {

    private TextView tvCallStatus, tvContactName, tvCallDuration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);

        tvCallStatus = findViewById(R.id.tv_call_status);
        tvContactName = findViewById(R.id.tv_contact_name);
        tvCallDuration = findViewById(R.id.tv_call_duration);

        String contactName = getIntent().getStringExtra("contact_name");
        if (contactName != null) {
            tvContactName.setText(contactName);
        }

        findViewById(R.id.btn_hangup).setOnClickListener(v -> finish());
        findViewById(R.id.btn_mute).setOnClickListener(v ->
                tvCallStatus.setText("已静音"));
        findViewById(R.id.btn_speaker).setOnClickListener(v ->
                tvCallStatus.setText("扬声器已开启"));
    }
}