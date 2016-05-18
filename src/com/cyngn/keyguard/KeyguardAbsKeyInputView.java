package com.cyngn.keyguard;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.CountDownTimer;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings.System;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import com.android.internal.widget.LockPatternUtils;
import com.cyngn.keyguard.KeyguardMessageArea.Helper;

public abstract class KeyguardAbsKeyInputView extends LinearLayout implements TextWatcher, OnEditorActionListener, KeyguardSecurityView {
    protected static final int MINIMUM_PASSWORD_LENGTH_BEFORE_REPORT = 3;
    private Drawable mBouncerFrame;
    protected KeyguardSecurityCallback mCallback;
    private GestureDetector mDoubleTapGesture;
    protected View mEcaView;
    protected boolean mEnableHaptics;
    protected LockPatternUtils mLockPatternUtils;
    protected TextView mPasswordEntry;
    private boolean mQuickUnlock;
    protected SecurityMessageDisplay mSecurityMessageDisplay;

    protected abstract int getPasswordTextViewId();

    protected abstract void resetState();

    public KeyguardAbsKeyInputView(Context context) {
        this(context, null);
    }

    public KeyguardAbsKeyInputView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setKeyguardCallback(KeyguardSecurityCallback callback) {
        this.mCallback = callback;
    }

    public void setLockPatternUtils(LockPatternUtils utils) {
        this.mLockPatternUtils = utils;
        this.mEnableHaptics = this.mLockPatternUtils.isTactileFeedbackEnabled();
    }

    public void onWindowFocusChanged(boolean hasWindowFocus) {
        if (hasWindowFocus) {
            reset();
        }
    }

    public void reset() {
        this.mPasswordEntry.setText("");
        this.mPasswordEntry.requestFocus();
        long deadline = this.mLockPatternUtils.getLockoutAttemptDeadline();
        if (shouldLockout(deadline)) {
            handleAttemptLockout(deadline);
        } else {
            resetState();
        }
    }

    protected boolean shouldLockout(long deadline) {
        return deadline != 0;
    }

