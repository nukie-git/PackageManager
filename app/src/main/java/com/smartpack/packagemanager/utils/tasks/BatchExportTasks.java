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
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.smartpack.packagemanager.R;
import com.smartpack.packagemanager.dialogs.ProgressDialog;
import com.smartpack.packagemanager.utils.ExportNameBuilder;
import com.smartpack.packagemanager.utils.FileUtils;
import com.smartpack.packagemanager.utils.PackageData;
import com.smartpack.packagemanager.utils.SplitAPKInstaller;
import com.smartpack.packagemanager.utils.ZipFileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import in.sunilpaulmathew.sCommon.CommonUtils.sExecutor;
import in.sunilpaulmathew.sCommon.PackageUtils.sPackageUtils;

/*
 * Created by sunilpaulmathew <sunil.kde@gmail.com> on May 22, 2026
 */
public class BatchExportTasks extends sExecutor {

    private final Activity mActivity;
    private final List<String> mPackageNames;
    private ProgressDialog mProgressDialog;

    public BatchExportTasks(List<String> packageNames, Activity activity) {
        this.mPackageNames = new ArrayList<>(packageNames);
        this.mActivity = activity;
    }

    @SuppressLint("StringFormatInvalid")
    @Override
    public void onPreExecute() {
        mProgressDialog = new ProgressDialog(mActivity);
        mProgressDialog.setIcon(R.mipmap.ic_launcher);
        mProgressDialog.setTitle(mActivity.getString(R.string.export_selected));
        mProgressDialog.setMessage(mActivity.getString(R.string.preparing_message));
        mProgressDialog.show();
    }

    @Override
    public void doInBackground() {
        PackageData.makePackageFolder(mActivity);
        
        for (String pkg : mPackageNames) {
            String appName = sPackageUtils.getAppName(pkg, mActivity).toString();
            String apkPath = sPackageUtils.getSourceDir(pkg, mActivity);
            
            try {
                PackageInfo pi = mActivity.getPackageManager().getPackageInfo(pkg, 0);
                String exportName = ExportNameBuilder.getExportName(mActivity, pi);
                
                mProgressDialog.setMessage(mActivity.getString(R.string.exporting, appName));

                boolean isBundle = new File(apkPath).getName().equals("base.apk") && 
                                  SplitAPKInstaller.splitApks(sPackageUtils.getParentDir(pkg, mActivity)).size() > 1;

                if (isBundle) {
                    String parentDir = sPackageUtils.getParentDir(pkg, mActivity);
                    List<File> mFiles = new ArrayList<>();
                    for (final String splitApps : SplitAPKInstaller.splitApks(parentDir)) {
                        mFiles.add(new File(parentDir, splitApps));
                    }
                    try (ZipFileUtils zipFileUtils = new ZipFileUtils(PackageData.getPackageDir(mActivity) + "/" + exportName + ".apkm")) {
                        zipFileUtils.setProgress(mProgressDialog);
                        zipFileUtils.zip(mFiles);
                    }
                } else {
                    FileUtils fileUtils = new FileUtils(new File(PackageData.getPackageDir(mActivity), exportName + ".apk"), mProgressDialog);
                    fileUtils.copy(apkPath);
                }
            } catch (IOException | PackageManager.NameNotFoundException ignored) {}
        }
    }

    @SuppressLint("StringFormatInvalid")
    @Override
    public void onPostExecute() {
        mProgressDialog.dismiss();
        new MaterialAlertDialogBuilder(mActivity)
                .setIcon(R.mipmap.ic_launcher)
                .setTitle(R.string.app_name)
                .setMessage(mActivity.getString(R.string.export_success_message, PackageData.getPackageDir(mActivity)))
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

}