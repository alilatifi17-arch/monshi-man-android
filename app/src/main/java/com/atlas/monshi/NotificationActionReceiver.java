package com.atlas.monshi;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.json.JSONObject;

import java.util.Calendar;

public class NotificationActionReceiver extends BroadcastReceiver {
    private static int code(String s) { return (s == null ? 0 : s.hashCode()) & 0x7fffffff; }

    @Override public void onReceive(Context context, Intent intent) {
        String id = intent.getStringExtra("id");
        if (id == null) return;
        Reminder r = ReminderStore.get(context, id);
        if (r == null) return;
        String a = intent.getAction() == null ? "" : intent.getAction();
        ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(code(id));

        try {
            if (a.endsWith("DONE")) {
                emit(context, "done", r, "once".equals(r.repeat) ? 0L : r.nextAt);
                ReminderStore.incrementDone(context);
                if ("once".equals(r.repeat)) { r.active = false; ReminderStore.save(context, r); AlarmScheduler.cancel(context, id); }
            } else if (a.endsWith("SNOOZE15")) {
                snooze(context, r, System.currentTimeMillis() + 15L * 60L * 1000L);
            } else if (a.endsWith("SNOOZE60")) {
                snooze(context, r, System.currentTimeMillis() + 60L * 60L * 1000L);
            } else if (a.endsWith("MISSED")) {
                emit(context, "missed", r, "once".equals(r.repeat) ? 0L : r.nextAt);
                if ("once".equals(r.repeat)) { r.active = false; ReminderStore.save(context, r); AlarmScheduler.cancel(context, id); }
            } else if (a.endsWith("TOMORROW")) {
                Calendar n = Calendar.getInstance();
                Calendar old = Calendar.getInstance();
                old.setTimeInMillis(r.nextAt);
                n.add(Calendar.DAY_OF_MONTH, 1);
                n.set(Calendar.HOUR_OF_DAY, old.get(Calendar.HOUR_OF_DAY));
                n.set(Calendar.MINUTE, old.get(Calendar.MINUTE));
                n.set(Calendar.SECOND, 0);
                n.set(Calendar.MILLISECOND, 0);
                snooze(context, r, n.getTimeInMillis());
            }
        } catch (Exception ignored) {}
    }

    private void snooze(Context c, Reminder r, long when) throws Exception {
        if ("once".equals(r.repeat)) {
            r.nextAt = when;
            r.active = true;
            ReminderStore.save(c, r);
            AlarmScheduler.schedule(c, r);
            emit(c, "rescheduled", r, when);
        } else {
            AlarmScheduler.scheduleAt(c, r.id, when, true);
            emit(c, "snooze", r, when);
        }
    }

    private void emit(Context c, String type, Reminder r, long nextAt) throws Exception {
        JSONObject e = new JSONObject();
        e.put("type", type);
        e.put("id", r.id);
        e.put("date", ReminderStore.localDateKey());
        e.put("at", new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US).format(new java.util.Date()));
        e.put("nextAt", nextAt);
        ReminderStore.addEvent(c, e);
    }
}
