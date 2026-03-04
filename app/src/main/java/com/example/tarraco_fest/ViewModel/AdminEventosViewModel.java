package com.example.tarraco_fest.ViewModel;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.StringRes;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.tarraco_fest.Modelo.AdminEvento;
import com.example.tarraco_fest.R;
import com.example.tarraco_fest.Repository.AdminAccessRepository;
import com.example.tarraco_fest.Repository.AdminEventosRepository;
import com.example.tarraco_fest.Repository.EventosRepository;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * ViewModel de administracion de eventos.
 * Centraliza acceso, carga, filtros y acciones de negocio para desacoplar la Activity.
 */
public class AdminEventosViewModel extends ViewModel {

    public static final String FILTRO_TODOS = "todos";
    public static final String FILTRO_ACTIVOS = "activos";
    public static final String FILTRO_INACTIVOS = "inactivos";

    public interface ActionResult {
        void onOk();
        void onError();
    }

    private final AdminAccessRepository accessRepository = new AdminAccessRepository();
    private final AdminEventosRepository eventosRepository = new AdminEventosRepository();
    private final List<AdminEvento> eventosCache = new ArrayList<>();

    private final MutableLiveData<List<AdminEvento>> eventos = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> toastMessageRes = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> shouldCloseScreen = new MutableLiveData<>(false);

    private String busquedaNormalizada = "";
    private String filtroEstadoActual = FILTRO_TODOS;

    public LiveData<List<AdminEvento>> getEventos() {
        return eventos;
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

    // Valida acceso admin y, si procede, carga la lista de eventos.
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
                cargarEventos();
            }

            @Override
            public void onError(Exception e) {
                loading.postValue(false);
                emitirToast(R.string.admin_access_error);
                shouldCloseScreen.postValue(true);
            }
        });
    }

    // Carga eventos y reaplica filtros locales.
    public void cargarEventos() {
        loading.postValue(true);
        eventosRepository.cargarEventos(new AdminEventosRepository.ListCallback() {
            @Override
            public void onOk(List<AdminEvento> data) {
                eventosCache.clear();
                if (data != null) {
                    eventosCache.addAll(data);
                }
                aplicarFiltrosInternos();
                loading.postValue(false);
            }

            @Override
            public void onError(Exception e) {
                loading.postValue(false);
                emitirToast(R.string.admin_events_load_error);
            }
        });
    }

    // Actualiza filtro de busqueda y recalcula lista visible.
    public void actualizarBusqueda(String textoBusqueda) {
        busquedaNormalizada = normalizar(textoBusqueda);
        aplicarFiltrosInternos();
    }

    // Actualiza filtro de estado y recalcula lista visible.
    public void actualizarFiltroEstado(String filtroEstado) {
        filtroEstadoActual = filtroEstado == null || filtroEstado.trim().isEmpty()
                ? FILTRO_TODOS
                : filtroEstado;
        aplicarFiltrosInternos();
    }

    // Guarda evento (crear/editar), invalida cache API y refresca listado.
    public void guardarEvento(AdminEvento evento, boolean crear, ActionResult result) {
        AdminEventosRepository.ActionCallback callback = new AdminEventosRepository.ActionCallback() {
            @Override
            public void onOk() {
                EventosRepository.invalidarCacheApi();
                emitirToast(R.string.admin_event_saved_ok);
                cargarEventos();
                if (result != null) result.onOk();
            }

            @Override
            public void onError(Exception e) {
                emitirToast(R.string.admin_event_save_error);
                if (result != null) result.onError();
            }
        };

        if (crear) {
            eventosRepository.crearEvento(evento, callback);
        } else {
            eventosRepository.actualizarEvento(evento, callback);
        }
    }

    // Alterna estado activo/inactivo y recarga listado.
    public void toggleActivo(AdminEvento evento, ActionResult result) {
        if (evento == null || evento.id == null || evento.id.trim().isEmpty()) {
            emitirToast(R.string.admin_event_save_error);
            if (result != null) result.onError();
            return;
        }

        eventosRepository.actualizarActivo(evento.id, !evento.activo, new AdminEventosRepository.ActionCallback() {
            @Override
            public void onOk() {
                EventosRepository.invalidarCacheApi();
                emitirToast(R.string.admin_event_saved_ok);
                cargarEventos();
                if (result != null) result.onOk();
            }

            @Override
            public void onError(Exception e) {
                emitirToast(R.string.admin_event_save_error);
                if (result != null) result.onError();
            }
        });
    }

    // Elimina evento y recarga listado.
    public void eliminarEvento(AdminEvento evento, ActionResult result) {
        if (evento == null || evento.id == null || evento.id.trim().isEmpty()) {
            emitirToast(R.string.admin_event_delete_error);
            if (result != null) result.onError();
            return;
        }

        eventosRepository.eliminarEvento(evento.id, new AdminEventosRepository.ActionCallback() {
            @Override
            public void onOk() {
                EventosRepository.invalidarCacheApi();
                emitirToast(R.string.admin_event_deleted_ok);
                cargarEventos();
                if (result != null) result.onOk();
            }

            @Override
            public void onError(Exception e) {
                emitirToast(R.string.admin_event_delete_error);
                if (result != null) result.onError();
            }
        });
    }

    // Wrapper para mantener subida de imagen en capa de datos.
    public void subirImagenEvento(Context context, Uri imageUri, AdminEventosRepository.ImageUploadCallback cb) {
        eventosRepository.subirImagenEvento(context, imageUri, cb);
    }

    // Wrapper para generar i18n desde repositorio.
    public void generarI18nEvento(String titulo, String descripcion, AdminEventosRepository.EventI18nCallback cb) {
        eventosRepository.generarI18nEvento(titulo, descripcion, cb);
    }

    // Limpia toast tras consumirlo en la vista.
    public void consumirToastMessage() {
        toastMessageRes.setValue(null);
    }

    // Limpia accion de cierre tras consumirla en la vista.
    public void consumirCloseScreen() {
        shouldCloseScreen.setValue(false);
    }

    // Aplica filtros de texto + estado sobre el cache.
    private void aplicarFiltrosInternos() {
        List<AdminEvento> filtrados = new ArrayList<>();

        for (AdminEvento e : eventosCache) {
            if (e == null) continue;

            String titulo = normalizar(e.titulo);
            String lugar = normalizar(e.lugarNombre);
            boolean coincideTexto = busquedaNormalizada.isEmpty()
                    || titulo.contains(busquedaNormalizada)
                    || lugar.contains(busquedaNormalizada);

            boolean coincideEstado = FILTRO_TODOS.equals(filtroEstadoActual)
                    || (FILTRO_ACTIVOS.equals(filtroEstadoActual) && e.activo)
                    || (FILTRO_INACTIVOS.equals(filtroEstadoActual) && !e.activo);

            if (coincideTexto && coincideEstado) {
                filtrados.add(e);
            }
        }

        eventos.postValue(filtrados);
    }

    // Normaliza texto para filtros robustos sin tildes.
    private String normalizar(String value) {
        if (value == null) return "";
        String base = value.trim().toLowerCase(Locale.ROOT);
        String normalized = Normalizer.normalize(base, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    // Publica mensaje de UI para toast.
    private void emitirToast(@StringRes int messageRes) {
        toastMessageRes.postValue(messageRes);
    }
}

