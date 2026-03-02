package com.example.tarraco_fest.Reminder;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public final class ReminderScheduler {

    public static final String EXTRA_EVENT_ID = "extra_event_id";
    public static final String EXTRA_EVENT_TITLE = "extra_event_title";
    public static final String EXTRA_EVENT_START_AT = "extra_event_start_at";
    public static final String ACTION_EVENT_REMINDER = "com.example.tarraco_fest.ACTION_EVENT_REMINDER";

    private ReminderScheduler() {
    }

    public static void schedule(
            Context context,
            String eventId,
            String eventTitle,
            long eventStartAtMillis,
            long remindAtMillis
    ) {
        if (context == null || eventId == null || eventId.trim().isEmpty()) return;
        if (remindAtMillis <= 0L) return;

        Context appContext = context.getApplicationContext();
        Intent intent = new Intent(appContext, ReminderReceiver.class);
        intent.setAction(ACTION_EVENT_REMINDER);
        intent.putExtra(EXTRA_EVENT_ID, eventId);
        intent.putExtra(EXTRA_EVENT_TITLE, eventTitle == null ? "" : eventTitle.trim());
        intent.putExtra(EXTRA_EVENT_START_AT, eventStartAtMillis);

        int requestCode = Math.abs(eventId.hashCode());
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                appContext,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager am = (AlarmManager) appContext.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, remindAtMillis, pendingIntent);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, remindAtMillis, pendingIntent);
            }
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, remindAtMillis, pendingIntent);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            am.setExact(AlarmManager.RTC_WAKEUP, remindAtMillis, pendingIntent);
        } else {
            am.set(AlarmManager.RTC_WAKEUP, remindAtMillis, pendingIntent);
        }
    }
}
