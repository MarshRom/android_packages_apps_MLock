package com.cyngn.keyguard;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.IRotationWatcher;
import android.view.IWindowManager;
import android.view.IWindowManager.Stub;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import com.android.internal.widget.LockPatternUtils;
import com.cyngn.keyguard.KeyguardMessageArea.Helper;

public class KeyguardFaceUnlockView extends LinearLayout implements KeyguardSecurityView {
    private static final boolean DEBUG = false;
    private static final String TAG = "FULKeyguardFaceUnlockView";
    private BiometricSensorUnlock mBiometricUnlock;
    private Drawable mBouncerFrame;
    private ImageButton mCancelButton;
    private View mEcaView;
    private View mFaceUnlockAreaView;
    private boolean mIsShowing;
    private final Object mIsShowingLock;
    private KeyguardSecurityCallback mKeyguardSecurityCallback;
    private int mLastRotation;
    private LockPatternUtils mLockPatternUtils;
    private final IRotationWatcher mRotationWatcher;
    private SecurityMessageDisplay mSecurityMessageDisplay;
    KeyguardUpdateMonitorCallback mUpdateCallback;
    private boolean mWatchingRotation;
    private final IWindowManager mWindowManager;

    public KeyguardFaceUnlockView(Context context) {
        this(context, null);
    }

