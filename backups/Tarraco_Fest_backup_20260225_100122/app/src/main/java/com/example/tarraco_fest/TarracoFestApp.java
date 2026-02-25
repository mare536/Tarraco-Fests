package com.example.tarraco_fest;

import android.app.Application;

import com.google.firebase.auth.FirebaseAuth;

/**
 * Gestiona comportamiento global de sesion.
 * Se ejecuta una sola vez por arranque de proceso.
 */
public class TarracoFestApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // Sesion no persistente entre cierres reales de la app:
        // al iniciar un proceso nuevo, limpia la sesion previa.
        FirebaseAuth.getInstance().signOut();
    }
}
