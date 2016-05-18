package com.cyngn.keyguard;

import android.content.Context;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.telephony.MSimTelephonyManager;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import com.android.internal.telephony.IccCardConstants.State;
import com.android.internal.telephony.msim.ITelephonyMSim.Stub;

public class MSimKeyguardSimPinView extends KeyguardSimPinView {
    private static final boolean DEBUG = false;
    private static String TAG = "MSimKeyguardSimPinView";
    private static int sCancelledCount = 0;
    private int mSubscription;

    private abstract class MSimCheckSimPin extends Thread {
        private final String mPin;

        abstract void onSimCheckResponse(boolean z);

        protected MSimCheckSimPin(String pin, int sub) {
            this.mPin = pin;
            MSimKeyguardSimPinView.this.mSubscription = sub;
        }

        public void run() {
            try {
                final boolean result = Stub.asInterface(ServiceManager.checkService("phone_msim")).supplyPin(this.mPin, MSimKeyguardSimPinView.this.mSubscription);
                MSimKeyguardSimPinView.this.post(new Runnable() {
                    public void run() {
                        MSimCheckSimPin.this.onSimCheckResponse(result);
                    }
                });
            } catch (RemoteException e) {
                MSimKeyguardSimPinView.this.post(new Runnable() {
                    public void run() {
                        MSimCheckSimPin.this.onSimCheckResponse(false);
                    }
                });
            }
        }
    }

    public MSimKeyguardSimPinView(Context context) {
        this(context, null);
    }

    public MSimKeyguardSimPinView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.mSubscription = -1;
    }

    protected void showCancelButton() {
        View cancel = findViewById(R.id.key_cancel);
        if (cancel != null) {
            cancel.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    MSimKeyguardSimPinView.this.doHapticKeyClick();
                    MSimKeyguardSimPinView.this.closeKeyGuard(false);
                }
            });
        }
    }

    private void closeKeyGuard(boolean bAuthenticated) {
        KeyguardUpdateMonitor updateMonitor = KeyguardUpdateMonitor.getInstance(getContext());
        int numCardsConfigured = 0;
        int numPinLocked = 0;
        State simState = State.UNKNOWN;
        int numPhones = MSimTelephonyManager.getDefault().getPhoneCount();
        for (int i = 0; i < numPhones; i++) {
            simState = updateMonitor.getSimState(i);
            if (simState == State.PIN_REQUIRED) {
                numPinLocked++;
            }
            if (simState == State.READY || simState == State.PIN_REQUIRED || simState == State.PUK_REQUIRED || simState == State.PERSO_LOCKED) {
                numCardsConfigured++;
            }
        }
        if (!bAuthenticated) {
            if (sCancelledCount < numCardsConfigured - 1) {
                sCancelledCount++;
            } else {
                return;
            }
        }
        if (numPinLocked <= 1) {
            sCancelledCount = 0;
        }
        if (!bAuthenticated) {
            this.mSubscription = updateMonitor.getPinLockedSubscription();
        }
        if (this.mSubscription >= 0) {
            updateMonitor.reportSimUnlocked(this.mSubscription);
        }
        this.mCallback.dismiss(true);
    }

    public void resetState() {
        String displayMessage = "";
        try {
            this.mSubscription = KeyguardUpdateMonitor.getInstance(this.mContext).getPinLockedSubscription();
            int attemptsRemaining = Stub.asInterface(ServiceManager.checkService("phone_msim")).getIccPin1RetryCount(this.mSubscription);
            if (attemptsRemaining >= 0) {
                displayMessage = getContext().getString(R.string.keyguard_password_wrong_pin_code) + getContext().getString(R.string.pinpuk_attempts) + attemptsRemaining + ". ";
            }
        } catch (RemoteException e) {
            displayMessage = getContext().getString(R.string.keyguard_password_pin_failed);
        }
        this.mSecurityMessageDisplay.setMessage(getSecurityMessageDisplay(R.string.kg_sim_pin_instructions) + displayMessage, true);
        this.mPasswordEntry.setEnabled(true);
    }

    protected void verifyPasswordAndUnlock() {
        if (this.mPasswordEntry.getText().toString().length() < 4) {
            this.mSecurityMessageDisplay.setMessage(getSecurityMessageDisplay(R.string.kg_invalid_sim_pin_hint), true);
            this.mPasswordEntry.setText("");
            this.mCallback.userActivity(0);
            return;
        }
        getSimUnlockProgressDialog().show();
        if (!this.mSimCheckInProgress) {
            this.mSimCheckInProgress = true;
            new MSimCheckSimPin(this.mPasswordEntry.getText().toString(), KeyguardUpdateMonitor.getInstance(this.mContext).getPinLockedSubscription()) {
                void onSimCheckResponse(final boolean success) {
                    MSimKeyguardSimPinView.this.post(new Runnable() {
                        public void run() {
                            if (MSimKeyguardSimPinView.this.mSimUnlockProgressDialog != null) {
                                MSimKeyguardSimPinView.this.mSimUnlockProgressDialog.hide();
                            }
                            if (success) {
                                MSimKeyguardSimPinView.this.closeKeyGuard(success);
                            } else {
                                MSimKeyguardSimPinView.this.mSecurityMessageDisplay.setMessage(MSimKeyguardSimPinView.this.getSecurityMessageDisplay(R.string.kg_password_wrong_pin_code), true);
                                MSimKeyguardSimPinView.this.mPasswordEntry.setText("");
                            }
                            MSimKeyguardSimPinView.this.mCallback.userActivity(0);
                            MSimKeyguardSimPinView.this.mSimCheckInProgress = false;
                        }
                    });
                }
            }.start();
        }
    }

    protected CharSequence getSecurityMessageDisplay(int resId) {
        return getContext().getString(R.string.msim_kg_sim_pin_msg_format, new Object[]{Integer.valueOf(KeyguardUpdateMonitor.getInstance(this.mContext).getPinLockedSubscription() + 1), getContext().getResources().getText(resId)});
    }
}
