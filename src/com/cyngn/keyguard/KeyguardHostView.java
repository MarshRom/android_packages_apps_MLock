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
import android.graphics.Canvas;
import android.graphics.Rect;
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
import android.widget.RemoteViews.OnClickHandler;
import com.android.internal.widget.LockPatternUtils;
import com.cyngn.keyguard.KeyguardWidgetPager.Callbacks;
import com.cyngn.keyguard.SlidingChallengeLayout.LayoutParams;
import java.io.File;
import java.lang.ref.WeakReference;
import java.util.List;

public class KeyguardHostView extends KeyguardViewBase {
    static final int APPWIDGET_HOST_ID = 1262836039;
    public static boolean DEBUG = false;
    public static boolean DEBUGXPORT = false;
    private static final String ENABLE_MENU_KEY_FILE = "/data/local/enable_menu_key";
    private static final String TAG = "KeyguardHostView";
    static final int TRANSPORT_GONE = 0;
    static final int TRANSPORT_INVISIBLE = 1;
    static final int TRANSPORT_VISIBLE = 2;
    private final int MAX_WIDGETS;
    private final KeyguardActivityLauncher mActivityLauncher;
    private KeyguardWidgetPager mAppWidgetContainer;
    private AppWidgetHost mAppWidgetHost;
    private AppWidgetManager mAppWidgetManager;
    private int mAppWidgetToShow;
    private final Callbacks mApplicationWidgetFrame;
    private View mApplicationWidgetView;
    private KeyguardSecurityCallback mCallback;
    private boolean mCameraDisabled;
    private final Callbacks mCameraWidgetCallbacks;
    protected int mClientGeneration;
    private SecurityMode mCurrentSecuritySelection;
    private boolean mDefaultAppWidgetAttached;
    private int mDisabledFeatures;
    protected OnDismissAction mDismissAction;
    private boolean mEnableFallback;
    private View mExpandChallengeView;
    protected int mFailedAttempts;
    private final OnLongClickListener mFastUnlockClickListener;
    private final Rect mInsets;
    private boolean mIsVerifyUnlockOnly;
    private KeyguardMultiUserSelectorView mKeyguardMultiUserSelectorView;
    private KeyguardSelectorView mKeyguardSelectorView;
    private LockPatternUtils mLockPatternUtils;
    private MultiPaneChallengeLayout mMultiPaneChallengeLayout;
    private KeyguardSecurityCallback mNullCallback;
    private MyOnClickHandler mOnClickHandler;
    private Runnable mPostBootCompletedRunnable;
    private boolean mSafeModeEnabled;
    private KeyguardSecurityModel mSecurityModel;
    private KeyguardSecurityViewFlipper mSecurityViewContainer;
    protected boolean mShowSecurityWhenReturn;
    private SlidingChallengeLayout mSlidingChallengeLayout;
    private final Runnable mSwitchPageRunnable;
    private Rect mTempRect;
    private KeyguardTransportControlView mTransportControl;
    private int mTransportState;
    private KeyguardUpdateMonitorCallback mUpdateMonitorCallbacks;
    private final int mUserId;
    private boolean mUserSetupCompleted;
    private KeyguardViewStateManager mViewStateManager;
    private Callbacks mWidgetCallbacks;

    interface OnDismissAction {
        boolean onDismiss();
    }

    interface TransportControlCallback {
        void userActivity();
    }

    interface UserSwitcherCallback {
        void hideSecurityView(int i);

        void showSecurityView();

        void showUnlockHint();

        void userActivity();
    }