    public KeyguardFaceUnlockView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.mIsShowing = DEBUG;
        this.mIsShowingLock = new Object();
        this.mWindowManager = Stub.asInterface(ServiceManager.getService("window"));
        this.mRotationWatcher = new IRotationWatcher.Stub() {
            public void onRotationChanged(int rotation) {
                if (Math.abs(rotation - KeyguardFaceUnlockView.this.mLastRotation) == 2 && KeyguardFaceUnlockView.this.mBiometricUnlock != null) {
                    KeyguardFaceUnlockView.this.mBiometricUnlock.stop();
                    KeyguardFaceUnlockView.this.maybeStartBiometricUnlock();
                }
                KeyguardFaceUnlockView.this.mLastRotation = rotation;
            }
        };
        this.mUpdateCallback = new KeyguardUpdateMonitorCallback() {
            public void onPhoneStateChanged(int phoneState) {
                if (phoneState == 1 && KeyguardFaceUnlockView.this.mBiometricUnlock != null) {
                    KeyguardFaceUnlockView.this.mBiometricUnlock.stopAndShowBackup();
                }
            }

            public void onUserSwitching(int userId) {
                if (KeyguardFaceUnlockView.this.mBiometricUnlock != null) {
                    KeyguardFaceUnlockView.this.mBiometricUnlock.stop();
                }
            }

            public void onUserSwitchComplete(int userId) {
                if (KeyguardFaceUnlockView.this.mBiometricUnlock != null) {
                    KeyguardFaceUnlockView.this.maybeStartBiometricUnlock();
                }
            }

            public void onKeyguardVisibilityChanged(boolean showing) {
                synchronized (KeyguardFaceUnlockView.this.mIsShowingLock) {
                    boolean wasShowing = KeyguardFaceUnlockView.this.mIsShowing;
                    KeyguardFaceUnlockView.this.mIsShowing = showing;
                }
                PowerManager powerManager = (PowerManager) KeyguardFaceUnlockView.this.mContext.getSystemService("power");
                if (KeyguardFaceUnlockView.this.mBiometricUnlock == null) {
                    return;
                }
                if (!showing && wasShowing) {
                    KeyguardFaceUnlockView.this.mBiometricUnlock.stop();
                } else if (showing && powerManager.isScreenOn() && !wasShowing) {
                    KeyguardFaceUnlockView.this.maybeStartBiometricUnlock();
                }
            }

            public void onEmergencyCallAction() {
                if (KeyguardFaceUnlockView.this.mBiometricUnlock != null) {
                    KeyguardFaceUnlockView.this.mBiometricUnlock.stop();
                }
            }
        };
    }

    protected void onFinishInflate() {
        super.onFinishInflate();
        initializeBiometricUnlockView();
        this.mSecurityMessageDisplay = new Helper(this);
        this.mEcaView = findViewById(R.id.keyguard_selector_fade_container);
        View bouncerFrameView = findViewById(R.id.keyguard_bouncer_frame);
        if (bouncerFrameView != null) {
            this.mBouncerFrame = bouncerFrameView.getBackground();
        }
    }

    public void setKeyguardCallback(KeyguardSecurityCallback callback) {
        this.mKeyguardSecurityCallback = callback;
        ((FaceUnlock) this.mBiometricUnlock).setKeyguardCallback(callback);
    }

    public void setLockPatternUtils(LockPatternUtils utils) {
        this.mLockPatternUtils = utils;
    }

    public void reset() {
    }

    public void onDetachedFromWindow() {
        if (this.mBiometricUnlock != null) {
            this.mBiometricUnlock.stop();
        }
        KeyguardUpdateMonitor.getInstance(this.mContext).removeCallback(this.mUpdateCallback);
        if (this.mWatchingRotation) {
            try {
                this.mWindowManager.removeRotationWatcher(this.mRotationWatcher);
                this.mWatchingRotation = DEBUG;
            } catch (RemoteException e) {
                Log.e(TAG, "Remote exception when removing rotation watcher");
            }
        }
    }

    public void onPause() {
        if (this.mBiometricUnlock != null) {
            this.mBiometricUnlock.stop();
        }
        KeyguardUpdateMonitor.getInstance(this.mContext).removeCallback(this.mUpdateCallback);
        if (this.mWatchingRotation) {
            try {
                this.mWindowManager.removeRotationWatcher(this.mRotationWatcher);
                this.mWatchingRotation = DEBUG;
            } catch (RemoteException e) {
                Log.e(TAG, "Remote exception when removing rotation watcher");
            }
        }
    }

    public void onResume(int reason) {
        this.mIsShowing = KeyguardUpdateMonitor.getInstance(this.mContext).isKeyguardVisible();
        if (!KeyguardUpdateMonitor.getInstance(this.mContext).isSwitchingUser()) {
            maybeStartBiometricUnlock();
        }
        KeyguardUpdateMonitor.getInstance(this.mContext).registerCallback(this.mUpdateCallback);
        if (!this.mWatchingRotation) {
            try {
                this.mLastRotation = this.mWindowManager.watchRotation(this.mRotationWatcher);
                this.mWatchingRotation = true;
            } catch (RemoteException e) {
                Log.e(TAG, "Remote exception when adding rotation watcher");
            }
        }
    }

    public boolean needsInput() {
        return DEBUG;
    }

    public KeyguardSecurityCallback getCallback() {
        return this.mKeyguardSecurityCallback;
    }

    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        this.mBiometricUnlock.initializeView(this.mFaceUnlockAreaView);
    }

    private void initializeBiometricUnlockView() {
        this.mFaceUnlockAreaView = findViewById(R.id.face_unlock_area_view);
        if (this.mFaceUnlockAreaView != null) {
            this.mBiometricUnlock = new FaceUnlock(this.mContext);
            this.mCancelButton = (ImageButton) findViewById(R.id.face_unlock_cancel_button);
            this.mCancelButton.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    KeyguardFaceUnlockView.this.mBiometricUnlock.stopAndShowBackup();
                }
            });
            return;
        }
        Log.w(TAG, "Couldn't find biometric unlock view");
    }

    private void maybeStartBiometricUnlock() {
        if (this.mBiometricUnlock != null) {
            KeyguardUpdateMonitor monitor = KeyguardUpdateMonitor.getInstance(this.mContext);
            boolean backupIsTimedOut = monitor.getFailedUnlockAttempts() >= 5 ? true : DEBUG;
            PowerManager powerManager = (PowerManager) this.mContext.getSystemService("power");
            synchronized (this.mIsShowingLock) {
                boolean isShowing = this.mIsShowing;
            }
            if (!powerManager.isScreenOn() || !isShowing) {
                this.mBiometricUnlock.stop();
            } else if (monitor.getPhoneState() != 0 || !monitor.isAlternateUnlockEnabled() || monitor.getMaxBiometricUnlockAttemptsReached() || backupIsTimedOut) {
                this.mBiometricUnlock.stopAndShowBackup();
            } else {
                this.mBiometricUnlock.start();
            }
        }
    }

    public void showUsabilityHint() {
    }

    public void showBouncer(int duration) {
        KeyguardSecurityViewHelper.showBouncer(this.mSecurityMessageDisplay, this.mEcaView, this.mBouncerFrame, duration);
    }

    public void hideBouncer(int duration) {
        KeyguardSecurityViewHelper.hideBouncer(this.mSecurityMessageDisplay, this.mEcaView, this.mBouncerFrame, duration);
    }
}
