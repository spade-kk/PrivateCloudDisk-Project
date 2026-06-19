package com.privateclouddisk.android.ui.main;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.privateclouddisk.android.R;
import com.privateclouddisk.android.data.local.PreferenceManager;
import com.privateclouddisk.android.data.repository.AuthRepository;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * 个人中心 Fragment
 */
@AndroidEntryPoint
public class ProfileFragment extends Fragment {

    @Inject AuthRepository authRepository;
    @Inject PreferenceManager preferenceManager;

    private ImageView ivAvatar;
    private TextView tvUserName, tvAccount, tvUsedStorage, tvTotalStorage;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                              @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ivAvatar = view.findViewById(R.id.iv_avatar);
        tvUserName = view.findViewById(R.id.tv_user_name);
        tvAccount = view.findViewById(R.id.tv_account);
        tvUsedStorage = view.findViewById(R.id.tv_used_storage);
        tvTotalStorage = view.findViewById(R.id.tv_total_storage);

        loadProfile();
    }

    private void loadProfile() {
        tvUserName.setText(preferenceManager.getUserName());
        tvAccount.setText("账号: " + preferenceManager.getUserId());

        String avatarUrl = preferenceManager.getUserAvatar();
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            Glide.with(this)
                    .load(avatarUrl)
                    .circleCrop()
                    .placeholder(R.drawable.ic_default_avatar)
                    .into(ivAvatar);
        }

        authRepository.getProfile()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        profile -> {
                            if (profile.getUserName() != null) {
                                tvUserName.setText(profile.getUserName());
                            }
                            if (profile.getUserId() != null) {
                                tvAccount.setText("账号: " + profile.getUserId());
                            }
                        },
                        throwable -> { /* ignore */ }
                );
    }
}