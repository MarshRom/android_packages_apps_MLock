package com.cyngn.keyguard;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings.System;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.Button;
import android.widget.LinearLayout;
import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.LockPatternView;
import com.android.internal.widget.LockPatternView.Cell;
import com.android.internal.widget.LockPatternView.DisplayMode;
import com.android.internal.widget.LockPatternView.OnPatternListener;
import com.cyngn.keyguard.KeyguardMessageArea.Helper;
import java.util.List;

public class KeyguardPatternView extends LinearLayout implements KeyguardSecurityView {
    private static final boolean DEBUG = false;
    private static final int MIN_PATTERN_BEFORE_POKE_WAKELOCK = 2;
    private static final int PATTERN_CLEAR_TIMEOUT_MS = 2000;
    private static final String TAG = "SecurityPatternView";
    private static final int UNLOCK_PATTERN_WAKE_INTERVAL_FIRST_DOTS_MS = 2000;
    private static final int UNLOCK_PATTERN_WAKE_INTERVAL_MS = 7000;
    private Drawable mBouncerFrame;
    private KeyguardSecurityCallback mCallback;
    private Runnable mCancelPatternRunnable;
    private CountDownTimer mCountdownTimer;
    private GestureDetector mDoubleTapGesture;
    private View mEcaView;
    private boolean mEnableFallback;
    private int mFailedPatternAttemptsSinceLastTimeout;
    private Button mForgotPatternButton;
    private long mLastPokeTime;
    private LockPatternUtils mLockPatternUtils;
    private LockPatternView mLockPatternView;
    private SecurityMessageDisplay mSecurityMessageDisplay;
    private Rect mTempRect;
    private int mTotalFailedPatternAttempts;

