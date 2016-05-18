package com.cyngn.keyguard;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;
import android.view.View;
import com.android.internal.policy.IFaceLockCallback;
import com.android.internal.policy.IFaceLockInterface;
import com.android.internal.policy.IFaceLockInterface.Stub;
import com.android.internal.widget.LockPatternUtils;
import com.cyngn.keyguard.SlidingChallengeLayout.LayoutParams;

public class FaceUnlock implements Callback, BiometricSensorUnlock {
    private static final boolean DEBUG = false;
    private static final String TAG = "FULLockscreen";
    private final int BACKUP_LOCK_TIMEOUT = 5000;
    private final int MSG_CANCEL = 3;
    private final int MSG_POKE_WAKELOCK = 5;
    private final int MSG_REPORT_FAILED_ATTEMPT = 4;
    private final int MSG_SERVICE_CONNECTED = 0;
    private final int MSG_SERVICE_DISCONNECTED = 1;
    private final int MSG_UNLOCK = 2;
    private boolean mBoundToService = DEBUG;
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder iservice) {
            Log.d(FaceUnlock.TAG, "Connected to Face Unlock service");
            FaceUnlock.this.mService = Stub.asInterface(iservice);
            FaceUnlock.this.mHandler.sendEmptyMessage(0);
        }

        public void onServiceDisconnected(ComponentName className) {
            Log.e(FaceUnlock.TAG, "Unexpected disconnect from Face Unlock service");
            FaceUnlock.this.mHandler.sendEmptyMessage(1);
        }
    };
    private final Context mContext;
    private final IFaceLockCallback mFaceUnlockCallback = new IFaceLockCallback.Stub() {
        public void unlock() {
            FaceUnlock.this.mHandler.sendMessage(FaceUnlock.this.mHandler.obtainMessage(2, UserHandle.getCallingUserId(), -1));
        }

        public void cancel() {
            FaceUnlock.this.mHandler.sendEmptyMessage(3);
        }

        public void reportFailedAttempt() {
            FaceUnlock.this.mHandler.sendEmptyMessage(4);
        }

        public void pokeWakelock(int millis) {
            FaceUnlock.this.mHandler.sendMessage(FaceUnlock.this.mHandler.obtainMessage(5, millis, -1));
        }
    };
    private View mFaceUnlockView;
    private Handler mHandler;
    private volatile boolean mIsRunning = DEBUG;
    KeyguardSecurityCallback mKeyguardScreenCallback;
    private final LockPatternUtils mLockPatternUtils;
    private IFaceLockInterface mService;
    private boolean mServiceRunning = DEBUG;
    private final Object mServiceRunningLock = new Object();

    public FaceUnlock(Context context) {
        this.mContext = context;
        this.mLockPatternUtils = new LockPatternUtils(context);
        this.mHandler = new Handler(this);
    }

    public void setKeyguardCallback(KeyguardSecurityCallback keyguardScreenCallback) {
        this.mKeyguardScreenCallback = keyguardScreenCallback;
    }

    public void initializeView(View biometricUnlockView) {
        Log.d(TAG, "initializeView()");
        this.mFaceUnlockView = biometricUnlockView;
    }

    public boolean isRunning() {
        return this.mIsRunning;
    }

    public void stopAndShowBackup() {
        this.mHandler.sendEmptyMessage(3);
    }

    public boolean start() {
        if (this.mHandler.getLooper() != Looper.myLooper()) {
            Log.e(TAG, "start() called off of the UI thread");
        }
        if (this.mIsRunning) {
            Log.w(TAG, "start() called when already running");
        }
        if (this.mBoundToService) {
            Log.w(TAG, "Attempt to bind to Face Unlock when already bound");
        } else {
            Log.d(TAG, "Binding to Face Unlock service for user=" + this.mLockPatternUtils.getCurrentUser());
            this.mContext.bindServiceAsUser(new Intent(IFaceLockInterface.class.getName()), this.mConnection, 1, new UserHandle(this.mLockPatternUtils.getCurrentUser()));
            this.mBoundToService = true;
        }
        this.mIsRunning = true;
        return true;
    }

    public boolean stop() {
        if (this.mHandler.getLooper() != Looper.myLooper()) {
            Log.e(TAG, "stop() called from non-UI thread");
        }
        this.mHandler.removeMessages(0);
        boolean mWasRunning = this.mIsRunning;
        stopUi();
        if (this.mBoundToService) {
            if (this.mService != null) {
                try {
                    this.mService.unregisterCallback(this.mFaceUnlockCallback);
                } catch (RemoteException e) {
                }
            }
            Log.d(TAG, "Unbinding from Face Unlock service");
            this.mContext.unbindService(this.mConnection);
            this.mBoundToService = DEBUG;
        }
        this.mIsRunning = DEBUG;
        return mWasRunning;
    }

    public void cleanUp() {
        if (this.mService != null) {
            try {
                this.mService.unregisterCallback(this.mFaceUnlockCallback);
            } catch (RemoteException e) {
            }
            stopUi();
            this.mService = null;
        }
    }

    public int getQuality() {
        return 32768;
    }

    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case SlidingChallengeLayout.SCROLL_STATE_IDLE /*0*/:
                handleServiceConnected();
                break;
            case SlidingChallengeLayout.SCROLL_STATE_DRAGGING /*1*/:
                handleServiceDisconnected();
                break;
            case SlidingChallengeLayout.SCROLL_STATE_SETTLING /*2*/:
                handleUnlock(msg.arg1);
                break;
            case SlidingChallengeLayout.SCROLL_STATE_FADING /*3*/:
                handleCancel();
                break;
            case LayoutParams.CHILD_TYPE_SCRIM /*4*/:
                handleReportFailedAttempt();
                break;
            case LayoutParams.CHILD_TYPE_WIDGETS /*5*/:
                handlePokeWakelock(msg.arg1);
                break;
            default:
                Log.e(TAG, "Unhandled message");
                return DEBUG;
        }
        return true;
    }

    void handleServiceConnected() {
        Log.d(TAG, "handleServiceConnected()");
        if (this.mBoundToService) {
            try {
                this.mService.registerCallback(this.mFaceUnlockCallback);
                if (this.mFaceUnlockView != null) {
                    IBinder windowToken = this.mFaceUnlockView.getWindowToken();
                    if (windowToken != null) {
                        this.mKeyguardScreenCallback.userActivity(0);
                        int[] position = new int[2];
                        this.mFaceUnlockView.getLocationInWindow(position);
                        startUi(windowToken, position[0], position[1], this.mFaceUnlockView.getWidth(), this.mFaceUnlockView.getHeight());
                        return;
                    }
                    Log.e(TAG, "windowToken is null in handleServiceConnected()");
                    return;
                }
                return;
            } catch (RemoteException e) {
                Log.e(TAG, "Caught exception connecting to Face Unlock: " + e.toString());
                this.mService = null;
                this.mBoundToService = DEBUG;
                this.mIsRunning = DEBUG;
                return;
            }
        }
        Log.d(TAG, "Dropping startUi() in handleServiceConnected() because no longer bound");
    }

    void handleServiceDisconnected() {
        Log.e(TAG, "handleServiceDisconnected()");
        synchronized (this.mServiceRunningLock) {
            this.mService = null;
            this.mServiceRunning = DEBUG;
        }
        this.mBoundToService = DEBUG;
        this.mIsRunning = DEBUG;
    }

    void handleUnlock(int authenticatedUserId) {
        stop();
        int currentUserId = this.mLockPatternUtils.getCurrentUser();
        if (authenticatedUserId == currentUserId) {
            this.mKeyguardScreenCallback.reportSuccessfulUnlockAttempt();
            this.mKeyguardScreenCallback.dismiss(true);
            return;
        }
        Log.d(TAG, "Ignoring unlock for authenticated user (" + authenticatedUserId + ") because the current user is " + currentUserId);
    }

    void handleCancel() {
        KeyguardUpdateMonitor.getInstance(this.mContext).setAlternateUnlockEnabled(DEBUG);
        this.mKeyguardScreenCallback.showBackupSecurity();
        stop();
        this.mKeyguardScreenCallback.userActivity(5000);
    }

    void handleReportFailedAttempt() {
        KeyguardUpdateMonitor.getInstance(this.mContext).setAlternateUnlockEnabled(DEBUG);
        this.mKeyguardScreenCallback.reportFailedUnlockAttempt();
    }

    void handlePokeWakelock(int millis) {
        if (((PowerManager) this.mContext.getSystemService("power")).isScreenOn()) {
            this.mKeyguardScreenCallback.userActivity((long) millis);
        }
    }

    private void startUi(IBinder windowToken, int x, int y, int w, int h) {
        synchronized (this.mServiceRunningLock) {
            if (this.mServiceRunning) {
                Log.w(TAG, "startUi() attempted while running");
            } else {
                Log.d(TAG, "Starting Face Unlock");
                try {
                    this.mService.startUi(windowToken, x, y, w, h, this.mLockPatternUtils.isBiometricWeakLivelinessEnabled());
                    this.mServiceRunning = true;
                } catch (RemoteException e) {
                    Log.e(TAG, "Caught exception starting Face Unlock: " + e.toString());
                    return;
                }
            }
        }
    }

    private void stopUi() {
        synchronized (this.mServiceRunningLock) {
            if (this.mServiceRunning) {
                Log.d(TAG, "Stopping Face Unlock");
                try {
                    this.mService.stopUi();
                } catch (RemoteException e) {
                    Log.e(TAG, "Caught exception stopping Face Unlock: " + e.toString());
                }
                this.mServiceRunning = DEBUG;
            }
        }
    }
}
