package com.example.tarraco_fest.Repository;

import com.example.tarraco_fest.Data.FirestoreSchema;
import com.example.tarraco_fest.Modelo.Evento;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class EventosRepository {

    public interface Callback {
        void onOk(List<Evento> eventos);
        void onError(Exception e);
    }

    public void cargarEventos(Callback cb) {
        FirebaseFirestore.getInstance()
                .collection(FirestoreSchema.Collections.EVENTOS)
                .orderBy(FirestoreSchema.EventoFields.INICIO)
                .limit(50)
                .get()
                .addOnSuccessListener(qs -> {
                    List<Evento> list = new ArrayList<>();

                    for (QueryDocumentSnapshot d : qs) {

                        Boolean activo = d.getBoolean(FirestoreSchema.EventoFields.ACTIVO);
                        if (activo != null && !activo) continue;

                        Evento e = new Evento();
                        e.id = d.getId();
                        e.titulo = d.getString(FirestoreSchema.EventoFields.TITULO);
                        e.inicio = d.getTimestamp(FirestoreSchema.EventoFields.INICIO);
                        e.lugar = d.getString(FirestoreSchema.EventoFields.LUGAR_NOMBRE);

                        list.add(e);
                    }

                    cb.onOk(list);
                })
                .addOnFailureListener(cb::onError);
    }

}
