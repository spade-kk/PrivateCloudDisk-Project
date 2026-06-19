package com.privateclouddisk.android.ui.login;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.privateclouddisk.android.data.model.UserProfile;
import com.privateclouddisk.android.data.repository.AuthRepository;
import com.privateclouddisk.android.util.SingleLiveEvent;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import timber.log.Timber;

/**
 * 登录 ViewModel
 */
@HiltViewModel
public class LoginViewModel extends ViewModel {

    private final AuthRepository authRepository;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final SingleLiveEvent<UserProfile> loginResult = new SingleLiveEvent<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    @Inject
    public LoginViewModel(AuthRepository authRepository) {
        this.authRepository = authRepository;
    }

    public LiveData<Boolean> getIsLoading() { return isLoading; }
    public SingleLiveEvent<UserProfile> getLoginResult() { return loginResult; }
    public LiveData<String> getErrorMessage() { return errorMessage; }

    public boolean isLoggedIn() {
        return authRepository.isLoggedIn();
    }

    public void login(String account, String password) {
        isLoading.setValue(true);

        disposables.add(
                authRepository.login(account, password)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                profile -> {
                                    isLoading.setValue(false);
                                    loginResult.setValue(profile);
                                },
                                throwable -> {
                                    Timber.e(throwable, "Login failed");
                                    isLoading.setValue(false);
                                    errorMessage.setValue("登录失败: " + throwable.getMessage());
                                }
                        )
        );
    }

    @Override
    protected void onCleared() {
        disposables.clear();
        super.onCleared();
    }
}