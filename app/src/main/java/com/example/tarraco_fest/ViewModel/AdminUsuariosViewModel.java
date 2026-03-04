package com.example.tarraco_fest.ViewModel;

import androidx.annotation.StringRes;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.tarraco_fest.Data.FirestoreSchema;
import com.example.tarraco_fest.Modelo.AdminUser;
import com.example.tarraco_fest.R;
import com.example.tarraco_fest.Repository.AdminAccessRepository;
import com.example.tarraco_fest.Repository.AdminUsersRepository;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * ViewModel de administracion de usuarios.
 * Centraliza carga, filtros y acciones para mantener la Activity enfocada en UI.
 */
public class AdminUsuariosViewModel extends ViewModel {

    private static final String FILTRO_TODOS = "todos";
    private static final String FILTRO_ADMIN = "admin";
    private static final String FILTRO_USUARIO = "usuario";
    private static final String FILTRO_ACTIVOS = "activos";
    private static final String FILTRO_BLOQUEADOS = "bloqueados";

    private final AdminAccessRepository accessRepository = new AdminAccessRepository();
    private final AdminUsersRepository usersRepository = new AdminUsersRepository();
    private final List<AdminUser> usersCache = new ArrayList<>();

    private final MutableLiveData<List<AdminUser>> usuarios = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> toastMessageRes = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> shouldCloseScreen = new MutableLiveData<>(false);

    private String busquedaNormalizada = "";
    private String filtroRolActual = FILTRO_TODOS;
    private String filtroEstadoActual = FILTRO_TODOS;

