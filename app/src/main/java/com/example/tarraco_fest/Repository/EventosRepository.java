package com.example.tarraco_fest.Repository;

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
                .collection("eventos")
                .orderBy("inicio")   // <-- tu campo real
                .limit(50)
                .get()
                .addOnSuccessListener(qs -> {
                    List<Evento> list = new ArrayList<>();

                    for (QueryDocumentSnapshot d : qs) {

                        // Si quieres mostrar solo activos sin crear índices, filtramos aquí:
                        Boolean activo = d.getBoolean("activo");
                        if (activo != null && !activo) continue;

                        Evento e = new Evento();
                        e.id = d.getId();
                        e.titulo = d.getString("titulo");           // <-- tu campo real
                        e.inicio = d.getTimestamp("inicio");        // <-- tu campo real
                        e.lugar = d.getString("lugarNombre");       // <-- tu campo real

                        list.add(e);
                    }

                    cb.onOk(list);
                })
                .addOnFailureListener(cb::onError);
    }

}
