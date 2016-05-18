package com.cyngn.keyguard;

import android.content.Context;
import android.text.TextUtils;
import android.text.method.SingleLineTransformationMethod;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;
import com.android.internal.telephony.IccCardConstants.State;
import com.android.internal.widget.LockPatternUtils;
import com.cyngn.keyguard.SlidingChallengeLayout.LayoutParams;
import java.util.Locale;

public class CarrierText extends TextView {
    private static CharSequence mSeparator;
    private KeyguardUpdateMonitorCallback mCallback;
    private LockPatternUtils mLockPatternUtils;

    static /* synthetic */ class AnonymousClass2 {
        static final /* synthetic */ int[] $SwitchMap$com$android$internal$telephony$IccCardConstants$State = new int[State.values().length];
        static final /* synthetic */ int[] $SwitchMap$com$cyngn$keyguard$CarrierText$StatusMode = new int[StatusMode.values().length];

        static {
            try {
                $SwitchMap$com$android$internal$telephony$IccCardConstants$State[State.ABSENT.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$IccCardConstants$State[State.PERSO_LOCKED.ordinal()] = 2;
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
                $SwitchMap$com$android$internal$telephony$IccCardConstants$State[State.UNKNOWN.ordinal()] = 8;
            } catch (NoSuchFieldError e8) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$IccCardConstants$State[State.CARD_IO_ERROR.ordinal()] = 9;
            } catch (NoSuchFieldError e9) {
            }
            try {
                $SwitchMap$com$cyngn$keyguard$CarrierText$StatusMode[StatusMode.Normal.ordinal()] = 1;
            } catch (NoSuchFieldError e10) {
            }
            try {
                $SwitchMap$com$cyngn$keyguard$CarrierText$StatusMode[StatusMode.SimNotReady.ordinal()] = 2;
            } catch (NoSuchFieldError e11) {
            }
            try {
                $SwitchMap$com$cyngn$keyguard$CarrierText$StatusMode[StatusMode.PersoLocked.ordinal()] = 3;
            } catch (NoSuchFieldError e12) {
            }
            try {
                $SwitchMap$com$cyngn$keyguard$CarrierText$StatusMode[StatusMode.SimMissing.ordinal()] = 4;
            } catch (NoSuchFieldError e13) {
            }
            try {
                $SwitchMap$com$cyngn$keyguard$CarrierText$StatusMode[StatusMode.SimPermDisabled.ordinal()] = 5;
            } catch (NoSuchFieldError e14) {
            }
            try {
                $SwitchMap$com$cyngn$keyguard$CarrierText$StatusMode[StatusMode.SimMissingLocked.ordinal()] = 6;
            } catch (NoSuchFieldError e15) {
            }
            try {
                $SwitchMap$com$cyngn$keyguard$CarrierText$StatusMode[StatusMode.SimLocked.ordinal()] = 7;
            } catch (NoSuchFieldError e16) {
            }
            try {
                $SwitchMap$com$cyngn$keyguard$CarrierText$StatusMode[StatusMode.SimPukLocked.ordinal()] = 8;
            } catch (NoSuchFieldError e17) {
            }
            try {
                $SwitchMap$com$cyngn$keyguard$CarrierText$StatusMode[StatusMode.SimIoError.ordinal()] = 9;
            } catch (NoSuchFieldError e18) {
            }
        }
    }

    private class CarrierTextTransformationMethod extends SingleLineTransformationMethod {
        private final boolean mAllCaps;
        private final Locale mLocale;

        public CarrierTextTransformationMethod(Context context, boolean allCaps) {
            this.mLocale = context.getResources().getConfiguration().locale;
            this.mAllCaps = allCaps;
        }

        public CharSequence getTransformation(CharSequence source, View view) {
            source = super.getTransformation(source, view);
            if (!this.mAllCaps || source == null) {
                return source;
            }
            return source.toString().toUpperCase(this.mLocale);
        }
    }

