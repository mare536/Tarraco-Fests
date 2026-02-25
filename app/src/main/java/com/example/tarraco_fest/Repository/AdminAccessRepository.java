package com.example.tarraco_fest.Repository;

import com.example.tarraco_fest.Data.FirestoreSchema;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class AdminAccessRepository {

    public interface Callback {
        void onResult(boolean isAdmin);
        void onError(Exception e);
    }

    public void verificarAccesoAdmin(Callback cb) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            cb.onResult(false);
            return;
        }

        FirebaseFirestore.getInstance()
                .collection(FirestoreSchema.Collections.USUARIOS)
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    String rol = doc.getString(FirestoreSchema.UsuarioFields.ROL);
                    Boolean bloqueado = doc.getBoolean(FirestoreSchema.UsuarioFields.BLOQUEADO);
                    boolean isAdmin = FirestoreSchema.UserRoles.ADMIN.equalsIgnoreCase(rol);
                    boolean isBlocked = bloqueado != null && bloqueado;
                    cb.onResult(isAdmin && !isBlocked);
                })
                .addOnFailureListener(cb::onError);
    }
}
