package com.cyngn.keyguard;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.UserHandle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.LoginFilter.UsernameFilterGeneric;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import com.android.internal.widget.LockPatternUtils;
import com.cyngn.keyguard.KeyguardMessageArea.Helper;
import java.io.IOException;

public class KeyguardAccountView extends LinearLayout implements TextWatcher, OnClickListener, KeyguardSecurityView {
    private static final int AWAKE_POKE_MILLIS = 30000;
    private static final String LOCK_PATTERN_CLASS = "com.android.settings.ChooseLockGeneric";
    private static final String LOCK_PATTERN_PACKAGE = "com.android.settings";
    private KeyguardSecurityCallback mCallback;
    private ProgressDialog mCheckingDialog;
    public boolean mEnableFallback;
    private LockPatternUtils mLockPatternUtils;
    private EditText mLogin;
    private Button mOk;
    private EditText mPassword;
    private SecurityMessageDisplay mSecurityMessageDisplay;

    public KeyguardAccountView(Context context) {
        this(context, null, 0);
    }

    public KeyguardAccountView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public KeyguardAccountView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        this.mLockPatternUtils = new LockPatternUtils(getContext());
    }

    protected void onFinishInflate() {
        super.onFinishInflate();
        this.mLogin = (EditText) findViewById(R.id.login);
        this.mLogin.setFilters(new InputFilter[]{new UsernameFilterGeneric()});
        this.mLogin.addTextChangedListener(this);
        this.mPassword = (EditText) findViewById(R.id.password);
        this.mPassword.addTextChangedListener(this);
        this.mOk = (Button) findViewById(R.id.ok);
        this.mOk.setOnClickListener(this);
        this.mSecurityMessageDisplay = new Helper(this);
        reset();
    }

    public void setKeyguardCallback(KeyguardSecurityCallback callback) {
        this.mCallback = callback;
    }

    public void setLockPatternUtils(LockPatternUtils utils) {
        this.mLockPatternUtils = utils;
    }

    public KeyguardSecurityCallback getCallback() {
        return this.mCallback;
    }

    public void afterTextChanged(Editable s) {
    }

    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    public void onTextChanged(CharSequence s, int start, int before, int count) {
        if (this.mCallback != null) {
            this.mCallback.userActivity(30000);
        }
    }

    protected boolean onRequestFocusInDescendants(int direction, Rect previouslyFocusedRect) {
        return this.mLogin.requestFocus(direction, previouslyFocusedRect);
    }

    public boolean needsInput() {
        return true;
    }

    public void reset() {
        this.mLogin.setText("");
        this.mPassword.setText("");
        this.mLogin.requestFocus();
        boolean permLocked = this.mLockPatternUtils.isPermanentlyLocked();
        this.mSecurityMessageDisplay.setMessage(permLocked ? R.string.kg_login_too_many_attempts : R.string.kg_login_instructions, permLocked);
    }

    public void cleanUp() {
        if (this.mCheckingDialog != null) {
            this.mCheckingDialog.hide();
        }
        this.mCallback = null;
        this.mLockPatternUtils = null;
    }

    public void onClick(View v) {
        this.mCallback.userActivity(0);
        if (v == this.mOk) {
            asyncCheckPassword();
        }
    }

    private void postOnCheckPasswordResult(final boolean success) {
        this.mLogin.post(new Runnable() {
            public void run() {
                if (success) {
                    KeyguardAccountView.this.mLockPatternUtils.setPermanentlyLocked(false);
                    KeyguardAccountView.this.mLockPatternUtils.setLockPatternEnabled(false);
                    KeyguardAccountView.this.mLockPatternUtils.saveLockPattern(null);
                    Intent intent = new Intent();
                    intent.setClassName(KeyguardAccountView.LOCK_PATTERN_PACKAGE, KeyguardAccountView.LOCK_PATTERN_CLASS);
                    intent.setFlags(268435456);
                    KeyguardAccountView.this.mContext.startActivityAsUser(intent, new UserHandle(KeyguardAccountView.this.mLockPatternUtils.getCurrentUser()));
                    KeyguardAccountView.this.mCallback.reportSuccessfulUnlockAttempt();
                    KeyguardAccountView.this.mCallback.dismiss(true);
                    return;
                }
                KeyguardAccountView.this.mSecurityMessageDisplay.setMessage((int) R.string.kg_login_invalid_input, true);
                KeyguardAccountView.this.mPassword.setText("");
                KeyguardAccountView.this.mCallback.reportFailedUnlockAttempt();
            }
        });
    }

    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() != 0 || event.getKeyCode() != 4) {
            return super.dispatchKeyEvent(event);
        }
        if (this.mLockPatternUtils.isPermanentlyLocked()) {
            this.mCallback.dismiss(false);
        }
        return true;
    }

    private Account findIntendedAccount(String username) {
        Account bestAccount = null;
        int bestScore = 0;
        for (Account a : AccountManager.get(this.mContext).getAccountsByTypeAsUser("com.google", new UserHandle(this.mLockPatternUtils.getCurrentUser()))) {
            int score = 0;
            if (username.equals(a.name)) {
                score = 4;
            } else if (username.equalsIgnoreCase(a.name)) {
                score = 3;
            } else if (username.indexOf(64) < 0) {
                int i = a.name.indexOf(64);
                if (i >= 0) {
                    String aUsername = a.name.substring(0, i);
                    if (username.equals(aUsername)) {
                        score = 2;
                    } else if (username.equalsIgnoreCase(aUsername)) {
                        score = 1;
                    }
                }
            }
            if (score > bestScore) {
                bestAccount = a;
                bestScore = score;
            } else if (score == bestScore) {
                bestAccount = null;
            }
        }
        return bestAccount;
    }

    private void asyncCheckPassword() {
        this.mCallback.userActivity(30000);
        String login = this.mLogin.getText().toString();
        String password = this.mPassword.getText().toString();
        Account account = findIntendedAccount(login);
        if (account == null) {
            postOnCheckPasswordResult(false);
            return;
        }
        getProgressDialog().show();
        Bundle options = new Bundle();
        options.putString("password", password);
        AccountManager.get(this.mContext).confirmCredentialsAsUser(account, options, null, new AccountManagerCallback<Bundle>() {
            public void run(AccountManagerFuture<Bundle> future) {
                try {
                    KeyguardAccountView.this.mCallback.userActivity(30000);
                    KeyguardAccountView.this.postOnCheckPasswordResult(((Bundle) future.getResult()).getBoolean("booleanResult"));
                } catch (OperationCanceledException e) {
                    KeyguardAccountView.this.postOnCheckPasswordResult(false);
                } catch (IOException e2) {
                    KeyguardAccountView.this.postOnCheckPasswordResult(false);
                } catch (AuthenticatorException e3) {
                    KeyguardAccountView.this.postOnCheckPasswordResult(false);
                } finally {
                    KeyguardAccountView.this.mLogin.post(new Runnable() {
                        public void run() {
                            KeyguardAccountView.this.getProgressDialog().hide();
                        }
                    });
                }
            }
        }, null, new UserHandle(this.mLockPatternUtils.getCurrentUser()));
    }

    private Dialog getProgressDialog() {
        if (this.mCheckingDialog == null) {
            this.mCheckingDialog = new ProgressDialog(this.mContext);
            this.mCheckingDialog.setMessage(this.mContext.getString(R.string.kg_login_checking_password));
            this.mCheckingDialog.setIndeterminate(true);
            this.mCheckingDialog.setCancelable(false);
            this.mCheckingDialog.getWindow().setType(2009);
        }
        return this.mCheckingDialog;
    }

    public void onPause() {
    }

    public void onResume(int reason) {
        reset();
    }

    public void showUsabilityHint() {
    }

    public void showBouncer(int duration) {
    }

    public void hideBouncer(int duration) {
    }
}