    static /* synthetic */ class AnonymousClass15 {
        static final /* synthetic */ int[] $SwitchMap$com$cyngn$keyguard$KeyguardSecurityModel$SecurityMode = new int[SecurityMode.values().length];

        static {
            try {
                $SwitchMap$com$cyngn$keyguard$KeyguardSecurityModel$SecurityMode[SecurityMode.Pattern.ordinal()] = KeyguardHostView.TRANSPORT_INVISIBLE;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$cyngn$keyguard$KeyguardSecurityModel$SecurityMode[SecurityMode.PIN.ordinal()] = KeyguardHostView.TRANSPORT_VISIBLE;
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

    private static class MyOnClickHandler extends OnClickHandler {
        WeakReference<KeyguardHostView> mThis;

        MyOnClickHandler(KeyguardHostView hostView) {
            this.mThis = new WeakReference(hostView);
        }

        public boolean onClickHandler(final View view, final PendingIntent pendingIntent, final Intent fillInIntent) {
            KeyguardHostView hostView = (KeyguardHostView) this.mThis.get();
            if (hostView == null) {
                return false;
            }
            if (!pendingIntent.isActivity()) {
                return super.onClickHandler(view, pendingIntent, fillInIntent);
            }
            hostView.setOnDismissAction(new OnDismissAction() {
                public boolean onDismiss() {
                    try {
                        view.getContext().startIntentSender(pendingIntent.getIntentSender(), fillInIntent, 268435456, 268435456, KeyguardHostView.TRANSPORT_GONE, ActivityOptions.makeScaleUpAnimation(view, KeyguardHostView.TRANSPORT_GONE, KeyguardHostView.TRANSPORT_GONE, view.getMeasuredWidth(), view.getMeasuredHeight()).toBundle());
                    } catch (SendIntentException e) {
                        Log.e(KeyguardHostView.TAG, "Cannot send pending intent: ", e);
                    } catch (Exception e2) {
                        Log.e(KeyguardHostView.TAG, "Cannot send pending intent due to unknown exception: ", e2);
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
            this.appWidgetToShow = KeyguardHostView.TRANSPORT_GONE;
            this.insets = new Rect();
        }

        private SavedState(Parcel in) {
            super(in);
            this.appWidgetToShow = KeyguardHostView.TRANSPORT_GONE;
            this.insets = new Rect();
            this.transportState = in.readInt();
            this.appWidgetToShow = in.readInt();
            this.insets = (Rect) in.readParcelable(null);
        }

        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(this.transportState);
            out.writeInt(this.appWidgetToShow);
            out.writeParcelable(this.insets, KeyguardHostView.TRANSPORT_GONE);
        }
    }

    public KeyguardHostView(Context context) {
        this(context, null);
    }

    public KeyguardHostView(Context context, AttributeSet attrs) {
        Context userContext;
        boolean z = false;
        super(context, attrs);
        this.mTransportState = TRANSPORT_GONE;
        this.MAX_WIDGETS = 5;
        this.mCurrentSecuritySelection = SecurityMode.Invalid;
        this.mTempRect = new Rect();
        this.mInsets = new Rect();
        this.mOnClickHandler = new MyOnClickHandler(this);
        this.mUpdateMonitorCallbacks = new KeyguardUpdateMonitorCallback() {
            public void onBootCompleted() {
                if (KeyguardHostView.this.mPostBootCompletedRunnable != null) {
                    KeyguardHostView.this.mPostBootCompletedRunnable.run();
                    KeyguardHostView.this.mPostBootCompletedRunnable = null;
                }
            }

            public void onUserSwitchComplete(int userId) {
                if (KeyguardHostView.this.mKeyguardMultiUserSelectorView != null) {
                    KeyguardHostView.this.mKeyguardMultiUserSelectorView.finalizeActiveUserView(true);
                }
            }

            void onMusicClientIdChanged(int clientGeneration, boolean clearing, PendingIntent intent) {
                int newState = KeyguardHostView.TRANSPORT_VISIBLE;
                if (KeyguardHostView.DEBUGXPORT && (KeyguardHostView.this.mClientGeneration != clientGeneration || clearing)) {
                    Log.v(KeyguardHostView.TAG, (clearing ? "hide" : "show") + " transport, gen:" + clientGeneration);
                }
                KeyguardHostView.this.mClientGeneration = clientGeneration;
                if (clearing) {
                    newState = KeyguardHostView.TRANSPORT_GONE;
                } else if (KeyguardHostView.this.mTransportState != KeyguardHostView.TRANSPORT_VISIBLE) {
                    newState = KeyguardHostView.TRANSPORT_INVISIBLE;
                }
                if (newState != KeyguardHostView.this.mTransportState) {
                    KeyguardHostView.this.mTransportState = newState;
                    if (KeyguardHostView.DEBUGXPORT) {
                        Log.v(KeyguardHostView.TAG, "update widget: transport state changed");
                    }
                    KeyguardHostView.this.post(KeyguardHostView.this.mSwitchPageRunnable);
                }
            }

            public void onMusicPlaybackStateChanged(int playbackState, long eventTime) {
                if (KeyguardHostView.DEBUGXPORT) {
                    Log.v(KeyguardHostView.TAG, "music state changed: " + playbackState);
                }
                if (KeyguardHostView.this.mTransportState != 0) {
                    int newState = KeyguardHostView.isMusicPlaying(playbackState) ? KeyguardHostView.TRANSPORT_VISIBLE : KeyguardHostView.TRANSPORT_INVISIBLE;
                    if (newState != KeyguardHostView.this.mTransportState) {
                        KeyguardHostView.this.mTransportState = newState;
                        if (KeyguardHostView.DEBUGXPORT) {
                            Log.v(KeyguardHostView.TAG, "update widget: play state changed");
                        }
                        KeyguardHostView.this.post(KeyguardHostView.this.mSwitchPageRunnable);
                    }
                }
            }
        };
        this.mFastUnlockClickListener = new OnLongClickListener() {
            public boolean onLongClick(View v) {
                if (KeyguardHostView.this.mLockPatternUtils.isTactileFeedbackEnabled()) {
                    v.performHapticFeedback(KeyguardHostView.TRANSPORT_GONE, KeyguardHostView.TRANSPORT_VISIBLE);
                }
                KeyguardHostView.this.showNextSecurityScreenOrFinish(false);
                return true;
            }
        };
        this.mWidgetCallbacks = new Callbacks() {
            public void userActivity() {
                KeyguardHostView.this.userActivity();
            }

            public void onUserActivityTimeoutChanged() {
                KeyguardHostView.this.onUserActivityTimeoutChanged();
            }

            public void onAddView(View v) {
                if (!KeyguardHostView.this.shouldEnableAddWidget()) {
                    KeyguardHostView.this.mAppWidgetContainer.setAddWidgetEnabled(false);
                }
            }

            public void onRemoveView(View v, boolean deletePermanently) {
                if (deletePermanently) {
                    int appWidgetId = ((KeyguardWidgetFrame) v).getContentAppWidgetId();
                    if (appWidgetId != 0 && appWidgetId != -2) {
                        KeyguardHostView.this.mAppWidgetHost.deleteAppWidgetId(appWidgetId);
                    }
                }
            }

            public void onRemoveViewAnimationCompleted() {
                if (KeyguardHostView.this.shouldEnableAddWidget()) {
                    KeyguardHostView.this.mAppWidgetContainer.setAddWidgetEnabled(true);
                }
            }
        };
        this.mCallback = new KeyguardSecurityCallback() {
            public void userActivity(long timeout) {
                if (KeyguardHostView.this.mViewMediatorCallback != null) {
                    KeyguardHostView.this.mViewMediatorCallback.userActivity(timeout);
                }
            }

            public void dismiss(boolean authenticated) {
                KeyguardHostView.this.showNextSecurityScreenOrFinish(authenticated);
            }

            public boolean isVerifyUnlockOnly() {
                return KeyguardHostView.this.mIsVerifyUnlockOnly;
            }

            public void reportSuccessfulUnlockAttempt() {
                KeyguardUpdateMonitor.getInstance(KeyguardHostView.this.mContext).clearFailedUnlockAttempts();
                KeyguardHostView.this.mLockPatternUtils.reportSuccessfulPasswordAttempt();
            }

            public void reportFailedUnlockAttempt() {
                if (KeyguardHostView.this.mCurrentSecuritySelection == SecurityMode.Biometric) {
                    KeyguardUpdateMonitor.getInstance(KeyguardHostView.this.mContext).reportFailedBiometricUnlockAttempt();
                } else {
                    KeyguardHostView.this.reportFailedUnlockAttempt();
                }
            }

            public int getFailedAttempts() {
                return KeyguardUpdateMonitor.getInstance(KeyguardHostView.this.mContext).getFailedUnlockAttempts();
            }

            public void showBackupSecurity() {
                KeyguardHostView.this.showBackupSecurityScreen();
            }

            public void setOnDismissAction(OnDismissAction action) {
                KeyguardHostView.this.setOnDismissAction(action);
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
                return KeyguardHostView.TRANSPORT_GONE;
            }

            public void dismiss(boolean securityVerified) {
            }
        };
        this.mCameraWidgetCallbacks = new Callbacks() {
            public void onLaunchingCamera() {
                setSliderHandleAlpha(0.0f);
            }

            public void onCameraLaunchedSuccessfully() {
                if (KeyguardHostView.this.mAppWidgetContainer.isCameraPage(KeyguardHostView.this.mAppWidgetContainer.getCurrentPage())) {
                    KeyguardHostView.this.mAppWidgetContainer.scrollLeft();
                }
                setSliderHandleAlpha(1.0f);
                KeyguardHostView.this.mShowSecurityWhenReturn = true;
            }

            public void onCameraLaunchedUnsuccessfully() {
                setSliderHandleAlpha(1.0f);
            }

            private void setSliderHandleAlpha(float alpha) {
                SlidingChallengeLayout slider = (SlidingChallengeLayout) KeyguardHostView.this.findViewById(R.id.sliding_layout);
                if (slider != null) {
                    slider.setHandleAlpha(alpha);
                }
            }
        };
        this.mApplicationWidgetFrame = new Callbacks() {
            public void onLaunchingApplicationWidgetContainer() {
                setSliderHandleAlpha(0.0f);
            }

            public void onApplicationWidgetContainerLaunchedSuccessfully() {
                if (KeyguardHostView.this.mAppWidgetContainer.isApplicationWidgetPage(KeyguardHostView.this.mAppWidgetContainer.getCurrentPage())) {
                    KeyguardHostView.this.mAppWidgetContainer.scrollRight();
                }
                setSliderHandleAlpha(1.0f);
                KeyguardHostView.this.mShowSecurityWhenReturn = true;
            }

            public void onApplicationWidgetContainerLaunchedUnsuccessfully() {
                setSliderHandleAlpha(1.0f);
            }

            private void setSliderHandleAlpha(float alpha) {
                SlidingChallengeLayout slider = (SlidingChallengeLayout) KeyguardHostView.this.findViewById(R.id.sliding_layout);
                if (slider != null) {
                    slider.setHandleAlpha(alpha);
                }
            }
        };
        this.mActivityLauncher = new KeyguardActivityLauncher() {
            Context getContext() {
                return KeyguardHostView.this.mContext;
            }

            KeyguardSecurityCallback getCallback() {
                return KeyguardHostView.this.mCallback;
            }

            LockPatternUtils getLockPatternUtils() {
                return KeyguardHostView.this.mLockPatternUtils;
            }
        };
        this.mSwitchPageRunnable = new Runnable() {
            public void run() {
                KeyguardHostView.this.showAppropriateWidgetPage();
            }
        };
        if (DEBUG) {
            Log.e(TAG, "KeyguardHostView()");
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
        if (Secure.getIntForUser(this.mContext.getContentResolver(), "user_setup_complete", TRANSPORT_GONE, -2) != 0) {
            z = true;
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

    public void announceCurrentSecurityMethod() {
        View v = (View) getSecurityView(this.mCurrentSecuritySelection);
        if (v != null) {
            v.announceForAccessibility(v.getContentDescription());
        }
    }

    private boolean applicationWidgetDisabledByDpm() {
        return (this.mDisabledFeatures & 4) != 0;
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

    protected void onFinishInflate() {
        View deleteDropTarget = findViewById(R.id.keyguard_widget_pager_delete_target);
        this.mAppWidgetContainer = (KeyguardWidgetPager) findViewById(R.id.app_widget_container);
        this.mAppWidgetContainer.setVisibility(TRANSPORT_GONE);
        this.mAppWidgetContainer.setCallbacks(this.mWidgetCallbacks);
        this.mAppWidgetContainer.setDeleteDropTarget(deleteDropTarget);
        this.mAppWidgetContainer.setMinScale(0.5f);
        this.mSlidingChallengeLayout = (SlidingChallengeLayout) findViewById(R.id.sliding_layout);
        if (this.mSlidingChallengeLayout != null) {
            this.mSlidingChallengeLayout.setOnChallengeScrolledListener(this.mViewStateManager);
        }
        this.mAppWidgetContainer.setViewStateManager(this.mViewStateManager);
        this.mAppWidgetContainer.setLockPatternUtils(this.mLockPatternUtils);
        this.mMultiPaneChallengeLayout = (MultiPaneChallengeLayout) findViewById(R.id.multi_pane_challenge);
        ChallengeLayout challenge = this.mSlidingChallengeLayout != null ? this.mSlidingChallengeLayout : this.mMultiPaneChallengeLayout;
        challenge.setOnBouncerStateChangedListener(this.mViewStateManager);
        this.mAppWidgetContainer.setBouncerAnimationDuration(challenge.getBouncerAnimationDuration());
        this.mViewStateManager.setPagedView(this.mAppWidgetContainer);
        this.mViewStateManager.setChallengeLayout(challenge);
        this.mSecurityViewContainer = (KeyguardSecurityViewFlipper) findViewById(R.id.view_flipper);
        this.mKeyguardSelectorView = (KeyguardSelectorView) findViewById(R.id.keyguard_selector_view);
        this.mViewStateManager.setSecurityViewContainer(this.mSecurityViewContainer);
        setBackButtonEnabled(false);
        if (KeyguardUpdateMonitor.getInstance(this.mContext).hasBootCompleted()) {
            updateAndAddWidgets();
        } else {
            this.mPostBootCompletedRunnable = new Runnable() {
                public void run() {
                    KeyguardHostView.this.updateAndAddWidgets();
                }
            };
        }
        showPrimarySecurityScreen(false);
        updateSecurityViews();
        enableUserSelectorIfNecessary();
        minimizeChallengeIfDesired();
        this.mExpandChallengeView = findViewById(R.id.expand_challenge_handle);
        if (this.mExpandChallengeView != null) {
            this.mExpandChallengeView.setOnLongClickListener(this.mFastUnlockClickListener);
        }
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
        return numWidgets() < 5 && this.mUserSetupCompleted;
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
    }

    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        this.mAppWidgetHost.stopListening();
        KeyguardUpdateMonitor.getInstance(this.mContext).removeCallback(this.mUpdateMonitorCallbacks);
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
        switch (AnonymousClass15.$SwitchMap$com$cyngn$keyguard$KeyguardSecurityModel$SecurityMode[this.mSecurityModel.getSecurityMode().ordinal()]) {
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
            switch (AnonymousClass15.$SwitchMap$com$cyngn$keyguard$KeyguardSecurityModel$SecurityMode[this.mCurrentSecuritySelection.ordinal()]) {
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
            KeyguardUpdateMonitor.getInstance(this.mContext).setAlternateUnlockEnabled(true);
            boolean deferKeyguardDone = false;
            if (this.mDismissAction != null) {
                deferKeyguardDone = this.mDismissAction.onDismiss();
                this.mDismissAction = null;
            }
            if (this.mViewMediatorCallback == null) {
                return;
            }
            if (deferKeyguardDone) {
                this.mViewMediatorCallback.keyguardDonePending();
                return;
            } else {
                this.mViewMediatorCallback.keyguardDone(true);
                return;
            }
        }
        this.mViewStateManager.showBouncer(true);
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
        if (view instanceof KeyguardSelectorView) {
            KeyguardSelectorView selectorView = (KeyguardSelectorView) view;
            selectorView.setCarrierArea(selectorView.findViewById(R.id.keyguard_selector_fade_container));
        }
        return view;
    }

    private void showSecurityScreen(SecurityMode securityMode) {
        boolean z = false;
        if (DEBUG) {
            Log.d(TAG, "showSecurityScreen(" + securityMode + ")");
        }
        if (securityMode != this.mCurrentSecuritySelection) {
            boolean isSimOrAccount;
            KeyguardSecurityView oldView = getSecurityView(this.mCurrentSecuritySelection);
            KeyguardSecurityView newView = getSecurityView(securityMode);
            boolean fullScreenEnabled = getResources().getBoolean(R.bool.kg_sim_puk_account_full_screen);
            if (securityMode == SecurityMode.SimPin || securityMode == SecurityMode.SimPuk || securityMode == SecurityMode.Account) {
                isSimOrAccount = true;
            } else {
                isSimOrAccount = false;
            }
            KeyguardWidgetPager keyguardWidgetPager = this.mAppWidgetContainer;
            int i = (isSimOrAccount && fullScreenEnabled) ? 8 : TRANSPORT_GONE;
            keyguardWidgetPager.setVisibility(i);
            if (isSimOrAccount) {
                i = getSystemUiVisibility() | 33554432;
            } else {
                i = getSystemUiVisibility() & -33554433;
            }
            setSystemUiVisibility(i);
            if (this.mSlidingChallengeLayout != null) {
                SlidingChallengeLayout slidingChallengeLayout = this.mSlidingChallengeLayout;
                if (!fullScreenEnabled) {
                    z = true;
                }
                slidingChallengeLayout.setChallengeInteractive(z);
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
        }
    }

    public void onScreenTurnedOn() {
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
    }

    public void onScreenTurnedOff() {
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
        switch (AnonymousClass15.$SwitchMap$com$cyngn$keyguard$KeyguardSecurityModel$SecurityMode[securityMode.ordinal()]) {
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
        switch (AnonymousClass15.$SwitchMap$com$cyngn$keyguard$KeyguardSecurityModel$SecurityMode[securityMode.ordinal()]) {
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
                return R.layout.keyguard_selector_view;
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
        if (!(this.mSafeModeEnabled || widgetsDisabled())) {
            View addWidget = LayoutInflater.from(this.mContext).inflate(R.layout.keyguard_add_widget, this, false);
            this.mAppWidgetContainer.addWidget(addWidget, TRANSPORT_GONE);
            addWidget.findViewById(R.id.keyguard_add_widget_view).setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    KeyguardHostView.this.mActivityLauncher.launchWidgetPicker(KeyguardHostView.TRANSPORT_GONE);
                }
            });
        }
        if (!this.mSafeModeEnabled && !applicationWidgetDisabledByDpm() && this.mUserSetupCompleted && this.mContext.getResources().getBoolean(R.bool.kg_enable_application_widget)) {
            Pair<String, byte[]> applicationWidget = KeyguardUpdateMonitor.getInstance(this.mContext).getApplicationWidgetDetails();
            if (applicationWidget.first != null) {
                this.mApplicationWidgetView = ApplicationWidgetFrame.create(this.mContext, this.mApplicationWidgetFrame, this.mActivityLauncher);
                if (this.mApplicationWidgetView != null) {
                    ((ApplicationWidgetFrame) this.mApplicationWidgetView).setApplicationWidgetPackageName((String) applicationWidget.first);
                    ((ApplicationWidgetFrame) this.mApplicationWidgetView).updatePreviewImage((byte[]) applicationWidget.second);
                    this.mAppWidgetContainer.addWidget(this.mApplicationWidgetView);
                }
            }
        }
        if (!this.mSafeModeEnabled && !cameraDisabledByDpm() && this.mUserSetupCompleted && this.mContext.getResources().getBoolean(R.bool.kg_enable_camera_default_widget)) {
            View cameraWidget = CameraWidgetFrame.create(this.mContext, this.mCameraWidgetCallbacks, this.mActivityLauncher);
            if (cameraWidget != null) {
                this.mAppWidgetContainer.addWidget(cameraWidget);
            }
        }
    }

    private KeyguardTransportControlView getOrCreateTransportControl() {
        if (this.mTransportControl == null) {
            this.mTransportControl = (KeyguardTransportControlView) LayoutInflater.from(this.mContext).inflate(R.layout.keyguard_transport_control_view, this, false);
            this.mTransportControl.setTransportControlCallback(new TransportControlCallback() {
                public void userActivity() {
                    KeyguardHostView.this.mViewMediatorCallback.userActivity();
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
        if (!this.mSafeModeEnabled && !widgetsDisabled()) {
            int insertionIndex = getInsertPageIndex();
            int[] widgets = this.mLockPatternUtils.getAppWidgets();
            if (widgets == null) {
                Log.d(TAG, "Problem reading widgets");
                return;
            }
            for (int i = widgets.length - 1; i >= 0; i--) {
                if (widgets[i] == -2) {
                    addDefaultStatusWidget(insertionIndex);
                } else {
                    addWidget(widgets[i], insertionIndex, true);
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
            boolean userAddedWidgetsEnabled;
            int insertPageIndex = getInsertPageIndex();
            if (widgetsDisabled()) {
                userAddedWidgetsEnabled = false;
            } else {
                userAddedWidgetsEnabled = true;
            }
            this.mDefaultAppWidgetAttached = false;
            if (!this.mSafeModeEnabled) {
                int appWidgetId;
                if (userAddedWidgetsEnabled) {
                    appWidgetId = allocateIdForDefaultAppWidget();
                    if (appWidgetId != 0) {
                        this.mDefaultAppWidgetAttached = addWidget(appWidgetId, insertPageIndex, true);
                    }
                } else {
                    appWidgetId = this.mLockPatternUtils.getFallbackAppWidgetId();
                    if (appWidgetId == 0) {
                        appWidgetId = allocateIdForDefaultAppWidget();
                        if (appWidgetId != 0) {
                            this.mLockPatternUtils.writeFallbackAppWidgetId(appWidgetId);
                        }
                    }
                    if (appWidgetId != 0) {
                        this.mDefaultAppWidgetAttached = addWidget(appWidgetId, insertPageIndex, false);
                        if (!this.mDefaultAppWidgetAttached) {
                            this.mAppWidgetHost.deleteAppWidgetId(appWidgetId);
                            this.mLockPatternUtils.writeFallbackAppWidgetId(TRANSPORT_GONE);
                        }
                    }
                }
            }
            if (!this.mDefaultAppWidgetAttached) {
                addDefaultStatusWidget(insertPageIndex);
            }
            if (!this.mSafeModeEnabled && userAddedWidgetsEnabled) {
                this.mAppWidgetContainer.onAddView(this.mAppWidgetContainer.getChildAt(insertPageIndex), insertPageIndex);
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
        if (this.mSlidingChallengeLayout != null) {
            this.mSlidingChallengeLayout.setInsets(this.mInsets);
        }
        if (this.mMultiPaneChallengeLayout != null) {
            this.mMultiPaneChallengeLayout.setInsets(this.mInsets);
        }
        CameraWidgetFrame cameraWidget = findCameraPage();
        if (cameraWidget != null) {
            cameraWidget.setInsets(this.mInsets);
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
            SlidingChallengeLayout slider = (SlidingChallengeLayout) findViewById(R.id.sliding_layout);
            if (slider != null) {
                slider.setHandleAlpha(1.0f);
                slider.showChallenge(true);
            }
            this.mShowSecurityWhenReturn = false;
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
                    KeyguardHostView.this.mAppWidgetContainer.setCurrentPage(pageToShow);
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
        for (int i = this.mAppWidgetContainer.getChildCount() - 1; i >= 0; i--) {
            if (this.mAppWidgetContainer.isCameraPage(i)) {
                return (CameraWidgetFrame) this.mAppWidgetContainer.getChildAt(i);
            }
        }
        return null;
    }

    private ApplicationWidgetFrame findApplicationWidgetPage() {
        for (int i = this.mAppWidgetContainer.getChildCount() - 1; i >= 0; i--) {
            if (this.mAppWidgetContainer.isApplicationWidgetPage(i)) {
                return (ApplicationWidgetFrame) this.mAppWidgetContainer.getChildAt(i);
            }
        }
        return null;
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
                            KeyguardHostView.this.mSecurityViewContainer.animate().alpha(0.0f).setDuration((long) duration);
                        }

                        public void showSecurityView() {
                            KeyguardHostView.this.mSecurityViewContainer.setAlpha(1.0f);
                        }

                        public void showUnlockHint() {
                            if (KeyguardHostView.this.mKeyguardSelectorView != null) {
                                KeyguardHostView.this.mKeyguardSelectorView.showUsabilityHint();
                            }
                        }

                        public void userActivity() {
                            if (KeyguardHostView.this.mViewMediatorCallback != null) {
                                KeyguardHostView.this.mViewMediatorCallback.userActivity();
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
        this.mAppWidgetContainer.handleExternalCameraEvent(event);
    }

    public void dispatchApplicationWidgetEvent(MotionEvent event) {
        this.mAppWidgetContainer.handleExternalApplicationWidgetEvent(event);
    }

    public void launchCamera() {
        this.mActivityLauncher.launchCamera(getHandler(), null);
    }

    public void launchApplicationWidget(String packageName) {
        this.mActivityLauncher.launchApplicationWidget(getHandler(), null, packageName);
    }
}
