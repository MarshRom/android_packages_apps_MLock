package com.cyngn.keyguard;

import android.app.Activity;
import android.app.ActivityManagerNative;
import android.app.AlarmManager;
import android.app.IActivityManager;
import android.app.PendingIntent;
import android.app.ProfileManager;
import android.app.SearchManager;
import android.app.StatusBarManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings.Global;
import android.provider.Settings.Secure;
import android.provider.Settings.System;
import android.telephony.MSimTelephonyManager;
import android.telephony.TelephonyManager;
import android.util.EventLog;
import android.util.Log;
import android.util.Slog;
import android.view.MotionEvent;
import android.view.WindowManager;
import com.android.internal.policy.IKeyguardExitCallback;
import com.android.internal.policy.IKeyguardShowCallback;
import com.android.internal.telephony.IccCardConstants.State;
import com.android.internal.util.cm.QuietHoursUtils;
import com.android.internal.widget.LockPatternUtils;

public class KeyguardViewMediator {
    protected static final int AWAKE_INTERVAL_DEFAULT_MS = 10000;
    private static final boolean DBG_WAKE = false;
    static final boolean DEBUG = false;
    private static final String DELAYED_KEYGUARD_ACTION = "com.android.internal.policy.impl.PhoneWindowManager.DELAYED_KEYGUARD";
    private static final int DISMISS = 19;
    private static final String DISMISS_KEYGUARD_SECURELY_ACTION = "com.android.keyguard.action.DISMISS_KEYGUARD_SECURELY";
    private static final int DISPATCH_APPLICATION_WIDGET_EVENT = 16;
    private static final int DISPATCH_CAMERA_EVENT = 15;
    private static final boolean ENABLE_INSECURE_STATUS_BAR_EXPAND = true;
    private static final int HIDE = 3;
    private static final int KEYGUARD_DISPLAY_TIMEOUT_DELAY_DEFAULT = 30000;
    private static final int KEYGUARD_DONE = 9;
    private static final int KEYGUARD_DONE_AUTHENTICATING = 11;
    private static final int KEYGUARD_DONE_DRAWING = 10;
    private static final int KEYGUARD_DONE_DRAWING_TIMEOUT_MS = 2000;
    private static final int KEYGUARD_LOCK_AFTER_DELAY_DEFAULT = 5000;
    private static final int KEYGUARD_TIMEOUT = 13;
    private static final int LAUNCH_APPLICATION_WIDGET = 18;
    private static final int LAUNCH_CAMERA = 17;
    private static final int NOTIFY_SCREEN_OFF = 6;
    private static final int NOTIFY_SCREEN_ON = 7;
    private static final int RESET = 4;
    private static final int SET_HIDDEN = 12;
    private static final int SHOW = 2;
    private static final int SHOW_ASSISTANT = 14;
    private static final String TAG = "KeyguardViewMediator";
    private static final Intent USER_PRESENT_INTENT = new Intent("android.intent.action.USER_PRESENT").addFlags(603979776);
    private static final int VERIFY_UNLOCK = 5;
    private static MultiUserAvatarCache sMultiUserAvatarCache = new MultiUserAvatarCache();
    private AlarmManager mAlarmManager;
    private AudioManager mAudioManager;
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if (KeyguardViewMediator.DELAYED_KEYGUARD_ACTION.equals(intent.getAction())) {
                int sequence = intent.getIntExtra("seq", 0);
                synchronized (KeyguardViewMediator.this) {
                    if (KeyguardViewMediator.this.mDelayedShowingSequence == sequence) {
                        KeyguardViewMediator.this.doKeyguardLocked(null);
                    }
                }
            } else if ("android.intent.action.LID_STATE_CHANGED".equals(intent.getAction())) {
                int state = intent.getIntExtra(KeyguardHostViewMod.PREFS_FILE, -1);
                synchronized (KeyguardViewMediator.this) {
                    if (state != KeyguardViewMediator.this.mLidState) {
                        KeyguardViewMediator.this.mLidState = state;
                        KeyguardViewMediator.this.mUpdateMonitor.dispatchLidStateChange(state);
                    }
                }
            } else if (KeyguardViewMediator.DISMISS_KEYGUARD_SECURELY_ACTION.equals(intent.getAction())) {
                synchronized (KeyguardViewMediator.this) {
                    KeyguardViewMediator.this.dismiss();
                }
            }
        }
    };
    private Context mContext;
    private int mDelayedShowingSequence;
    private IKeyguardExitCallback mExitSecureCallback;
    private boolean mExternallyEnabled = ENABLE_INSECURE_STATUS_BAR_EXPAND;
    private Handler mHandler = new Handler(Looper.myLooper(), null, ENABLE_INSECURE_STATUS_BAR_EXPAND) {
        public void handleMessage(Message msg) {
            boolean z = KeyguardViewMediator.ENABLE_INSECURE_STATUS_BAR_EXPAND;
            switch (msg.what) {
                case KeyguardViewMediator.SHOW /*2*/:
                    KeyguardViewMediator.this.handleShow((Bundle) msg.obj);
                    return;
                case KeyguardViewMediator.HIDE /*3*/:
                    KeyguardViewMediator.this.handleHide();
                    return;
                case KeyguardViewMediator.RESET /*4*/:
                    KeyguardViewMediator.this.handleReset((Bundle) msg.obj);
                    return;
                case KeyguardViewMediator.VERIFY_UNLOCK /*5*/:
                    KeyguardViewMediator.this.handleVerifyUnlock();
                    return;
                case KeyguardViewMediator.NOTIFY_SCREEN_OFF /*6*/:
                    KeyguardViewMediator.this.handleNotifyScreenOff();
                    return;
                case KeyguardViewMediator.NOTIFY_SCREEN_ON /*7*/:
                    KeyguardViewMediator.this.handleNotifyScreenOn((IKeyguardShowCallback) msg.obj);
                    return;
                case KeyguardViewMediator.KEYGUARD_DONE /*9*/:
                    KeyguardViewMediator keyguardViewMediator = KeyguardViewMediator.this;
                    boolean z2 = msg.arg1 != 0 ? KeyguardViewMediator.ENABLE_INSECURE_STATUS_BAR_EXPAND : KeyguardViewMediator.DEBUG;
                    if (msg.arg2 == 0) {
                        z = KeyguardViewMediator.DEBUG;
                    }
                    keyguardViewMediator.handleKeyguardDone(z2, z);
                    return;
                case KeyguardViewMediator.KEYGUARD_DONE_DRAWING /*10*/:
                    KeyguardViewMediator.this.handleKeyguardDoneDrawing();
                    return;
                case KeyguardViewMediator.KEYGUARD_DONE_AUTHENTICATING /*11*/:
                    KeyguardViewMediator.this.keyguardDone(KeyguardViewMediator.ENABLE_INSECURE_STATUS_BAR_EXPAND, KeyguardViewMediator.ENABLE_INSECURE_STATUS_BAR_EXPAND);
                    return;
                case KeyguardViewMediator.SET_HIDDEN /*12*/:
                    KeyguardViewMediator keyguardViewMediator2 = KeyguardViewMediator.this;
                    if (msg.arg1 == 0) {
                        z = KeyguardViewMediator.DEBUG;
                    }
                    keyguardViewMediator2.handleSetHidden(z);
                    return;
                case KeyguardViewMediator.KEYGUARD_TIMEOUT /*13*/:
                    synchronized (KeyguardViewMediator.this) {
                        KeyguardViewMediator.this.doKeyguardLocked((Bundle) msg.obj);
                    }
                    return;
                case KeyguardViewMediator.SHOW_ASSISTANT /*14*/:
                    KeyguardViewMediator.this.handleShowAssistant();
                    return;
                case KeyguardViewMediator.DISPATCH_CAMERA_EVENT /*15*/:
                    KeyguardViewMediator.this.handleDispatchCameraEvent((MotionEvent) msg.obj);
                    return;
                case KeyguardViewMediator.DISPATCH_APPLICATION_WIDGET_EVENT /*16*/:
                    KeyguardViewMediator.this.handleDispatchApplicationWidgetEvent((MotionEvent) msg.obj);
                    return;
                case KeyguardViewMediator.LAUNCH_CAMERA /*17*/:
                    KeyguardViewMediator.this.handleLaunchCamera();
                    return;
                case KeyguardViewMediator.LAUNCH_APPLICATION_WIDGET /*18*/:
                    KeyguardViewMediator.this.handleLaunchApplicationWidget();
                    return;
                case KeyguardViewMediator.DISMISS /*19*/:
                    KeyguardViewMediator.this.handleDismiss();
                    return;
                default:
                    return;
            }
        }
    };
    private boolean mHidden = DEBUG;
    private KeyguardDisplayManager mKeyguardDisplayManager;
    private boolean mKeyguardDonePending = DEBUG;
    private KeyguardViewManager mKeyguardViewManager;
    private int mLastProfileMode = 0;
    private int mLidState = -1;
    private LockPatternUtils mLockPatternUtils;
    private int mLockSoundId;
    private int mLockSoundStreamId;
    private final float mLockSoundVolume;
    private SoundPool mLockSounds;
    private int mMasterStreamType;
    private boolean mNeedToReshowWhenReenabled = DEBUG;
    private PowerManager mPM;
    private String mPhoneState = TelephonyManager.EXTRA_STATE_IDLE;
    private ProfileManager mProfileManager;
    private boolean mScreenOn;
    private SearchManager mSearchManager;
    private WakeLock mShowKeyguardWakeLock;
    private boolean mShowing;
    private StatusBarManager mStatusBarManager;
    private boolean mSuppressNextLockSound = ENABLE_INSECURE_STATUS_BAR_EXPAND;
    private boolean mSwitchingUser;
    private boolean mSystemReady;
    private int mUnlockSoundId;
    KeyguardUpdateMonitorCallback mUpdateCallback = new KeyguardUpdateMonitorCallback() {
        public void onUserSwitching(int userId) {
            synchronized (KeyguardViewMediator.this) {
                KeyguardViewMediator.this.mSwitchingUser = KeyguardViewMediator.ENABLE_INSECURE_STATUS_BAR_EXPAND;
                KeyguardViewMediator.this.resetStateLocked(null);
                KeyguardViewMediator.this.adjustStatusBarLocked();
                KeyguardUpdateMonitor.getInstance(KeyguardViewMediator.this.mContext).setAlternateUnlockEnabled(KeyguardViewMediator.ENABLE_INSECURE_STATUS_BAR_EXPAND);
            }
        }

        public void onUserSwitchComplete(int userId) {
            KeyguardViewMediator.this.mSwitchingUser = KeyguardViewMediator.DEBUG;
        }

        public void onUserRemoved(int userId) {
            KeyguardViewMediator.this.mLockPatternUtils.removeUser(userId);
            KeyguardViewMediator.sMultiUserAvatarCache.clear(userId);
        }

        public void onUserInfoChanged(int userId) {
            KeyguardViewMediator.sMultiUserAvatarCache.clear(userId);
        }

        void onPhoneStateChanged(int phoneState) {
            synchronized (KeyguardViewMediator.this) {
                if (phoneState == 0) {
                    if (!KeyguardViewMediator.this.mScreenOn && KeyguardViewMediator.this.mExternallyEnabled) {
                        KeyguardViewMediator.this.doKeyguardLocked(null);
                    }
                }
            }
        }

        public void onClockVisibilityChanged() {
            KeyguardViewMediator.this.adjustStatusBarLocked();
        }

        public void onDeviceProvisioned() {
            KeyguardViewMediator.this.sendUserPresentBroadcast();
        }

        public void onSimStateChanged(State simState) {
            onSimStateChanged(simState, MSimTelephonyManager.getDefault().getDefaultSubscription());
        }

        public void onSimStateChanged(State simState, int subscription) {
            switch (AnonymousClass6.$SwitchMap$com$android$internal$telephony$IccCardConstants$State[simState.ordinal()]) {
                case SlidingChallengeLayout.SCROLL_STATE_DRAGGING /*1*/:
                case KeyguardViewMediator.SHOW /*2*/:
                    synchronized (this) {
                        if (!KeyguardViewMediator.this.mUpdateMonitor.isDeviceProvisioned()) {
                            if (KeyguardViewMediator.this.isShowing()) {
                                KeyguardViewMediator.this.resetStateLocked(null);
                            } else {
                                KeyguardViewMediator.this.doKeyguardLocked(null);
                            }
                        }
                    }
                    return;
                case KeyguardViewMediator.HIDE /*3*/:
                case KeyguardViewMediator.RESET /*4*/:
                    synchronized (this) {
                        if (KeyguardViewMediator.this.isShowing()) {
                            KeyguardViewMediator.this.resetStateLocked(null);
                        } else {
                            KeyguardViewMediator.this.doKeyguardLocked(null);
                        }
                    }
                    return;
                case KeyguardViewMediator.VERIFY_UNLOCK /*5*/:
                    synchronized (this) {
                        if (KeyguardViewMediator.this.isShowing()) {
                            KeyguardViewMediator.this.resetStateLocked(null);
                        } else {
                            KeyguardViewMediator.this.doKeyguardLocked(null);
                        }
                    }
                    return;
                case KeyguardViewMediator.NOTIFY_SCREEN_OFF /*6*/:
                    synchronized (this) {
                        if (KeyguardViewMediator.this.isShowing()) {
                            KeyguardViewMediator.this.resetStateLocked(null);
                        }
                    }
                    return;
                default:
                    return;
            }
        }
    };
    private KeyguardUpdateMonitor mUpdateMonitor;
    private UserManager mUserManager;
    ViewMediatorCallback mViewMediatorCallback = new ViewMediatorCallback() {
        public void userActivity() {
            KeyguardViewMediator.this.userActivity();
        }

        public void userActivity(long holdMs) {
            KeyguardViewMediator.this.userActivity(holdMs);
        }

        public void keyguardDone(boolean authenticated) {
            KeyguardViewMediator.this.keyguardDone(authenticated, KeyguardViewMediator.ENABLE_INSECURE_STATUS_BAR_EXPAND);
        }

        public void keyguardDoneDrawing() {
            KeyguardViewMediator.this.mHandler.sendEmptyMessage(KeyguardViewMediator.KEYGUARD_DONE_DRAWING);
        }

        public void setNeedsInput(boolean needsInput) {
            KeyguardViewMediator.this.mKeyguardViewManager.setNeedsInput(needsInput);
        }

        public void onUserActivityTimeoutChanged() {
            KeyguardViewMediator.this.mKeyguardViewManager.updateUserActivityTimeout();
        }

        public void keyguardDonePending() {
            KeyguardViewMediator.this.mKeyguardDonePending = KeyguardViewMediator.ENABLE_INSECURE_STATUS_BAR_EXPAND;
        }

        public void keyguardGone() {
            KeyguardViewMediator.this.mKeyguardDisplayManager.hide();
        }
    };
    private boolean mWaitingUntilKeyguardVisible = DEBUG;

    public interface ViewMediatorCallback {
        void keyguardDone(boolean z);

        void keyguardDoneDrawing();

        void keyguardDonePending();

        void keyguardGone();

        void onUserActivityTimeoutChanged();

        void setNeedsInput(boolean z);

        void userActivity();

        void userActivity(long j);
    }

    static /* synthetic */ class AnonymousClass6 {
        static final /* synthetic */ int[] $SwitchMap$com$android$internal$telephony$IccCardConstants$State = new int[State.values().length];

        static {
            try {
                $SwitchMap$com$android$internal$telephony$IccCardConstants$State[State.NOT_READY.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$IccCardConstants$State[State.ABSENT.ordinal()] = KeyguardViewMediator.SHOW;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$IccCardConstants$State[State.PIN_REQUIRED.ordinal()] = KeyguardViewMediator.HIDE;
            } catch (NoSuchFieldError e3) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$IccCardConstants$State[State.PUK_REQUIRED.ordinal()] = KeyguardViewMediator.RESET;
            } catch (NoSuchFieldError e4) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$IccCardConstants$State[State.PERM_DISABLED.ordinal()] = KeyguardViewMediator.VERIFY_UNLOCK;
            } catch (NoSuchFieldError e5) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$IccCardConstants$State[State.READY.ordinal()] = KeyguardViewMediator.NOTIFY_SCREEN_OFF;
            } catch (NoSuchFieldError e6) {
            }
        }
    }

    private void userActivity() {
        userActivity(10000);
    }

    public void userActivity(long holdMs) {
        this.mPM.userActivity(SystemClock.uptimeMillis(), DEBUG);
    }

    public KeyguardViewMediator(Context context, LockPatternUtils lockPatternUtils) {
        this.mContext = context;
        this.mPM = (PowerManager) context.getSystemService("power");
        this.mUserManager = (UserManager) this.mContext.getSystemService("user");
        this.mShowKeyguardWakeLock = this.mPM.newWakeLock(1, "show keyguard");
        this.mShowKeyguardWakeLock.setReferenceCounted(DEBUG);
        this.mContext.registerReceiver(this.mBroadcastReceiver, new IntentFilter(DELAYED_KEYGUARD_ACTION));
        this.mContext.registerReceiver(this.mBroadcastReceiver, new IntentFilter("android.intent.action.LID_STATE_CHANGED"));
        this.mContext.registerReceiver(this.mBroadcastReceiver, new IntentFilter(DISMISS_KEYGUARD_SECURELY_ACTION), "android.permission.CONTROL_KEYGUARD", null);
        this.mKeyguardDisplayManager = new KeyguardDisplayManager(context);
        this.mAlarmManager = (AlarmManager) context.getSystemService("alarm");
        this.mUpdateMonitor = KeyguardUpdateMonitor.getInstance(context);
        if (lockPatternUtils == null) {
            lockPatternUtils = new LockPatternUtils(this.mContext);
        }
        this.mLockPatternUtils = lockPatternUtils;
        this.mLockPatternUtils.setCurrentUser(0);
        boolean z = ((this.mUpdateMonitor.isDeviceProvisioned() || this.mLockPatternUtils.isSecure()) && !this.mLockPatternUtils.isLockScreenDisabled()) ? ENABLE_INSECURE_STATUS_BAR_EXPAND : DEBUG;
        this.mShowing = z;
        WindowManager wm = (WindowManager) context.getSystemService("window");
        this.mProfileManager = (ProfileManager) context.getSystemService("profile");
        this.mKeyguardViewManager = new KeyguardViewManager(context, wm, this.mViewMediatorCallback, this.mLockPatternUtils);
        ContentResolver cr = this.mContext.getContentResolver();
        this.mScreenOn = this.mPM.isScreenOn();
        this.mLockSounds = new SoundPool(1, 1, 0);
        String soundPath = Global.getString(cr, "lock_sound");
        if (soundPath != null) {
            this.mLockSoundId = this.mLockSounds.load(soundPath, 1);
        }
        if (soundPath == null || this.mLockSoundId == 0) {
            Log.w(TAG, "failed to load lock sound from " + soundPath);
        }
        soundPath = Global.getString(cr, "unlock_sound");
        if (soundPath != null) {
            this.mUnlockSoundId = this.mLockSounds.load(soundPath, 1);
        }
        if (soundPath == null || this.mUnlockSoundId == 0) {
            Log.w(TAG, "failed to load unlock sound from " + soundPath);
        }
        this.mLockSoundVolume = (float) Math.pow(10.0d, (double) (((float) context.getResources().getInteger(17694725)) / 20.0f));
    }

    public void onSystemReady() {
        this.mSearchManager = (SearchManager) this.mContext.getSystemService("search");
        synchronized (this) {
            this.mSystemReady = ENABLE_INSECURE_STATUS_BAR_EXPAND;
            this.mUpdateMonitor.registerCallback(this.mUpdateCallback);
            if (this.mLockPatternUtils.usingBiometricWeak() && this.mLockPatternUtils.isBiometricWeakInstalled()) {
                this.mUpdateMonitor.setAlternateUnlockEnabled(DEBUG);
            } else {
                this.mUpdateMonitor.setAlternateUnlockEnabled(ENABLE_INSECURE_STATUS_BAR_EXPAND);
            }
            doKeyguardLocked(null);
        }
        maybeSendUserPresentBroadcast();
    }

    public void onScreenTurnedOff(int why) {
        synchronized (this) {
            this.mScreenOn = DEBUG;
            this.mKeyguardDonePending = DEBUG;
            boolean lockImmediately = this.mLockPatternUtils.getPowerButtonInstantlyLocks();
            if (this.mExitSecureCallback != null) {
                try {
                    this.mExitSecureCallback.onKeyguardExitResult(DEBUG);
                } catch (RemoteException e) {
                    Slog.w(TAG, "Failed to call onKeyguardExitResult(false)", e);
                }
                this.mExitSecureCallback = null;
                if (!this.mExternallyEnabled) {
                    hideLocked();
                }
            } else if (this.mShowing) {
                notifyScreenOffLocked();
                resetStateLocked(null);
            } else if (why == HIDE || (why == SHOW && !lockImmediately)) {
                doKeyguardLaterLocked();
            } else if (why != RESET) {
                doKeyguardLocked(null);
            }
        }
        KeyguardUpdateMonitor.getInstance(this.mContext).dispatchScreenTurndOff(why);
    }

    private void doKeyguardLaterLocked() {
        long timeout;
        ContentResolver cr = this.mContext.getContentResolver();
        long displayTimeout = (long) System.getInt(cr, "screen_off_timeout", KEYGUARD_DISPLAY_TIMEOUT_DELAY_DEFAULT);
        long lockAfterTimeout = (long) Secure.getInt(cr, "lock_screen_lock_after_timeout", KEYGUARD_LOCK_AFTER_DELAY_DEFAULT);
        long policyTimeout = this.mLockPatternUtils.getDevicePolicyManager().getMaximumTimeToLock(null, this.mLockPatternUtils.getCurrentUser());
        if (policyTimeout > 0) {
            timeout = Math.min(policyTimeout - Math.max(displayTimeout, 0), lockAfterTimeout);
        } else {
            timeout = lockAfterTimeout;
        }
        if (timeout <= 0) {
            doKeyguardLocked(null);
            return;
        }
        long when = SystemClock.elapsedRealtime() + timeout;
        Intent intent = new Intent(DELAYED_KEYGUARD_ACTION);
        intent.putExtra("seq", this.mDelayedShowingSequence);
        this.mAlarmManager.set(SHOW, when, PendingIntent.getBroadcast(this.mContext, 0, intent, 268435456));
    }

    private void cancelDoKeyguardLaterLocked() {
        this.mDelayedShowingSequence++;
    }

    public void onScreenTurnedOn(IKeyguardShowCallback callback) {
        synchronized (this) {
            this.mScreenOn = ENABLE_INSECURE_STATUS_BAR_EXPAND;
            cancelDoKeyguardLaterLocked();
            if (callback != null) {
                notifyScreenOnLocked(callback);
            }
        }
        KeyguardUpdateMonitor.getInstance(this.mContext).dispatchScreenTurnedOn();
        maybeSendUserPresentBroadcast();
    }

    private void maybeSendUserPresentBroadcast() {
        if (this.mSystemReady && isKeyguardDisabled() && !this.mShowing && !this.mShowKeyguardWakeLock.isHeld()) {
            sendUserPresentBroadcast();
        }
    }

    private boolean isKeyguardDisabled() {
        if (!this.mExternallyEnabled) {
            return ENABLE_INSECURE_STATUS_BAR_EXPAND;
        }
        if ((this.mLockPatternUtils.isLockScreenDisabled() && this.mUserManager.getUsers(ENABLE_INSECURE_STATUS_BAR_EXPAND).size() == 1) || this.mLockPatternUtils.getActiveProfileLockMode() == SHOW) {
            return ENABLE_INSECURE_STATUS_BAR_EXPAND;
        }
        return DEBUG;
    }

    public void onDreamingStarted() {
        synchronized (this) {
            if (this.mScreenOn && this.mLockPatternUtils.isSecure()) {
                doKeyguardLaterLocked();
            }
        }
    }

    public void onDreamingStopped() {
        synchronized (this) {
            if (this.mScreenOn) {
                cancelDoKeyguardLaterLocked();
            }
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void setKeyguardEnabled(boolean r6) {
        /*
        r5 = this;
        monitor-enter(r5);
        r5.mExternallyEnabled = r6;	 Catch:{ all -> 0x0017 }
        if (r6 != 0) goto L_0x001a;
    L_0x0005:
        r1 = r5.mShowing;	 Catch:{ all -> 0x0017 }
        if (r1 == 0) goto L_0x001a;
    L_0x0009:
        r1 = r5.mExitSecureCallback;	 Catch:{ all -> 0x0017 }
        if (r1 == 0) goto L_0x000f;
    L_0x000d:
        monitor-exit(r5);	 Catch:{ all -> 0x0017 }
    L_0x000e:
        return;
    L_0x000f:
        r1 = 1;
        r5.mNeedToReshowWhenReenabled = r1;	 Catch:{ all -> 0x0017 }
        r5.hideLocked();	 Catch:{ all -> 0x0017 }
    L_0x0015:
        monitor-exit(r5);	 Catch:{ all -> 0x0017 }
        goto L_0x000e;
    L_0x0017:
        r1 = move-exception;
        monitor-exit(r5);	 Catch:{ all -> 0x0017 }
        throw r1;
    L_0x001a:
        if (r6 == 0) goto L_0x0015;
    L_0x001c:
        r1 = r5.mNeedToReshowWhenReenabled;	 Catch:{ all -> 0x0017 }
        if (r1 == 0) goto L_0x0015;
    L_0x0020:
        r1 = 0;
        r5.mNeedToReshowWhenReenabled = r1;	 Catch:{ all -> 0x0017 }
        r1 = r5.mExitSecureCallback;	 Catch:{ all -> 0x0017 }
        if (r1 == 0) goto L_0x003e;
    L_0x0027:
        r1 = r5.mExitSecureCallback;	 Catch:{ RemoteException -> 0x0035 }
        r2 = 0;
        r1.onKeyguardExitResult(r2);	 Catch:{ RemoteException -> 0x0035 }
    L_0x002d:
        r1 = 0;
        r5.mExitSecureCallback = r1;	 Catch:{ all -> 0x0017 }
        r1 = 0;
        r5.resetStateLocked(r1);	 Catch:{ all -> 0x0017 }
        goto L_0x0015;
    L_0x0035:
        r0 = move-exception;
        r1 = "KeyguardViewMediator";
        r2 = "Failed to call onKeyguardExitResult(false)";
        android.util.Slog.w(r1, r2, r0);	 Catch:{ all -> 0x0017 }
        goto L_0x002d;
    L_0x003e:
        r1 = 0;
        r5.showLocked(r1);	 Catch:{ all -> 0x0017 }
        r1 = 1;
        r5.mWaitingUntilKeyguardVisible = r1;	 Catch:{ all -> 0x0017 }
        r1 = r5.mHandler;	 Catch:{ all -> 0x0017 }
        r2 = 10;
        r3 = 2000; // 0x7d0 float:2.803E-42 double:9.88E-321;
        r1.sendEmptyMessageDelayed(r2, r3);	 Catch:{ all -> 0x0017 }
    L_0x004e:
        r1 = r5.mWaitingUntilKeyguardVisible;	 Catch:{ all -> 0x0017 }
        if (r1 == 0) goto L_0x0015;
    L_0x0052:
        r5.wait();	 Catch:{ InterruptedException -> 0x0056 }
        goto L_0x004e;
    L_0x0056:
        r0 = move-exception;
        r1 = java.lang.Thread.currentThread();	 Catch:{ all -> 0x0017 }
        r1.interrupt();	 Catch:{ all -> 0x0017 }
        goto L_0x004e;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.cyngn.keyguard.KeyguardViewMediator.setKeyguardEnabled(boolean):void");
    }

    public void verifyUnlock(IKeyguardExitCallback callback) {
        synchronized (this) {
            if (!this.mUpdateMonitor.isDeviceProvisioned()) {
                try {
                    callback.onKeyguardExitResult(DEBUG);
                } catch (RemoteException e) {
                    Slog.w(TAG, "Failed to call onKeyguardExitResult(false)", e);
                }
            } else if (this.mExternallyEnabled) {
                Log.w(TAG, "verifyUnlock called when not externally disabled");
                try {
                    callback.onKeyguardExitResult(DEBUG);
                } catch (RemoteException e2) {
                    Slog.w(TAG, "Failed to call onKeyguardExitResult(false)", e2);
                }
            } else if (this.mExitSecureCallback != null) {
                try {
                    callback.onKeyguardExitResult(DEBUG);
                } catch (RemoteException e22) {
                    Slog.w(TAG, "Failed to call onKeyguardExitResult(false)", e22);
                }
            } else {
                this.mExitSecureCallback = callback;
                verifyUnlockLocked();
            }
        }
    }

    public boolean isShowing() {
        return this.mShowing;
    }

    public boolean isShowingAndNotHidden() {
        return (!this.mShowing || this.mHidden) ? DEBUG : ENABLE_INSECURE_STATUS_BAR_EXPAND;
    }

    public void setHidden(boolean isHidden) {
        int i = 1;
        this.mUpdateMonitor.sendKeyguardVisibilityChanged(!isHidden ? ENABLE_INSECURE_STATUS_BAR_EXPAND : DEBUG);
        this.mHandler.removeMessages(SET_HIDDEN);
        Handler handler = this.mHandler;
        if (!isHidden) {
            i = 0;
        }
        this.mHandler.sendMessage(handler.obtainMessage(SET_HIDDEN, i, 0));
    }

    private void handleSetHidden(boolean isHidden) {
        synchronized (this) {
            if (this.mHidden != isHidden) {
                this.mHidden = isHidden;
                updateActivityLockScreenState();
                adjustStatusBarLocked();
            }
        }
    }

    public void doKeyguardTimeout(Bundle options) {
        this.mHandler.removeMessages(KEYGUARD_TIMEOUT);
        this.mHandler.sendMessage(this.mHandler.obtainMessage(KEYGUARD_TIMEOUT, options));
    }

    public boolean isInputRestricted() {
        return (this.mShowing || this.mNeedToReshowWhenReenabled || !this.mUpdateMonitor.isDeviceProvisioned()) ? ENABLE_INSECURE_STATUS_BAR_EXPAND : DEBUG;
    }

    private void doKeyguardLocked(Bundle options) {
        if (!this.mKeyguardViewManager.isShowing()) {
            boolean provisioned = this.mUpdateMonitor.isDeviceProvisioned();
            int numPhones = MSimTelephonyManager.getDefault().getPhoneCount();
            State[] state = new State[numPhones];
            boolean lockedOrMissing = DEBUG;
            for (int i = 0; i < numPhones; i++) {
                state[i] = this.mUpdateMonitor.getSimState(i);
                lockedOrMissing = (lockedOrMissing || isLockedOrMissing(state[i])) ? ENABLE_INSECURE_STATUS_BAR_EXPAND : DEBUG;
                if (lockedOrMissing) {
                    break;
                }
            }
            if (!lockedOrMissing && !provisioned) {
                return;
            }
            if (!isKeyguardDisabled() || lockedOrMissing) {
                showLocked(options);
            }
        }
    }

    boolean isLockedOrMissing(State state) {
        return (state.isPinLocked() || ((state == State.ABSENT || state == State.PERM_DISABLED) && (!SystemProperties.getBoolean("keyguard.no_require_sim", DEBUG) ? ENABLE_INSECURE_STATUS_BAR_EXPAND : DEBUG))) ? ENABLE_INSECURE_STATUS_BAR_EXPAND : DEBUG;
    }

    public void handleDismiss() {
        if (this.mShowing && !this.mHidden) {
            this.mKeyguardViewManager.dismiss();
        }
    }

    public void dismiss() {
        this.mHandler.sendEmptyMessage(DISMISS);
    }

    private void resetStateLocked(Bundle options) {
        this.mHandler.sendMessage(this.mHandler.obtainMessage(RESET, options));
    }

    private void verifyUnlockLocked() {
        this.mHandler.sendEmptyMessage(VERIFY_UNLOCK);
    }

    private void notifyScreenOffLocked() {
        this.mHandler.sendEmptyMessage(NOTIFY_SCREEN_OFF);
    }

    private void notifyScreenOnLocked(IKeyguardShowCallback result) {
        this.mHandler.sendMessage(this.mHandler.obtainMessage(NOTIFY_SCREEN_ON, result));
    }

    private void showLocked(Bundle options) {
        this.mShowKeyguardWakeLock.acquire();
        this.mHandler.sendMessage(this.mHandler.obtainMessage(SHOW, options));
    }

    private void hideLocked() {
        this.mHandler.sendMessage(this.mHandler.obtainMessage(HIDE));
    }

    public boolean isSecure() {
        return (this.mLockPatternUtils.isSecure() || KeyguardUpdateMonitor.getInstance(this.mContext).isSimPinSecure()) ? ENABLE_INSECURE_STATUS_BAR_EXPAND : DEBUG;
    }

    public void setCurrentUser(int newUserId) {
        this.mLockPatternUtils.setCurrentUser(newUserId);
    }

    public void keyguardDone(boolean authenticated, boolean wakeup) {
        int i;
        int i2 = 1;
        EventLog.writeEvent(70000, SHOW);
        synchronized (this) {
            this.mKeyguardDonePending = DEBUG;
        }
        Handler handler = this.mHandler;
        if (authenticated) {
            i = 1;
        } else {
            i = 0;
        }
        if (!wakeup) {
            i2 = 0;
        }
        this.mHandler.sendMessage(handler.obtainMessage(KEYGUARD_DONE, i, i2));
    }

    private void handleKeyguardDone(boolean authenticated, boolean wakeup) {
        if (authenticated) {
            this.mUpdateMonitor.clearFailedUnlockAttempts();
        }
        if (this.mExitSecureCallback != null) {
            try {
                this.mExitSecureCallback.onKeyguardExitResult(authenticated);
            } catch (RemoteException e) {
                Slog.w(TAG, "Failed to call onKeyguardExitResult(" + authenticated + ")", e);
            }
            this.mExitSecureCallback = null;
            if (authenticated) {
                this.mExternallyEnabled = ENABLE_INSECURE_STATUS_BAR_EXPAND;
                this.mNeedToReshowWhenReenabled = DEBUG;
            }
        }
        handleHide();
        sendUserPresentBroadcast();
    }

    protected void handleLaunchCamera() {
        this.mKeyguardViewManager.launchCamera();
    }

    protected void handleLaunchApplicationWidget() {
        this.mKeyguardViewManager.launchApplicationWidget();
    }

    protected void handleDispatchCameraEvent(MotionEvent event) {
        this.mKeyguardViewManager.dispatchCameraEvent(event);
    }

    protected void handleDispatchApplicationWidgetEvent(MotionEvent event) {
        this.mKeyguardViewManager.dispatchApplicationWidgetEvent(event);
    }

    private void sendUserPresentBroadcast() {
        this.mContext.sendBroadcastAsUser(USER_PRESENT_INTENT, new UserHandle(this.mLockPatternUtils.getCurrentUser()));
    }

    private void handleKeyguardDoneDrawing() {
        synchronized (this) {
            if (this.mWaitingUntilKeyguardVisible) {
                this.mWaitingUntilKeyguardVisible = DEBUG;
                notifyAll();
                this.mHandler.removeMessages(KEYGUARD_DONE_DRAWING);
            }
        }
    }

    private void playSounds(boolean locked) {
        if (this.mSuppressNextLockSound) {
            this.mSuppressNextLockSound = DEBUG;
        } else if (!QuietHoursUtils.inQuietHours(this.mContext, "quiet_hours_system") && System.getInt(this.mContext.getContentResolver(), "lockscreen_sounds_enabled", 1) == 1) {
            int whichSound = locked ? this.mLockSoundId : this.mUnlockSoundId;
            this.mLockSounds.stop(this.mLockSoundStreamId);
            if (this.mAudioManager == null) {
                this.mAudioManager = (AudioManager) this.mContext.getSystemService("audio");
                if (this.mAudioManager != null) {
                    this.mMasterStreamType = this.mAudioManager.getMasterStreamType();
                } else {
                    return;
                }
            }
            if (!this.mAudioManager.isStreamMute(this.mMasterStreamType) && !this.mAudioManager.isMusicActive()) {
                TelephonyManager tm = (TelephonyManager) this.mContext.getSystemService("phone");
                if (tm != null) {
                    try {
                        if (tm.isOffhook() || tm.isRinging()) {
                            return;
                        }
                    } catch (Exception e) {
                    }
                }
                this.mLockSoundStreamId = this.mLockSounds.play(whichSound, this.mLockSoundVolume, this.mLockSoundVolume, 1, 0, 1.0f);
            }
        }
    }

    private void updateActivityLockScreenState() {
        try {
            IActivityManager iActivityManager = ActivityManagerNative.getDefault();
            boolean z = (!this.mShowing || this.mHidden) ? DEBUG : ENABLE_INSECURE_STATUS_BAR_EXPAND;
            iActivityManager.setLockScreenShown(z);
        } catch (RemoteException e) {
        }
    }

    private void handleShow(Bundle options) {
        synchronized (this) {
            if (this.mSystemReady) {
                new Thread(new Runnable() {
                    public void run() {
                        KeyguardViewMediator.this.playSounds(KeyguardViewMediator.ENABLE_INSECURE_STATUS_BAR_EXPAND);
                    }
                }).start();
                this.mKeyguardViewManager.show(options);
                this.mShowing = ENABLE_INSECURE_STATUS_BAR_EXPAND;
                this.mKeyguardDonePending = DEBUG;
                updateActivityLockScreenState();
                adjustStatusBarLocked();
                userActivity();
                try {
                    ActivityManagerNative.getDefault().closeSystemDialogs("lock");
                } catch (RemoteException e) {
                }
                this.mShowKeyguardWakeLock.release();
                this.mKeyguardDisplayManager.show();
                return;
            }
        }
    }

    private void handleHide() {
        synchronized (this) {
            if (TelephonyManager.EXTRA_STATE_IDLE.equals(this.mPhoneState)) {
                playSounds(DEBUG);
            }
            this.mKeyguardViewManager.hide();
            this.mShowing = DEBUG;
            this.mKeyguardDonePending = DEBUG;
            updateActivityLockScreenState();
            adjustStatusBarLocked();
        }
    }

    private void adjustStatusBarLocked() {
        if (this.mStatusBarManager == null) {
            this.mStatusBarManager = (StatusBarManager) this.mContext.getSystemService("statusbar");
        }
        if (this.mStatusBarManager == null) {
            Log.w(TAG, "Could not get status bar manager");
            return;
        }
        int flags = 0;
        if (this.mShowing) {
            flags = 0 | 16777216;
            if (isSecure()) {
                flags |= 65536;
            }
            if (isSecure()) {
                flags |= 524288;
            }
            if (!isAssistantAvailable()) {
                flags |= 33554432;
            }
            if (!KeyguardUpdateMonitor.getInstance(this.mContext).isClockVisible()) {
                flags |= 8388608;
            }
        }
        if (!(this.mContext instanceof Activity)) {
            this.mStatusBarManager.disable(flags);
        }
    }

    private void handleReset(Bundle options) {
        if (options == null) {
            options = new Bundle();
        }
        options.putBoolean(KeyguardViewManager.IS_SWITCHING_USER, this.mSwitchingUser);
        synchronized (this) {
            this.mKeyguardViewManager.reset(options);
        }
    }

    private void handleVerifyUnlock() {
        synchronized (this) {
            this.mKeyguardViewManager.verifyUnlock();
            this.mShowing = ENABLE_INSECURE_STATUS_BAR_EXPAND;
            updateActivityLockScreenState();
        }
    }

    private void handleNotifyScreenOff() {
        synchronized (this) {
            this.mKeyguardViewManager.onScreenTurnedOff();
        }
    }

    private void handleNotifyScreenOn(IKeyguardShowCallback callback) {
        checkProfileMode();
        synchronized (this) {
            this.mKeyguardViewManager.onScreenTurnedOn(callback);
        }
    }

    public boolean isDismissable() {
        return (this.mKeyguardDonePending || !isSecure()) ? ENABLE_INSECURE_STATUS_BAR_EXPAND : DEBUG;
    }

    public void showAssistant() {
        this.mHandler.sendMessage(this.mHandler.obtainMessage(SHOW_ASSISTANT));
    }

    public void handleShowAssistant() {
        this.mKeyguardViewManager.showAssistant();
    }

    private boolean isAssistantAvailable() {
        return (this.mSearchManager == null || this.mSearchManager.getAssistIntent(this.mContext, DEBUG, -2) == null) ? DEBUG : ENABLE_INSECURE_STATUS_BAR_EXPAND;
    }

    public static MultiUserAvatarCache getAvatarCache() {
        return sMultiUserAvatarCache;
    }

    public void dispatchCameraEvent(MotionEvent event) {
        this.mHandler.sendMessage(this.mHandler.obtainMessage(DISPATCH_CAMERA_EVENT, event));
    }

    public void dispatchApplicationWidgetEvent(MotionEvent event) {
        this.mHandler.sendMessage(this.mHandler.obtainMessage(DISPATCH_APPLICATION_WIDGET_EVENT, event));
    }

    public void launchCamera() {
        this.mHandler.sendMessage(this.mHandler.obtainMessage(LAUNCH_CAMERA));
    }

    public void launchApplicationWidget() {
        this.mHandler.sendMessage(this.mHandler.obtainMessage(LAUNCH_APPLICATION_WIDGET));
    }

    public void onBootCompleted() {
        checkProfileMode();
        this.mUpdateMonitor.dispatchBootCompleted();
    }

    private void checkProfileMode() {
        int newMode = this.mLockPatternUtils.getActiveProfileLockMode();
        if (this.mLastProfileMode != newMode) {
            if (newMode == SHOW) {
                hideLocked();
            } else {
                resetStateLocked(null);
            }
            this.mLastProfileMode = newMode;
        }
    }
}
