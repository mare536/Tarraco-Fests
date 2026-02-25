package com.example.tarraco_fest.Repository;

import com.example.tarraco_fest.Data.FirestoreSchema;
import com.example.tarraco_fest.Modelo.AdminEvento;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdminEventosRepository {

    public interface ListCallback {
        void onOk(List<AdminEvento> eventos);
        void onError(Exception e);
    }

    public interface ActionCallback {
        void onOk();
        void onError(Exception e);
    }

    public void cargarEventos(ListCallback cb) {
        FirebaseFirestore.getInstance()
                .collection(FirestoreSchema.Collections.EVENTOS)
                .orderBy(FirestoreSchema.EventoFields.INICIO)
                .limit(500)
                .get()
                .addOnSuccessListener(qs -> {
                    List<AdminEvento> out = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : qs) {
                        AdminEvento e = new AdminEvento();
                        e.id = doc.getId();
                        e.titulo = leer(doc.getString(FirestoreSchema.EventoFields.TITULO));
                        e.lugarNombre = leer(doc.getString(FirestoreSchema.EventoFields.LUGAR_NOMBRE));
                        e.inicio = doc.getTimestamp(FirestoreSchema.EventoFields.INICIO);
                        Boolean act = doc.getBoolean(FirestoreSchema.EventoFields.ACTIVO);
                        e.activo = act == null || act;
                        out.add(e);
                    }
                    cb.onOk(out);
                })
                .addOnFailureListener(cb::onError);
    }

    public void crearEvento(AdminEvento evento, ActionCallback cb) {
        Map<String, Object> data = toFirestore(evento);
        data.put(FirestoreSchema.EventoFields.CREADO_EN, Timestamp.now());
        data.put(FirestoreSchema.EventoFields.UPDATED_AT, Timestamp.now());

        FirebaseFirestore.getInstance()
                .collection(FirestoreSchema.Collections.EVENTOS)
                .add(data)
                .addOnSuccessListener(ref -> cb.onOk())
                .addOnFailureListener(cb::onError);
    }

    public void actualizarEvento(AdminEvento evento, ActionCallback cb) {
        if (evento.id == null || evento.id.trim().isEmpty()) {
            cb.onError(new IllegalArgumentException("Id de evento no valido"));
            return;
        }

        Map<String, Object> data = toFirestore(evento);
        data.put(FirestoreSchema.EventoFields.UPDATED_AT, Timestamp.now());

        FirebaseFirestore.getInstance()
                .collection(FirestoreSchema.Collections.EVENTOS)
                .document(evento.id)
                .set(data, SetOptions.merge())
                .addOnSuccessListener(v -> cb.onOk())
                .addOnFailureListener(cb::onError);
    }

    public void actualizarActivo(String eventId, boolean activo, ActionCallback cb) {
        Map<String, Object> up = new HashMap<>();
        up.put(FirestoreSchema.EventoFields.ACTIVO, activo);
        up.put(FirestoreSchema.EventoFields.UPDATED_AT, Timestamp.now());

        FirebaseFirestore.getInstance()
                .collection(FirestoreSchema.Collections.EVENTOS)
                .document(eventId)
                .set(up, SetOptions.merge())
                .addOnSuccessListener(v -> cb.onOk())
                .addOnFailureListener(cb::onError);
    }

    private Map<String, Object> toFirestore(AdminEvento e) {
        Map<String, Object> data = new HashMap<>();
        data.put(FirestoreSchema.EventoFields.TITULO, leer(e.titulo));
        data.put(FirestoreSchema.EventoFields.LUGAR_NOMBRE, leer(e.lugarNombre));
        data.put(FirestoreSchema.EventoFields.INICIO, e.inicio != null ? e.inicio : Timestamp.now());
        data.put(FirestoreSchema.EventoFields.ACTIVO, e.activo);
        return data;
    }

    private String leer(String value) {
        return value == null ? "" : value.trim();
    }
}
