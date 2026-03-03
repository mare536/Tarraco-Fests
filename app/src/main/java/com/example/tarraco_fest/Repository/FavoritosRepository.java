package com.example.tarraco_fest.Repository;

import com.example.tarraco_fest.Data.FirestoreSchema;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Repositorio para gestionar favoritos por usuario.
 * Persiste favoritos en Firestore bajo usuarios/{uid}/favoritos.
 */
public class FavoritosRepository {

    public interface Callback {
        void onOk();
        void onError(Exception e);
    }

    public interface FavoritosIdsCallback {
        void onOk(Set<String> favoritosIds);
        void onError(Exception e);
    }

    // Carga IDs de eventos marcados como favoritos por el usuario actual.
    public void cargarFavoritosIds(FavoritosIdsCallback cb) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            cb.onError(new IllegalStateException("No hay usuario autenticado"));
            return;
        }

        FirebaseFirestore.getInstance()
                .collection(FirestoreSchema.Collections.USUARIOS)
                .document(uid)
                .collection(FirestoreSchema.Subcollections.FAVORITOS)
                .get()
                .addOnSuccessListener(qs -> {
                    Set<String> out = new HashSet<>();
                    if (qs != null) {
                        qs.getDocuments().forEach(doc -> {
                            if (doc != null) {
                                String id = doc.getId();
                                if (id != null && !id.trim().isEmpty()) {
                                    out.add(id.trim());
                                }
                            }
                        });
                    }
                    cb.onOk(out);
                })
                .addOnFailureListener(cb::onError);
    }

    // Marca un evento como favorito para el usuario actual.
    public void marcarFavorito(String eventId, String eventTitle, Callback cb) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            cb.onError(new IllegalStateException("No hay usuario autenticado"));
            return;
        }
        if (eventId == null || eventId.trim().isEmpty()) {
            cb.onError(new IllegalArgumentException("Evento invalido"));
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put(FirestoreSchema.FavoritoFields.EVENT_ID, eventId.trim());
        data.put(FirestoreSchema.FavoritoFields.EVENT_TITLE, eventTitle == null ? "" : eventTitle.trim());
        data.put(FirestoreSchema.FavoritoFields.UPDATED_AT, Timestamp.now());
        data.put(FirestoreSchema.FavoritoFields.CREATED_AT, Timestamp.now());

        FirebaseFirestore.getInstance()
                .collection(FirestoreSchema.Collections.USUARIOS)
                .document(uid)
                .collection(FirestoreSchema.Subcollections.FAVORITOS)
                .document(eventId.trim())
                .set(data, SetOptions.merge())
                .addOnSuccessListener(v -> cb.onOk())
                .addOnFailureListener(cb::onError);
    }

    // Quita el favorito del evento para el usuario actual.
    public void quitarFavorito(String eventId, Callback cb) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            cb.onError(new IllegalStateException("No hay usuario autenticado"));
            return;
        }
        if (eventId == null || eventId.trim().isEmpty()) {
            cb.onError(new IllegalArgumentException("Evento invalido"));
            return;
        }

        FirebaseFirestore.getInstance()
                .collection(FirestoreSchema.Collections.USUARIOS)
                .document(uid)
                .collection(FirestoreSchema.Subcollections.FAVORITOS)
                .document(eventId.trim())
                .delete()
                .addOnSuccessListener(v -> cb.onOk())
                .addOnFailureListener(cb::onError);
    }
}
