/*
 * Copyright (C) 2021-2022 sunilpaulmathew <sunil.kde@gmail.com>
 *
 * This file is part of Package Manager, a simple, yet powerful application
 * to manage other application installed on an android device.
 *
 */

package com.smartpack.packagemanager.utils;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.widget.ProgressBar;

import com.smartpack.packagemanager.R;
import com.smartpack.packagemanager.utils.SerializableItems.PackageItems;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import in.sunilpaulmathew.sCommon.CommonUtils.sCommonUtils;
import in.sunilpaulmathew.sCommon.FileUtils.sFileUtils;

/*
 * Created by sunilpaulmathew <sunil.kde@gmail.com> on January 12, 2020
 */
public class PackageData {

    /**
     * Initialize package data loading.
     * This method triggers generateData with no progress bar.
     * @param activity Activity context used for package manager access.
     */
    public static void init(Activity activity) {
        if (mRawData == null) {
            generateData(null, activity, false);
        }
    }

    private static volatile List<PackageItems> mRawData = null;
    private static volatile List<PackageItems> mRemovedData = null;
    private static volatile boolean mIsUninstalledScanned = false;

    public static boolean isTextMatched(String text, String searchText) {
        for (int a = 0; a < text.length() - searchText.length() + 1; a++) {
            if (searchText.equalsIgnoreCase(text.substring(a, a + searchText.length()))) {
                return true;
            }
        }
        return false;
    }

    public static int getSortingType(Context context) {
        return sCommonUtils.getInt("sort_apps", 0, context);
    }

