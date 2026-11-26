package com.parsagames.shiacalendar.amiralmomenin;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.os.Build;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * اعلان روی گوشی از سرور:
 * هر ~۳۰ دقیقه فایل push.json را از Supabase می‌خواند و اگر id جدید بود، اعلان نشان می‌دهد.
 * فایل: {"id":1,"active":true,"title":"عنوان","text":"متن","expires":"2027-01-30"}
 */
public class AnnounceNotifier {
    static final String ACTION_POLL = "com.parsagames.shiacalendar.amiralmomenin.PUSH_POLL";
    static final String CHANNEL = "push_channel";
    static final int NOTIF_ID = 4128;
    static final String SUPABASE = "https://chhsridbkvmqrkhzdszc.supabase.co";
    static final String FILE_URL = SUPABASE + "/storage/v1/object/public/app/push.json";
    static final long EVERY_MS = 30L * 60L * 1000L;
    private static final String PREF_LAST = "push_last_id";

    private static PendingIntent pending(Context c, int flags) {
        Intent i = new Intent(c, AdhanReceiver.class).setAction(ACTION_POLL);
        return PendingIntent.getBroadcast(c, 15, i, flags | PendingIntent.FLAG_IMMUTABLE);
    }

    /** اگر آلارم بررسی هنوز تنظیم نشده بود (مثلاً بعد از ریبوت)، تنظیمش می‌کند. */
    static void ensureScheduled(Context c) {
        try {
            if (pending(c, PendingIntent.FLAG_NO_CREATE) != null) return;
            scheduleIn(c, 20L * 1000L);
        } catch (Exception e) {
            // ignore
        }
    }

    static void scheduleIn(Context c, long delayMs) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            PendingIntent pi = pending(c, PendingIntent.FLAG_UPDATE_CURRENT);
            long at = System.currentTimeMillis() + delayMs;
            if (Build.VERSION.SDK_INT >= 23) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } else {
                am.set(AlarmManager.RTC_WAKEUP, at, pi);
            }
        } catch (Exception e) {
            // ignore
        }
    }

    /** از AdhanReceiver صدا زده می‌شود. شبکه در یک نخ جدا انجام می‌شود. */
    static void onAlarm(final Context context, final BroadcastReceiver.PendingResult result) {
        scheduleIn(context, EVERY_MS);          // نوبت بعدی
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    check(context);
                } catch (Throwable t) {
                    // ignore
                } finally {
                    try { result.finish(); } catch (Throwable t2) { /* ignore */ }
                }
            }
        }).start();
    }

    private static void check(Context c) throws Exception {
        String body = fetch(FILE_URL + "?t=" + System.currentTimeMillis());
        if (body == null) return;
        JSONObject j = new JSONObject(body);
        if (!j.optBoolean("active", true)) return;
        String id = j.optString("id", "");
        String title = j.optString("title", "");
        String text = j.optString("text", "");
        if (id.length() == 0 || (title.length() == 0 && text.length() == 0)) return;
        String exp = j.optString("expires", "");
        if (exp.length() >= 10) {
            try {
                java.util.Date d = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                        .parse(exp.substring(0, 10) + " 23:59:59");
                if (d != null && System.currentTimeMillis() > d.getTime()) return;
            } catch (Exception e) {
                // ignore bad date
            }
        }
        android.content.SharedPreferences sp = c.getSharedPreferences(AdhanScheduler.PREFS, 0);
        if (id.equals(sp.getString(PREF_LAST, ""))) return;
        if (show(c, title, text)) {
            sp.edit().putString(PREF_LAST, id).apply();
        }
    }

    private static String fetch(String u) {
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) new URL(u).openConnection();
            con.setConnectTimeout(8000);
            con.setReadTimeout(8000);
            con.setUseCaches(false);
            con.setRequestProperty("Cache-Control", "no-cache");
            if (con.getResponseCode() != 200) return null;
            InputStream in = con.getInputStream();
            BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[2048];
            int n;
            while ((n = r.read(buf)) > 0 && sb.length() < 20000) sb.append(buf, 0, n);
            r.close();
            return sb.toString();
        } catch (Exception e) {
            return null;
        } finally {
            if (con != null) con.disconnect();
        }
    }

    private static boolean show(Context c, String title, String text) {
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return false;
            if (Build.VERSION.SDK_INT >= 33
                    && c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return false;   // اجازه نیست؛ بعداً دوباره تلاش می‌شود
            }
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = new NotificationChannel(CHANNEL, "اطلاعیه‌ها", NotificationManager.IMPORTANCE_HIGH);
                ch.setShowBadge(true);
                nm.createNotificationChannel(ch);
            }
            Notification.Builder b = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(c, CHANNEL) : new Notification.Builder(c);
            int icon = c.getResources().getIdentifier("ic_notif", "drawable", c.getPackageName());
            b.setSmallIcon(icon != 0 ? icon : c.getApplicationInfo().icon)
                    .setContentTitle(title.length() > 0 ? title : "تقویم شیعه امیرالمومنین")
                    .setContentText(text)
                    .setStyle(new Notification.BigTextStyle().bigText(text))
                    .setAutoCancel(true)
                    .setWhen(System.currentTimeMillis())
                    .setShowWhen(true);
            if (Build.VERSION.SDK_INT < 26) b.setPriority(Notification.PRIORITY_HIGH);
            Intent launch = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
            if (launch != null) {
                b.setContentIntent(PendingIntent.getActivity(c, 16, launch,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
            }
            nm.notify(NOTIF_ID, b.build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
