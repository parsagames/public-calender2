package com.parsagames.amiralmomenincalendar;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
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
            manager.updateAppWidget(ids[i], build(context));
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String a = intent == null ? null : intent.getAction();
        if (Intent.ACTION_DATE_CHANGED.equals(a) || Intent.ACTION_TIME_CHANGED.equals(a)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(a)) {
            updateAll(context);
        }
    }

    static void updateAll(Context context) {
        try {
            AppWidgetManager mgr = AppWidgetManager.getInstance(context);
            int[] ids = mgr.getAppWidgetIds(new ComponentName(context, CalendarWidget.class));
            for (int i = 0; i < ids.length; i++) {
                mgr.updateAppWidget(ids[i], build(context));
            }
        } catch (Exception e) {
            // ignore
        }
    }

    private static void setText(RemoteViews rv, int id, String text) {
        rv.setTextViewText(id, text == null ? "" : text);
    }

    static RemoteViews build(Context context) {
        RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.widget_calendar);

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
            setText(rv, R.id.w_greg, "");
            setText(rv, R.id.w_hijri, "");
            rv.setViewVisibility(R.id.w_occ, View.GONE);
            rv.setViewVisibility(R.id.w_hair, View.GONE);
            rv.setViewVisibility(R.id.w_prayer_row, View.GONE);
            rv.setViewVisibility(R.id.w_city, View.GONE);
            return rv;
        }

        setText(rv, R.id.w_weekday, day.optString("wd"));
        setText(rv, R.id.w_jalali, day.optString("j"));
        setText(rv, R.id.w_greg, day.optString("g"));
        setText(rv, R.id.w_hijri, day.optString("h"));

        String occ = day.optString("occ", "");
        if (occ.length() > 0) {
            setText(rv, R.id.w_occ, occ);
            rv.setTextColor(R.id.w_occ, day.optBoolean("hol", false) ? 0xFFFF9A7B : 0xFFE3C581);
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

        JSONObject t = day.optJSONObject("t");
        if (t != null) {
            setText(rv, R.id.w_t_fajr, t.optString("fajr"));
            setText(rv, R.id.w_t_sunrise, t.optString("sunrise"));
            setText(rv, R.id.w_t_dhuhr, t.optString("dhuhr"));
            setText(rv, R.id.w_t_sunset, t.optString("sunset"));
            setText(rv, R.id.w_t_maghrib, t.optString("maghrib"));
            rv.setViewVisibility(R.id.w_prayer_row, View.VISIBLE);
            String city = day.optString("city", "");
            setText(rv, R.id.w_city, city.length() > 0 ? "اوقات شرعی " + city : "");
            rv.setViewVisibility(R.id.w_city, city.length() > 0 ? View.VISIBLE : View.GONE);
        } else {
            rv.setViewVisibility(R.id.w_prayer_row, View.GONE);
            setText(rv, R.id.w_city, "شهر را در برنامه انتخاب کنید");
            rv.setViewVisibility(R.id.w_city, View.VISIBLE);
        }
        return rv;
    }
}
