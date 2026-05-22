/*
 * Copyright (C) 2026 sunilpaulmathew <sunil.kde@gmail.com>
 *
 * This file is part of Package Manager, a simple, yet powerful application
 * to manage other application installed on an android device.
 *
 * Created by sunilpaulmathew on May 19, 2026
 */

package com.smartpack.packagemanager.utils;

import android.content.Context;
import androidx.appcompat.app.AppCompatDelegate;
import com.smartpack.packagemanager.R;
import in.sunilpaulmathew.sCommon.CommonUtils.sCommonUtils;

public class ThemeHelper {

    public static void applyTheme(Context context) {
        String theme = sCommonUtils.getString(AppSettings.PREF_APP_THEME, "auto", context);
        switch (theme) {
            case "light":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case "dark":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }

    public static String getThemeSummary(Context context) {
        String theme = sCommonUtils.getString(AppSettings.PREF_APP_THEME, "auto", context);
        switch (theme) {
            case "light":
                return context.getString(R.string.app_theme_light);
            case "dark":
                return context.getString(R.string.app_theme_dark);
            default:
                return context.getString(R.string.app_theme_auto);
        }
    }

    public static int getThemeIndex(Context context) {
        String theme = sCommonUtils.getString(AppSettings.PREF_APP_THEME, "auto", context);
        switch (theme) {
            case "light":
                return 2;
            case "dark":
                return 1;
            default:
                return 0;
        }
    }
}
