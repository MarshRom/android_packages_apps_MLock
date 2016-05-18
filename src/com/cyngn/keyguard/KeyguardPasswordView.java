package com.cyngn.keyguard;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.TextKeyListener;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewGroup.MarginLayoutParams;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;
import android.widget.TextView.OnEditorActionListener;
import java.util.List;

public class KeyguardPasswordView extends KeyguardAbsKeyInputView implements TextWatcher, OnEditorActionListener, KeyguardSecurityView {
    InputMethodManager mImm;
    private final boolean mShowImeAtScreenOn;

    public KeyguardPasswordView(Context context) {
        this(context, null);
    }

    public KeyguardPasswordView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.mShowImeAtScreenOn = context.getResources().getBoolean(R.bool.kg_show_ime_at_screen_on);
    }

    protected void resetState() {
        this.mSecurityMessageDisplay.setMessage((int) R.string.kg_password_instructions, false);
        this.mPasswordEntry.setEnabled(true);
    }

    protected int getPasswordTextViewId() {
        return R.id.passwordEntry;
    }

    public boolean needsInput() {
        return true;
    }

    public void onResume(int reason) {
        super.onResume(reason);
        this.mPasswordEntry.requestFocus();
        if (reason != 1 || this.mShowImeAtScreenOn) {
            this.mImm.showSoftInput(this.mPasswordEntry, 1);
        }
    }

    public void onPause() {
        super.onPause();
        this.mImm.hideSoftInputFromWindow(getWindowToken(), 0);
    }

    protected void onFinishInflate() {
        super.onFinishInflate();
        boolean imeOrDeleteButtonVisible = false;
        this.mImm = (InputMethodManager) getContext().getSystemService("input_method");
        this.mPasswordEntry.setKeyListener(TextKeyListener.getInstance());
        this.mPasswordEntry.setInputType(129);
        this.mPasswordEntry.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                KeyguardPasswordView.this.mCallback.userActivity(0);
            }
        });
        this.mPasswordEntry.addTextChangedListener(new TextWatcher() {
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void afterTextChanged(Editable s) {
                if (KeyguardPasswordView.this.mCallback != null) {
                    KeyguardPasswordView.this.mCallback.userActivity(0);
                }
            }
        });
        this.mPasswordEntry.requestFocus();
        View switchImeButton = findViewById(R.id.switch_ime_button);
        if (switchImeButton != null && hasMultipleEnabledIMEsOrSubtypes(this.mImm, false)) {
            switchImeButton.setVisibility(0);
            imeOrDeleteButtonVisible = true;
            switchImeButton.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    KeyguardPasswordView.this.mCallback.userActivity(0);
                    KeyguardPasswordView.this.mImm.showInputMethodPicker();
                }
            });
        }
        if (!imeOrDeleteButtonVisible) {
            LayoutParams params = this.mPasswordEntry.getLayoutParams();
            if (params instanceof MarginLayoutParams) {
                ((MarginLayoutParams) params).setMarginStart(0);
                this.mPasswordEntry.setLayoutParams(params);
            }
        }
    }

    private boolean hasMultipleEnabledIMEsOrSubtypes(InputMethodManager imm, boolean shouldIncludeAuxiliarySubtypes) {
        boolean z = false;
        int filteredImisCount = 0;
        for (InputMethodInfo imi : imm.getEnabledInputMethodList()) {
            if (filteredImisCount > 1) {
                return true;
            }
            List<InputMethodSubtype> subtypes = imm.getEnabledInputMethodSubtypeList(imi, true);
            if (subtypes.isEmpty()) {
                filteredImisCount++;
            } else {
                int auxCount = 0;
                for (InputMethodSubtype subtype : subtypes) {
                    if (subtype.isAuxiliary()) {
                        auxCount++;
                    }
                }
                if (subtypes.size() - auxCount > 0 || (shouldIncludeAuxiliarySubtypes && auxCount > 1)) {
                    filteredImisCount++;
                }
            }
        }
        if (filteredImisCount > 1 || imm.getEnabledInputMethodSubtypeList(null, false).size() > 1) {
            z = true;
        }
        return z;
    }

    public void showUsabilityHint() {
    }

    public int getWrongPasswordStringId() {
        return R.string.kg_wrong_password;
    }
}
