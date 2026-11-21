package com.parsagames.amiralmomenincalendar;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.RemoteViews;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** صفحه‌ی تنظیم ویجت هنگام اضافه کردن: انتخاب تم و اندازه‌ی نوشته، همراه با پیش‌نمایش زنده */
public class WidgetConfigActivity extends Activity {
    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private String themeId = "app";
    private String sizeKey = "m";
    private FrameLayout previewBox;
    private final List<TextView> themeChips = new ArrayList<TextView>();
    private final List<String> themeChipIds = new ArrayList<String>();
    private final List<TextView> sizeChips = new ArrayList<TextView>();
    private final String[] sizeKeys = {"s", "m", "l"};

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setResult(RESULT_CANCELED);
        Intent in = getIntent();
        if (in != null && in.getExtras() != null) {
            widgetId = in.getExtras().getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        }
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }
        String[] c = CalendarWidget.configOf(this, widgetId);
        themeId = c[0];
        sizeKey = c[1];

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.parseColor("#F4F1E8"));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(24), dp(16), dp(24));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        sv.addView(root, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(label("تنظیم ویجت", 22, true, "#2A2418"));
        root.addView(label("پیش‌نمایش", 14, false, "#6B6249"));

        previewBox = new FrameLayout(this);
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(170));
        pl.topMargin = dp(8);
        pl.bottomMargin = dp(16);
        root.addView(previewBox, pl);

        root.addView(label("تم ویجت", 16, true, "#2A2418"));
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        hs.addView(row);
        addThemeChip(row, "app", "مثل برنامه", "#FFFFFF", "#2A2418");
        for (int i = 0; i < ThemeColors.IDS.size(); i++) {
            String id = ThemeColors.IDS.get(i);
            ThemeColors.T t = ThemeColors.get(this, id);
            addThemeChip(row, id, ThemeColors.NAMES.get(id), hex(t.bg), hex(t.text));
        }
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hl.topMargin = dp(8);
        hl.bottomMargin = dp(16);
        root.addView(hs, hl);

        root.addView(label("اندازه‌ی نوشته‌ها", 16, true, "#2A2418"));
        LinearLayout sr = new LinearLayout(this);
        sr.setOrientation(LinearLayout.HORIZONTAL);
        sr.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        String[] sn = {"کوچک", "متوسط", "بزرگ"};
        for (int i = 0; i < 3; i++) {
            final String k = sizeKeys[i];
            TextView tv = chip(sn[i], "#FFFFFF", "#2A2418");
            tv.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { sizeKey = k; refresh(); }
            });
            sizeChips.add(tv);
            sr.addView(tv);
        }
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sl.topMargin = dp(8);
        sl.bottomMargin = dp(24);
        root.addView(sr, sl);

        TextView ok = chip("تأیید و افزودن ویجت", "#CDA24D", "#1A1208");
        ok.setGravity(Gravity.CENTER);
        ok.setTextSize(17);
        ok.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { save(); }
        });
        LinearLayout.LayoutParams ol = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        root.addView(ok, ol);

        setContentView(sv);
        refresh();
    }

    private static String hex(int c) {
        return String.format("#%06X", c & 0xFFFFFF);
    }

    private TextView label(String text, int sp, boolean bold, String color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(Color.parseColor(color));
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        t.setGravity(Gravity.RIGHT);
        t.setPadding(0, dp(6), 0, dp(2));
        return t;
    }

    private TextView chip(String text, String bg, String fg) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(15);
        t.setTextColor(Color.parseColor(fg));
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(16), dp(10), dp(16), dp(10));
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.parseColor(bg));
        g.setCornerRadius(dp(14));
        g.setStroke(dp(2), Color.parseColor("#D9CDAD"));
        t.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(8);
        t.setLayoutParams(lp);
        return t;
    }

    private void addThemeChip(LinearLayout row, final String id, String name, String bg, String fg) {
        TextView tv = chip(name, bg, fg);
        tv.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { themeId = id; refresh(); }
        });
        themeChips.add(tv);
        themeChipIds.add(id);
        row.addView(tv);
    }

    private void mark(TextView tv, boolean on) {
        GradientDrawable g = (GradientDrawable) tv.getBackground();
        g.setStroke(dp(on ? 3 : 2), Color.parseColor(on ? "#A8782A" : "#D9CDAD"));
    }

    /** انتخاب‌ها را علامت می‌زند و پیش‌نمایش را دوباره می‌سازد */
    private void refresh() {
        for (int i = 0; i < themeChips.size(); i++) mark(themeChips.get(i), themeChipIds.get(i).equals(themeId));
        for (int i = 0; i < sizeChips.size(); i++) mark(sizeChips.get(i), sizeKeys[i].equals(sizeKey));
        try {
            previewBox.removeAllViews();
            RemoteViews rv = CalendarWidget.buildWith(this, themeId, sizeKey, 0);
            View v = rv.apply(getApplicationContext(), previewBox);
            previewBox.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } catch (Exception e) {
            // preview is optional
        }
    }

    private void save() {
        try {
            getSharedPreferences(AdhanScheduler.PREFS, 0).edit().putString("wcfg_" + widgetId, themeId + "|" + sizeKey).apply();
            AppWidgetManager mgr = AppWidgetManager.getInstance(this);
            mgr.updateAppWidget(widgetId, CalendarWidget.build(this, widgetId, CalendarWidget.heightOf(mgr, widgetId)));
        } catch (Exception e) {
            // ignore
        }
        Intent r = new Intent();
        r.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        setResult(RESULT_OK, r);
        finish();
    }
}
