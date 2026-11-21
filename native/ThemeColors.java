package com.parsagames.amiralmomenincalendar;

import android.content.Context;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** رنگ‌های تم‌های برنامه برای اعلان و ویجت (همان تم‌های داخل برنامه) */
public class ThemeColors {
    public static class T {
        public String id;
        public int bg, text, dim, gold, azure, faint, hol;
    }

    public static final List<String> IDS = new ArrayList<String>();
    public static final Map<String, String> NAMES = new HashMap<String, String>();
    private static final Map<String, T> MAP = new HashMap<String, T>();

    private static void add(String id, int bg, int text, int dim, int gold, int azure, int faint, int hol) {
        T t = new T();
        t.id = id; t.bg = bg; t.text = text; t.dim = dim; t.gold = gold; t.azure = azure; t.faint = faint; t.hol = hol;
        IDS.add(id);
        MAP.put(id, t);
    }

    static {
        add("light", 0xFFF7F2E4, 0xFF2A2418, 0xFF5F5740, 0xFFA8782A, 0xFF2F8A84, 0xFF85795C, 0xFFC0502F);
        add("green", 0xFF0B2A24, 0xFFF3ECD9, 0xFFA9C2BA, 0xFFE3C581, 0xFF8FD4CE, 0xFF7FA79C, 0xFFFF9A7B);
        add("mint", 0xFFEAF4EE, 0xFF1D2B24, 0xFF4B6258, 0xFF8A6A1F, 0xFF1F7A6D, 0xFF6F8478, 0xFFC0502F);
        add("sky", 0xFFEAF1FA, 0xFF1A2540, 0xFF4A5B7D, 0xFF9A6F1C, 0xFF2467B3, 0xFF6E7F9F, 0xFFC0502F);
        add("navy", 0xFF0B1426, 0xFFEEF2FA, 0xFFA9B8D4, 0xFFEFD28B, 0xFF8CC6F2, 0xFF7F93B8, 0xFFFF9A7B);
        add("plum", 0xFF1D0F1A, 0xFFF6EAF0, 0xFFCBA9BD, 0xFFECC582, 0xFFEAB0D2, 0xFFA07D93, 0xFFFF9A7B);
        NAMES.put("light", "روشن کرم");
        NAMES.put("green", "سبز تیره");
        NAMES.put("mint", "سبز روشن");
        NAMES.put("sky", "آبی آسمانی");
        NAMES.put("navy", "آبی شب");
        NAMES.put("plum", "ارغوانی شب");
    }

    /** تم فعلی برنامه (از داده‌ای که برنامه فرستاده) */
    public static String appThemeId(Context context) {
        try {
            String all = context.getSharedPreferences(AdhanScheduler.PREFS, 0).getString("widget", "");
            if (all != null && all.length() > 2) {
                String id = new JSONObject(all).optString("_theme", "light");
                if (MAP.containsKey(id)) return id;
            }
        } catch (Exception e) {
            // ignore
        }
        return "light";
    }

    /** id می‌تواند "app" باشد = همان تم برنامه */
    public static T get(Context context, String id) {
        if (id == null || "app".equals(id) || !MAP.containsKey(id)) id = appThemeId(context);
        T t = MAP.get(id);
        return t != null ? t : MAP.get("light");
    }

    public static int drawableId(Context context, String prefix, String themeId) {
        return context.getResources().getIdentifier(prefix + "_" + themeId, "drawable", context.getPackageName());
    }
}
