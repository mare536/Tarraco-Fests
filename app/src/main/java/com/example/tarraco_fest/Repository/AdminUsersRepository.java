package com.example.tarraco_fest.Repository;

import com.example.tarraco_fest.Data.FirestoreSchema;
import com.example.tarraco_fest.Modelo.AdminUser;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Repositorio de administracion de usuarios.
 * Carga usuarios y aplica cambios de rol/bloqueo.
 */
public class AdminUsersRepository {

    public interface ListCallback {
        void onOk(List<AdminUser> users);
        void onError(Exception e);
    }

    public interface ActionCallback {
        void onOk();
        void onError(Exception e);
    }

    // Carga usuarios desde la fuente correspondiente.
    public void cargarUsuarios(ListCallback cb) {
        FirebaseFirestore.getInstance()
                .collection(FirestoreSchema.Collections.USUARIOS)
                .orderBy(FirestoreSchema.UsuarioFields.EMAIL)
                .limit(500)
                .get()
                .addOnSuccessListener(qs -> {
                    List<AdminUser> out = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : qs) {
                        AdminUser u = new AdminUser();
                        u.uid = doc.getId();
                        u.nombre = leer(doc.getString(FirestoreSchema.UsuarioFields.NOMBRE_MOSTRADO));
                        u.email = leer(doc.getString(FirestoreSchema.UsuarioFields.EMAIL));
                        u.rol = leer(doc.getString(FirestoreSchema.UsuarioFields.ROL));
                        if (u.rol.isEmpty()) u.rol = FirestoreSchema.UserRoles.USUARIO;
                        Boolean blocked = doc.getBoolean(FirestoreSchema.UsuarioFields.BLOQUEADO);
                        u.bloqueado = blocked != null && blocked;
                        Boolean pass = doc.getBoolean(FirestoreSchema.UsuarioFields.TIENE_PASSWORD);
                        u.tienePassword = pass != null && pass;
                        out.add(u);
                    }
                    cb.onOk(out);
                })
                .addOnFailureListener(cb::onError);
    }

    // Actualiza bloqueo con la logica de negocio actual.
    public void actualizarBloqueo(String uid, boolean bloqueado, ActionCallback cb) {
        Map<String, Object> up = new HashMap<>();
        up.put(FirestoreSchema.UsuarioFields.BLOQUEADO, bloqueado);
        up.put(FirestoreSchema.UsuarioFields.UPDATED_AT, Timestamp.now());

        FirebaseFirestore.getInstance()
                .collection(FirestoreSchema.Collections.USUARIOS)
                .document(uid)
                .set(up, SetOptions.merge())
                .addOnSuccessListener(v -> cb.onOk())
                .addOnFailureListener(cb::onError);
    }

    // Actualiza rol con la logica de negocio actual.
    public void actualizarRol(String uid, String rol, ActionCallback cb) {
        Map<String, Object> up = new HashMap<>();
        up.put(FirestoreSchema.UsuarioFields.ROL, rol);
        up.put(FirestoreSchema.UsuarioFields.UPDATED_AT, Timestamp.now());

        FirebaseFirestore.getInstance()
                .collection(FirestoreSchema.Collections.USUARIOS)
                .document(uid)
                .set(up, SetOptions.merge())
                .addOnSuccessListener(v -> cb.onOk())
                .addOnFailureListener(cb::onError);
    }

    // Gestiona leer en este bloque.
    private String leer(String value) {
        return value == null ? "" : value.trim();
    }
}
