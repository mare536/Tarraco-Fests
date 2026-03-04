package com.example.tarraco_fest.ViewModel;

import androidx.annotation.StringRes;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.tarraco_fest.R;
import com.example.tarraco_fest.Repository.AdminAccessRepository;

/**
 * ViewModel del panel admin.
 * Se encarga de verificar acceso y exponer el estado para la vista.
 */
public class AdminPanelViewModel extends ViewModel {

    private final AdminAccessRepository accessRepository = new AdminAccessRepository();

    private final MutableLiveData<Boolean> adminEnabled = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> statusMessageRes = new MutableLiveData<>(R.string.admin_checking_access);
    private final MutableLiveData<Integer> toastMessageRes = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> shouldCloseScreen = new MutableLiveData<>(false);

    public LiveData<Boolean> getAdminEnabled() {
        return adminEnabled;
    }

    public LiveData<Integer> getStatusMessageRes() {
        return statusMessageRes;
    }

    public LiveData<Integer> getToastMessageRes() {
        return toastMessageRes;
    }

    public LiveData<Boolean> getShouldCloseScreen() {
        return shouldCloseScreen;
    }

    // Valida acceso admin y publica el estado para habilitar acciones.
    public void verificarAcceso() {
        adminEnabled.postValue(false);
        statusMessageRes.postValue(R.string.admin_checking_access);

        accessRepository.verificarAccesoAdmin(new AdminAccessRepository.Callback() {
            @Override
            public void onResult(boolean isAdmin) {
                if (!isAdmin) {
                    emitirToast(R.string.admin_access_denied);
                    shouldCloseScreen.postValue(true);
                    return;
                }
                statusMessageRes.postValue(R.string.admin_access_granted);
                adminEnabled.postValue(true);
            }

            @Override
            public void onError(Exception e) {
                emitirToast(R.string.admin_access_error);
                shouldCloseScreen.postValue(true);
            }
        });
    }

    // Limpia mensaje temporal tras mostrarlo en UI.
    public void consumirToastMessage() {
        toastMessageRes.setValue(null);
    }

    // Limpia accion de cierre tras consumirla en UI.
    public void consumirCloseScreen() {
        shouldCloseScreen.setValue(false);
    }

    // Publica mensaje de UI basado en string resource.
    private void emitirToast(@StringRes int messageRes) {
        toastMessageRes.postValue(messageRes);
    }
}

