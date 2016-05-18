package com.cyngn.keyguard;

import android.animation.ObjectAnimator;
import android.app.SearchManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.os.PowerManager;
import android.provider.Settings.Secure;
import android.provider.Settings.System;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import com.android.internal.telephony.IccCardConstants.State;
import com.android.internal.util.cm.LockscreenTargetUtils;
import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.multiwaveview.GlowPadView;
import com.android.internal.widget.multiwaveview.GlowPadView.OnTriggerListener;
import com.android.internal.widget.multiwaveview.TargetDrawable;
import com.cyngn.keyguard.KeyguardMessageArea.Helper;
import java.net.URISyntaxException;
import java.util.ArrayList;

public class KeyguardSelectorViewMod extends LinearLayout implements KeyguardSecurityView {
    private static final String ASSIST_ICON_METADATA_NAME = "com.android.systemui.action_assist_icon";
    private static final boolean DEBUG = KeyguardHostView.DEBUG;
    private static final String TAG = "SecuritySelectorView";
    private final KeyguardActivityLauncher mActivityLauncher;
    private ObjectAnimator mAnim;
    private Drawable mBouncerFrame;
    private KeyguardSecurityCallback mCallback;
    private boolean mCameraDisabled;
    private GestureDetector mDoubleTapGesture;
    private View mFadeView;
    private GlowPadView mGlowPadView;
    private boolean mIsBouncing;
    private boolean mIsScreenLarge;
    private LockPatternUtils mLockPatternUtils;
    OnTriggerListener mOnTriggerListener;
    private boolean mSearchDisabled;
    private SecurityMessageDisplay mSecurityMessageDisplay;
    private String[] mStoredTargets;
    private int mTargetOffset;
    KeyguardUpdateMonitorCallback mUpdateCallback;

    public KeyguardSelectorViewMod(Context context) {
        this(context, null);
    }

    public KeyguardSelectorViewMod(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.mOnTriggerListener = new OnTriggerListener() {
            public void onTrigger(View v, int target) {
                if (KeyguardSelectorViewMod.this.mStoredTargets == null) {
                    switch (KeyguardSelectorViewMod.this.mGlowPadView.getResourceIdForTarget(target)) {
                        case R.drawable.ic_action_assist_generic /*2130837505*/:
                            Intent assistIntent = ((SearchManager) KeyguardSelectorViewMod.this.mContext.getSystemService("search")).getAssistIntent(KeyguardSelectorViewMod.this.mContext, true, -2);
                            if (assistIntent != null) {
                                KeyguardSelectorViewMod.this.mActivityLauncher.launchActivity(assistIntent, KeyguardSelectorViewMod.DEBUG, true, null, null);
                            } else {
                                Log.w(KeyguardSelectorViewMod.TAG, "Failed to get intent for assist activity");
                            }
                            KeyguardSelectorViewMod.this.mCallback.userActivity(0);
                            return;
                        case R.drawable.ic_lockscreen_camera /*2130837521*/:
                            KeyguardSelectorViewMod.this.mActivityLauncher.launchCamera(null, null);
                            KeyguardSelectorViewMod.this.mCallback.userActivity(0);
                            return;
                        case R.drawable.ic_lockscreen_unlock /*2130837589*/:
                        case R.drawable.ic_lockscreen_unlock_phantom /*2130837592*/:
                            KeyguardSelectorViewMod.this.mCallback.userActivity(0);
                            KeyguardSelectorViewMod.this.mCallback.dismiss(KeyguardSelectorViewMod.DEBUG);
                            return;
                        default:
                            return;
                    }
                } else if (target == KeyguardSelectorViewMod.this.mTargetOffset) {
                    KeyguardSelectorViewMod.this.mCallback.dismiss(KeyguardSelectorViewMod.DEBUG);
                } else {
                    int realTarget = (target - KeyguardSelectorViewMod.this.mTargetOffset) - 1;
                    String targetUri = realTarget < KeyguardSelectorViewMod.this.mStoredTargets.length ? KeyguardSelectorViewMod.this.mStoredTargets[realTarget] : null;
                    if ("empty".equals(targetUri)) {
                        KeyguardSelectorViewMod.this.mCallback.dismiss(KeyguardSelectorViewMod.DEBUG);
                        return;
                    }
                    try {
                        KeyguardSelectorViewMod.this.mActivityLauncher.launchActivity(Intent.parseUri(targetUri, 0), KeyguardSelectorViewMod.DEBUG, true, null, null);
                    } catch (URISyntaxException e) {
                        Log.w(KeyguardSelectorViewMod.TAG, "Invalid lockscreen target " + targetUri);
                    }
                }
            }

            public void onReleased(View v, int handle) {
                if (!KeyguardSelectorViewMod.this.mIsBouncing) {
                    KeyguardSelectorViewMod.this.doTransition(KeyguardSelectorViewMod.this.mFadeView, 1.0f);
                }
            }

            public void onGrabbed(View v, int handle) {
                KeyguardSelectorViewMod.this.mCallback.userActivity(0);
                KeyguardSelectorViewMod.this.doTransition(KeyguardSelectorViewMod.this.mFadeView, 0.0f);
            }

            public void onGrabbedStateChange(View v, int handle) {
            }

            public void onFinishFinalAnimation() {
            }
        };
        this.mUpdateCallback = new KeyguardUpdateMonitorCallback() {
            public void onDevicePolicyManagerStateChanged() {
                KeyguardSelectorViewMod.this.updateTargets();
            }

            public void onSimStateChanged(State simState) {
                KeyguardSelectorViewMod.this.updateTargets();
            }
        };
        this.mActivityLauncher = new KeyguardActivityLauncher() {
            KeyguardSecurityCallback getCallback() {
                return KeyguardSelectorViewMod.this.mCallback;
            }

            LockPatternUtils getLockPatternUtils() {
                return KeyguardSelectorViewMod.this.mLockPatternUtils;
            }

            protected void dismissKeyguardOnNextActivity() {
                getCallback().dismiss(KeyguardSelectorViewMod.DEBUG);
            }

            Context getContext() {
                return KeyguardSelectorViewMod.this.mContext;
            }
        };
        this.mLockPatternUtils = new LockPatternUtils(getContext());
        this.mTargetOffset = LockscreenTargetUtils.getTargetOffset(context);
    }

