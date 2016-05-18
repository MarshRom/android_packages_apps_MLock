package com.cyngn.keyguard;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.SpannableStringBuilder;
import android.text.style.TextAppearanceSpan;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import com.android.internal.widget.LockPatternUtils;

public class NumPadKey extends Button {
    static String[] sKlondike;
    int mDigit;
    boolean mEnableHaptics;
    private OnClickListener mListener;
    TextView mTextView;
    int mTextViewResId;

    public NumPadKey(Context context) {
        this(context, null);
    }

    public NumPadKey(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NumPadKey(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        this.mDigit = -1;
        this.mTextView = null;
        this.mListener = new OnClickListener() {
            public void onClick(View thisView) {
                if (NumPadKey.this.mTextView == null && NumPadKey.this.mTextViewResId > 0) {
                    View v = NumPadKey.this.getRootView().findViewById(NumPadKey.this.mTextViewResId);
                    if (v != null && (v instanceof TextView)) {
                        NumPadKey.this.mTextView = (TextView) v;
                    }
                }
                if (NumPadKey.this.mTextView != null && NumPadKey.this.mTextView.isEnabled()) {
                    NumPadKey.this.mTextView.append(String.valueOf(NumPadKey.this.mDigit));
                }
                NumPadKey.this.doHapticKeyClick();
            }
        };
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.NumPadKey);
        this.mDigit = a.getInt(0, this.mDigit);
        setTextViewResId(a.getResourceId(1, 0));
        setOnClickListener(this.mListener);
        setOnHoverListener(new LiftToActivateListener(context));
        setAccessibilityDelegate(new ObscureSpeechDelegate(context));
        this.mEnableHaptics = new LockPatternUtils(context).isTactileFeedbackEnabled();
        updateText();
    }

    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        ObscureSpeechDelegate.sAnnouncedHeadset = false;
    }

    public void setDigit(int digit) {
        this.mDigit = digit;
        updateText();
    }

    private void updateText() {
        SpannableStringBuilder builder = new SpannableStringBuilder();
        builder.append(String.valueOf(this.mDigit));
        if (this.mDigit >= 0) {
            if (sKlondike == null) {
                sKlondike = getContext().getResources().getStringArray(R.array.lockscreen_num_pad_klondike);
            }
            if (sKlondike != null && sKlondike.length > this.mDigit) {
                String extra = sKlondike[this.mDigit];
                int extraLen = extra.length();
                if (extraLen > 0) {
                    builder.append(" ");
                    builder.append(extra);
                    builder.setSpan(new TextAppearanceSpan(getContext(), R.style.TextAppearance_NumPadKey_Klondike), builder.length() - extraLen, builder.length(), 0);
                }
            }
            setText(builder);
        }
    }

    public void setTextView(TextView tv) {
        this.mTextView = tv;
    }

    public void setTextViewResId(int resId) {
        this.mTextView = null;
        this.mTextViewResId = resId;
    }

    public void doHapticKeyClick() {
        if (this.mEnableHaptics) {
            performHapticFeedback(1, 3);
        }
    }
}
