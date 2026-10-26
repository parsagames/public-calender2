package com.parsagames.amiralmomenincalendar;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.os.PowerManager;
import android.provider.Settings;
import android.content.pm.PackageManager;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "Adhan")
public class AdhanPlugin extends Plugin {

    @PluginMethod
    public void schedule(PluginCall call) {
        String times = call.getString("times", "[]");
        String file = call.getString("file", "");
        String url = call.getString("url", "");
        AdhanScheduler.save(getContext(), times, file, url);
        AdhanScheduler.scheduleAll(getContext());
        call.resolve();
    }

    @PluginMethod
    public void playNow(PluginCall call) {
        String file = call.getString("file", "");
        String url = call.getString("url", "");
        String name = call.getString("name", "اذان");
        if ((file != null && file.length() > 0) || (url != null && url.length() > 0)) {
            getContext().getSharedPreferences(AdhanScheduler.PREFS, 0).edit()
                    .putString("file", file).putString("url", url).apply();
        }
        AdhanScheduler.startPlay(getContext(), name);
        call.resolve();
    }

    @PluginMethod
    public void cancel(PluginCall call) {
        AdhanScheduler.save(getContext(), "[]", "", "");
        AdhanScheduler.scheduleAll(getContext());
        call.resolve();
    }

    @PluginMethod
    public void setWidgetData(PluginCall call) {
        String json = call.getString("json", "");
        getContext().getSharedPreferences(AdhanScheduler.PREFS, 0).edit().putString("widget", json).apply();
        CalendarWidget.updateAll(getContext());
        DateNotifier.refresh(getContext());
        call.resolve();
    }

    @PluginMethod
    public void stop(PluginCall call) {
        AdhanService.requestStop(getContext());
        call.resolve();
    }

    @PluginMethod
    public void requestNotifPermission(PluginCall call) {
        if (Build.VERSION.SDK_INT >= 33 && getActivity() != null) {
            if (getContext().checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                getActivity().requestPermissions(
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 9917);
            }
        }
        call.resolve();
    }

    /** وضعیت اذان‌گو برای نمایش در برنامه (چند آلارم ثبت شده، مجوز اعلان، محدودیت باتری، صدای آلارم) */
    @PluginMethod
    public void status(PluginCall call) {
        Context c = getContext();
        JSObject o = new JSObject();
        int n = 0;
        long next = 0;
        try {
            JSONArray a = new JSONArray(c.getSharedPreferences(AdhanScheduler.PREFS, 0).getString("times", "[]"));
            long now = System.currentTimeMillis();
            for (int i = 0; i < a.length(); i++) {
                JSONObject t = a.getJSONObject(i);
                long at = t.optLong("at", 0);
                if (at > now) {
                    n++;
                    if (next == 0 || at < next) next = at;
                }
            }
        } catch (Exception e) {
            // ignore
        }
        o.put("scheduled", n);
        o.put("next", next);
        boolean notif = true;
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && Build.VERSION.SDK_INT >= 24) notif = nm.areNotificationsEnabled();
        } catch (Exception e) {
            // ignore
        }
        o.put("notif", notif);
        boolean battery = true;
        try {
            PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
            if (pm != null && Build.VERSION.SDK_INT >= 23) battery = pm.isIgnoringBatteryOptimizations(c.getPackageName());
        } catch (Exception e) {
            // ignore
        }
        o.put("battery", battery);
        int vol = -1;
        try {
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) vol = am.getStreamVolume(AudioManager.STREAM_ALARM);
        } catch (Exception e) {
            // ignore
        }
        o.put("alarmVol", vol);
        call.resolve(o);
    }

    /** باز کردن تنظیمات «بدون محدودیت باتری» برنامه */
    @PluginMethod
    public void openBattery(PluginCall call) {
        Context c = getContext();
        try {
            Intent i = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        } catch (Exception e) {
            try {
                Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                i.setData(Uri.fromParts("package", c.getPackageName(), null));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(i);
            } catch (Exception e2) {
                // ignore
            }
        }
        call.resolve();
    }
}
