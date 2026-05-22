/*
 * Copyright (C) 2021-2022 sunilpaulmathew <sunil.kde@gmail.com>
 *
 * This file is part of Package Manager, a simple, yet powerful application
 * to manage other application installed on an android device.
 *
 */

package com.smartpack.packagemanager.utils.tasks;

import android.annotation.SuppressLint;
import android.app.Activity;

import com.smartpack.packagemanager.R;
import com.smartpack.packagemanager.dialogs.BatchResultsDialog;
import com.smartpack.packagemanager.dialogs.ProgressDialog;
import com.smartpack.packagemanager.utils.PackageData;
import com.smartpack.packagemanager.utils.RootShell;
import com.smartpack.packagemanager.utils.SerializableItems.BatchOptionsItems;
import com.smartpack.packagemanager.utils.SerializableItems.PackageItems;
import com.smartpack.packagemanager.utils.ShizukuShell;
import com.smartpack.packagemanager.utils.Utils;

import java.util.ArrayList;
import java.util.List;

import in.sunilpaulmathew.sCommon.CommonUtils.sExecutor;
import in.sunilpaulmathew.sCommon.PackageUtils.sPackageUtils;

/*
 * Created by sunilpaulmathew <sunil.kde@gmail.com> on May 22, 2026
 */
public class BatchUninstallTasks extends sExecutor {

    private final Activity mActivity;
    private final List<String> mPackageNames;
    private ProgressDialog mProgressDialog;
    private final List<BatchOptionsItems> mResults = new ArrayList<>();
    private static final RootShell mRootShell = new RootShell();
    private static final ShizukuShell mShizukuShell = new ShizukuShell();

    public BatchUninstallTasks(List<String> packageNames, Activity activity) {
        this.mPackageNames = new ArrayList<>(packageNames);
        this.mActivity = activity;
    }

    @SuppressLint("StringFormatInvalid")
    @Override
    public void onPreExecute() {
        mProgressDialog = new ProgressDialog(mActivity);
        mProgressDialog.setIcon(R.mipmap.ic_launcher);
        mProgressDialog.setTitle(mActivity.getString(R.string.uninstall));
        mProgressDialog.setMessage(mActivity.getString(R.string.preparing_message));
        mProgressDialog.show();
    }

    @SuppressLint("StringFormatInvalid")
    @Override
    public void doInBackground() {
        for (String pkg : mPackageNames) {
            String appName = sPackageUtils.getAppName(pkg, mActivity).toString();

            // Update progress message on UI thread to avoid touching UI from background
            if (mProgressDialog != null) {
                mActivity.runOnUiThread(() -> mProgressDialog.setMessage(mActivity.getString(R.string.uninstall_summary, appName)));
            }

            String cmd = "pm uninstall --user " + Utils.getUserID() + " " + pkg;
            boolean success;
            if (mRootShell.rootAccess()) {
                success = mRootShell.runAndGetOutput(cmd).toLowerCase().contains("success");
            } else {
                success = mShizukuShell.runAndGetOutput(cmd).toLowerCase().contains("success");
            }

            if (success) {
                List<PackageItems> raw = PackageData.getRawData();
                if (raw != null) {
                    synchronized (PackageData.class) {
                        PackageItems packageItems = null;
                        for (PackageItems item : new ArrayList<>(raw)) {
                            if (pkg.equals(item.getPackageName())) {
                                packageItems = item;
                                break;
                            }
                        }
                        if (packageItems != null) {
                            raw.remove(packageItems);
                            List<PackageItems> removed = PackageData.getRemovedPackagesData();
                            if (removed == null) {
                                removed = PackageData.getRemovedPackagesData(mActivity);
                            }
                            if (removed == null) {
                                removed = new java.util.ArrayList<>();
                            }
                            removed.add(new PackageItems(packageItems.getPackageName(), packageItems.getAppName(), packageItems.getSourceDir(), true, (android.content.pm.PackageInfo) null));
                        }
                    }
                }
            } else {
                mResults.add(new BatchOptionsItems(appName, pkg, sPackageUtils.getAppIcon(pkg, mActivity), false, -1));
            }
        }
    }

    @Override
    public void onPostExecute() {
        mProgressDialog.dismiss();
        if (!mResults.isEmpty()) {
            new BatchResultsDialog(mResults, mActivity).show();
        }
        PackageData.generateData(null, mActivity);
    }

}