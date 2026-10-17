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
            Intent s = new Intent(context, AdhanService.class);
            s.setAction(AdhanService.ACTION_PLAY);
            s.putExtra("name", intent.getStringExtra("name"));
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(s);
            } else {
                context.startService(s);
            }
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            AdhanScheduler.scheduleAll(context);
        }
    }
}
