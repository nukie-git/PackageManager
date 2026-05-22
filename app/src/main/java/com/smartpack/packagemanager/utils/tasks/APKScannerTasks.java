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
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.smartpack.packagemanager.R;
import com.smartpack.packagemanager.dialogs.BatchOptionsDialog;
import com.smartpack.packagemanager.dialogs.ProgressDialog;
import com.smartpack.packagemanager.utils.PackageData;
import com.smartpack.packagemanager.utils.SerializableItems.BatchOptionsItems;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import in.sunilpaulmathew.sCommon.CommonUtils.sExecutor;

/*
 * Created by sunilpaulmathew on May 22, 2026
 */
public class APKScannerTasks extends sExecutor {

    private final Activity mActivity;
    private ProgressDialog mProgressDialog;
    private final List<String> mFoundPaths = new ArrayList<>();
    private final Set<String> mScannedPaths = new HashSet<>();

    public APKScannerTasks(Activity activity) {
        this.mActivity = activity;
    }

    @SuppressLint("StringFormatInvalid")
    @Override
    public void onPreExecute() {
        mProgressDialog = new ProgressDialog(mActivity);
        mProgressDialog.setIcon(R.mipmap.ic_launcher);
        mProgressDialog.setTitle(R.string.exploring);
        mProgressDialog.setMessage(mActivity.getString(R.string.preparing_message));
        mProgressDialog.show();
    }

    @Override
    public void doInBackground() {
        File exportDir = PackageData.getPackageDir(mActivity);
        
        // 1. Scan using MediaStore (fast)
        scanWithMediaStore(exportDir);
        
        // 2. Scan External Storage Root
        File root = Environment.getExternalStorageDirectory();
        if (root.exists()) {
            scanRecursive(root, exportDir, 0);
        }
    }

    private void scanWithMediaStore(File exportDir) {
        ContentResolver cr = mActivity.getContentResolver();
        Uri uri = MediaStore.Files.getContentUri("external");
        String[] projection = {MediaStore.Files.FileColumns.DATA};
        String selection = MediaStore.Files.FileColumns.DATA + " LIKE '%.apk' OR " + 
                          MediaStore.Files.FileColumns.DATA + " LIKE '%.apkm'";
        
        try (Cursor cursor = cr.query(uri, projection, selection, null, null)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    @SuppressLint("Range") String path = cursor.getString(cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA));
                    addPathIfValid(path, exportDir);
                }
            }
        } catch (Exception e) {
            android.util.Log.e("SmartPack", "MediaStore scan failed", e);
        }
    }

    private void scanRecursive(File dir, File exportDir, int depth) {
        // Limit depth and avoid scanning system folders
        if (depth > 10) return;
        if (dir.getAbsolutePath().equals(exportDir.getAbsolutePath())) return;
        
        String dirName = dir.getName();
        if (dirName.startsWith(".") || dirName.equalsIgnoreCase("Android")) return;
        
        File[] files = dir.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            if (file.isDirectory()) {
                scanRecursive(file, exportDir, depth + 1);
            } else {
                String path = file.getAbsolutePath();
                if (path.toLowerCase().endsWith(".apk") || path.toLowerCase().endsWith(".apkm")) {
                    addPathIfValid(path, exportDir);
                }
            }
        }
    }

    private void addPathIfValid(String path, File exportDir) {
        if (path == null) return;
        File file = new File(path);
        if (file.exists() && file.getParentFile() != null && !file.getParentFile().equals(exportDir)) {
            synchronized (mFoundPaths) {
                if (!mScannedPaths.contains(path)) {
                    mFoundPaths.add(path);
                    mScannedPaths.add(path);
                    mActivity.runOnUiThread(() -> mProgressDialog.setMessage("Found: " + file.getName()));
                }
            }
        }
    }

    @Override
    public void onPostExecute() {
        mProgressDialog.dismiss();
        if (mFoundPaths.isEmpty()) {
            new MaterialAlertDialogBuilder(mActivity)
                    .setIcon(R.mipmap.ic_launcher)
                    .setTitle(R.string.apk_finder)
                    .setMessage("No app installers found outside the export folder.")
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }

        new BatchOptionsDialog(mActivity.getString(R.string.apk_finder_desc), mActivity.getString(R.string.apk_finder_move), mFoundPaths, mActivity, true) {
            @Override
            public void apply(List<BatchOptionsItems> data) {
                List<Object> selectedPaths = new ArrayList<>();
                for (BatchOptionsItems item : data) {
                    if (item.isChecked()) {
                        selectedPaths.add(item.getPackageName());
                    }
                }
                if (!selectedPaths.isEmpty()) {
                    new ImportExternalApkTask(selectedPaths, mActivity).execute();
                }
            }
        };
    }

}