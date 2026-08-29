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

import java.util.Objects;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Utility class to build sanitized, user-customizable file names for exported APKs and app bundles.
 * Supports standard upstream naming schemes as well as custom placeholder templates.
 */
public class ExportNameBuilder {

    /**
     * Supported export naming modes.
     * Index alignment matches {@link AppSettings#getAPKNameOptionsMenu(Context)} (0 through 5).
     */
    public enum Mode {
        /** App Name only: e.g. "Package_Manager" (Index 0) */
        APP_NAME,
        /** Package ID only: e.g. "com.smartpack.packagemanager" (Index 1) */
        PACKAGE_NAME,
        /** App Name + Version: e.g. "Package_Manager_8.0" (Index 2) */
        APP_NAME_VERSION,
        /** Package ID + Version: e.g. "com.smartpack.packagemanager_8.0" (Index 3) */
        PACKAGE_VERSION,
        /** App Name + Package ID + Version: e.g. "Package_Manager_com.smartpack.packagemanager_8.0" (Index 4) */
        APP_PACKAGE_VERSION,
        /** Custom template with placeholder tokens (Index 5) */
        CUSTOM_TEMPLATE
    }

    /**
     * Generates a sanitized export filename for the given package according to user preferences.
     *
     * @param context Application/Activity context used to read user preferences
     * @param pi PackageInfo for the target application
     * @return Sanitized filename without extension
     */
    public static String getExportName(Context context, PackageInfo pi) {
        Mode mode = AppSettings.getExportMode(context);
        String separator = AppSettings.getExportSeparator(context);
        String label = pi.applicationInfo != null && context.getPackageManager() != null 
                ? context.getPackageManager().getApplicationLabel(pi.applicationInfo).toString() 
                : pi.packageName;
        
        long versionCode = androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(pi);
        String versionName = Objects.requireNonNullElse(pi.versionName, "unknown");

        switch (mode) {
            case PACKAGE_NAME:
                return sanitize(pi.packageName, separator);
            case APP_NAME_VERSION:
                return sanitize(normalizeLabel(label) + separator + versionName, separator);
            case PACKAGE_VERSION:
                return sanitize(pi.packageName + separator + versionName, separator);
            case APP_PACKAGE_VERSION:
                return sanitize(normalizeLabel(label) + separator + pi.packageName + separator + versionName, separator);
            case CUSTOM_TEMPLATE:
                return formatCustomTemplate(label, pi.packageName, versionName, versionCode, context);
            case APP_NAME:
            default:
                return sanitize(normalizeLabel(label), separator);
        }
    }

    /**
     * Formats a custom filename using template tokens.
     * Supported tokens:
     * <ul>
     *   <li><code>{appname}</code> - Application display name (spaces replaced with _)</li>
     *   <li><code>{packageid}</code> - Android package identifier (e.g. com.example.app)</li>
     *   <li><code>{versionname}</code> - Version name string (e.g. 1.0.0)</li>
     *   <li><code>{versioncode}</code> - Version code number (e.g. 82)</li>
     *   <li><code>{date}</code> - Current date in yyyy-MM-dd format</li>
     * </ul>
     *
     * @param label App label string
     * @param packageName Package identifier
     * @param versionName App version name
     * @param versionCode App version code
     * @param context Context to read separator and template preferences
     * @return Sanitized filename string without extension
     */
    public static String formatCustomTemplate(String label, String packageName, String versionName, long versionCode, Context context) {
        String separator = AppSettings.getExportSeparator(context);
        String template = AppSettings.getExportTemplate(context);
        String result = template
                .replace("{appname}", normalizeLabel(label))
                .replace("{packageid}", Objects.requireNonNullElse(packageName, ""))
                .replace("{versionname}", versionName != null ? versionName : "unknown")
                .replace("{versioncode}", "" + versionCode)
                .replace("{date}", new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date()));
        return sanitize(result, separator);
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

    /**
     * Sanitizes a string for filesystem compatibility by replacing spaces with the configured
     * separator and stripping invalid characters.
     */
    private static String sanitize(String input, String separator) {
        if (input == null) return "";
        // Replace spaces with the separator
        String output = input.replace(" ", separator);
        // Retain only alphanumeric, dots, underscores, hyphens, and the separator itself
        String allowedPattern = "[^a-zA-Z0-9._\\-" + escapeRegex(separator) + "]";
        output = output.replaceAll(allowedPattern, "");
        // Avoid consecutive separators
        if (!separator.isEmpty()) {
            output = output.replaceAll(escapeRegex(separator) + "+", separator);
        }
        return output;
    }

    /**
     * Escapes regex special characters in a custom separator string.
     */
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
