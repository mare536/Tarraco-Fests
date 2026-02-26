package com.example.tarraco_fest.Repository;

import com.example.tarraco_fest.Data.FirestoreSchema;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class ReminderRepository {

    public interface Callback {
        void onOk();
        void onError(Exception e);
    }

    public void guardarRecordatorio(String eventId, String eventTitle, long inicioMillis, long remindAtMillis, Callback cb) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            cb.onError(new IllegalStateException("No hay usuario autenticado"));
            return;
        }
        if (eventId == null || eventId.trim().isEmpty()) {
            cb.onError(new IllegalArgumentException("Evento invalido"));
            return;
        }
        if (inicioMillis <= 0L) {
            cb.onError(new IllegalStateException("El evento no tiene fecha valida"));
            return;
        }
        if (remindAtMillis <= 0L) {
            cb.onError(new IllegalArgumentException("Fecha de recordatorio invalida"));
            return;
        }
        if (remindAtMillis <= System.currentTimeMillis()) {
            cb.onError(new IllegalStateException("La hora del recordatorio ya paso"));
            return;
        }
        if (remindAtMillis >= inicioMillis) {
            cb.onError(new IllegalStateException("El recordatorio debe ser antes del inicio del evento"));
            return;
        }

        long offsetMinutes = (inicioMillis - remindAtMillis) / (60L * 1000L);
        if (offsetMinutes <= 0L) {
            cb.onError(new IllegalStateException("No se pudo calcular el tiempo del recordatorio"));
            return;
        }
        long offsetHours = Math.max(1L, offsetMinutes / 60L);

        Map<String, Object> data = new HashMap<>();
        data.put(FirestoreSchema.RecordatorioFields.EVENT_ID, eventId);
        data.put(FirestoreSchema.RecordatorioFields.EVENT_TITLE, eventTitle == null ? "" : eventTitle.trim());
        data.put(FirestoreSchema.RecordatorioFields.EVENT_START_AT, new Timestamp(new Date(inicioMillis)));
        data.put(FirestoreSchema.RecordatorioFields.REMINDER_OFFSET_HOURS, offsetHours);
        data.put(FirestoreSchema.RecordatorioFields.REMINDER_OFFSET_MINUTES, offsetMinutes);
        data.put(FirestoreSchema.RecordatorioFields.REMIND_AT, new Timestamp(new Date(remindAtMillis)));
        data.put(FirestoreSchema.RecordatorioFields.ENABLED, true);
        data.put(FirestoreSchema.RecordatorioFields.CREATED_AT, Timestamp.now());

        FirebaseFirestore.getInstance()
                .collection(FirestoreSchema.Collections.USUARIOS)
                .document(uid)
                .collection(FirestoreSchema.Subcollections.RECORDATORIOS)
                .document(eventId)
                .set(data, SetOptions.merge())
                .addOnSuccessListener(v -> cb.onOk())
                .addOnFailureListener(cb::onError);
    }
}
