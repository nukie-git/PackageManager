/*
 * Copyright (C) 2021-2022 sunilpaulmathew <sunil.kde@gmail.com>
 *
 * This file is part of Package Manager, a simple, yet powerful application
 * to manage other application installed on an android device.
 *
 */

package com.smartpack.packagemanager.dialogs;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.core.widget.ContentLoadingProgressBar;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.smartpack.packagemanager.R;

/*
 * Created by sunilpaulmathew <sunil.kde@gmail.com> on May 15, 2025
 */
public class ProgressDialog {

    private AlertDialog mAlertDialog = null;
    private final ContentLoadingProgressBar mProgressBar;
    private final MaterialAlertDialogBuilder mDialogBuilder;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    public ProgressDialog(Context context) {
        LayoutInflater layoutInflater = LayoutInflater.from(context);
        View progressLayout = layoutInflater.inflate(R.layout.progress_view_layout, null);
        mProgressBar = progressLayout.findViewById(R.id.progress);
        mDialogBuilder = new MaterialAlertDialogBuilder(context)
                .setView(progressLayout)
                .setCancelable(false);
    }

    public boolean isShowing() {
        return mAlertDialog != null && mAlertDialog.isShowing();
    }

    public int getProgress() {
        return mProgressBar.getProgress();
    }

    public void show() {
        mHandler.post(() -> {
            if (mAlertDialog == null) {
                mAlertDialog = mDialogBuilder.create();
            }
            mAlertDialog.show();
        });
    }

    public void dismiss() {
        mHandler.post(() -> {
            if (mAlertDialog != null) {
                mAlertDialog.dismiss();
            }
        });
    }

    public void setIcon(int resourceID) {
        mDialogBuilder.setIcon(resourceID);
    }

    public void setIcon(Drawable icon) {
        mDialogBuilder.setIcon(icon);
    }

    public void setMessage(int resourceID) {
        mDialogBuilder.setMessage(resourceID);
        mHandler.post(() -> {
            if (mAlertDialog != null) {
                mAlertDialog.setMessage(mAlertDialog.getContext().getString(resourceID));
            }
        });
    }

    public void setMessage(CharSequence charSequence) {
        mDialogBuilder.setMessage(charSequence);
        mHandler.post(() -> {
            if (mAlertDialog != null) {
                mAlertDialog.setMessage(charSequence);
            }
        });
    }

    public void setTitle(int resourceID) {
        mDialogBuilder.setTitle(resourceID);
        mHandler.post(() -> {
            if (mAlertDialog != null) {
                mAlertDialog.setTitle(mAlertDialog.getContext().getString(resourceID));
            }
        });
    }

    public void setTitle(CharSequence charSequence) {
        mDialogBuilder.setTitle(charSequence);
        mHandler.post(() -> {
            if (mAlertDialog != null) {
                mAlertDialog.setTitle(charSequence);
            }
        });
    }

    public void setIndeterminate(boolean b) {
        mHandler.post(() -> mProgressBar.setIndeterminate(b));
    }

    public void setMax(int max) {
        mHandler.post(() -> {
            mProgressBar.setIndeterminate(false);
            mProgressBar.setMax(max);
        });
    }

    public void updateProgress(int progress) {
        mHandler.post(() -> {
            if (mProgressBar.getProgress() < mProgressBar.getMax()) {
                mProgressBar.setProgress(mProgressBar.getProgress() + progress);
            } else {
                mProgressBar.setProgress(0);
                mProgressBar.setIndeterminate(true);
            }
        });
    }

}