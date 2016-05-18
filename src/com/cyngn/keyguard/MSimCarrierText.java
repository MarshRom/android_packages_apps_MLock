package com.cyngn.keyguard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.telephony.MSimTelephonyManager;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.android.internal.telephony.IccCardConstants.State;
import com.cyngn.keyguard.SlidingChallengeLayout.LayoutParams;

public class MSimCarrierText extends LinearLayout {
    private static final boolean DEBUG = false;
    private static final String TAG = MSimCarrierText.class.getSimpleName();
    private BroadcastReceiver mBroadcastReceiver;
    private CharSequence[] mCarrierTextSub;
    private String mEmergencyCallOnlyLabel;
    private KeyguardUpdateMonitorCallback mMSimCallback;
    private String[] mMSimNetworkName;
    private String mNetworkNameDefault;
    private String mNetworkNameSeparator;
    private String[] mPlmn;
    private boolean[] mShowPlmn;
    private boolean[] mShowSpn;
    private State[] mSimState;
    private String[] mSpn;
    private TextView[] mTextViewLabels;

    static /* synthetic */ class AnonymousClass3 {
        static final /* synthetic */ int[] $SwitchMap$com$android$internal$telephony$IccCardConstants$State = new int[State.values().length];

        static {
            try {
                $SwitchMap$com$android$internal$telephony$IccCardConstants$State[State.ABSENT.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$IccCardConstants$State[State.UNKNOWN.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$IccCardConstants$State[State.NOT_READY.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$IccCardConstants$State[State.PIN_REQUIRED.ordinal()] = 4;
            } catch (NoSuchFieldError e4) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$IccCardConstants$State[State.PUK_REQUIRED.ordinal()] = 5;
            } catch (NoSuchFieldError e5) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$IccCardConstants$State[State.READY.ordinal()] = 6;
            } catch (NoSuchFieldError e6) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$IccCardConstants$State[State.PERM_DISABLED.ordinal()] = 7;
            } catch (NoSuchFieldError e7) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$IccCardConstants$State[State.CARD_IO_ERROR.ordinal()] = 8;
            } catch (NoSuchFieldError e8) {
            }
        }
    }

    protected void onFinishInflate() {
        super.onFinishInflate();
        this.mTextViewLabels = new TextView[3];
        this.mTextViewLabels[0] = (TextView) findViewById(R.id.sub1_label);
        this.mTextViewLabels[1] = (TextView) findViewById(R.id.sub2_label);
        this.mTextViewLabels[2] = (TextView) findViewById(R.id.sub3_label);
        if (MSimTelephonyManager.getDefault().getPhoneCount() == 3) {
            findViewById(R.id.sub2_separator).setVisibility(0);
            findViewById(R.id.sub3_label).setVisibility(0);
        }
    }

    private void initialize() {
        int i;
        this.mNetworkNameDefault = this.mContext.getString(17040387);
        this.mEmergencyCallOnlyLabel = this.mContext.getString(17040413);
        this.mNetworkNameSeparator = this.mContext.getString(R.string.network_name_separator);
        int numPhones = MSimTelephonyManager.getDefault().getPhoneCount();
        this.mMSimNetworkName = new String[numPhones];
        this.mPlmn = new String[numPhones];
        this.mSpn = new String[numPhones];
        this.mSimState = new State[numPhones];
        for (i = 0; i < this.mSimState.length; i++) {
            this.mSimState[i] = State.UNKNOWN;
        }
        this.mShowSpn = new boolean[numPhones];
        this.mShowPlmn = new boolean[numPhones];
        this.mCarrierTextSub = new CharSequence[numPhones];
        MSimTelephonyManager tm = MSimTelephonyManager.getDefault();
        for (i = 0; i < tm.getPhoneCount(); i++) {
            this.mSpn[i] = tm.getSimOperatorName(i);
            this.mPlmn[i] = tm.getNetworkOperatorName(i);
            updateNetworkName(true, this.mSpn[i], true, this.mPlmn[i], i);
        }
    }

    public MSimCarrierText(Context context) {
        this(context, null);
    }

    public MSimCarrierText(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.mMSimCallback = new KeyguardUpdateMonitorCallback() {
            public void onRefreshCarrierInfo(CharSequence plmn, CharSequence spn, int sub) {
            }

            public void onSimStateChanged(State simState, int sub) {
                MSimCarrierText.this.mSimState[sub] = simState;
                MSimCarrierText.this.updateCarrierText(sub);
                MSimCarrierText.this.setCarrierText();
            }
        };
        this.mBroadcastReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals("android.provider.Telephony.SPN_STRINGS_UPDATED")) {
                    int subscription = intent.getIntExtra("subscription", 0);
                    MSimCarrierText.this.mShowSpn[subscription] = intent.getBooleanExtra("showSpn", false);
                    MSimCarrierText.this.mSpn[subscription] = intent.getStringExtra("spn");
                    MSimCarrierText.this.mShowPlmn[subscription] = intent.getBooleanExtra("showPlmn", false);
                    MSimCarrierText.this.mPlmn[subscription] = intent.getStringExtra("plmn");
                    MSimCarrierText.this.updateNetworkName(MSimCarrierText.this.mShowSpn[subscription], MSimCarrierText.this.mSpn[subscription], MSimCarrierText.this.mShowPlmn[subscription], MSimCarrierText.this.mPlmn[subscription], subscription);
                    MSimCarrierText.this.updateCarrierText(subscription);
                    MSimCarrierText.this.setCarrierText();
                }
            }
        };
        initialize();
    }

    private void updateCarrierText(int sub) {
        int textResId = 0;
        switch (AnonymousClass3.$SwitchMap$com$android$internal$telephony$IccCardConstants$State[this.mSimState[sub].ordinal()]) {
            case SlidingChallengeLayout.SCROLL_STATE_DRAGGING /*1*/:
            case SlidingChallengeLayout.SCROLL_STATE_SETTLING /*2*/:
            case SlidingChallengeLayout.SCROLL_STATE_FADING /*3*/:
                textResId = 17040402;
                break;
            case LayoutParams.CHILD_TYPE_SCRIM /*4*/:
                textResId = R.string.keyguard_sim_locked_message;
                break;
            case LayoutParams.CHILD_TYPE_WIDGETS /*5*/:
                textResId = R.string.keyguard_sim_puk_locked_message;
                break;
            case LayoutParams.CHILD_TYPE_EXPAND_CHALLENGE_HANDLE /*6*/:
                this.mCarrierTextSub[sub] = this.mMSimNetworkName[sub];
                break;
            case MultiPaneChallengeLayout.LayoutParams.CHILD_TYPE_PAGE_DELETE_DROP_TARGET /*7*/:
                textResId = R.string.keyguard_permanent_disabled_sim_message_short;
                break;
            case KeyguardViewDragHelper.EDGE_BOTTOM /*8*/:
                textResId = R.string.lockscreen_sim_error_message_short;
                break;
            default:
                textResId = 17040402;
                break;
        }
        if (textResId != 0) {
            this.mCarrierTextSub[sub] = this.mContext.getString(textResId);
        }
    }

    void updateNetworkName(boolean showSpn, String spn, boolean showPlmn, String plmn, int subscription) {
        StringBuilder str = new StringBuilder();
        boolean something = false;
        if (showPlmn && plmn != null) {
            plmn = maybeStripPeriod(plmn);
            str.append(plmn);
            something = true;
        }
        if (showSpn && spn != null) {
            if (something && showPlmn && !spn.equals(plmn) && !this.mEmergencyCallOnlyLabel.equals(plmn)) {
                str.append("  ");
                str.append(this.mNetworkNameSeparator);
                str.append("  ");
                str.append(spn);
            } else if (!showPlmn) {
                str.append(spn);
                something = true;
            }
        }
        if (something) {
            this.mMSimNetworkName[subscription] = str.toString();
        } else {
            this.mMSimNetworkName[subscription] = this.mNetworkNameDefault;
        }
        this.mMSimNetworkName[subscription] = maybeStripPeriod(this.mMSimNetworkName[subscription]);
    }

    protected String maybeStripPeriod(String name) {
        if (TextUtils.isEmpty(name) || !name.equals(this.mNetworkNameDefault)) {
            return name;
        }
        return name.replace(".", "");
    }

    private void setCarrierText() {
        if (this.mTextViewLabels != null) {
            if (this.mTextViewLabels[0] != null) {
                this.mTextViewLabels[0].setText(this.mCarrierTextSub[0]);
            }
            if (this.mTextViewLabels[1] != null) {
                this.mTextViewLabels[1].setText(this.mCarrierTextSub[1]);
            }
            if (this.mCarrierTextSub.length == 3 && this.mTextViewLabels[2] != null) {
                this.mTextViewLabels[2].setText(this.mCarrierTextSub[2]);
            }
        }
    }

    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        KeyguardUpdateMonitor.getInstance(this.mContext).registerCallback(this.mMSimCallback);
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.provider.Telephony.SPN_STRINGS_UPDATED");
        this.mContext.registerReceiver(this.mBroadcastReceiver, filter);
    }

    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        this.mContext.unregisterReceiver(this.mBroadcastReceiver);
        KeyguardUpdateMonitor.getInstance(this.mContext).removeCallback(this.mMSimCallback);
    }
}
