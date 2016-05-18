package com.cyngn.keyguard;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.text.TextWatcher;
import android.text.method.DigitsKeyListener;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.TextView.OnEditorActionListener;
import com.android.internal.telephony.ITelephony.Stub;

public class KeyguardSimPukView extends KeyguardAbsKeyInputView implements TextWatcher, OnEditorActionListener, KeyguardSecurityView {
    private static final boolean DEBUG = false;
    private static final String LOG_TAG = "KeyguardSimPukView";
    public static final String TAG = "KeyguardSimPukView";
    protected volatile boolean mCheckInProgress;
    protected CheckSimPuk mCheckSimPukThread;
    protected String mPinText;
    protected String mPukText;
    protected AlertDialog mRemainingAttemptsDialog;
    protected ProgressDialog mSimUnlockProgressDialog;
    protected StateMachine mStateMachine;

    private abstract class CheckSimPuk extends Thread {
        private final String mPin;
        private final String mPuk;

        abstract void onSimLockChangedResponse(int i, int i2);

        protected CheckSimPuk(String puk, String pin) {
            this.mPuk = puk;
            this.mPin = pin;
        }

        public void run() {
            try {
                Log.v(KeyguardSimPukView.TAG, "call supplyPukReportResult()");
                final int[] result = Stub.asInterface(ServiceManager.checkService("phone")).supplyPukReportResult(this.mPuk, this.mPin);
                Log.v(KeyguardSimPukView.TAG, "supplyPukReportResult returned: " + result[0] + " " + result[1]);
                KeyguardSimPukView.this.post(new Runnable() {
                    public void run() {
                        CheckSimPuk.this.onSimLockChangedResponse(result[0], result[1]);
                    }
                });
            } catch (RemoteException e) {
                Log.e(KeyguardSimPukView.TAG, "RemoteException for supplyPukReportResult:", e);
                KeyguardSimPukView.this.post(new Runnable() {
                    public void run() {
                        CheckSimPuk.this.onSimLockChangedResponse(2, -1);
                    }
                });
            }
        }
    }

    protected class StateMachine {
        final int CONFIRM_PIN = 2;
        final int DONE = 3;
        final int ENTER_PIN = 1;
        final int ENTER_PUK = 0;
        protected int state = 0;

        protected StateMachine() {
        }

        public void next() {
            int msg = 0;
            if (this.state == 0) {
                if (KeyguardSimPukView.this.checkPuk()) {
                    this.state = 1;
                    msg = R.string.kg_puk_enter_pin_hint;
                } else {
                    msg = R.string.kg_invalid_sim_puk_hint;
                }
            } else if (this.state == 1) {
                if (KeyguardSimPukView.this.checkPin()) {
                    this.state = 2;
                    msg = R.string.kg_enter_confirm_pin_hint;
                } else {
                    msg = R.string.kg_invalid_sim_pin_hint;
                }
            } else if (this.state == 2) {
                if (KeyguardSimPukView.this.confirmPin()) {
                    this.state = 3;
                    msg = R.string.keyguard_sim_unlock_progress_dialog_message;
                    KeyguardSimPukView.this.updateSim();
                } else {
                    this.state = 1;
                    msg = R.string.kg_invalid_confirm_pin_hint;
                }
            }
            KeyguardSimPukView.this.mPasswordEntry.setText(null);
            if (msg != 0) {
                KeyguardSimPukView.this.mSecurityMessageDisplay.setMessage(msg, true);
            }
        }

        void reset() {
            KeyguardSimPukView.this.mPinText = "";
            KeyguardSimPukView.this.mPukText = "";
            this.state = 0;
            KeyguardSimPukView.this.mSecurityMessageDisplay.setMessage((int) R.string.kg_puk_enter_puk_hint, true);
            KeyguardSimPukView.this.mPasswordEntry.requestFocus();
        }
    }

    private String getPukPasswordErrorMessage(int attemptsRemaining) {
        if (attemptsRemaining == 0) {
            return getContext().getString(R.string.kg_password_wrong_puk_code_dead);
        }
        if (attemptsRemaining <= 0) {
            return getContext().getString(R.string.kg_password_puk_failed);
        }
        return getContext().getResources().getQuantityString(R.plurals.kg_password_wrong_puk_code, attemptsRemaining, new Object[]{Integer.valueOf(attemptsRemaining)});
    }

    public KeyguardSimPukView(Context context) {
        this(context, null);
    }

