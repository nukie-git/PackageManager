/*
 * Copyright (C) 2021-2022 sunilpaulmathew <sunil.kde@gmail.com>
 *
 * This file is part of Package Manager, a simple, yet powerful application
 * to manage other application installed on an android device.
 *
 */

package com.smartpack.packagemanager.activities;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.smartpack.packagemanager.BuildConfig;
import com.smartpack.packagemanager.R;
import com.smartpack.packagemanager.adapters.SettingsAdapter;
import com.smartpack.packagemanager.utils.AppSettings;
import com.smartpack.packagemanager.utils.Billing;
import com.smartpack.packagemanager.utils.ExportNameBuilder;
import com.smartpack.packagemanager.utils.RecyclerViewSwipeListener;
import com.smartpack.packagemanager.utils.SerializableItems.SettingsItems;
import com.smartpack.packagemanager.utils.ThemeHelper;
import com.smartpack.packagemanager.utils.Utils;

import java.util.ArrayList;

import in.sunilpaulmathew.sCommon.CommonUtils.sCommonUtils;
import in.sunilpaulmathew.sCommon.Credits.sCreditsUtils;
import in.sunilpaulmathew.sCommon.Dialog.sSingleChoiceDialog;
import in.sunilpaulmathew.sCommon.ThemeUtils.sThemeUtils;
import in.sunilpaulmathew.sCommon.TranslatorUtils.sTranslatorUtils;

/*
 * Created by sunilpaulmathew <sunil.kde@gmail.com> on February 10, 2020
 * Migrated from SettingsFragment to SettingsActivity (upstream v8.0).
 * Local customizations preserved: ThemeHelper, ExportNameBuilder CUSTOM_TEMPLATE flow.
 */
public class SettingsActivity extends BaseActivity {

    private RecyclerViewSwipeListener mRecyclerViewSwipeListener;
    private SettingsAdapter mRecycleViewAdapter;
    private ArrayList<SettingsItems> mData;

    @SuppressLint({"StringFormatInvalid", "SetTextI18n"})
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentViewWithInsets(R.layout.activity_settings, R.id.layout_root);

        RecyclerView mRecyclerView = findViewById(R.id.recycler_view);

        mData = buildSettingsData();
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mRecycleViewAdapter = new SettingsAdapter(mData);
        mRecyclerView.setAdapter(mRecycleViewAdapter);

