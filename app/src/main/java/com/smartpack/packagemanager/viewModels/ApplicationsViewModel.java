/*
 * Copyright (C) 2021-2022 sunilpaulmathew <sunil.kde@gmail.com>
 *
 * This file is part of Package Manager, a simple, yet powerful application
 * to manage other application installed on an android device.
 *
 */

package com.smartpack.packagemanager.viewModels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

/**
 * ViewModel to communicate user interface events across fragments and activities,
 * including search queries, data reloads, and sort order changes.
 */
public class ApplicationsViewModel extends ViewModel {

    private final MutableLiveData<Boolean> searchTrigger = new MutableLiveData<>(false);
    private final MutableLiveData<Void> reloadTrigger = new MutableLiveData<>();
    private final MutableLiveData<Void> sortTrigger = new MutableLiveData<>();

    /**
     * @return LiveData observing whether search mode is enabled/disabled
     */
    public LiveData<Boolean> getSearchTrigger() {
        return searchTrigger;
    }

    /**
     * @return LiveData observing data reload events
     */
    public LiveData<Void> getReloadTrigger() {
        return reloadTrigger;
    }

    /**
     * @return LiveData observing sort order change events
     */
    public LiveData<Void> getSortTrigger() {
        return sortTrigger;
    }

    /**
     * Emits a trigger to enable or disable search input.
     *
     * @param enable True to show/enable search, false otherwise
     */
    public void triggerSearch(boolean enable) {
        searchTrigger.setValue(enable);
    }

    /**
     * Emits a trigger to reload the package list off the main thread.
     */
    public void triggerReload() {
        reloadTrigger.setValue(null);
    }

    /**
     * Emits a trigger to re-sort the current dataset.
     */
    public void triggerSort() {
        sortTrigger.setValue(null);
    }

}