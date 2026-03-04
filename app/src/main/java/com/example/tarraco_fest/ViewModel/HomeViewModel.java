package com.example.tarraco_fest.ViewModel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.tarraco_fest.Modelo.Evento;
import com.example.tarraco_fest.Repository.EventosRepository;
import com.example.tarraco_fest.Repository.FavoritosRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * ViewModel de Home.
 * Centraliza carga de eventos y favoritos para desacoplar la Activity del acceso a datos.
 */
public class HomeViewModel extends ViewModel {

    private final EventosRepository eventosRepository = new EventosRepository();
    private final FavoritosRepository favoritosRepository = new FavoritosRepository();

    private final MutableLiveData<List<Evento>> eventos = new MutableLiveData<>();
    private final MutableLiveData<String> eventosError = new MutableLiveData<>();
    private final MutableLiveData<Boolean> eventosLoading = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> eventosTranslating = new MutableLiveData<>(false);

    private final MutableLiveData<Set<String>> favoritosIds = new MutableLiveData<>();
    private final MutableLiveData<String> favoritosError = new MutableLiveData<>();

    public LiveData<List<Evento>> getEventos() {
        return eventos;
    }

    public LiveData<String> getEventosError() {
        return eventosError;
    }

    public LiveData<Boolean> getEventosLoading() {
        return eventosLoading;
    }

    public LiveData<Boolean> getEventosTranslating() {
        return eventosTranslating;
    }

    public LiveData<Set<String>> getFavoritosIds() {
        return favoritosIds;
    }

    public LiveData<String> getFavoritosError() {
        return favoritosError;
    }

    // Carga eventos desde repositorio y publica resultados para la vista.
    public void cargarEventos() {
        eventosRepository.cargarEventos(new EventosRepository.Callback() {
            @Override
            public void onLoadingStateChanged(boolean isLoading) {
                eventosLoading.postValue(isLoading);
            }

            @Override
            public void onTranslationStateChanged(boolean isTranslating) {
                eventosTranslating.postValue(isTranslating);
            }

            @Override
            public void onOk(List<Evento> data) {
                List<Evento> safe = data == null ? new ArrayList<>() : new ArrayList<>(data);
                eventos.postValue(safe);
            }

            @Override
            public void onError(Exception e) {
                String message = e == null || e.getMessage() == null ? "" : e.getMessage();
                eventosError.postValue(message);
            }
        });
    }

    // Carga ids de favoritos del usuario autenticado.
    public void cargarFavoritos() {
        favoritosRepository.cargarFavoritosIds(new FavoritosRepository.FavoritosIdsCallback() {
            @Override
            public void onOk(Set<String> ids) {
                Set<String> safe = ids == null ? new HashSet<>() : new HashSet<>(ids);
                favoritosIds.postValue(safe);
            }

            @Override
            public void onError(Exception e) {
                String message = e == null || e.getMessage() == null ? "" : e.getMessage();
                favoritosError.postValue(message);
            }
        });
    }
}
