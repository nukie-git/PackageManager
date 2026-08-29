/*
 * Copyright (C) 2021-2022 sunilpaulmathew <sunil.kde@gmail.com>
 *
 * This file is part of Package Manager, a simple, yet powerful application
 * to manage other application installed on an android device.
 *
 */

package com.smartpack.packagemanager.utils;

import android.view.MotionEvent;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Touch listener attached to a RecyclerView that detects horizontal swipe gestures
 * when the list cannot scroll further horizontally (e.g. for swipe-to-dismiss).
 */
public abstract class RecyclerViewSwipeListener {

    private final RecyclerView.OnItemTouchListener touchListener;
    private final RecyclerView recyclerView;

    /**
     * Attaches horizontal swipe detection to the given RecyclerView.
     *
     * @param recyclerView The target RecyclerView to monitor for gestures
     */
    public RecyclerViewSwipeListener(@NonNull RecyclerView recyclerView) {
        this.recyclerView = recyclerView;

        this.touchListener = new RecyclerView.SimpleOnItemTouchListener() {
            private float startX = 0f;
            private float startY = 0f;
            private boolean actionTriggered = false;

            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = e.getX();
                        startY = e.getY();
                        actionTriggered = false;
                        break;

                    case MotionEvent.ACTION_MOVE:
                        if (!actionTriggered) {
                            float deltaX = e.getX() - startX;
                            float deltaY = e.getY() - startY;
                            float absX = Math.abs(deltaX);
                            float absY = Math.abs(deltaY);

                            if (absX > ViewConfiguration.get(recyclerView.getContext()).getScaledTouchSlop() * 2 && absX > absY) {
                                int direction = (deltaX > 0) ? -1 : 1;

                                if (!rv.canScrollHorizontally(direction)) {
                                    actionTriggered = true;
                                    if (deltaX > 0) {
                                        onSwipeLeft();
                                    } else {
                                        onSwipeRight();
                                    }
                                }
                            }
                        }
                        break;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        actionTriggered = false;
                        break;
                }
                return false;
            }
        };

        this.recyclerView.addOnItemTouchListener(touchListener);
    }

    /**
     * Unregisters the touch listener from the RecyclerView to avoid memory leaks.
     */
    public void detach() {
        if (recyclerView != null && touchListener != null) {
            recyclerView.removeOnItemTouchListener(touchListener);
        }
    }

    /**
     * Invoked when a swipe-left (left-to-right gesture) is detected.
     */
    public abstract void onSwipeLeft();

    /**
     * Invoked when a swipe-right (right-to-left gesture) is detected.
     */
    public abstract void onSwipeRight();

}