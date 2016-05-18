package com.cyngn.keyguard;

import android.app.ActivityManagerNative;
import android.app.IUserSwitchObserver;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.IRemoteControlDisplay.Stub;
import android.os.Bundle;
import android.os.Handler;
import android.os.IRemoteCallback;
import android.os.Message;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.Settings.Global;
import android.provider.Settings.System;
import android.telephony.MSimTelephonyManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.util.Pair;
import com.android.internal.telephony.IccCardConstants.State;
import com.cyngn.keyguard.SlidingChallengeLayout.LayoutParams;
import com.google.android.collect.Lists;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class KeyguardUpdateMonitor {
    private static final boolean DEBUG = false;
    private static final boolean DEBUG_SIM_STATES = false;
    private static final int FAILED_BIOMETRIC_UNLOCK_ATTEMPTS_BEFORE_BACKUP = 3;
    private static final int LOW_BATTERY_THRESHOLD = 20;
    private static final int MSG_APPLICATION_WIDGET_UPDATED = 322;
    private static final int MSG_BATTERY_UPDATE = 302;
    protected static final int MSG_BOOT_COMPLETED = 313;
    private static final int MSG_CARRIER_INFO_UPDATE = 303;
    private static final int MSG_CLOCK_VISIBILITY_CHANGED = 307;
    private static final int MSG_DEVICE_PROVISIONED = 308;
    private static final int MSG_DPM_STATE_CHANGED = 309;
    private static final int MSG_KEYGUARD_VISIBILITY_CHANGED = 312;
    private static final int MSG_LID_STATE_CHANGED = 321;
    private static final int MSG_PHONE_STATE_CHANGED = 306;
    protected static final int MSG_REPORT_EMERGENCY_CALL_ACTION = 318;
    private static final int MSG_RINGER_MODE_CHANGED = 305;
    private static final int MSG_SCREEN_TURNED_OFF = 320;
    private static final int MSG_SCREEN_TURNED_ON = 319;
    private static final int MSG_SET_CURRENT_CLIENT_ID = 315;
    protected static final int MSG_SET_PLAYBACK_STATE = 316;
    private static final int MSG_SIM_STATE_CHANGE = 304;
    private static final int MSG_TIME_UPDATE = 301;
    protected static final int MSG_USER_INFO_CHANGED = 317;
    private static final int MSG_USER_REMOVED = 311;
    private static final int MSG_USER_SWITCHING = 310;
    private static final int MSG_USER_SWITCH_COMPLETE = 314;
    private static final String TAG = "KeyguardUpdateMonitor";
    private static KeyguardUpdateMonitor sInstance;
    public static final boolean sIsMultiSimEnabled = MSimTelephonyManager.getDefault().isMultiSimEnabled();
    private boolean mAlternateUnlockEnabled;
    private byte[] mApplicationWidgetIcon;
    private final Object mApplicationWidgetLock = new Object();
    private String mApplicationWidgetPackageName;
    private final BroadcastReceiver mApplicationWidgetPackageReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if ("android.intent.action.PACKAGE_FULLY_REMOVED".equals(action)) {
                if (intent.getData().getSchemeSpecificPart().equals(KeyguardUpdateMonitor.this.mApplicationWidgetSetterPackage)) {
                    KeyguardUpdateMonitor.this.mContext.sendBroadcast(new Intent("android.intent.action.UNSET_KEYGUARD_APPLICATION_WIDGET_ACTION"), "android.permission.SET_KEYGUARD_APPLICATION_WIDGET");
                }
            } else if ("android.intent.action.PACKAGE_REMOVED".equals(action) && intent.getData().getSchemeSpecificPart().equals(KeyguardUpdateMonitor.this.mApplicationWidgetSetterPackage) && !intent.getBooleanExtra("android.intent.extra.REPLACING", KeyguardUpdateMonitor.DEBUG_SIM_STATES)) {
                KeyguardUpdateMonitor.this.mContext.sendBroadcast(new Intent("android.intent.action.UNSET_KEYGUARD_APPLICATION_WIDGET_ACTION"), "android.permission.SET_KEYGUARD_APPLICATION_WIDGET");
            }
        }
    };
    private String mApplicationWidgetSetterPackage;
    private AudioManager mAudioManager;
    private BatteryStatus mBatteryStatus;
    private boolean mBootCompleted;
    private final BroadcastReceiver mBroadcastAllReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if ("android.intent.action.USER_INFO_CHANGED".equals(intent.getAction())) {
                KeyguardUpdateMonitor.this.mHandler.sendMessage(KeyguardUpdateMonitor.this.mHandler.obtainMessage(KeyguardUpdateMonitor.MSG_USER_INFO_CHANGED, intent.getIntExtra("android.intent.extra.user_handle", getSendingUserId()), 0));
            }
        }
    };
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if ("android.intent.action.TIME_TICK".equals(action) || "android.intent.action.TIME_SET".equals(action) || "android.intent.action.TIMEZONE_CHANGED".equals(action)) {
                KeyguardUpdateMonitor.this.mHandler.sendEmptyMessage(KeyguardUpdateMonitor.MSG_TIME_UPDATE);
            } else if ("android.provider.Telephony.SPN_STRINGS_UPDATED".equals(action)) {
                int subscription = intent.getIntExtra("subscription", 0);
                Log.d(KeyguardUpdateMonitor.TAG, "Received SPN update on sub :" + subscription);
                KeyguardUpdateMonitor.this.mTelephonyPlmn[subscription] = KeyguardUpdateMonitor.this.getTelephonyPlmnFrom(intent);
                KeyguardUpdateMonitor.this.mTelephonySpn[subscription] = KeyguardUpdateMonitor.this.getTelephonySpnFrom(intent);
                Message msg = KeyguardUpdateMonitor.this.mHandler.obtainMessage(KeyguardUpdateMonitor.MSG_CARRIER_INFO_UPDATE);
                msg.arg1 = subscription;
                KeyguardUpdateMonitor.this.mHandler.sendMessage(msg);
            } else if ("android.intent.action.BATTERY_CHANGED".equals(action)) {
                int status = intent.getIntExtra("status", 1);
                int plugged = intent.getIntExtra("plugged", 0);
                KeyguardUpdateMonitor.this.mHandler.sendMessage(KeyguardUpdateMonitor.this.mHandler.obtainMessage(KeyguardUpdateMonitor.MSG_BATTERY_UPDATE, new BatteryStatus(status, intent.getIntExtra("level", 0), plugged, intent.getIntExtra("health", 1))));
            } else if ("android.intent.action.SIM_STATE_CHANGED".equals(action)) {
                KeyguardUpdateMonitor.this.mHandler.sendMessage(KeyguardUpdateMonitor.this.mHandler.obtainMessage(KeyguardUpdateMonitor.MSG_SIM_STATE_CHANGE, SimArgs.fromIntent(intent)));
            } else if ("android.media.RINGER_MODE_CHANGED".equals(action)) {
                KeyguardUpdateMonitor.this.mHandler.sendMessage(KeyguardUpdateMonitor.this.mHandler.obtainMessage(KeyguardUpdateMonitor.MSG_RINGER_MODE_CHANGED, intent.getIntExtra("android.media.EXTRA_RINGER_MODE", -1), 0));
            } else if ("android.intent.action.PHONE_STATE".equals(action)) {
                KeyguardUpdateMonitor.this.mHandler.sendMessage(KeyguardUpdateMonitor.this.mHandler.obtainMessage(KeyguardUpdateMonitor.MSG_PHONE_STATE_CHANGED, intent.getStringExtra(KeyguardHostViewMod.PREFS_FILE)));
            } else if ("android.app.action.DEVICE_POLICY_MANAGER_STATE_CHANGED".equals(action)) {
                KeyguardUpdateMonitor.this.mHandler.sendEmptyMessage(KeyguardUpdateMonitor.MSG_DPM_STATE_CHANGED);
            } else if ("android.intent.action.USER_REMOVED".equals(action)) {
                KeyguardUpdateMonitor.this.mHandler.sendMessage(KeyguardUpdateMonitor.this.mHandler.obtainMessage(KeyguardUpdateMonitor.MSG_USER_REMOVED, intent.getIntExtra("android.intent.extra.user_handle", 0), 0));
            } else if ("android.intent.action.BOOT_COMPLETED".equals(action)) {
                KeyguardUpdateMonitor.this.dispatchBootCompleted();
            }
        }
    };
    private final ArrayList<WeakReference<KeyguardUpdateMonitorCallback>> mCallbacks = Lists.newArrayList();
    private boolean mClockVisible;
    private final Context mContext;
    private boolean mDeviceProvisioned;
    private ContentObserver mDeviceProvisionedObserver;
    private DisplayClientState mDisplayClientState = new DisplayClientState();
    private int mFailedAttempts = 0;
    private int mFailedBiometricUnlockAttempts = 0;
    private final Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case KeyguardUpdateMonitor.MSG_TIME_UPDATE /*301*/:
                    KeyguardUpdateMonitor.this.handleTimeUpdate();
                    return;
                case KeyguardUpdateMonitor.MSG_BATTERY_UPDATE /*302*/:
                    KeyguardUpdateMonitor.this.handleBatteryUpdate((BatteryStatus) msg.obj);
                    return;
                case KeyguardUpdateMonitor.MSG_CARRIER_INFO_UPDATE /*303*/:
                    KeyguardUpdateMonitor.this.handleCarrierInfoUpdate(msg.arg1);
                    return;
                case KeyguardUpdateMonitor.MSG_SIM_STATE_CHANGE /*304*/:
                    KeyguardUpdateMonitor.this.handleSimStateChange((SimArgs) msg.obj);
                    return;
                case KeyguardUpdateMonitor.MSG_RINGER_MODE_CHANGED /*305*/:
                    KeyguardUpdateMonitor.this.handleRingerModeChange(msg.arg1);
                    return;
                case KeyguardUpdateMonitor.MSG_PHONE_STATE_CHANGED /*306*/:
                    KeyguardUpdateMonitor.this.handlePhoneStateChanged((String) msg.obj);
                    return;
                case KeyguardUpdateMonitor.MSG_CLOCK_VISIBILITY_CHANGED /*307*/:
                    KeyguardUpdateMonitor.this.handleClockVisibilityChanged();
                    return;
                case KeyguardUpdateMonitor.MSG_DEVICE_PROVISIONED /*308*/:
                    KeyguardUpdateMonitor.this.handleDeviceProvisioned();
                    return;
                case KeyguardUpdateMonitor.MSG_DPM_STATE_CHANGED /*309*/:
                    KeyguardUpdateMonitor.this.handleDevicePolicyManagerStateChanged();
                    return;
                case KeyguardUpdateMonitor.MSG_USER_SWITCHING /*310*/:
                    KeyguardUpdateMonitor.this.handleUserSwitching(msg.arg1, (IRemoteCallback) msg.obj);
                    return;
                case KeyguardUpdateMonitor.MSG_USER_REMOVED /*311*/:
                    KeyguardUpdateMonitor.this.handleUserRemoved(msg.arg1);
                    return;
                case KeyguardUpdateMonitor.MSG_KEYGUARD_VISIBILITY_CHANGED /*312*/:
                    KeyguardUpdateMonitor.this.handleKeyguardVisibilityChanged(msg.arg1);
                    return;
                case KeyguardUpdateMonitor.MSG_BOOT_COMPLETED /*313*/:
                    KeyguardUpdateMonitor.this.handleBootCompleted();
                    return;
                case KeyguardUpdateMonitor.MSG_USER_SWITCH_COMPLETE /*314*/:
                    KeyguardUpdateMonitor.this.handleUserSwitchComplete(msg.arg1);
                    return;
                case KeyguardUpdateMonitor.MSG_SET_CURRENT_CLIENT_ID /*315*/:
                    KeyguardUpdateMonitor.this.handleSetGenerationId(msg.arg1, msg.arg2 != 0 ? true : KeyguardUpdateMonitor.DEBUG_SIM_STATES, (PendingIntent) msg.obj);
                    return;
                case KeyguardUpdateMonitor.MSG_SET_PLAYBACK_STATE /*316*/:
                    KeyguardUpdateMonitor.this.handleSetPlaybackState(msg.arg1, msg.arg2, ((Long) msg.obj).longValue());
                    return;
                case KeyguardUpdateMonitor.MSG_USER_INFO_CHANGED /*317*/:
                    KeyguardUpdateMonitor.this.handleUserInfoChanged(msg.arg1);
                    return;
                case KeyguardUpdateMonitor.MSG_REPORT_EMERGENCY_CALL_ACTION /*318*/:
                    KeyguardUpdateMonitor.this.handleReportEmergencyCallAction();
                    return;
                case KeyguardUpdateMonitor.MSG_SCREEN_TURNED_ON /*319*/:
                    KeyguardUpdateMonitor.this.handleScreenTurnedOn();
                    return;
                case KeyguardUpdateMonitor.MSG_SCREEN_TURNED_OFF /*320*/:
                    KeyguardUpdateMonitor.this.handleScreenTurnedOff(msg.arg1);
                    return;
                case KeyguardUpdateMonitor.MSG_LID_STATE_CHANGED /*321*/:
                    KeyguardUpdateMonitor.this.handleLidStateChanged(msg.arg1);
                    return;
                case KeyguardUpdateMonitor.MSG_APPLICATION_WIDGET_UPDATED /*322*/:
                    KeyguardUpdateMonitor.this.handleApplicationWidgetUpdated();
                    return;
                default:
                    return;
            }
        }
    };
    private boolean mKeyguardIsVisible;
    private int mPhoneState;
    private final Stub mRemoteControlDisplay = new Stub() {
        public void setPlaybackState(int generationId, int state, long stateChangeTimeMs, long currentPosMs, float speed) {
            KeyguardUpdateMonitor.this.mHandler.sendMessage(KeyguardUpdateMonitor.this.mHandler.obtainMessage(KeyguardUpdateMonitor.MSG_SET_PLAYBACK_STATE, generationId, state, Long.valueOf(stateChangeTimeMs)));
        }

        public void setMetadata(int generationId, Bundle metadata) {
        }

        public void setTransportControlInfo(int generationId, int flags, int posCapabilities) {
        }

        public void setArtwork(int generationId, Bitmap bitmap) {
        }

        public void setAllMetadata(int generationId, Bundle metadata, Bitmap bitmap) {
        }

        public void setEnabled(boolean enabled) {
        }

        public void setCurrentClientId(int clientGeneration, PendingIntent mediaIntent, boolean clearing) throws RemoteException {
            KeyguardUpdateMonitor.this.mHandler.sendMessage(KeyguardUpdateMonitor.this.mHandler.obtainMessage(KeyguardUpdateMonitor.MSG_SET_CURRENT_CLIENT_ID, clientGeneration, clearing ? 1 : 0, mediaIntent));
        }
    };
    private int mRingMode;
    private boolean mScreenOn;
    private State[] mSimState;
    private boolean mSwitchingUser;
    private CharSequence[] mTelephonyPlmn;
    private CharSequence[] mTelephonySpn;

    static class BatteryStatus {
        public final int health;
        public final int level;
        public final int plugged;
        public final int status;

        public BatteryStatus(int status, int level, int plugged, int health) {
            this.status = status;
            this.level = level;
            this.plugged = plugged;
            this.health = health;
        }

        boolean isPluggedIn() {
            return (this.plugged == 1 || this.plugged == 2 || this.plugged == 4) ? true : KeyguardUpdateMonitor.DEBUG_SIM_STATES;
        }

        public boolean isCharged() {
            return (this.status == 5 || this.level >= 100) ? true : KeyguardUpdateMonitor.DEBUG_SIM_STATES;
        }

        public boolean isBatteryLow() {
            return this.level < KeyguardUpdateMonitor.LOW_BATTERY_THRESHOLD ? true : KeyguardUpdateMonitor.DEBUG_SIM_STATES;
        }
    }

    static class DisplayClientState {
        public boolean clearing;
        public int clientGeneration;
        public PendingIntent intent;
        public long playbackEventTime;
        public int playbackState;

        DisplayClientState() {
        }
    }

    private static class SimArgs {
        public final State simState;
        public int subscription;

        SimArgs(State state, int sub) {
            this.simState = state;
            this.subscription = sub;
        }

        static SimArgs fromIntent(Intent intent) {
            if ("android.intent.action.SIM_STATE_CHANGED".equals(intent.getAction())) {
                State state;
                int subscription = intent.getIntExtra("subscription", 0);
                Log.d(KeyguardUpdateMonitor.TAG, "ACTION_SIM_STATE_CHANGED intent received on sub = " + subscription);
                String stateExtra = intent.getStringExtra("ss");
                if ("ABSENT".equals(stateExtra)) {
                    if ("PERM_DISABLED".equals(intent.getStringExtra("reason"))) {
                        state = State.PERM_DISABLED;
                    } else {
                        state = State.ABSENT;
                    }
                } else if ("READY".equals(stateExtra)) {
                    state = State.READY;
                } else if ("LOCKED".equals(stateExtra)) {
                    String lockedReason = intent.getStringExtra("reason");
                    if ("PIN".equals(lockedReason)) {
                        state = State.PIN_REQUIRED;
                    } else if ("PUK".equals(lockedReason)) {
                        state = State.PUK_REQUIRED;
                    } else if ("PERSO".equals(stateExtra)) {
                        state = State.PERSO_LOCKED;
                    } else {
                        state = State.UNKNOWN;
                    }
                } else if ("CARD_IO_ERROR".equals(stateExtra)) {
                    state = State.CARD_IO_ERROR;
                } else if ("LOADED".equals(stateExtra) || "IMSI".equals(stateExtra)) {
                    state = State.READY;
                } else {
                    state = State.UNKNOWN;
                }
                return new SimArgs(state, subscription);
            }
            throw new IllegalArgumentException("only handles intent ACTION_SIM_STATE_CHANGED");
        }

        public String toString() {
            return this.simState.toString();
        }
    }

    public static KeyguardUpdateMonitor getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new KeyguardUpdateMonitor(context);
        }
        return sInstance;
    }

    protected void handleLidStateChanged(int arg1) {
        int count = this.mCallbacks.size();
        for (int i = 0; i < count; i++) {
            KeyguardUpdateMonitorCallback cb = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i)).get();
            if (cb != null) {
                cb.onLidStateChanged(arg1);
            }
        }
    }

    protected void handleApplicationWidgetUpdated() {
        int count = this.mCallbacks.size();
        synchronized (this.mApplicationWidgetLock) {
            for (int i = 0; i < count; i++) {
                KeyguardUpdateMonitorCallback cb = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i)).get();
                if (cb != null) {
                    cb.onApplicationWidgetUpdated(this.mApplicationWidgetPackageName, this.mApplicationWidgetIcon);
                }
            }
        }
    }

    protected void handleScreenTurnedOn() {
        int count = this.mCallbacks.size();
        for (int i = 0; i < count; i++) {
            KeyguardUpdateMonitorCallback cb = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i)).get();
            if (cb != null) {
                cb.onScreenTurnedOn();
            }
        }
    }

    protected void handleScreenTurnedOff(int arg1) {
        int count = this.mCallbacks.size();
        for (int i = 0; i < count; i++) {
            KeyguardUpdateMonitorCallback cb = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i)).get();
            if (cb != null) {
                cb.onScreenTurnedOff(arg1);
            }
        }
    }

    public void dispatchSetBackground(Bitmap bmp) {
        int count = this.mCallbacks.size();
        for (int i = 0; i < count; i++) {
            KeyguardUpdateMonitorCallback cb = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i)).get();
            if (cb != null) {
                cb.onSetBackground(bmp);
            }
        }
    }

    protected void handleSetGenerationId(int clientGeneration, boolean clearing, PendingIntent p) {
        this.mDisplayClientState.clientGeneration = clientGeneration;
        this.mDisplayClientState.clearing = clearing;
        this.mDisplayClientState.intent = p;
        for (int i = 0; i < this.mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i)).get();
            if (cb != null) {
                cb.onMusicClientIdChanged(clientGeneration, clearing, p);
            }
        }
    }

    protected void handleSetPlaybackState(int generationId, int playbackState, long eventTime) {
        this.mDisplayClientState.playbackState = playbackState;
        this.mDisplayClientState.playbackEventTime = eventTime;
        if (generationId == this.mDisplayClientState.clientGeneration) {
            for (int i = 0; i < this.mCallbacks.size(); i++) {
                KeyguardUpdateMonitorCallback cb = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i)).get();
                if (cb != null) {
                    cb.onMusicPlaybackStateChanged(playbackState, eventTime);
                }
            }
            return;
        }
        Log.w(TAG, "Ignoring generation id " + generationId + " because it's not current");
    }

    private void handleUserInfoChanged(int userId) {
        for (int i = 0; i < this.mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i)).get();
            if (cb != null) {
                cb.onUserInfoChanged(userId);
            }
        }
    }

    private KeyguardUpdateMonitor(Context context) {
        this.mContext = context;
        this.mDeviceProvisioned = isDeviceProvisionedInSettingsDb();
        if (!this.mDeviceProvisioned) {
            watchForDeviceProvisioning();
        }
        this.mBatteryStatus = new BatteryStatus(1, 100, 0, 0);
        int numPhones = MSimTelephonyManager.getDefault().getPhoneCount();
        this.mTelephonyPlmn = new CharSequence[numPhones];
        this.mTelephonySpn = new CharSequence[numPhones];
        this.mSimState = new State[numPhones];
        for (int i = 0; i < numPhones; i++) {
            this.mTelephonyPlmn[i] = getDefaultPlmn();
            this.mTelephonySpn[i] = null;
            this.mSimState[i] = getSimStateFromSystem(i);
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.intent.action.TIME_TICK");
        filter.addAction("android.intent.action.TIME_SET");
        filter.addAction("android.intent.action.BATTERY_CHANGED");
        filter.addAction("android.intent.action.TIMEZONE_CHANGED");
        filter.addAction("android.intent.action.SIM_STATE_CHANGED");
        filter.addAction("android.intent.action.PHONE_STATE");
        filter.addAction("android.provider.Telephony.SPN_STRINGS_UPDATED");
        filter.addAction("android.media.RINGER_MODE_CHANGED");
        filter.addAction("android.app.action.DEVICE_POLICY_MANAGER_STATE_CHANGED");
        filter.addAction("android.intent.action.USER_REMOVED");
        context.registerReceiver(this.mBroadcastReceiver, filter);
        IntentFilter bootCompleteFilter = new IntentFilter();
        bootCompleteFilter.setPriority(1000);
        bootCompleteFilter.addAction("android.intent.action.BOOT_COMPLETED");
        context.registerReceiver(this.mBroadcastReceiver, bootCompleteFilter);
        Context context2 = context;
        context2.registerReceiverAsUser(this.mBroadcastAllReceiver, UserHandle.ALL, new IntentFilter("android.intent.action.USER_INFO_CHANGED"), null, null);
        IntentFilter applicationWidgetFilter = new IntentFilter();
        applicationWidgetFilter.addAction("android.intent.action.SET_KEYGUARD_APPLICATION_WIDGET_ACTION");
        applicationWidgetFilter.addAction("android.intent.action.UNSET_KEYGUARD_APPLICATION_WIDGET_ACTION");
        context.registerReceiver(new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                synchronized (KeyguardUpdateMonitor.this.mApplicationWidgetLock) {
                    if ("android.intent.action.SET_KEYGUARD_APPLICATION_WIDGET_ACTION".equals(intent.getAction())) {
                        KeyguardUpdateMonitor.this.mApplicationWidgetPackageName = intent.getStringExtra("android.intent.extra.EXTRA_KEYGUARD_APPLICATION_WIDGET_PACKAGE_NAME");
                        KeyguardUpdateMonitor.this.mApplicationWidgetIcon = intent.getByteArrayExtra("android.intent.extra.EXTRA_KEYGUARD_APPLICATION_WIDGET_ICON");
                        if (KeyguardUpdateMonitor.this.mApplicationWidgetSetterPackage == null) {
                            KeyguardUpdateMonitor.this.mApplicationWidgetSetterPackage = "com.nextbit.app";
                            IntentFilter filter = new IntentFilter("android.intent.action.PACKAGE_REMOVED");
                            filter.addAction("android.intent.action.PACKAGE_FULLY_REMOVED");
                            filter.addDataScheme("package");
                            KeyguardUpdateMonitor.this.mContext.registerReceiver(KeyguardUpdateMonitor.this.mApplicationWidgetPackageReceiver, filter);
                        }
                        KeyguardUpdateMonitor.this.mHandler.sendMessage(KeyguardUpdateMonitor.this.mHandler.obtainMessage(KeyguardUpdateMonitor.MSG_APPLICATION_WIDGET_UPDATED));
                    } else if ("android.intent.action.UNSET_KEYGUARD_APPLICATION_WIDGET_ACTION".equals(intent.getAction())) {
                        KeyguardUpdateMonitor.this.mApplicationWidgetPackageName = null;
                        KeyguardUpdateMonitor.this.mApplicationWidgetIcon = null;
                        KeyguardUpdateMonitor.this.mHandler.sendMessage(KeyguardUpdateMonitor.this.mHandler.obtainMessage(KeyguardUpdateMonitor.MSG_APPLICATION_WIDGET_UPDATED));
                        if (KeyguardUpdateMonitor.this.mApplicationWidgetSetterPackage != null) {
                            KeyguardUpdateMonitor.this.mContext.unregisterReceiver(KeyguardUpdateMonitor.this.mApplicationWidgetPackageReceiver);
                            KeyguardUpdateMonitor.this.mApplicationWidgetSetterPackage = null;
                        }
                    }
                }
            }
        }, applicationWidgetFilter, "android.permission.SET_KEYGUARD_APPLICATION_WIDGET", null);
        try {
            ActivityManagerNative.getDefault().registerUserSwitchObserver(new IUserSwitchObserver.Stub() {
                public void onUserSwitching(int newUserId, IRemoteCallback reply) {
                    KeyguardUpdateMonitor.this.mHandler.sendMessage(KeyguardUpdateMonitor.this.mHandler.obtainMessage(KeyguardUpdateMonitor.MSG_USER_SWITCHING, newUserId, 0, reply));
                    KeyguardUpdateMonitor.this.mSwitchingUser = true;
                }

                public void onUserSwitchComplete(int newUserId) throws RemoteException {
                    KeyguardUpdateMonitor.this.mHandler.sendMessage(KeyguardUpdateMonitor.this.mHandler.obtainMessage(KeyguardUpdateMonitor.MSG_USER_SWITCH_COMPLETE, Integer.valueOf(newUserId)));
                    KeyguardUpdateMonitor.this.mSwitchingUser = KeyguardUpdateMonitor.DEBUG_SIM_STATES;
                }
            });
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private boolean isDeviceProvisionedInSettingsDb() {
        return Global.getInt(this.mContext.getContentResolver(), "device_provisioned", 0) != 0 ? true : DEBUG_SIM_STATES;
    }

    private void watchForDeviceProvisioning() {
        this.mDeviceProvisionedObserver = new ContentObserver(this.mHandler) {
            public void onChange(boolean selfChange) {
                super.onChange(selfChange);
                KeyguardUpdateMonitor.this.mDeviceProvisioned = KeyguardUpdateMonitor.this.isDeviceProvisionedInSettingsDb();
                if (KeyguardUpdateMonitor.this.mDeviceProvisioned) {
                    KeyguardUpdateMonitor.this.mHandler.sendEmptyMessage(KeyguardUpdateMonitor.MSG_DEVICE_PROVISIONED);
                }
            }
        };
        this.mContext.getContentResolver().registerContentObserver(Global.getUriFor("device_provisioned"), DEBUG_SIM_STATES, this.mDeviceProvisionedObserver);
        boolean provisioned = isDeviceProvisionedInSettingsDb();
        if (provisioned != this.mDeviceProvisioned) {
            this.mDeviceProvisioned = provisioned;
            if (this.mDeviceProvisioned) {
                this.mHandler.sendEmptyMessage(MSG_DEVICE_PROVISIONED);
            }
        }
    }

    protected void handleDevicePolicyManagerStateChanged() {
        for (int i = this.mCallbacks.size() - 1; i >= 0; i--) {
            KeyguardUpdateMonitorCallback cb = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i)).get();
            if (cb != null) {
                cb.onDevicePolicyManagerStateChanged();
            }
        }
    }

    protected void handleUserSwitching(int userId, IRemoteCallback reply) {
        for (int i = 0; i < this.mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i)).get();
            if (cb != null) {
                cb.onUserSwitching(userId);
            }
        }
        try {
            reply.sendResult(null);
        } catch (RemoteException e) {
        }
    }

    protected void handleUserSwitchComplete(int userId) {
        for (int i = 0; i < this.mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i)).get();
            if (cb != null) {
                cb.onUserSwitchComplete(userId);
            }
        }
    }

    protected void dispatchBootCompleted() {
        this.mHandler.sendEmptyMessage(MSG_BOOT_COMPLETED);
    }

    protected void handleBootCompleted() {
        if (!this.mBootCompleted) {
            this.mBootCompleted = true;
            this.mAudioManager = new AudioManager(this.mContext);
            this.mAudioManager.registerRemoteControlDisplay(this.mRemoteControlDisplay);
            for (int i = 0; i < this.mCallbacks.size(); i++) {
                KeyguardUpdateMonitorCallback cb = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i)).get();
                if (cb != null) {
                    cb.onBootCompleted();
                }
            }
        }
    }

    public boolean hasBootCompleted() {
        return this.mBootCompleted;
    }

    protected void handleUserRemoved(int userId) {
        for (int i = 0; i < this.mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i)).get();
            if (cb != null) {
                cb.onUserRemoved(userId);
            }
        }
    }

    protected void handleDeviceProvisioned() {
        for (int i = 0; i < this.mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i)).get();
            if (cb != null) {
                cb.onDeviceProvisioned();
            }
        }
        if (this.mDeviceProvisionedObserver != null) {
            this.mContext.getContentResolver().unregisterContentObserver(this.mDeviceProvisionedObserver);
            this.mDeviceProvisionedObserver = null;
        }
    }

    protected void handlePhoneStateChanged(String newState) {
        if (TelephonyManager.EXTRA_STATE_IDLE.equals(newState)) {
            this.mPhoneState = 0;
        } else if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(newState)) {
            this.mPhoneState = 2;
        } else if (TelephonyManager.EXTRA_STATE_RINGING.equals(newState)) {
            this.mPhoneState = 1;
        }
        for (int i = 0; i < this.mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i)).get();
            if (cb != null) {
                cb.onPhoneStateChanged(this.mPhoneState);
            }
        }
    }

    protected void handleRingerModeChange(int mode) {
        this.mRingMode = mode;
        for (int i = 0; i < this.mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i)).get();
            if (cb != null) {
                cb.onRingerModeChanged(mode);
            }
        }
    }

    private void handleTimeUpdate() {
        for (int i = 0; i < this.mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i)).get();
            if (cb != null) {
                cb.onTimeChanged();
            }
        }
    }

    private void handleBatteryUpdate(BatteryStatus status) {
        boolean batteryUpdateInteresting = isBatteryUpdateInteresting(this.mBatteryStatus, status, this.mContext);
        this.mBatteryStatus = status;
        if (batteryUpdateInteresting) {
            for (int i = 0; i < this.mCallbacks.size(); i++) {
                KeyguardUpdateMonitorCallback cb = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i)).get();
                if (cb != null) {
                    cb.onRefreshBatteryInfo(status);
                }
            }
        }
    }

    private void handleCarrierInfoUpdate(int subscription) {
        for (int i = 0; i < this.mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i)).get();
            if (cb != null) {
                if (sIsMultiSimEnabled) {
                    cb.onRefreshCarrierInfo(this.mTelephonyPlmn[subscription], this.mTelephonySpn[subscription], subscription);
                } else {
                    cb.onRefreshCarrierInfo(this.mTelephonyPlmn[subscription], this.mTelephonySpn[subscription]);
                }
            }
        }
    }

    private void handleSimStateChange(SimArgs simArgs) {
        State state = simArgs.simState;
        int subscription = simArgs.subscription;
        Log.d(TAG, "handleSimStateChange: intentValue = " + simArgs + " " + "state resolved to " + state.toString() + " " + "subscription =" + subscription);
        if (state != State.UNKNOWN && state != this.mSimState[subscription]) {
            this.mSimState[subscription] = state;
            for (int i = 0; i < this.mCallbacks.size(); i++) {
                KeyguardUpdateMonitorCallback cb = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i)).get();
                if (cb != null) {
                    if (sIsMultiSimEnabled) {
                        cb.onSimStateChanged(state, subscription);
                    } else {
                        cb.onSimStateChanged(state);
                    }
                }
            }
        }
    }

    private void handleClockVisibilityChanged() {
        for (int i = 0; i < this.mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i)).get();
            if (cb != null) {
                cb.onClockVisibilityChanged();
            }
        }
    }

    private void handleKeyguardVisibilityChanged(int showing) {
        boolean isShowing = true;
        if (showing != 1) {
            isShowing = DEBUG_SIM_STATES;
        }
        this.mKeyguardIsVisible = isShowing;
        for (int i = 0; i < this.mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i)).get();
            if (cb != null) {
                cb.onKeyguardVisibilityChangedRaw(isShowing);
            }
        }
    }

    private void handleReportEmergencyCallAction() {
        for (int i = 0; i < this.mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i)).get();
            if (cb != null) {
                cb.onEmergencyCallAction();
            }
        }
    }

    public boolean isKeyguardVisible() {
        return this.mKeyguardIsVisible;
    }

    public boolean isSwitchingUser() {
        return this.mSwitchingUser;
    }

    private static boolean isBatteryUpdateInteresting(BatteryStatus old, BatteryStatus current, Context context) {
        boolean nowPluggedIn = current.isPluggedIn();
        boolean wasPluggedIn = old.isPluggedIn();
        boolean stateChangedWhilePluggedIn = (wasPluggedIn && nowPluggedIn && old.status != current.status) ? true : DEBUG_SIM_STATES;
        if (wasPluggedIn == nowPluggedIn && !stateChangedWhilePluggedIn && old.level == current.level) {
            return ((nowPluggedIn || shouldAlwaysShowBatteryInfo(context) || current.isBatteryLow()) && old.level != current.level) ? true : DEBUG_SIM_STATES;
        } else {
            return true;
        }
    }

    public static boolean shouldAlwaysShowBatteryInfo(Context context) {
        return System.getInt(context.getContentResolver(), "lockscreen_always_show_battery", 0) == 1 ? true : DEBUG_SIM_STATES;
    }

    public static boolean shouldNeverShowBatteryInfo(Context context) {
        return (System.getInt(context.getContentResolver(), "lockscreen_always_show_battery", 0) == 2 || System.getInt(context.getContentResolver(), "lockscreen_modlock_enabled", 1) == 1) ? true : DEBUG_SIM_STATES;
    }

    private CharSequence getTelephonyPlmnFrom(Intent intent) {
        if (!intent.getBooleanExtra("showPlmn", DEBUG_SIM_STATES)) {
            return null;
        }
        String plmn = intent.getStringExtra("plmn");
        if (plmn != null) {
            return plmn;
        }
        return getDefaultPlmn();
    }

    private CharSequence getDefaultPlmn() {
        return this.mContext.getResources().getText(R.string.keyguard_carrier_default);
    }

    private CharSequence getTelephonySpnFrom(Intent intent) {
        if (intent.getBooleanExtra("showSpn", DEBUG_SIM_STATES)) {
            String spn = intent.getStringExtra("spn");
            if (spn != null) {
                return spn;
            }
        }
        return null;
    }

    public void removeCallback(KeyguardUpdateMonitorCallback callback) {
        for (int i = this.mCallbacks.size() - 1; i >= 0; i--) {
            if (((WeakReference) this.mCallbacks.get(i)).get() == callback) {
                this.mCallbacks.remove(i);
            }
        }
    }

    public void registerCallback(KeyguardUpdateMonitorCallback callback) {
        int i = 0;
        while (i < this.mCallbacks.size()) {
            if (((WeakReference) this.mCallbacks.get(i)).get() != callback) {
                i++;
            } else {
                return;
            }
        }
        this.mCallbacks.add(new WeakReference(callback));
        removeCallback(null);
        sendUpdates(callback);
    }

    private void sendUpdates(KeyguardUpdateMonitorCallback callback) {
        callback.onRefreshBatteryInfo(this.mBatteryStatus);
        callback.onTimeChanged();
        callback.onRingerModeChanged(this.mRingMode);
        callback.onPhoneStateChanged(this.mPhoneState);
        callback.onClockVisibilityChanged();
        int subscription = MSimTelephonyManager.getDefault().getDefaultSubscription();
        if (sIsMultiSimEnabled) {
            for (int i = 0; i < MSimTelephonyManager.getDefault().getPhoneCount(); i++) {
                callback.onRefreshCarrierInfo(this.mTelephonyPlmn[i], this.mTelephonySpn[i], i);
                callback.onSimStateChanged(this.mSimState[i], i);
            }
        } else {
            callback.onRefreshCarrierInfo(this.mTelephonyPlmn[subscription], this.mTelephonySpn[subscription]);
            callback.onSimStateChanged(this.mSimState[subscription]);
        }
        callback.onMusicClientIdChanged(this.mDisplayClientState.clientGeneration, this.mDisplayClientState.clearing, this.mDisplayClientState.intent);
        callback.onMusicPlaybackStateChanged(this.mDisplayClientState.playbackState, this.mDisplayClientState.playbackEventTime);
    }

    public void sendKeyguardVisibilityChanged(boolean showing) {
        Message message = this.mHandler.obtainMessage(MSG_KEYGUARD_VISIBILITY_CHANGED);
        message.arg1 = showing ? 1 : 0;
        message.sendToTarget();
    }

    public void reportClockVisible(boolean visible) {
        this.mClockVisible = visible;
        this.mHandler.obtainMessage(MSG_CLOCK_VISIBILITY_CHANGED).sendToTarget();
    }

    public State getSimState() {
        return getSimState(MSimTelephonyManager.getDefault().getDefaultSubscription());
    }

    public State getSimState(int subscription) {
        return this.mSimState[subscription];
    }

    public State getSimStateFromSystem(int subscription) {
        switch (MSimTelephonyManager.getDefault().getSimState(subscription)) {
            case SlidingChallengeLayout.SCROLL_STATE_IDLE /*0*/:
                return State.UNKNOWN;
            case SlidingChallengeLayout.SCROLL_STATE_DRAGGING /*1*/:
                return State.ABSENT;
            case SlidingChallengeLayout.SCROLL_STATE_SETTLING /*2*/:
                return State.PIN_REQUIRED;
            case FAILED_BIOMETRIC_UNLOCK_ATTEMPTS_BEFORE_BACKUP /*3*/:
                return State.PUK_REQUIRED;
            case LayoutParams.CHILD_TYPE_WIDGETS /*5*/:
                return State.READY;
            case LayoutParams.CHILD_TYPE_EXPAND_CHALLENGE_HANDLE /*6*/:
                return State.CARD_IO_ERROR;
            default:
                return State.NOT_READY;
        }
    }

    public int getPinLockedSubscription() {
        for (int i = 0; i < this.mSimState.length; i++) {
            if (this.mSimState[i] == State.PIN_REQUIRED) {
                return i;
            }
        }
        return -1;
    }

    public int getPukLockedSubscription() {
        for (int i = 0; i < this.mSimState.length; i++) {
            if (this.mSimState[i] == State.PUK_REQUIRED) {
                return i;
            }
        }
        return -1;
    }

    public void reportSimUnlocked() {
        reportSimUnlocked(MSimTelephonyManager.getDefault().getDefaultSubscription());
    }

    public void reportSimUnlocked(int subscription) {
        handleSimStateChange(new SimArgs(State.READY, subscription));
    }

    public void reportEmergencyCallAction(boolean bypassHandler) {
        if (bypassHandler) {
            handleReportEmergencyCallAction();
        } else {
            this.mHandler.obtainMessage(MSG_REPORT_EMERGENCY_CALL_ACTION).sendToTarget();
        }
    }

    public CharSequence getTelephonyPlmn() {
        return getTelephonyPlmn(MSimTelephonyManager.getDefault().getDefaultSubscription());
    }

    public CharSequence getTelephonyPlmn(int subscription) {
        return this.mTelephonyPlmn[subscription];
    }

    public CharSequence getTelephonySpn() {
        return getTelephonySpn(MSimTelephonyManager.getDefault().getDefaultSubscription());
    }

    public CharSequence getTelephonySpn(int subscription) {
        return this.mTelephonySpn[subscription];
    }

    public boolean isDeviceProvisioned() {
        return this.mDeviceProvisioned;
    }

    public int getFailedUnlockAttempts() {
        return this.mFailedAttempts;
    }

    public void clearFailedUnlockAttempts() {
        this.mFailedAttempts = 0;
        this.mFailedBiometricUnlockAttempts = 0;
    }

    public void reportFailedUnlockAttempt() {
        this.mFailedAttempts++;
    }

    public boolean isClockVisible() {
        return this.mClockVisible;
    }

    public int getPhoneState() {
        return this.mPhoneState;
    }

    public void reportFailedBiometricUnlockAttempt() {
        this.mFailedBiometricUnlockAttempts++;
    }

    public boolean getMaxBiometricUnlockAttemptsReached() {
        return this.mFailedBiometricUnlockAttempts >= FAILED_BIOMETRIC_UNLOCK_ATTEMPTS_BEFORE_BACKUP ? true : DEBUG_SIM_STATES;
    }

    public boolean isAlternateUnlockEnabled() {
        return this.mAlternateUnlockEnabled;
    }

    public void setAlternateUnlockEnabled(boolean enabled) {
        this.mAlternateUnlockEnabled = enabled;
    }

    public boolean isSimLocked() {
        for (State simState : this.mSimState) {
            if (isSimLocked(simState)) {
                return true;
            }
        }
        return DEBUG_SIM_STATES;
    }

    public boolean isSimLocked(int subscription) {
        return isSimLocked(this.mSimState[subscription]);
    }

    public static boolean isSimLocked(State state) {
        return (state == State.PIN_REQUIRED || state == State.PUK_REQUIRED || state == State.PERM_DISABLED) ? true : DEBUG_SIM_STATES;
    }

    public boolean isSimPinSecure() {
        for (State simState : this.mSimState) {
            if (isSimPinSecure(simState)) {
                return true;
            }
        }
        return DEBUG_SIM_STATES;
    }

    public boolean isSimPinSecure(int subscription) {
        return isSimPinSecure(this.mSimState[subscription]);
    }

    public static boolean isSimPinSecure(State state) {
        State simState = state;
        return (simState == State.PIN_REQUIRED || simState == State.PUK_REQUIRED || simState == State.PERM_DISABLED) ? true : DEBUG_SIM_STATES;
    }

    public DisplayClientState getCachedDisplayClientState() {
        return this.mDisplayClientState;
    }

    public void dispatchScreenTurnedOn() {
        synchronized (this) {
            this.mScreenOn = true;
        }
        this.mHandler.sendEmptyMessage(MSG_SCREEN_TURNED_ON);
    }

    public void dispatchScreenTurndOff(int why) {
        synchronized (this) {
            this.mScreenOn = DEBUG_SIM_STATES;
        }
        this.mHandler.sendMessage(this.mHandler.obtainMessage(MSG_SCREEN_TURNED_OFF, why, 0));
    }

    public void dispatchLidStateChange(int state) {
        this.mHandler.sendMessage(this.mHandler.obtainMessage(MSG_LID_STATE_CHANGED, state, 0));
    }

    public boolean isScreenOn() {
        return this.mScreenOn;
    }

    public Pair<String, byte[]> getApplicationWidgetDetails() {
        Pair<String, byte[]> pair;
        synchronized (this.mApplicationWidgetLock) {
            pair = new Pair(this.mApplicationWidgetPackageName, this.mApplicationWidgetIcon);
        }
        return pair;
    }
}
