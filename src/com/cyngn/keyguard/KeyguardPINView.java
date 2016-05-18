package com.cyngn.keyguard;

import android.content.Context;
import android.provider.Settings.System;
import android.text.TextWatcher;
import android.text.method.DigitsKeyListener;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.LinearLayout;
import android.widget.TextView.OnEditorActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class KeyguardPINView extends KeyguardAbsKeyInputView implements TextWatcher, OnEditorActionListener, KeyguardSecurityView {
    private static List<Integer> sNumbers = Arrays.asList(new Integer[]{Integer.valueOf(1), Integer.valueOf(2), Integer.valueOf(3), Integer.valueOf(4), Integer.valueOf(5), Integer.valueOf(6), Integer.valueOf(7), Integer.valueOf(8), Integer.valueOf(9), Integer.valueOf(0)});

    public KeyguardPINView(Context context) {
        this(context, null);
    }

    public KeyguardPINView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    protected void resetState() {
        if (KeyguardUpdateMonitor.getInstance(getContext()).getMaxBiometricUnlockAttemptsReached()) {
            this.mSecurityMessageDisplay.setMessage((int) R.string.faceunlock_multiple_failures, true);
        } else {
            this.mSecurityMessageDisplay.setMessage((int) R.string.kg_pin_instructions, false);
        }
        this.mPasswordEntry.setEnabled(true);
    }

    protected int getPasswordTextViewId() {
        return R.id.pinEntry;
    }

    protected void onFinishInflate() {
        boolean scramblePin = true;
        super.onFinishInflate();
        View ok = findViewById(R.id.key_enter);
        if (ok != null) {
            ok.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    KeyguardPINView.this.doHapticKeyClick();
                    if (KeyguardPINView.this.mPasswordEntry.isEnabled()) {
                        KeyguardPINView.this.verifyPasswordAndUnlock();
                    }
                }
            });
            ok.setOnHoverListener(new LiftToActivateListener(getContext()));
        }
        if (System.getInt(getContext().getContentResolver(), "lockscreen_scramble_pin_layout", 0) != 1) {
            scramblePin = false;
        }
        if (scramblePin) {
            int i;
            Collections.shuffle(sNumbers);
            LinearLayout bouncer = (LinearLayout) findViewById(R.id.keyguard_bouncer_frame);
            List<NumPadKey> views = new ArrayList();
            for (i = 0; i < bouncer.getChildCount(); i++) {
                if (bouncer.getChildAt(i) instanceof LinearLayout) {
                    LinearLayout nestedLayout = (LinearLayout) bouncer.getChildAt(i);
                    for (int j = 0; j < nestedLayout.getChildCount(); j++) {
                        View view = nestedLayout.getChildAt(j);
                        if (view.getClass() == NumPadKey.class) {
                            views.add((NumPadKey) view);
                        }
                    }
                }
            }
            for (i = 0; i < sNumbers.size(); i++) {
                ((NumPadKey) views.get(i)).setDigit(((Integer) sNumbers.get(i)).intValue());
            }
        }
        View pinDelete = findViewById(R.id.delete_button);
        if (pinDelete != null) {
            pinDelete.setVisibility(0);
            pinDelete.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    if (KeyguardPINView.this.mPasswordEntry.isEnabled()) {
                        CharSequence str = KeyguardPINView.this.mPasswordEntry.getText();
                        if (str.length() > 0) {
                            KeyguardPINView.this.mPasswordEntry.setText(str.subSequence(0, str.length() - 1));
                        }
                    }
                    KeyguardPINView.this.doHapticKeyClick();
                }
            });
            pinDelete.setOnLongClickListener(new OnLongClickListener() {
                public boolean onLongClick(View v) {
                    if (KeyguardPINView.this.mPasswordEntry.isEnabled()) {
                        KeyguardPINView.this.mPasswordEntry.setText("");
                    }
                    KeyguardPINView.this.doHapticKeyClick();
                    return true;
                }
            });
        }
        this.mPasswordEntry.setKeyListener(DigitsKeyListener.getInstance());
        this.mPasswordEntry.setInputType(18);
        this.mPasswordEntry.requestFocus();
    }

    public void showUsabilityHint() {
    }

    public int getWrongPasswordStringId() {
        return R.string.kg_wrong_pin;
    }
}
