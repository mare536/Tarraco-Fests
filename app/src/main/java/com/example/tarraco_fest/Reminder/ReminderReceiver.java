package com.example.tarraco_fest.Reminder;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.tarraco_fest.Activity.HomeActivity;
import com.example.tarraco_fest.R;

/**
 * BroadcastReceiver que publica la notificacion cuando vence un recordatorio.
 * Lee los extras del intent y arma la notificacion local.
 */
public class ReminderReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "tarraco_reminders";

    // Gestiona on receive en este bloque.
    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        if (!ReminderScheduler.ACTION_EVENT_REMINDER.equals(intent.getAction())) return;

        String eventId = intent.getStringExtra(ReminderScheduler.EXTRA_EVENT_ID);
        String eventTitle = intent.getStringExtra(ReminderScheduler.EXTRA_EVENT_TITLE);
        long eventStartAt = intent.getLongExtra(ReminderScheduler.EXTRA_EVENT_START_AT, 0L);

        if (eventId == null || eventId.trim().isEmpty()) return;
        if (eventStartAt > 0L && System.currentTimeMillis() >= eventStartAt) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            boolean granted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED;
            if (!granted) return;
        }

        ensureChannel(context);

        Intent openIntent = new Intent(context, HomeActivity.class);
        openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                Math.abs(eventId.hashCode()),
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String contentText = (eventTitle == null || eventTitle.trim().isEmpty())
                ? context.getString(R.string.detail_reminder_notif_default_text)
                : eventTitle.trim();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_drawer_info)
                .setContentTitle(context.getString(R.string.detail_reminder_notif_title))
                .setContentText(contentText)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(contentText))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(contentIntent);

        NotificationManagerCompat.from(context)
                .notify(Math.abs(eventId.hashCode()), builder.build());
    }

    // Gestiona ensure channel en este bloque.
    private void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) return;

        NotificationChannel existing = nm.getNotificationChannel(CHANNEL_ID);
        if (existing != null) return;

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.detail_reminder_notif_channel_name),
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription(context.getString(R.string.detail_reminder_notif_channel_desc));
        nm.createNotificationChannel(channel);
    }
}
