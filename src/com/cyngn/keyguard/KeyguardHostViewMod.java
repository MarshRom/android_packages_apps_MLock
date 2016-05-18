package com.cyngn.keyguard;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.PendingIntent;
import android.app.SearchManager;
import android.app.admin.DevicePolicyManager;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.UserInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Looper;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings.Secure;
import android.provider.Settings.System;
import android.support.v7.graphics.Palette;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.util.Slog;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.BaseSavedState;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewStub;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RemoteViews.OnClickHandler;
import com.android.internal.widget.LockPatternUtils;
import com.cyngn.keyguard.ChallengeLayout.OnBouncerStateChangedListener;
import com.cyngn.keyguard.KeyguardDrawerLayout.SimpleDrawerListener;
import com.cyngn.keyguard.KeyguardWidgetPager.Callbacks;
import com.cyngn.keyguard.SlidingChallengeLayout.LayoutParams;
import com.pheelicks.visualizer.AudioData;
import com.pheelicks.visualizer.FFTData;
import com.pheelicks.visualizer.VisualizerView;
import com.pheelicks.visualizer.renderer.Renderer;
import java.io.File;
import java.lang.ref.WeakReference;
import java.util.List;

public class KeyguardHostViewMod extends KeyguardViewBase {
    static final int APPWIDGET_HOST_ID = 1262836039;
    public static final String CUSTOM_LOCKSCREEN_STATE = "com.android.keyguard.custom.STATE";
    public static boolean DEBUG = false;
    public static boolean DEBUGXPORT = false;
    private static final String ENABLE_MENU_KEY_FILE = "/data/local/enable_menu_key";
    public static final String PREFS_FILE = "state";
    public static final String PREF_CAMERA_DRAWER_HINTS = "camera_drawer_hints";
    private static final String TAG = "KeyguardHostViewMod";
    static final int TRANSPORT_GONE = 0;
    static final int TRANSPORT_INVISIBLE = 1;
    static final int TRANSPORT_VISIBLE = 2;
    private static final int VISUALIZER_ANIMATION_DURATION = 300;
    private final int MAX_WIDGETS;
    private final KeyguardActivityLauncher mActivityLauncher;
    private AudioManager mAm;
    private KeyguardWidgetPager mAppWidgetContainer;
    private AppWidgetHost mAppWidgetHost;
    private AppWidgetManager mAppWidgetManager;
    private int mAppWidgetToShow;
    boolean mApplicationWidgetAnimating;
    private FrameLayout mApplicationWidgetContainer;
    private View mApplicationWidgetDrawer;
    private int mApplicationWidgetDrawerMode;
    private final Callbacks mApplicationWidgetFrame;
    private Runnable mApplicationWidgetLaunchTransition;
    private boolean mApplicationWidgetLaunched;
    private View mApplicationWidgetView;
    private float mApplicationWidgetXDown;
    private KeyguardSecurityCallback mCallback;
    private boolean mCameraDisabled;
    private View mCameraDrawer;
    boolean mCameraDrawerAnimating;
    private int mCameraDrawerMode;
    private Runnable mCameraLaunchTransition;
    private boolean mCameraLaunched;
    private final Callbacks mCameraWidgetCallbacks;
    private FrameLayout mCameraWidgetContainer;
    private float mCameraXDown;
    protected int mClientGeneration;
    private SecurityMode mCurrentSecuritySelection;
    private boolean mDefaultAppWidgetAttached;
    private int mDisabledFeatures;
    protected OnDismissAction mDismissAction;
    private boolean mEnableFallback;
    private View mExpandChallengeView;
    protected int mFailedAttempts;
    private final OnLongClickListener mFastUnlockClickListener;
    private ImageView mGradientBackground;
    private final Rect mInsets;
    private boolean mIsBouncing;
    private boolean mIsVerifyUnlockOnly;
    private KeyguardMultiUserSelectorView mKeyguardMultiUserSelectorView;
    private KeyguardSelectorViewMod mKeyguardSelectorViewMod;
    private LockPatternUtils mLockPatternUtils;
    private MultiPaneChallengeLayout mMultiPaneChallengeLayout;
    private KeyguardSecurityCallback mNullCallback;
    private MyOnClickHandler mOnClickHandler;
    private Runnable mPostBootCompletedRunnable;
    private boolean mSafeModeEnabled;
    private boolean mScreenOn;
    private KeyguardSecurityModel mSecurityModel;
    private KeyguardSecurityViewFlipper mSecurityViewContainer;
    protected boolean mShowSecurityWhenReturn;
    private boolean mShowing;
    private SlidingChallengeLayout mSlidingChallengeLayout;
    private KeyguardDrawerLayout mSlidingDrawer;
    private final Runnable mStartVisualizer;
    private final Runnable mStopVisualizer;
    private final Runnable mSwitchPageRunnable;
    private Rect mTempRect;
    private KeyguardTransportControlView mTransportControl;
    private int mTransportState;
    private KeyguardUpdateMonitorCallback mUpdateMonitorCallbacks;
    private final int mUserId;
    private boolean mUserSetupCompleted;
    private KeyguardViewStateManager mViewStateManager;
    private VisualizerView mVisualizer;
    private Callbacks mWidgetCallbacks;

    static /* synthetic */ class AnonymousClass25 {
        static final /* synthetic */ int[] $SwitchMap$com$cyngn$keyguard$KeyguardSecurityModel$SecurityMode = new int[SecurityMode.values().length];

        static {
            try {
                $SwitchMap$com$cyngn$keyguard$KeyguardSecurityModel$SecurityMode[SecurityMode.Pattern.ordinal()] = KeyguardHostViewMod.TRANSPORT_INVISIBLE;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$cyngn$keyguard$KeyguardSecurityModel$SecurityMode[SecurityMode.PIN.ordinal()] = KeyguardHostViewMod.TRANSPORT_VISIBLE;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$com$cyngn$keyguard$KeyguardSecurityModel$SecurityMode[SecurityMode.Password.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                $SwitchMap$com$cyngn$keyguard$KeyguardSecurityModel$SecurityMode[SecurityMode.Account.ordinal()] = 4;
            } catch (NoSuchFieldError e4) {
            }
            try {
                $SwitchMap$com$cyngn$keyguard$KeyguardSecurityModel$SecurityMode[SecurityMode.Biometric.ordinal()] = 5;
            } catch (NoSuchFieldError e5) {
            }
            try {
                $SwitchMap$com$cyngn$keyguard$KeyguardSecurityModel$SecurityMode[SecurityMode.SimPin.ordinal()] = 6;
            } catch (NoSuchFieldError e6) {
            }
            try {
                $SwitchMap$com$cyngn$keyguard$KeyguardSecurityModel$SecurityMode[SecurityMode.SimPuk.ordinal()] = 7;
            } catch (NoSuchFieldError e7) {
            }
            try {
                $SwitchMap$com$cyngn$keyguard$KeyguardSecurityModel$SecurityMode[SecurityMode.None.ordinal()] = 8;
            } catch (NoSuchFieldError e8) {
            }
        }
    }

    private static class LockscreenBarEqRenderer extends Renderer {
        private int mDbFuzz;
        private int mDbFuzzFactor;
        private int mDivisions;
        private Paint mPaint;

        public LockscreenBarEqRenderer(int divisions, Paint paint, int dbfuzz, int dbFactor) {
            if (KeyguardHostViewMod.DEBUG) {
                Log.d(KeyguardHostViewMod.TAG, "Lockscreen EQ Renderer; divisions:" + divisions + ", dbfuzz: " + dbfuzz + "dbFactor: " + dbFactor);
            }
            this.mDivisions = divisions;
            this.mPaint = paint;
            this.mDbFuzz = dbfuzz;
            this.mDbFuzzFactor = dbFactor;
        }

        public void onRender(Canvas canvas, AudioData data, Rect rect) {
        }

        public void onRender(Canvas canvas, FFTData data, Rect rect) {
            for (int i = KeyguardHostViewMod.TRANSPORT_GONE; i < data.bytes.length / this.mDivisions; i += KeyguardHostViewMod.TRANSPORT_INVISIBLE) {
                this.mFFTPoints[i * 4] = (float) ((i * 4) * this.mDivisions);
                this.mFFTPoints[(i * 4) + KeyguardHostViewMod.TRANSPORT_VISIBLE] = (float) ((i * 4) * this.mDivisions);
                byte rfk = data.bytes[this.mDivisions * i];
                byte ifk = data.bytes[(this.mDivisions * i) + KeyguardHostViewMod.TRANSPORT_INVISIBLE];
                int dbValue = (int) (10.0d * Math.log10((double) ((float) ((rfk * rfk) + (ifk * ifk)))));
                this.mFFTPoints[(i * 4) + KeyguardHostViewMod.TRANSPORT_INVISIBLE] = (float) rect.height();
                this.mFFTPoints[(i * 4) + 3] = (float) (rect.height() - ((this.mDbFuzzFactor * dbValue) + this.mDbFuzz));
            }
            canvas.drawLines(this.mFFTPoints, this.mPaint);
        }
    }

    private static class MyOnClickHandler extends OnClickHandler {
        WeakReference<KeyguardHostViewMod> mThis;

        MyOnClickHandler(KeyguardHostViewMod hostView) {
            this.mThis = new WeakReference(hostView);
        }

        public boolean onClickHandler(final View view, final PendingIntent pendingIntent, final Intent fillInIntent) {
            KeyguardHostViewMod hostView = (KeyguardHostViewMod) this.mThis.get();
            if (hostView == null) {
                return false;
            }
            if (!pendingIntent.isActivity()) {
                return super.onClickHandler(view, pendingIntent, fillInIntent);
            }
            hostView.setOnDismissAction(new OnDismissAction() {
                public boolean onDismiss() {
                    try {
                        view.getContext().startIntentSender(pendingIntent.getIntentSender(), fillInIntent, 268435456, 268435456, KeyguardHostViewMod.TRANSPORT_GONE, ActivityOptions.makeScaleUpAnimation(view, KeyguardHostViewMod.TRANSPORT_GONE, KeyguardHostViewMod.TRANSPORT_GONE, view.getMeasuredWidth(), view.getMeasuredHeight()).toBundle());
                    } catch (SendIntentException e) {
                        Log.e(KeyguardHostViewMod.TAG, "Cannot send pending intent: ", e);
                    } catch (Exception e2) {
                        Log.e(KeyguardHostViewMod.TAG, "Cannot send pending intent due to unknown exception: ", e2);
                    }
                    return false;
                }
            });
            if (hostView.mViewStateManager.isChallengeShowing()) {
                hostView.mViewStateManager.showBouncer(true);
            } else {
                hostView.mCallback.dismiss(false);
            }
            return true;
        }
    }

    static class SavedState extends BaseSavedState {
        public static final Creator<SavedState> CREATOR = new Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
        int appWidgetToShow;
        Rect insets;
        int transportState;

        SavedState(Parcelable superState) {
            super(superState);
            this.appWidgetToShow = KeyguardHostViewMod.TRANSPORT_GONE;
            this.insets = new Rect();
        }

        private SavedState(Parcel in) {
            super(in);
            this.appWidgetToShow = KeyguardHostViewMod.TRANSPORT_GONE;
            this.insets = new Rect();
            this.transportState = in.readInt();
            this.appWidgetToShow = in.readInt();
            this.insets = (Rect) in.readParcelable(null);
        }

        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(this.transportState);
            out.writeInt(this.appWidgetToShow);
            out.writeParcelable(this.insets, KeyguardHostViewMod.TRANSPORT_GONE);
        }
    }

    public KeyguardHostViewMod(Context context) {
        this(context, null);
    }

