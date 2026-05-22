/*
 * Copyright (C) 2021-2022 sunilpaulmathew <sunil.kde@gmail.com>
 *
 * This file is part of Package Manager, a simple, yet powerful application
 * to manage other application installed on an android device.
 *
 */

package com.smartpack.packagemanager.fragments;

import static android.view.View.GONE;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.facebook.shimmer.ShimmerFrameLayout;
import com.smartpack.packagemanager.databinding.FragmentPackagetasksBinding;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.smartpack.packagemanager.BuildConfig;
import com.smartpack.packagemanager.R;
import com.smartpack.packagemanager.adapters.PackageTasksAdapter;
import com.smartpack.packagemanager.dialogs.BatchOptionsDialog;
import com.smartpack.packagemanager.utils.PackageData;
import com.smartpack.packagemanager.utils.RootShell;
import com.smartpack.packagemanager.utils.SerializableItems.BatchOptionsItems;
import com.smartpack.packagemanager.utils.SerializableItems.PackageItems;
import com.smartpack.packagemanager.utils.ShizukuShell;
import com.smartpack.packagemanager.utils.Utils;
import com.smartpack.packagemanager.utils.tasks.ExportAPKTasks;
import com.smartpack.packagemanager.utils.tasks.ExportBundleTasks;
import com.smartpack.packagemanager.utils.tasks.UninstallSystemAppsTasks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.ConcurrentModificationException;

import in.sunilpaulmathew.sCommon.CommonUtils.sCommonUtils;
import in.sunilpaulmathew.sCommon.CommonUtils.sExecutor;
import in.sunilpaulmathew.sCommon.PackageUtils.sPackageUtils;

/*
 * Created by sunilpaulmathew <sunil.kde@gmail.com> on February 10, 2020
 */
public class PackageTasksFragment extends Fragment {

    private List<PackageItems> mData;
    private final List<String> mBatchList = new ArrayList<>();
    private MaterialAutoCompleteTextView mSearchWord;
    private MaterialButton mBatchOptions, mSort;
    private ProgressBar mProgress;
    private RecyclerView mRecyclerView;
    private PackageTasksAdapter mRecycleViewAdapter;
    private RootShell mRootShell;
    private ShizukuShell mShizukuShell;
    private String mPackageNameRemoved = null, mSearchText = null;
    private View mRootView;
    private boolean mIsFirstLoad = true;
    private FragmentPackagetasksBinding binding;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentPackagetasksBinding.inflate(inflater, container, false);
        mRootView = binding.getRoot();

        mProgress = binding.progress;
        mBatchOptions = binding.batch;
        mRecyclerView = mRootView.findViewById(R.id.recycler_view);
        mSearchWord = mRootView.findViewById(R.id.search_word);
        MaterialButton mSearch = binding.searchIcon;
        TabLayout mTabLayout = binding.tabLayout;
        mSort = binding.sortIcon;
        MaterialButton mReload = binding.reloadIcon;
        FloatingActionButton mFAB = requireActivity().findViewById(R.id.fab);

        mFAB.setVisibility(View.VISIBLE);

        mSearchWord.setHintTextColor(Color.GRAY);

        mRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mRecyclerView.addItemDecoration(new DividerItemDecoration(requireActivity(), DividerItemDecoration.VERTICAL));

        mRootShell = new RootShell();
        mShizukuShell = new ShizukuShell();

        new Thread(() -> {
            boolean hasRoot = mRootShell.rootAccess();
            if (!hasRoot && mShizukuShell.isSupported() && sCommonUtils.getBoolean("request_shizuku", true, requireActivity())) {
                requireActivity().runOnUiThread(() -> {
                    if (mShizukuShell.isPermissionDenied()) {
                        new MaterialAlertDialogBuilder(requireActivity())
                                .setCancelable(false)
                                .setIcon(R.mipmap.ic_launcher)
                                .setTitle(getString(R.string.app_name))
                                .setMessage(getString(R.string.shizuku_integration_message))
                                .setNegativeButton(getString(R.string.never_show), (dialogInterface, i) -> sCommonUtils.saveBoolean(
                                        "request_shizuku", false, requireActivity()))
                                .setPositiveButton(getString(R.string.request), (dialogInterface, i) -> mShizukuShell.requestPermission()
                                ).show();
                    } else {
                        // Activate Shizuku on app launch for supported and enabled devices;
                        mShizukuShell.ensureUserService();
                    }
                });
            }
        }).start();

