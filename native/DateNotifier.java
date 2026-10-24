package com.parsagames.amiralmomenincalendar;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** اعلان همیشگی تاریخ (شمسی، قمری، میلادی) — با تغییر روز خودکار به‌روز می‌شود. */
public class DateNotifier {
    static final String CHANNEL = "date_channel";
    static final String ACTION_TICK = "com.parsagames.amiralmomenincalendar.DATE_TICK";
    static final String ACTION_DISMISSED = "com.parsagames.amiralmomenincalendar.DATE_DISMISSED";
    static final int NOTIF_ID = 4127;

    static void refresh(Context context) {
        try {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            if (Build.VERSION.SDK_INT >= 33
                    && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return;
            }
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = new NotificationChannel(CHANNEL, "تاریخ امروز", NotificationManager.IMPORTANCE_LOW);
                ch.setShowBadge(false);
                ch.setSound(null, null);
                ch.enableVibration(false);
                ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                nm.createNotificationChannel(ch);
            }

            String title = "تقویم شیعه امیرالمومنین";
            String text = "برای به‌روزرسانی، برنامه را یک بار باز کنید";
            String dayNum = "";
            String occ = "";
            try {
                String all = context.getSharedPreferences(AdhanScheduler.PREFS, 0).getString("widget", "");
                if (all != null && all.length() > 2) {
                    String key = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
                    JSONObject day = new JSONObject(all).optJSONObject(key);
                    if (day != null) {
                        title = day.optString("wd") + " " + day.optString("j");
                        text = day.optString("g") + " ، " + day.optString("h");
                        String j = day.optString("j").trim();
                        int sp = j.indexOf(' ');
                        dayNum = sp > 0 ? j.substring(0, sp) : j;
                        occ = day.optString("occ", "");
                    }
                }
            } catch (Exception e) {
                // keep defaults
            }

            Notification.Builder b = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(context, CHANNEL) : new Notification.Builder(context);
            int icon = context.getResources().getIdentifier("ic_notif", "drawable", context.getPackageName());
            b.setSmallIcon(icon != 0 ? icon : context.getApplicationInfo().icon)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setOngoing(true)
                    .setShowWhen(false)
                    .setOnlyAlertOnce(true)
                    .setCategory(Notification.CATEGORY_STATUS)
                    .setVisibility(Notification.VISIBILITY_PUBLIC);
            if (Build.VERSION.SDK_INT < 26) b.setPriority(Notification.PRIORITY_LOW);

            if (Build.VERSION.SDK_INT >= 24) {
                b.setStyle(new Notification.DecoratedCustomViewStyle());
                b.setCustomContentView(card(context, title, text, dayNum, ""));
                b.setCustomBigContentView(card(context, title, text, dayNum, occ));
            }

            Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
            if (launch != null) {
                b.setContentIntent(PendingIntent.getActivity(context, 12, launch,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
            }
            Intent del = new Intent(context, AdhanReceiver.class).setAction(ACTION_DISMISSED);
            b.setDeleteIntent(PendingIntent.getBroadcast(context, 13, del,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));

            nm.notify(NOTIF_ID, b.build());
            scheduleNextTick(context);
        } catch (Exception e) {
            // ignore
        }
    }

    private static RemoteViews card(Context context, String title, String sub, String dayNum, String occ) {
        RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.notif_date);
        rv.setTextViewText(R.id.n_day, dayNum);
        rv.setTextViewText(R.id.n_title, title);
        rv.setTextViewText(R.id.n_sub, sub);
        if (occ != null && occ.length() > 0) {
            rv.setTextViewText(R.id.n_occ, occ);
            rv.setViewVisibility(R.id.n_occ, View.VISIBLE);
        } else {
            rv.setViewVisibility(R.id.n_occ, View.GONE);
        }
        return rv;
    }

    /** شب بعد از ۰۰:۰۰ دوباره اجرا می‌شود تا تاریخ عوض شود */
    static void scheduleNextTick(Context context) {
        try {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            Calendar c = Calendar.getInstance();
            c.add(Calendar.DAY_OF_YEAR, 1);
            c.set(Calendar.HOUR_OF_DAY, 0);
            c.set(Calendar.MINUTE, 0);
            c.set(Calendar.SECOND, 20);
            c.set(Calendar.MILLISECOND, 0);
            Intent i = new Intent(context, AdhanReceiver.class).setAction(ACTION_TICK);
            PendingIntent pi = PendingIntent.getBroadcast(context, 14, i,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            if (Build.VERSION.SDK_INT >= 23) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), pi);
            } else {
                am.set(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), pi);
            }
        } catch (Exception e) {
            // ignore
        }
    }
}
