package com.cyngn.keyguard;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;
import com.android.internal.widget.LockPatternUtils;
import java.lang.ref.WeakReference;
import libcore.util.MutableInt;

class KeyguardMessageArea extends TextView {
    private static final long ANNOUNCEMENT_DELAY = 250;
    private static final Object ANNOUNCE_TOKEN = new Object();
    static final int BATTERY_LOW_ICON = 0;
    static final int CHARGING_ICON = 0;
    static final int DISCHARGING_ICON = 0;
    protected static final int FADE_DURATION = 750;
    static final int SECURITY_MESSAGE_DURATION = 5000;
    private static final String TAG = "KeyguardMessageArea";
    boolean mAlwaysShowBattery;
    protected boolean mBatteryCharged;
    protected boolean mBatteryIsLow;
    int mBatteryLevel;
    boolean mCharging;
    Runnable mClearMessageRunnable;
    private Handler mHandler;
    private KeyguardUpdateMonitorCallback mInfoCallback;
    private LockPatternUtils mLockPatternUtils;
    CharSequence mMessage;
    private CharSequence mSeparator;
    boolean mShowingBatteryInfo;
    boolean mShowingBouncer;
    boolean mShowingMessage;
    long mTimeout;
    KeyguardUpdateMonitor mUpdateMonitor;

    private static class AnnounceRunnable implements Runnable {
        private final WeakReference<View> mHost;
        private final CharSequence mTextToAnnounce;

        public AnnounceRunnable(View host, CharSequence textToAnnounce) {
            this.mHost = new WeakReference(host);
            this.mTextToAnnounce = textToAnnounce;
        }

        public void run() {
            View host = (View) this.mHost.get();
            if (host != null) {
                host.announceForAccessibility(this.mTextToAnnounce);
            }
        }
    }

    public static class Helper implements SecurityMessageDisplay {
        KeyguardMessageArea mMessageArea;

        Helper(View v) {
            this.mMessageArea = (KeyguardMessageArea) v.findViewById(R.id.keyguard_message_area);
            if (this.mMessageArea == null) {
                throw new RuntimeException("Can't find keyguard_message_area in " + v.getClass());
            }
        }

        public void setMessage(CharSequence msg, boolean important) {
            if (!TextUtils.isEmpty(msg) && important) {
                this.mMessageArea.mMessage = msg;
                this.mMessageArea.securityMessageChanged();
            }
        }

        public void setMessage(int resId, boolean important) {
            if (resId != 0 && important) {
                this.mMessageArea.mMessage = this.mMessageArea.getContext().getResources().getText(resId);
                this.mMessageArea.securityMessageChanged();
            }
        }

        public void setMessage(int resId, boolean important, Object... formatArgs) {
            if (resId != 0 && important) {
                this.mMessageArea.mMessage = this.mMessageArea.getContext().getString(resId, formatArgs);
                this.mMessageArea.securityMessageChanged();
            }
        }

        public void showBouncer(int duration) {
            this.mMessageArea.hideMessage(duration, false);
            this.mMessageArea.mShowingBouncer = true;
        }

        public void hideBouncer(int duration) {
            this.mMessageArea.showMessage(duration);
            this.mMessageArea.mShowingBouncer = false;
        }

        public void setTimeout(int timeoutMs) {
            this.mMessageArea.mTimeout = (long) timeoutMs;
        }
    }

    public KeyguardMessageArea(Context context) {
        this(context, null);
    }

    public KeyguardMessageArea(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.mShowingBatteryInfo = false;
        this.mAlwaysShowBattery = false;
        this.mShowingBouncer = false;
        this.mCharging = false;
        this.mBatteryLevel = 100;
        this.mTimeout = 5000;
        this.mClearMessageRunnable = new Runnable() {
            public void run() {
                KeyguardMessageArea.this.mMessage = null;
                KeyguardMessageArea.this.mShowingMessage = false;
                if (KeyguardMessageArea.this.mShowingBouncer) {
                    KeyguardMessageArea.this.hideMessage(KeyguardMessageArea.FADE_DURATION, true);
                } else {
                    KeyguardMessageArea.this.update();
                }
            }
        };
        this.mInfoCallback = new KeyguardUpdateMonitorCallback() {
            public void onRefreshBatteryInfo(BatteryStatus status) {
                boolean z = false;
                KeyguardMessageArea keyguardMessageArea = KeyguardMessageArea.this;
                boolean z2 = status.status == 2 || status.status == 5;
                keyguardMessageArea.mCharging = z2;
                KeyguardMessageArea.this.mBatteryLevel = status.level;
                KeyguardMessageArea.this.mBatteryCharged = status.isCharged();
                KeyguardMessageArea.this.mBatteryIsLow = status.isBatteryLow();
                KeyguardMessageArea.this.mAlwaysShowBattery = KeyguardUpdateMonitor.shouldAlwaysShowBatteryInfo(KeyguardMessageArea.this.getContext());
                if (KeyguardUpdateMonitor.shouldNeverShowBatteryInfo(KeyguardMessageArea.this.getContext())) {
                    KeyguardMessageArea.this.mShowingBatteryInfo = false;
                } else {
                    KeyguardMessageArea keyguardMessageArea2 = KeyguardMessageArea.this;
                    if (status.isPluggedIn() || status.isBatteryLow() || KeyguardMessageArea.this.mAlwaysShowBattery) {
                        z = true;
                    }
                    keyguardMessageArea2.mShowingBatteryInfo = z;
                }
                KeyguardMessageArea.this.update();
            }

            public void onScreenTurnedOff(int why) {
                KeyguardMessageArea.this.setSelected(false);
            }

            public void onScreenTurnedOn() {
                KeyguardMessageArea.this.setSelected(true);
            }
        };
        setLayerType(2, null);
        this.mLockPatternUtils = new LockPatternUtils(context);
        this.mUpdateMonitor = KeyguardUpdateMonitor.getInstance(getContext());
        this.mUpdateMonitor.registerCallback(this.mInfoCallback);
        this.mHandler = new Handler(Looper.myLooper());
        this.mSeparator = getResources().getString(R.string.kg_text_message_separator);
        update();
    }

