package com.cyngn.keyguard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.PhoneLookup;
import android.provider.Settings.System;
import android.support.v7.graphics.Palette;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.style.RelativeSizeSpan;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import com.android.internal.widget.LockPatternUtils;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class KeyguardSmartCoverView extends LinearLayout {
    private static final boolean DEBUG = false;
    public static final int SMART_COVER_TIMEOUT = 8000;
    public static final int SYSTEM_UI_FLAGS = 1286;
    private static final String TAG = "KeyguardStatusView";
    private TextView mAlarmStatusView;
    private TextView mAmPm;
    private ImageView mBatteryImage;
    private BatteryStatus mBatteryStatus;
    private TextView mBatteryStatusView;
    private BroadcastReceiver mBroadcastReceiver;
    private TextClock mClockView;
    private ContentObserver mContentObserver;
    private TextClock mDateView;
    private boolean mEnableBatterySuperscript;
    private boolean mEnableFullDay;
    private boolean mEnableRefresh;
    private boolean mFadeInWeather;
    private Handler mHandler;
    private KeyguardUpdateMonitorCallback mInfoCallback;
    private TextView mLine1;
    private TextView mLine2;
    private TextView mLine3;
    private LockPatternUtils mLockPatternUtils;
    private int mMissedCalls;
    private Runnable mPostBootCompletedRunnable;
    private int mUnreadMessages;
    private ImageView mWeatherImage;
    private TextView mWeatherStatus;

    private static final class Patterns {
        static String cacheKey;
        static String clockView12;
        static String clockView24;
        static String dateView;
        static String dateViewFull;

        private Patterns() {
        }

        static void update(Context context) {
            Locale locale = Locale.getDefault();
            Resources res = context.getResources();
            String dateViewSkel = res.getString(R.string.abbrev_wday_month_day_no_year);
            String dateViewFullSkel = res.getString(R.string.full_wday_month_day_no_year);
            String clockView12Skel = res.getString(R.string.clock_12hr_format);
            String clockView24Skel = res.getString(R.string.clock_24hr_format);
            String key = locale.toString() + dateViewSkel + clockView12Skel + clockView24Skel;
            if (!key.equals(cacheKey)) {
                dateView = DateFormat.getBestDateTimePattern(locale, dateViewSkel);
                dateViewFull = DateFormat.getBestDateTimePattern(locale, dateViewFullSkel);
                clockView12 = DateFormat.getBestDateTimePattern(locale, clockView12Skel);
                if (!clockView12Skel.contains("a")) {
                    clockView12 = clockView12.replaceAll("a", "").trim();
                }
                clockView24 = DateFormat.getBestDateTimePattern(locale, clockView24Skel);
                cacheKey = key;
            }
        }
    }

    public static class SuperscriptSpanAdjuster extends RelativeSizeSpan {
        private final float ratio;

        public SuperscriptSpanAdjuster(float proportion, float ratio) {
            super(proportion);
            this.ratio = ratio;
        }

        public void updateDrawState(TextPaint paint) {
            super.updateDrawState(paint);
            paint.baselineShift += (int) (paint.ascent() * this.ratio);
        }

        public void updateMeasureState(TextPaint paint) {
            super.updateMeasureState(paint);
            paint.baselineShift += (int) (paint.ascent() * this.ratio);
        }
    }

    public KeyguardSmartCoverView(Context context) {
        this(context, null, 0);
    }

    public KeyguardSmartCoverView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public KeyguardSmartCoverView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        this.mEnableRefresh = DEBUG;
        this.mFadeInWeather = DEBUG;
        this.mEnableBatterySuperscript = DEBUG;
        this.mEnableFullDay = DEBUG;
        this.mInfoCallback = new KeyguardUpdateMonitorCallback() {
            public void onBootCompleted() {
                if (KeyguardSmartCoverView.this.mPostBootCompletedRunnable != null) {
                    KeyguardSmartCoverView.this.mPostBootCompletedRunnable.run();
                    KeyguardSmartCoverView.this.mPostBootCompletedRunnable = null;
                }
            }

            public void onTimeChanged() {
                if (KeyguardSmartCoverView.this.mEnableRefresh) {
                    KeyguardSmartCoverView.this.refresh();
                }
            }

            public void onScreenTurnedOn() {
                KeyguardSmartCoverView.this.setEnableMarquee(true);
                KeyguardSmartCoverView.this.mEnableRefresh = true;
                KeyguardSmartCoverView.this.refresh();
            }

            public void onScreenTurnedOff(int why) {
                KeyguardSmartCoverView.this.setEnableMarquee(KeyguardSmartCoverView.DEBUG);
                KeyguardSmartCoverView.this.mEnableRefresh = KeyguardSmartCoverView.DEBUG;
            }

            void onRefreshBatteryInfo(BatteryStatus status) {
                KeyguardSmartCoverView.this.mBatteryStatus = status;
                KeyguardSmartCoverView.this.refreshBatteryStatus();
            }
        };
        this.mBroadcastReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                if (!"com.cyanogenmod.lockclock.action.WEATHER_UPDATE_FINISHED".equals(intent.getAction())) {
                    KeyguardSmartCoverView.this.refresh();
                } else if (!intent.getBooleanExtra("update_cancelled", true)) {
                    KeyguardSmartCoverView.this.refreshWeatherStatus();
                }
            }
        };
        this.mHandler = new Handler();
        this.mContentObserver = new ContentObserver(this.mHandler) {
            public void onChange(boolean selfChange) {
                onChange(selfChange, null);
            }

            public void onChange(boolean selfChange, Uri uri) {
                if (uri == null) {
                    KeyguardSmartCoverView.this.refresh();
                } else if (uri.equals(Calls.CONTENT_URI)) {
                    KeyguardSmartCoverView.this.refreshStatusLines();
                    KeyguardSmartCoverView.this.setSystemUiVisibility(KeyguardSmartCoverView.this.getSystemUiVisibility() | KeyguardSmartCoverView.SYSTEM_UI_FLAGS);
                } else if (uri.equals(Uri.parse("content://sms/inbox"))) {
                    KeyguardSmartCoverView.this.refreshStatusLines();
                } else {
                    KeyguardSmartCoverView.this.refresh();
                }
            }
        };
        setBackgroundColor(-16777216);
    }

    public void setPalette(Palette palette) {
        int textColor;
        if (palette == null || palette.getLightVibrantColor() == null) {
            textColor = getResources().getColor(R.color.default_text_palette_highlight);
        } else {
            textColor = palette.getLightVibrantColor().getRgb();
        }
        this.mWeatherStatus.setTextColor(textColor);
        this.mWeatherImage.setColorFilter(textColor);
        this.mBatteryStatusView.setTextColor(textColor);
        this.mBatteryImage.setColorFilter(textColor);
        int textLinesColor = -1;
        if (!(palette == null || palette.getLightMutedColor() == null)) {
            textLinesColor = palette.getLightMutedColor().getRgb();
        }
        this.mLine1.setTextColor(textLinesColor);
        this.mLine2.setTextColor(textLinesColor);
        this.mLine3.setTextColor(textLinesColor);
    }

    private void registerForWeatherUpdates() {
        this.mContext.sendBroadcast(new Intent("com.cyanogenmod.lockclock.action.REQUEST_WEATHER_UPDATE"));
    }

    private void setEnableMarquee(boolean enabled) {
        if (this.mAlarmStatusView != null) {
            this.mAlarmStatusView.setSelected(enabled);
        }
    }

    protected void onFinishInflate() {
        super.onFinishInflate();
        this.mAlarmStatusView = (TextView) findViewById(R.id.alarm_status);
        this.mBatteryStatusView = (TextView) findViewById(R.id.battery_text);
        this.mDateView = (TextClock) findViewById(R.id.date_view);
        this.mClockView = (TextClock) findViewById(R.id.clock_view);
        this.mLine1 = (TextView) findViewById(R.id.line_1);
        this.mLine2 = (TextView) findViewById(R.id.line_2);
        this.mLine3 = (TextView) findViewById(R.id.line_3);
        this.mAmPm = (TextView) findViewById(R.id.am_pm_text);
        this.mWeatherStatus = (TextView) findViewById(R.id.weather_text);
        this.mWeatherStatus.setAlpha(0.0f);
        this.mWeatherImage = (ImageView) findViewById(R.id.weather_image);
        this.mWeatherImage.setAlpha(0.0f);
        this.mBatteryImage = (ImageView) findViewById(R.id.battery_image);
        this.mBatteryImage.setImageLevel(50);
        this.mLockPatternUtils = new LockPatternUtils(getContext());
        setEnableMarquee(KeyguardUpdateMonitor.getInstance(this.mContext).isScreenOn());
        refresh();
        if (KeyguardUpdateMonitor.getInstance(this.mContext).hasBootCompleted()) {
            registerForWeatherUpdates();
        } else {
            this.mPostBootCompletedRunnable = new Runnable() {
                public void run() {
                    KeyguardSmartCoverView.this.registerForWeatherUpdates();
                }
            };
        }
    }

    protected void refresh() {
        refreshClock();
        refreshAlarmStatus();
        refreshBatteryStatus();
        refreshWeatherStatus();
        refreshStatusLines();
    }

    void refreshClock() {
        Patterns.update(this.mContext);
        if (this.mEnableFullDay) {
            this.mDateView.setFormat24Hour(Patterns.dateViewFull);
            this.mDateView.setFormat12Hour(Patterns.dateViewFull);
        } else {
            this.mDateView.setFormat24Hour(Patterns.dateView);
            this.mDateView.setFormat12Hour(Patterns.dateView);
        }
        this.mClockView.setFormat12Hour(Patterns.clockView12);
        this.mClockView.setFormat24Hour(Patterns.clockView24);
        if (this.mClockView.is24HourModeEnabled()) {
            this.mAmPm.setText(null);
            return;
        }
        this.mAmPm.setText(new SimpleDateFormat("aa").format(new Date()));
    }

    public void setEnableBatterySuperscript(boolean enableBatterySuperscript) {
        this.mEnableBatterySuperscript = enableBatterySuperscript;
    }

    public void setEnableFullDay(boolean enableFullDay) {
        this.mEnableFullDay = enableFullDay;
        refreshClock();
    }

    void refreshBatteryStatus() {
        if (this.mBatteryStatusView != null && this.mBatteryImage != null) {
            if (this.mBatteryStatus == null) {
                this.mBatteryImage.setVisibility(4);
                this.mBatteryStatusView.setVisibility(4);
                return;
            }
            this.mBatteryImage.setVisibility(0);
            this.mBatteryStatusView.setVisibility(0);
            this.mBatteryImage.setImageLevel(this.mBatteryStatus.level);
            String text = String.format(this.mContext.getString(R.string.keyguard_battery_percent), new Object[]{Integer.valueOf(this.mBatteryStatus.level)});
            SpannableStringBuilder formatted = new SpannableStringBuilder(text);
            if (this.mEnableBatterySuperscript) {
                formatted.setSpan(new SuperscriptSpanAdjuster(0.5f, 0.7f), text.length() - 1, text.length(), 34);
            } else {
                formatted.setSpan(new RelativeSizeSpan(0.7f), text.length() - 1, text.length(), 34);
            }
            this.mBatteryStatusView.setText(formatted);
        }
    }

    void refreshWeatherStatus() {
        if (this.mWeatherStatus != null && this.mWeatherImage != null) {
            String weather = getCurrentTemperature();
            if (weather != null) {
                SpannableStringBuilder formatted = new SpannableStringBuilder(weather);
                formatted.setSpan(new RelativeSizeSpan(0.7f), weather.length() - 1, weather.length(), 34);
                this.mWeatherStatus.setText(formatted);
                if (this.mFadeInWeather) {
                    this.mWeatherStatus.animate().alpha(1.0f).setDuration(500);
                    this.mWeatherImage.animate().alpha(1.0f).setDuration(500);
                    this.mFadeInWeather = DEBUG;
                    return;
                }
                this.mWeatherStatus.setAlpha(1.0f);
                this.mWeatherImage.setAlpha(1.0f);
            }
        }
    }

    void refreshAlarmStatus() {
        if (this.mAlarmStatusView != null) {
            String nextAlarm = this.mLockPatternUtils.getNextAlarm();
            if (TextUtils.isEmpty(nextAlarm)) {
                this.mAlarmStatusView.setVisibility(8);
                return;
            }
            this.mAlarmStatusView.setText(nextAlarm);
            this.mAlarmStatusView.setVisibility(0);
        }
    }

    void refreshStatusLines() {
        this.mLine1.setText(null);
        this.mLine2.setText(null);
        this.mLine3.setText(null);
        String[] missedCallText = refreshMissedCalls();
        String[] unreadMessagesText = refreshMissedTexts();
        if (this.mMissedCalls == 1) {
            this.mLine1.setText(missedCallText[0]);
            this.mLine2.setText(missedCallText[1]);
        } else if (this.mUnreadMessages == 1) {
            this.mLine1.setText(unreadMessagesText[0]);
            this.mLine2.setText(unreadMessagesText[1]);
        } else {
            if (this.mMissedCalls > 0) {
                this.mLine2.setText(this.mContext.getResources().getQuantityString(R.plurals.missed_calls, this.mMissedCalls, new Object[]{Integer.valueOf(this.mMissedCalls)}));
            }
            if (this.mUnreadMessages > 0) {
                this.mLine3.setText(this.mContext.getResources().getQuantityString(R.plurals.unread_messages, this.mUnreadMessages, new Object[]{Integer.valueOf(this.mUnreadMessages)}));
            }
        }
    }

    private String getCurrentTemperature() {
        Cursor c = this.mContext.getContentResolver().query(Uri.parse("content://com.cyanogenmod.lockclock.weather.provider/weather/current"), new String[]{"temperature"}, null, null, null);
        if (c == null) {
            this.mFadeInWeather = true;
            this.mContext.sendBroadcast(new Intent("com.cyanogenmod.lockclock.action.FORCE_WEATHER_UPDATE"));
            return null;
        }
        try {
            c.moveToFirst();
            String weather = c.getString(0);
            if (weather == null) {
                weather = "";
            }
            c.close();
            return weather;
        } catch (Throwable th) {
            c.close();
        }
    }

    private String[] refreshMissedCalls() {
        Cursor c = null;
        String[] result = new String[2];
        try {
            c = this.mContext.getContentResolver().query(Calls.CONTENT_URI, null, "type = ? AND new = ?", new String[]{Integer.toString(3), "1"}, "date DESC ");
            if (c != null) {
                c.moveToFirst();
                int count = c.getCount();
                this.mMissedCalls = count;
                if (count == 1) {
                    if (this.mLockPatternUtils.isSecure()) {
                        result[0] = "";
                        result[1] = this.mContext.getResources().getQuantityString(R.plurals.missed_calls, this.mMissedCalls, new Object[]{Integer.valueOf(this.mMissedCalls)});
                    } else {
                        String name = c.getString(c.getColumnIndex("name"));
                        result[0] = this.mContext.getString(R.string.missed_call);
                        result[1] = name;
                    }
                }
            }
            if (c != null) {
                c.close();
            }
            return result;
        } catch (Throwable th) {
            if (c != null) {
                c.close();
            }
        }
    }

    private String[] refreshMissedTexts() {
        String[] result = new String[2];
        Cursor c = null;
        try {
            c = this.mContext.getContentResolver().query(Uri.parse("content://sms/inbox"), null, "read=0", null, null);
            c.moveToFirst();
            int count = c.getCount();
            this.mUnreadMessages = count;
            if (count == 1) {
                String name = getContactDisplayNameByNumber(c.getString(c.getColumnIndex("address")));
                if (this.mLockPatternUtils.isSecure()) {
                    result[0] = this.mContext.getString(R.string.message_from);
                    result[1] = name;
                } else {
                    result[0] = String.format(this.mContext.getString(R.string.message_from_person), new Object[]{name});
                    result[1] = c.getString(c.getColumnIndex("body"));
                }
            }
            if (c != null) {
                c.close();
            }
            return result;
        } catch (Throwable th) {
            if (c != null) {
                c.close();
            }
        }
    }

    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        KeyguardUpdateMonitor.getInstance(this.mContext).registerCallback(this.mInfoCallback);
        IntentFilter f = new IntentFilter();
        f.addAction("android.intent.action.LOCALE_CHANGED");
        f.addAction("com.cyanogenmod.lockclock.action.WEATHER_UPDATE_FINISHED");
        this.mContext.registerReceiver(this.mBroadcastReceiver, f);
        this.mContext.getContentResolver().registerContentObserver(System.getUriFor("time_12_24"), DEBUG, this.mContentObserver);
        this.mContext.getContentResolver().registerContentObserver(System.getUriFor("next_alarm_formatted"), DEBUG, this.mContentObserver);
        this.mContext.getContentResolver().registerContentObserver(Calls.CONTENT_URI, true, this.mContentObserver);
        this.mContext.getContentResolver().registerContentObserver(Uri.parse("content://sms/"), true, this.mContentObserver);
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

    public String getContactDisplayNameByNumber(String number) {
        Uri uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
        String name = number;
        Cursor contactLookup = this.mContext.getContentResolver().query(uri, new String[]{"_id", "display_name"}, null, null, null);
        if (contactLookup != null) {
            try {
                if (contactLookup.getCount() > 0) {
                    contactLookup.moveToNext();
                    name = contactLookup.getString(contactLookup.getColumnIndex("display_name"));
                }
            } catch (Throwable th) {
                if (contactLookup != null) {
                    contactLookup.close();
                }
            }
        }
        if (contactLookup != null) {
            contactLookup.close();
        }
        return name;
    }
}