        loadUI(mSearchText, requireActivity());

        mTabLayout.addTab(mTabLayout.newTab().setText(getString(R.string.show_apps_all)));
        mTabLayout.addTab(mTabLayout.newTab().setText(getString(R.string.show_apps_system)));
        mTabLayout.addTab(mTabLayout.newTab().setText(getString(R.string.show_apps_user)));

        switch (sCommonUtils.getString("appTypes", "all", requireActivity())) {
            case "system":
                Objects.requireNonNull(mTabLayout.getTabAt(1)).select();
                break;
            case "user":
                Objects.requireNonNull(mTabLayout.getTabAt(2)).select();
                break;
            default:
                Objects.requireNonNull(mTabLayout.getTabAt(0)).select();
                break;
        }

        mTabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                String mStatus = sCommonUtils.getString("appTypes", "all", requireActivity());
                switch (tab.getPosition()) {
                    case 0:
                        if (!mStatus.equals("all")) {
                            sCommonUtils.saveString("appTypes", "all", requireActivity());
                            loadUI(mSearchText, requireActivity());
                        }
                        break;
                    case 1:
                        if (!mStatus.equals("system")) {
                            sCommonUtils.saveString("appTypes", "system", requireActivity());
                            loadUI(mSearchText, requireActivity());
                        }
                        break;
                    case 2:
                        if (!mStatus.equals("user")) {
                            sCommonUtils.saveString("appTypes", "user", requireActivity());
                            loadUI(mSearchText, requireActivity());
                        }
                        break;
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        mSearch.setOnClickListener(v -> {
            if (mSearchWord.getVisibility() == View.VISIBLE) {
                mSearchWord.setVisibility(View.GONE);
                Utils.toggleKeyboard(0, mSearchWord, requireActivity());
            } else {
                mSearchWord.setVisibility(View.VISIBLE);
                Utils.toggleKeyboard(1, mSearchWord, requireActivity());
            }
        });

        mSearchWord.setOnEditorActionListener((v, actionId, event) -> {
            Utils.toggleKeyboard(0, mSearchWord, requireActivity());
            mSearchWord.clearFocus();
            return true;
        });

        mSearchWord.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                loadUI(s.toString().trim(), requireActivity());
            }
        });

        mSort.setOnClickListener(v -> {
            PopupMenu popupMenu = new PopupMenu(requireActivity(), mSort);
            Menu menu = popupMenu.getMenu();
            if (mData != null && !mData.isEmpty()) {
                menu.add(Menu.NONE, 0, Menu.NONE, getString(R.string.name)).setCheckable(true)
                        .setChecked(PackageData.getSortingType(requireActivity()) == 0);
                menu.add(Menu.NONE, 1, Menu.NONE, getString(R.string.package_id)).setCheckable(true)
                        .setChecked(PackageData.getSortingType(requireActivity()) == 1);
                menu.add(Menu.NONE, 2, Menu.NONE, getString(R.string.time_installed)).setCheckable(true)
                        .setChecked(PackageData.getSortingType(requireActivity()) == 2);
                menu.add(Menu.NONE, 3, Menu.NONE, getString(R.string.time_updated)).setCheckable(true)
                        .setChecked(PackageData.getSortingType(requireActivity()) == 3);
                menu.add(Menu.NONE, 4, Menu.NONE, getString(R.string.size)).setCheckable(true)
                        .setChecked(PackageData.getSortingType(requireActivity()) == 4);
                menu.add(Menu.NONE, 5, Menu.NONE, getString(R.string.reverse_order)).setCheckable(true)
                        .setChecked(sCommonUtils.getBoolean("reverse_order", false, requireActivity()));
            }
            popupMenu.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 5) {
                    sCommonUtils.saveBoolean("reverse_order", !sCommonUtils.getBoolean("reverse_order", false, requireActivity()), requireActivity());
                } else {
                    PackageData.setSortingType(item.getItemId(), requireActivity());
                }
                loadUI(mSearchText, requireActivity());
                return false;
            });
            popupMenu.show();
        });

        mReload.setOnClickListener(v -> {
            PackageData.generateData(null, requireActivity());
            loadUI(mSearchText, requireActivity());
        });

        mBatchOptions.setOnClickListener(v -> {
             PopupMenu popupMenu = new PopupMenu(requireActivity(), mBatchOptions);
             Menu menu = popupMenu.getMenu();
             
             menu.add(Menu.NONE, 0, Menu.NONE, getString(R.string.export_selected));
             menu.add(Menu.NONE, 1, Menu.NONE, getString(R.string.uninstall_selected_question));
             menu.add(Menu.NONE, 2, Menu.NONE, getString(R.string.select_all));
             menu.add(Menu.NONE, 3, Menu.NONE, getString(R.string.batch_list_clear));
             
             popupMenu.setOnMenuItemClickListener(item -> {
                 switch (item.getItemId()) {
                     case 0:
                         handleBatchExport(requireActivity());
                         break;
                     case 1:
                         handleBatchUninstall(requireActivity());
                         break;
                     case 2:
                         mBatchList.clear();
                         for (PackageItems mPackage : mData) {
                             mBatchList.add(mPackage.getPackageName());
                         }
                         loadUI(mSearchText, requireActivity());
                         break;
                     case 3:
                         mBatchList.clear();
                         loadUI(mSearchText, requireActivity());
                         break;
                 }
                 return true;
             });
             popupMenu.show();
        });

        requireActivity().getOnBackPressedDispatcher().addCallback(new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (mProgress.getVisibility() == View.VISIBLE) {
                    return;
                }
                if (mSearchWord.getVisibility() == View.VISIBLE) {
                    if (mSearchText != null) {
                        mSearchText = null;
                        mSearchWord.setText(null);
                    }
                    mSearchWord.setVisibility(View.GONE);
                    return;
                }

                requireActivity().finish();
            }
        });

        return mRootView;
    }

    private void handleBatchExport(Activity activity) {
        new BatchOptionsDialog(getString(R.string.export_selected), getString(R.string.export), mBatchList, activity) {
            @Override
            public void apply(List<BatchOptionsItems> data) {
                List<String> selected = new ArrayList<>();
                for (BatchOptionsItems item : data) {
                    if (item.isChecked()) selected.add(item.getPackageName());
                }
                if (selected.isEmpty()) return;
                
                new com.smartpack.packagemanager.utils.tasks.BatchExportTasks(selected, activity).execute();

                mBatchList.clear();
                loadUI(mSearchText, activity);
            }
        };
    }

    private void handleBatchUninstall(Activity activity) {
        new MaterialAlertDialogBuilder(activity)
                .setIcon(R.mipmap.ic_launcher)
                .setTitle(activity.getString(R.string.uninstall_selected_question))
                .setMessage(activity.getString(R.string.uninstall_warning))
                .setCancelable(false)
                .setNegativeButton(getString(R.string.cancel), (dialogInterface, i) -> {
                })
                .setPositiveButton(getString(R.string.yes), (dialogInterface, i) -> {
                    new BatchOptionsDialog(getString(R.string.uninstall_selected_question), getString(R.string.uninstall), mBatchList, activity) {
                        @Override
                        public void apply(List<BatchOptionsItems> data) {
                            List<String> selected = new ArrayList<>();
                            for (BatchOptionsItems item : data) {
                                if (item.isChecked()) selected.add(item.getPackageName());
                            }
                            if (selected.isEmpty()) return;

                            if (mRootShell.rootAccess() || mShizukuShell.isReady()) {
                                new com.smartpack.packagemanager.utils.tasks.BatchUninstallTasks(selected, activity).execute();
                                mBatchList.clear();
                                loadUI(mSearchText, activity);
                            } else {
                                // Non-root batch uninstall (system-by-system)
                                handleUninstallEvent();
                            }
                        }
                    };
                })
                .show();
    }

    private void handleUninstallEvent() {
        if (!mBatchList.isEmpty()) {
            Intent intent = new Intent(Intent.ACTION_DELETE);
            intent.setData(Uri.parse("package:" + mBatchList.get(0)));
            uninstallApps.launch(intent);
        } else {
            loadUI(mSearchText, requireActivity());
        }
    }

    private void uninstallUserApp(String packageName) {
        Intent intent = new Intent(Intent.ACTION_DELETE);
        intent.setData(Uri.parse("package:" + packageName));
        mPackageNameRemoved = packageName;
        diableOrUninstall.launch(intent);
    }

    private void loadUI(String searchTxt, Activity activity) {
        new sExecutor() {
            ShimmerFrameLayout shimmer = null;

            @Override
            public void onPreExecute() {
                if (mRootView != null) {
                    shimmer = mRootView.findViewById(R.id.shimmer_view_container);
                }
                
                android.util.Log.d("SmartPack", "loadUI.onPreExecute: mIsFirstLoad=" + mIsFirstLoad + ", shimmer=" + (shimmer != null));

                if (mIsFirstLoad && shimmer != null) {
                    shimmer.setVisibility(View.VISIBLE);
                    shimmer.startShimmer();
                    mRecyclerView.setVisibility(View.GONE);
                } else {
                    mProgress.setVisibility(View.VISIBLE);
                    mRecyclerView.setVisibility(View.GONE);
                }
            }

            @Override
            public void doInBackground() {
                long start = System.currentTimeMillis();
                if (mIsFirstLoad) {
                    android.util.Log.d("SmartPack", "loadUI: First load, check initialization");
                    PackageData.init(activity);
                }
                mData = PackageData.getData(searchTxt, activity);
                long end = System.currentTimeMillis();
                android.util.Log.d("SmartPack", "loadUI.doInBackground: retrieved " + (mData != null ? mData.size() : 0) + " items in " + (end - start) + "ms");
                
                if (mData == null || mData.isEmpty()) {
                    android.util.Log.d("SmartPack", "loadUI: data empty, regenerating");
                    PackageData.generateData(null, activity);
                    mData = PackageData.getData(searchTxt, activity);
                }
                mRecycleViewAdapter = new PackageTasksAdapter(mData, searchTxt, mBatchList, mBatchOptions, diableOrUninstall, activity);
            }

            @SuppressLint("StringFormatInvalid")
            @Override
            public void onPostExecute() {
                android.util.Log.d("SmartPack", "loadUI.onPostExecute: isAdded=" + isAdded());
                if (!isAdded()) {
                    return;
                }
                mSearchText = searchTxt;
                mSearchWord.setHint(getString(R.string.search_market_message, mRecycleViewAdapter.getItemCount() + " " + getString(R.string.applications)));
                mBatchOptions.setVisibility(!mBatchList.isEmpty() ? View.VISIBLE : GONE);
                mBatchOptions.setText(activity.getString(R.string.batch_options, mBatchList.size()));
                mRecyclerView.setAdapter(mRecycleViewAdapter);

                if (shimmer != null) {
                    shimmer.stopShimmer();
                    shimmer.setVisibility(View.GONE);
                    if (mIsFirstLoad) {
                        android.util.Log.d("SmartPack", "loadUI.onPostExecute: first load finished, shimmer stopped and hidden");
                        mIsFirstLoad = false;
                    }
                } else if (mIsFirstLoad) {
                    mIsFirstLoad = false;
                }
                
                mProgress.setVisibility(View.GONE);
                mRecyclerView.setVisibility(View.VISIBLE);
            }
        }.execute();
    }

    private sExecutor removeItem(String packageName) {
        return new sExecutor() {
            private int position;

            @Override
            public void onPreExecute() {
                mProgress.setVisibility(View.VISIBLE);
            }

            @Override
            public void doInBackground() {
                for (int i=0; i<mData.size(); i++) {
                    if (mData.get(i).getPackageName().equals(packageName)) {
                        PackageData.getRawData().remove(mData.get(i));
                        mData.remove(i);
                        position = i;
                        return;
                    }
                }
            }

            @SuppressLint("StringFormatInvalid")
            @Override
            public void onPostExecute() {
                if (mPackageNameRemoved != null) {
                    mPackageNameRemoved = null;
                }
                mRecycleViewAdapter.notifyItemRemoved(position);
                mRecycleViewAdapter.notifyItemRangeChanged(position, mRecycleViewAdapter.getItemCount());
                mProgress.setVisibility(GONE);
            }
        };
    }

    @SuppressLint("StringFormatInvalid")
    private final ActivityResultLauncher<Intent> diableOrUninstall = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Intent data = result.getData();
                    String packageName = data != null ? data.getStringExtra("packageName") : null;
                    String packageNameDisabled = data != null ? data.getStringExtra("packageNameDisabled") : null;
                    if (packageName != null) {
                        if (sPackageUtils.isPackageInstalled(packageName, requireActivity())) {
                            uninstallUserApp(packageName);
                        } else {
                            removeItem(packageName).execute();
                        }
                    } else if (packageNameDisabled != null) {
                        new sExecutor() {
                            private int position;

                            @Override
                            public void onPreExecute() {
                                mProgress.setVisibility(View.VISIBLE);
                            }

                            @Override
                            public void doInBackground() {
                                for (int i=0; i<mData.size(); i++) {
                                    if (mData.get(i).getPackageName().equals(packageNameDisabled)) {
                                        PackageItems itemOld = mData.get(i);
                                        PackageItems itemNew = new PackageItems(
                                                itemOld.getPackageName(),
                                                sPackageUtils.getAppName(itemOld.getPackageName(), requireActivity()).toString() + (sPackageUtils.isEnabled(itemOld.getPackageName(), requireActivity()) ? "" : " (Disabled)"),
                                                itemOld.getSourceDir(),
                                                itemOld.isRemoved(),
                                                (android.content.pm.PackageInfo) null
                                        );
                                        int index = PackageData.getRawData().indexOf(itemOld);
                                        if (index != -1) {
                                            PackageData.getRawData().set(index, itemNew);
                                        }
                                        mData.set(i, itemNew);
                                        position = i;
                                        return;
                                    }
                                }
                            }

                            @SuppressLint("StringFormatInvalid")
                            @Override
                            public void onPostExecute() {
                                mRecycleViewAdapter.notifyItemChanged(position);
                                mProgress.setVisibility(GONE);
                            }
                        }.execute();
                    } else if (mPackageNameRemoved != null) {
                        removeItem(mPackageNameRemoved).execute();
                    }
                }
            }
    );

    @SuppressLint("StringFormatInvalid")
    private final ActivityResultLauncher<Intent> uninstallApps = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    // If uninstallation succeed
                    try {
                        for (PackageItems item : PackageData.getRawData()) {
                            if (item.getPackageName().equals(mBatchList.get(0))) {
                                PackageData.getRawData().remove(item);
                                mBatchList.remove(0);

                                int index = mData.indexOf(item);
                                if (index != -1) {
                                    mData.remove(index);
                                    mRecycleViewAdapter.notifyItemRangeChanged(0, mRecycleViewAdapter.getItemCount());
                                }
                            }
                        }
                    } catch (ConcurrentModificationException ignored) {}
                    handleUninstallEvent();
                } else {
                    sCommonUtils.toast(getString(R.string.uninstall_status_failed, sPackageUtils.getAppName(mBatchList.get(0), requireActivity())), requireActivity()).show();
                    mBatchList.remove(0);
                    mRecycleViewAdapter.notifyItemRangeChanged(0, mRecycleViewAdapter.getItemCount());
                    handleUninstallEvent();
                }
            }
    );

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mSearchText != null) {
            mSearchWord.setText(null);
        }
        if (mBatchList != null) {
            mBatchList.clear();
        }
        if (mRootShell != null && mRootShell.rootAccess()) mRootShell.closeSU();
    }

}