    protected void onFinishInflate() {
        super.onFinishInflate();
        updateTargets();
        this.mSecurityMessageDisplay = new Helper(this);
        this.mDoubleTapGesture = new GestureDetector(this.mContext, new SimpleOnGestureListener() {
            public boolean onDoubleTap(MotionEvent e) {
                PowerManager pm = (PowerManager) KeyguardSelectorViewMod.this.mContext.getSystemService("power");
                if (pm != null) {
                    pm.goToSleep(e.getEventTime());
                }
                return true;
            }
        });
        KeyguardSmartCoverView kscv = (KeyguardSmartCoverView) findViewById(R.id.keyguard_cover_layout_mod);
        if (kscv != null) {
            kscv.setBackgroundColor(0);
            kscv.setEnableBatterySuperscript(true);
            kscv.setEnableFullDay(true);
            kscv.setAlpha(1.0f);
            kscv.setVisibility(0);
        }
    }

    public void setCarrierArea(View carrierArea) {
        this.mFadeView = carrierArea;
    }

    public boolean isTargetPresent(int resId) {
        return DEBUG;
    }

    public void showUsabilityHint() {
    }

    private void updateTargets() {
        int currentUserHandle = this.mLockPatternUtils.getCurrentUser();
        DevicePolicyManager dpm = this.mLockPatternUtils.getDevicePolicyManager();
        boolean secureCameraDisabled = (!this.mLockPatternUtils.isSecure() || (dpm.getKeyguardDisabledFeatures(null, currentUserHandle) & 2) == 0) ? DEBUG : true;
        boolean cameraDisabledByAdmin = (dpm.getCameraDisabled(null, currentUserHandle) || secureCameraDisabled) ? true : DEBUG;
        boolean disabledBySimState = KeyguardUpdateMonitor.getInstance(getContext()).isSimLocked();
        boolean cameraTargetPresent = this.mContext.getPackageManager().hasSystemFeature("android.hardware.camera");
        boolean searchTargetPresent = isTargetPresent(R.drawable.ic_action_assist_generic);
        if (cameraDisabledByAdmin) {
            Log.v(TAG, "Camera disabled by Device Policy");
        } else if (disabledBySimState) {
            Log.v(TAG, "Camera disabled by Sim State");
        }
        boolean currentUserSetup = Secure.getIntForUser(this.mContext.getContentResolver(), "user_setup_complete", 0, currentUserHandle) != 0 ? true : DEBUG;
        boolean searchActionAvailable = ((SearchManager) this.mContext.getSystemService("search")).getAssistIntent(this.mContext, DEBUG, -2) != null ? true : DEBUG;
        boolean z = (cameraDisabledByAdmin || disabledBySimState || !cameraTargetPresent || !currentUserSetup) ? true : DEBUG;
        this.mCameraDisabled = z;
        z = (!disabledBySimState && searchActionAvailable && searchTargetPresent && currentUserSetup) ? DEBUG : true;
        this.mSearchDisabled = z;
        updateResources();
    }

