package com.atlas.monshi;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.provider.Settings;

public final class NotificationUtil {
    public static final String CHANNEL_REMINDERS = "monshi_reminders";
    public static final String CHANNEL_SUMMARY = "monshi_summary";
    private NotificationUtil() {}

    public static void ensureChannels(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel reminder = new NotificationChannel(CHANNEL_REMINDERS, "یادآوری‌ها", NotificationManager.IMPORTANCE_HIGH);
        reminder.setDescription("هشدارهای منشی من");
        reminder.enableVibration(true);
        reminder.setSound(Settings.System.DEFAULT_NOTIFICATION_URI,
                new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).build());
        NotificationChannel summary = new NotificationChannel(CHANNEL_SUMMARY, "خلاصه شبانه", NotificationManager.IMPORTANCE_DEFAULT);
        summary.setDescription("گزارش روزانه منشی من");
        nm.createNotificationChannel(reminder);
        nm.createNotificationChannel(summary);
    }
}
