package com.example.tarraco_fest.Repository;

import android.text.TextUtils;
import android.util.Log;

import com.example.tarraco_fest.Data.FirestoreSchema;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.HashMap;
import java.util.Map;

/**
 * Sincroniza el token FCM del dispositivo en Firestore para envio de push segmentado.
 * Solo guarda token cuando existe sesion de usuario activa.
 */
public class PushTokenRepository {

    private static final String TAG = "PushTokenRepository";

    private final FirebaseAuth auth;
    private final FirebaseFirestore db;

    public PushTokenRepository() {
        this(FirebaseAuth.getInstance(), FirebaseFirestore.getInstance());
    }

    public PushTokenRepository(FirebaseAuth auth, FirebaseFirestore db) {
        this.auth = auth;
        this.db = db;
    }

    // Sincroniza token actual en Firestore si hay usuario autenticado.
    public void sincronizarTokenUsuarioActual() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(token -> guardarToken(user.getUid(), token))
                .addOnFailureListener(e -> Log.w(TAG, "No se pudo obtener token FCM", e));
    }

    // Guarda token recien generado para el usuario autenticado.
    public void sincronizarTokenRecibido(String token) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;
        guardarToken(user.getUid(), token);
    }

    // Quita el token actual del usuario autenticado al cerrar sesion en el dispositivo.
    public void desvincularTokenUsuarioActual() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(token -> quitarToken(user.getUid(), token))
                .addOnFailureListener(e -> Log.w(TAG, "No se pudo obtener token para desvincular", e));
    }

    // Guarda token en el documento de usuario con merge no destructivo.
    private void guardarToken(String uid, String token) {
        if (TextUtils.isEmpty(uid) || TextUtils.isEmpty(token)) return;

        Map<String, Object> data = new HashMap<>();
        data.put(FirestoreSchema.UsuarioFields.FCM_TOKEN, token);
        data.put(FirestoreSchema.UsuarioFields.FCM_TOKEN_UPDATED_AT, Timestamp.now());
        data.put(FirestoreSchema.UsuarioFields.FCM_TOKENS, FieldValue.arrayUnion(token));
        data.put(FirestoreSchema.UsuarioFields.UPDATED_AT, Timestamp.now());

        db.collection(FirestoreSchema.Collections.USUARIOS)
                .document(uid)
                .set(data, SetOptions.merge())
                .addOnFailureListener(e -> Log.w(TAG, "No se pudo guardar token FCM", e));
    }

    // Quita token de la lista historica y actualiza timestamp de sincronizacion.
    private void quitarToken(String uid, String token) {
        if (TextUtils.isEmpty(uid) || TextUtils.isEmpty(token)) return;

        Map<String, Object> data = new HashMap<>();
        data.put(FirestoreSchema.UsuarioFields.FCM_TOKENS, FieldValue.arrayRemove(token));
        data.put(FirestoreSchema.UsuarioFields.FCM_TOKEN_UPDATED_AT, Timestamp.now());
        data.put(FirestoreSchema.UsuarioFields.UPDATED_AT, Timestamp.now());

        db.collection(FirestoreSchema.Collections.USUARIOS)
                .document(uid)
                .set(data, SetOptions.merge())
                .addOnFailureListener(e -> Log.w(TAG, "No se pudo desvincular token FCM", e));
    }
}
