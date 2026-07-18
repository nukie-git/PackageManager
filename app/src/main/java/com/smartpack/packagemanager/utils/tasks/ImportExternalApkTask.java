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
                String originalName = srcFile.getName().toLowerCase();
                File metaApk = null;
                try {
                    PackageInfo pi = pm.getPackageArchiveInfo(srcFile.getAbsolutePath(), 0);
                    
                    // If directly parsing failed and it's a bundle (.apks/.apkm), try parsing an internal APK
                    if (pi == null && (originalName.endsWith(".apkm") || originalName.endsWith(".apks") || originalName.endsWith(".zip"))) {
                        try (net.lingala.zip4j.ZipFile zipFile = new net.lingala.zip4j.ZipFile(srcFile)) {
                            List<net.lingala.zip4j.model.FileHeader> headers = zipFile.getFileHeaders();
                            net.lingala.zip4j.model.FileHeader targetHeader = null;
                            for (net.lingala.zip4j.model.FileHeader header : headers) {
                                if (header.getFileName().equalsIgnoreCase("base.apk")) {
                                    targetHeader = header;
                                    break;
                                }
                            }
                            if (targetHeader == null) {
                                for (net.lingala.zip4j.model.FileHeader header : headers) {
                                    if (header.getFileName().toLowerCase().endsWith(".apk")) {
                                        targetHeader = header;
                                        break;
                                    }
                                }
                            }
                            
                            if (targetHeader != null) {
                                String entryName = targetHeader.getFileName();
                                if (!(entryName.contains("../") || entryName.contains("..\\"))) {
                                    metaApk = new File(mActivity.getCacheDir(), "meta_" + System.currentTimeMillis() + ".apk");
                                    zipFile.extractFile(targetHeader, mActivity.getCacheDir().getAbsolutePath(), metaApk.getName());
                                    pi = pm.getPackageArchiveInfo(metaApk.getAbsolutePath(), 0);
                                }
                            }
                        } catch (Exception ignored) {
                        }
                    }

                    String exportName;
                    String extension;
                    if (originalName.endsWith(".apkm")) extension = ".apkm";
                    else if (originalName.endsWith(".apks")) extension = ".apks";
                    else extension = ".apk";

                    if (pi != null) {
                        if (pi.applicationInfo != null) {
                            // Point to the file containing resources (either the apk itself or the extracted base.apk)
                            File resourceFile = (metaApk != null) ? metaApk : srcFile;
                            pi.applicationInfo.sourceDir = resourceFile.getAbsolutePath();
                            pi.applicationInfo.publicSourceDir = resourceFile.getAbsolutePath();
                        }
                        exportName = ExportNameBuilder.getExportName(mActivity, pi);
                    } else {
                        // Fallback: use original name without extension
                        exportName = srcFile.getName();
                        if (exportName.lastIndexOf('.') > 0) {
                            exportName = exportName.substring(0, exportName.lastIndexOf('.'));
                        }
                    }
                    
                    File finalFile = new File(exportDir, exportName + extension);
                    sFileUtils.copy(srcFile, finalFile);
                    
                    if (finalFile.exists()) {
                        mSuccessCount++;
                        File parent = srcFile.getParentFile();
                        if (!deleteSrc && parent != null && !parent.getAbsolutePath().equals(exportDir.getAbsolutePath())) {
                            srcFile.delete();
                        }
                    }
                    
                } catch (Exception e) {
                    android.util.Log.e("SmartPack", "Failed to import APK: " + source, e);
                } finally {
                    if (metaApk != null && metaApk.exists()) {
                        metaApk.delete();
                    }
                    if (deleteSrc && srcFile != null && srcFile.exists()) {
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