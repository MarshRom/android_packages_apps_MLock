package com.cyngn.keyguard;

import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.media.IAudioService;
import android.media.IAudioService.Stub;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.telephony.TelephonyManager;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Slog;
import android.view.KeyEvent;
import android.widget.FrameLayout;
import com.cyngn.keyguard.KeyguardViewMediator.ViewMediatorCallback;

public abstract class KeyguardViewBase extends FrameLayout {
    private static final boolean KEYGUARD_MANAGES_VOLUME = true;
    private AudioManager mAudioManager;
    private TelephonyManager mTelephonyManager;
    protected ViewMediatorCallback mViewMediatorCallback;

    public abstract void cleanUp();

    public abstract long getUserActivityTimeout();

    public abstract void onScreenTurnedOff();

    public abstract void onScreenTurnedOn();

    public abstract void show();

    public abstract void verifyUnlock();

    public KeyguardViewBase(Context context) {
        this(context, null);
    }

    public KeyguardViewBase(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.mTelephonyManager = null;
    }

    public boolean dispatchKeyEvent(KeyEvent event) {
        if (interceptMediaKey(event)) {
            return KEYGUARD_MANAGES_VOLUME;
        }
        return super.dispatchKeyEvent(event);
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    private boolean interceptMediaKey(android.view.KeyEvent r6) {
        /*
        r5 = this;
        r2 = 1;
        r0 = r6.getKeyCode();
        r1 = r6.getAction();
        if (r1 != 0) goto L_0x0057;
    L_0x000b:
        switch(r0) {
            case 24: goto L_0x0032;
            case 25: goto L_0x0032;
            case 79: goto L_0x002e;
            case 85: goto L_0x0010;
            case 86: goto L_0x002e;
            case 87: goto L_0x002e;
            case 88: goto L_0x002e;
            case 89: goto L_0x002e;
            case 90: goto L_0x002e;
            case 91: goto L_0x002e;
            case 126: goto L_0x0010;
            case 127: goto L_0x0010;
            case 130: goto L_0x002e;
            case 164: goto L_0x0032;
            case 222: goto L_0x002e;
            default: goto L_0x000e;
        };
    L_0x000e:
        r2 = 0;
    L_0x000f:
        return r2;
    L_0x0010:
        r1 = r5.mTelephonyManager;
        if (r1 != 0) goto L_0x0022;
    L_0x0014:
        r1 = r5.getContext();
        r3 = "phone";
        r1 = r1.getSystemService(r3);
        r1 = (android.telephony.TelephonyManager) r1;
        r5.mTelephonyManager = r1;
    L_0x0022:
        r1 = r5.mTelephonyManager;
        if (r1 == 0) goto L_0x002e;
    L_0x0026:
        r1 = r5.mTelephonyManager;
        r1 = r1.getCallState();
        if (r1 != 0) goto L_0x000f;
    L_0x002e:
        r5.handleMediaKeyEvent(r6);
        goto L_0x000f;
    L_0x0032:
        monitor-enter(r5);
        r1 = r5.mAudioManager;	 Catch:{ all -> 0x0052 }
        if (r1 != 0) goto L_0x0045;
    L_0x0037:
        r1 = r5.getContext();	 Catch:{ all -> 0x0052 }
        r3 = "audio";
        r1 = r1.getSystemService(r3);	 Catch:{ all -> 0x0052 }
        r1 = (android.media.AudioManager) r1;	 Catch:{ all -> 0x0052 }
        r5.mAudioManager = r1;	 Catch:{ all -> 0x0052 }
    L_0x0045:
        monitor-exit(r5);	 Catch:{ all -> 0x0052 }
        r3 = r5.mAudioManager;
        r4 = 3;
        r1 = 24;
        if (r0 != r1) goto L_0x0055;
    L_0x004d:
        r1 = r2;
    L_0x004e:
        r3.adjustLocalOrRemoteStreamVolume(r4, r1);
        goto L_0x000f;
    L_0x0052:
        r1 = move-exception;
        monitor-exit(r5);	 Catch:{ all -> 0x0052 }
        throw r1;
    L_0x0055:
        r1 = -1;
        goto L_0x004e;
    L_0x0057:
        r1 = r6.getAction();
        if (r1 != r2) goto L_0x000e;
    L_0x005d:
        switch(r0) {
            case 79: goto L_0x0061;
            case 85: goto L_0x0061;
            case 86: goto L_0x0061;
            case 87: goto L_0x0061;
            case 88: goto L_0x0061;
            case 89: goto L_0x0061;
            case 90: goto L_0x0061;
            case 91: goto L_0x0061;
            case 126: goto L_0x0061;
            case 127: goto L_0x0061;
            case 130: goto L_0x0061;
            case 222: goto L_0x0061;
            default: goto L_0x0060;
        };
    L_0x0060:
        goto L_0x000e;
    L_0x0061:
        r5.handleMediaKeyEvent(r6);
        goto L_0x000f;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.cyngn.keyguard.KeyguardViewBase.interceptMediaKey(android.view.KeyEvent):boolean");
    }

    void handleMediaKeyEvent(KeyEvent keyEvent) {
        IAudioService audioService = Stub.asInterface(ServiceManager.checkService("audio"));
        if (audioService != null) {
            try {
                audioService.dispatchMediaKeyEvent(keyEvent);
                return;
            } catch (RemoteException e) {
                Log.e("KeyguardViewBase", "dispatchMediaKeyEvent threw exception " + e);
                return;
            }
        }
        Slog.w("KeyguardViewBase", "Unable to find IAudioService for media key event");
    }

    public void dispatchSystemUiVisibilityChanged(int visibility) {
        super.dispatchSystemUiVisibilityChanged(visibility);
        if (!(this.mContext instanceof Activity)) {
            setSystemUiVisibility(4194304);
        }
    }

    public void setViewMediatorCallback(ViewMediatorCallback viewMediatorCallback) {
        this.mViewMediatorCallback = viewMediatorCallback;
    }
}