    static /* synthetic */ class AnonymousClass6 {
        static final /* synthetic */ int[] $SwitchMap$com$cyngn$keyguard$KeyguardPatternView$FooterMode = new int[FooterMode.values().length];

        static {
            try {
                $SwitchMap$com$cyngn$keyguard$KeyguardPatternView$FooterMode[FooterMode.Normal.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$cyngn$keyguard$KeyguardPatternView$FooterMode[FooterMode.ForgotLockPattern.ordinal()] = KeyguardPatternView.MIN_PATTERN_BEFORE_POKE_WAKELOCK;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$com$cyngn$keyguard$KeyguardPatternView$FooterMode[FooterMode.VerifyUnlocked.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
        }
    }

    private class AccountAnalyzer implements AccountManagerCallback<Bundle> {
        private int mAccountIndex;
        private final AccountManager mAccountManager;
        private final Account[] mAccounts;

        private AccountAnalyzer(AccountManager accountManager) {
            this.mAccountManager = accountManager;
            this.mAccounts = accountManager.getAccountsByTypeAsUser("com.google", new UserHandle(KeyguardPatternView.this.mLockPatternUtils.getCurrentUser()));
        }

        private void next() {
            if (!KeyguardPatternView.this.mEnableFallback && this.mAccountIndex < this.mAccounts.length) {
                this.mAccountManager.confirmCredentialsAsUser(this.mAccounts[this.mAccountIndex], null, null, this, null, new UserHandle(KeyguardPatternView.this.mLockPatternUtils.getCurrentUser()));
            }
        }

        public void start() {
            KeyguardPatternView.this.mEnableFallback = KeyguardPatternView.DEBUG;
            this.mAccountIndex = 0;
            next();
        }

        /* JADX WARNING: inconsistent code. */
        /* Code decompiled incorrectly, please refer to instructions dump. */
        public void run(android.accounts.AccountManagerFuture<android.os.Bundle> r4) {
            /*
            r3 = this;
            r0 = r4.getResult();	 Catch:{ OperationCanceledException -> 0x001e, IOException -> 0x0029, AuthenticatorException -> 0x0034, all -> 0x003f }
            r0 = (android.os.Bundle) r0;	 Catch:{ OperationCanceledException -> 0x001e, IOException -> 0x0029, AuthenticatorException -> 0x0034, all -> 0x003f }
            r1 = "intent";
            r1 = r0.getParcelable(r1);	 Catch:{ OperationCanceledException -> 0x001e, IOException -> 0x0029, AuthenticatorException -> 0x0034, all -> 0x003f }
            if (r1 == 0) goto L_0x0014;
        L_0x000e:
            r1 = com.cyngn.keyguard.KeyguardPatternView.this;	 Catch:{ OperationCanceledException -> 0x001e, IOException -> 0x0029, AuthenticatorException -> 0x0034, all -> 0x003f }
            r2 = 1;
            r1.mEnableFallback = r2;	 Catch:{ OperationCanceledException -> 0x001e, IOException -> 0x0029, AuthenticatorException -> 0x0034, all -> 0x003f }
        L_0x0014:
            r1 = r3.mAccountIndex;
            r1 = r1 + 1;
            r3.mAccountIndex = r1;
            r3.next();
        L_0x001d:
            return;
        L_0x001e:
            r1 = move-exception;
            r1 = r3.mAccountIndex;
            r1 = r1 + 1;
            r3.mAccountIndex = r1;
            r3.next();
            goto L_0x001d;
        L_0x0029:
            r1 = move-exception;
            r1 = r3.mAccountIndex;
            r1 = r1 + 1;
            r3.mAccountIndex = r1;
            r3.next();
            goto L_0x001d;
        L_0x0034:
            r1 = move-exception;
            r1 = r3.mAccountIndex;
            r1 = r1 + 1;
            r3.mAccountIndex = r1;
            r3.next();
            goto L_0x001d;
        L_0x003f:
            r1 = move-exception;
            r2 = r3.mAccountIndex;
            r2 = r2 + 1;
            r3.mAccountIndex = r2;
            r3.next();
            throw r1;
            */
            throw new UnsupportedOperationException("Method not decompiled: com.cyngn.keyguard.KeyguardPatternView.AccountAnalyzer.run(android.accounts.AccountManagerFuture):void");
        }
    }

    enum FooterMode {
        Normal,
        ForgotLockPattern,
        VerifyUnlocked
    }

    private class UnlockPatternListener implements OnPatternListener {
        private UnlockPatternListener() {
        }

        public void onPatternStart() {
            KeyguardPatternView.this.mLockPatternView.removeCallbacks(KeyguardPatternView.this.mCancelPatternRunnable);
        }

        public void onPatternCleared() {
        }

        public void onPatternCellAdded(List<Cell> pattern) {
            if (pattern.size() > KeyguardPatternView.MIN_PATTERN_BEFORE_POKE_WAKELOCK) {
                KeyguardPatternView.this.mCallback.userActivity(7000);
            } else {
                KeyguardPatternView.this.mCallback.userActivity(2000);
            }
        }

        public void onPatternDetected(List<Cell> pattern) {
            if (KeyguardPatternView.this.mLockPatternUtils.checkPattern(pattern)) {
                KeyguardPatternView.this.mCallback.reportSuccessfulUnlockAttempt();
                KeyguardPatternView.this.mLockPatternView.setDisplayMode(DisplayMode.Correct);
                KeyguardPatternView.this.mTotalFailedPatternAttempts = 0;
                KeyguardPatternView.this.mCallback.dismiss(true);
                return;
            }
            if (pattern.size() > KeyguardPatternView.MIN_PATTERN_BEFORE_POKE_WAKELOCK) {
                KeyguardPatternView.this.mCallback.userActivity(7000);
            }
            KeyguardPatternView.this.mLockPatternView.setDisplayMode(DisplayMode.Wrong);
            if (pattern.size() >= 4) {
                KeyguardPatternView.this.mTotalFailedPatternAttempts = KeyguardPatternView.this.mTotalFailedPatternAttempts + 1;
                KeyguardPatternView.this.mFailedPatternAttemptsSinceLastTimeout = KeyguardPatternView.this.mFailedPatternAttemptsSinceLastTimeout + 1;
                KeyguardPatternView.this.mCallback.reportFailedUnlockAttempt();
            }
            if (KeyguardPatternView.this.mFailedPatternAttemptsSinceLastTimeout >= 5) {
                KeyguardPatternView.this.handleAttemptLockout(KeyguardPatternView.this.mLockPatternUtils.setLockoutAttemptDeadline());
                return;
            }
            KeyguardPatternView.this.mSecurityMessageDisplay.setMessage((int) R.string.kg_wrong_pattern, true);
            KeyguardPatternView.this.mLockPatternView.postDelayed(KeyguardPatternView.this.mCancelPatternRunnable, 2000);
        }
    }

    public KeyguardPatternView(Context context) {
        this(context, null);
    }

    public KeyguardPatternView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.mFailedPatternAttemptsSinceLastTimeout = 0;
        this.mTotalFailedPatternAttempts = 0;
        this.mCountdownTimer = null;
        this.mLastPokeTime = -7000;
        this.mCancelPatternRunnable = new Runnable() {
            public void run() {
                KeyguardPatternView.this.mLockPatternView.clearPattern();
            }
        };
        this.mTempRect = new Rect();
    }

    public void setKeyguardCallback(KeyguardSecurityCallback callback) {
        this.mCallback = callback;
    }

    public void setLockPatternUtils(LockPatternUtils utils) {
        this.mLockPatternUtils = utils;
    }

    protected void onFinishInflate() {
        super.onFinishInflate();
        this.mLockPatternUtils = this.mLockPatternUtils == null ? new LockPatternUtils(this.mContext) : this.mLockPatternUtils;
        this.mDoubleTapGesture = new GestureDetector(this.mContext, new SimpleOnGestureListener() {
            public boolean onDoubleTap(MotionEvent e) {
                PowerManager pm = (PowerManager) KeyguardPatternView.this.mContext.getSystemService("power");
                if (pm != null) {
                    pm.goToSleep(e.getEventTime());
                }
                return true;
            }
        });
        this.mLockPatternView = (LockPatternView) findViewById(R.id.lockPatternView);
        this.mLockPatternView.setSaveEnabled(DEBUG);
        this.mLockPatternView.setFocusable(DEBUG);
        this.mLockPatternView.setOnPatternListener(new UnlockPatternListener());
        this.mLockPatternView.setLockPatternUtils(this.mLockPatternUtils);
        this.mLockPatternView.setVisibleDots(this.mLockPatternUtils.isVisibleDotsEnabled());
        this.mLockPatternView.setShowErrorPath(this.mLockPatternUtils.isShowErrorPath());
        this.mLockPatternView.setInStealthMode(!this.mLockPatternUtils.isVisiblePatternEnabled() ? true : DEBUG);
        this.mLockPatternView.setTactileFeedbackEnabled(this.mLockPatternUtils.isTactileFeedbackEnabled());
        this.mLockPatternView.setLockPatternSize(this.mLockPatternUtils.getLockPatternSize());
        if (System.getInt(this.mContext.getContentResolver(), "double_tap_sleep_gesture", 0) == 1) {
            this.mLockPatternView.setOnTouchListener(new OnTouchListener() {
                public boolean onTouch(View v, MotionEvent event) {
                    return KeyguardPatternView.this.mDoubleTapGesture.onTouchEvent(event);
                }
            });
        }
        this.mForgotPatternButton = (Button) findViewById(R.id.forgot_password_button);
        if (this.mForgotPatternButton != null) {
            this.mForgotPatternButton.setText(R.string.kg_forgot_pattern_button_text);
            this.mForgotPatternButton.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    KeyguardPatternView.this.mCallback.showBackupSecurity();
                }
            });
        }
        setFocusableInTouchMode(true);
        maybeEnableFallback(this.mContext);
        this.mSecurityMessageDisplay = new Helper(this);
        this.mEcaView = findViewById(R.id.keyguard_selector_fade_container);
        View bouncerFrameView = findViewById(R.id.keyguard_bouncer_frame);
        if (bouncerFrameView != null) {
            this.mBouncerFrame = bouncerFrameView.getBackground();
        }
    }

    private void updateFooter(FooterMode mode) {
        if (this.mForgotPatternButton != null) {
            switch (AnonymousClass6.$SwitchMap$com$cyngn$keyguard$KeyguardPatternView$FooterMode[mode.ordinal()]) {
                case SlidingChallengeLayout.SCROLL_STATE_DRAGGING /*1*/:
                    this.mForgotPatternButton.setVisibility(8);
                    return;
                case MIN_PATTERN_BEFORE_POKE_WAKELOCK /*2*/:
                    this.mForgotPatternButton.setVisibility(0);
                    return;
                case SlidingChallengeLayout.SCROLL_STATE_FADING /*3*/:
                    this.mForgotPatternButton.setVisibility(8);
                    return;
                default:
                    return;
            }
        }
    }

    public boolean onTouchEvent(MotionEvent ev) {
        boolean result = super.onTouchEvent(ev);
        long elapsed = SystemClock.elapsedRealtime() - this.mLastPokeTime;
        if (result && elapsed > 6900) {
            this.mLastPokeTime = SystemClock.elapsedRealtime();
        }
        this.mTempRect.set(0, 0, 0, 0);
        offsetRectIntoDescendantCoords(this.mLockPatternView, this.mTempRect);
        ev.offsetLocation((float) this.mTempRect.left, (float) this.mTempRect.top);
        result = (this.mLockPatternView.dispatchTouchEvent(ev) || result) ? true : DEBUG;
        ev.offsetLocation((float) (-this.mTempRect.left), (float) (-this.mTempRect.top));
        return result;
    }

    public void reset() {
        this.mLockPatternView.enableInput();
        this.mLockPatternView.setEnabled(true);
        this.mLockPatternView.clearPattern();
        long deadline = this.mLockPatternUtils.getLockoutAttemptDeadline();
        if (deadline != 0) {
            handleAttemptLockout(deadline);
        } else {
            displayDefaultSecurityMessage();
        }
        if (this.mCallback.isVerifyUnlockOnly()) {
            updateFooter(FooterMode.VerifyUnlocked);
        } else if (!this.mEnableFallback || this.mTotalFailedPatternAttempts < 5) {
            updateFooter(FooterMode.Normal);
        } else {
            updateFooter(FooterMode.ForgotLockPattern);
        }
    }

    private void displayDefaultSecurityMessage() {
        if (KeyguardUpdateMonitor.getInstance(this.mContext).getMaxBiometricUnlockAttemptsReached()) {
            this.mSecurityMessageDisplay.setMessage((int) R.string.faceunlock_multiple_failures, true);
        } else {
            this.mSecurityMessageDisplay.setMessage((int) R.string.kg_pattern_instructions, (boolean) DEBUG);
        }
    }

    public void showUsabilityHint() {
    }

    public void cleanUp() {
        this.mLockPatternUtils = null;
        this.mLockPatternView.setOnPatternListener(null);
    }

    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (hasWindowFocus) {
            reset();
        }
    }

    private void maybeEnableFallback(Context context) {
        new AccountAnalyzer(AccountManager.get(context)).start();
    }

    private void handleAttemptLockout(long elapsedRealtimeDeadline) {
        this.mLockPatternView.clearPattern();
        this.mLockPatternView.setEnabled(DEBUG);
        long elapsedRealtime = SystemClock.elapsedRealtime();
        if (this.mEnableFallback) {
            updateFooter(FooterMode.ForgotLockPattern);
        }
        this.mCountdownTimer = new CountDownTimer(elapsedRealtimeDeadline - elapsedRealtime, 1000) {
            public void onTick(long millisUntilFinished) {
                int secondsRemaining = (int) (millisUntilFinished / 1000);
                KeyguardPatternView.this.mSecurityMessageDisplay.setMessage(R.string.kg_too_many_failed_attempts_countdown, true, Integer.valueOf(secondsRemaining));
            }

            public void onFinish() {
                KeyguardPatternView.this.mLockPatternView.setEnabled(true);
                KeyguardPatternView.this.displayDefaultSecurityMessage();
                KeyguardPatternView.this.mFailedPatternAttemptsSinceLastTimeout = 0;
                if (KeyguardPatternView.this.mEnableFallback) {
                    KeyguardPatternView.this.updateFooter(FooterMode.ForgotLockPattern);
                } else {
                    KeyguardPatternView.this.updateFooter(FooterMode.Normal);
                }
            }
        }.start();
    }

    public boolean needsInput() {
        return DEBUG;
    }

    public void onPause() {
        if (this.mCountdownTimer != null) {
            this.mCountdownTimer.cancel();
            this.mCountdownTimer = null;
        }
    }

    public void onResume(int reason) {
        reset();
    }

    public KeyguardSecurityCallback getCallback() {
        return this.mCallback;
    }

    public void showBouncer(int duration) {
        KeyguardSecurityViewHelper.showBouncer(this.mSecurityMessageDisplay, this.mEcaView, this.mBouncerFrame, duration);
    }

    public void hideBouncer(int duration) {
        KeyguardSecurityViewHelper.hideBouncer(this.mSecurityMessageDisplay, this.mEcaView, this.mBouncerFrame, duration);
    }
}
