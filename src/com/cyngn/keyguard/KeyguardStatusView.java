package com.cyngn.keyguard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings.System;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.widget.GridLayout;
import android.widget.TextClock;
import android.widget.TextView;
import com.android.internal.widget.LockPatternUtils;
import java.util.Locale;

public class KeyguardStatusView extends GridLayout {
    private static final boolean DEBUG = false;
    private static final String TAG = "KeyguardStatusView";
    private TextView mAlarmStatusView;
    private BroadcastReceiver mBroadcastReceiver;
    private TextClock mClockView;
    private ContentObserver mContentObserver;
    private TextClock mDateView;
    private boolean mEnableRefresh;
    private KeyguardUpdateMonitorCallback mInfoCallback;
    private LockPatternUtils mLockPatternUtils;

    private static final class Patterns {
        static String cacheKey;
        static String clockView12;
        static String clockView24;
        static String dateView;

        private Patterns() {
        }

        static void update(Context context) {
            Locale locale = Locale.getDefault();
            Resources res = context.getResources();
            String dateViewSkel = res.getString(R.string.abbrev_wday_month_day_no_year);
            String clockView12Skel = res.getString(R.string.clock_12hr_format);
            String clockView24Skel = res.getString(R.string.clock_24hr_format);
            String key = locale.toString() + dateViewSkel + clockView12Skel + clockView24Skel;
            if (!key.equals(cacheKey)) {
                dateView = DateFormat.getBestDateTimePattern(locale, dateViewSkel);
                clockView12 = DateFormat.getBestDateTimePattern(locale, clockView12Skel);
                if (!clockView12Skel.contains("a")) {
                    clockView12 = clockView12.replaceAll("a", "").trim();
                }
                clockView24 = DateFormat.getBestDateTimePattern(locale, clockView24Skel);
                cacheKey = key;
            }
        }
    }

    public KeyguardStatusView(Context context) {
        this(context, null, 0);
    }

    public KeyguardStatusView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public KeyguardStatusView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        this.mEnableRefresh = DEBUG;
        this.mInfoCallback = new KeyguardUpdateMonitorCallback() {
            public void onTimeChanged() {
                if (KeyguardStatusView.this.mEnableRefresh) {
                    KeyguardStatusView.this.refresh();
                }
            }

            public void onScreenTurnedOn() {
                KeyguardStatusView.this.setEnableMarquee(true);
                KeyguardStatusView.this.mEnableRefresh = true;
                KeyguardStatusView.this.refresh();
            }

            public void onScreenTurnedOff(int why) {
                KeyguardStatusView.this.setEnableMarquee(KeyguardStatusView.DEBUG);
                KeyguardStatusView.this.mEnableRefresh = KeyguardStatusView.DEBUG;
            }
        };
        this.mBroadcastReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                KeyguardStatusView.this.refresh();
            }
        };
        this.mContentObserver = new ContentObserver(new Handler()) {
            public void onChange(boolean selfChange) {
                KeyguardStatusView.this.refresh();
            }
        };
    }

    private void setEnableMarquee(boolean enabled) {
        if (this.mAlarmStatusView != null) {
            this.mAlarmStatusView.setSelected(enabled);
        }
    }

    protected void onFinishInflate() {
        super.onFinishInflate();
        this.mAlarmStatusView = (TextView) findViewById(R.id.alarm_status);
        this.mDateView = (TextClock) findViewById(R.id.date_view);
        this.mClockView = (TextClock) findViewById(R.id.clock_view);
        this.mLockPatternUtils = new LockPatternUtils(getContext());
        setEnableMarquee(KeyguardUpdateMonitor.getInstance(this.mContext).isScreenOn());
        refresh();
    }

    protected void refresh() {
        Patterns.update(this.mContext);
        this.mDateView.setFormat24Hour(Patterns.dateView);
        this.mDateView.setFormat12Hour(Patterns.dateView);
        this.mClockView.setFormat12Hour(Patterns.clockView12);
        this.mClockView.setFormat24Hour(Patterns.clockView24);
        refreshAlarmStatus();
    }

    void refreshAlarmStatus() {
        String nextAlarm = this.mLockPatternUtils.getNextAlarm();
        if (TextUtils.isEmpty(nextAlarm)) {
            this.mAlarmStatusView.setVisibility(8);
            return;
        }
        this.mAlarmStatusView.setText(nextAlarm);
        this.mAlarmStatusView.setVisibility(0);
    }

    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        KeyguardUpdateMonitor.getInstance(this.mContext).registerCallback(this.mInfoCallback);
        IntentFilter f = new IntentFilter();
        f.addAction("android.intent.action.LOCALE_CHANGED");
        this.mContext.registerReceiver(this.mBroadcastReceiver, f);
        this.mContext.getContentResolver().registerContentObserver(System.getUriFor("time_12_24"), DEBUG, this.mContentObserver);
        this.mContext.getContentResolver().registerContentObserver(System.getUriFor("next_alarm_formatted"), DEBUG, this.mContentObserver);
    }

    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        KeyguardUpdateMonitor.getInstance(this.mContext).removeCallback(this.mInfoCallback);
        this.mContext.unregisterReceiver(this.mBroadcastReceiver);
        this.mContext.getContentResolver().unregisterContentObserver(this.mContentObserver);
    }

    public int getAppWidgetId() {
        return -2;
    }
}
