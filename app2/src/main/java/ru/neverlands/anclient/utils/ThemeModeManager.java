package ru.neverlands.anclient.utils;

import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;

/**
 * Central DayNight mode switch for ANClient UI. WebView/game frames keep their own HTML colors.
 */
public final class ThemeModeManager {
    public static final String KEY_FORCE_DARK_THEME = "force_dark_theme";

    private ThemeModeManager() {
    }

    public static boolean isForceDarkEnabled(Context context) {
        if (context == null) {
            return false;
        }
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(KEY_FORCE_DARK_THEME, false);
    }

    public static void applyFromPreferences(Context context) {
        applyNightMode(isForceDarkEnabled(context));
    }

    public static void setForceDarkEnabled(Context context, boolean enabled) {
        if (context != null) {
            PreferenceManager.getDefaultSharedPreferences(context)
                    .edit()
                    .putBoolean(KEY_FORCE_DARK_THEME, enabled)
                    .apply();
        }
        applyNightMode(enabled);
    }

    private static void applyNightMode(boolean forceDark) {
        int mode = forceDark
                ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        if (AppCompatDelegate.getDefaultNightMode() != mode) {
            AppCompatDelegate.setDefaultNightMode(mode);
        }
    }
}
