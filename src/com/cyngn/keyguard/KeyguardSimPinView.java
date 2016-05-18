package com.cyngn.keyguard;

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

public class KeyguardSimPinView extends KeyguardAbsKeyInputView implements TextWatcher, OnEditorActionListener, KeyguardSecurityView {
    private static final boolean DEBUG = false;
    private static final String LOG_TAG = "KeyguardSimPinView";
    public static final String TAG = "KeyguardSimPinView";
    private CheckSimPin mCheckSimPinThread;
    private AlertDialog mRemainingAttemptsDialog;
    protected volatile boolean mSimCheckInProgress;
    protected ProgressDialog mSimUnlockProgressDialog;

    private abstract class CheckSimPin extends Thread {
        private final String mPin;

        abstract void onSimCheckResponse(int i, int i2);

        protected CheckSimPin(String pin) {
            this.mPin = pin;
        }

        public void run() {
            try {
                Log.v(KeyguardSimPinView.TAG, "call supplyPinReportResult()");
                final int[] result = Stub.asInterface(ServiceManager.checkService("phone")).supplyPinReportResult(this.mPin);
                Log.v(KeyguardSimPinView.TAG, "supplyPinReportResult returned: " + result[0] + " " + result[1]);
                KeyguardSimPinView.this.post(new Runnable() {
                    public void run() {
                        CheckSimPin.this.onSimCheckResponse(result[0], result[1]);
                    }
                });
            } catch (RemoteException e) {
                Log.e(KeyguardSimPinView.TAG, "RemoteException for supplyPinReportResult:", e);
                KeyguardSimPinView.this.post(new Runnable() {
                    public void run() {
                        CheckSimPin.this.onSimCheckResponse(2, -1);
                    }
                });
            }
        }
    }

    public KeyguardSimPinView(Context context) {
        this(context, null);
    }

    public KeyguardSimPinView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.mSimUnlockProgressDialog = null;
    }

    protected void showCancelButton() {
        View cancel = findViewById(R.id.key_cancel);
        if (cancel != null) {
            cancel.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    KeyguardSimPinView.this.doHapticKeyClick();
                }
            });
        }
    }

    public void resetState() {
        String displayMessage = "";
        try {
            int attemptsRemaining = Stub.asInterface(ServiceManager.checkService("phone")).getIccPin1RetryCount();
            if (attemptsRemaining >= 0) {
                displayMessage = getContext().getString(R.string.keyguard_password_wrong_pin_code) + getContext().getString(R.string.pinpuk_attempts) + attemptsRemaining + ". ";
            }
        } catch (RemoteException e) {
            displayMessage = getContext().getString(R.string.keyguard_password_pin_failed);
        }
        this.mSecurityMessageDisplay.setMessage(displayMessage + getContext().getString(R.string.kg_sim_pin_instructions), true);
        this.mPasswordEntry.setEnabled(true);
    }

    private String getPinPasswordErrorMessage(int attemptsRemaining) {
        if (attemptsRemaining == 0) {
            return getContext().getString(R.string.kg_password_wrong_pin_code_pukked);
        }
        if (attemptsRemaining <= 0) {
            return getContext().getString(R.string.kg_password_pin_failed);
        }
        return getContext().getResources().getQuantityString(R.plurals.kg_password_wrong_pin_code, attemptsRemaining, new Object[]{Integer.valueOf(attemptsRemaining)});
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
                    KeyguardSimPinView.this.doHapticKeyClick();
                    KeyguardSimPinView.this.verifyPasswordAndUnlock();
                }
            });
        }
        showCancelButton();
        View pinDelete = findViewById(R.id.delete_button);
        if (pinDelete != null) {
            pinDelete.setVisibility(0);
            pinDelete.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    CharSequence str = KeyguardSimPinView.this.mPasswordEntry.getText();
                    if (str.length() > 0) {
                        KeyguardSimPinView.this.mPasswordEntry.setText(str.subSequence(0, str.length() - 1));
                    }
                    KeyguardSimPinView.this.doHapticKeyClick();
                }
            });
            pinDelete.setOnLongClickListener(new OnLongClickListener() {
                public boolean onLongClick(View v) {
                    KeyguardSimPinView.this.mPasswordEntry.setText("");
                    KeyguardSimPinView.this.doHapticKeyClick();
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
            this.mSimUnlockProgressDialog.getWindow().setType(2009);
        }
        return this.mSimUnlockProgressDialog;
    }

    private Dialog getSimRemainingAttemptsDialog(int remaining) {
        String msg = getPinPasswordErrorMessage(remaining);
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

    protected void verifyPasswordAndUnlock() {
        if (this.mPasswordEntry.getText().toString().length() < 4) {
            this.mSecurityMessageDisplay.setMessage((int) R.string.kg_invalid_sim_pin_hint, true);
            this.mPasswordEntry.setText("");
            this.mCallback.userActivity(0);
            return;
        }
        getSimUnlockProgressDialog().show();
        if (this.mCheckSimPinThread == null) {
            this.mCheckSimPinThread = new CheckSimPin(this.mPasswordEntry.getText().toString()) {
                void onSimCheckResponse(final int result, final int attemptsRemaining) {
                    KeyguardSimPinView.this.post(new Runnable() {
                        public void run() {
                            if (KeyguardSimPinView.this.mSimUnlockProgressDialog != null) {
                                KeyguardSimPinView.this.mSimUnlockProgressDialog.hide();
                            }
                            if (result == 0) {
                                KeyguardUpdateMonitor.getInstance(KeyguardSimPinView.this.getContext()).reportSimUnlocked();
                                KeyguardSimPinView.this.mCallback.dismiss(true);
                            } else {
                                if (result != 1) {
                                    KeyguardSimPinView.this.mSecurityMessageDisplay.setMessage(KeyguardSimPinView.this.getContext().getString(R.string.kg_password_pin_failed), true);
                                } else if (attemptsRemaining <= 2) {
                                    KeyguardSimPinView.this.getSimRemainingAttemptsDialog(attemptsRemaining).show();
                                } else {
                                    KeyguardSimPinView.this.mSecurityMessageDisplay.setMessage(KeyguardSimPinView.this.getPinPasswordErrorMessage(attemptsRemaining), true);
                                }
                                KeyguardSimPinView.this.mPasswordEntry.setText("");
                            }
                            KeyguardSimPinView.this.mCallback.userActivity(0);
                            KeyguardSimPinView.this.mCheckSimPinThread = null;
                        }
                    });
                }
            };
            this.mCheckSimPinThread.start();
        }
    }
}
