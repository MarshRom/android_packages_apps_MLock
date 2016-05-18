package com.cyngn.keyguard;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewDebug.ExportedProperty;
import android.widget.ViewFlipper;
import com.android.internal.widget.LockPatternUtils;

public class KeyguardSecurityViewFlipper extends ViewFlipper implements KeyguardSecurityView {
    private static final boolean DEBUG = false;
    private static final String TAG = "KeyguardSecurityViewFlipper";
    private Rect mTempRect;

    public static class LayoutParams extends android.widget.FrameLayout.LayoutParams {
        @ExportedProperty(category = "layout")
        public int maxHeight;
        @ExportedProperty(category = "layout")
        public int maxWidth;

        public LayoutParams(android.view.ViewGroup.LayoutParams other) {
            super(other);
        }

        public LayoutParams(LayoutParams other) {
            super(other);
            this.maxWidth = other.maxWidth;
            this.maxHeight = other.maxHeight;
        }

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
            TypedArray a = c.obtainStyledAttributes(attrs, R.styleable.KeyguardSecurityViewFlipper_Layout, 0, 0);
            this.maxWidth = a.getDimensionPixelSize(1, 0);
            this.maxHeight = a.getDimensionPixelSize(0, 0);
            a.recycle();
        }
    }

    public KeyguardSecurityViewFlipper(Context context) {
        this(context, null);
    }

    public KeyguardSecurityViewFlipper(Context context, AttributeSet attr) {
        super(context, attr);
        this.mTempRect = new Rect();
    }

    public boolean onTouchEvent(MotionEvent ev) {
        boolean result = super.onTouchEvent(ev);
        this.mTempRect.set(0, 0, 0, 0);
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == 0) {
                offsetRectIntoDescendantCoords(child, this.mTempRect);
                ev.offsetLocation((float) this.mTempRect.left, (float) this.mTempRect.top);
                result = (child.dispatchTouchEvent(ev) || result) ? true : DEBUG;
                ev.offsetLocation((float) (-this.mTempRect.left), (float) (-this.mTempRect.top));
            }
        }
        return result;
    }

    KeyguardSecurityView getSecurityView() {
        View child = getChildAt(getDisplayedChild());
        if (child instanceof KeyguardSecurityView) {
            return (KeyguardSecurityView) child;
        }
        return null;
    }

    public void setKeyguardCallback(KeyguardSecurityCallback callback) {
        KeyguardSecurityView ksv = getSecurityView();
        if (ksv != null) {
            ksv.setKeyguardCallback(callback);
        }
    }

    public void setLockPatternUtils(LockPatternUtils utils) {
        KeyguardSecurityView ksv = getSecurityView();
        if (ksv != null) {
            ksv.setLockPatternUtils(utils);
        }
    }

    public void reset() {
        KeyguardSecurityView ksv = getSecurityView();
        if (ksv != null) {
            ksv.reset();
        }
    }

    public void onPause() {
        KeyguardSecurityView ksv = getSecurityView();
        if (ksv != null) {
            ksv.onPause();
        }
    }

    public void onResume(int reason) {
        KeyguardSecurityView ksv = getSecurityView();
        if (ksv != null) {
            ksv.onResume(reason);
        }
    }

    public boolean needsInput() {
        KeyguardSecurityView ksv = getSecurityView();
        return ksv != null ? ksv.needsInput() : DEBUG;
    }

    public KeyguardSecurityCallback getCallback() {
        KeyguardSecurityView ksv = getSecurityView();
        return ksv != null ? ksv.getCallback() : null;
    }

    public void showUsabilityHint() {
        KeyguardSecurityView ksv = getSecurityView();
        if (ksv != null) {
            ksv.showUsabilityHint();
        }
    }

    public void showBouncer(int duration) {
        KeyguardSecurityView active = getSecurityView();
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child instanceof KeyguardSecurityView) {
                KeyguardSecurityView ksv = (KeyguardSecurityView) child;
                ksv.showBouncer(ksv == active ? duration : 0);
            }
        }
    }

    public void hideBouncer(int duration) {
        KeyguardSecurityView active = getSecurityView();
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child instanceof KeyguardSecurityView) {
                KeyguardSecurityView ksv = (KeyguardSecurityView) child;
                ksv.hideBouncer(ksv == active ? duration : 0);
            }
        }
    }

    protected boolean checkLayoutParams(android.view.ViewGroup.LayoutParams p) {
        return p instanceof LayoutParams;
    }

    protected android.view.ViewGroup.LayoutParams generateLayoutParams(android.view.ViewGroup.LayoutParams p) {
        return p instanceof LayoutParams ? new LayoutParams((LayoutParams) p) : new LayoutParams(p);
    }

    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    protected void onMeasure(int widthSpec, int heightSpec) {
        int i;
        int widthMode = MeasureSpec.getMode(widthSpec);
        int heightMode = MeasureSpec.getMode(heightSpec);
        int widthSize = MeasureSpec.getSize(widthSpec);
        int heightSize = MeasureSpec.getSize(heightSpec);
        int maxWidth = widthSize;
        int maxHeight = heightSize;
        int count = getChildCount();
        for (i = 0; i < count; i++) {
            LayoutParams lp = (LayoutParams) getChildAt(i).getLayoutParams();
            if (lp.maxWidth > 0 && lp.maxWidth < maxWidth) {
                maxWidth = lp.maxWidth;
            }
            if (lp.maxHeight > 0 && lp.maxHeight < maxHeight) {
                maxHeight = lp.maxHeight;
            }
        }
        int wPadding = getPaddingLeft() + getPaddingRight();
        int hPadding = getPaddingTop() + getPaddingBottom();
        maxWidth -= wPadding;
        maxHeight -= hPadding;
        int width = widthMode == 1073741824 ? widthSize : 0;
        int height = heightMode == 1073741824 ? heightSize : 0;
        for (i = 0; i < count; i++) {
            View child = getChildAt(i);
            lp = (LayoutParams) child.getLayoutParams();
            child.measure(makeChildMeasureSpec(maxWidth, lp.width), makeChildMeasureSpec(maxHeight, lp.height));
            width = Math.max(width, Math.min(child.getMeasuredWidth(), widthSize - wPadding));
            height = Math.max(height, Math.min(child.getMeasuredHeight(), heightSize - hPadding));
        }
        setMeasuredDimension(width + wPadding, height + hPadding);
    }

    private int makeChildMeasureSpec(int maxSize, int childDimen) {
        int mode;
        int size;
        switch (childDimen) {
            case -2:
                mode = Integer.MIN_VALUE;
                size = maxSize;
                break;
            case KeyguardViewDragHelper.INVALID_POINTER /*-1*/:
                mode = 1073741824;
                size = maxSize;
                break;
            default:
                mode = 1073741824;
                size = Math.min(maxSize, childDimen);
                break;
        }
        return MeasureSpec.makeMeasureSpec(size, mode);
    }
}