    public KeyguardHostViewMod(Context context, AttributeSet attrs) {
        Context userContext;
        boolean z;
        super(context, attrs);
        this.mShowing = false;
        this.mTransportState = TRANSPORT_GONE;
        this.MAX_WIDGETS = 5;
        this.mCurrentSecuritySelection = SecurityMode.Invalid;
        this.mTempRect = new Rect();
        this.mInsets = new Rect();
        this.mOnClickHandler = new MyOnClickHandler(this);
        this.mCameraDrawerMode = TRANSPORT_GONE;
        this.mApplicationWidgetDrawerMode = TRANSPORT_GONE;
        this.mUpdateMonitorCallbacks = new KeyguardUpdateMonitorCallback() {
            public void onBootCompleted() {
                if (KeyguardHostViewMod.this.mPostBootCompletedRunnable != null) {
                    KeyguardHostViewMod.this.mPostBootCompletedRunnable.run();
                    KeyguardHostViewMod.this.mPostBootCompletedRunnable = null;
                }
            }

            public void onUserSwitchComplete(int userId) {
                if (KeyguardHostViewMod.this.mKeyguardMultiUserSelectorView != null) {
                    KeyguardHostViewMod.this.mKeyguardMultiUserSelectorView.finalizeActiveUserView(true);
                }
            }

            void onMusicClientIdChanged(int clientGeneration, boolean clearing, PendingIntent intent) {
                int newState = KeyguardHostViewMod.TRANSPORT_VISIBLE;
                if (KeyguardHostViewMod.DEBUGXPORT && (KeyguardHostViewMod.this.mClientGeneration != clientGeneration || clearing)) {
                    Log.v(KeyguardHostViewMod.TAG, (clearing ? "hide" : "show") + " transport, gen:" + clientGeneration);
                }
                KeyguardHostViewMod.this.mClientGeneration = clientGeneration;
                if (clearing) {
                    newState = KeyguardHostViewMod.TRANSPORT_GONE;
                } else if (KeyguardHostViewMod.this.mTransportState != KeyguardHostViewMod.TRANSPORT_VISIBLE) {
                    newState = KeyguardHostViewMod.TRANSPORT_INVISIBLE;
                }
                if (newState != KeyguardHostViewMod.this.mTransportState) {
                    KeyguardHostViewMod.this.mTransportState = newState;
                    if (KeyguardHostViewMod.DEBUGXPORT) {
                        Log.v(KeyguardHostViewMod.TAG, "update widget: transport state changed");
                    }
                    KeyguardHostViewMod.this.post(KeyguardHostViewMod.this.mSwitchPageRunnable);
                }
            }

            public void onMusicPlaybackStateChanged(int playbackState, long eventTime) {
                boolean z = true;
                if (KeyguardHostViewMod.DEBUGXPORT) {
                    Log.v(KeyguardHostViewMod.TAG, "music state changed: " + playbackState);
                }
                if (KeyguardHostViewMod.this.mTransportState != 0) {
                    int newState = KeyguardHostViewMod.isMusicPlaying(playbackState) ? KeyguardHostViewMod.TRANSPORT_VISIBLE : KeyguardHostViewMod.TRANSPORT_INVISIBLE;
                    if (newState != KeyguardHostViewMod.this.mTransportState) {
                        KeyguardHostViewMod.this.mTransportState = newState;
                        if (KeyguardHostViewMod.DEBUGXPORT) {
                            Log.v(KeyguardHostViewMod.TAG, "update widget: play state changed");
                        }
                        KeyguardHostViewMod.this.post(KeyguardHostViewMod.this.mSwitchPageRunnable);
                    }
                    KeyguardHostViewMod keyguardHostViewMod = KeyguardHostViewMod.this;
                    if (KeyguardHostViewMod.this.mTransportState == 0) {
                        z = false;
                    }
                    keyguardHostViewMod.requestVisualizer(z, KeyguardHostViewMod.TRANSPORT_GONE);
                    KeyguardHostViewMod.this.updateStatusBarFlags();
                }
            }
        };
        this.mFastUnlockClickListener = new OnLongClickListener() {
            public boolean onLongClick(View v) {
                if (KeyguardHostViewMod.this.mLockPatternUtils.isTactileFeedbackEnabled()) {
                    v.performHapticFeedback(KeyguardHostViewMod.TRANSPORT_GONE, KeyguardHostViewMod.TRANSPORT_VISIBLE);
                }
                KeyguardHostViewMod.this.showNextSecurityScreenOrFinish(false);
                return true;
            }
        };
        this.mWidgetCallbacks = new Callbacks() {
            public void userActivity() {
                KeyguardHostViewMod.this.userActivity();
            }

            public void onUserActivityTimeoutChanged() {
                KeyguardHostViewMod.this.onUserActivityTimeoutChanged();
            }

            public void onAddView(View v) {
                if (!KeyguardHostViewMod.this.shouldEnableAddWidget()) {
                    KeyguardHostViewMod.this.mAppWidgetContainer.setAddWidgetEnabled(false);
                }
            }

            public void onRemoveView(View v, boolean deletePermanently) {
                if (deletePermanently) {
                    int appWidgetId = ((KeyguardWidgetFrame) v).getContentAppWidgetId();
                    if (appWidgetId != 0 && appWidgetId != -2) {
                        KeyguardHostViewMod.this.mAppWidgetHost.deleteAppWidgetId(appWidgetId);
                    }
                }
            }

            public void onRemoveViewAnimationCompleted() {
                if (KeyguardHostViewMod.this.shouldEnableAddWidget()) {
                    KeyguardHostViewMod.this.mAppWidgetContainer.setAddWidgetEnabled(true);
                }
            }
        };
        this.mCallback = new KeyguardSecurityCallback() {
            public void userActivity(long timeout) {
                if (KeyguardHostViewMod.this.mViewMediatorCallback != null) {
                    KeyguardHostViewMod.this.mViewMediatorCallback.userActivity(timeout);
                }
            }

            public void dismiss(boolean authenticated) {
                KeyguardHostViewMod.this.showNextSecurityScreenOrFinish(authenticated);
            }

            public boolean isVerifyUnlockOnly() {
                return KeyguardHostViewMod.this.mIsVerifyUnlockOnly;
            }

            public void reportSuccessfulUnlockAttempt() {
                KeyguardUpdateMonitor.getInstance(KeyguardHostViewMod.this.mContext).clearFailedUnlockAttempts();
                KeyguardHostViewMod.this.mLockPatternUtils.reportSuccessfulPasswordAttempt();
            }

            public void reportFailedUnlockAttempt() {
                if (KeyguardHostViewMod.this.mCurrentSecuritySelection == SecurityMode.Biometric) {
                    KeyguardUpdateMonitor.getInstance(KeyguardHostViewMod.this.mContext).reportFailedBiometricUnlockAttempt();
                } else {
                    KeyguardHostViewMod.this.reportFailedUnlockAttempt();
                }
            }

            public int getFailedAttempts() {
                return KeyguardUpdateMonitor.getInstance(KeyguardHostViewMod.this.mContext).getFailedUnlockAttempts();
            }

            public void showBackupSecurity() {
                KeyguardHostViewMod.this.showBackupSecurityScreen();
            }

            public void setOnDismissAction(OnDismissAction action) {
                KeyguardHostViewMod.this.setOnDismissAction(action);
            }
        };
        this.mNullCallback = new KeyguardSecurityCallback() {
            public void userActivity(long timeout) {
            }

            public void showBackupSecurity() {
            }

            public void setOnDismissAction(OnDismissAction action) {
            }

            public void reportSuccessfulUnlockAttempt() {
            }

            public void reportFailedUnlockAttempt() {
            }

            public boolean isVerifyUnlockOnly() {
                return false;
            }

            public int getFailedAttempts() {
                return KeyguardHostViewMod.TRANSPORT_GONE;
            }

            public void dismiss(boolean securityVerified) {
            }
        };
        this.mCameraWidgetCallbacks = new Callbacks() {
            public void onLaunchingCamera() {
                KeyguardHostViewMod.this.mCameraLaunchTransition = null;
                KeyguardHostViewMod.this.mShowSecurityWhenReturn = true;
            }

            public void onCameraLaunchedSuccessfully() {
                KeyguardHostViewMod.this.mShowSecurityWhenReturn = true;
                reset();
            }

            public void onCameraLaunchedUnsuccessfully() {
                reset();
            }

            public void reset() {
                KeyguardHostViewMod.this.mCameraLaunchTransition = null;
                KeyguardHostViewMod.this.mCameraWidgetContainer.setVisibility(4);
                KeyguardHostViewMod.this.mSlidingDrawer.closeDrawer(5);
            }
        };
        this.mApplicationWidgetFrame = new Callbacks() {
            public void onLaunchingApplicationWidgetContainer() {
                KeyguardHostViewMod.this.mApplicationWidgetLaunchTransition = null;
                KeyguardHostViewMod.this.mShowSecurityWhenReturn = true;
            }

            public void onApplicationWidgetContainerLaunchedSuccessfully() {
                KeyguardHostViewMod.this.mShowSecurityWhenReturn = true;
                reset();
            }

            public void onApplicationWidgetContainerLaunchedUnsuccessfully() {
                reset();
            }

            public void reset() {
                KeyguardHostViewMod.this.mApplicationWidgetLaunchTransition = null;
                KeyguardHostViewMod.this.mApplicationWidgetContainer.setVisibility(4);
                KeyguardHostViewMod.this.mSlidingDrawer.closeDrawer(3);
            }
        };
        this.mActivityLauncher = new KeyguardActivityLauncher() {
            Context getContext() {
                return KeyguardHostViewMod.this.mContext;
            }

            KeyguardSecurityCallback getCallback() {
                return KeyguardHostViewMod.this.mCallback;
            }

            LockPatternUtils getLockPatternUtils() {
                return KeyguardHostViewMod.this.mLockPatternUtils;
            }
        };
        this.mSwitchPageRunnable = new Runnable() {
            public void run() {
                KeyguardHostViewMod.this.showAppropriateWidgetPage();
            }
        };
        this.mStartVisualizer = new Runnable() {
            public void run() {
                if (KeyguardHostViewMod.DEBUG) {
                    Log.w(KeyguardHostViewMod.TAG, "mStartVisualizer");
                }
                if (KeyguardHostViewMod.this.mVisualizer != null) {
                    KeyguardHostViewMod.this.mVisualizer.animate().alpha(1.0f).withLayer().setDuration(300);
                    AsyncTask.execute(new Runnable() {
                        public void run() {
                            KeyguardHostViewMod.this.mVisualizer.link(KeyguardHostViewMod.TRANSPORT_GONE);
                        }
                    });
                }
            }
        };
        this.mStopVisualizer = new Runnable() {
            public void run() {
                if (KeyguardHostViewMod.DEBUG) {
                    Log.w(KeyguardHostViewMod.TAG, "mStopVisualizer");
                }
                if (KeyguardHostViewMod.this.mVisualizer != null) {
                    KeyguardHostViewMod.this.mVisualizer.animate().alpha(0.0f).withLayer().setDuration(300);
                    AsyncTask.execute(new Runnable() {
                        public void run() {
                            KeyguardHostViewMod.this.mVisualizer.unlink();
                        }
                    });
                }
            }
        };
        if (DEBUG) {
            Log.e(TAG, "KeyguardHostViewMod()");
        }
        this.mLockPatternUtils = new LockPatternUtils(context);
        this.mUserId = this.mLockPatternUtils.getCurrentUser();
        DevicePolicyManager dpm = (DevicePolicyManager) this.mContext.getSystemService("device_policy");
        if (dpm != null) {
            this.mDisabledFeatures = getDisabledFeatures(dpm);
            this.mCameraDisabled = dpm.getCameraDisabled(null);
        }
        this.mSafeModeEnabled = LockPatternUtils.isSafeModeEnabled();
        try {
            String packageName = "system";
            userContext = this.mContext.createPackageContextAsUser("system", TRANSPORT_GONE, new UserHandle(this.mUserId));
        } catch (NameNotFoundException e) {
            e.printStackTrace();
            userContext = context;
        }
        this.mAppWidgetHost = new AppWidgetHost(userContext, APPWIDGET_HOST_ID, this.mOnClickHandler, Looper.myLooper());
        this.mAppWidgetManager = AppWidgetManager.getInstance(userContext);
        this.mSecurityModel = new KeyguardSecurityModel(context);
        this.mViewStateManager = new KeyguardViewStateManager(this);
        this.mAm = (AudioManager) this.mContext.getSystemService("audio");
        if (Secure.getIntForUser(this.mContext.getContentResolver(), "user_setup_complete", TRANSPORT_GONE, -2) != 0) {
            z = true;
        } else {
            z = false;
        }
        this.mUserSetupCompleted = z;
        getInitialTransportState();
        if (this.mSafeModeEnabled) {
            Log.v(TAG, "Keyguard widgets disabled by safe mode");
        }
        if ((this.mDisabledFeatures & TRANSPORT_INVISIBLE) != 0) {
            Log.v(TAG, "Keyguard widgets disabled by DPM");
        }
        if ((this.mDisabledFeatures & TRANSPORT_VISIBLE) != 0) {
            Log.v(TAG, "Keyguard secure camera disabled by DPM");
        }
        if ((this.mDisabledFeatures & 4) != 0) {
            Log.v(TAG, "Keyguard application widget disabled by DPM");
        }
    }

