package com.cyngn.keyguard;

import android.content.Context;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.AttributeSet;
import com.android.internal.telephony.msim.ITelephonyMSim.Stub;

public class MSimKeyguardSimPukView extends KeyguardSimPukView {
    private static final boolean DEBUG = false;
    private static String TAG = "MSimKeyguardSimPukView";

    private abstract class MSimCheckSimPuk extends Thread {
        private final String mPin;
        private final String mPuk;
        protected final int mSubscription;

        abstract void onSimLockChangedResponse(boolean z);

        protected MSimCheckSimPuk(String puk, String pin, int sub) {
            this.mPuk = puk;
            this.mPin = pin;
            this.mSubscription = sub;
        }

        public void run() {
            try {
                final boolean result = Stub.asInterface(ServiceManager.checkService("phone_msim")).supplyPuk(this.mPuk, this.mPin, this.mSubscription);
                MSimKeyguardSimPukView.this.post(new Runnable() {
                    public void run() {
                        MSimCheckSimPuk.this.onSimLockChangedResponse(result);
                    }
                });
            } catch (RemoteException e) {
                MSimKeyguardSimPukView.this.post(new Runnable() {
                    public void run() {
                        MSimCheckSimPuk.this.onSimLockChangedResponse(false);
                    }
                });
            }
        }
    }

    protected class MSimStateMachine extends StateMachine {
        protected MSimStateMachine() {
            super();
        }

        public void next() {
            int msg = 0;
            if (this.state == 0) {
                if (MSimKeyguardSimPukView.this.checkPuk()) {
                    this.state = 1;
                    msg = R.string.kg_puk_enter_pin_hint;
                } else {
                    msg = R.string.kg_invalid_sim_puk_hint;
                }
            } else if (this.state == 1) {
                if (MSimKeyguardSimPukView.this.checkPin()) {
                    this.state = 2;
                    msg = R.string.kg_enter_confirm_pin_hint;
                } else {
                    msg = R.string.kg_invalid_sim_pin_hint;
                }
            } else if (this.state == 2) {
                if (MSimKeyguardSimPukView.this.confirmPin()) {
                    this.state = 3;
                    msg = R.string.keyguard_sim_unlock_progress_dialog_message;
                    MSimKeyguardSimPukView.this.updateSim();
                } else {
                    this.state = 1;
                    msg = R.string.kg_invalid_confirm_pin_hint;
                }
            }
            MSimKeyguardSimPukView.this.mPasswordEntry.setText(null);
            if (msg != 0) {
                MSimKeyguardSimPukView.this.mSecurityMessageDisplay.setMessage(MSimKeyguardSimPukView.this.getSecurityMessageDisplay(msg), true);
            }
        }

        void reset() {
            String displayMessage = "";
            try {
                int attemptsRemaining = Stub.asInterface(ServiceManager.checkService("phone_msim")).getIccPin1RetryCount(KeyguardUpdateMonitor.getInstance(MSimKeyguardSimPukView.this.mContext).getPukLockedSubscription());
                if (attemptsRemaining >= 0) {
                    displayMessage = MSimKeyguardSimPukView.this.getContext().getString(R.string.keyguard_password_wrong_puk_code) + MSimKeyguardSimPukView.this.getContext().getString(R.string.pinpuk_attempts) + attemptsRemaining + ". ";
                }
            } catch (RemoteException e) {
                displayMessage = MSimKeyguardSimPukView.this.getContext().getString(R.string.keyguard_password_puk_failed);
            }
            CharSequence displayMessage2 = MSimKeyguardSimPukView.this.getSecurityMessageDisplay(R.string.kg_puk_enter_puk_hint) + displayMessage;
            MSimKeyguardSimPukView.this.mPinText = "";
            MSimKeyguardSimPukView.this.mPukText = "";
            this.state = 0;
            MSimKeyguardSimPukView.this.mSecurityMessageDisplay.setMessage(displayMessage2, true);
            MSimKeyguardSimPukView.this.mPasswordEntry.requestFocus();
        }
    }

    public MSimKeyguardSimPukView(Context context) {
        this(context, null);
    }

    public MSimKeyguardSimPukView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.mStateMachine = new MSimStateMachine();
    }

    protected void updateSim() {
        getSimUnlockProgressDialog().show();
        if (!this.mCheckInProgress) {
            this.mCheckInProgress = true;
            new MSimCheckSimPuk(this.mPukText, this.mPinText, KeyguardUpdateMonitor.getInstance(this.mContext).getPukLockedSubscription()) {
                void onSimLockChangedResponse(final boolean success) {
                    MSimKeyguardSimPukView.this.post(new Runnable() {
                        public void run() {
                            if (MSimKeyguardSimPukView.this.mSimUnlockProgressDialog != null) {
                                MSimKeyguardSimPukView.this.mSimUnlockProgressDialog.hide();
                            }
                            if (success) {
                                MSimKeyguardSimPukView.this.mCallback.dismiss(true);
                            } else {
                                MSimKeyguardSimPukView.this.mStateMachine.reset();
                                MSimKeyguardSimPukView.this.mSecurityMessageDisplay.setMessage(MSimKeyguardSimPukView.this.getSecurityMessageDisplay(R.string.kg_invalid_puk), true);
                            }
                            MSimKeyguardSimPukView.this.mCheckInProgress = false;
                        }
                    });
                }
            }.start();
        }
    }

    protected CharSequence getSecurityMessageDisplay(int resId) {
        return getContext().getString(R.string.msim_kg_sim_pin_msg_format, new Object[]{Integer.valueOf(KeyguardUpdateMonitor.getInstance(this.mContext).getPukLockedSubscription() + 1), getContext().getResources().getText(resId)});
    }
}
