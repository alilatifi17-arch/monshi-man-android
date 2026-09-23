package com.atlas.monshi;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.json.JSONObject;

public class AlarmReceiver extends BroadcastReceiver {
    private static int code(String s) { return (s == null ? 0 : s.hashCode()) & 0x7fffffff; }

    @Override public void onReceive(Context context, Intent intent) {
        String id = intent.getStringExtra("id");
        boolean snoozeOnly = intent.getBooleanExtra("snoozeOnly", false);
        if (id == null) return;
        Reminder r = ReminderStore.get(context, id);
        if (r == null) return;
        NotificationUtil.ensureChannels(context);

        Intent open = new Intent(context, MainActivity.class);
        open.putExtra("openReminderId", id);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentPi = PendingIntent.getActivity(context, code(id + ":open"), open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = new Notification.Builder(context, NotificationUtil.CHANNEL_REMINDERS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("منشی من ⏰")
                .setContentText(r.title)
                .setStyle(new Notification.BigTextStyle().bigText(r.note == null || r.note.isEmpty() ? r.title : r.title + "\n" + r.note))
                .setContentIntent(contentPi)
                .setAutoCancel(false)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setOnlyAlertOnce(false)
                .setPriority(Notification.PRIORITY_HIGH);

        b.addAction(action(context, id, "DONE", "انجام شد"));
        b.addAction(action(context, id, "SNOOZE15", "۱۵ دقیقه بعد"));
        b.addAction(action(context, id, "SNOOZE60", "۱ ساعت بعد"));
        b.addAction(action(context, id, "TOMORROW", "فردا"));
        b.addAction(action(context, id, "MISSED", "انجام نشد"));

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(code(id), b.build());

        if (!snoozeOnly) {
            long next = AlarmScheduler.nextOccurrence(r, r.nextAt);
            if (next > 0L) {
                r.nextAt = next;
                ReminderStore.save(context, r);
                AlarmScheduler.schedule(context, r);
            }
            try {
                JSONObject e = new JSONObject();
                e.put("type", "fired");
                e.put("id", id);
                e.put("nextAt", next);
                e.put("at", System.currentTimeMillis());
                ReminderStore.addEvent(context, e);
            } catch (Exception ignored) {}
        }
    }

    private Notification.Action action(Context c, String id, String action, String title) {
        Intent i = new Intent(c, NotificationActionReceiver.class);
        i.setAction("com.atlas.monshi.ACTION_" + action);
        i.putExtra("id", id);
        PendingIntent pi = PendingIntent.getBroadcast(c, code(id + ":" + action), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Action.Builder(R.drawable.ic_notification, title, pi).build();
    }
}