    public void setKeyguardPalette(Palette palette) {
        if (((FrameLayout) findViewById(R.id.keyguard_foreground)) != null) {
            KeyguardSmartCoverView kscv = (KeyguardSmartCoverView) findViewById(R.id.keyguard_cover_layout_mod);
            if (kscv != null) {
                kscv.setBackgroundColor(TRANSPORT_GONE);
                kscv.setEnableBatterySuperscript(true);
                kscv.setEnableFullDay(true);
                kscv.setAlpha(1.0f);
                kscv.setVisibility(TRANSPORT_GONE);
                kscv.setPalette(palette);
                return;
            }
            return;
        }
        Log.w(TAG, "setKeyguardPalette() but drawer was null :(");
    }

    public void announceCurrentSecurityMethod() {
        View v = (View) getSecurityView(this.mCurrentSecuritySelection);
        if (v != null) {
            v.announceForAccessibility(v.getContentDescription());
        }
    }

    private void getInitialTransportState() {
        DisplayClientState dcs = KeyguardUpdateMonitor.getInstance(this.mContext).getCachedDisplayClientState();
        int i = dcs.clearing ? TRANSPORT_GONE : isMusicPlaying(dcs.playbackState) ? TRANSPORT_VISIBLE : TRANSPORT_INVISIBLE;
        this.mTransportState = i;
        if (DEBUGXPORT) {
            Log.v(TAG, "Initial transport state: " + this.mTransportState + ", pbstate=" + dcs.playbackState);
        }
    }

    private void cleanupAppWidgetIds() {
        if (!this.mSafeModeEnabled && !widgetsDisabled()) {
            int[] appWidgetIdsInKeyguardSettings = this.mLockPatternUtils.getAppWidgets();
            int[] appWidgetIdsBoundToHost = this.mAppWidgetHost.getAppWidgetIds();
            int fallbackWidgetId = this.mLockPatternUtils.getFallbackAppWidgetId();
            for (int i = TRANSPORT_GONE; i < appWidgetIdsBoundToHost.length; i += TRANSPORT_INVISIBLE) {
                int appWidgetId = appWidgetIdsBoundToHost[i];
                if (!contains(appWidgetIdsInKeyguardSettings, appWidgetId)) {
                    if (appWidgetId == fallbackWidgetId) {
                        this.mLockPatternUtils.writeFallbackAppWidgetId(TRANSPORT_GONE);
                    }
                    Log.d(TAG, "Found a appWidgetId that's not being used by keyguard, deleting id " + appWidgetId);
                    this.mAppWidgetHost.deleteAppWidgetId(appWidgetId);
                }
            }
        }
    }

    private static boolean contains(int[] array, int target) {
        int[] arr$ = array;
        int len$ = arr$.length;
        for (int i$ = TRANSPORT_GONE; i$ < len$; i$ += TRANSPORT_INVISIBLE) {
            if (arr$[i$] == target) {
                return true;
            }
        }
        return false;
    }

    private void requestVisualizer(boolean show, int delay) {
        boolean start = false;
        if (show && this.mScreenOn && (this.mTransportState != 0 || this.mAm.isMusicActive())) {
            start = true;
        }
        removeCallbacks(this.mStartVisualizer);
        removeCallbacks(this.mStopVisualizer);
        if (start) {
            postDelayed(this.mStartVisualizer, (long) delay);
        } else {
            postDelayed(this.mStopVisualizer, (long) delay);
        }
    }

