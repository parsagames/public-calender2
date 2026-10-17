package com.parsagames.amiralmomenincalendar;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import org.json.JSONArray;
import org.json.JSONObject;

public class AdhanScheduler {
    static final String PREFS = "adhan_native";
    static final String ACTION_ALARM = "com.parsagames.amiralmomenincalendar.ADHAN_ALARM";
    static final int MAX_ID = 100;

    static void save(Context c, String times, String file, String url) {
        c.getSharedPreferences(PREFS, 0).edit()
                .putString("times", times)
                .putString("file", file)
                .putString("url", url)
                .apply();
    }

    private static PendingIntent alarmIntent(Context c, int id, String name, int flag) {
        Intent i = new Intent(c, AdhanReceiver.class);
        i.setAction(ACTION_ALARM);
        i.putExtra("name", name);
        return PendingIntent.getBroadcast(c, id, i, flag | PendingIntent.FLAG_IMMUTABLE);
    }

    static void startPlay(Context c, String name) {
        Intent s = new Intent(c, AdhanService.class);
        s.setAction(AdhanService.ACTION_PLAY);
        s.putExtra("name", name);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            c.startForegroundService(s);
        } else {
            c.startService(s);
        }
    }

    static void scheduleAll(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        // remove everything scheduled earlier
        for (int id = 1; id <= MAX_ID; id++) {
            PendingIntent old = alarmIntent(c, id, "", PendingIntent.FLAG_NO_CREATE);
            if (old != null) {
                am.cancel(old);
                old.cancel();
            }
        }
        String json = c.getSharedPreferences(PREFS, 0).getString("times", "[]");
        PendingIntent show = null;
        Intent launch = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
        if (launch != null) {
            show = PendingIntent.getActivity(c, 0, launch, PendingIntent.FLAG_IMMUTABLE);
        }
        long now = System.currentTimeMillis();
        try {
            JSONArray arr = new JSONArray(json);
            for (int k = 0; k < arr.length(); k++) {
                JSONObject o = arr.getJSONObject(k);
                int id = o.getInt("id");
                long at = o.getLong("at");
                String name = o.optString("name", "اذان");
                if (id < 1 || id > MAX_ID || at <= now) continue;
                PendingIntent pi = alarmIntent(c, id, name, PendingIntent.FLAG_UPDATE_CURRENT);
                // setAlarmClock: exact even in Doze and needs no special permission
                am.setAlarmClock(new AlarmManager.AlarmClockInfo(at, show), pi);
            }
        } catch (Exception e) {
            // bad data: nothing is scheduled
        }
    }
}
