package com.cyngn.keyguard;

import android.app.PendingIntent;
import android.graphics.Bitmap;
import android.os.SystemClock;
import com.android.internal.telephony.IccCardConstants.State;

class KeyguardUpdateMonitorCallback {
    private static final long VISIBILITY_CHANGED_COLLAPSE_MS = 1000;
    private boolean mShowing;
    private long mVisibilityChangedCalled;

    KeyguardUpdateMonitorCallback() {
    }

    void onRefreshBatteryInfo(BatteryStatus status) {
    }

    void onTimeChanged() {
    }

    void onRefreshCarrierInfo(CharSequence plmn, CharSequence spn) {
    }

    void onRingerModeChanged(int state) {
    }

    void onPhoneStateChanged(int phoneState) {
    }

    void onKeyguardVisibilityChanged(boolean showing) {
    }

    void onKeyguardVisibilityChangedRaw(boolean showing) {
        long now = SystemClock.elapsedRealtime();
        if (showing != this.mShowing || now - this.mVisibilityChangedCalled >= VISIBILITY_CHANGED_COLLAPSE_MS) {
            onKeyguardVisibilityChanged(showing);
            this.mVisibilityChangedCalled = now;
            this.mShowing = showing;
        }
    }

    void onClockVisibilityChanged() {
    }

    void onDeviceProvisioned() {
    }

    void onDevicePolicyManagerStateChanged() {
    }

    void onUserSwitching(int userId) {
    }

    void onUserSwitchComplete(int userId) {
    }

    void onSimStateChanged(State simState) {
    }

    void onUserRemoved(int userId) {
    }

    void onUserInfoChanged(int userId) {
    }

    void onBootCompleted() {
    }

    void onMusicClientIdChanged(int clientGeneration, boolean clearing, PendingIntent intent) {
    }

    public void onMusicPlaybackStateChanged(int playbackState, long eventTime) {
    }

    void onEmergencyCallAction() {
    }

    public void onSetBackground(Bitmap bitmap) {
    }

    public void onScreenTurnedOn() {
    }

    public void onScreenTurnedOff(int why) {
    }

    void onSimStateChanged(State simState, int subscription) {
    }

    void onRefreshCarrierInfo(CharSequence plmn, CharSequence spn, int subscription) {
    }

    public void onLidStateChanged(int state) {
    }

    void onApplicationWidgetUpdated(String packageName, byte[] icon) {
    }
}
