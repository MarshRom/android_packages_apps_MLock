package com.cyngn.keyguard;

import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.telephony.TelephonyManager;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import com.android.internal.telephony.IccCardConstants.State;
import com.android.internal.widget.LockPatternUtils;

public class EmergencyButton extends Button {
    private static final String ACTION_EMERGENCY_DIAL = "com.android.phone.EmergencyDialer.DIAL";
    private static final int EMERGENCY_CALL_TIMEOUT = 10000;
    KeyguardUpdateMonitorCallback mInfoCallback;
    private LockPatternUtils mLockPatternUtils;
    private PowerManager mPowerManager;

    public EmergencyButton(Context context) {
        this(context, null);
    }

    public EmergencyButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.mInfoCallback = new KeyguardUpdateMonitorCallback() {
            public void onSimStateChanged(State simState) {
                EmergencyButton.this.updateEmergencyCallButton(simState, KeyguardUpdateMonitor.getInstance(EmergencyButton.this.mContext).getPhoneState());
            }

            void onPhoneStateChanged(int phoneState) {
                EmergencyButton.this.updateEmergencyCallButton(KeyguardUpdateMonitor.getInstance(EmergencyButton.this.mContext).getSimState(), phoneState);
            }
        };
    }

    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        KeyguardUpdateMonitor.getInstance(this.mContext).registerCallback(this.mInfoCallback);
    }

    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        KeyguardUpdateMonitor.getInstance(this.mContext).removeCallback(this.mInfoCallback);
    }

    protected void onFinishInflate() {
        super.onFinishInflate();
        this.mLockPatternUtils = new LockPatternUtils(this.mContext);
        this.mPowerManager = (PowerManager) this.mContext.getSystemService("power");
        setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                EmergencyButton.this.takeEmergencyCallAction();
            }
        });
        updateEmergencyCallButton(KeyguardUpdateMonitor.getInstance(this.mContext).getSimState(), KeyguardUpdateMonitor.getInstance(this.mContext).getPhoneState());
    }

    public void takeEmergencyCallAction() {
        this.mPowerManager.userActivity(SystemClock.uptimeMillis(), true);
        if (TelephonyManager.getDefault().getCallState() == 2) {
            this.mLockPatternUtils.resumeCall();
            return;
        }
        KeyguardUpdateMonitor.getInstance(this.mContext).reportEmergencyCallAction(true);
        Intent intent = new Intent(ACTION_EMERGENCY_DIAL);
        intent.setFlags(276824064);
        getContext().startActivityAsUser(intent, new UserHandle(this.mLockPatternUtils.getCurrentUser()));
    }

    private void updateEmergencyCallButton(State simState, int phoneState) {
        boolean enabled = false;
        if (phoneState == 2) {
            enabled = true;
        } else if (this.mLockPatternUtils.isEmergencyCallCapable()) {
            if (KeyguardUpdateMonitor.getInstance(this.mContext).isSimLocked()) {
                enabled = this.mLockPatternUtils.isEmergencyCallEnabledWhileSimLocked();
            } else {
                enabled = this.mLockPatternUtils.isSecure();
            }
        }
        this.mLockPatternUtils.updateEmergencyCallButtonState(this, phoneState, enabled, false);
    }
}
