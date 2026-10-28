package com.parsagames.amiralmomenincalendar;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class AdhanReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (action == null) return;
        if (AdhanScheduler.ACTION_ALARM.equals(action)) {
            AdhanScheduler.log(context, "آلارم رسید: " + intent.getStringExtra("name"));
            Intent s = new Intent(context, AdhanService.class);
            s.setAction(AdhanService.ACTION_PLAY);
            s.putExtra("name", intent.getStringExtra("name"));
            try {
                if (Build.VERSION.SDK_INT >= 26) {
                    context.startForegroundService(s);
                } else {
                    context.startService(s);
                }
            } catch (Exception e) {
                AdhanScheduler.log(context, "خطا در شروع سرویس: " + e.getClass().getSimpleName());
                try {
                    context.startService(s);
                } catch (Exception e2) {
                    AdhanScheduler.log(context, "خطا در شروع ساده: " + e2.getClass().getSimpleName());
                }
            }
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            AdhanScheduler.scheduleAll(context);
            DateNotifier.refresh(context);
            CalendarWidget.updateAll(context);
        } else if (DateNotifier.ACTION_TICK.equals(action) || DateNotifier.ACTION_DISMISSED.equals(action)) {
            DateNotifier.refresh(context);
            if (DateNotifier.ACTION_TICK.equals(action)) CalendarWidget.updateAll(context);
        }
    }
}
