package com.example.tarraco_fest.Repository;

import com.example.tarraco_fest.Modelo.Evento;
import com.example.tarraco_fest.R;
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
                        e.setId(d.getId());

// Campos directos de Firebase
                        e.setTitulo(d.getString("titulo"));
                        e.setDescripcion(d.getString("descripcion"));
                        e.setCategoriaId(d.getString("categoriaId"));
                        e.setLugarNombre(d.getString("lugarNombre"));
                        e.setCiudad(d.getString("ciudad"));
                        e.setDireccion(d.getString("direccion"));
                        e.setImagenUrl(d.getString("imagenUrl"));

                        Boolean estadoActivo = d.getBoolean("activo");
                        e.setActivo(estadoActivo != null ? estadoActivo : true);

// Casteo seguro de Array a List
                        Object palabrasObj = d.get("palabrasClave");
                        if (palabrasObj instanceof java.util.List) {
                            e.setPalabrasClave((java.util.List<String>) palabrasObj);
                        }

// Transformación del Timestamp a String para la UI
                        com.google.firebase.Timestamp timestamp = d.getTimestamp("inicio");
                        if (timestamp != null) {
                            // Formato: "17 Ene 2026 - 21:00"
                            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd MMM yyyy - HH:mm", java.util.Locale.getDefault());
                            e.setFecha(sdf.format(timestamp.toDate()));
                        } else {
                            e.setFecha("Fecha por confirmar");
                        }

// Campos faltantes en BD pero necesarios en UI
                        Double precioBase = d.getDouble("precio");
                        e.setPrecio(precioBase != null ? precioBase : 0.0);
                        e.setImagenResId(R.drawable.card_festival); // Mock

                        list.add(e);
                    }

                    cb.onOk(list);
                })
                .addOnFailureListener(cb::onError);
    }

}
