package com.example.tarraco_fest.Push;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.tarraco_fest.Activity.HomeActivity;
import com.example.tarraco_fest.R;
import com.example.tarraco_fest.Repository.PushTokenRepository;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

/**
 * Servicio de Firebase Messaging para recibir push remotos.
 * Publica notificaciones locales y sincroniza token en Firestore.
 */
public class TarracoMessagingService extends FirebaseMessagingService {

    private final PushTokenRepository pushTokenRepository = new PushTokenRepository();

    // Gestiona on new token en este bloque.
    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        pushTokenRepository.sincronizarTokenRecibido(token);
    }

    // Gestiona on message received en este bloque.
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        if (remoteMessage == null) return;
        if (!tienePermisoNotificaciones()) return;

        ensureChannel();

        Map<String, String> data = remoteMessage.getData();
        String eventId = obtenerValor(data, PushContract.DATA_EVENT_ID, PushContract.DATA_EVENT_ID_ALT);
        String title = obtenerTitulo(remoteMessage, data);
        String body = obtenerBody(remoteMessage, data);

        Intent openIntent = new Intent(this, HomeActivity.class);
        openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (!TextUtils.isEmpty(eventId)) {
            openIntent.putExtra(PushContract.EXTRA_PUSH_EVENT_ID, eventId);
        }
        openIntent.putExtra(PushContract.EXTRA_PUSH_TITLE, title);
        openIntent.putExtra(PushContract.EXTRA_PUSH_BODY, body);

        int requestCode = Math.abs((eventId + System.currentTimeMillis()).hashCode());
        PendingIntent contentIntent = PendingIntent.getActivity(
                this,
                requestCode,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, PushContract.CHANNEL_GENERAL)
                .setSmallIcon(R.drawable.ic_drawer_info)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(contentIntent);

        NotificationManagerCompat.from(this).notify(requestCode, builder.build());
    }

    // Indica si permiso de notificaciones esta concedido en Android 13+.
    private boolean tienePermisoNotificaciones() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true;
        return ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    // Crea canal de notificaciones si aun no existe.
    private void ensureChannel() {
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

    // Devuelve titulo priorizando payload notification y luego data.
    private String obtenerTitulo(RemoteMessage remoteMessage, Map<String, String> data) {
        RemoteMessage.Notification notification = remoteMessage.getNotification();
        String value = notification != null ? notification.getTitle() : null;
        if (TextUtils.isEmpty(value)) {
            value = data != null ? data.get(PushContract.DATA_TITLE) : null;
        }
        if (TextUtils.isEmpty(value)) {
            value = getString(R.string.push_notif_default_title);
        }
        return value.trim();
    }

    // Devuelve texto priorizando payload notification y luego data.
    private String obtenerBody(RemoteMessage remoteMessage, Map<String, String> data) {
        RemoteMessage.Notification notification = remoteMessage.getNotification();
        String value = notification != null ? notification.getBody() : null;
        if (TextUtils.isEmpty(value)) {
            value = data != null ? data.get(PushContract.DATA_BODY) : null;
        }
        if (TextUtils.isEmpty(value)) {
            value = getString(R.string.push_notif_default_body);
        }
        return value.trim();
    }

    // Busca el primer valor no vacio entre varias claves posibles.
    private String obtenerValor(Map<String, String> data, String... keys) {
        if (data == null || keys == null) return "";
        for (String key : keys) {
            String value = data.get(key);
            if (!TextUtils.isEmpty(value)) return value.trim();
        }
        return "";
    }
}
