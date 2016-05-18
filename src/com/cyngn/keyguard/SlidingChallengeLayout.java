package com.cyngn.keyguard;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.FloatProperty;
import android.util.Log;
import android.util.Property;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.View.OnClickListener;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewGroup.MarginLayoutParams;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.Interpolator;
import android.widget.Scroller;
import com.cyngn.keyguard.ChallengeLayout.OnBouncerStateChangedListener;

public class SlidingChallengeLayout extends ViewGroup implements ChallengeLayout {
    public static final int CHALLENGE_FADE_IN_DURATION = 160;
    public static final int CHALLENGE_FADE_OUT_DURATION = 100;
    private static final boolean DEBUG = false;
    private static final int DRAG_HANDLE_CLOSED_ABOVE = 8;
    private static final int DRAG_HANDLE_CLOSED_BELOW = 0;
    private static final int DRAG_HANDLE_OPEN_ABOVE = 8;
    private static final int DRAG_HANDLE_OPEN_BELOW = 0;
    static final Property<SlidingChallengeLayout, Float> HANDLE_ALPHA = new FloatProperty<SlidingChallengeLayout>("handleAlpha") {
        public void setValue(SlidingChallengeLayout view, float value) {
            view.mHandleAlpha = value;
            view.invalidate();
        }

        public Float get(SlidingChallengeLayout view) {
            return Float.valueOf(view.mHandleAlpha);
        }
    };
    private static final int HANDLE_ANIMATE_DURATION = 250;
    private static final int INVALID_POINTER = -1;
    private static final int MAX_SETTLE_DURATION = 600;
    public static final int SCROLL_STATE_DRAGGING = 1;
    public static final int SCROLL_STATE_FADING = 3;
    public static final int SCROLL_STATE_IDLE = 0;
    public static final int SCROLL_STATE_SETTLING = 2;
    private static final String TAG = "SlidingChallengeLayout";
    private static final Interpolator sHandleFadeInterpolator = new Interpolator() {
        public float getInterpolation(float t) {
            return t * t;
        }
    };
    private static final Interpolator sMotionInterpolator = new Interpolator() {
        public float getInterpolation(float t) {
            t -= 1.0f;
            return ((((t * t) * t) * t) * t) + 1.0f;
        }
    };
    private int mActivePointerId;
    private boolean mBlockDrag;
    private OnBouncerStateChangedListener mBouncerListener;
    private int mChallengeBottomBound;
    private boolean mChallengeInteractiveExternal;
    private boolean mChallengeInteractiveInternal;
    private float mChallengeOffset;
    private boolean mChallengeShowing;
    private boolean mChallengeShowingTargetState;
    private KeyguardSecurityContainer mChallengeView;
    private DisplayMetrics mDisplayMetrics;
    private int mDragHandleClosedAbove;
    private int mDragHandleClosedBelow;
    private int mDragHandleEdgeSlop;
    private int mDragHandleOpenAbove;
    private int mDragHandleOpenBelow;
    private boolean mDragging;
    private boolean mEdgeCaptured;
    private boolean mEnableChallengeDragging;
    private final Runnable mEndScrollRunnable;
    private final OnClickListener mExpandChallengeClickListener;
    private View mExpandChallengeView;
    private ObjectAnimator mFader;
    float mFrameAlpha;
    private ObjectAnimator mFrameAnimation;
    float mFrameAnimationTarget;
    private int mGestureStartChallengeBottom;
    private float mGestureStartX;
    private float mGestureStartY;
    float mHandleAlpha;
    private ObjectAnimator mHandleAnimation;
    private boolean mHasGlowpad;
    private boolean mHasLayout;
    private final Rect mInsets;
    private boolean mIsBouncing;
    private int mMaxVelocity;
    private int mMinVelocity;
    private final OnClickListener mScrimClickListener;
    private View mScrimView;
    private OnChallengeScrolledListener mScrollListener;
    private int mScrollState;
    private final Scroller mScroller;
    private int mTouchSlop;
    private int mTouchSlopSquare;
    private VelocityTracker mVelocityTracker;
    private boolean mWasChallengeShowing;
    private View mWidgetsView;

    public interface OnChallengeScrolledListener {
        void onScrollPositionChanged(float f, int i);

        void onScrollStateChanged(int i);
    }

    public static class LayoutParams extends MarginLayoutParams {
        public static final int CHILD_TYPE_CHALLENGE = 2;
        public static final int CHILD_TYPE_EXPAND_CHALLENGE_HANDLE = 6;
        public static final int CHILD_TYPE_NONE = 0;
        public static final int CHILD_TYPE_SCRIM = 4;
        public static final int CHILD_TYPE_WIDGETS = 5;
        public int childType;
        public int maxHeight;

        public LayoutParams() {
            this((int) SlidingChallengeLayout.INVALID_POINTER, -2);
        }

        public LayoutParams(int width, int height) {
            super(width, height);
            this.childType = CHILD_TYPE_NONE;
        }

        public LayoutParams(android.view.ViewGroup.LayoutParams source) {
            super(source);
            this.childType = CHILD_TYPE_NONE;
        }

        public LayoutParams(MarginLayoutParams source) {
            super(source);
            this.childType = CHILD_TYPE_NONE;
        }