    public void updateResources() {
        String storedTargets = System.getStringForUser(this.mContext.getContentResolver(), "lockscreen_targets", -2);
        if (storedTargets != null) {
            int i;
            this.mStoredTargets = storedTargets.split("\\|");
            ArrayList<TargetDrawable> storedDrawables = new ArrayList();
            Resources res = getResources();
            Drawable activeBack = new InsetDrawable(res.getDrawable(R.drawable.ic_lockscreen_target_activated), 0, 0, 0, 0);
            Drawable blankInActiveDrawable = res.getDrawable(R.drawable.ic_lockscreen_lock_pressed);
            Drawable unlockActiveDrawable = res.getDrawable(R.drawable.ic_lockscreen_unlock_activated);
            for (i = 0; i < this.mTargetOffset; i++) {
                storedDrawables.add(new TargetDrawable(res, null));
            }
            storedDrawables.add(new TargetDrawable(res, res.getDrawable(R.drawable.ic_lockscreen_unlock)));
            for (i = 0; i < (8 - this.mTargetOffset) - 1; i++) {
                if (i >= this.mStoredTargets.length) {
                    storedDrawables.add(new TargetDrawable(res, 0));
                } else {
                    String uri = this.mStoredTargets[i];
                    if (uri.equals("empty")) {
                        storedDrawables.add(new TargetDrawable(res, LockscreenTargetUtils.getLayeredDrawable(this.mContext, unlockActiveDrawable, blankInActiveDrawable, LockscreenTargetUtils.getInsetForIconType(this.mContext, null), true)));
                    } else {
                        try {
                            Intent intent = Intent.parseUri(uri, 0);
                            Drawable front = null;
                            Drawable back = activeBack;
                            boolean frontBlank = DEBUG;
                            String type = null;
                            if (intent.hasExtra("icon_file")) {
                                type = "icon_file";
                                front = LockscreenTargetUtils.getDrawableFromFile(this.mContext, intent.getStringExtra("icon_file"));
                            } else {
                                if (intent.hasExtra("icon_resource")) {
                                    String source = intent.getStringExtra("icon_resource");
                                    String packageName = intent.getStringExtra("icon_package");
                                    if (source != null) {
                                        front = LockscreenTargetUtils.getDrawableFromResources(this.mContext, packageName, source, DEBUG);
                                        back = LockscreenTargetUtils.getDrawableFromResources(this.mContext, packageName, source, true);
                                        type = "icon_resource";
                                        frontBlank = true;
                                    }
                                }
                            }
                            if (front == null) {
                                front = LockscreenTargetUtils.getDrawableFromIntent(this.mContext, intent);
                            }
                            if (back == null) {
                                back = activeBack;
                            }
                            TargetDrawable targetDrawable = new TargetDrawable(res, LockscreenTargetUtils.getLayeredDrawable(this.mContext, back, front, LockscreenTargetUtils.getInsetForIconType(this.mContext, type), frontBlank));
                            ComponentName compName = intent.getComponent();
                            String className = compName == null ? null : compName.getClassName();
                            if (TextUtils.equals(className, "com.android.camera.CameraLauncher")) {
                                targetDrawable.setEnabled(!this.mCameraDisabled ? true : DEBUG);
                            } else if (TextUtils.equals(className, "SearchActivity")) {
                                targetDrawable.setEnabled(!this.mSearchDisabled ? true : DEBUG);
                            }
                            storedDrawables.add(targetDrawable);
                        } catch (URISyntaxException e) {
                            Log.w(TAG, "Invalid target uri " + uri);
                            storedDrawables.add(new TargetDrawable(res, 0));
                        }
                    }
                }
            }
        } else if (!this.mSearchDisabled) {
            ((SearchManager) this.mContext.getSystemService("search")).getAssistIntent(this.mContext, DEBUG, -2);
        }
    }

    void doTransition(View view, float to) {
        if (this.mAnim != null) {
            this.mAnim.cancel();
        }
        this.mAnim = ObjectAnimator.ofFloat(view, "alpha", new float[]{to});
        this.mAnim.start();
    }

    public void setKeyguardCallback(KeyguardSecurityCallback callback) {
        this.mCallback = callback;
    }

    public void setLockPatternUtils(LockPatternUtils utils) {
        this.mLockPatternUtils = utils;
    }

    public void reset() {
        this.mGlowPadView.reset(DEBUG);
    }

    public boolean needsInput() {
        return DEBUG;
    }

    public void onPause() {
        KeyguardUpdateMonitor.getInstance(getContext()).removeCallback(this.mUpdateCallback);
    }

    public void onResume(int reason) {
        KeyguardUpdateMonitor.getInstance(getContext()).registerCallback(this.mUpdateCallback);
    }

    public KeyguardSecurityCallback getCallback() {
        return this.mCallback;
    }

    public void showBouncer(int duration) {
        this.mIsBouncing = true;
    }

    public void hideBouncer(int duration) {
        this.mIsBouncing = DEBUG;
    }
}
