package com.atlas.monshi;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class SummaryReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        NotificationUtil.ensureChannels(context);
        int[] s = ReminderStore.getStats(context);
        String text = s == null
                ? "وقت مرور کارهای امروز است. منشی من را باز کن."
                : "امروز " + s[0] + " کار داشتی؛ " + s[1] + " انجام شد و " + s[2] + " مورد باقی یا عقب‌افتاده است.";
        Intent open = new Intent(context, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(context, 777001, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(context, NotificationUtil.CHANNEL_SUMMARY)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("خلاصه شبانه منشی من")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE)).notify(777002, n);
        AlarmScheduler.scheduleSummary(context, ReminderStore.getSummaryTime(context));
    }
}
