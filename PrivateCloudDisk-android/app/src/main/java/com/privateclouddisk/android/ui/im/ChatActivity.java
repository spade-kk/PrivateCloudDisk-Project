package com.privateclouddisk.android.ui.im;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.privateclouddisk.android.R;

/**
 * 企业级 IM 聊天主页
 * 参考 QQ/微信聊天列表设计，简约风格，统一品牌色调
 */
public class ChatActivity extends AppCompatActivity {

    private RecyclerView recyclerConversations;
    private View emptyChat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        recyclerConversations = findViewById(R.id.recycler_conversations);
        emptyChat = findViewById(R.id.empty_chat);

        recyclerConversations.setLayoutManager(new LinearLayoutManager(this));

        // 显示空状态（后续集成 WebSocket 实时通信后填充数据）
        emptyChat.setVisibility(View.VISIBLE);
        recyclerConversations.setVisibility(View.GONE);

        findViewById(R.id.fab_new_chat).setOnClickListener(v ->
                Toast.makeText(this, "新建会话功能开发中", Toast.LENGTH_SHORT).show());
    }
}