        mRecycleViewAdapter.setOnItemClickListener((position, v) -> {
            if (mData.get(position).getUrl() != null) {
                sCommonUtils.launchUrl(mData.get(position).getUrl(), this);
            } else if (position == 0) {
                // App info header — open app system settings
                Intent settings = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                Uri uri = Uri.fromParts("package", BuildConfig.APPLICATION_ID, null);
                settings.setData(uri);
                startActivity(settings);
            } else if (position == 2) {
                AppSettings.setLanguage(this);
            } else if (position == 3) {
                // Theme selection (uses local ThemeHelper for string-based persistence)
                new sSingleChoiceDialog(R.drawable.ic_theme, getString(R.string.app_theme),
                        new String[]{
                                getString(R.string.app_theme_auto),
                                getString(R.string.app_theme_dark),
                                getString(R.string.app_theme_light)
                        }, ThemeHelper.getThemeIndex(this), this) {

                    @Override
                    public void onItemSelected(int selectedPosition) {
                        if (selectedPosition == ThemeHelper.getThemeIndex(SettingsActivity.this)) {
                            return;
                        }
                        String themeVal;
                        switch (selectedPosition) {
                            case 2:
                                themeVal = "light";
                                break;
                            case 1:
                                themeVal = "dark";
                                break;
                            default:
                                themeVal = "auto";
                                break;
                        }
                        sCommonUtils.saveString(AppSettings.PREF_APP_THEME, themeVal, SettingsActivity.this);
                        ThemeHelper.applyTheme(SettingsActivity.this);
                        mData.get(position).setDescription(ThemeHelper.getThemeSummary(SettingsActivity.this));
                        mRecycleViewAdapter.notifyItemChanged(position);
                        recreate();
                    }
                }.show();
            } else if (position == 5) {
                // Exported APK naming — preserves CUSTOM_TEMPLATE flow
                new sSingleChoiceDialog(R.drawable.ic_pencil, getString(R.string.exported_apps_name),
                        AppSettings.getAPKNameOptionsMenu(this), AppSettings.getAPKNameOptionsPosition(this), this) {

                    @Override
                    public void onItemSelected(int itemPosition) {
                        ExportNameBuilder.Mode newMode = ExportNameBuilder.Mode.values()[itemPosition];
                        sCommonUtils.saveString(AppSettings.PREF_EXPORT_MODE, newMode.name(), SettingsActivity.this);
                        switch (newMode) {
                            case CUSTOM_TEMPLATE:
                                showCustomTemplateDialog(position);
                                break;
                            default:
                                mData.get(position).setDescription(AppSettings.getExportedAPKName(SettingsActivity.this));
                                mRecycleViewAdapter.notifyItemChanged(position);
                                break;
                        }
                    }
                }.show();
            } else if (position == 6) {
                new sSingleChoiceDialog(R.drawable.ic_install, getString(R.string.installer_clicking),
                        AppSettings.getInstallerOptionsMenu(this), AppSettings.getInstallerOptionsPosition(this), this) {

                    @Override
                    public void onItemSelected(int itemPosition) {
                        switch (itemPosition) {
                            case 0:
                                if (sCommonUtils.getBoolean("neverShow", false, SettingsActivity.this)) {
                                    sCommonUtils.saveBoolean("neverShow", false, SettingsActivity.this);
                                }
                                break;
                            case 1:
                                if (!sCommonUtils.getBoolean("neverShow", false, SettingsActivity.this)) {
                                    sCommonUtils.saveBoolean("neverShow", true, SettingsActivity.this);
                                }
                                break;
                        }
                        mData.get(position).setDescription(AppSettings.getInstallerStatus(SettingsActivity.this));
                        mRecycleViewAdapter.notifyItemChanged(position);
                    }
                }.show();
            } else if (position == 7) {
                new sSingleChoiceDialog(R.drawable.ic_exit, getString(R.string.exiting_app),
                        AppSettings.getExitOptionsMenu(this), AppSettings.getExitMenuPosition(this), this) {

                    @Override
                    public void onItemSelected(int itemPosition) {
                        switch (itemPosition) {
                            case 0:
                                if (sCommonUtils.getBoolean("exit_confirmation", true, SettingsActivity.this)) {
                                    sCommonUtils.saveBoolean("exit_confirmation", false, SettingsActivity.this);
                                }
                                break;
                            case 1:
                                if (!sCommonUtils.getBoolean("exit_confirmation", true, SettingsActivity.this)) {
                                    sCommonUtils.saveBoolean("exit_confirmation", true, SettingsActivity.this);
                                }
                                break;
                        }
                        mData.get(position).setDescription(AppSettings.getExitingStatus(SettingsActivity.this));
                        mRecycleViewAdapter.notifyItemChanged(position);
                    }
                }.show();
            } else if (position == 12) {
                Billing.showDonateOption(this);
            } else if (position == 15) {
                new sTranslatorUtils(getString(R.string.app_name), "https://poeditor.com/join/project?hash=0CitpyI1Oc", this).show();
            } else if (position == 17) {
                Intent share_app = new Intent();
                share_app.setAction(Intent.ACTION_SEND);
                share_app.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name));
                share_app.putExtra(Intent.EXTRA_TEXT, getString(R.string.share_message, Billing.getAppVersion()) + Billing.getPackageExt());
                share_app.setType("text/plain");
                Intent shareIntent = Intent.createChooser(share_app, getString(R.string.share_with));
                startActivity(shareIntent);
            } else if (position == 19) {
                new sCreditsUtils(AppSettings.getCredits(),
                        sCommonUtils.getDrawable(R.mipmap.ic_launcher, this),
                        sCommonUtils.getDrawable(R.drawable.ic_back, this),
                        Utils.getColor(R.attr.colorPrimary, this),
                        25, getString(R.string.app_name), getString(R.string.copyright_text),
                        Billing.getAppVersion()).launchCredits(this);
            }
        });

        mRecyclerViewSwipeListener = new RecyclerViewSwipeListener(mRecyclerView) {
            @Override
            public void onSwipeLeft() {
                finish();
            }

            @Override
            public void onSwipeRight() {
            }
        };
    }

    @SuppressLint("StringFormatInvalid")
    private ArrayList<SettingsItems> buildSettingsData() {
        ArrayList<SettingsItems> data = new ArrayList<>();
        data.add(new SettingsItems(getString(R.string.version, Billing.getAppVersion()), getString(R.string.copyright, getString(R.string.copyright_text)), sCommonUtils.getDrawable(R.mipmap.ic_launcher_round, this), null, false, 18));

        // User interface
        data.add(new SettingsItems(getString(R.string.user_interface), null, null, null, true, 15));
        data.add(new SettingsItems(getString(R.string.language), AppSettings.getLanguageDescription(this), sCommonUtils.getDrawable(R.drawable.ic_language, this), null, false, 18));
        data.add(new SettingsItems(getString(R.string.app_theme), ThemeHelper.getThemeSummary(this), sCommonUtils.getDrawable(R.drawable.ic_theme, this), null, false, 18));

        // General
        data.add(new SettingsItems(getString(R.string.general), null, null, null, true, 15));
        data.add(new SettingsItems(getString(R.string.exported_apps_name), AppSettings.getExportedAPKName(this), sCommonUtils.getDrawable(R.drawable.ic_pencil, this), null, false, 18));
        data.add(new SettingsItems(getString(R.string.installer_clicking), AppSettings.getInstallerStatus(this), sCommonUtils.getDrawable(R.drawable.ic_install, this), null, false, 18));
        data.add(new SettingsItems(getString(R.string.exiting_app), AppSettings.getExitingStatus(this), sCommonUtils.getDrawable(R.drawable.ic_exit, this), null, false, 18));

        // Other
        data.add(new SettingsItems(getString(R.string.other), null, null, null, true, 15));
        data.add(new SettingsItems(getString(R.string.source_code), getString(R.string.source_code_summary), sCommonUtils.getDrawable(R.drawable.ic_github, this), "https://github.com/SmartPack/PackageManager", false, 18));
        data.add(new SettingsItems(getString(R.string.support), getString(R.string.support_summary), sCommonUtils.getDrawable(R.drawable.ic_support, this), "https://t.me/smartpack_kmanager", false, 18));
        data.add(new SettingsItems(getString(R.string.report_issue), getString(R.string.report_issue_summary), sCommonUtils.getDrawable(R.drawable.ic_issue, this), "https://github.com/SmartPack/PackageManager/issues/new/choose", false, 18));
        data.add(new SettingsItems(getString(R.string.support_development), null, sCommonUtils.getDrawable(R.drawable.ic_donate, this), null, false, 18));
        data.add(new SettingsItems(getString(R.string.more_apps), getString(R.string.more_apps_summary), sCommonUtils.getDrawable(R.drawable.ic_playstore, this), "https://play.google.com/store/apps/dev?id=5836199813143882901", false, 18));
        data.add(new SettingsItems(getString(R.string.documentation), getString(R.string.documentation_summary), sCommonUtils.getDrawable(R.drawable.ic_book, this), "https://smartpack.github.io/PackageManager/general/", false, 18));
        data.add(new SettingsItems(getString(R.string.translations), getString(R.string.translations_summary), sCommonUtils.getDrawable(R.drawable.ic_translate, this), null, false, 18));
        data.add(new SettingsItems(getString(R.string.change_logs), null, sCommonUtils.getDrawable(R.drawable.ic_change_logs, this), "https://smartpack.github.io/PackageManager/changelog/", false, 18));
        data.add(new SettingsItems(getString(R.string.share_app), getString(R.string.share_app_Summary), sCommonUtils.getDrawable(R.drawable.ic_share, this), null, false, 18));
        data.add(new SettingsItems(getString(R.string.rate_us), getString(R.string.rate_us_Summary), sCommonUtils.getDrawable(R.drawable.ic_rate, this), "https://play.google.com/store/apps/details?id=com.smartpack.packagemanager" + Billing.getPackageExt(), false, 18));
        data.add(new SettingsItems(getString(R.string.credits), getString(R.string.credits_summary), sCommonUtils.getDrawable(R.drawable.ic_credits, this), null, false, 18));

        return data;
    }

    /**
     * Shows the CUSTOM_TEMPLATE name dialog — a local customization preserved from SettingsFragment.
     * Allows users to define a filename template with supported tokens:
     *   {appname}, {packageid}, {versionname}, {versioncode}, {date}
     */
    private void showCustomTemplateDialog(final int position) {
        final EditText input = new EditText(this);
        input.setText(sCommonUtils.getString(AppSettings.PREF_EXPORT_TPL, "{appname}-{packageid}-{versionname}", this));

        FrameLayout container = new FrameLayout(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int margin = (int) (24 * getResources().getDisplayMetrics().density);
        params.setMargins(margin, 0, margin, 0);
        input.setLayoutParams(params);
        container.addView(input);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.exported_apps_name)
                .setMessage("Enter custom naming template:\nSupported: {appname}, {packageid}, {versionname}, {versioncode}, {date}")
                .setView(container)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String inputStr = input.getText().toString().trim();
                    String template = inputStr.isEmpty() ? "{appname}-{packageid}-{versionname}" : inputStr;
                    sCommonUtils.saveString(AppSettings.PREF_EXPORT_TPL, template, this);
                    new sSingleChoiceDialog(R.drawable.ic_pencil, "Select Separator",
                            new String[]{"Hyphen (-)", "Underscore (_)", "None (empty)"},
                            getSeparatorIndex(), this) {
                        @Override
                        public void onItemSelected(int sepPosition) {
                            String sep;
                            switch (sepPosition) {
                                case 1:
                                    sep = "_";
                                    break;
                                case 2:
                                    sep = "";
                                    break;
                                default:
                                    sep = "-";
                                    break;
                            }
                            sCommonUtils.saveString(AppSettings.PREF_EXPORT_SEP, sep, SettingsActivity.this);
                            mData.get(position).setDescription(AppSettings.getExportedAPKName(SettingsActivity.this));
                            mRecycleViewAdapter.notifyItemChanged(position);
                        }
                    }.show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private int getSeparatorIndex() {
        String sep = sCommonUtils.getString(AppSettings.PREF_EXPORT_SEP, "-", this);
        switch (sep) {
            case "_":
                return 1;
            case "":
                return 2;
            default:
                return 0;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mRecyclerViewSwipeListener != null) {
            mRecyclerViewSwipeListener.detach();
            mRecyclerViewSwipeListener = null;
        }
    }

}
