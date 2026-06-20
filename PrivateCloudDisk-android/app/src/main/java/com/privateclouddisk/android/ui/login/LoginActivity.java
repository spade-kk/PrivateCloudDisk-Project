package com.privateclouddisk.android.ui.login;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.privateclouddisk.android.R;
import com.privateclouddisk.android.ui.main.MainActivity;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * 登录界面
 *
 * 对应 Windows 的 LoginWindow
 * 简洁的登录界面，支持账号密码登录
 */
@AndroidEntryPoint
public class LoginActivity extends AppCompatActivity {

    private EditText etAccount, etPassword;
    private Button btnLogin;
    private TextView tvRegister;
    private ProgressBar progressBar;
    private LoginViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        viewModel = new ViewModelProvider(this).get(LoginViewModel.class);

        initViews();
        setupObservers();

        // 检查是否已登录
        if (viewModel.isLoggedIn()) {
            navigateToMain();
        }
    }

    private void initViews() {
        etAccount = findViewById(R.id.et_account);
        etPassword = findViewById(R.id.et_password);
        btnLogin = findViewById(R.id.btn_login);
        tvRegister = findViewById(R.id.tv_register);
        progressBar = findViewById(R.id.progress_bar);

        btnLogin.setOnClickListener(v -> login());
        tvRegister.setOnClickListener(v -> {
            // TODO: 跳转到注册页面
            Toast.makeText(this, "注册功能开发中", Toast.LENGTH_SHORT).show();
        });
    }

    private void setupObservers() {
        viewModel.getIsLoading().observe(this, loading -> {
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
            btnLogin.setEnabled(!loading);
        });

        viewModel.getLoginResult().observe(this, event -> {
            if (event != null) {
                navigateToMain();
            }
        });

        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void login() {
        String account = etAccount.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(account)) {
            etAccount.setError("请输入账号");
            return;
        }
        if (TextUtils.isEmpty(password)) {
            etPassword.setError("请输入密码");
            return;
        }

        viewModel.login(account, password);
    }

    private void navigateToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}