    private enum StatusMode {
        Normal,
        PersoLocked,
        SimMissing,
        SimMissingLocked,
        SimPukLocked,
        SimLocked,
        SimPermDisabled,
        SimNotReady,
        SimIoError
    }

    public CarrierText(Context context) {
        this(context, null);
    }

    public CarrierText(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.mCallback = new KeyguardUpdateMonitorCallback() {
            private CharSequence mPlmn;
            private State mSimState;
            private CharSequence mSpn;

            public void onRefreshCarrierInfo(CharSequence plmn, CharSequence spn) {
                this.mPlmn = plmn;
                this.mSpn = spn;
                CarrierText.this.updateCarrierText(this.mSimState, this.mPlmn, this.mSpn);
            }

            public void onSimStateChanged(State simState) {
                this.mSimState = simState;
                CarrierText.this.updateCarrierText(this.mSimState, this.mPlmn, this.mSpn);
            }

            public void onScreenTurnedOff(int why) {
                CarrierText.this.setSelected(false);
            }

            public void onScreenTurnedOn() {
                CarrierText.this.setSelected(true);
            }
        };
        this.mLockPatternUtils = new LockPatternUtils(this.mContext);
        setTransformationMethod(new CarrierTextTransformationMethod(this.mContext, this.mContext.getResources().getBoolean(R.bool.kg_use_all_caps)));
    }

    protected void updateCarrierText(State simState, CharSequence plmn, CharSequence spn) {
        setText(getCarrierTextForSimState(simState, plmn, spn));
    }