    protected void onFinishInflate() {
        boolean z;
        this.mLockPatternUtils = new LockPatternUtils(this.mContext);
        this.mDoubleTapGesture = new GestureDetector(this.mContext, new SimpleOnGestureListener() {
            public boolean onDoubleTap(MotionEvent e) {
                PowerManager pm = (PowerManager) KeyguardAbsKeyInputView.this.mContext.getSystemService("power");
                if (pm != null) {
                    pm.goToSleep(e.getEventTime());
                }
                return true;
            }
        });
        this.mPasswordEntry = (TextView) findViewById(getPasswordTextViewId());
        this.mPasswordEntry.setOnEditorActionListener(this);
        this.mPasswordEntry.addTextChangedListener(this);
        if (System.getInt(this.mContext.getContentResolver(), "double_tap_sleep_gesture", 0) == 1) {
            this.mPasswordEntry.setOnTouchListener(new OnTouchListener() {
                public boolean onTouch(View v, MotionEvent event) {
                    return KeyguardAbsKeyInputView.this.mDoubleTapGesture.onTouchEvent(event);
                }
            });
        }
        if (System.getInt(this.mContext.getContentResolver(), "lockscreen_quick_unlock_control", 0) == 1) {
            z = true;
        } else {
            z = false;
        }
        this.mQuickUnlock = z;
        this.mPasswordEntry.setSelected(true);
        this.mPasswordEntry.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                KeyguardAbsKeyInputView.this.mCallback.userActivity(0);
            }
        });
        this.mPasswordEntry.addTextChangedListener(new TextWatcher() {
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void afterTextChanged(Editable s) {
                if (KeyguardAbsKeyInputView.this.mCallback != null) {
                    KeyguardAbsKeyInputView.this.mCallback.userActivity(0);
                }
                if (KeyguardAbsKeyInputView.this.mQuickUnlock) {
                    String entry = KeyguardAbsKeyInputView.this.mPasswordEntry.getText().toString();
                    if (entry.length() > KeyguardAbsKeyInputView.MINIMUM_PASSWORD_LENGTH_BEFORE_REPORT && KeyguardAbsKeyInputView.this.mLockPatternUtils.checkPassword(entry)) {
                        KeyguardAbsKeyInputView.this.mCallback.reportSuccessfulUnlockAttempt();
                        KeyguardAbsKeyInputView.this.mCallback.dismiss(true);
                    }
                }
            }
        });
        this.mSecurityMessageDisplay = new Helper(this);
        this.mEcaView = findViewById(R.id.keyguard_selector_fade_container);
        View bouncerFrameView = findViewById(R.id.keyguard_bouncer_frame);
        if (bouncerFrameView != null) {
            this.mBouncerFrame = bouncerFrameView.getBackground();
        }
    }

    protected boolean onRequestFocusInDescendants(int direction, Rect previouslyFocusedRect) {
        return this.mPasswordEntry.requestFocus(direction, previouslyFocusedRect);
    }

    protected int getWrongPasswordStringId() {
        return R.string.kg_wrong_password;
    }

    protected void verifyPasswordAndUnlock() {
        String entry = this.mPasswordEntry.getText().toString();
        if (this.mLockPatternUtils.checkPassword(entry)) {
            this.mCallback.reportSuccessfulUnlockAttempt();
            this.mCallback.dismiss(true);
        } else if (entry.length() > MINIMUM_PASSWORD_LENGTH_BEFORE_REPORT) {
            this.mCallback.reportFailedUnlockAttempt();
            if (this.mCallback.getFailedAttempts() % 5 == 0) {
                handleAttemptLockout(this.mLockPatternUtils.setLockoutAttemptDeadline());
            }
            this.mSecurityMessageDisplay.setMessage(getWrongPasswordStringId(), true);
        }
        this.mPasswordEntry.setText("");
    }

    protected void handleAttemptLockout(long elapsedRealtimeDeadline) {
        this.mPasswordEntry.setEnabled(false);
        new CountDownTimer(elapsedRealtimeDeadline - SystemClock.elapsedRealtime(), 1000) {
            public void onTick(long millisUntilFinished) {
                int secondsRemaining = (int) (millisUntilFinished / 1000);
                KeyguardAbsKeyInputView.this.mSecurityMessageDisplay.setMessage(R.string.kg_too_many_failed_attempts_countdown, true, Integer.valueOf(secondsRemaining));
            }

            public void onFinish() {
                KeyguardAbsKeyInputView.this.mSecurityMessageDisplay.setMessage((CharSequence) "", false);
                KeyguardAbsKeyInputView.this.resetState();
            }
        }.start();
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        this.mCallback.userActivity(0);
        return false;
    }

    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (actionId != 0 && actionId != 6 && actionId != 5) {
            return false;
        }
        verifyPasswordAndUnlock();
        return true;
    }

    public boolean needsInput() {
        return false;
    }

    public void onPause() {
    }

    public void onResume(int reason) {
        reset();
    }

    public KeyguardSecurityCallback getCallback() {
        return this.mCallback;
    }

    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        if (this.mCallback != null) {
            this.mCallback.userActivity(5000);
        }
    }

    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    public void afterTextChanged(Editable s) {
    }

    public void doHapticKeyClick() {
        if (this.mEnableHaptics) {
            performHapticFeedback(1, MINIMUM_PASSWORD_LENGTH_BEFORE_REPORT);
        }
    }

    public void showBouncer(int duration) {
        KeyguardSecurityViewHelper.showBouncer(this.mSecurityMessageDisplay, this.mEcaView, this.mBouncerFrame, duration);
    }

    public void hideBouncer(int duration) {
        KeyguardSecurityViewHelper.hideBouncer(this.mSecurityMessageDisplay, this.mEcaView, this.mBouncerFrame, duration);
    }
}