    private static final boolean isMusicPlaying(int playbackState) {
        switch (playbackState) {
            case SlidingChallengeLayout.SCROLL_STATE_FADING /*3*/:
            case LayoutParams.CHILD_TYPE_SCRIM /*4*/:
            case LayoutParams.CHILD_TYPE_WIDGETS /*5*/:
            case LayoutParams.CHILD_TYPE_EXPAND_CHALLENGE_HANDLE /*6*/:
            case MultiPaneChallengeLayout.LayoutParams.CHILD_TYPE_PAGE_DELETE_DROP_TARGET /*7*/:
            case KeyguardViewDragHelper.EDGE_BOTTOM /*8*/:
                return true;
            default:
                return false;
        }
    }

    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getAction()) {
            case TRANSPORT_GONE /*0*/:
                requestVisualizer(false, TRANSPORT_GONE);
                break;
            case TRANSPORT_INVISIBLE /*1*/:
            case SlidingChallengeLayout.SCROLL_STATE_FADING /*3*/:
                requestVisualizer(true, 400);
                break;
        }
        return super.onInterceptTouchEvent(ev);
    }

    public int getParallaxDistance() {
        if (this.mSlidingDrawer == null) {
            return TRANSPORT_GONE;
        }
        return this.mSlidingDrawer.getParallaxDistance();
    }

    public boolean onTouchEvent(MotionEvent ev) {
        boolean result = super.onTouchEvent(ev);
        this.mTempRect.set(TRANSPORT_GONE, TRANSPORT_GONE, TRANSPORT_GONE, TRANSPORT_GONE);
        offsetRectIntoDescendantCoords(this.mSecurityViewContainer, this.mTempRect);
        ev.offsetLocation((float) this.mTempRect.left, (float) this.mTempRect.top);
        if (this.mSecurityViewContainer.dispatchTouchEvent(ev) || result) {
            result = true;
        } else {
            result = false;
        }
        ev.offsetLocation((float) (-this.mTempRect.left), (float) (-this.mTempRect.top));
        return result;
    }

    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (this.mViewMediatorCallback != null) {
            this.mViewMediatorCallback.keyguardDoneDrawing();
        }
    }

    private int getWidgetPosition(int id) {
        KeyguardWidgetPager appWidgetContainer = this.mAppWidgetContainer;
        int children = appWidgetContainer.getChildCount();
        for (int i = TRANSPORT_GONE; i < children; i += TRANSPORT_INVISIBLE) {
            View content = appWidgetContainer.getWidgetPageAt(i).getContent();
            if (content != null && content.getId() == id) {
                return i;
            }
            if (content == null) {
                Log.w(TAG, "*** Null content at i=" + i + ",id=" + id + ",N=" + children);
            }
        }
        return -1;
    }

    public void dispatchWindowFocusChanged(boolean hasFocus) {
        super.dispatchWindowFocusChanged(hasFocus);
        if (hasFocus) {
            requestVisualizer(true, TRANSPORT_GONE);
        }
    }

    protected void onFinishInflate() {
        View deleteDropTarget = findViewById(R.id.keyguard_widget_pager_delete_target);
        this.mAppWidgetContainer = (KeyguardWidgetPager) findViewById(R.id.app_widget_container);
        this.mAppWidgetContainer.setVisibility(TRANSPORT_GONE);
        this.mAppWidgetContainer.setCallbacks(this.mWidgetCallbacks);
        this.mAppWidgetContainer.setDeleteDropTarget(deleteDropTarget);
        this.mAppWidgetContainer.setMinScale(0.5f);
        this.mAppWidgetContainer.setViewStateManager(this.mViewStateManager);
        this.mAppWidgetContainer.setLockPatternUtils(this.mLockPatternUtils);
        this.mCameraWidgetContainer = (FrameLayout) findViewById(R.id.camera_widget_container);
        this.mApplicationWidgetContainer = (FrameLayout) findViewById(R.id.application_widget_container);
        this.mSlidingDrawer = (KeyguardDrawerLayout) findViewById(R.id.drawer_layout);
        ChallengeLayout challenge = new ChallengeLayout() {
            boolean mChallengeShowing;
            boolean mWasChallengeShowing;

            public boolean isChallengeShowing() {
                return this.mChallengeShowing;
            }

            public boolean isChallengeOverlapping() {
                return this.mChallengeShowing;
            }

            public void showChallenge(boolean show) {
                this.mChallengeShowing = show;
            }

            public void showBouncer() {
                if (!KeyguardHostViewMod.this.mIsBouncing) {
                    KeyguardHostViewMod.this.setSystemUiVisibility(KeyguardHostViewMod.this.getSystemUiVisibility() | 33554432);
                    this.mWasChallengeShowing = this.mChallengeShowing;
                    KeyguardHostViewMod.this.mIsBouncing = true;
                    showChallenge(true);
                    CameraWidgetFrame cameraPage = KeyguardHostViewMod.this.findCameraPage();
                    if (cameraPage != null) {
                        cameraPage.onBouncerShowing(true);
                    }
                    ApplicationWidgetFrame applicationWidgetPage = KeyguardHostViewMod.this.findApplicationWidgetPage();
                    if (applicationWidgetPage != null) {
                        applicationWidgetPage.onBouncerShowing(true);
                    }
                    if (KeyguardHostViewMod.this.mViewStateManager != null) {
                        KeyguardHostViewMod.this.mViewStateManager.onBouncerStateChanged(true);
                    }
                    KeyguardHostViewMod.this.mSlidingDrawer.setDrawerLockMode((int) KeyguardHostViewMod.TRANSPORT_INVISIBLE, 5);
                    KeyguardHostViewMod.this.mSlidingDrawer.setDrawerLockMode((int) KeyguardHostViewMod.TRANSPORT_INVISIBLE, 3);
                }
            }

            public void hideBouncer() {
                if (KeyguardHostViewMod.this.mIsBouncing) {
                    KeyguardHostViewMod.this.setSystemUiVisibility(KeyguardHostViewMod.this.getSystemUiVisibility() & -33554433);
                    if (!this.mWasChallengeShowing) {
                        showChallenge(false);
                    }
                    KeyguardHostViewMod.this.mIsBouncing = false;
                    CameraWidgetFrame cameraPage = KeyguardHostViewMod.this.findCameraPage();
                    if (cameraPage != null) {
                        cameraPage.onBouncerShowing(false);
                    }
                    ApplicationWidgetFrame applicationWidgetPage = KeyguardHostViewMod.this.findApplicationWidgetPage();
                    if (applicationWidgetPage != null) {
                        applicationWidgetPage.onBouncerShowing(false);
                    }
                    if (KeyguardHostViewMod.this.mViewStateManager != null) {
                        KeyguardHostViewMod.this.mViewStateManager.onBouncerStateChanged(false);
                    }
                    KeyguardHostViewMod.this.mSlidingDrawer.setDrawerLockMode((int) KeyguardHostViewMod.TRANSPORT_GONE, 5);
                    KeyguardHostViewMod.this.mSlidingDrawer.setDrawerLockMode((int) KeyguardHostViewMod.TRANSPORT_GONE, 3);
                }
            }

            public boolean isBouncing() {
                return KeyguardHostViewMod.this.mIsBouncing;
            }

            public int getBouncerAnimationDuration() {
                return KeyguardHostViewMod.TRANSPORT_GONE;
            }

            public void setOnBouncerStateChangedListener(OnBouncerStateChangedListener listener) {
            }
        };
        this.mAppWidgetContainer.setBouncerAnimationDuration(challenge.getBouncerAnimationDuration());
        this.mViewStateManager.setPagedView(this.mAppWidgetContainer);
        this.mViewStateManager.setChallengeLayout(challenge);
        this.mSecurityViewContainer = (KeyguardSecurityViewFlipper) findViewById(R.id.view_flipper);
        this.mKeyguardSelectorViewMod = (KeyguardSelectorViewMod) findViewById(R.id.keyguard_selector_view);
        this.mViewStateManager.setSecurityViewContainer(this.mSecurityViewContainer);
        setBackButtonEnabled(false);
        if (KeyguardUpdateMonitor.getInstance(this.mContext).hasBootCompleted()) {
            updateAndAddWidgets();
            this.mSlidingDrawer.setParallaxDistance(KeyguardViewManager.WALLPAPER_PAPER_OFFSET);
        } else {
            this.mPostBootCompletedRunnable = new Runnable() {
                public void run() {
                    KeyguardHostViewMod.this.updateAndAddWidgets();
                    KeyguardHostViewMod.this.mSlidingDrawer.setParallaxDistance(KeyguardViewManager.WALLPAPER_PAPER_OFFSET);
                }
            };
        }
        this.mSlidingDrawer.setScrimColor(TRANSPORT_GONE);
        if (this.mLockPatternUtils.isSecure() || KeyguardUpdateMonitor.getInstance(this.mContext).isSimPinSecure()) {
            this.mSlidingDrawer.setDrawerLockMode((int) TRANSPORT_VISIBLE, 80);
        }
        final View bottomDrawer = this.mSlidingDrawer.findDrawerWithGravity(80);
        final View rightDrawer = this.mSlidingDrawer.findDrawerWithGravity(5);
        this.mCameraDrawer = rightDrawer;
        final View leftDrawer = this.mSlidingDrawer.findDrawerWithGravity(3);
        this.mApplicationWidgetDrawer = leftDrawer;
        final View bottomGradient = findViewById(R.id.static_gradient);
        this.mSlidingDrawer.setDrawerListener(new SimpleDrawerListener() {
            private Matrix center_inside;
            private int vheight;
            private int vwidth;

            public void onDrawerSlide(View drawerView, float slideOffset) {
                int dwidth;
                int dheight;
                float scale;
                float dx;
                float dy;
                if (rightDrawer == drawerView) {
                    ImageView camera_drawer = (ImageView) drawerView;
                    if (this.center_inside == null) {
                        dwidth = camera_drawer.getDrawable().getIntrinsicWidth();
                        dheight = camera_drawer.getDrawable().getIntrinsicHeight();
                        this.vwidth = (camera_drawer.getWidth() - camera_drawer.getPaddingLeft()) - camera_drawer.getPaddingRight();
                        this.vheight = (camera_drawer.getHeight() - camera_drawer.getPaddingTop()) - camera_drawer.getPaddingBottom();
                        this.center_inside = new Matrix(Matrix.IDENTITY_MATRIX);
                        if (dwidth > this.vwidth || dheight > this.vheight) {
                            scale = Math.min(((float) this.vwidth) / ((float) dwidth), ((float) this.vheight) / ((float) dheight));
                        } else {
                            scale = 1.0f;
                        }
                        dx = (float) ((int) (((((float) this.vwidth) - (((float) dwidth) * scale)) * 0.5f) + 0.5f));
                        dy = (float) ((int) (((((float) this.vheight) - (((float) dheight) * scale)) * 0.5f) + 0.5f));
                        this.center_inside.setScale(scale, scale);
                        this.center_inside.postTranslate(dx, dy);
                    }
                    Matrix camera_matrix = new Matrix(this.center_inside);
                    if (((double) slideOffset) < 0.75d) {
                        camera_matrix.postRotate(Math.max(0.0f, slideOffset - 0.5f) * 360.0f, (((float) this.vwidth) * 0.5f) + 0.5f, (((float) this.vheight) * 0.5f) + 0.5f);
                    } else {
                        camera_matrix.postRotate(90.0f, (((float) this.vwidth) * 0.5f) + 0.5f, (((float) this.vheight) * 0.5f) + 0.5f);
                        camera_matrix.postTranslate((slideOffset - 0.75f) * (2.0f * ((float) this.vwidth)), 0.0f);
                    }
                    camera_drawer.setImageMatrix(camera_matrix);
                    if (slideOffset == 0.0f) {
                        bottomGradient.setVisibility(KeyguardHostViewMod.TRANSPORT_GONE);
                    } else {
                        bottomGradient.setVisibility(4);
                    }
                } else if (leftDrawer == drawerView) {
                    ImageView applicationWidgetDrawer = (ImageView) drawerView;
                    Pair<String, byte[]> applicationWidgetDetails = KeyguardUpdateMonitor.getInstance(KeyguardHostViewMod.this.mContext).getApplicationWidgetDetails();
                    if (applicationWidgetDetails.first == null) {
                        KeyguardHostViewMod.this.mSlidingDrawer.closeDrawer(3);
                        return;
                    }
                    Bitmap image = BitmapFactory.decodeByteArray((byte[]) applicationWidgetDetails.second, KeyguardHostViewMod.TRANSPORT_GONE, ((byte[]) applicationWidgetDetails.second).length);
                    Drawable cameraIcon = KeyguardHostViewMod.this.getResources().getDrawable(R.drawable.ic_camera);
                    applicationWidgetDrawer.setImageBitmap(Bitmap.createScaledBitmap(image, cameraIcon.getIntrinsicHeight(), cameraIcon.getIntrinsicHeight(), false));
                    if (this.center_inside == null) {
                        dwidth = cameraIcon.getIntrinsicWidth();
                        dheight = cameraIcon.getIntrinsicHeight();
                        this.vwidth = (applicationWidgetDrawer.getWidth() - applicationWidgetDrawer.getPaddingLeft()) - applicationWidgetDrawer.getPaddingRight();
                        this.vheight = (applicationWidgetDrawer.getHeight() - applicationWidgetDrawer.getPaddingTop()) - applicationWidgetDrawer.getPaddingBottom();
                        this.center_inside = new Matrix(Matrix.IDENTITY_MATRIX);
                        if (dwidth > this.vwidth || dheight > this.vheight) {
                            scale = Math.min(((float) this.vwidth) / ((float) dwidth), ((float) this.vheight) / ((float) dheight));
                        } else {
                            scale = 1.0f;
                        }
                        dx = (float) ((int) (((((float) this.vwidth) - (((float) dwidth) * scale)) * 0.5f) + 0.5f));
                        dy = (float) ((int) (((((float) this.vheight) - (((float) dheight) * scale)) * 0.5f) + 0.5f));
                        this.center_inside.setScale(scale, scale);
                        this.center_inside.postTranslate(dx, dy);
                    }
                    Matrix applicationWidgetMatrix = new Matrix(this.center_inside);
                    if (((double) slideOffset) >= 0.75d) {
                        applicationWidgetMatrix.postTranslate((slideOffset - 0.75f) * (-2.0f * ((float) this.vwidth)), 0.0f);
                    }
                    applicationWidgetDrawer.setImageMatrix(applicationWidgetMatrix);
                    if (slideOffset == 0.0f) {
                        bottomGradient.setVisibility(KeyguardHostViewMod.TRANSPORT_GONE);
                    } else {
                        bottomGradient.setVisibility(4);
                    }
                }
                KeyguardHostViewMod.this.userActivity();
            }

            public void onDrawerOpened(View drawerView) {
                if (rightDrawer == drawerView) {
                    this.center_inside = null;
                    KeyguardHostViewMod.this.sendStickyBroadcast(false);
                    KeyguardHostViewMod.this.maybeTransitionToCamera();
                } else if (leftDrawer == drawerView) {
                    this.center_inside = null;
                    KeyguardHostViewMod.this.sendStickyBroadcast(false);
                    KeyguardHostViewMod.this.maybeTransitionToApplicationWidget();
                }
            }

            public void onDrawerClosed(View drawerView) {
                if (bottomDrawer == drawerView) {
                    KeyguardUpdateMonitor.getInstance(KeyguardHostViewMod.this.mContext).setAlternateUnlockEnabled(true);
                    boolean deferKeyguardDone = false;
                    if (KeyguardHostViewMod.this.mDismissAction != null) {
                        deferKeyguardDone = KeyguardHostViewMod.this.mDismissAction.onDismiss();
                        KeyguardHostViewMod.this.mDismissAction = null;
                    }
                    if (KeyguardHostViewMod.this.mViewMediatorCallback != null) {
                        if (deferKeyguardDone) {
                            KeyguardHostViewMod.this.mViewMediatorCallback.keyguardDonePending();
                        } else {
                            KeyguardHostViewMod.this.mViewMediatorCallback.keyguardDone(true);
                        }
                    }
                    KeyguardHostViewMod.this.sendStickyBroadcast(false);
                }
            }

            public void onDrawerReleased(View drawerView, boolean isOpening) {
                if (isOpening && rightDrawer == drawerView && !KeyguardHostViewMod.this.mCameraDrawerAnimating) {
                    KeyguardHostViewMod.this.maybeTransitionToCamera();
                } else if (isOpening && leftDrawer == drawerView && !KeyguardHostViewMod.this.mApplicationWidgetAnimating) {
                    KeyguardHostViewMod.this.maybeTransitionToApplicationWidget();
                }
            }
        });
        this.mSlidingDrawer.setDrawerLockMode(this.mCameraDrawerMode, 5);
        this.mSlidingDrawer.setDrawerLockMode(this.mApplicationWidgetDrawerMode, 3);
        showPrimarySecurityScreen(false);
        updateSecurityViews();
        enableUserSelectorIfNecessary();
        minimizeChallengeIfDesired();
        this.mSlidingDrawer.openDrawer(80);
    }

    private void updateAndAddWidgets() {
        cleanupAppWidgetIds();
        addDefaultWidgets();
        addWidgetsFromSettings();
        maybeEnableAddButton();
        checkAppWidgetConsistency();
        if (this.mSlidingChallengeLayout != null) {
            SlidingChallengeLayout slidingChallengeLayout = this.mSlidingChallengeLayout;
            boolean z = !widgetsDisabled() || this.mDefaultAppWidgetAttached;
            slidingChallengeLayout.setEnableChallengeDragging(z);
        }
        this.mSwitchPageRunnable.run();
        this.mViewStateManager.showUsabilityHints();
    }

    private void maybeEnableAddButton() {
        if (!shouldEnableAddWidget()) {
            this.mAppWidgetContainer.setAddWidgetEnabled(false);
        }
    }

    private void setBackButtonEnabled(boolean enabled) {
        if (!(this.mContext instanceof Activity)) {
            setSystemUiVisibility(enabled ? getSystemUiVisibility() & -4194305 : getSystemUiVisibility() | 4194304);
        }
    }

    private boolean shouldEnableAddWidget() {
        return false;
    }

    private int getDisabledFeatures(DevicePolicyManager dpm) {
        if (dpm != null) {
            return dpm.getKeyguardDisabledFeatures(null, this.mLockPatternUtils.getCurrentUser());
        }
        return TRANSPORT_GONE;
    }

    private boolean widgetsDisabled() {
        boolean disabledByDpm;
        if ((this.mDisabledFeatures & TRANSPORT_INVISIBLE) != 0) {
            disabledByDpm = true;
        } else {
            disabledByDpm = false;
        }
        boolean disabledByUser;
        if (this.mLockPatternUtils.getWidgetsEnabled()) {
            disabledByUser = false;
        } else {
            disabledByUser = true;
        }
        if (disabledByDpm || disabledByUser) {
            return true;
        }
        return false;
    }

    private boolean cameraDisabledByDpm() {
        boolean disabledSecureKeyguard;
        if ((this.mDisabledFeatures & TRANSPORT_VISIBLE) == 0 || !this.mLockPatternUtils.isSecure()) {
            disabledSecureKeyguard = false;
        } else {
            disabledSecureKeyguard = true;
        }
        if (this.mCameraDisabled || disabledSecureKeyguard || !this.mLockPatternUtils.getCameraEnabled()) {
            return true;
        }
        return false;
    }

    private boolean applicationWidgetDisabledByDpm() {
        return (this.mDisabledFeatures & 4) != 0;
    }

    private void updateSecurityViews() {
        int children = this.mSecurityViewContainer.getChildCount();
        for (int i = TRANSPORT_GONE; i < children; i += TRANSPORT_INVISIBLE) {
            updateSecurityView(this.mSecurityViewContainer.getChildAt(i));
        }
    }

    private void updateSecurityView(View view) {
        if (view instanceof KeyguardSecurityView) {
            KeyguardSecurityView ksv = (KeyguardSecurityView) view;
            ksv.setKeyguardCallback(this.mCallback);
            ksv.setLockPatternUtils(this.mLockPatternUtils);
            if (this.mViewStateManager.isBouncing()) {
                ksv.showBouncer(TRANSPORT_GONE);
                return;
            } else {
                ksv.hideBouncer(TRANSPORT_GONE);
                return;
            }
        }
        Log.w(TAG, "View " + view + " is not a KeyguardSecurityView");
    }

    void setLockPatternUtils(LockPatternUtils utils) {
        this.mSecurityModel.setLockPatternUtils(utils);
        this.mLockPatternUtils = utils;
        updateSecurityViews();
    }

    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        this.mAppWidgetHost.startListening();
        KeyguardUpdateMonitor.getInstance(this.mContext).registerCallback(this.mUpdateMonitorCallbacks);
        sendStickyBroadcast(true);
    }

    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        this.mAppWidgetHost.stopListening();
        KeyguardUpdateMonitor.getInstance(this.mContext).removeCallback(this.mUpdateMonitorCallbacks);
        requestVisualizer(false, TRANSPORT_GONE);
    }

    void addWidget(AppWidgetHostView view, int pageIndex) {
        this.mAppWidgetContainer.addWidget(view, pageIndex);
    }

    public void initializeSwitchingUserState(boolean switching) {
        if (!switching && this.mKeyguardMultiUserSelectorView != null) {
            this.mKeyguardMultiUserSelectorView.finalizeActiveUserView(false);
        }
    }

    public void userActivity() {
        if (this.mViewMediatorCallback != null) {
            this.mViewMediatorCallback.userActivity();
        }
    }

    public void onUserActivityTimeoutChanged() {
        if (this.mViewMediatorCallback != null) {
            this.mViewMediatorCallback.onUserActivityTimeoutChanged();
        }
    }

    public long getUserActivityTimeout() {
        if (this.mAppWidgetContainer != null) {
            return this.mAppWidgetContainer.getUserActivityTimeout();
        }
        return -1;
    }

    private void showDialog(String title, String message) {
        AlertDialog dialog = new Builder(this.mContext).setTitle(title).setMessage(message).setNeutralButton(R.string.ok, null).create();
        if (!(this.mContext instanceof Activity)) {
            dialog.getWindow().setType(2009);
        }
        dialog.show();
    }

    private void showTimeoutDialog() {
        int messageId = TRANSPORT_GONE;
        switch (AnonymousClass25.$SwitchMap$com$cyngn$keyguard$KeyguardSecurityModel$SecurityMode[this.mSecurityModel.getSecurityMode().ordinal()]) {
            case TRANSPORT_INVISIBLE /*1*/:
                messageId = R.string.kg_too_many_failed_pattern_attempts_dialog_message;
                break;
            case TRANSPORT_VISIBLE /*2*/:
                messageId = R.string.kg_too_many_failed_pin_attempts_dialog_message;
                break;
            case SlidingChallengeLayout.SCROLL_STATE_FADING /*3*/:
                messageId = R.string.kg_too_many_failed_password_attempts_dialog_message;
                break;
        }
        if (messageId != 0) {
            Context context = this.mContext;
            Object[] objArr = new Object[TRANSPORT_VISIBLE];
            objArr[TRANSPORT_GONE] = Integer.valueOf(KeyguardUpdateMonitor.getInstance(this.mContext).getFailedUnlockAttempts());
            objArr[TRANSPORT_INVISIBLE] = Integer.valueOf(30);
            showDialog(null, context.getString(messageId, objArr));
        }
    }

    private void showAlmostAtWipeDialog(int attempts, int remaining) {
        Context context = this.mContext;
        Object[] objArr = new Object[TRANSPORT_VISIBLE];
        objArr[TRANSPORT_GONE] = Integer.valueOf(attempts);
        objArr[TRANSPORT_INVISIBLE] = Integer.valueOf(remaining);
        showDialog(null, context.getString(R.string.kg_failed_attempts_almost_at_wipe, objArr));
    }

    private void showWipeDialog(int attempts) {
        Context context = this.mContext;
        Object[] objArr = new Object[TRANSPORT_INVISIBLE];
        objArr[TRANSPORT_GONE] = Integer.valueOf(attempts);
        showDialog(null, context.getString(R.string.kg_failed_attempts_now_wiping, objArr));
    }

    private void showAlmostAtAccountLoginDialog() {
        showDialog(null, this.mContext.getString(R.string.kg_failed_attempts_almost_at_login, new Object[]{Integer.valueOf(15), Integer.valueOf(5), Integer.valueOf(30)}));
    }

    private void reportFailedUnlockAttempt() {
        boolean usingPattern;
        KeyguardUpdateMonitor monitor = KeyguardUpdateMonitor.getInstance(this.mContext);
        int failedAttempts = monitor.getFailedUnlockAttempts() + TRANSPORT_INVISIBLE;
        if (DEBUG) {
            Log.d(TAG, "reportFailedPatternAttempt: #" + failedAttempts);
        }
        if (this.mSecurityModel.getSecurityMode() == SecurityMode.Pattern) {
            usingPattern = true;
        } else {
            usingPattern = false;
        }
        int failedAttemptsBeforeWipe = this.mLockPatternUtils.getDevicePolicyManager().getMaximumFailedPasswordsForWipe(null, this.mLockPatternUtils.getCurrentUser());
        int remainingBeforeWipe = failedAttemptsBeforeWipe > 0 ? failedAttemptsBeforeWipe - failedAttempts : Integer.MAX_VALUE;
        boolean showTimeout = false;
        if (remainingBeforeWipe >= 5) {
            showTimeout = failedAttempts % 5 == 0;
            if (usingPattern && this.mEnableFallback) {
                if (failedAttempts == 15) {
                    showAlmostAtAccountLoginDialog();
                    showTimeout = false;
                } else if (failedAttempts >= 20) {
                    this.mLockPatternUtils.setPermanentlyLocked(true);
                    showSecurityScreen(SecurityMode.Account);
                    showTimeout = false;
                }
            }
        } else if (remainingBeforeWipe > 0) {
            showAlmostAtWipeDialog(failedAttempts, remainingBeforeWipe);
        } else {
            Slog.i(TAG, "Too many unlock attempts; device will be wiped!");
            showWipeDialog(failedAttempts);
        }
        monitor.reportFailedUnlockAttempt();
        this.mLockPatternUtils.reportFailedPasswordAttempt();
        if (showTimeout) {
            showTimeoutDialog();
        }
    }

    void showPrimarySecurityScreen(boolean turningOff) {
        SecurityMode securityMode = this.mSecurityModel.getSecurityMode();
        if (DEBUG) {
            Log.v(TAG, "showPrimarySecurityScreen(turningOff=" + turningOff + ")");
        }
        if (!turningOff && KeyguardUpdateMonitor.getInstance(this.mContext).isAlternateUnlockEnabled()) {
            securityMode = this.mSecurityModel.getAlternateFor(securityMode);
        }
        showSecurityScreen(securityMode);
    }

    private void showBackupSecurityScreen() {
        if (DEBUG) {
            Log.d(TAG, "showBackupSecurity()");
        }
        showSecurityScreen(this.mSecurityModel.getBackupSecurityMode(this.mCurrentSecuritySelection));
    }

    public boolean showNextSecurityScreenIfPresent() {
        SecurityMode securityMode = this.mSecurityModel.getAlternateFor(this.mSecurityModel.getSecurityMode());
        if (SecurityMode.None == securityMode) {
            return false;
        }
        showSecurityScreen(securityMode);
        return true;
    }

    private void showNextSecurityScreenOrFinish(boolean authenticated) {
        if (DEBUG) {
            Log.d(TAG, "showNextSecurityScreenOrFinish(" + authenticated + ")");
        }
        boolean finish = false;
        SecurityMode securityMode;
        if (SecurityMode.None == this.mCurrentSecuritySelection) {
            securityMode = this.mSecurityModel.getAlternateFor(this.mSecurityModel.getSecurityMode());
            if (SecurityMode.None == securityMode) {
                finish = true;
            } else {
                showSecurityScreen(securityMode);
            }
        } else if (authenticated) {
            switch (AnonymousClass25.$SwitchMap$com$cyngn$keyguard$KeyguardSecurityModel$SecurityMode[this.mCurrentSecuritySelection.ordinal()]) {
                case TRANSPORT_INVISIBLE /*1*/:
                case TRANSPORT_VISIBLE /*2*/:
                case SlidingChallengeLayout.SCROLL_STATE_FADING /*3*/:
                case LayoutParams.CHILD_TYPE_SCRIM /*4*/:
                case LayoutParams.CHILD_TYPE_WIDGETS /*5*/:
                    finish = true;
                    break;
                case LayoutParams.CHILD_TYPE_EXPAND_CHALLENGE_HANDLE /*6*/:
                case MultiPaneChallengeLayout.LayoutParams.CHILD_TYPE_PAGE_DELETE_DROP_TARGET /*7*/:
                    securityMode = this.mSecurityModel.getSecurityMode();
                    if (securityMode == SecurityMode.None) {
                        finish = true;
                        break;
                    } else {
                        showSecurityScreen(securityMode);
                        break;
                    }
                default:
                    Log.v(TAG, "Bad security screen " + this.mCurrentSecuritySelection + ", fail safe");
                    showPrimarySecurityScreen(false);
                    break;
            }
        } else {
            showPrimarySecurityScreen(false);
        }
        if (finish) {
            this.mSlidingDrawer.closeDrawer(80);
        } else {
            this.mViewStateManager.showBouncer(true);
        }
    }

    protected void setOnDismissAction(OnDismissAction action) {
        this.mDismissAction = action;
    }

    private KeyguardSecurityView getSecurityView(SecurityMode securityMode) {
        int securityViewIdForMode = getSecurityViewIdForMode(securityMode);
        KeyguardSecurityView view = null;
        int children = this.mSecurityViewContainer.getChildCount();
        for (int child = TRANSPORT_GONE; child < children; child += TRANSPORT_INVISIBLE) {
            if (this.mSecurityViewContainer.getChildAt(child).getId() == securityViewIdForMode) {
                view = (KeyguardSecurityView) this.mSecurityViewContainer.getChildAt(child);
                break;
            }
        }
        int layoutId = getLayoutIdFor(securityMode);
        if (view == null && layoutId != 0) {
            LayoutInflater inflater = LayoutInflater.from(this.mContext);
            if (DEBUG) {
                Log.v(TAG, "inflating id = " + layoutId);
            }
            View v = inflater.inflate(layoutId, this.mSecurityViewContainer, false);
            if (KeyguardUpdateMonitor.sIsMultiSimEnabled) {
                ViewStub vStub = (ViewStub) v.findViewById(R.id.stub_msim_carrier_text);
                if (vStub != null) {
                    vStub.inflate();
                }
            }
            this.mSecurityViewContainer.addView(v);
            updateSecurityView(v);
            view = (KeyguardSecurityView) v;
        }
        if (view instanceof KeyguardSelectorViewMod) {
            KeyguardSelectorViewMod selectorView = (KeyguardSelectorViewMod) view;
            selectorView.setCarrierArea(selectorView.findViewById(R.id.keyguard_selector_fade_container));
        }
        return view;
    }

    private void showSecurityScreen(SecurityMode securityMode) {
        if (DEBUG) {
            Log.d(TAG, "showSecurityScreen(" + securityMode + ")");
        }
        if (securityMode != this.mCurrentSecuritySelection) {
            KeyguardSecurityView oldView = getSecurityView(this.mCurrentSecuritySelection);
            KeyguardSecurityView newView = getSecurityView(securityMode);
            boolean fullScreenEnabled = getResources().getBoolean(R.bool.kg_sim_puk_account_full_screen);
            boolean isSimOrAccount = securityMode == SecurityMode.SimPin || securityMode == SecurityMode.SimPuk || securityMode == SecurityMode.Account;
            KeyguardWidgetPager keyguardWidgetPager = this.mAppWidgetContainer;
            int i = (isSimOrAccount && fullScreenEnabled) ? 8 : TRANSPORT_GONE;
            keyguardWidgetPager.setVisibility(i);
            setSystemUiVisibility(isSimOrAccount ? getSystemUiVisibility() | 33554432 : getSystemUiVisibility() & -33554433);
            if (this.mSlidingChallengeLayout != null) {
                this.mSlidingChallengeLayout.setChallengeInteractive(!fullScreenEnabled);
            }
            if (oldView != null) {
                oldView.onPause();
                oldView.setKeyguardCallback(this.mNullCallback);
            }
            newView.onResume(TRANSPORT_VISIBLE);
            newView.setKeyguardCallback(this.mCallback);
            boolean needsInput = newView.needsInput();
            if (this.mViewMediatorCallback != null) {
                this.mViewMediatorCallback.setNeedsInput(needsInput);
            }
            int childCount = this.mSecurityViewContainer.getChildCount();
            int securityViewIdForMode = getSecurityViewIdForMode(securityMode);
            for (int i2 = TRANSPORT_GONE; i2 < childCount; i2 += TRANSPORT_INVISIBLE) {
                if (this.mSecurityViewContainer.getChildAt(i2).getId() == securityViewIdForMode) {
                    this.mSecurityViewContainer.setDisplayedChild(i2);
                    break;
                }
            }
            if (securityMode == SecurityMode.None) {
                setOnDismissAction(null);
            }
            if (securityMode == SecurityMode.Account && !this.mLockPatternUtils.isPermanentlyLocked()) {
                setBackButtonEnabled(true);
            }
            this.mCurrentSecuritySelection = securityMode;
            if (securityMode == SecurityMode.None) {
                this.mVisualizer = (VisualizerView) findViewById(R.id.visualizerView);
                if (this.mVisualizer != null) {
                    Paint paint = new Paint();
                    Resources res = this.mContext.getResources();
                    paint.setStrokeWidth((float) res.getDimensionPixelSize(R.dimen.eqalizer_path_stroke_width));
                    paint.setAntiAlias(true);
                    paint.setColor(res.getColor(R.color.equalizer_fill_color));
                    float[] fArr = new float[TRANSPORT_VISIBLE];
                    fArr[TRANSPORT_GONE] = (float) res.getDimensionPixelSize(R.dimen.eqalizer_path_effect_1);
                    fArr[TRANSPORT_INVISIBLE] = (float) res.getDimensionPixelSize(R.dimen.eqalizer_path_effect_2);
                    paint.setPathEffect(new DashPathEffect(fArr, 0.0f));
                    this.mVisualizer.addRenderer(new LockscreenBarEqRenderer(res.getInteger(R.integer.equalizer_divisions), paint, res.getInteger(R.integer.equalizer_db_fuzz), res.getInteger(R.integer.equalizer_db_fuzz_factor)));
                    return;
                }
                return;
            }
            this.mVisualizer = null;
        }
    }

    public void onScreenTurnedOn() {
        this.mScreenOn = true;
        if (DEBUG) {
            Log.d(TAG, "screen on, instance " + Integer.toHexString(hashCode()));
        }
        showPrimarySecurityScreen(false);
        getSecurityView(this.mCurrentSecuritySelection).onResume(TRANSPORT_INVISIBLE);
        requestLayout();
        if (this.mViewStateManager != null) {
            this.mViewStateManager.showUsabilityHints();
        }
        requestFocus();
        minimizeChallengeIfDesired();
        final int hints = this.mContext.getSharedPreferences(PREFS_FILE, TRANSPORT_GONE).getInt(PREF_CAMERA_DRAWER_HINTS, TRANSPORT_GONE);
        if (hints < 3) {
            this.mCameraDrawerAnimating = true;
            postDelayed(new Runnable() {
                public void run() {
                    KeyguardHostViewMod.this.mSlidingDrawer.openDrawerTo(KeyguardHostViewMod.this.mCameraDrawer, 0.5f);
                }
            }, 400);
            postDelayed(new Runnable() {
                public void run() {
                    KeyguardHostViewMod.this.mSlidingDrawer.openDrawerTo(KeyguardHostViewMod.this.mCameraDrawer, 0.0f);
                    KeyguardHostViewMod.this.mCameraDrawerAnimating = false;
                    KeyguardHostViewMod.this.mContext.getSharedPreferences(KeyguardHostViewMod.PREFS_FILE, KeyguardHostViewMod.TRANSPORT_GONE).edit().putInt(KeyguardHostViewMod.PREF_CAMERA_DRAWER_HINTS, hints + KeyguardHostViewMod.TRANSPORT_INVISIBLE).commit();
                }
            }, 1000);
        }
        requestVisualizer(true, TRANSPORT_GONE);
        updateStatusBarFlags();
    }

    private void updateStatusBarFlags() {
        boolean showClock = this.mTransportState != 0 && this.mLockPatternUtils.isSecure();
        KeyguardUpdateMonitor.getInstance(this.mContext).reportClockVisible(showClock);
    }

    public void onScreenTurnedOff() {
        this.mScreenOn = false;
        if (DEBUG) {
            String str = TAG;
            Object[] objArr = new Object[TRANSPORT_VISIBLE];
            objArr[TRANSPORT_GONE] = Integer.toHexString(hashCode());
            objArr[TRANSPORT_INVISIBLE] = Long.valueOf(SystemClock.uptimeMillis());
            Log.d(str, String.format("screen off, instance %s at %s", objArr));
        }
        KeyguardUpdateMonitor.getInstance(this.mContext).setAlternateUnlockEnabled(true);
        clearAppWidgetToShow();
        if (KeyguardUpdateMonitor.getInstance(this.mContext).hasBootCompleted()) {
            checkAppWidgetConsistency();
        }
        showPrimarySecurityScreen(true);
        getSecurityView(this.mCurrentSecuritySelection).onPause();
        CameraWidgetFrame cameraPage = findCameraPage();
        if (cameraPage != null) {
            cameraPage.onScreenTurnedOff();
        }
        ApplicationWidgetFrame applicationWidgetPage = findApplicationWidgetPage();
        if (applicationWidgetPage != null) {
            applicationWidgetPage.onScreenTurnedOff();
        }
        requestVisualizer(false, TRANSPORT_GONE);
        updateStatusBarFlags();
        clearFocus();
    }

    public void clearAppWidgetToShow() {
        this.mAppWidgetToShow = TRANSPORT_GONE;
    }

    public void show() {
        if (DEBUG) {
            Log.d(TAG, "show()");
        }
        showPrimarySecurityScreen(false);
    }

    public void verifyUnlock() {
        SecurityMode securityMode = this.mSecurityModel.getSecurityMode();
        if (securityMode == SecurityMode.None) {
            if (this.mViewMediatorCallback != null) {
                this.mViewMediatorCallback.keyguardDone(true);
            }
        } else if (securityMode == SecurityMode.Pattern || securityMode == SecurityMode.PIN || securityMode == SecurityMode.Password) {
            this.mIsVerifyUnlockOnly = true;
            showSecurityScreen(securityMode);
        } else if (this.mViewMediatorCallback != null) {
            this.mViewMediatorCallback.keyguardDone(false);
        }
    }

    private void minimizeChallengeIfDesired() {
        if (this.mSlidingChallengeLayout != null && System.getIntForUser(getContext().getContentResolver(), "lockscreen_maximize_widgets", TRANSPORT_GONE, -2) == TRANSPORT_INVISIBLE) {
            this.mSlidingChallengeLayout.showChallenge(false);
        }
    }

    private int getSecurityViewIdForMode(SecurityMode securityMode) {
        switch (AnonymousClass25.$SwitchMap$com$cyngn$keyguard$KeyguardSecurityModel$SecurityMode[securityMode.ordinal()]) {
            case TRANSPORT_INVISIBLE /*1*/:
                return R.id.keyguard_pattern_view;
            case TRANSPORT_VISIBLE /*2*/:
                return R.id.keyguard_pin_view;
            case SlidingChallengeLayout.SCROLL_STATE_FADING /*3*/:
                return R.id.keyguard_password_view;
            case LayoutParams.CHILD_TYPE_SCRIM /*4*/:
                return R.id.keyguard_account_view;
            case LayoutParams.CHILD_TYPE_WIDGETS /*5*/:
                return R.id.keyguard_face_unlock_view;
            case LayoutParams.CHILD_TYPE_EXPAND_CHALLENGE_HANDLE /*6*/:
                if (KeyguardUpdateMonitor.sIsMultiSimEnabled) {
                    return R.id.msim_keyguard_sim_pin_view;
                }
                return R.id.keyguard_sim_pin_view;
            case MultiPaneChallengeLayout.LayoutParams.CHILD_TYPE_PAGE_DELETE_DROP_TARGET /*7*/:
                if (KeyguardUpdateMonitor.sIsMultiSimEnabled) {
                    return R.id.msim_keyguard_sim_puk_view;
                }
                return R.id.keyguard_sim_puk_view;
            case KeyguardViewDragHelper.EDGE_BOTTOM /*8*/:
                return R.id.keyguard_selector_view;
            default:
                return TRANSPORT_GONE;
        }
    }

    private int getLayoutIdFor(SecurityMode securityMode) {
        switch (AnonymousClass25.$SwitchMap$com$cyngn$keyguard$KeyguardSecurityModel$SecurityMode[securityMode.ordinal()]) {
            case TRANSPORT_INVISIBLE /*1*/:
                return R.layout.keyguard_pattern_view;
            case TRANSPORT_VISIBLE /*2*/:
                return R.layout.keyguard_pin_view;
            case SlidingChallengeLayout.SCROLL_STATE_FADING /*3*/:
                return R.layout.keyguard_password_view;
            case LayoutParams.CHILD_TYPE_SCRIM /*4*/:
                return R.layout.keyguard_account_view;
            case LayoutParams.CHILD_TYPE_WIDGETS /*5*/:
                return R.layout.keyguard_face_unlock_view;
            case LayoutParams.CHILD_TYPE_EXPAND_CHALLENGE_HANDLE /*6*/:
                if (KeyguardUpdateMonitor.sIsMultiSimEnabled) {
                    return R.layout.msim_keyguard_sim_pin_view;
                }
                return R.layout.keyguard_sim_pin_view;
            case MultiPaneChallengeLayout.LayoutParams.CHILD_TYPE_PAGE_DELETE_DROP_TARGET /*7*/:
                if (KeyguardUpdateMonitor.sIsMultiSimEnabled) {
                    return R.layout.msim_keyguard_sim_puk_view;
                }
                return R.layout.keyguard_sim_puk_view;
            case KeyguardViewDragHelper.EDGE_BOTTOM /*8*/:
                return R.layout.keyguard_selector_view_mod;
            default:
                return TRANSPORT_GONE;
        }
    }

    private boolean addWidget(int appId, int pageIndex, boolean updateDbIfFailed) {
        AppWidgetProviderInfo appWidgetInfo = this.mAppWidgetManager.getAppWidgetInfo(appId);
        if (appWidgetInfo != null) {
            AppWidgetHostView view = this.mAppWidgetHost.createView(this.mContext, appId, appWidgetInfo);
            Bundle options = new Bundle();
            options.putInt("appWidgetCategory", TRANSPORT_VISIBLE);
            view.updateAppWidgetOptions(options);
            addWidget(view, pageIndex);
            return true;
        }
        if (updateDbIfFailed) {
            Log.w(TAG, "*** AppWidgetInfo for app widget id " + appId + "  was null for user" + this.mUserId + ", deleting");
            this.mAppWidgetHost.deleteAppWidgetId(appId);
            this.mLockPatternUtils.removeAppWidget(appId);
        }
        return false;
    }

    private synchronized void maybeTransitionToCamera() {
        if (this.mCameraLaunchTransition == null) {
            this.mCameraLaunchTransition = new Runnable() {
                public void run() {
                    final CameraWidgetFrame cameraPage = KeyguardHostViewMod.this.findCameraPage();
                    if (cameraPage != null) {
                        KeyguardHostViewMod.this.mCameraWidgetContainer.setVisibility(KeyguardHostViewMod.TRANSPORT_GONE);
                        KeyguardHostViewMod.this.mCameraWidgetContainer.setAlpha(0.0f);
                        KeyguardHostViewMod.this.mCameraWidgetContainer.animate().alpha(1.0f).setDuration(500).withStartAction(new Runnable() {
                            public void run() {
                                cameraPage.onActive(true);
                            }
                        }).start();
                    }
                }
            };
            post(this.mCameraLaunchTransition);
        }
    }

    private synchronized void maybeTransitionToApplicationWidget() {
        if (this.mApplicationWidgetLaunchTransition == null) {
            this.mApplicationWidgetLaunchTransition = new Runnable() {
                public void run() {
                    final ApplicationWidgetFrame applicationWidgetPage = KeyguardHostViewMod.this.findApplicationWidgetPage();
                    if (applicationWidgetPage != null) {
                        KeyguardHostViewMod.this.mApplicationWidgetContainer.setVisibility(KeyguardHostViewMod.TRANSPORT_GONE);
                        KeyguardHostViewMod.this.mApplicationWidgetContainer.setAlpha(0.0f);
                        KeyguardHostViewMod.this.mApplicationWidgetContainer.animate().alpha(1.0f).setDuration(500).withStartAction(new Runnable() {
                            public void run() {
                                applicationWidgetPage.onActive(true);
                            }
                        }).start();
                    }
                }
            };
            post(this.mApplicationWidgetLaunchTransition);
        }
    }

    private int numWidgets() {
        int childCount = this.mAppWidgetContainer.getChildCount();
        int widgetCount = TRANSPORT_GONE;
        for (int i = TRANSPORT_GONE; i < childCount; i += TRANSPORT_INVISIBLE) {
            if (this.mAppWidgetContainer.isWidgetPage(i)) {
                widgetCount += TRANSPORT_INVISIBLE;
            }
        }
        return widgetCount;
    }

    private void addDefaultWidgets() {
        if (!(this.mSafeModeEnabled || widgetsDisabled() || !this.mLockPatternUtils.isSecure())) {
            View addWidget = LayoutInflater.from(this.mContext).inflate(R.layout.keyguard_add_widget, this, false);
            this.mAppWidgetContainer.addWidget(addWidget, TRANSPORT_GONE);
            addWidget.findViewById(R.id.keyguard_add_widget_view).setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    KeyguardHostViewMod.this.mActivityLauncher.launchWidgetPicker(KeyguardHostViewMod.TRANSPORT_GONE);
                }
            });
        }
        if (this.mSafeModeEnabled || applicationWidgetDisabledByDpm() || !this.mUserSetupCompleted || !this.mContext.getResources().getBoolean(R.bool.kg_enable_application_widget)) {
            this.mApplicationWidgetDrawerMode = TRANSPORT_INVISIBLE;
        } else {
            this.mApplicationWidgetDrawerMode = TRANSPORT_GONE;
            this.mApplicationWidgetView = ApplicationWidgetFrame.create(this.mContext, this.mApplicationWidgetFrame, this.mActivityLauncher);
            Pair<String, byte[]> applicationWidget = KeyguardUpdateMonitor.getInstance(this.mContext).getApplicationWidgetDetails();
            if (applicationWidget.first != null) {
                ((ApplicationWidgetFrame) this.mApplicationWidgetView).setApplicationWidgetPackageName((String) applicationWidget.first);
                ((ApplicationWidgetFrame) this.mApplicationWidgetView).updatePreviewImage((byte[]) applicationWidget.second);
            }
            this.mApplicationWidgetContainer.addView(this.mApplicationWidgetView);
        }
        if (this.mSafeModeEnabled || cameraDisabledByDpm() || !this.mUserSetupCompleted || !this.mContext.getResources().getBoolean(R.bool.kg_enable_camera_default_widget)) {
            this.mCameraDrawerMode = TRANSPORT_INVISIBLE;
        } else {
            this.mCameraDrawerMode = TRANSPORT_GONE;
            View cameraWidget = CameraWidgetFrame.create(this.mContext, this.mCameraWidgetCallbacks, this.mActivityLauncher);
            if (cameraWidget != null) {
                this.mCameraWidgetContainer.addView(cameraWidget);
            }
        }
        if (this.mSlidingDrawer != null) {
            this.mSlidingDrawer.setDrawerLockMode(this.mCameraDrawerMode, 5);
            this.mSlidingDrawer.setDrawerLockMode(this.mApplicationWidgetDrawerMode, 3);
        }
    }

    private KeyguardTransportControlView getOrCreateTransportControl() {
        if (this.mTransportControl == null) {
            this.mTransportControl = (KeyguardTransportControlView) LayoutInflater.from(this.mContext).inflate(R.layout.keyguard_transport_control_view_mod, this, false);
            this.mTransportControl.setTransportControlCallback(new TransportControlCallback() {
                public void userActivity() {
                    KeyguardHostViewMod.this.mViewMediatorCallback.userActivity();
                }
            });
        }
        return this.mTransportControl;
    }

    private int getInsertPageIndex() {
        int addWidgetIndex = this.mAppWidgetContainer.indexOfChild(this.mAppWidgetContainer.findViewById(R.id.keyguard_add_widget));
        int applicationWidgetIndex = -1;
        if (this.mApplicationWidgetView != null) {
            applicationWidgetIndex = this.mAppWidgetContainer.getWidgetPageIndex(this.mApplicationWidgetView);
        }
        if (addWidgetIndex < 0 && applicationWidgetIndex < 0) {
            return TRANSPORT_GONE;
        }
        if (addWidgetIndex < 0 || applicationWidgetIndex < 0) {
            return TRANSPORT_INVISIBLE;
        }
        return TRANSPORT_VISIBLE;
    }

    private void addDefaultStatusWidget(int index) {
        this.mAppWidgetContainer.addWidget(LayoutInflater.from(this.mContext).inflate(R.layout.keyguard_status_view, null, true), index);
    }

    private void addWidgetsFromSettings() {
        if (!this.mSafeModeEnabled && !widgetsDisabled() && this.mLockPatternUtils.isSecure()) {
            int insertionIndex = getInsertPageIndex();
            int[] widgets = this.mLockPatternUtils.getAppWidgets();
            if (widgets == null) {
                Log.d(TAG, "Problem reading widgets");
                return;
            }
            for (int i = widgets.length - 1; i >= 0; i--) {
                if (widgets[i] == -2) {
                    addDefaultStatusWidget(insertionIndex);
                }
            }
        }
    }

    private int allocateIdForDefaultAppWidget() {
        Resources res = getContext().getResources();
        ComponentName defaultAppWidget = new ComponentName(res.getString(R.string.widget_default_package_name), res.getString(R.string.widget_default_class_name));
        int appWidgetId = this.mAppWidgetHost.allocateAppWidgetId();
        try {
            this.mAppWidgetManager.bindAppWidgetId(appWidgetId, defaultAppWidget);
            return appWidgetId;
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Error when trying to bind default AppWidget: " + e);
            this.mAppWidgetHost.deleteAppWidgetId(appWidgetId);
            return TRANSPORT_GONE;
        }
    }

    public void checkAppWidgetConsistency() {
        int childCount = this.mAppWidgetContainer.getChildCount();
        boolean widgetPageExists = false;
        for (int i = TRANSPORT_GONE; i < childCount; i += TRANSPORT_INVISIBLE) {
            if (this.mAppWidgetContainer.isWidgetPage(i)) {
                widgetPageExists = true;
                break;
            }
        }
        if (!widgetPageExists) {
            int insertPageIndex = getInsertPageIndex();
            if (widgetsDisabled()) {
                boolean z = false;
            }
            this.mDefaultAppWidgetAttached = false;
            if (!this.mSafeModeEnabled && this.mLockPatternUtils.isSecure()) {
                addDefaultStatusWidget(insertPageIndex);
            }
        }
    }

    public Parcelable onSaveInstanceState() {
        if (DEBUG) {
            Log.d(TAG, "onSaveInstanceState, tstate=" + this.mTransportState);
        }
        SavedState ss = new SavedState(super.onSaveInstanceState());
        boolean showing = this.mTransportControl != null && this.mAppWidgetContainer.getWidgetPageIndex(this.mTransportControl) >= 0;
        ss.transportState = showing ? TRANSPORT_VISIBLE : this.mTransportState;
        ss.appWidgetToShow = this.mAppWidgetToShow;
        ss.insets.set(this.mInsets);
        return ss;
    }

    public void onRestoreInstanceState(Parcelable state) {
        if (state instanceof SavedState) {
            SavedState ss = (SavedState) state;
            super.onRestoreInstanceState(ss.getSuperState());
            this.mTransportState = ss.transportState;
            this.mAppWidgetToShow = ss.appWidgetToShow;
            setInsets(ss.insets);
            if (DEBUG) {
                Log.d(TAG, "onRestoreInstanceState, transport=" + this.mTransportState);
            }
            this.mSwitchPageRunnable.run();
            return;
        }
        super.onRestoreInstanceState(state);
    }

    protected boolean fitSystemWindows(Rect insets) {
        setInsets(insets);
        return true;
    }

    private void setInsets(Rect insets) {
        this.mInsets.set(insets);
        if (this.mSlidingDrawer != null) {
            this.mSlidingDrawer.setInsets(insets);
        }
        CameraWidgetFrame cameraPage = findCameraPage();
        if (cameraPage != null) {
            cameraPage.setInsets(this.mInsets);
        }
        ApplicationWidgetFrame applicationWidgetFrame = findApplicationWidgetPage();
        if (applicationWidgetFrame != null) {
            applicationWidgetFrame.setInsets(this.mInsets);
        }
    }

    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (DEBUG) {
            Log.d(TAG, "Window is " + (hasWindowFocus ? "focused" : "unfocused"));
        }
        if (hasWindowFocus && this.mShowSecurityWhenReturn) {
            this.mShowSecurityWhenReturn = false;
            this.mCameraWidgetContainer.setVisibility(4);
            this.mApplicationWidgetContainer.setVisibility(4);
            this.mSlidingDrawer.closeDrawer(5);
            this.mSlidingDrawer.closeDrawer(3);
        }
    }

    private void showAppropriateWidgetPage() {
        int state = this.mTransportState;
        boolean transportAdded = ensureTransportPresentOrRemoved(state);
        final int pageToShow = getAppropriateWidgetPage(state);
        if (!transportAdded) {
            this.mAppWidgetContainer.setCurrentPage(pageToShow);
        } else if (state == TRANSPORT_VISIBLE) {
            post(new Runnable() {
                public void run() {
                    KeyguardHostViewMod.this.mAppWidgetContainer.setCurrentPage(pageToShow);
                }
            });
        }
    }

    private boolean ensureTransportPresentOrRemoved(int state) {
        boolean showing;
        if (getWidgetPosition(R.id.keyguard_transport_control) != -1) {
            showing = true;
        } else {
            showing = false;
        }
        boolean visible;
        if (state == TRANSPORT_VISIBLE) {
            visible = true;
        } else {
            visible = false;
        }
        boolean shouldBeVisible;
        if (state == TRANSPORT_INVISIBLE && isMusicPlaying(state)) {
            shouldBeVisible = true;
        } else {
            shouldBeVisible = false;
        }
        if (showing || !(visible || shouldBeVisible)) {
            if (showing && state == 0) {
                if (DEBUGXPORT) {
                    Log.v(TAG, "remove transport");
                }
                this.mAppWidgetContainer.removeWidget(getOrCreateTransportControl());
                this.mTransportControl = null;
                KeyguardUpdateMonitor.getInstance(getContext()).dispatchSetBackground(null);
            }
            return false;
        }
        int lastWidget = this.mAppWidgetContainer.getChildCount() - 1;
        int position = TRANSPORT_GONE;
        if (lastWidget >= 0) {
            position = this.mAppWidgetContainer.isCameraPage(lastWidget) ? lastWidget : lastWidget + TRANSPORT_INVISIBLE;
        }
        if (DEBUGXPORT) {
            Log.v(TAG, "add transport at " + position);
        }
        this.mAppWidgetContainer.addWidget(getOrCreateTransportControl(), position);
        return true;
    }

    private CameraWidgetFrame findCameraPage() {
        if (this.mCameraWidgetContainer == null || this.mCameraWidgetContainer.getChildCount() <= 0) {
            return null;
        }
        return (CameraWidgetFrame) this.mCameraWidgetContainer.getChildAt(TRANSPORT_GONE);
    }

    private ApplicationWidgetFrame findApplicationWidgetPage() {
        if (this.mApplicationWidgetContainer == null || this.mApplicationWidgetContainer.getChildCount() <= 0) {
            return null;
        }
        return (ApplicationWidgetFrame) this.mApplicationWidgetContainer.getChildAt(TRANSPORT_GONE);
    }

    boolean isMusicPage(int pageIndex) {
        return pageIndex >= 0 && pageIndex == getWidgetPosition(R.id.keyguard_transport_control);
    }

    private int getAppropriateWidgetPage(int musicTransportState) {
        if (this.mAppWidgetToShow != 0) {
            int childCount = this.mAppWidgetContainer.getChildCount();
            for (int i = TRANSPORT_GONE; i < childCount; i += TRANSPORT_INVISIBLE) {
                if (this.mAppWidgetContainer.getWidgetPageAt(i).getContentAppWidgetId() == this.mAppWidgetToShow) {
                    return i;
                }
            }
            this.mAppWidgetToShow = TRANSPORT_GONE;
        }
        if (musicTransportState == TRANSPORT_VISIBLE) {
            if (DEBUG) {
                Log.d(TAG, "Music playing, show transport");
            }
            return this.mAppWidgetContainer.getWidgetPageIndex(getOrCreateTransportControl());
        }
        int rightMost = this.mAppWidgetContainer.getChildCount() - 1;
        if (this.mAppWidgetContainer.isCameraPage(rightMost)) {
            rightMost--;
        }
        if (DEBUG) {
            Log.d(TAG, "Show right-most page " + rightMost);
        }
        return rightMost;
    }

    private void enableUserSelectorIfNecessary() {
        if (UserManager.supportsMultipleUsers()) {
            UserManager um = (UserManager) this.mContext.getSystemService("user");
            Throwable t;
            if (um == null) {
                t = new Throwable();
                t.fillInStackTrace();
                Log.e(TAG, "user service is null.", t);
                return;
            }
            List<UserInfo> users = um.getUsers(true);
            if (users == null) {
                t = new Throwable();
                t.fillInStackTrace();
                Log.e(TAG, "list of users is null.", t);
                return;
            }
            View multiUserView = findViewById(R.id.keyguard_user_selector);
            if (multiUserView == null) {
                t = new Throwable();
                t.fillInStackTrace();
                Log.e(TAG, "can't find user_selector in layout.", t);
            } else if (users.size() <= TRANSPORT_INVISIBLE) {
            } else {
                if (multiUserView instanceof KeyguardMultiUserSelectorView) {
                    this.mKeyguardMultiUserSelectorView = (KeyguardMultiUserSelectorView) multiUserView;
                    this.mKeyguardMultiUserSelectorView.setVisibility(TRANSPORT_GONE);
                    this.mKeyguardMultiUserSelectorView.addUsers(users);
                    this.mKeyguardMultiUserSelectorView.setCallback(new UserSwitcherCallback() {
                        public void hideSecurityView(int duration) {
                            KeyguardHostViewMod.this.mSecurityViewContainer.animate().alpha(0.0f).setDuration((long) duration);
                        }

                        public void showSecurityView() {
                            KeyguardHostViewMod.this.mSecurityViewContainer.setAlpha(1.0f);
                        }

                        public void showUnlockHint() {
                            if (KeyguardHostViewMod.this.mKeyguardSelectorViewMod != null) {
                                KeyguardHostViewMod.this.mKeyguardSelectorViewMod.showUsabilityHint();
                            }
                        }

                        public void userActivity() {
                            if (KeyguardHostViewMod.this.mViewMediatorCallback != null) {
                                KeyguardHostViewMod.this.mViewMediatorCallback.userActivity();
                            }
                        }
                    });
                    return;
                }
                t = new Throwable();
                t.fillInStackTrace();
                if (multiUserView == null) {
                    Log.e(TAG, "could not find the user_selector.", t);
                } else {
                    Log.e(TAG, "user_selector is the wrong type.", t);
                }
            }
        }
    }

    public void cleanUp() {
        int count = this.mAppWidgetContainer.getChildCount();
        for (int i = TRANSPORT_GONE; i < count; i += TRANSPORT_INVISIBLE) {
            this.mAppWidgetContainer.getWidgetPageAt(i).removeAllViews();
        }
    }

    private boolean shouldEnableMenuKey() {
        boolean configDisabled = getResources().getBoolean(R.bool.config_disableMenuKeyInLockScreen);
        boolean isTestHarness = ActivityManager.isRunningInTestHarness();
        boolean fileOverride = new File(ENABLE_MENU_KEY_FILE).exists();
        boolean menuOverride;
        if (System.getInt(getContext().getContentResolver(), "menu_unlock_screen", TRANSPORT_GONE) == TRANSPORT_INVISIBLE) {
            menuOverride = true;
        } else {
            menuOverride = false;
        }
        if (!configDisabled || isTestHarness || fileOverride || menuOverride) {
            return true;
        }
        return false;
    }

    private boolean shouldEnableHomeKey() {
        if (System.getInt(getContext().getContentResolver(), "home_unlock_screen", TRANSPORT_GONE) == TRANSPORT_INVISIBLE) {
            return true;
        }
        return false;
    }

    private boolean shouldEnableCameraKey() {
        if (System.getInt(getContext().getContentResolver(), "camera_unlock_screen", TRANSPORT_GONE) == TRANSPORT_INVISIBLE) {
            return true;
        }
        return false;
    }

    public void goToWidget(int appWidgetId) {
        this.mAppWidgetToShow = appWidgetId;
        this.mSwitchPageRunnable.run();
    }

    public boolean handleMenuKey() {
        if (!shouldEnableMenuKey()) {
            return false;
        }
        showNextSecurityScreenOrFinish(false);
        return true;
    }

    public boolean handleHomeKey() {
        if (!shouldEnableHomeKey()) {
            return false;
        }
        showNextSecurityScreenOrFinish(false);
        return true;
    }

    public boolean handleCameraKey() {
        if (!shouldEnableCameraKey()) {
            return false;
        }
        showNextSecurityScreenOrFinish(false);
        return true;
    }

    public boolean handleBackKey() {
        if (this.mCurrentSecuritySelection == SecurityMode.Account) {
            setBackButtonEnabled(false);
            showPrimarySecurityScreen(false);
            return true;
        } else if (this.mCurrentSecuritySelection == SecurityMode.None) {
            return false;
        } else {
            this.mCallback.dismiss(false);
            return true;
        }
    }

    public void dismiss() {
        requestVisualizer(false, TRANSPORT_GONE);
        showNextSecurityScreenOrFinish(false);
    }

    public void showAssistant() {
        Intent intent = ((SearchManager) this.mContext.getSystemService("search")).getAssistIntent(this.mContext, true, -2);
        if (intent != null) {
            ActivityOptions opts = ActivityOptions.makeCustomAnimation(this.mContext, R.anim.keyguard_action_assist_enter, R.anim.keyguard_action_assist_exit, getHandler(), null);
            intent.addFlags(268435456);
            this.mActivityLauncher.launchActivityWithAnimation(intent, false, opts.toBundle(), null, null);
        }
    }

    public void dispatchCameraEvent(MotionEvent event) {
        switch (event.getAction()) {
            case TRANSPORT_GONE /*0*/:
                this.mCameraLaunched = false;
                userActivity();
                this.mSlidingDrawer.openDrawerTo(5, 0.15f);
                this.mCameraXDown = event.getX();
                return;
            case TRANSPORT_INVISIBLE /*1*/:
            case SlidingChallengeLayout.SCROLL_STATE_FADING /*3*/:
                if (this.mCameraLaunched) {
                    this.mSlidingDrawer.openDrawerTo(5, 1.0f);
                    return;
                } else if (this.mSlidingDrawer.getDrawerViewOffset(5) < 1.0f) {
                    this.mSlidingDrawer.closeDrawer(5);
                    return;
                } else {
                    return;
                }
            case TRANSPORT_VISIBLE /*2*/:
                float percent = ((this.mCameraXDown - event.getX()) / (getResources().getDimension(R.dimen.camera_drawer_width) / 2.0f)) * 100.0f;
                if (percent >= 15.0f && percent < 100.0f) {
                    this.mCameraLaunched = false;
                    this.mSlidingDrawer.openDrawerTo(5, percent / 100.0f);
                    return;
                } else if (percent >= 100.0f) {
                    this.mCameraLaunched = true;
                    return;
                } else {
                    return;
                }
            default:
                return;
        }
    }

    public void launchCamera() {
        this.mActivityLauncher.launchCamera(getHandler(), new Runnable() {
            public void run() {
                if (KeyguardHostViewMod.this.mSlidingDrawer != null) {
                    KeyguardHostViewMod.this.mSlidingDrawer.closeDrawer(5);
                }
            }
        });
    }

    public void dispatchApplicationWidgetEvent(MotionEvent event) {
        switch (event.getAction()) {
            case TRANSPORT_GONE /*0*/:
                this.mApplicationWidgetLaunched = false;
                userActivity();
                this.mSlidingDrawer.openDrawerTo(3, 0.15f);
                this.mApplicationWidgetXDown = event.getX();
                return;
            case TRANSPORT_INVISIBLE /*1*/:
            case SlidingChallengeLayout.SCROLL_STATE_FADING /*3*/:
                if (this.mApplicationWidgetLaunched) {
                    this.mSlidingDrawer.openDrawerTo(3, 1.0f);
                    return;
                } else if (this.mSlidingDrawer.getDrawerViewOffset(3) < 1.0f) {
                    this.mSlidingDrawer.closeDrawer(3);
                    return;
                } else {
                    return;
                }
            case TRANSPORT_VISIBLE /*2*/:
                float percent = (-((this.mApplicationWidgetXDown - event.getX()) / (getResources().getDimension(R.dimen.application_widget_drawer_width) / 2.0f))) * 100.0f;
                if (percent >= 15.0f && percent < 100.0f) {
                    this.mApplicationWidgetLaunched = false;
                    this.mSlidingDrawer.openDrawerTo(3, percent / 100.0f);
                    return;
                } else if (percent >= 100.0f) {
                    this.mApplicationWidgetLaunched = true;
                    return;
                } else {
                    return;
                }
            default:
                return;
        }
    }

    public void launchApplicationWidget(String packageName) {
        this.mActivityLauncher.launchApplicationWidget(getHandler(), new Runnable() {
            public void run() {
                if (KeyguardHostViewMod.this.mSlidingDrawer != null) {
                    KeyguardHostViewMod.this.mSlidingDrawer.closeDrawer(3);
                }
            }
        }, packageName);
    }

    public void setCustomBackground(Drawable d, Drawable b) {
        this.mSlidingDrawer.setCustomBackground(d, b);
    }

    private synchronized void sendStickyBroadcast(boolean showing) {
        if (showing != this.mShowing) {
            this.mShowing = showing;
            if (DEBUG) {
                Log.v(TAG, "sending sticky broadcast " + (this.mShowing ? "Showing" : "Hiding"));
            }
            Intent intent = new Intent(CUSTOM_LOCKSCREEN_STATE);
            intent.putExtra("showing", this.mShowing);
            this.mContext.sendStickyBroadcast(intent);
        }
    }
}
