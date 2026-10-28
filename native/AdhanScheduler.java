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

    /** گزارش کوتاه برای عیب‌یابی (۸ خط آخر) */
    static void log(Context c, String msg) {
        try {
            android.content.SharedPreferences sp = c.getSharedPreferences(PREFS, 0);
            String old = sp.getString("log", "");
            String line = new java.text.SimpleDateFormat("MM/dd HH:mm:ss", java.util.Locale.US).format(new java.util.Date()) + " " + msg;
            String[] parts = old.length() == 0 ? new String[0] : old.split("\n");
            StringBuilder sb = new StringBuilder();
            int from = Math.max(0, parts.length - 7);
            for (int i = from; i < parts.length; i++) sb.append(parts[i]).append('\n');
            sb.append(line);
            sp.edit().putString("log", sb.toString()).apply();
        } catch (Exception e) {
            // ignore
        }
    }

    /** آلارم آزمایشی واقعی یک دقیقه بعد (با همان مسیر اذان‌های اصلی) */
    static void scheduleTest(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent show = null;
        Intent launch = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
        if (launch != null) show = PendingIntent.getActivity(c, 0, launch, PendingIntent.FLAG_IMMUTABLE);
        long at = System.currentTimeMillis() + 60000L;
        PendingIntent pi = alarmIntent(c, 99, "آزمایش اذان", PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(at, show), pi);
            log(c, "آلارم آزمایشی برای یک دقیقه بعد ثبت شد");
        } catch (Exception e) {
            log(c, "ثبت آلارم دقیق رد شد (" + e.getClass().getSimpleName() + ")؛ حالت کم‌دقت");
            try {
                if (android.os.Build.VERSION.SDK_INT >= 23) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                } else {
                    am.set(AlarmManager.RTC_WAKEUP, at, pi);
                }
            } catch (Exception e2) {
                log(c, "ثبت آلارم ناموفق: " + e2.getClass().getSimpleName());
            }
        }
    }

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
        int ok = 0, fail = 0;
        String lastErr = "";
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
                try {
                    am.setAlarmClock(new AlarmManager.AlarmClockInfo(at, show), pi);
                    ok++;
                } catch (Exception e1) {
                    // مجوز آلارم دقیق نیست؛ نسخهٔ کم‌دقت‌تر را امتحان می‌کنیم
                    fail++;
                    lastErr = e1.getClass().getSimpleName();
                    try {
                        if (android.os.Build.VERSION.SDK_INT >= 23) {
                            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                        } else {
                            am.set(AlarmManager.RTC_WAKEUP, at, pi);
                        }
                    } catch (Exception e2) {
                        lastErr = e2.getClass().getSimpleName();
                    }
                }
            }
            if (fail > 0) log(c, "ثبت دقیق آلارم رد شد (" + lastErr + ") برای " + fail + " مورد");
        } catch (Exception e) {
            // bad data: nothing is scheduled
        }
    }
}
