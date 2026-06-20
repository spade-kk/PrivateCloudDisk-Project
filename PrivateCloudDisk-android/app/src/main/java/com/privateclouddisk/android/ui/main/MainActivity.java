package com.privateclouddisk.android.ui.main;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationView;
import com.privateclouddisk.android.R;
import com.privateclouddisk.android.data.local.PreferenceManager;
import com.privateclouddisk.android.service.DownloadService;
import com.privateclouddisk.android.service.SyncService;
import com.privateclouddisk.android.service.UploadService;
import com.privateclouddisk.android.ui.login.LoginActivity;
import com.privateclouddisk.android.ui.settings.SettingsActivity;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import timber.log.Timber;

/**
 * 主界面 Activity
 *
 * 对应 Windows 的 MainWindow
 * 使用 BottomNavigationView + Navigation Drawer 的经典 Android 布局
 */
@AndroidEntryPoint
public class MainActivity extends AppCompatActivity {

    @Inject PreferenceManager preferenceManager;

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private BottomNavigationView bottomNavigationView;
    private MainViewModel viewModel;

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (granted) {
                            startServices();
                        }
                    });

    private final ActivityResultLauncher<String[]> storagePermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> {
                        boolean allGranted = true;
                        for (Boolean granted : result.values()) {
                            if (!granted) allGranted = false;
                        }
                        if (allGranted) {
                            startServices();
                        } else {
                            Toast.makeText(this, "存储权限被拒绝，部分功能不可用",
                                    Toast.LENGTH_LONG).show();
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Timber.d("MainActivity onCreate");

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);

        setupToolbar();
        setupDrawer();
        setupBottomNavigation(savedInstanceState);
        setupObservers();
        requestPermissions();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_menu);
        }
    }

    private void setupDrawer() {
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_files) {
                // 主页面，已在 files fragment
            } else if (id == R.id.nav_favorites) {
                navigateToFavorites();
            } else if (id == R.id.nav_trash) {
                navigateToTrash();
            } else if (id == R.id.nav_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
            } else if (id == R.id.nav_logout) {
                viewModel.logout();
            }

            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });

        // 更新侧边栏用户信息
        updateNavHeader();
    }

    private void setupBottomNavigation(Bundle savedInstanceState) {
        bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_files) {
                navigateToFragment(new FileListFragment(), "files");
                return true;
            } else if (id == R.id.nav_favorites) {
                navigateToFavorites();
                return true;
            } else if (id == R.id.nav_im) {
                // TODO: IM Fragment
                Toast.makeText(this, "IM 功能开发中", Toast.LENGTH_SHORT).show();
                return true;
            } else if (id == R.id.nav_profile) {
                navigateToFragment(new ProfileFragment(), "profile");
                return true;
            }
            return false;
        });

        // 默认显示文件列表
        if (savedInstanceState == null) {
            bottomNavigationView.setSelectedItemId(R.id.nav_files);
        }
    }

    private void setupObservers() {
        viewModel.getLogoutEvent().observe(this, event -> {
            if (event != null) {
                stopServices();
                Intent intent = new Intent(this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }
        });
    }

    private void navigateToFragment(Fragment fragment, String tag) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment, tag)
                .commit();
    }

    private void navigateToFavorites() {
        FileListFragment fragment = new FileListFragment();
        Bundle args = new Bundle();
        args.putBoolean("favorites", true);
        fragment.setArguments(args);
        navigateToFragment(fragment, "favorites");
    }

    private void navigateToTrash() {
        FileListFragment fragment = new FileListFragment();
        Bundle args = new Bundle();
        args.putBoolean("trash", true);
        fragment.setArguments(args);
        navigateToFragment(fragment, "trash");
    }

    private void updateNavHeader() {
        View headerView = navigationView.getHeaderView(0);
        // TODO: 绑定用户头像和名称
    }

    private void requestPermissions() {
        // 通知权限（Android 13+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                return;
            }
        }

        // 存储权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!android.os.Environment.isExternalStorageManager()) {
                Toast.makeText(this, "需要存储权限以管理文件", Toast.LENGTH_LONG).show();
            }
        } else {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                storagePermissionLauncher.launch(new String[]{
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                });
                return;
            }
        }

        startServices();
    }

    private void startServices() {
        // 启动同步服务
        Intent syncIntent = new Intent(this, SyncService.class);
        ContextCompat.startForegroundService(this, syncIntent);
    }

    private void stopServices() {
        stopService(new Intent(this, SyncService.class));
        stopService(new Intent(this, UploadService.class));
        stopService(new Intent(this, DownloadService.class));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            drawerLayout.openDrawer(GravityCompat.START);
            return true;
        } else if (item.getItemId() == R.id.action_search) {
            // TODO: 搜索
            return true;
        } else if (item.getItemId() == R.id.action_upload) {
            // TODO: 上传文件
            return true;
        } else if (item.getItemId() == R.id.action_create_folder) {
            viewModel.showCreateFolderDialog(this);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }
}