    public static void makePackageFolder(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if (getPackageDir(context).exists() && getPackageDir(context).isFile()) {
                sFileUtils.delete(getPackageDir(context));
            }
            sFileUtils.mkdir(getPackageDir(context));
        } else {
            if (!getPackageDir(context).exists()) {
                sFileUtils.mkdir(getPackageDir(context));
            }
        }

    }

    public static List<PackageItems> getData(String searchTxt, Context context) {
        android.util.Log.d("SmartPack", "getData: searchTxt=" + searchTxt + ", mRawData is " + (mRawData == null ? "null" : "size " + mRawData.size()));
        if (getRawData() == null) {
            generateData(null, context);
        }
        List<PackageItems> mData = new ArrayList<>();
        List<PackageItems> rawData = getRawData();
        if (rawData != null) {
            String appTypePref = sCommonUtils.getString("appTypes", "all", context);
            for (PackageItems item : rawData) {
                boolean mAppType;
                if (appTypePref.equals("system")) {
                    mAppType = item.isSystemApp();
                } else if (appTypePref.equals("user")) {
                    mAppType = item.isUserApp();
                } else {
                    mAppType = true;
                }
                if (mAppType && item.getPackageName().contains(".")) {
                    if (searchTxt == null || (isTextMatched(item.getAppName(), searchTxt)
                            || isTextMatched(item.getPackageName(), searchTxt))) {
                        mData.add(item);
                    }
                }
            }
            int sortType = PackageData.getSortingType(context);
            if (sortType == 0) {
                mData.sort((lhs, rhs) -> {
                    String nameA = lhs.getAppName() != null ? lhs.getAppName() : (lhs.getPackageName() != null ? lhs.getPackageName() : "");
                    String nameB = rhs.getAppName() != null ? rhs.getAppName() : (rhs.getPackageName() != null ? rhs.getPackageName() : "");
                    return String.CASE_INSENSITIVE_ORDER.compare(nameA, nameB);
                });
            } else if (sortType == 4) {
                mData.sort(Comparator.comparingLong(PackageItems::getAPKSize));
            } else if (sortType == 2) {
                mData.sort(Comparator.comparingLong(PackageItems::getInstalledTime));
            } else if (sortType == 3) {
                mData.sort(Comparator.comparingLong(PackageItems::getUpdatedTime));
            } else {
                mData.sort((lhs, rhs) -> {
                    String pkgA = lhs.getPackageName() != null ? lhs.getPackageName() : "";
                    String pkgB = rhs.getPackageName() != null ? rhs.getPackageName() : "";
                    return String.CASE_INSENSITIVE_ORDER.compare(pkgA, pkgB);
                });
            }
            if (sCommonUtils.getBoolean("reverse_order", false, context)) {
                Collections.reverse(mData);
            }
        }
        android.util.Log.d("SmartPack", "getData: returning " + mData.size() + " items");
        return mData;
    }

    public static PackageInfo getPackageInfo(String packageName, Context context) {
        try {
            return context.getPackageManager().getPackageInfo(packageName, PackageManager.GET_PERMISSIONS);
        } catch (Exception ignored) {
        }
        return null;
    }

    public static String getFileName(String packageName, Context context) {
        try {
            PackageInfo pi = context.getPackageManager().getPackageInfo(packageName, 0);
            return ExportNameBuilder.getExportName(context, pi);
        } catch (Exception e) {
            return packageName;
        }
    }

    public static File getPackageDir(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    context.getString(R.string.app_name));
        } else {
            return new File(Environment.getExternalStorageDirectory(), "Package_Manager");
        }
    }

    public static void clearAppSettings(String packageID) {
        if (new RootShell().rootAccess()) {
            new RootShell().runCommand("pm clear " + packageID);
        } else {
            new ShizukuShell().runCommand("pm clear " + packageID);
        }
    }

    public static long getBundleSize(String path) {
        long size = 0;
        for (String mSplit : SplitAPKInstaller.splitApks(path)) {
            size += new File(path, mSplit).length();
        }
        return size;
    }

    public static List<PackageItems> getRawData() {
        return mRawData;
    }

    public static List<PackageItems> getRemovedPackagesData() {
        return mRemovedData;
    }

    public static List<PackageItems> getRemovedPackagesData(Context context) {
        if (mRemovedData == null || !mIsUninstalledScanned) {
            generateData(null, context, true);
        }
        return mRemovedData;
    }

    public static void generateData(ProgressBar progressBar, Context context) {
        generateData(progressBar, context, mIsUninstalledScanned);
    }

    public static synchronized void generateData(ProgressBar progressBar, Context context, boolean includeUninstalled) {
        android.util.Log.d("SmartPack", "generateData: start package scanning, includeUninstalled=" + includeUninstalled);
        List<PackageItems> rawDataTemp = new ArrayList<>();
        List<PackageItems> removedDataTemp = new ArrayList<>();
        mIsUninstalledScanned = includeUninstalled;
        if (context == null) {
            android.util.Log.e("SmartPack", "generateData: context is null");
            return;
        }
        PackageManager pm = context.getPackageManager();
        if (pm == null) {
            android.util.Log.e("SmartPack", "generateData: PackageManager is null");
            return;
        }

        List<PackageInfo> packages = new java.util.ArrayList<>();
        int flags = includeUninstalled ? PackageManager.MATCH_UNINSTALLED_PACKAGES : 0;
        try {
            List<PackageInfo> installed = pm.getInstalledPackages(flags);
            if (installed != null) {
                packages.addAll(installed);
            }
            android.util.Log.d("SmartPack", "generateData: getInstalledPackages (" + flags + ") returned " + packages.size() + " apps");
        } catch (Exception e) {
            android.util.Log.e("SmartPack", "generateData: failed getInstalledPackages (" + flags + ")", e);
            try {
                List<PackageInfo> installed = pm.getInstalledPackages(0);
                if (installed != null) {
                    packages.addAll(installed);
                }
                android.util.Log.d("SmartPack", "generateData: getInstalledPackages (0) returned " + packages.size() + " apps");
            } catch (Exception ex) {
                android.util.Log.e("SmartPack", "generateData: failed getInstalledPackages (0)", ex);
            }
        }

        final int totalSize = packages.size();
        final android.os.Handler handler = progressBar != null ? new android.os.Handler(android.os.Looper.getMainLooper()) : null;

        if (progressBar != null && handler != null) {
            handler.post(() -> {
                if (progressBar.isIndeterminate()) {
                    progressBar.setIndeterminate(false);
                }
                progressBar.setMax(totalSize);
            });
        }

        for (int i = 0; i < totalSize; i++) {
            PackageInfo pi = packages.get(i);
            if (pi == null || pi.applicationInfo == null) continue;
            try {
                ApplicationInfo ai = pi.applicationInfo;
                boolean disabled = !ai.enabled;
                boolean removed = (ai.flags & ApplicationInfo.FLAG_INSTALLED) == 0;
                
                CharSequence label = ai.nonLocalizedLabel;
                if (label == null) {
                    label = pm.getApplicationLabel(ai);
                }
                
                String appName = label.toString();
                String apkPath = ai.sourceDir != null ? ai.sourceDir : "";
                if (removed) {
                    removedDataTemp.add(new PackageItems(
                            pi.packageName,
                            appName,
                            apkPath,
                            true,
                            pi)
                    );
                } else {
                    rawDataTemp.add(new PackageItems(
                            pi.packageName,
                            appName + (disabled ? " (Disabled)" : ""),
                            apkPath,
                            false,
                            pi)
                    );
                }
            } catch (Exception e) {
                android.util.Log.e("SmartPack", "generateData: error processing package: " + pi.packageName, e);
            }
            if (progressBar != null && handler != null && (i % 10 == 0 || i == totalSize - 1)) {
                final int currentProgress = i + 1;
                handler.post(() -> progressBar.setProgress(currentProgress));
            }
        }
        mRawData = new CopyOnWriteArrayList<>(rawDataTemp);
        mRemovedData = new CopyOnWriteArrayList<>(removedDataTemp);
        android.util.Log.d("SmartPack", "generateData: finished package scanning. mRawData size: " + mRawData.size() + ", mRemovedData size: " + mRemovedData.size());
    }

    public static void setSortingType(int value, Context context) {
        sCommonUtils.saveInt("sort_apps", value, context);
    }

}