    protected void onFinishInflate() {
        super.onFinishInflate();
        mSeparator = getResources().getString(R.string.kg_text_message_separator);
        setSelected(KeyguardUpdateMonitor.getInstance(this.mContext).isScreenOn());
    }

    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!KeyguardUpdateMonitor.sIsMultiSimEnabled) {
            KeyguardUpdateMonitor.getInstance(this.mContext).registerCallback(this.mCallback);
        }
    }

    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        KeyguardUpdateMonitor.getInstance(this.mContext).removeCallback(this.mCallback);
    }

    protected CharSequence getCarrierTextForSimState(State simState, CharSequence plmn, CharSequence spn) {
        switch (AnonymousClass2.$SwitchMap$com$cyngn$keyguard$CarrierText$StatusMode[getStatusForIccState(simState).ordinal()]) {
            case SlidingChallengeLayout.SCROLL_STATE_DRAGGING /*1*/:
                return concatenate(plmn, spn);
            case SlidingChallengeLayout.SCROLL_STATE_SETTLING /*2*/:
                return null;
            case SlidingChallengeLayout.SCROLL_STATE_FADING /*3*/:
                return makeCarrierStringOnEmergencyCapable(getContext().getText(R.string.keyguard_perso_locked_message), plmn);
            case LayoutParams.CHILD_TYPE_SCRIM /*4*/:
                return makeCarrierStringOnEmergencyCapable(getContext().getText(R.string.keyguard_missing_sim_message_short), plmn);
            case LayoutParams.CHILD_TYPE_WIDGETS /*5*/:
                return getContext().getText(R.string.keyguard_permanent_disabled_sim_message_short);
            case LayoutParams.CHILD_TYPE_EXPAND_CHALLENGE_HANDLE /*6*/:
                return makeCarrierStringOnEmergencyCapable(getContext().getText(R.string.keyguard_missing_sim_message_short), plmn);
            case MultiPaneChallengeLayout.LayoutParams.CHILD_TYPE_PAGE_DELETE_DROP_TARGET /*7*/:
                return makeCarrierStringOnEmergencyCapable(getContext().getText(R.string.keyguard_sim_locked_message), plmn);
            case KeyguardViewDragHelper.EDGE_BOTTOM /*8*/:
                return makeCarrierStringOnEmergencyCapable(getContext().getText(R.string.keyguard_sim_puk_locked_message), plmn);
            case 9:
                return makeCarrierStringOnEmergencyCapable(getContext().getText(R.string.lockscreen_sim_error_message_short), plmn);
            default:
                return null;
        }
    }

    private CharSequence makeCarrierStringOnEmergencyCapable(CharSequence simMessage, CharSequence emergencyCallMessage) {
        if (this.mLockPatternUtils.isEmergencyCallCapable()) {
            return concatenate(simMessage, emergencyCallMessage);
        }
        return simMessage;
    }

    private StatusMode getStatusForIccState(State simState) {
        if (simState == null) {
            return StatusMode.Normal;
        }
        boolean missingAndNotProvisioned = !KeyguardUpdateMonitor.getInstance(this.mContext).isDeviceProvisioned() && (simState == State.ABSENT || simState == State.PERM_DISABLED);
        if (missingAndNotProvisioned) {
            simState = State.PERSO_LOCKED;
        }
        switch (AnonymousClass2.$SwitchMap$com$android$internal$telephony$IccCardConstants$State[simState.ordinal()]) {
            case SlidingChallengeLayout.SCROLL_STATE_DRAGGING /*1*/:
                return StatusMode.SimMissing;
            case SlidingChallengeLayout.SCROLL_STATE_SETTLING /*2*/:
                return StatusMode.PersoLocked;
            case SlidingChallengeLayout.SCROLL_STATE_FADING /*3*/:
                return StatusMode.SimNotReady;
            case LayoutParams.CHILD_TYPE_SCRIM /*4*/:
                return StatusMode.SimLocked;
            case LayoutParams.CHILD_TYPE_WIDGETS /*5*/:
                return StatusMode.SimPukLocked;
            case LayoutParams.CHILD_TYPE_EXPAND_CHALLENGE_HANDLE /*6*/:
                return StatusMode.Normal;
            case MultiPaneChallengeLayout.LayoutParams.CHILD_TYPE_PAGE_DELETE_DROP_TARGET /*7*/:
                return StatusMode.SimPermDisabled;
            case KeyguardViewDragHelper.EDGE_BOTTOM /*8*/:
                return StatusMode.SimMissing;
            case 9:
                return StatusMode.SimIoError;
            default:
                return StatusMode.SimMissing;
        }
    }

    private static CharSequence concatenate(CharSequence plmn, CharSequence spn) {
        boolean plmnValid;
        boolean spnValid;
        if (TextUtils.isEmpty(plmn)) {
            plmnValid = false;
        } else {
            plmnValid = true;
        }
        if (TextUtils.isEmpty(spn)) {
            spnValid = false;
        } else {
            spnValid = true;
        }
        if (plmnValid && spnValid) {
            return plmn + mSeparator + spn;
        }
        if (plmnValid) {
            return plmn;
        }
        if (spnValid) {
            return spn;
        }
        return "";
    }

    private CharSequence getCarrierHelpTextForSimState(State simState, String plmn, String spn) {
        int carrierHelpTextId = 0;
        switch (AnonymousClass2.$SwitchMap$com$cyngn$keyguard$CarrierText$StatusMode[getStatusForIccState(simState).ordinal()]) {
            case SlidingChallengeLayout.SCROLL_STATE_FADING /*3*/:
                carrierHelpTextId = R.string.keyguard_instructions_when_pattern_disabled;
                break;
            case LayoutParams.CHILD_TYPE_SCRIM /*4*/:
                carrierHelpTextId = R.string.keyguard_missing_sim_instructions_long;
                break;
            case LayoutParams.CHILD_TYPE_WIDGETS /*5*/:
                carrierHelpTextId = R.string.keyguard_permanent_disabled_sim_instructions;
                break;
            case LayoutParams.CHILD_TYPE_EXPAND_CHALLENGE_HANDLE /*6*/:
                carrierHelpTextId = R.string.keyguard_missing_sim_instructions;
                break;
        }
        return this.mContext.getText(carrierHelpTextId);
    }
}
