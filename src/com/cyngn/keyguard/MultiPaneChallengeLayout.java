package com.cyngn.keyguard;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.MarginLayoutParams;
import com.cyngn.keyguard.ChallengeLayout.OnBouncerStateChangedListener;

public class MultiPaneChallengeLayout extends ViewGroup implements ChallengeLayout {
    public static final int ANIMATE_BOUNCE_DURATION = 350;
    public static final int HORIZONTAL = 0;
    private static final String TAG = "MultiPaneChallengeLayout";
    public static final int VERTICAL = 1;
    private OnBouncerStateChangedListener mBouncerListener;
    private KeyguardSecurityContainer mChallengeView;
    private final DisplayMetrics mDisplayMetrics;
    private final Rect mInsets;
    private boolean mIsBouncing;
    final int mOrientation;
    private final OnClickListener mScrimClickListener;
    private View mScrimView;
    private final Rect mTempRect;
    private View mUserSwitcherView;
    private final Rect mZeroPadding;

    public static class LayoutParams extends MarginLayoutParams {
        public static final int CHILD_TYPE_CHALLENGE = 2;
        public static final int CHILD_TYPE_NONE = 0;
        public static final int CHILD_TYPE_PAGE_DELETE_DROP_TARGET = 7;
        public static final int CHILD_TYPE_SCRIM = 4;
        public static final int CHILD_TYPE_USER_SWITCHER = 3;
        public static final int CHILD_TYPE_WIDGET = 1;
        public float centerWithinArea;
        public int childType;
        public int gravity;
        public int maxHeight;
        public int maxWidth;

        public LayoutParams() {
            this(-2, -2);
        }

        LayoutParams(Context c, AttributeSet attrs, MultiPaneChallengeLayout parent) {
            super(c, attrs);
            this.centerWithinArea = 0.0f;
            this.childType = CHILD_TYPE_NONE;
            this.gravity = CHILD_TYPE_NONE;
            this.maxWidth = -1;
            this.maxHeight = -1;
            TypedArray a = c.obtainStyledAttributes(attrs, R.styleable.MultiPaneChallengeLayout_Layout);
            this.centerWithinArea = a.getFloat(CHILD_TYPE_USER_SWITCHER, 0.0f);
            this.childType = a.getInt(CHILD_TYPE_WIDGET, CHILD_TYPE_NONE);
            this.gravity = a.getInt(CHILD_TYPE_NONE, CHILD_TYPE_NONE);
            this.maxWidth = a.getDimensionPixelSize(CHILD_TYPE_SCRIM, -1);
            this.maxHeight = a.getDimensionPixelSize(CHILD_TYPE_CHALLENGE, -1);
            if (this.gravity == 0) {
                if (parent.mOrientation != 0) {
                    switch (this.childType) {
                        case CHILD_TYPE_WIDGET /*1*/:
                            this.gravity = 49;
                            break;
                        case CHILD_TYPE_CHALLENGE /*2*/:
                            this.gravity = 81;
                            break;
                        case CHILD_TYPE_USER_SWITCHER /*3*/:
                            this.gravity = 81;
                            break;
                        default:
                            break;
                    }
                }
                switch (this.childType) {
                    case CHILD_TYPE_WIDGET /*1*/:
                        this.gravity = 19;
                        break;
                    case CHILD_TYPE_CHALLENGE /*2*/:
                        this.gravity = 21;
                        break;
                    case CHILD_TYPE_USER_SWITCHER /*3*/:
                        this.gravity = 81;
                        break;
                }
            }
            a.recycle();
        }

        public LayoutParams(int width, int height) {
            super(width, height);
            this.centerWithinArea = 0.0f;
            this.childType = CHILD_TYPE_NONE;
            this.gravity = CHILD_TYPE_NONE;
            this.maxWidth = -1;
            this.maxHeight = -1;
        }

