package com.privateclouddisk.android.ui.im;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.privateclouddisk.android.R;

/**
 * IM 聊天 Activity
 * 未来集成 WebSocket 实时通信
 */
public class ChatActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        Toast.makeText(this, "IM 聊天功能开发中", Toast.LENGTH_SHORT).show();
    }
}