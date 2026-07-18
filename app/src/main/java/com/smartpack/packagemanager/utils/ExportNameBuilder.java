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
import android.content.pm.PackageInfo;
import android.os.Build;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ExportNameBuilder {

    public enum Mode {
        PACKAGE_NAME,
        APP_NAME,
        APP_PACKAGE_VERSION,
        CUSTOM_TEMPLATE
    }

    public static String getExportName(Context context, PackageInfo pi) {
        Mode mode = AppSettings.getExportMode(context);
        String separator = AppSettings.getExportSeparator(context);
        String label = pi.applicationInfo != null && context.getPackageManager() != null 
                ? context.getPackageManager().getApplicationLabel(pi.applicationInfo).toString() 
                : pi.packageName;
        
        long versionCode = androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(pi);
        String versionName = pi.versionName != null ? pi.versionName : "unknown";

        switch (mode) {
            case APP_NAME:
                return sanitize(normalizeLabel(label), separator);
            case APP_PACKAGE_VERSION:
                return sanitize(normalizeLabel(label) + separator + pi.packageName + separator + versionName + separator + versionCode, separator);
            case CUSTOM_TEMPLATE:
                String template = AppSettings.getExportTemplate(context);
                String result = template
                        .replace("{appname}", normalizeLabel(label))
                        .replace("{packageid}", pi.packageName)
                        .replace("{versionname}", versionName)
                        .replace("{versioncode}", String.valueOf(versionCode))
                        .replace("{date}", new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date()));
                return sanitize(result, separator);
            case PACKAGE_NAME:
            default:
                return sanitize(pi.packageName, separator);
        }
    }

    /**
     * Normalizes app label for use in filenames: replaces spaces with underscores.
     * This is applied to the AppName component specifically so spaces in app names
     * are always represented by '_', independent of the user-configured separator.
     */
    private static String normalizeLabel(String label) {
        if (label == null) return "";
        return label.replace(" ", "_");
    }

    private static String sanitize(String input, String separator) {
        if (input == null) return "";
        // Replace spaces with the separator
        String output = input.replace(" ", separator);
        // Retain only alphanumeric, dots, underscores, hyphens, and the separator itself
        // To be safe, let's keep a-zA-Z0-9, dot (.), underscore (_), hyphen (-) and whatever the separator is
        String allowedPattern = "[^a-zA-Z0-9._\\-" + escapeRegex(separator) + "]";
        output = output.replaceAll(allowedPattern, "");
        // Avoid consecutive separators
        if (!separator.isEmpty()) {
            output = output.replaceAll(escapeRegex(separator) + "+", separator);
        }
        return output;
    }

    private static String escapeRegex(String input) {
        if (input == null || input.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : input.toCharArray()) {
            if ("<([{\\^-=$!|]})?*+.>".indexOf(c) != -1) {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
