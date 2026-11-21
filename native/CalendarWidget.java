package com.parsagames.amiralmomenincalendar;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CalendarWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int i = 0; i < ids.length; i++) {
            manager.updateAppWidget(ids[i], build(context, ids[i], heightOf(manager, ids[i])));
        }
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int id, Bundle newOptions) {
        manager.updateAppWidget(id, build(context, id, newOptions == null ? 0
                : newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)));
    }

    /** current height of the placed widget in dp (0 = unknown -> show everything) */
    static int heightOf(AppWidgetManager mgr, int id) {
        try {
            Bundle o = mgr.getAppWidgetOptions(id);
            return o == null ? 0 : o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0);
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String a = intent == null ? null : intent.getAction();
        if (Intent.ACTION_DATE_CHANGED.equals(a) || Intent.ACTION_TIME_CHANGED.equals(a)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(a)) {
            updateAll(context);
            DateNotifier.refresh(context);
        }
    }

    static void updateAll(Context context) {
        try {
            AppWidgetManager mgr = AppWidgetManager.getInstance(context);
            int[] ids = mgr.getAppWidgetIds(new ComponentName(context, CalendarWidget.class));
            for (int i = 0; i < ids.length; i++) {
                mgr.updateAppWidget(ids[i], build(context, ids[i], heightOf(mgr, ids[i])));
            }
        } catch (Exception e) {
            // ignore
        }
    }


    @Override
    public void onDeleted(Context context, int[] ids) {
        try {
            android.content.SharedPreferences.Editor e = context.getSharedPreferences(AdhanScheduler.PREFS, 0).edit();
            for (int i = 0; i < ids.length; i++) e.remove("wcfg_" + ids[i]);
            e.apply();
        } catch (Exception ex) {
            // ignore
        }
    }

    /** تنظیم هر ویجت: "تم|اندازه"  (تم: app یا شناسه‌ی تم، اندازه: s / m / l) */
    static String[] configOf(Context context, int widgetId) {
        String v = context.getSharedPreferences(AdhanScheduler.PREFS, 0).getString("wcfg_" + widgetId, "app|m");
        String[] p = v.split("\\|");
        return new String[]{p.length > 0 ? p[0] : "app", p.length > 1 ? p[1] : "m"};
    }

    static float scaleOf(String key) {
        if ("s".equals(key)) return 0.85f;
        if ("l".equals(key)) return 1.2f;
        return 1f;
    }

    static RemoteViews build(Context context, int widgetId, int heightDp) {
        String[] c = configOf(context, widgetId);
        return buildWith(context, c[0], c[1], heightDp);
    }

    private static void sz(RemoteViews rv, int id, float sp, float k) {
        rv.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, sp * k);
    }

    private static void col(RemoteViews rv, int id, int color) {
        rv.setTextColor(id, color);
    }

    /** رنگ و اندازه‌ی نوشته‌ها بر اساس تم و اندازه */
    private static void style(Context context, RemoteViews rv, ThemeColors.T t, float k, String themeId) {
        int bg = ThemeColors.drawableId(context, "widget_bg", t.id);
        if (bg != 0) rv.setInt(R.id.w_root, "setBackgroundResource", bg);
        col(rv, R.id.w_weekday, t.gold);  sz(rv, R.id.w_weekday, 19, k);
        col(rv, R.id.w_jalali, t.text);   sz(rv, R.id.w_jalali, 17, k);
        col(rv, R.id.w_hijri, t.azure);   sz(rv, R.id.w_hijri, 14, k);
        col(rv, R.id.w_clock, t.text);    sz(rv, R.id.w_clock, 34, k);
        sz(rv, R.id.w_occ, 14, k);
        col(rv, R.id.w_hair, t.azure);    sz(rv, R.id.w_hair, 12, k);
        col(rv, R.id.w_nail, t.azure);    sz(rv, R.id.w_nail, 12, k);
        col(rv, R.id.w_sew, t.azure);     sz(rv, R.id.w_sew, 12, k);
        col(rv, R.id.w_city, t.faint);    sz(rv, R.id.w_city, 12, k);
        int[] labels = {R.id.w_l_fajr, R.id.w_l_sunrise, R.id.w_l_dhuhr, R.id.w_l_sunset, R.id.w_l_maghrib, R.id.w_l_midnight};
        for (int i = 0; i < labels.length; i++) { col(rv, labels[i], t.dim); sz(rv, labels[i], 12, k); }
        int[] times = {R.id.w_t_fajr, R.id.w_t_sunrise, R.id.w_t_dhuhr, R.id.w_t_sunset, R.id.w_t_maghrib, R.id.w_t_midnight};
        for (int i = 0; i < times.length; i++) { col(rv, times[i], t.text); sz(rv, times[i], 16, k); }
    }

    private static void setText(RemoteViews rv, int id, String text) {
        rv.setTextViewText(id, text == null ? "" : text);
    }

    static RemoteViews buildWith(Context context, String themeId, String sizeKey, int heightDp) {
        RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.widget_calendar);
        final ThemeColors.T th = ThemeColors.get(context, themeId);
        style(context, rv, th, scaleOf(sizeKey), themeId);

        Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (launch != null) {
            PendingIntent pi = PendingIntent.getActivity(context, 11, launch,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            rv.setOnClickPendingIntent(R.id.w_root, pi);
        }

        JSONObject day = null;
        try {
            String all = context.getSharedPreferences(AdhanScheduler.PREFS, 0).getString("widget", "");
            if (all != null && all.length() > 2) {
                String key = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
                day = new JSONObject(all).optJSONObject(key);
            }
        } catch (Exception e) {
            day = null;
        }

        if (day == null) {
            setText(rv, R.id.w_weekday, "تقویم شیعه امیرالمومنین");
            setText(rv, R.id.w_jalali, "برای به‌روزرسانی، برنامه را یک بار باز کنید");
            setText(rv, R.id.w_hijri, "");
            rv.setViewVisibility(R.id.w_occ, View.GONE);
            rv.setViewVisibility(R.id.w_hair, View.GONE);
            rv.setViewVisibility(R.id.w_nail, View.GONE);
            rv.setViewVisibility(R.id.w_sew, View.GONE);
            rv.setViewVisibility(R.id.w_prayer_row, View.GONE);
            rv.setViewVisibility(R.id.w_city, View.GONE);
            return rv;
        }

        setText(rv, R.id.w_weekday, day.optString("wd"));
        setText(rv, R.id.w_jalali, day.optString("j"));
        setText(rv, R.id.w_hijri, day.optString("h") + " · " + day.optString("g"));

        // همهٔ خطوط همیشه نشان داده می‌شوند (حتی در کوچک‌ترین اندازه)
        String occ = day.optString("occ", "");
        if (occ.length() > 0) {
            setText(rv, R.id.w_occ, occ);
            rv.setTextColor(R.id.w_occ, day.optBoolean("hol", false) ? th.hol : th.gold);
            rv.setViewVisibility(R.id.w_occ, View.VISIBLE);
        } else {
            rv.setViewVisibility(R.id.w_occ, View.GONE);
        }
        String hair = day.optString("hair", "");
        if (hair.length() > 0) {
            setText(rv, R.id.w_hair, "اصلاح سر و صورت: " + hair);
            rv.setViewVisibility(R.id.w_hair, View.VISIBLE);
        } else {
            rv.setViewVisibility(R.id.w_hair, View.GONE);
        }
        String nail = day.optString("nail", "");
        if (nail.length() > 0) {
            setText(rv, R.id.w_nail, "ناخن گرفتن: " + nail);
            rv.setViewVisibility(R.id.w_nail, View.VISIBLE);
        } else {
            rv.setViewVisibility(R.id.w_nail, View.GONE);
        }
        String sew = day.optString("sew", "");
        if (sew.length() > 0) {
            setText(rv, R.id.w_sew, "دوخت و دوز: " + sew);
            rv.setViewVisibility(R.id.w_sew, View.VISIBLE);
        } else {
            rv.setViewVisibility(R.id.w_sew, View.GONE);
        }

        JSONObject t = day.optJSONObject("t");
        if (t != null) {
            setText(rv, R.id.w_t_fajr, t.optString("fajr"));
            setText(rv, R.id.w_t_sunrise, t.optString("sunrise"));
            setText(rv, R.id.w_t_dhuhr, t.optString("dhuhr"));
            setText(rv, R.id.w_t_sunset, t.optString("sunset"));
            setText(rv, R.id.w_t_maghrib, t.optString("maghrib"));
            setText(rv, R.id.w_t_midnight, t.optString("midnight"));
            rv.setViewVisibility(R.id.w_prayer_row, View.VISIBLE);
            String city = day.optString("city", "");
            setText(rv, R.id.w_city, city.length() > 0 ? "اوقات شرعی " + city : "");
            rv.setViewVisibility(R.id.w_city, (city.length() > 0 && (heightDp == 0 || heightDp >= 150)) ? View.VISIBLE : View.GONE);
        } else {
            rv.setViewVisibility(R.id.w_prayer_row, View.GONE);
            setText(rv, R.id.w_city, "شهر را در برنامه انتخاب کنید");
            rv.setViewVisibility(R.id.w_city, View.VISIBLE);
        }
        return rv;
    }
}
