/*
 * Copyright (C) 2021-2022 sunilpaulmathew <sunil.kde@gmail.com>
 *
 * This file is part of Package Manager, a simple, yet powerful application
 * to manage other application installed on an android device.
 *
 */

package com.smartpack.packagemanager.utils.SerializableItems;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.Serializable;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/*
 * Created by sunilpaulmathew <sunil.kde@gmail.com> on February 10, 2020
 */
public class PackageItems implements Serializable {

    private boolean mSystemApp, mUpdatedSystemApp, mUserApp;
    private transient Drawable mAppIcon;
    private final boolean removed;
    private final String mPackageName, mAppName, mAPKPath;
    private final long mInstalledTime, mUpdatedTime;
    
    private static final ExecutorService iconExecutor = Executors.newFixedThreadPool(Math.max(1, Runtime.getRuntime().availableProcessors()));

    public PackageItems(String packageName, String appName, String apkPath, boolean removed, @Nullable PackageInfo pi) {
        this.mPackageName = packageName;
        this.mAppName = appName;
        this.mAPKPath = apkPath;
        this.removed = removed;
        
        if (pi != null) {
            this.mInstalledTime = pi.firstInstallTime;
            this.mUpdatedTime = pi.lastUpdateTime;
            ApplicationInfo ai = pi.applicationInfo;
            if (ai != null) {
                mSystemApp = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                mUpdatedSystemApp = (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
                mUserApp = !mSystemApp && !mUpdatedSystemApp;
            } else {
                mSystemApp = false;
                mUpdatedSystemApp = false;
                mUserApp = !removed;
            }
        } else {
            this.mInstalledTime = 0;
            this.mUpdatedTime = 0;
            mSystemApp = false;
            mUpdatedSystemApp = false;
            mUserApp = !removed;
        }
    }

    // Deprecated constructor, use the one with PackageInfo for better performance
    public PackageItems(String packageName, String appName, String apkPath, boolean removed, Context context) {
        this.mPackageName = packageName;
        this.mAppName = appName;
        this.mAPKPath = apkPath;
        this.removed = removed;

        long installed = 0, updated = 0;
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo pi = null;
            if (!removed) {
                pi = pm.getPackageInfo(packageName, 0);
            } else if (apkPath != null && !apkPath.isEmpty()) {
                pi = pm.getPackageArchiveInfo(apkPath, 0);
            }
            
            if (pi != null) {
                installed = pi.firstInstallTime;
                updated = pi.lastUpdateTime;
                ApplicationInfo ai = pi.applicationInfo;
                if (ai != null) {
                    mSystemApp = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                    mUpdatedSystemApp = (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
                    mUserApp = !mSystemApp && !mUpdatedSystemApp;
                } else {
                    mSystemApp = false;
                    mUpdatedSystemApp = false;
                    mUserApp = !removed;
                }
            } else {
                mSystemApp = false;
                mUpdatedSystemApp = false;
                mUserApp = !removed;
            }
        } catch (Exception e) {
            mSystemApp = false;
            mUpdatedSystemApp = false;
            mUserApp = !removed;
        }
        this.mInstalledTime = installed;
        this.mUpdatedTime = updated;
    }

    public boolean isRemoved() {
        return removed;
    }

    public boolean isSystemApp() {
        return mSystemApp;
    }

    public boolean isUserApp() {
        return mUserApp;
    }

    public Intent launchIntent(Context context) {
        return context.getPackageManager().getLaunchIntentForPackage(mPackageName);
    }

    public String getSourceDir() {
        return mAPKPath;
    }

    public String getPackageName() {
        return mPackageName;
    }

    public String getAppName() {
        return mAppName;
    }

    public long getAPKSize() {
        if (mAPKPath == null || mAPKPath.isEmpty()) {
            return 0L;
        }
        return new File(mAPKPath).length();
    }

    public long getInstalledTime() {
        return mInstalledTime;
    }

    public long getUpdatedTime() {
        return mUpdatedTime;
    }
    
    @Deprecated
    public long getInstalledTime(Context context) {
        return mInstalledTime;
    }

    @Deprecated
    public long getUpdatedTime(Context context) {
        return mUpdatedTime;
    }

    public void loadAppIcon(ImageView view) {
        if (mAppIcon != null) {
            view.setImageDrawable(mAppIcon);
            return;
        }
        iconExecutor.execute(() -> {
            PackageManager pm = view.getContext().getPackageManager();
            ApplicationInfo ai = null;
            try {
                if (!removed) {
                    ai = pm.getApplicationInfo(mPackageName, 0);
                } else if (mAPKPath != null && !mAPKPath.isEmpty()) {
                    PackageInfo pi = pm.getPackageArchiveInfo(mAPKPath, 0);
                    if (pi != null) {
                        ai = pi.applicationInfo;
                        Objects.requireNonNull(ai).sourceDir = mAPKPath;
                        ai.publicSourceDir = mAPKPath;
                    }
                }

                if (ai != null) {
                    mAppIcon = pm.getApplicationIcon(ai);
                }
            } catch (Exception ignored) {
            }

            new Handler(Looper.getMainLooper()).post(() -> {
                if (mAppIcon != null) {
                    view.setImageDrawable(mAppIcon);
                }
            });
        });
    }

}