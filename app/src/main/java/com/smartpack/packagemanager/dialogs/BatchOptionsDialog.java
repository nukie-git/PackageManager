/*
 * Copyright (C) 2020-2025 sunilpaulmathew <sunil.kde@gmail.com>
 *
 * This file is part of Package Manager, a simple, yet powerful application
 * to manage other application installed on an android device.
 *
 */

package com.smartpack.packagemanager.dialogs;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textview.MaterialTextView;
import com.smartpack.packagemanager.R;
import com.smartpack.packagemanager.adapters.BatchOptionsAdapter;
import com.smartpack.packagemanager.utils.SerializableItems.BatchOptionsItems;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import in.sunilpaulmathew.sCommon.PackageUtils.sPackageUtils;

/*
 * Created by sunilpaulmathew <sunil.kde@gmail.com> on August 13, 2025
 */
public abstract class BatchOptionsDialog extends MaterialAlertDialogBuilder {

    private List<BatchOptionsItems> mBatchOptionsItems;

    public BatchOptionsDialog(String title, String actionTitle, List<String> items, Activity activity) {
        this(title, actionTitle, items, activity, false);
    }

    public BatchOptionsDialog(String title, String actionTitle, List<String> items, Activity activity, boolean isPaths) {
        super(activity);
        View rootView = View.inflate(activity, R.layout.layout_batch_options, null);
        RecyclerView recyclerView = rootView.findViewById(R.id.recycler_view);
        MaterialTextView titleText = rootView.findViewById(R.id.title);
        recyclerView.addItemDecoration(new DividerItemDecoration(activity, DividerItemDecoration.VERTICAL));
        recyclerView.setLayoutManager(new LinearLayoutManager(activity));
        titleText.setText(title);

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Handler handler = new Handler(Looper.getMainLooper());

            executor.execute(() -> {
                List<BatchOptionsItems> data = getData(items, activity, isPaths);
                BatchOptionsAdapter adapter = new BatchOptionsAdapter(data);

                handler.post(() -> {
                    recyclerView.setAdapter(adapter);
                    recyclerView.setVisibility(View.VISIBLE);
                });
            });
        }

        setView(rootView);
        setNeutralButton(R.string.cancel, (dialog, id) -> {
        });
        setPositiveButton(actionTitle, (dialog, id) ->
                apply(mBatchOptionsItems));
        show();
    }

    private List<BatchOptionsItems> getData(List<String> items, Activity activity, boolean isPaths) {
        mBatchOptionsItems = new ArrayList<>();
        if (!isPaths) {
            for (String packageID : items) {
                if (packageID.contains(".") && sPackageUtils.isPackageInstalled(packageID, activity)) {
                    mBatchOptionsItems.add(new BatchOptionsItems(sPackageUtils.getAppName(packageID, activity), packageID, sPackageUtils.getAppIcon(packageID, activity), true, Integer.MIN_VALUE));
                }
            }
        } else {
            PackageManager pm = activity.getPackageManager();
            for (String path : items) {
                File file = new File(path);
                if (file.exists()) {
                    PackageInfo pi = pm.getPackageArchiveInfo(path, 0);
                    if (pi != null) {
                        if (pi.applicationInfo != null) {
                            pi.applicationInfo.sourceDir = path;
                            pi.applicationInfo.publicSourceDir = path;
                        }
                        CharSequence label = pm.getApplicationLabel(pi.applicationInfo);
                        mBatchOptionsItems.add(new BatchOptionsItems(label, path, pm.getApplicationIcon(pi.applicationInfo), true, Integer.MIN_VALUE));
                    } else {
                        mBatchOptionsItems.add(new BatchOptionsItems(file.getName(), path, activity.getDrawable(R.mipmap.ic_launcher), true, Integer.MIN_VALUE));
                    }
                }
            }
        }
        return mBatchOptionsItems;
    }

    public abstract void apply(List<BatchOptionsItems> data);

}