    protected void onFinishInflate() {
        setSelected(KeyguardUpdateMonitor.getInstance(this.mContext).isScreenOn());
    }

    public void securityMessageChanged() {
        setAlpha(1.0f);
        this.mShowingMessage = true;
        update();
        this.mHandler.removeCallbacks(this.mClearMessageRunnable);
        if (this.mTimeout > 0) {
            this.mHandler.postDelayed(this.mClearMessageRunnable, this.mTimeout);
        }
        this.mHandler.removeCallbacksAndMessages(ANNOUNCE_TOKEN);
        this.mHandler.postAtTime(new AnnounceRunnable(this, getText()), ANNOUNCE_TOKEN, SystemClock.uptimeMillis() + ANNOUNCEMENT_DELAY);
    }

    void update() {
        CharSequence status = concat(getChargeInfo(new MutableInt(DISCHARGING_ICON)), getOwnerInfo(), getCurrentMessage());
        setCompoundDrawablesWithIntrinsicBounds(icon.value, DISCHARGING_ICON, DISCHARGING_ICON, DISCHARGING_ICON);
        setText(status);
    }

    private CharSequence concat(CharSequence... args) {
        StringBuilder b = new StringBuilder();
        if (!TextUtils.isEmpty(args[DISCHARGING_ICON])) {
            b.append(args[DISCHARGING_ICON]);
        }
        for (int i = 1; i < args.length; i++) {
            CharSequence text = args[i];
            if (!TextUtils.isEmpty(text)) {
                if (b.length() > 0) {
                    b.append(this.mSeparator);
                }
                b.append(text);
            }
        }
        return b.toString();
    }

    CharSequence getCurrentMessage() {
        return this.mShowingMessage ? this.mMessage : null;
    }

    String getOwnerInfo() {
        ContentResolver res = getContext().getContentResolver();
        if (!this.mLockPatternUtils.isOwnerInfoEnabled() || this.mShowingMessage) {
            return null;
        }
        return this.mLockPatternUtils.getOwnerInfo(this.mLockPatternUtils.getCurrentUser());
    }

    private CharSequence getChargeInfo(MutableInt icon) {
        if (!this.mShowingBatteryInfo || this.mShowingMessage) {
            return null;
        }
        CharSequence string;
        if (this.mCharging) {
            string = getContext().getString(this.mBatteryCharged ? R.string.keyguard_charged : R.string.keyguard_plugged_in, new Object[]{Integer.valueOf(this.mBatteryLevel)});
            icon.value = DISCHARGING_ICON;
            return string;
        } else if (this.mBatteryIsLow) {
            string = getContext().getString(R.string.keyguard_low_battery, new Object[]{Integer.valueOf(this.mBatteryLevel)});
            icon.value = DISCHARGING_ICON;
            return string;
        } else if (!this.mAlwaysShowBattery) {
            return null;
        } else {
            string = getContext().getString(R.string.keyguard_discharging, new Object[]{Integer.valueOf(this.mBatteryLevel)});
            icon.value = DISCHARGING_ICON;
            return string;
        }
    }

    private void hideMessage(int duration, boolean thenUpdate) {
        if (duration > 0) {
            Animator anim = ObjectAnimator.ofFloat(this, "alpha", new float[]{0.0f});
            anim.setDuration((long) duration);
            if (thenUpdate) {
                anim.addListener(new AnimatorListenerAdapter() {
                    public void onAnimationEnd(Animator animation) {
                        KeyguardMessageArea.this.update();
                    }
                });
            }
            anim.start();
            return;
        }
        setAlpha(0.0f);
        if (thenUpdate) {
            update();
        }
    }

    private void showMessage(int duration) {
        if (duration > 0) {
            Animator anim = ObjectAnimator.ofFloat(this, "alpha", new float[]{1.0f});
            anim.setDuration((long) duration);
            anim.start();
            return;
        }
        setAlpha(1.0f);
    }
}
