package com.cyngn.keyguard;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.Debug;
import android.os.IBinder;
import android.util.Log;
import android.view.MotionEvent;
import com.android.internal.policy.IKeyguardExitCallback;
import com.android.internal.policy.IKeyguardService.Stub;
import com.android.internal.policy.IKeyguardShowCallback;
import com.android.internal.widget.LockPatternUtils;
import java.io.FileDescriptor;
import java.io.PrintWriter;

public class KeyguardService extends Service {
    static final String PERMISSION = "android.permission.CONTROL_KEYGUARD";
    static final String TAG = "KeyguardService";
    private final Stub mBinder = new Stub() {
        public boolean isShowing() {
            return KeyguardService.this.mKeyguardViewMediator.isShowing();
        }

        public boolean isSecure() {
            return KeyguardService.this.mKeyguardViewMediator.isSecure();
        }

        public boolean isShowingAndNotHidden() {
            return KeyguardService.this.mKeyguardViewMediator.isShowingAndNotHidden();
        }

        public boolean isInputRestricted() {
            return KeyguardService.this.mKeyguardViewMediator.isInputRestricted();
        }

        public void verifyUnlock(IKeyguardExitCallback callback) {
            KeyguardService.this.mKeyguardViewMediator.verifyUnlock(callback);
        }

        public void keyguardDone(boolean authenticated, boolean wakeup) {
            KeyguardService.this.checkPermission();
            KeyguardService.this.mKeyguardViewMediator.keyguardDone(authenticated, wakeup);
        }

        public void setHidden(boolean isHidden) {
            KeyguardService.this.checkPermission();
            KeyguardService.this.mKeyguardViewMediator.setHidden(isHidden);
        }

        public void dismiss() {
            KeyguardService.this.mKeyguardViewMediator.dismiss();
        }

        public void onDreamingStarted() {
            KeyguardService.this.checkPermission();
            KeyguardService.this.mKeyguardViewMediator.onDreamingStarted();
        }

        public void onDreamingStopped() {
            KeyguardService.this.checkPermission();
            KeyguardService.this.mKeyguardViewMediator.onDreamingStopped();
        }

        public void onScreenTurnedOff(int reason) {
            KeyguardService.this.checkPermission();
            KeyguardService.this.mKeyguardViewMediator.onScreenTurnedOff(reason);
        }

        public void onScreenTurnedOn(IKeyguardShowCallback callback) {
            KeyguardService.this.checkPermission();
            KeyguardService.this.mKeyguardViewMediator.onScreenTurnedOn(callback);
        }

        public void setKeyguardEnabled(boolean enabled) {
            KeyguardService.this.checkPermission();
            KeyguardService.this.mKeyguardViewMediator.setKeyguardEnabled(enabled);
        }

        public boolean isDismissable() {
            return KeyguardService.this.mKeyguardViewMediator.isDismissable();
        }

        public void onSystemReady() {
            KeyguardService.this.checkPermission();
            KeyguardService.this.mKeyguardViewMediator.onSystemReady();
        }

        public void doKeyguardTimeout(Bundle options) {
            KeyguardService.this.checkPermission();
            KeyguardService.this.mKeyguardViewMediator.doKeyguardTimeout(options);
        }

        public void setCurrentUser(int userId) {
            KeyguardService.this.checkPermission();
            KeyguardService.this.mKeyguardViewMediator.setCurrentUser(userId);
        }

        public void showAssistant() {
            KeyguardService.this.checkPermission();
            KeyguardService.this.mKeyguardViewMediator.showAssistant();
        }

        public void dispatchCameraEvent(MotionEvent event) {
            KeyguardService.this.checkPermission();
            KeyguardService.this.mKeyguardViewMediator.dispatchCameraEvent(event);
        }

        public void dispatchApplicationWidgetEvent(MotionEvent event) {
            KeyguardService.this.checkPermission();
            KeyguardService.this.mKeyguardViewMediator.dispatchApplicationWidgetEvent(event);
        }

        public void launchCamera() {
            KeyguardService.this.checkPermission();
            KeyguardService.this.mKeyguardViewMediator.launchCamera();
        }

        public void launchApplicationWidget() {
            KeyguardService.this.checkPermission();
            KeyguardService.this.mKeyguardViewMediator.launchApplicationWidget();
        }

        public void onBootCompleted() {
            KeyguardService.this.checkPermission();
            KeyguardService.this.mKeyguardViewMediator.onBootCompleted();
        }
    };
    private KeyguardViewMediator mKeyguardViewMediator;

    public void onCreate() {
        if (this.mKeyguardViewMediator == null) {
            this.mKeyguardViewMediator = new KeyguardViewMediator(this, new LockPatternUtils(this));
        }
        Log.v(TAG, "onCreate()");
    }

    public IBinder onBind(Intent intent) {
        return this.mBinder;
    }

    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
    }

    void checkPermission() {
        if (getBaseContext().checkCallingOrSelfPermission(PERMISSION) != 0) {
            Log.w(TAG, "Caller needs permission 'android.permission.CONTROL_KEYGUARD' to call " + Debug.getCaller());
            throw new SecurityException("Access denied to process: " + Binder.getCallingPid() + ", must have permission " + PERMISSION);
        }
    }
}
