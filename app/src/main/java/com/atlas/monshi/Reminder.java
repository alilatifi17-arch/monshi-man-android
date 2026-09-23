package com.atlas.monshi;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class Reminder {
    public String id = "";
    public String title = "";
    public String note = "";
    public long nextAt = 0L;
    public String repeat = "once";
    public int hours = 1;
    public List<Integer> weekdays = new ArrayList<>();
    public boolean active = true;

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("title", title);
        o.put("note", note);
        o.put("nextAt", nextAt);
        o.put("repeat", repeat);
        o.put("hours", hours);
        JSONArray a = new JSONArray();
        for (Integer d : weekdays) a.put(d);
        o.put("weekdays", a);
        o.put("active", active);
        return o;
    }

    public static Reminder fromJson(String json) throws JSONException {
        return fromJson(new JSONObject(json));
    }

    public static Reminder fromJson(JSONObject o) throws JSONException {
        Reminder r = new Reminder();
        r.id = o.optString("id", "");
        r.title = o.optString("title", "");
        r.note = o.optString("note", "");
        r.nextAt = o.optLong("nextAt", 0L);
        r.repeat = o.optString("repeat", "once");
        r.hours = Math.max(1, o.optInt("hours", 1));
        JSONArray a = o.optJSONArray("weekdays");
        if (a != null) for (int i = 0; i < a.length(); i++) r.weekdays.add(a.optInt(i));
        r.active = o.optBoolean("active", true);
        return r;
    }
}