    public KeyguardSimPukView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.mSimUnlockProgressDialog = null;
        this.mStateMachine = new StateMachine();
    }

    public void resetState() {
        this.mStateMachine.reset();
        this.mPasswordEntry.setEnabled(true);
    }

    protected boolean shouldLockout(long deadline) {
        return DEBUG;
    }

    protected int getPasswordTextViewId() {
        return R.id.pinEntry;
    }

    protected void onFinishInflate() {
        super.onFinishInflate();
        View ok = findViewById(R.id.key_enter);
        if (ok != null) {
            ok.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    KeyguardSimPukView.this.doHapticKeyClick();
                    KeyguardSimPukView.this.verifyPasswordAndUnlock();
                }
            });
        }
        View pinDelete = findViewById(R.id.delete_button);
        if (pinDelete != null) {
            pinDelete.setVisibility(0);
            pinDelete.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    CharSequence str = KeyguardSimPukView.this.mPasswordEntry.getText();
                    if (str.length() > 0) {
                        KeyguardSimPukView.this.mPasswordEntry.setText(str.subSequence(0, str.length() - 1));
                    }
                    KeyguardSimPukView.this.doHapticKeyClick();
                }
            });
            pinDelete.setOnLongClickListener(new OnLongClickListener() {
                public boolean onLongClick(View v) {
                    KeyguardSimPukView.this.mPasswordEntry.setText("");
                    KeyguardSimPukView.this.doHapticKeyClick();
                    return true;
                }
            });
        }
        this.mPasswordEntry.setKeyListener(DigitsKeyListener.getInstance());
        this.mPasswordEntry.setInputType(18);
        this.mPasswordEntry.requestFocus();
        this.mSecurityMessageDisplay.setTimeout(0);
    }

    public void showUsabilityHint() {
    }

    public void onPause() {
        if (this.mSimUnlockProgressDialog != null) {
            this.mSimUnlockProgressDialog.dismiss();
            this.mSimUnlockProgressDialog = null;
        }
    }

    protected Dialog getSimUnlockProgressDialog() {
        if (this.mSimUnlockProgressDialog == null) {
            this.mSimUnlockProgressDialog = new ProgressDialog(this.mContext);
            this.mSimUnlockProgressDialog.setMessage(this.mContext.getString(R.string.kg_sim_unlock_progress_dialog_message));
            this.mSimUnlockProgressDialog.setIndeterminate(true);
            this.mSimUnlockProgressDialog.setCancelable(DEBUG);
            if (!(this.mContext instanceof Activity)) {
                this.mSimUnlockProgressDialog.getWindow().setType(2009);
            }
        }
        return this.mSimUnlockProgressDialog;
    }

    protected Dialog getPukRemainingAttemptsDialog(int remaining) {
        String msg = getPukPasswordErrorMessage(remaining);
        if (this.mRemainingAttemptsDialog == null) {
            Builder builder = new Builder(this.mContext);
            builder.setMessage(msg);
            builder.setCancelable(DEBUG);
            builder.setNeutralButton(R.string.ok, null);
            this.mRemainingAttemptsDialog = builder.create();
            this.mRemainingAttemptsDialog.getWindow().setType(2009);
        } else {
            this.mRemainingAttemptsDialog.setMessage(msg);
        }
        return this.mRemainingAttemptsDialog;
    }

    protected boolean checkPuk() {
        if (this.mPasswordEntry.getText().length() != 8) {
            return DEBUG;
        }
        this.mPukText = this.mPasswordEntry.getText().toString();
        return true;
    }

    protected boolean checkPin() {
        int length = this.mPasswordEntry.getText().length();
        if (length < 4 || length > 8) {
            return DEBUG;
        }
        this.mPinText = this.mPasswordEntry.getText().toString();
        return true;
    }

    public boolean confirmPin() {
        return this.mPinText.equals(this.mPasswordEntry.getText().toString());
    }

    protected void updateSim() {
        getSimUnlockProgressDialog().show();
        if (this.mCheckSimPukThread == null) {
            this.mCheckSimPukThread = new CheckSimPuk(this.mPukText, this.mPinText) {
                void onSimLockChangedResponse(final int result, final int attemptsRemaining) {
                    KeyguardSimPukView.this.post(new Runnable() {
                        public void run() {
                            if (KeyguardSimPukView.this.mSimUnlockProgressDialog != null) {
                                KeyguardSimPukView.this.mSimUnlockProgressDialog.hide();
                            }
                            if (result == 0) {
                                KeyguardUpdateMonitor.getInstance(KeyguardSimPukView.this.getContext()).reportSimUnlocked();
                                KeyguardSimPukView.this.mCallback.dismiss(true);
                            } else {
                                if (result != 1) {
                                    KeyguardSimPukView.this.mSecurityMessageDisplay.setMessage(KeyguardSimPukView.this.getContext().getString(R.string.kg_password_puk_failed), true);
                                } else if (attemptsRemaining <= 2) {
                                    KeyguardSimPukView.this.getPukRemainingAttemptsDialog(attemptsRemaining).show();
                                } else {
                                    KeyguardSimPukView.this.mSecurityMessageDisplay.setMessage(KeyguardSimPukView.this.getPukPasswordErrorMessage(attemptsRemaining), true);
                                }
                                KeyguardSimPukView.this.mStateMachine.reset();
                            }
                            KeyguardSimPukView.this.mCheckSimPukThread = null;
                        }
                    });
                }
            };
            this.mCheckSimPukThread.start();
        }
    }

    protected void verifyPasswordAndUnlock() {
        this.mStateMachine.next();
    }
}