        public LayoutParams(android.view.ViewGroup.LayoutParams source) {
            super(source);
            this.centerWithinArea = 0.0f;
            this.childType = CHILD_TYPE_NONE;
            this.gravity = CHILD_TYPE_NONE;
            this.maxWidth = -1;
            this.maxHeight = -1;
        }

        public LayoutParams(MarginLayoutParams source) {
            super(source);
            this.centerWithinArea = 0.0f;
            this.childType = CHILD_TYPE_NONE;
            this.gravity = CHILD_TYPE_NONE;
            this.maxWidth = -1;
            this.maxHeight = -1;
        }

        public LayoutParams(LayoutParams source) {
            this((MarginLayoutParams) source);
            this.centerWithinArea = source.centerWithinArea;
            this.childType = source.childType;
            this.gravity = source.gravity;
            this.maxWidth = source.maxWidth;
            this.maxHeight = source.maxHeight;
        }
    }

    public MultiPaneChallengeLayout(Context context) {
        this(context, null);
    }

    public MultiPaneChallengeLayout(Context context, AttributeSet attrs) {
        this(context, attrs, HORIZONTAL);
    }

    public MultiPaneChallengeLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.mTempRect = new Rect();
        this.mZeroPadding = new Rect();
        this.mInsets = new Rect();
        this.mScrimClickListener = new OnClickListener() {
            public void onClick(View v) {
                MultiPaneChallengeLayout.this.hideBouncer();
            }
        };
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.MultiPaneChallengeLayout, defStyleAttr, HORIZONTAL);
        this.mOrientation = a.getInt(HORIZONTAL, HORIZONTAL);
        a.recycle();
        this.mDisplayMetrics = getResources().getDisplayMetrics();
        setSystemUiVisibility(768);
    }

    public void setInsets(Rect insets) {
        this.mInsets.set(insets);
    }

    public boolean isChallengeShowing() {
        return true;
    }

    public boolean isChallengeOverlapping() {
        return false;
    }

    public void showChallenge(boolean b) {
    }

    public int getBouncerAnimationDuration() {
        return ANIMATE_BOUNCE_DURATION;
    }

    public void showBouncer() {
        if (!this.mIsBouncing) {
            this.mIsBouncing = true;
            if (this.mScrimView != null) {
                if (this.mChallengeView != null) {
                    this.mChallengeView.showBouncer(ANIMATE_BOUNCE_DURATION);
                }
                float[] fArr = new float[VERTICAL];
                fArr[HORIZONTAL] = 1.0f;
                Animator anim = ObjectAnimator.ofFloat(this.mScrimView, "alpha", fArr);
                anim.setDuration(350);
                anim.addListener(new AnimatorListenerAdapter() {
                    public void onAnimationStart(Animator animation) {
                        MultiPaneChallengeLayout.this.mScrimView.setVisibility(MultiPaneChallengeLayout.HORIZONTAL);
                    }
                });
                anim.start();
            }
            if (this.mBouncerListener != null) {
                this.mBouncerListener.onBouncerStateChanged(true);
            }
        }
    }

    public void hideBouncer() {
        if (this.mIsBouncing) {
            this.mIsBouncing = false;
            if (this.mScrimView != null) {
                if (this.mChallengeView != null) {
                    this.mChallengeView.hideBouncer(ANIMATE_BOUNCE_DURATION);
                }
                float[] fArr = new float[VERTICAL];
                fArr[HORIZONTAL] = 0.0f;
                Animator anim = ObjectAnimator.ofFloat(this.mScrimView, "alpha", fArr);
                anim.setDuration(350);
                anim.addListener(new AnimatorListenerAdapter() {
                    public void onAnimationEnd(Animator animation) {
                        MultiPaneChallengeLayout.this.mScrimView.setVisibility(4);
                    }
                });
                anim.start();
            }
            if (this.mBouncerListener != null) {
                this.mBouncerListener.onBouncerStateChanged(false);
            }
        }
    }

    public boolean isBouncing() {
        return this.mIsBouncing;
    }

    public void setOnBouncerStateChangedListener(OnBouncerStateChangedListener listener) {
        this.mBouncerListener = listener;
    }

    public void requestChildFocus(View child, View focused) {
        if (this.mIsBouncing && child != this.mChallengeView) {
            hideBouncer();
        }
        super.requestChildFocus(child, focused);
    }

    void setScrimView(View scrim) {
        if (this.mScrimView != null) {
            this.mScrimView.setOnClickListener(null);
        }
        this.mScrimView = scrim;
        if (this.mScrimView != null) {
            this.mScrimView.setAlpha(this.mIsBouncing ? 1.0f : 0.0f);
            this.mScrimView.setVisibility(this.mIsBouncing ? HORIZONTAL : 4);
            this.mScrimView.setFocusable(true);
            this.mScrimView.setOnClickListener(this.mScrimClickListener);
        }
    }

    private int getVirtualHeight(LayoutParams lp, int height, int heightUsed) {
        int virtualHeight = height;
        View root = getRootView();
        if (root != null) {
            virtualHeight = (this.mDisplayMetrics.heightPixels - root.getPaddingTop()) - this.mInsets.top;
        }
        if (lp.childType == 3) {
            return virtualHeight - heightUsed;
        }
        return lp.childType != 7 ? Math.min(virtualHeight - heightUsed, height) : height;
    }

    protected void onMeasure(int widthSpec, int heightSpec) {
        if (MeasureSpec.getMode(widthSpec) == 1073741824 && MeasureSpec.getMode(heightSpec) == 1073741824) {
            int i;
            View child;
            LayoutParams lp;
            int adjustedWidthSpec;
            int adjustedHeightSpec;
            int width = MeasureSpec.getSize(widthSpec);
            int height = MeasureSpec.getSize(heightSpec);
            setMeasuredDimension(width, height);
            int insetHeight = (height - this.mInsets.top) - this.mInsets.bottom;
            int insetHeightSpec = MeasureSpec.makeMeasureSpec(insetHeight, 1073741824);
            int widthUsed = HORIZONTAL;
            int heightUsed = HORIZONTAL;
            this.mChallengeView = null;
            this.mUserSwitcherView = null;
            int count = getChildCount();
            for (i = HORIZONTAL; i < count; i += VERTICAL) {
                child = getChildAt(i);
                lp = (LayoutParams) child.getLayoutParams();
                if (lp.childType == 2) {
                    if (this.mChallengeView != null) {
                        throw new IllegalStateException("There may only be one child of type challenge");
                    } else if (child instanceof KeyguardSecurityContainer) {
                        this.mChallengeView = (KeyguardSecurityContainer) child;
                    } else {
                        throw new IllegalArgumentException("Challenge must be a KeyguardSecurityContainer");
                    }
                } else if (lp.childType == 3) {
                    if (this.mUserSwitcherView != null) {
                        throw new IllegalStateException("There may only be one child of type userSwitcher");
                    }
                    this.mUserSwitcherView = child;
                    if (child.getVisibility() != 8) {
                        adjustedWidthSpec = widthSpec;
                        adjustedHeightSpec = insetHeightSpec;
                        if (lp.maxWidth >= 0) {
                            adjustedWidthSpec = MeasureSpec.makeMeasureSpec(Math.min(lp.maxWidth, width), 1073741824);
                        }
                        if (lp.maxHeight >= 0) {
                            adjustedHeightSpec = MeasureSpec.makeMeasureSpec(Math.min(lp.maxHeight, insetHeight), 1073741824);
                        }
                        measureChildWithMargins(child, adjustedWidthSpec, HORIZONTAL, adjustedHeightSpec, HORIZONTAL);
                        if (Gravity.isVertical(lp.gravity)) {
                            heightUsed = (int) (((float) heightUsed) + (((float) child.getMeasuredHeight()) * 1.5f));
                        } else if (Gravity.isHorizontal(lp.gravity)) {
                            widthUsed = (int) (((float) widthUsed) + (((float) child.getMeasuredWidth()) * 1.5f));
                        }
                    }
                } else if (lp.childType == 4) {
                    setScrimView(child);
                    child.measure(widthSpec, heightSpec);
                }
            }
            for (i = HORIZONTAL; i < count; i += VERTICAL) {
                child = getChildAt(i);
                lp = (LayoutParams) child.getLayoutParams();
                if (!(lp.childType == 3 || lp.childType == 4 || child.getVisibility() == 8)) {
                    int virtualHeight = getVirtualHeight(lp, insetHeight, heightUsed);
                    if (lp.centerWithinArea <= 0.0f) {
                        adjustedWidthSpec = MeasureSpec.makeMeasureSpec(width - widthUsed, 1073741824);
                        adjustedHeightSpec = MeasureSpec.makeMeasureSpec(virtualHeight, 1073741824);
                    } else if (this.mOrientation == 0) {
                        adjustedWidthSpec = MeasureSpec.makeMeasureSpec((int) ((((float) (width - widthUsed)) * lp.centerWithinArea) + 0.5f), 1073741824);
                        adjustedHeightSpec = MeasureSpec.makeMeasureSpec(virtualHeight, 1073741824);
                    } else {
                        adjustedWidthSpec = MeasureSpec.makeMeasureSpec(width - widthUsed, 1073741824);
                        adjustedHeightSpec = MeasureSpec.makeMeasureSpec((int) ((((float) virtualHeight) * lp.centerWithinArea) + 0.5f), 1073741824);
                    }
                    if (lp.maxWidth >= 0) {
                        adjustedWidthSpec = MeasureSpec.makeMeasureSpec(Math.min(lp.maxWidth, MeasureSpec.getSize(adjustedWidthSpec)), 1073741824);
                    }
                    if (lp.maxHeight >= 0) {
                        adjustedHeightSpec = MeasureSpec.makeMeasureSpec(Math.min(lp.maxHeight, MeasureSpec.getSize(adjustedHeightSpec)), 1073741824);
                    }
                    measureChildWithMargins(child, adjustedWidthSpec, HORIZONTAL, adjustedHeightSpec, HORIZONTAL);
                }
            }
            return;
        }
        throw new IllegalArgumentException("MultiPaneChallengeLayout must be measured with an exact size");
    }

    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        Rect padding = this.mTempRect;
        padding.left = getPaddingLeft();
        padding.top = getPaddingTop();
        padding.right = getPaddingRight();
        padding.bottom = getPaddingBottom();
        int width = r - l;
        int height = b - t;
        int insetHeight = (height - this.mInsets.top) - this.mInsets.bottom;
        if (!(this.mUserSwitcherView == null || this.mUserSwitcherView.getVisibility() == 8)) {
            layoutWithGravity(width, insetHeight, this.mUserSwitcherView, padding, true);
        }
        int count = getChildCount();
        for (int i = HORIZONTAL; i < count; i += VERTICAL) {
            View child = getChildAt(i);
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            if (!(child == this.mUserSwitcherView || child.getVisibility() == 8)) {
                if (child == this.mScrimView) {
                    child.layout(HORIZONTAL, HORIZONTAL, width, height);
                } else if (lp.childType == 7) {
                    layoutWithGravity(width, insetHeight, child, this.mZeroPadding, false);
                } else {
                    layoutWithGravity(width, insetHeight, child, padding, false);
                }
            }
        }
    }

    private void layoutWithGravity(int width, int height, View child, Rect padding, boolean adjustPadding) {
        int adjustedWidth;
        int adjustedHeight;
        LayoutParams lp = (LayoutParams) child.getLayoutParams();
        height = getVirtualHeight(lp, height, ((padding.top + padding.bottom) - getPaddingTop()) - getPaddingBottom());
        int gravity = Gravity.getAbsoluteGravity(lp.gravity, getLayoutDirection());
        boolean fixedLayoutSize = lp.centerWithinArea > 0.0f;
        boolean fixedLayoutHorizontal = fixedLayoutSize && this.mOrientation == 0;
        boolean fixedLayoutVertical = fixedLayoutSize && this.mOrientation == VERTICAL;
        if (fixedLayoutHorizontal) {
            adjustedWidth = (int) ((((float) ((width - padding.left) - padding.right)) * lp.centerWithinArea) + 0.5f);
            adjustedHeight = height;
        } else if (fixedLayoutVertical) {
            adjustedWidth = width;
            adjustedHeight = (int) ((((float) ((height - getPaddingTop()) - getPaddingBottom())) * lp.centerWithinArea) + 0.5f);
        } else {
            adjustedWidth = width;
            adjustedHeight = height;
        }
        boolean isVertical = Gravity.isVertical(gravity);
        boolean isHorizontal = Gravity.isHorizontal(gravity);
        int childWidth = child.getMeasuredWidth();
        int childHeight = child.getMeasuredHeight();
        int left = padding.left;
        int top = padding.top;
        int right = left + childWidth;
        int bottom = top + childHeight;
        switch (gravity & 112) {
            case 16:
                top = padding.top + ((height - childHeight) / 2);
                bottom = top + childHeight;
                break;
            case 48:
                top = fixedLayoutVertical ? padding.top + ((adjustedHeight - childHeight) / 2) : padding.top;
                bottom = top + childHeight;
                if (adjustPadding && isVertical) {
                    padding.top = bottom;
                    padding.bottom += childHeight / 2;
                    break;
                }
            case 80:
                bottom = fixedLayoutVertical ? (padding.top + height) - ((adjustedHeight - childHeight) / 2) : padding.top + height;
                top = bottom - childHeight;
                if (adjustPadding && isVertical) {
                    padding.bottom = height - top;
                    padding.top += childHeight / 2;
                    break;
                }
        }
        switch (gravity & 7) {
            case VERTICAL /*1*/:
                left = (((width - padding.left) - padding.right) - childWidth) / 2;
                right = left + childWidth;
                break;
            case SlidingChallengeLayout.SCROLL_STATE_FADING /*3*/:
                left = fixedLayoutHorizontal ? padding.left + ((adjustedWidth - childWidth) / 2) : padding.left;
                right = left + childWidth;
                if (adjustPadding && isHorizontal && !isVertical) {
                    padding.left = right;
                    padding.right += childWidth / 2;
                    break;
                }
            case com.cyngn.keyguard.SlidingChallengeLayout.LayoutParams.CHILD_TYPE_WIDGETS /*5*/:
                right = fixedLayoutHorizontal ? (width - padding.right) - ((adjustedWidth - childWidth) / 2) : width - padding.right;
                left = right - childWidth;
                if (adjustPadding && isHorizontal && !isVertical) {
                    padding.right = width - left;
                    padding.left += childWidth / 2;
                    break;
                }
        }
        child.layout(left, top + this.mInsets.top, right, bottom + this.mInsets.top);
    }

    public android.view.ViewGroup.LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs, this);
    }

    protected android.view.ViewGroup.LayoutParams generateLayoutParams(android.view.ViewGroup.LayoutParams p) {
        if (p instanceof LayoutParams) {
            return new LayoutParams((LayoutParams) p);
        }
        return p instanceof MarginLayoutParams ? new LayoutParams((MarginLayoutParams) p) : new LayoutParams(p);
    }

    protected android.view.ViewGroup.LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams();
    }

    protected boolean checkLayoutParams(android.view.ViewGroup.LayoutParams p) {
        return p instanceof LayoutParams;
    }
}
