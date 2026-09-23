package com.atlas.monshi;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class ReminderStore {
    private static final String PREFS = "monshi_native_v1";
    private static final String REMINDER_PREFIX = "reminder_";
    private static final String EVENTS = "events";
    private static final String SUMMARY_TIME = "summary_time";
    private static final String STATS_DATE = "stats_date";
    private static final String STATS_TOTAL = "stats_total";
    private static final String STATS_DONE = "stats_done";
    private static final String STATS_LATE = "stats_late";

    private ReminderStore() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void save(Context c, Reminder r) {
        try {
            prefs(c).edit().putString(REMINDER_PREFIX + r.id, r.toJson().toString()).apply();
        } catch (Exception ignored) {}
    }

    public static Reminder get(Context c, String id) {
        try {
            String s = prefs(c).getString(REMINDER_PREFIX + id, null);
            return s == null ? null : Reminder.fromJson(s);
        } catch (Exception e) {
            return null;
        }
    }

    public static void delete(Context c, String id) {
        prefs(c).edit().remove(REMINDER_PREFIX + id).apply();
    }

    public static List<Reminder> all(Context c) {
        List<Reminder> out = new ArrayList<>();
        for (String key : prefs(c).getAll().keySet()) {
            if (!key.startsWith(REMINDER_PREFIX)) continue;
            try {
                String s = prefs(c).getString(key, null);
                if (s != null) out.add(Reminder.fromJson(s));
            } catch (Exception ignored) {}
        }
        return out;
    }

    public static synchronized void addEvent(Context c, JSONObject event) {
        try {
            SharedPreferences p = prefs(c);
            JSONArray arr = new JSONArray(p.getString(EVENTS, "[]"));
            event.put("eventId", System.currentTimeMillis() + "_" + Math.abs(event.hashCode()));
            arr.put(event);
            JSONArray trimmed = new JSONArray();
            int start = Math.max(0, arr.length() - 100);
            for (int i = start; i < arr.length(); i++) trimmed.put(arr.get(i));
            p.edit().putString(EVENTS, trimmed.toString()).apply();
        } catch (Exception ignored) {}
    }

    public static synchronized String drainEvents(Context c) {
        SharedPreferences p = prefs(c);
        String events = p.getString(EVENTS, "[]");
        p.edit().putString(EVENTS, "[]").apply();
        return events == null ? "[]" : events;
    }

    public static String localDateKey() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    public static void setSummaryTime(Context c, String hhmm) {
        prefs(c).edit().putString(SUMMARY_TIME, hhmm).apply();
    }

    public static String getSummaryTime(Context c) {
        return prefs(c).getString(SUMMARY_TIME, "22:00");
    }

    public static void setStats(Context c, int total, int done, int late, String date) {
        prefs(c).edit()
                .putInt(STATS_TOTAL, Math.max(0, total))
                .putInt(STATS_DONE, Math.max(0, done))
                .putInt(STATS_LATE, Math.max(0, late))
                .putString(STATS_DATE, date == null ? localDateKey() : date)
                .apply();
    }

    public static int[] getStats(Context c) {
        SharedPreferences p = prefs(c);
        if (!localDateKey().equals(p.getString(STATS_DATE, ""))) return null;
        return new int[]{p.getInt(STATS_TOTAL, 0), p.getInt(STATS_DONE, 0), p.getInt(STATS_LATE, 0)};
    }

    public static void incrementDone(Context c) {
        SharedPreferences p = prefs(c);
        String today = localDateKey();
        if (!today.equals(p.getString(STATS_DATE, ""))) {
            setStats(c, 0, 1, 0, today);
            return;
        }
        p.edit().putInt(STATS_DONE, p.getInt(STATS_DONE, 0) + 1).apply();
    }
}