    public LiveData<List<AdminUser>> getUsuarios() {
        return usuarios;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public LiveData<Integer> getToastMessageRes() {
        return toastMessageRes;
    }

    public LiveData<Boolean> getShouldCloseScreen() {
        return shouldCloseScreen;
    }

    // Valida acceso admin y, si procede, carga la lista de usuarios.
    public void verificarAccesoYCargar() {
        loading.postValue(true);
        accessRepository.verificarAccesoAdmin(new AdminAccessRepository.Callback() {
            @Override
            public void onResult(boolean isAdmin) {
                if (!isAdmin) {
                    loading.postValue(false);
                    emitirToast(R.string.admin_access_denied);
                    shouldCloseScreen.postValue(true);
                    return;
                }
                cargarUsuarios();
            }

            @Override
            public void onError(Exception e) {
                loading.postValue(false);
                emitirToast(R.string.admin_access_error);
                shouldCloseScreen.postValue(true);
            }
        });
    }

    // Carga usuarios desde el repositorio y reaplica filtros locales.
    public void cargarUsuarios() {
        loading.postValue(true);
        usersRepository.cargarUsuarios(new AdminUsersRepository.ListCallback() {
            @Override
            public void onOk(List<AdminUser> data) {
                usersCache.clear();
                if (data != null) {
                    usersCache.addAll(data);
                }
                aplicarFiltrosInternos();
                loading.postValue(false);
            }

            @Override
            public void onError(Exception e) {
                loading.postValue(false);
                emitirToast(R.string.admin_users_load_error);
            }
        });
    }

    // Actualiza filtro de texto y recalcula la lista visible.
    public void actualizarBusqueda(String textoBusqueda) {
        busquedaNormalizada = normalizar(textoBusqueda);
        aplicarFiltrosInternos();
    }

    // Actualiza filtro de rol y recalcula la lista visible.
    public void actualizarFiltroRol(String filtroRol) {
        filtroRolActual = filtroRol == null || filtroRol.trim().isEmpty() ? FILTRO_TODOS : filtroRol;
        aplicarFiltrosInternos();
    }

    // Actualiza filtro de estado y recalcula la lista visible.
    public void actualizarFiltroEstado(String filtroEstado) {
        filtroEstadoActual = filtroEstado == null || filtroEstado.trim().isEmpty() ? FILTRO_TODOS : filtroEstado;
        aplicarFiltrosInternos();
    }

    // Alterna bloqueo del usuario y recarga la lista para mantener consistencia.
    public void toggleBloqueo(AdminUser user) {
        if (user == null || user.uid == null || user.uid.trim().isEmpty()) {
            emitirToast(R.string.admin_user_update_error);
            return;
        }

        loading.postValue(true);
        usersRepository.actualizarBloqueo(user.uid, !user.bloqueado, new AdminUsersRepository.ActionCallback() {
            @Override
            public void onOk() {
                emitirToast(R.string.admin_user_updated_ok);
                cargarUsuarios();
            }

            @Override
            public void onError(Exception e) {
                loading.postValue(false);
                emitirToast(R.string.admin_user_update_error);
            }
        });
    }

    // Alterna rol del usuario entre admin/usuario y recarga la lista.
    public void toggleRol(AdminUser user) {
        if (user == null || user.uid == null || user.uid.trim().isEmpty()) {
            emitirToast(R.string.admin_user_update_error);
            return;
        }

        String nuevoRol = FirestoreSchema.UserRoles.ADMIN.equalsIgnoreCase(user.rol)
                ? FirestoreSchema.UserRoles.USUARIO
                : FirestoreSchema.UserRoles.ADMIN;

        loading.postValue(true);
        usersRepository.actualizarRol(user.uid, nuevoRol, new AdminUsersRepository.ActionCallback() {
            @Override
            public void onOk() {
                emitirToast(R.string.admin_user_updated_ok);
                cargarUsuarios();
            }

            @Override
            public void onError(Exception e) {
                loading.postValue(false);
                emitirToast(R.string.admin_user_update_error);
            }
        });
    }

    // Elimina un usuario bloqueado y recarga el listado de administracion.
    public void eliminarUsuarioBloqueado(AdminUser user) {
        if (user == null || user.uid == null || user.uid.trim().isEmpty()) {
            emitirToast(R.string.admin_user_delete_error);
            return;
        }

        if (!user.bloqueado) {
            emitirToast(R.string.admin_user_delete_requires_block);
            return;
        }

        loading.postValue(true);
        usersRepository.eliminarUsuarioBloqueado(user.uid, new AdminUsersRepository.ActionCallback() {
            @Override
            public void onOk() {
                emitirToast(R.string.admin_user_deleted_ok);
                cargarUsuarios();
            }

            @Override
            public void onError(Exception e) {
                loading.postValue(false);
                emitirToast(R.string.admin_user_delete_error);
            }
        });
    }

    // Limpia mensaje tras mostrar toast para evitar duplicados en cambios de configuracion.
    public void consumirToastMessage() {
        toastMessageRes.setValue(null);
    }

    // Limpia estado de cierre tras consumirlo en la vista.
    public void consumirCloseScreen() {
        shouldCloseScreen.setValue(false);
    }

    // Recalcula la lista visible aplicando texto, rol y estado.
    private void aplicarFiltrosInternos() {
        List<AdminUser> filtrados = new ArrayList<>();

        for (AdminUser u : usersCache) {
            if (u == null) continue;

            String nombre = normalizar(u.nombre);
            String email = normalizar(u.email);
            boolean coincideTexto = busquedaNormalizada.isEmpty()
                    || nombre.contains(busquedaNormalizada)
                    || email.contains(busquedaNormalizada);

            boolean esAdmin = FirestoreSchema.UserRoles.ADMIN.equalsIgnoreCase(u.rol);
            boolean coincideRol = FILTRO_TODOS.equals(filtroRolActual)
                    || (FILTRO_ADMIN.equals(filtroRolActual) && esAdmin)
                    || (FILTRO_USUARIO.equals(filtroRolActual) && !esAdmin);

            boolean coincideEstado = FILTRO_TODOS.equals(filtroEstadoActual)
                    || (FILTRO_ACTIVOS.equals(filtroEstadoActual) && !u.bloqueado)
                    || (FILTRO_BLOQUEADOS.equals(filtroEstadoActual) && u.bloqueado);

            if (coincideTexto && coincideRol && coincideEstado) {
                filtrados.add(u);
            }
        }

        usuarios.postValue(filtrados);
    }

    // Normaliza cadenas para filtrar de forma robusta sin depender de tildes/mayusculas.
    private String normalizar(String value) {
        if (value == null) return "";
        String base = value.trim().toLowerCase(Locale.ROOT);
        String normalized = Normalizer.normalize(base, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    // Publica un mensaje de UI basado en recurso para toasts de la pantalla.
    private void emitirToast(@StringRes int messageRes) {
        toastMessageRes.postValue(messageRes);
    }
}
