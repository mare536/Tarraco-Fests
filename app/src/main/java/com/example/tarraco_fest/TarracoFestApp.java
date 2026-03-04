package com.example.tarraco_fest;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

import com.example.tarraco_fest.Push.PushContract;

/**
 * Configuracion global de la app.
 * Se ejecuta una sola vez por arranque de proceso.
 */
public class TarracoFestApp extends Application {

    // Gestiona on create en este bloque.
    @Override
    public void onCreate() {
        super.onCreate();
        crearCanalPushGeneral();
    }

    // Crea el canal general para notificaciones push de Firebase.
    private void crearCanalPushGeneral() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;
        if (nm.getNotificationChannel(PushContract.CHANNEL_GENERAL) != null) return;

        NotificationChannel channel = new NotificationChannel(
                PushContract.CHANNEL_GENERAL,
                getString(R.string.push_notif_channel_name),
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription(getString(R.string.push_notif_channel_desc));
        nm.createNotificationChannel(channel);
    }
}
