package com.atlas.monshi;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

public final class AlarmScheduler {
    public static final String ACTION_ALARM = "com.atlas.monshi.ALARM";
    public static final String ACTION_SUMMARY = "com.atlas.monshi.SUMMARY";

    private AlarmScheduler() {}

    private static int code(String s) {
        return (s == null ? 0 : s.hashCode()) & 0x7fffffff;
    }

    private static PendingIntent alarmPendingIntent(Context c, String id, boolean snoozeOnly, long triggerAt) {
        Intent i = new Intent(c, AlarmReceiver.class);
        i.setAction(ACTION_ALARM);
        i.putExtra("id", id);
        i.putExtra("snoozeOnly", snoozeOnly);
        int request = snoozeOnly ? code(id + ":snooze:" + triggerAt) : code(id);
        return PendingIntent.getBroadcast(c, request, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static void schedule(Context c, Reminder r) {
        if (r == null || !r.active || r.nextAt <= 0) return;
        ReminderStore.save(c, r);
        scheduleAt(c, r.id, r.nextAt, false);
    }

    public static void scheduleAt(Context c, String id, long triggerAt, boolean snoozeOnly) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = alarmPendingIntent(c, id, snoozeOnly, triggerAt);
        long when = Math.max(triggerAt, System.currentTimeMillis() + 500L);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
        }
    }

    public static void cancel(Context c, String id) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        am.cancel(alarmPendingIntent(c, id, false, 0));
    }

    public static long nextOccurrence(Reminder r, long fromMillis) {
        if (r == null) return 0L;
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(fromMillis);
        switch (r.repeat) {
            case "daily":
                c.add(Calendar.DAY_OF_MONTH, 1);
                break;
            case "weekly":
                c.add(Calendar.DAY_OF_MONTH, 7);
                break;
            case "monthly": {
                int day = c.get(Calendar.DAY_OF_MONTH);
                int hour = c.get(Calendar.HOUR_OF_DAY);
                int minute = c.get(Calendar.MINUTE);
                c.set(Calendar.DAY_OF_MONTH, 1);
                c.add(Calendar.MONTH, 1);
                int max = c.getActualMaximum(Calendar.DAY_OF_MONTH);
                c.set(Calendar.DAY_OF_MONTH, Math.min(day, max));
                c.set(Calendar.HOUR_OF_DAY, hour);
                c.set(Calendar.MINUTE, minute);
                c.set(Calendar.SECOND, 0);
                c.set(Calendar.MILLISECOND, 0);
                break;
            }
            case "hours":
                c.add(Calendar.HOUR_OF_DAY, Math.max(1, r.hours));
                break;
            case "weekdays": {
                Set<Integer> wanted = new HashSet<>(r.weekdays);
                if (wanted.isEmpty()) return 0L;
                for (int i = 0; i < 14; i++) {
                    c.add(Calendar.DAY_OF_MONTH, 1);
                    int jsDay = c.get(Calendar.DAY_OF_WEEK) - 1; // 0=Sunday ... 6=Saturday
                    if (wanted.contains(jsDay)) break;
                }
                break;
            }
            default:
                return 0L;
        }
        int guard = 0;
        long now = System.currentTimeMillis();
        while (c.getTimeInMillis() <= now && guard++ < 400) {
            long n = nextOccurrenceSingle(r, c.getTimeInMillis());
            if (n <= c.getTimeInMillis()) break;
            c.setTimeInMillis(n);
        }
        return c.getTimeInMillis();
    }

    private static long nextOccurrenceSingle(Reminder r, long fromMillis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(fromMillis);
        switch (r.repeat) {
            case "daily": c.add(Calendar.DAY_OF_MONTH, 1); break;
            case "weekly": c.add(Calendar.DAY_OF_MONTH, 7); break;
            case "hours": c.add(Calendar.HOUR_OF_DAY, Math.max(1, r.hours)); break;
            case "monthly": {
                int day = c.get(Calendar.DAY_OF_MONTH);
                c.set(Calendar.DAY_OF_MONTH, 1);
                c.add(Calendar.MONTH, 1);
                c.set(Calendar.DAY_OF_MONTH, Math.min(day, c.getActualMaximum(Calendar.DAY_OF_MONTH)));
                break;
            }
            case "weekdays": {
                Set<Integer> wanted = new HashSet<>(r.weekdays);
                for (int i = 0; i < 14; i++) {
                    c.add(Calendar.DAY_OF_MONTH, 1);
                    if (wanted.contains(c.get(Calendar.DAY_OF_WEEK) - 1)) break;
                }
                break;
            }
            default: return 0L;
        }
        return c.getTimeInMillis();
    }

    public static void rescheduleAll(Context c) {
        long now = System.currentTimeMillis();
        for (Reminder r : ReminderStore.all(c)) {
            if (!r.active) continue;
            if (r.nextAt <= now && !"once".equals(r.repeat)) {
                r.nextAt = nextOccurrence(r, r.nextAt);
                ReminderStore.save(c, r);
            }
            if (r.nextAt <= now && "once".equals(r.repeat)) r.nextAt = now + 3000L;
            schedule(c, r);
        }
    }

    public static void scheduleSummary(Context c, String hhmm) {
        if (hhmm == null || !hhmm.matches("\\d{2}:\\d{2}")) hhmm = "22:00";
        ReminderStore.setSummaryTime(c, hhmm);
        String[] p = hhmm.split(":");
        int h = Integer.parseInt(p[0]), m = Integer.parseInt(p[1]);
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, h);
        cal.set(Calendar.MINUTE, m);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_MONTH, 1);
        Intent i = new Intent(c, SummaryReceiver.class).setAction(ACTION_SUMMARY);
        PendingIntent pi = PendingIntent.getBroadcast(c, 191919, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms())
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
            else
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
        }
    }
}