        public LayoutParams(LayoutParams source) {
            super(source);
            this.childType = CHILD_TYPE_NONE;
            this.childType = source.childType;
        }

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
            this.childType = CHILD_TYPE_NONE;
            TypedArray a = c.obtainStyledAttributes(attrs, R.styleable.SlidingChallengeLayout_Layout);
            this.childType = a.getInt(CHILD_TYPE_NONE, CHILD_TYPE_NONE);
            this.maxHeight = a.getDimensionPixelSize(SlidingChallengeLayout.SCROLL_STATE_DRAGGING, CHILD_TYPE_NONE);
            a.recycle();
        }
    }

    public SlidingChallengeLayout(Context context) {
        this(context, null);
    }

    public SlidingChallengeLayout(Context context, AttributeSet attrs) {
        this(context, attrs, SCROLL_STATE_IDLE);
    }

    public SlidingChallengeLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        this.mChallengeOffset = 1.0f;
        this.mChallengeShowing = true;
        this.mChallengeShowingTargetState = true;
        this.mWasChallengeShowing = true;
        this.mIsBouncing = DEBUG;
        this.mActivePointerId = INVALID_POINTER;
        this.mFrameAnimationTarget = Float.MIN_VALUE;
        this.mInsets = new Rect();
        this.mChallengeInteractiveExternal = true;
        this.mChallengeInteractiveInternal = true;
        this.mEndScrollRunnable = new Runnable() {
            public void run() {
                SlidingChallengeLayout.this.completeChallengeScroll();
            }
        };
        this.mScrimClickListener = new OnClickListener() {
            public void onClick(View v) {
                SlidingChallengeLayout.this.hideBouncer();
            }
        };
        this.mExpandChallengeClickListener = new OnClickListener() {
            public void onClick(View v) {
                if (!SlidingChallengeLayout.this.isChallengeShowing()) {
                    SlidingChallengeLayout.this.showChallenge(true);
                }
            }
        };
        this.mScroller = new Scroller(context, sMotionInterpolator);
        ViewConfiguration vc = ViewConfiguration.get(context);
        this.mMinVelocity = vc.getScaledMinimumFlingVelocity();
        this.mMaxVelocity = vc.getScaledMaximumFlingVelocity();
        Resources res = getResources();
        this.mDragHandleEdgeSlop = res.getDimensionPixelSize(R.dimen.kg_edge_swipe_region_size);
        this.mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        this.mTouchSlopSquare = this.mTouchSlop * this.mTouchSlop;
        this.mDisplayMetrics = res.getDisplayMetrics();
        float density = this.mDisplayMetrics.density;
        this.mDragHandleClosedAbove = (int) ((8.0f * density) + 0.5f);
        this.mDragHandleClosedBelow = (int) ((0.0f * density) + 0.5f);
        this.mDragHandleOpenAbove = (int) ((8.0f * density) + 0.5f);
        this.mDragHandleOpenBelow = (int) ((0.0f * density) + 0.5f);
        this.mChallengeBottomBound = res.getDimensionPixelSize(R.dimen.kg_widget_pager_bottom_padding);
        setWillNotDraw(DEBUG);
        setSystemUiVisibility(768);
    }

    public void setEnableChallengeDragging(boolean enabled) {
        this.mEnableChallengeDragging = enabled;
    }

    public void setInsets(Rect insets) {
        this.mInsets.set(insets);
    }

    public void setHandleAlpha(float alpha) {
        if (this.mExpandChallengeView != null) {
            this.mExpandChallengeView.setAlpha(alpha);
        }
    }

    public void setChallengeInteractive(boolean interactive) {
        this.mChallengeInteractiveExternal = interactive;
        if (this.mExpandChallengeView != null) {
            this.mExpandChallengeView.setEnabled(interactive);
        }
    }

    void animateHandle(boolean visible) {
        if (this.mHandleAnimation != null) {
            this.mHandleAnimation.cancel();
            this.mHandleAnimation = null;
        }
        float targetAlpha = visible ? 1.0f : 0.0f;
        if (targetAlpha != this.mHandleAlpha) {
            Property property = HANDLE_ALPHA;
            float[] fArr = new float[SCROLL_STATE_DRAGGING];
            fArr[SCROLL_STATE_IDLE] = targetAlpha;
            this.mHandleAnimation = ObjectAnimator.ofFloat(this, property, fArr);
            this.mHandleAnimation.setInterpolator(sHandleFadeInterpolator);
            this.mHandleAnimation.setDuration(250);
            this.mHandleAnimation.start();
        }
    }

    private void sendInitialListenerUpdates() {
        if (this.mScrollListener != null) {
            this.mScrollListener.onScrollPositionChanged(this.mChallengeOffset, this.mChallengeView != null ? this.mChallengeView.getTop() : SCROLL_STATE_IDLE);
            this.mScrollListener.onScrollStateChanged(this.mScrollState);
        }
    }

    public void setOnChallengeScrolledListener(OnChallengeScrolledListener listener) {
        this.mScrollListener = listener;
        if (this.mHasLayout) {
            sendInitialListenerUpdates();
        }
    }

    public void setOnBouncerStateChangedListener(OnBouncerStateChangedListener listener) {
        this.mBouncerListener = listener;
    }

    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        this.mHasLayout = DEBUG;
    }

    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(this.mEndScrollRunnable);
        this.mHasLayout = DEBUG;
    }

    public void requestChildFocus(View child, View focused) {
        if (this.mIsBouncing && child != this.mChallengeView) {
            hideBouncer();
        }
        super.requestChildFocus(child, focused);
    }

    float distanceInfluenceForSnapDuration(float f) {
        return (float) Math.sin((double) ((float) (((double) (f - 0.5f)) * 0.4712389167638204d)));
    }

    void setScrollState(int state) {
        if (this.mScrollState != state) {
            this.mScrollState = state;
            boolean z = (state != 0 || this.mChallengeShowing) ? DEBUG : true;
            animateHandle(z);
            if (this.mScrollListener != null) {
                this.mScrollListener.onScrollStateChanged(state);
            }
        }
    }

    void completeChallengeScroll() {
        setChallengeShowing(this.mChallengeShowingTargetState);
        this.mChallengeOffset = this.mChallengeShowing ? 1.0f : 0.0f;
        setScrollState(SCROLL_STATE_IDLE);
        this.mChallengeInteractiveInternal = true;
        this.mChallengeView.setLayerType(SCROLL_STATE_IDLE, null);
    }

    void setScrimView(View scrim) {
        if (this.mScrimView != null) {
            this.mScrimView.setOnClickListener(null);
        }
        this.mScrimView = scrim;
        if (this.mScrimView != null) {
            this.mScrimView.setVisibility(this.mIsBouncing ? SCROLL_STATE_IDLE : DRAG_HANDLE_OPEN_ABOVE);
            this.mScrimView.setFocusable(true);
            this.mScrimView.setOnClickListener(this.mScrimClickListener);
        }
    }

    void animateChallengeTo(int y, int velocity) {
        if (this.mChallengeView != null) {
            cancelTransitionsInProgress();
            this.mChallengeInteractiveInternal = DEBUG;
            enableHardwareLayerForChallengeView();
            int sy = this.mChallengeView.getBottom();
            int dy = y - sy;
            if (dy == 0) {
                completeChallengeScroll();
                return;
            }
            int duration;
            setScrollState(SCROLL_STATE_SETTLING);
            int childHeight = this.mChallengeView.getHeight();
            int halfHeight = childHeight / SCROLL_STATE_SETTLING;
            float distance = ((float) halfHeight) + (((float) halfHeight) * distanceInfluenceForSnapDuration(Math.min(1.0f, (((float) Math.abs(dy)) * 1.0f) / ((float) childHeight))));
            velocity = Math.abs(velocity);
            if (velocity > 0) {
                duration = Math.round(1000.0f * Math.abs(distance / ((float) velocity))) * 4;
            } else {
                duration = (int) (((((float) Math.abs(dy)) / ((float) childHeight)) + 1.0f) * 100.0f);
            }
            this.mScroller.startScroll(SCROLL_STATE_IDLE, sy, SCROLL_STATE_IDLE, dy, Math.min(duration, MAX_SETTLE_DURATION));
            postInvalidateOnAnimation();
        }
    }

    private void setChallengeShowing(boolean showChallenge) {
        if (this.mChallengeShowing != showChallenge) {
            this.mChallengeShowing = showChallenge;
            if (this.mExpandChallengeView != null && this.mChallengeView != null) {
                if (this.mChallengeShowing) {
                    this.mExpandChallengeView.setVisibility(4);
                    this.mChallengeView.setVisibility(SCROLL_STATE_IDLE);
                    if (AccessibilityManager.getInstance(this.mContext).isEnabled()) {
                        this.mChallengeView.requestAccessibilityFocus();
                        this.mChallengeView.announceForAccessibility(this.mContext.getString(R.string.keyguard_accessibility_unlock_area_expanded));
                        return;
                    }
                    return;
                }
                this.mExpandChallengeView.setVisibility(SCROLL_STATE_IDLE);
                this.mChallengeView.setVisibility(4);
                if (AccessibilityManager.getInstance(this.mContext).isEnabled()) {
                    this.mExpandChallengeView.requestAccessibilityFocus();
                    this.mChallengeView.announceForAccessibility(this.mContext.getString(R.string.keyguard_accessibility_unlock_area_collapsed));
                }
            }
        }
    }

    public boolean isChallengeShowing() {
        return this.mChallengeShowing;
    }

    public boolean isChallengeOverlapping() {
        return this.mChallengeShowing;
    }

    public boolean isBouncing() {
        return this.mIsBouncing;
    }

    public int getBouncerAnimationDuration() {
        return HANDLE_ANIMATE_DURATION;
    }

    public void showBouncer() {
        if (!this.mIsBouncing) {
            setSystemUiVisibility(getSystemUiVisibility() | 33554432);
            this.mWasChallengeShowing = this.mChallengeShowing;
            this.mIsBouncing = true;
            showChallenge(true);
            if (this.mScrimView != null) {
                float[] fArr = new float[SCROLL_STATE_DRAGGING];
                fArr[SCROLL_STATE_IDLE] = 1.0f;
                Animator anim = ObjectAnimator.ofFloat(this.mScrimView, "alpha", fArr);
                anim.setDuration(250);
                anim.addListener(new AnimatorListenerAdapter() {
                    public void onAnimationStart(Animator animation) {
                        SlidingChallengeLayout.this.mScrimView.setVisibility(SlidingChallengeLayout.SCROLL_STATE_IDLE);
                    }
                });
                anim.start();
            }
            if (this.mChallengeView != null) {
                this.mChallengeView.showBouncer(HANDLE_ANIMATE_DURATION);
            }
            if (this.mBouncerListener != null) {
                this.mBouncerListener.onBouncerStateChanged(true);
            }
        }
    }

    public void hideBouncer() {
        if (this.mIsBouncing) {
            setSystemUiVisibility(getSystemUiVisibility() & -33554433);
            if (!this.mWasChallengeShowing) {
                showChallenge((boolean) DEBUG);
            }
            this.mIsBouncing = DEBUG;
            if (this.mScrimView != null) {
                float[] fArr = new float[SCROLL_STATE_DRAGGING];
                fArr[SCROLL_STATE_IDLE] = 0.0f;
                Animator anim = ObjectAnimator.ofFloat(this.mScrimView, "alpha", fArr);
                anim.setDuration(250);
                anim.addListener(new AnimatorListenerAdapter() {
                    public void onAnimationEnd(Animator animation) {
                        SlidingChallengeLayout.this.mScrimView.setVisibility(SlidingChallengeLayout.DRAG_HANDLE_OPEN_ABOVE);
                    }
                });
                anim.start();
            }
            if (this.mChallengeView != null) {
                this.mChallengeView.hideBouncer(HANDLE_ANIMATE_DURATION);
            }
            if (this.mBouncerListener != null) {
                this.mBouncerListener.onBouncerStateChanged(DEBUG);
            }
        }
    }

    private int getChallengeMargin(boolean expanded) {
        return (expanded && this.mHasGlowpad) ? SCROLL_STATE_IDLE : this.mDragHandleEdgeSlop;
    }

    private float getChallengeAlpha() {
        float x = this.mChallengeOffset - 1.0f;
        return ((x * x) * x) + 1.0f;
    }

    public void requestDisallowInterceptTouchEvent(boolean allowIntercept) {
    }

    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (this.mVelocityTracker == null) {
            this.mVelocityTracker = VelocityTracker.obtain();
        }
        this.mVelocityTracker.addMovement(ev);
        switch (ev.getActionMasked()) {
            case SCROLL_STATE_IDLE /*0*/:
                this.mGestureStartX = ev.getX();
                this.mGestureStartY = ev.getY();
                this.mBlockDrag = DEBUG;
                break;
            case SCROLL_STATE_DRAGGING /*1*/:
            case SCROLL_STATE_FADING /*3*/:
                resetTouch();
                break;
            case SCROLL_STATE_SETTLING /*2*/:
                int count = ev.getPointerCount();
                for (int i = SCROLL_STATE_IDLE; i < count; i += SCROLL_STATE_DRAGGING) {
                    float x = ev.getX(i);
                    float y = ev.getY(i);
                    if (!this.mIsBouncing && this.mActivePointerId == INVALID_POINTER && ((crossedDragHandle(x, y, this.mGestureStartY) && shouldEnableChallengeDragging()) || (isInChallengeView(x, y) && this.mScrollState == SCROLL_STATE_SETTLING))) {
                        this.mActivePointerId = ev.getPointerId(i);
                        this.mGestureStartX = x;
                        this.mGestureStartY = y;
                        this.mGestureStartChallengeBottom = getChallengeBottom();
                        this.mDragging = true;
                        enableHardwareLayerForChallengeView();
                    } else if (this.mChallengeShowing && isInChallengeView(x, y) && shouldEnableChallengeDragging()) {
                        this.mBlockDrag = true;
                    }
                }
                break;
        }
        if (this.mBlockDrag || isChallengeInteractionBlocked()) {
            this.mActivePointerId = INVALID_POINTER;
            this.mDragging = DEBUG;
        }
        return this.mDragging;
    }

    private boolean shouldEnableChallengeDragging() {
        return (this.mEnableChallengeDragging || !this.mChallengeShowing) ? true : DEBUG;
    }

    private boolean isChallengeInteractionBlocked() {
        return (this.mChallengeInteractiveExternal && this.mChallengeInteractiveInternal) ? DEBUG : true;
    }

    private void resetTouch() {
        this.mVelocityTracker.recycle();
        this.mVelocityTracker = null;
        this.mActivePointerId = INVALID_POINTER;
        this.mBlockDrag = DEBUG;
        this.mDragging = DEBUG;
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public boolean onTouchEvent(android.view.MotionEvent r12) {
        /*
        r11 = this;
        r9 = 0;
        r10 = 1;
        r7 = r11.mVelocityTracker;
        if (r7 != 0) goto L_0x000c;
    L_0x0006:
        r7 = android.view.VelocityTracker.obtain();
        r11.mVelocityTracker = r7;
    L_0x000c:
        r7 = r11.mVelocityTracker;
        r7.addMovement(r12);
        r0 = r12.getActionMasked();
        switch(r0) {
            case 0: goto L_0x0019;
            case 1: goto L_0x0045;
            case 2: goto L_0x0069;
            case 3: goto L_0x0028;
            case 4: goto L_0x0018;
            case 5: goto L_0x0018;
            case 6: goto L_0x0039;
            default: goto L_0x0018;
        };
    L_0x0018:
        return r10;
    L_0x0019:
        r11.mBlockDrag = r9;
        r7 = r12.getX();
        r11.mGestureStartX = r7;
        r7 = r12.getY();
        r11.mGestureStartY = r7;
        goto L_0x0018;
    L_0x0028:
        r7 = r11.mDragging;
        if (r7 == 0) goto L_0x0035;
    L_0x002c:
        r7 = r11.isChallengeInteractionBlocked();
        if (r7 != 0) goto L_0x0035;
    L_0x0032:
        r11.showChallenge(r9);
    L_0x0035:
        r11.resetTouch();
        goto L_0x0018;
    L_0x0039:
        r7 = r11.mActivePointerId;
        r8 = r12.getActionIndex();
        r8 = r12.getPointerId(r8);
        if (r7 != r8) goto L_0x0018;
    L_0x0045:
        r7 = r11.mDragging;
        if (r7 == 0) goto L_0x0065;
    L_0x0049:
        r7 = r11.isChallengeInteractionBlocked();
        if (r7 != 0) goto L_0x0065;
    L_0x004f:
        r7 = r11.mVelocityTracker;
        r8 = 1000; // 0x3e8 float:1.401E-42 double:4.94E-321;
        r9 = r11.mMaxVelocity;
        r9 = (float) r9;
        r7.computeCurrentVelocity(r8, r9);
        r7 = r11.mVelocityTracker;
        r8 = r11.mActivePointerId;
        r7 = r7.getYVelocity(r8);
        r7 = (int) r7;
        r11.showChallenge(r7);
    L_0x0065:
        r11.resetTouch();
        goto L_0x0018;
    L_0x0069:
        r7 = r11.mDragging;
        if (r7 != 0) goto L_0x00bd;
    L_0x006d:
        r7 = r11.mBlockDrag;
        if (r7 != 0) goto L_0x00bd;
    L_0x0071:
        r7 = r11.mIsBouncing;
        if (r7 != 0) goto L_0x00bd;
    L_0x0075:
        r1 = r12.getPointerCount();
        r2 = 0;
    L_0x007a:
        if (r2 >= r1) goto L_0x00bd;
    L_0x007c:
        r5 = r12.getX(r2);
        r6 = r12.getY(r2);
        r7 = r11.isInDragHandle(r5, r6);
        if (r7 != 0) goto L_0x009d;
    L_0x008a:
        r7 = r11.mGestureStartY;
        r7 = r11.crossedDragHandle(r5, r6, r7);
        if (r7 != 0) goto L_0x009d;
    L_0x0092:
        r7 = r11.isInChallengeView(r5, r6);
        if (r7 == 0) goto L_0x00d4;
    L_0x0098:
        r7 = r11.mScrollState;
        r8 = 2;
        if (r7 != r8) goto L_0x00d4;
    L_0x009d:
        r7 = r11.mActivePointerId;
        r8 = -1;
        if (r7 != r8) goto L_0x00d4;
    L_0x00a2:
        r7 = r11.isChallengeInteractionBlocked();
        if (r7 != 0) goto L_0x00d4;
    L_0x00a8:
        r11.mGestureStartX = r5;
        r11.mGestureStartY = r6;
        r7 = r12.getPointerId(r2);
        r11.mActivePointerId = r7;
        r7 = r11.getChallengeBottom();
        r11.mGestureStartChallengeBottom = r7;
        r11.mDragging = r10;
        r11.enableHardwareLayerForChallengeView();
    L_0x00bd:
        r7 = r11.mDragging;
        if (r7 == 0) goto L_0x0018;
    L_0x00c1:
        r11.setScrollState(r10);
        r7 = r11.mActivePointerId;
        r3 = r12.findPointerIndex(r7);
        if (r3 >= 0) goto L_0x00d7;
    L_0x00cc:
        r11.resetTouch();
        r11.showChallenge(r9);
        goto L_0x0018;
    L_0x00d4:
        r2 = r2 + 1;
        goto L_0x007a;
    L_0x00d7:
        r6 = r12.getY(r3);
        r7 = r11.mGestureStartY;
        r7 = r6 - r7;
        r8 = r11.getLayoutBottom();
        r9 = r11.mChallengeBottomBound;
        r8 = r8 - r9;
        r8 = (float) r8;
        r4 = java.lang.Math.min(r7, r8);
        r7 = r11.mGestureStartChallengeBottom;
        r8 = (int) r4;
        r7 = r7 + r8;
        r11.moveChallengeTo(r7);
        goto L_0x0018;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.cyngn.keyguard.SlidingChallengeLayout.onTouchEvent(android.view.MotionEvent):boolean");
    }

    public boolean dispatchTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        boolean handled = DEBUG;
        if (action == 0) {
            this.mEdgeCaptured = DEBUG;
        }
        if (!(this.mWidgetsView == null || this.mIsBouncing || (!this.mEdgeCaptured && !isEdgeSwipeBeginEvent(ev)))) {
            handled = this.mEdgeCaptured | this.mWidgetsView.dispatchTouchEvent(ev);
            this.mEdgeCaptured = handled;
        }
        if (!(handled || this.mEdgeCaptured)) {
            handled = super.dispatchTouchEvent(ev);
        }
        if (action == SCROLL_STATE_DRAGGING || action == SCROLL_STATE_FADING) {
            this.mEdgeCaptured = DEBUG;
        }
        return handled;
    }

    private boolean isEdgeSwipeBeginEvent(MotionEvent ev) {
        if (ev.getActionMasked() != 0) {
            return DEBUG;
        }
        float x = ev.getX();
        if (x < ((float) this.mDragHandleEdgeSlop) || x >= ((float) (getWidth() - this.mDragHandleEdgeSlop))) {
            return true;
        }
        return DEBUG;
    }

    private int getDragHandleSizeAbove() {
        return isChallengeShowing() ? this.mDragHandleOpenAbove : this.mDragHandleClosedAbove;
    }

    private int getDragHandleSizeBelow() {
        return isChallengeShowing() ? this.mDragHandleOpenBelow : this.mDragHandleClosedBelow;
    }

    private boolean isInChallengeView(float x, float y) {
        return isPointInView(x, y, this.mChallengeView);
    }

    private boolean isInDragHandle(float x, float y) {
        return isPointInView(x, y, this.mExpandChallengeView);
    }

    private boolean isPointInView(float x, float y, View view) {
        if (view != null && x >= ((float) view.getLeft()) && y >= ((float) view.getTop()) && x < ((float) view.getRight()) && y < ((float) view.getBottom())) {
            return true;
        }
        return DEBUG;
    }

    private boolean crossedDragHandle(float x, float y, float initialY) {
        int challengeTop = this.mChallengeView.getTop();
        boolean horizOk = (x < 0.0f || x >= ((float) getWidth())) ? DEBUG : true;
        boolean vertOk = this.mChallengeShowing ? (initialY >= ((float) (challengeTop - getDragHandleSizeAbove())) || y <= ((float) (getDragHandleSizeBelow() + challengeTop))) ? DEBUG : true : (initialY <= ((float) (getDragHandleSizeBelow() + challengeTop)) || y >= ((float) (challengeTop - getDragHandleSizeAbove()))) ? DEBUG : true;
        return (horizOk && vertOk) ? true : DEBUG;
    }

    private int makeChildMeasureSpec(int maxSize, int childDimen) {
        int mode;
        int size;
        switch (childDimen) {
            case -2:
                mode = Integer.MIN_VALUE;
                size = maxSize;
                break;
            case INVALID_POINTER /*-1*/:
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

    protected void onMeasure(int widthSpec, int heightSpec) {
        if (MeasureSpec.getMode(widthSpec) == 1073741824 && MeasureSpec.getMode(heightSpec) == 1073741824) {
            int i;
            View child;
            LayoutParams lp;
            View root;
            int width = MeasureSpec.getSize(widthSpec);
            int height = MeasureSpec.getSize(heightSpec);
            setMeasuredDimension(width, height);
            int insetHeight = (height - this.mInsets.top) - this.mInsets.bottom;
            int insetHeightSpec = MeasureSpec.makeMeasureSpec(insetHeight, 1073741824);
            View oldChallengeView = this.mChallengeView;
            View oldExpandChallengeView = this.mChallengeView;
            this.mChallengeView = null;
            this.mExpandChallengeView = null;
            int count = getChildCount();
            for (i = SCROLL_STATE_IDLE; i < count; i += SCROLL_STATE_DRAGGING) {
                child = getChildAt(i);
                lp = (LayoutParams) child.getLayoutParams();
                if (lp.childType == SCROLL_STATE_SETTLING) {
                    if (this.mChallengeView != null) {
                        throw new IllegalStateException("There may only be one child with layout_isChallenge=\"true\"");
                    } else if (child instanceof KeyguardSecurityContainer) {
                        this.mChallengeView = (KeyguardSecurityContainer) child;
                        if (this.mChallengeView != oldChallengeView) {
                            this.mChallengeView.setVisibility(this.mChallengeShowing ? SCROLL_STATE_IDLE : 4);
                        }
                        if (!this.mHasLayout) {
                            this.mHasGlowpad = child.findViewById(R.id.keyguard_selector_view) != null ? true : DEBUG;
                            int challengeMargin = getChallengeMargin(true);
                            lp.rightMargin = challengeMargin;
                            lp.leftMargin = challengeMargin;
                        }
                    } else {
                        throw new IllegalArgumentException("Challenge must be a KeyguardSecurityContainer");
                    }
                } else if (lp.childType == 6) {
                    if (this.mExpandChallengeView != null) {
                        throw new IllegalStateException("There may only be one child with layout_childType=\"expandChallengeHandle\"");
                    }
                    this.mExpandChallengeView = child;
                    if (this.mExpandChallengeView != oldExpandChallengeView) {
                        this.mExpandChallengeView.setVisibility(this.mChallengeShowing ? 4 : SCROLL_STATE_IDLE);
                        this.mExpandChallengeView.setOnClickListener(this.mExpandChallengeClickListener);
                    }
                } else if (lp.childType == 4) {
                    setScrimView(child);
                } else if (lp.childType == 5) {
                    this.mWidgetsView = child;
                }
            }
            if (!(this.mChallengeView == null || this.mChallengeView.getVisibility() == DRAG_HANDLE_OPEN_ABOVE)) {
                int challengeHeightSpec = insetHeightSpec;
                root = getRootView();
                if (root != null) {
                    lp = (LayoutParams) this.mChallengeView.getLayoutParams();
                    int maxChallengeHeight = lp.maxHeight - (((this.mDisplayMetrics.heightPixels - root.getPaddingTop()) - this.mInsets.top) - insetHeight);
                    if (maxChallengeHeight > 0) {
                        challengeHeightSpec = makeChildMeasureSpec(maxChallengeHeight, lp.height);
                    }
                }
                measureChildWithMargins(this.mChallengeView, widthSpec, SCROLL_STATE_IDLE, challengeHeightSpec, SCROLL_STATE_IDLE);
            }
            for (i = SCROLL_STATE_IDLE; i < count; i += SCROLL_STATE_DRAGGING) {
                child = getChildAt(i);
                if (!(child.getVisibility() == DRAG_HANDLE_OPEN_ABOVE || child == this.mChallengeView)) {
                    int parentWidthSpec = widthSpec;
                    int parentHeightSpec = insetHeightSpec;
                    lp = (LayoutParams) child.getLayoutParams();
                    if (lp.childType == 5) {
                        root = getRootView();
                        if (root != null) {
                            int windowHeight = (this.mDisplayMetrics.heightPixels - root.getPaddingTop()) - this.mInsets.top;
                            parentWidthSpec = MeasureSpec.makeMeasureSpec(this.mDisplayMetrics.widthPixels, 1073741824);
                            parentHeightSpec = MeasureSpec.makeMeasureSpec(windowHeight, 1073741824);
                        }
                    } else if (lp.childType == 4) {
                        parentWidthSpec = widthSpec;
                        parentHeightSpec = heightSpec;
                    }
                    measureChildWithMargins(child, parentWidthSpec, SCROLL_STATE_IDLE, parentHeightSpec, SCROLL_STATE_IDLE);
                }
            }
            return;
        }
        throw new IllegalArgumentException("SlidingChallengeLayout must be measured with an exact size");
    }

    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int paddingLeft = getPaddingLeft();
        int paddingTop = getPaddingTop();
        int paddingRight = getPaddingRight();
        int paddingBottom = getPaddingBottom();
        int width = r - l;
        int height = b - t;
        int count = getChildCount();
        for (int i = SCROLL_STATE_IDLE; i < count; i += SCROLL_STATE_DRAGGING) {
            View child = getChildAt(i);
            if (child.getVisibility() != DRAG_HANDLE_OPEN_ABOVE) {
                LayoutParams lp = (LayoutParams) child.getLayoutParams();
                int left;
                int bottom;
                if (lp.childType == SCROLL_STATE_SETTLING) {
                    int center = ((paddingLeft + width) - paddingRight) / SCROLL_STATE_SETTLING;
                    int childWidth = child.getMeasuredWidth();
                    int childHeight = child.getMeasuredHeight();
                    left = center - (childWidth / SCROLL_STATE_SETTLING);
                    bottom = (((height - paddingBottom) - lp.bottomMargin) - this.mInsets.bottom) + ((int) (((float) (childHeight - this.mChallengeBottomBound)) * (1.0f - this.mChallengeOffset)));
                    child.setAlpha(getChallengeAlpha());
                    child.layout(left, bottom - childHeight, left + childWidth, bottom);
                } else if (lp.childType == 6) {
                    left = (((paddingLeft + width) - paddingRight) / SCROLL_STATE_SETTLING) - (child.getMeasuredWidth() / SCROLL_STATE_SETTLING);
                    bottom = ((height - paddingBottom) - lp.bottomMargin) - this.mInsets.bottom;
                    child.layout(left, bottom - child.getMeasuredHeight(), left + child.getMeasuredWidth(), bottom);
                } else if (lp.childType == 4) {
                    child.layout(SCROLL_STATE_IDLE, SCROLL_STATE_IDLE, getMeasuredWidth(), getMeasuredHeight());
                } else {
                    child.layout(lp.leftMargin + paddingLeft, (lp.topMargin + paddingTop) + this.mInsets.top, child.getMeasuredWidth() + paddingLeft, (child.getMeasuredHeight() + paddingTop) + this.mInsets.top);
                }
            }
        }
        if (!this.mHasLayout) {
            this.mHasLayout = true;
        }
    }

    public void draw(Canvas c) {
        super.draw(c);
    }

    protected boolean onRequestFocusInDescendants(int direction, Rect previouslyFocusedRect) {
        if (this.mChallengeView == null || !this.mChallengeView.requestFocus(direction, previouslyFocusedRect)) {
            return super.onRequestFocusInDescendants(direction, previouslyFocusedRect);
        }
        return true;
    }

    public void computeScroll() {
        super.computeScroll();
        if (!this.mScroller.isFinished()) {
            if (this.mChallengeView == null) {
                Log.e(TAG, "Challenge view missing in computeScroll");
                this.mScroller.abortAnimation();
                return;
            }
            this.mScroller.computeScrollOffset();
            moveChallengeTo(this.mScroller.getCurrY());
            if (this.mScroller.isFinished()) {
                post(this.mEndScrollRunnable);
            }
        }
    }

    private void cancelTransitionsInProgress() {
        if (!this.mScroller.isFinished()) {
            this.mScroller.abortAnimation();
            completeChallengeScroll();
        }
        if (this.mFader != null) {
            this.mFader.cancel();
        }
    }

    public void fadeInChallenge() {
        fadeChallenge(true);
    }

    public void fadeOutChallenge() {
        fadeChallenge(DEBUG);
    }

    public void fadeChallenge(final boolean show) {
        if (this.mChallengeView != null) {
            cancelTransitionsInProgress();
            float alpha = show ? 1.0f : 0.0f;
            int duration = show ? CHALLENGE_FADE_IN_DURATION : CHALLENGE_FADE_OUT_DURATION;
            float[] fArr = new float[SCROLL_STATE_DRAGGING];
            fArr[SCROLL_STATE_IDLE] = alpha;
            this.mFader = ObjectAnimator.ofFloat(this.mChallengeView, "alpha", fArr);
            this.mFader.addListener(new AnimatorListenerAdapter() {
                public void onAnimationStart(Animator animation) {
                    SlidingChallengeLayout.this.onFadeStart(show);
                }

                public void onAnimationEnd(Animator animation) {
                    SlidingChallengeLayout.this.onFadeEnd(show);
                }
            });
            this.mFader.setDuration((long) duration);
            this.mFader.start();
        }
    }

    private int getMaxChallengeBottom() {
        if (this.mChallengeView == null) {
            return SCROLL_STATE_IDLE;
        }
        return (getLayoutBottom() + this.mChallengeView.getMeasuredHeight()) - this.mChallengeBottomBound;
    }

    private int getMinChallengeBottom() {
        return getLayoutBottom();
    }

    private void onFadeStart(boolean show) {
        this.mChallengeInteractiveInternal = DEBUG;
        enableHardwareLayerForChallengeView();
        if (show) {
            moveChallengeTo(getMinChallengeBottom());
        }
        setScrollState(SCROLL_STATE_FADING);
    }

    private void enableHardwareLayerForChallengeView() {
        if (this.mChallengeView.isHardwareAccelerated()) {
            this.mChallengeView.setLayerType(SCROLL_STATE_SETTLING, null);
        }
    }

    private void onFadeEnd(boolean show) {
        this.mChallengeInteractiveInternal = true;
        setChallengeShowing(show);
        if (!show) {
            moveChallengeTo(getMaxChallengeBottom());
        }
        this.mChallengeView.setLayerType(SCROLL_STATE_IDLE, null);
        this.mFader = null;
        setScrollState(SCROLL_STATE_IDLE);
    }

    public int getMaxChallengeTop() {
        if (this.mChallengeView == null) {
            return SCROLL_STATE_IDLE;
        }
        return (getLayoutBottom() - this.mChallengeView.getMeasuredHeight()) - this.mInsets.top;
    }

    private boolean moveChallengeTo(int bottom) {
        if (this.mChallengeView == null || !this.mHasLayout) {
            return DEBUG;
        }
        int layoutBottom = getLayoutBottom();
        int challengeHeight = this.mChallengeView.getHeight();
        bottom = Math.max(getMinChallengeBottom(), Math.min(bottom, getMaxChallengeBottom()));
        float offset = 1.0f - (((float) (bottom - layoutBottom)) / ((float) (challengeHeight - this.mChallengeBottomBound)));
        this.mChallengeOffset = offset;
        if (offset > 0.0f && !this.mChallengeShowing) {
            setChallengeShowing(true);
        }
        this.mChallengeView.layout(this.mChallengeView.getLeft(), bottom - this.mChallengeView.getHeight(), this.mChallengeView.getRight(), bottom);
        this.mChallengeView.setAlpha(getChallengeAlpha());
        if (this.mScrollListener != null) {
            this.mScrollListener.onScrollPositionChanged(offset, this.mChallengeView.getTop());
        }
        postInvalidateOnAnimation();
        return true;
    }

    private int getLayoutBottom() {
        return ((getMeasuredHeight() - getPaddingBottom()) - (this.mChallengeView == null ? SCROLL_STATE_IDLE : ((LayoutParams) this.mChallengeView.getLayoutParams()).bottomMargin)) - this.mInsets.bottom;
    }

    private int getChallengeBottom() {
        if (this.mChallengeView == null) {
            return SCROLL_STATE_IDLE;
        }
        return this.mChallengeView.getBottom();
    }

    public void showChallenge(boolean show) {
        showChallenge(show, SCROLL_STATE_IDLE);
        if (!show) {
            this.mBlockDrag = true;
        }
    }

    private void showChallenge(int velocity) {
        boolean show;
        if (Math.abs(velocity) > this.mMinVelocity) {
            show = velocity < 0 ? true : DEBUG;
        } else {
            show = this.mChallengeOffset >= 0.5f ? true : DEBUG;
        }
        showChallenge(show, velocity);
    }

    private void showChallenge(boolean show, int velocity) {
        if (this.mChallengeView == null) {
            setChallengeShowing(DEBUG);
        } else if (this.mHasLayout) {
            this.mChallengeShowingTargetState = show;
            int layoutBottom = getLayoutBottom();
            if (!show) {
                layoutBottom = (this.mChallengeView.getHeight() + layoutBottom) - this.mChallengeBottomBound;
            }
            animateChallengeTo(layoutBottom, velocity);
        }
    }

    public android.view.ViewGroup.LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
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
