package com.cyngn.keyguard;

import android.content.Context;
import android.telephony.MSimTelephonyManager;
import com.android.internal.telephony.IccCardConstants.State;
import com.android.internal.widget.LockPatternUtils;

public class KeyguardSecurityModel {
    private Context mContext;
    private LockPatternUtils mLockPatternUtils;

    static /* synthetic */ class AnonymousClass1 {
        static final /* synthetic */ int[] $SwitchMap$com$cyngn$keyguard$KeyguardSecurityModel$SecurityMode = new int[SecurityMode.values().length];

        static {
            try {
                $SwitchMap$com$cyngn$keyguard$KeyguardSecurityModel$SecurityMode[SecurityMode.Biometric.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$cyngn$keyguard$KeyguardSecurityModel$SecurityMode[SecurityMode.Pattern.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
        }
    }

    enum SecurityMode {
        Invalid,
        None,
        Pattern,
        Password,
        PIN,
        Biometric,
        Account,
        SimPin,
        SimPuk
    }

    KeyguardSecurityModel(Context context) {
        this.mContext = context;
        this.mLockPatternUtils = new LockPatternUtils(context);
    }

    void setLockPatternUtils(LockPatternUtils utils) {
        this.mLockPatternUtils = utils;
    }

    boolean isBiometricUnlockEnabled() {
        return this.mLockPatternUtils.usingBiometricWeak() && this.mLockPatternUtils.isBiometricWeakInstalled();
    }

    private boolean isBiometricUnlockSuppressed() {
        KeyguardUpdateMonitor monitor = KeyguardUpdateMonitor.getInstance(this.mContext);
        boolean backupIsTimedOut;
        if (monitor.getFailedUnlockAttempts() >= 5) {
            backupIsTimedOut = true;
        } else {
            backupIsTimedOut = false;
        }
        if (monitor.getMaxBiometricUnlockAttemptsReached() || backupIsTimedOut || !monitor.isAlternateUnlockEnabled() || monitor.getPhoneState() != 0) {
            return true;
        }
        return false;
    }

    SecurityMode getSecurityMode() {
        KeyguardUpdateMonitor updateMonitor = KeyguardUpdateMonitor.getInstance(this.mContext);
        State simState = updateMonitor.getSimState();
        int numPhones = MSimTelephonyManager.getDefault().getPhoneCount();
        for (int i = 0; i < numPhones; i++) {
            simState = updateMonitor.getSimState(i);
            if (simState == State.PIN_REQUIRED || simState == State.PUK_REQUIRED) {
                break;
            }
        }
        SecurityMode mode = SecurityMode.None;
        if (simState == State.PIN_REQUIRED) {
            return SecurityMode.SimPin;
        }
        if (simState == State.PUK_REQUIRED && this.mLockPatternUtils.isPukUnlockScreenEnable()) {
            return SecurityMode.SimPuk;
        }
        if (this.mLockPatternUtils.getActiveProfileLockMode() == 1) {
            return mode;
        }
        switch (this.mLockPatternUtils.getKeyguardStoredPasswordQuality()) {
            case SlidingChallengeLayout.SCROLL_STATE_IDLE /*0*/:
            case 65536:
                if (!this.mLockPatternUtils.isLockPatternEnabled()) {
                    return mode;
                }
                return this.mLockPatternUtils.isPermanentlyLocked() ? SecurityMode.Account : SecurityMode.Pattern;
            case 131072:
                return this.mLockPatternUtils.isLockPasswordEnabled() ? SecurityMode.PIN : SecurityMode.None;
            case 262144:
            case 327680:
            case 393216:
                return this.mLockPatternUtils.isLockPasswordEnabled() ? SecurityMode.Password : SecurityMode.None;
            default:
                throw new IllegalStateException("Unknown unlock mode:" + mode);
        }
    }

    SecurityMode getAlternateFor(SecurityMode mode) {
        if (!isBiometricUnlockEnabled() || isBiometricUnlockSuppressed()) {
            return mode;
        }
        if (mode == SecurityMode.Password || mode == SecurityMode.PIN || mode == SecurityMode.Pattern) {
            return SecurityMode.Biometric;
        }
        return mode;
    }

    SecurityMode getBackupSecurityMode(SecurityMode mode) {
        switch (AnonymousClass1.$SwitchMap$com$cyngn$keyguard$KeyguardSecurityModel$SecurityMode[mode.ordinal()]) {
            case SlidingChallengeLayout.SCROLL_STATE_DRAGGING /*1*/:
                return getSecurityMode();
            case SlidingChallengeLayout.SCROLL_STATE_SETTLING /*2*/:
                return SecurityMode.Account;
            default:
                return mode;
        }
    }
}
