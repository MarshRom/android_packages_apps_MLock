package com.cyngn.keyguard;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

public class CheckLongPressHelper {
    private float mDownX;
    private float mDownY;
    private boolean mHasPerformedLongPress;
    private int mLongPressTimeout = ViewConfiguration.getLongPressTimeout();
    private CheckForLongPress mPendingCheckForLongPress;
    private int mScaledTouchSlop;
    private View mView;

    class CheckForLongPress implements Runnable {
        CheckForLongPress() {
        }

        public void run() {
            if (CheckLongPressHelper.this.mView.getParent() != null && CheckLongPressHelper.this.mView.hasWindowFocus() && !CheckLongPressHelper.this.mHasPerformedLongPress && CheckLongPressHelper.this.mView.performLongClick()) {
                CheckLongPressHelper.this.mView.setPressed(false);
                CheckLongPressHelper.this.mHasPerformedLongPress = true;
            }
        }
    }

    public CheckLongPressHelper(View v) {
        this.mScaledTouchSlop = ViewConfiguration.get(v.getContext()).getScaledTouchSlop();
        this.mView = v;
    }

    public void postCheckForLongPress(MotionEvent ev) {
        this.mDownX = ev.getX();
        this.mDownY = ev.getY();
        this.mHasPerformedLongPress = false;
        if (this.mPendingCheckForLongPress == null) {
            this.mPendingCheckForLongPress = new CheckForLongPress();
        }
        this.mView.postDelayed(this.mPendingCheckForLongPress, (long) this.mLongPressTimeout);
    }

    public void onMove(MotionEvent ev) {
        boolean xMoved;
        float x = ev.getX();
        float y = ev.getY();
        if (Math.abs(this.mDownX - x) > ((float) this.mScaledTouchSlop)) {
            xMoved = true;
        } else {
            xMoved = false;
        }
        boolean yMoved;
        if (Math.abs(this.mDownY - y) > ((float) this.mScaledTouchSlop)) {
            yMoved = true;
        } else {
            yMoved = false;
        }
        if (xMoved || yMoved) {
            cancelLongPress();
        }
    }

    public void cancelLongPress() {
        this.mHasPerformedLongPress = false;
        if (this.mPendingCheckForLongPress != null) {
            this.mView.removeCallbacks(this.mPendingCheckForLongPress);
            this.mPendingCheckForLongPress = null;
        }
    }

    public boolean hasPerformedLongPress() {
        return this.mHasPerformedLongPress;
    }
}
