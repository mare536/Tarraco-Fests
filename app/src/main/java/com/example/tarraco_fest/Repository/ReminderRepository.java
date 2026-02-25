package com.example.tarraco_fest.Repository;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public class ReminderRepository {

    public interface Callback {
        void onOk();
        void onError(Exception e);
    }

    public void guardarRecordatorio(String eventId, Callback cb) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            cb.onError(new IllegalStateException("No hay usuario autenticado"));
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("eventId", eventId);
        data.put("createdAt", Timestamp.now());

        FirebaseFirestore.getInstance()
                .collection("usuarios").document(uid)
                .collection("recordatorios").document(eventId)
                .set(data, SetOptions.merge())
                .addOnSuccessListener(v -> cb.onOk())
                .addOnFailureListener(cb::onError);
    }
}
