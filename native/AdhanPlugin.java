package com.parsagames.amiralmomenincalendar;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;

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
}
