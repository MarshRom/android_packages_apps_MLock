package com.cyngn.keyguard;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

public class KeyguardLinearLayout extends LinearLayout {
    int mTopChild;

    public KeyguardLinearLayout(Context context) {
        this(context, null, 0);
    }

    public KeyguardLinearLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public KeyguardLinearLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        this.mTopChild = 0;
    }

    public void setTopChild(View child) {
        this.mTopChild = indexOfChild(child);
        invalidate();
    }
}
