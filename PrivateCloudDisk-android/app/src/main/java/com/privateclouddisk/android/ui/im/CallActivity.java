package com.privateclouddisk.android.ui.im;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.privateclouddisk.android.R;

/**
 * 通话 Activity
 * 未来集成 WebRTC 音视频通话
 */
public class CallActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);

        Toast.makeText(this, "通话功能开发中", Toast.LENGTH_SHORT).show();
    }
}