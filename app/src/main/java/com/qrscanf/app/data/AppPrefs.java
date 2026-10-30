package com.qrscanf.app.data;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppPrefs {

    private static final String PREFS = "qrscanf_settings";
    private static final String KEY_AUTO_COPY = "auto_copy";
    private static final String KEY_BEEP = "beep";
    private static final String KEY_DEDUP = "dedup";

    private AppPrefs() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isAutoCopy(Context context) {
        return prefs(context).getBoolean(KEY_AUTO_COPY, false);
    }

    public static void setAutoCopy(Context context, boolean value) {
        prefs(context).edit().putBoolean(KEY_AUTO_COPY, value).apply();
    }

    public static boolean isBeep(Context context) {
        return prefs(context).getBoolean(KEY_BEEP, true);
    }

    public static void setBeep(Context context, boolean value) {
        prefs(context).edit().putBoolean(KEY_BEEP, value).apply();
    }

    public static boolean isDedup(Context context) {
        return prefs(context).getBoolean(KEY_DEDUP, true);
    }

    public static void setDedup(Context context, boolean value) {
        prefs(context).edit().putBoolean(KEY_DEDUP, value).apply();
    }

    public static void reset(Context context) {
        prefs(context).edit().clear().apply();
    }
}
