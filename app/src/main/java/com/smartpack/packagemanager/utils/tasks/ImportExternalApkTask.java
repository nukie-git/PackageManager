/*
 * Copyright (C) 2026 sunilpaulmathew <sunil.kde@gmail.com>
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
import android.net.Uri;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.smartpack.packagemanager.R;
import com.smartpack.packagemanager.dialogs.ProgressDialog;
import com.smartpack.packagemanager.utils.ExportNameBuilder;
import com.smartpack.packagemanager.utils.PackageData;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import in.sunilpaulmathew.sCommon.CommonUtils.sCommonUtils;
import in.sunilpaulmathew.sCommon.CommonUtils.sExecutor;
import in.sunilpaulmathew.sCommon.FileUtils.sFileUtils;

/*
 * Created by sunilpaulmathew on May 22, 2026
 */
public class ImportExternalApkTask extends sExecutor {

    private final Activity mActivity;
    private final List<Object> mSources = new ArrayList<>();
    private ProgressDialog mProgressDialog;
    private int mSuccessCount = 0;

    public ImportExternalApkTask(List<?> sources, Activity activity) {
        this.mSources.addAll(sources);
        this.mActivity = activity;
    }

    @SuppressLint("StringFormatInvalid")
    @Override
    public void onPreExecute() {
        mProgressDialog = new ProgressDialog(mActivity);
        mProgressDialog.setIcon(R.mipmap.ic_launcher);
        mProgressDialog.setTitle(R.string.preparing_message);
        mProgressDialog.show();
    }

    @Override
    public void doInBackground() {
        PackageData.makePackageFolder(mActivity);
        File exportDir = PackageData.getPackageDir(mActivity);
        PackageManager pm = mActivity.getPackageManager();

        for (Object source : mSources) {
            File srcFile = null;
            boolean deleteSrc = false;
            
            if (source instanceof Uri) {
                srcFile = new File(mActivity.getCacheDir(), "import_temp_" + System.currentTimeMillis() + ".apk");
                sFileUtils.copy((Uri) source, srcFile, mActivity);
                deleteSrc = true;
            } else if (source instanceof File) {
                srcFile = (File) source;
            } else if (source instanceof String) {
                srcFile = new File((String) source);
            }

            if (srcFile != null && srcFile.exists()) {
                try {
                    PackageInfo pi = pm.getPackageArchiveInfo(srcFile.getAbsolutePath(), 0);
                    if (pi != null) {
                        if (pi.applicationInfo != null) {
                            pi.applicationInfo.sourceDir = srcFile.getAbsolutePath();
                            pi.applicationInfo.publicSourceDir = srcFile.getAbsolutePath();
                        }
                        
                        String extension = srcFile.getName().endsWith(".apkm") ? ".apkm" : ".apk";
                        String exportName = ExportNameBuilder.getExportName(mActivity, pi);
                        File finalFile = new File(exportDir, exportName + extension);
                        
                        sFileUtils.copy(srcFile, finalFile);
                        if (finalFile.exists()) {
                            mSuccessCount++;
                            // If it was a real file (not from Uri), we should delete the original as requested "move"
                            if (!deleteSrc && !srcFile.getParentFile().equals(exportDir)) {
                                srcFile.delete();
                            }
                        }
                    }
                } catch (Exception e) {
                    android.util.Log.e("SmartPack", "Failed to import APK: " + source, e);
                } finally {
                    if (deleteSrc && srcFile.exists()) {
                        srcFile.delete();
                    }
                }
            }
        }
    }

    @SuppressLint("StringFormatInvalid")
    @Override
    public void onPostExecute() {
        mProgressDialog.dismiss();
        if (mSuccessCount > 0) {
            new MaterialAlertDialogBuilder(mActivity)
                    .setIcon(R.mipmap.ic_launcher)
                    .setTitle(R.string.app_name)
                    .setMessage(mActivity.getString(R.string.export_success_message, PackageData.getPackageDir(mActivity)))
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        } else {
            sCommonUtils.toast("No valid APK files found to import", mActivity).show();
        }
    }

}