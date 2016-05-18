package com.cyngn.keyguard;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.app.admin.DevicePolicyManager;
import android.appwidget.AppWidgetHostView;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Shader.TileMode;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import com.android.internal.widget.LockPatternUtils;
import com.cyngn.keyguard.SlidingChallengeLayout.LayoutParams;

public class KeyguardWidgetFrame extends FrameLayout {
    static final boolean ENABLE_HOVER_OVER_DELETE_DROP_TARGET_OVERLAY = true;
    static final int HOVER_OVER_DELETE_DROP_TARGET_OVERLAY_COLOR = -1711341568;
    static final float OUTLINE_ALPHA_MULTIPLIER = 0.6f;
    private static final PorterDuffXfermode sAddBlendMode = new PorterDuffXfermode(Mode.ADD);
    private float mBackgroundAlpha;
    private float mBackgroundAlphaMultiplier;
    private Drawable mBackgroundDrawable;
    private Rect mBackgroundRect;
    private Object mBgAlphaController;
    private float mContentAlpha;
    private int mForegroundAlpha;
    private LinearGradient mForegroundGradient;
    private final Rect mForegroundRect;
    private Animator mFrameFade;
    private int mFrameHeight;
    private int mFrameStrokeAdjustment;
    private int mGradientColor;
    private Paint mGradientPaint;
    private boolean mIsHoveringOverDeleteDropTarget;
    private boolean mIsSmall;
    boolean mLeftToRight;
    private LinearGradient mLeftToRightGradient;
    private CheckLongPressHelper mLongPressHelper;
    private int mMaxChallengeTop;
    private boolean mModLockWidget;
    private float mOverScrollAmount;
    private boolean mPerformAppWidgetSizeUpdateOnBootComplete;
    private LinearGradient mRightToLeftGradient;
    private int mSmallFrameHeight;
    private int mSmallWidgetHeight;
    private KeyguardUpdateMonitorCallback mUpdateMonitorCallbacks;
    private boolean mWidgetLockedSmall;
    private boolean mWidgetsDisabled;
    private Handler mWorkerHandler;

    public KeyguardWidgetFrame(Context context) {
        this(context, null, 0);
    }

    public KeyguardWidgetFrame(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public KeyguardWidgetFrame(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        this.mGradientPaint = new Paint();
        this.mLeftToRight = ENABLE_HOVER_OVER_DELETE_DROP_TARGET_OVERLAY;
        this.mOverScrollAmount = 0.0f;
        this.mForegroundRect = new Rect();
        this.mForegroundAlpha = 0;
        this.mIsSmall = false;
        this.mBackgroundAlphaMultiplier = 1.0f;
        this.mBackgroundRect = new Rect();
        this.mWidgetLockedSmall = false;
        this.mMaxChallengeTop = -1;
        this.mUpdateMonitorCallbacks = new KeyguardUpdateMonitorCallback() {
            public void onBootCompleted() {
                if (KeyguardWidgetFrame.this.mPerformAppWidgetSizeUpdateOnBootComplete) {
                    KeyguardWidgetFrame.this.performAppWidgetSizeCallbacksIfNecessary();
                    KeyguardWidgetFrame.this.mPerformAppWidgetSizeUpdateOnBootComplete = false;
                }
            }
        };
        this.mLongPressHelper = new CheckLongPressHelper(this);
        this.mWidgetsDisabled = widgetsDisabled(context);
        Resources res = context.getResources();
        float density = res.getDisplayMetrics().density;
        int padding = (int) (res.getDisplayMetrics().density * 8.0f);
        setPadding(padding, padding, padding, padding);
        this.mFrameStrokeAdjustment = ((int) (2.0f * density)) + 2;
        this.mSmallWidgetHeight = res.getDimensionPixelSize(R.dimen.kg_small_widget_height);
        this.mBackgroundDrawable = res.getDrawable(R.drawable.kg_widget_bg_padded);
        this.mGradientColor = res.getColor(R.color.kg_widget_pager_gradient);
        this.mGradientPaint.setXfermode(sAddBlendMode);
        this.mModLockWidget = KeyguardViewManager.isModLockEnabled(context);
    }

    private boolean widgetsDisabled(Context context) {
        int disabledFeatures = 0;
        LockPatternUtils lockPatternUtils = new LockPatternUtils(context);
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService("device_policy");
        if (dpm != null) {
            disabledFeatures = getDisabledFeatures(dpm, lockPatternUtils);
        }
        return (((disabledFeatures & 1) != 0 ? ENABLE_HOVER_OVER_DELETE_DROP_TARGET_OVERLAY : false) || (!lockPatternUtils.getWidgetsEnabled() ? ENABLE_HOVER_OVER_DELETE_DROP_TARGET_OVERLAY : false)) ? ENABLE_HOVER_OVER_DELETE_DROP_TARGET_OVERLAY : false;
    }

    private int getDisabledFeatures(DevicePolicyManager dpm, LockPatternUtils lockPatternUtils) {
        if (dpm != null) {
            return dpm.getKeyguardDisabledFeatures(null, lockPatternUtils.getCurrentUser());
        }
        return 0;
    }

    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        cancelLongPress();
        KeyguardUpdateMonitor.getInstance(this.mContext).removeCallback(this.mUpdateMonitorCallbacks);
    }

    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        KeyguardUpdateMonitor.getInstance(this.mContext).registerCallback(this.mUpdateMonitorCallbacks);
    }

    void setIsHoveringOverDeleteDropTarget(boolean isHovering) {
        if (this.mIsHoveringOverDeleteDropTarget != isHovering) {
            this.mIsHoveringOverDeleteDropTarget = isHovering;
            announceForAccessibility(getContext().getResources().getString(isHovering ? R.string.keyguard_accessibility_delete_widget_start : R.string.keyguard_accessibility_delete_widget_end, new Object[]{getContentDescription()}));
            invalidate();
        }
    }

    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!(this.mWidgetsDisabled || this.mModLockWidget)) {
            switch (ev.getAction()) {
                case SlidingChallengeLayout.SCROLL_STATE_IDLE /*0*/:
                    this.mLongPressHelper.postCheckForLongPress(ev);
                    break;
                case SlidingChallengeLayout.SCROLL_STATE_DRAGGING /*1*/:
                case SlidingChallengeLayout.SCROLL_STATE_FADING /*3*/:
                case LayoutParams.CHILD_TYPE_WIDGETS /*5*/:
                    this.mLongPressHelper.cancelLongPress();
                    break;
                case SlidingChallengeLayout.SCROLL_STATE_SETTLING /*2*/:
                    this.mLongPressHelper.onMove(ev);
                    break;
                default:
                    break;
            }
        }
        return false;
    }

    public boolean onTouchEvent(MotionEvent ev) {
        if (!this.mWidgetsDisabled) {
            switch (ev.getAction()) {
                case SlidingChallengeLayout.SCROLL_STATE_DRAGGING /*1*/:
                case SlidingChallengeLayout.SCROLL_STATE_FADING /*3*/:
                case LayoutParams.CHILD_TYPE_WIDGETS /*5*/:
                    this.mLongPressHelper.cancelLongPress();
                    break;
                case SlidingChallengeLayout.SCROLL_STATE_SETTLING /*2*/:
                    this.mLongPressHelper.onMove(ev);
                    break;
                default:
                    break;
            }
        }
        return ENABLE_HOVER_OVER_DELETE_DROP_TARGET_OVERLAY;
    }

    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        super.requestDisallowInterceptTouchEvent(disallowIntercept);
        cancelLongPress();
    }

    public void cancelLongPress() {
        super.cancelLongPress();
        this.mLongPressHelper.cancelLongPress();
    }

    private void drawGradientOverlay(Canvas c) {
        this.mGradientPaint.setShader(this.mForegroundGradient);
        this.mGradientPaint.setAlpha(this.mForegroundAlpha);
        c.drawRect(this.mForegroundRect, this.mGradientPaint);
    }

    private void drawHoveringOverDeleteOverlay(Canvas c) {
        if (this.mIsHoveringOverDeleteDropTarget) {
            c.drawColor(HOVER_OVER_DELETE_DROP_TARGET_OVERLAY_COLOR);
        }
    }

    protected void drawBg(Canvas canvas) {
        if (this.mBackgroundAlpha > 0.0f) {
            Drawable bg = this.mBackgroundDrawable;
            bg.setAlpha((int) ((this.mBackgroundAlpha * this.mBackgroundAlphaMultiplier) * 255.0f));
            bg.setBounds(this.mBackgroundRect);
            bg.draw(canvas);
        }
    }

    protected void dispatchDraw(Canvas canvas) {
        canvas.save();
        drawBg(canvas);
        super.dispatchDraw(canvas);
        drawGradientOverlay(canvas);
        drawHoveringOverDeleteOverlay(canvas);
        canvas.restore();
    }

    public void enableHardwareLayersForContent() {
        View widget = getContent();
        if (widget != null && widget.isHardwareAccelerated()) {
            widget.setLayerType(2, null);
        }
    }

    public void disableHardwareLayersForContent() {
        View widget = getContent();
        if (widget != null) {
            widget.setLayerType(0, null);
        }
    }

    public View getContent() {
        return getChildAt(0);
    }

    public int getContentAppWidgetId() {
        View content = getContent();
        if (content instanceof AppWidgetHostView) {
            return ((AppWidgetHostView) content).getAppWidgetId();
        }
        if (content instanceof KeyguardStatusView) {
            return ((KeyguardStatusView) content).getAppWidgetId();
        }
        return 0;
    }

    public float getBackgroundAlpha() {
        return this.mBackgroundAlpha;
    }

    public void setBackgroundAlphaMultiplier(float multiplier) {
        if (Float.compare(this.mBackgroundAlphaMultiplier, multiplier) != 0) {
            this.mBackgroundAlphaMultiplier = multiplier;
            invalidate();
        }
    }

    public float getBackgroundAlphaMultiplier() {
        return this.mBackgroundAlphaMultiplier;
    }

    public void setBackgroundAlpha(float alpha) {
        if (Float.compare(this.mBackgroundAlpha, alpha) != 0) {
            this.mBackgroundAlpha = alpha;
            invalidate();
        }
    }

    public float getContentAlpha() {
        return this.mContentAlpha;
    }

    public void setContentAlpha(float alpha) {
        this.mContentAlpha = alpha;
        View content = getContent();
        if (content != null) {
            content.setAlpha(alpha);
        }
    }

    private void setWidgetHeight(int height) {
        boolean needLayout = false;
        View widget = getContent();
        if (widget != null) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) widget.getLayoutParams();
            if (lp.height != height) {
                needLayout = ENABLE_HOVER_OVER_DELETE_DROP_TARGET_OVERLAY;
                lp.height = height;
            }
        }
        if (needLayout) {
            requestLayout();
        }
    }

    public void setMaxChallengeTop(int top) {
        boolean dirty = this.mMaxChallengeTop != top ? ENABLE_HOVER_OVER_DELETE_DROP_TARGET_OVERLAY : false;
        this.mMaxChallengeTop = top;
        this.mSmallWidgetHeight = top - getPaddingTop();
        this.mSmallFrameHeight = getPaddingBottom() + top;
        if (dirty && this.mIsSmall) {
            setWidgetHeight(this.mSmallWidgetHeight);
            setFrameHeight(this.mSmallFrameHeight);
        } else if (dirty && this.mWidgetLockedSmall) {
            setWidgetHeight(this.mSmallWidgetHeight);
        }
    }

    public boolean isSmall() {
        return this.mIsSmall;
    }

    public void adjustFrame(int challengeTop) {
        setFrameHeight(challengeTop + getPaddingBottom());
    }

    public void shrinkWidget(boolean alsoShrinkFrame) {
        this.mIsSmall = ENABLE_HOVER_OVER_DELETE_DROP_TARGET_OVERLAY;
        setWidgetHeight(this.mSmallWidgetHeight);
        if (alsoShrinkFrame) {
            setFrameHeight(this.mSmallFrameHeight);
        }
    }

    public int getSmallFrameHeight() {
        return this.mSmallFrameHeight;
    }

    public void setWidgetLockedSmall(boolean locked) {
        if (locked) {
            setWidgetHeight(this.mSmallWidgetHeight);
        }
        this.mWidgetLockedSmall = locked;
    }

    public void resetSize() {
        this.mIsSmall = false;
        if (!this.mWidgetLockedSmall) {
            setWidgetHeight(-1);
        }
        setFrameHeight(getMeasuredHeight());
    }

    public void setFrameHeight(int height) {
        this.mFrameHeight = height;
        this.mBackgroundRect.set(0, 0, getMeasuredWidth(), Math.min(this.mFrameHeight, getMeasuredHeight()));
        this.mForegroundRect.set(this.mFrameStrokeAdjustment, this.mFrameStrokeAdjustment, getMeasuredWidth() - this.mFrameStrokeAdjustment, Math.min(getMeasuredHeight(), this.mFrameHeight) - this.mFrameStrokeAdjustment);
        updateGradient();
        invalidate();
    }

    public void hideFrame(Object caller) {
        fadeFrame(caller, false, 0.0f, KeyguardWidgetPager.CHILDREN_OUTLINE_FADE_OUT_DURATION);
    }

    public void showFrame(Object caller) {
        fadeFrame(caller, ENABLE_HOVER_OVER_DELETE_DROP_TARGET_OVERLAY, OUTLINE_ALPHA_MULTIPLIER, 100);
    }

    public void fadeFrame(Object caller, boolean takeControl, float alpha, int duration) {
        if (takeControl) {
            this.mBgAlphaController = caller;
        }
        if (this.mBgAlphaController == caller || this.mBgAlphaController == null) {
            if (this.mFrameFade != null) {
                this.mFrameFade.cancel();
                this.mFrameFade = null;
            }
            PropertyValuesHolder bgAlpha = PropertyValuesHolder.ofFloat("backgroundAlpha", new float[]{alpha});
            this.mFrameFade = ObjectAnimator.ofPropertyValuesHolder(this, new PropertyValuesHolder[]{bgAlpha});
            this.mFrameFade.setDuration((long) duration);
            this.mFrameFade.start();
        }
    }

    private void updateGradient() {
        float x1;
        float x0 = this.mLeftToRight ? 0.0f : (float) this.mForegroundRect.width();
        if (this.mLeftToRight) {
            x1 = (float) this.mForegroundRect.width();
        } else {
            x1 = 0.0f;
        }
        this.mLeftToRightGradient = new LinearGradient(x0, 0.0f, x1, 0.0f, this.mGradientColor, 0, TileMode.CLAMP);
        this.mRightToLeftGradient = new LinearGradient(x1, 0.0f, x0, 0.0f, this.mGradientColor, 0, TileMode.CLAMP);
    }

    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (!this.mIsSmall) {
            this.mFrameHeight = h;
        }
        this.mForegroundRect.set(this.mFrameStrokeAdjustment, this.mFrameStrokeAdjustment, w - this.mFrameStrokeAdjustment, Math.min(h, this.mFrameHeight) - this.mFrameStrokeAdjustment);
        this.mBackgroundRect.set(0, 0, getMeasuredWidth(), Math.min(h, this.mFrameHeight));
        updateGradient();
        invalidate();
    }

    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        performAppWidgetSizeCallbacksIfNecessary();
    }

    private void performAppWidgetSizeCallbacksIfNecessary() {
        View content = getContent();
        if (!(content instanceof AppWidgetHostView)) {
            return;
        }
        if (KeyguardUpdateMonitor.getInstance(this.mContext).hasBootCompleted()) {
            AppWidgetHostView awhv = (AppWidgetHostView) content;
            float density = getResources().getDisplayMetrics().density;
            int width = (int) (((float) content.getMeasuredWidth()) / density);
            int height = (int) (((float) content.getMeasuredHeight()) / density);
            awhv.updateAppWidgetSize(null, width, height, width, height, ENABLE_HOVER_OVER_DELETE_DROP_TARGET_OVERLAY);
            return;
        }
        this.mPerformAppWidgetSizeUpdateOnBootComplete = ENABLE_HOVER_OVER_DELETE_DROP_TARGET_OVERLAY;
    }

    void setOverScrollAmount(float r, boolean left) {
        if (Float.compare(this.mOverScrollAmount, r) != 0) {
            this.mOverScrollAmount = r;
            this.mForegroundGradient = left ? this.mLeftToRightGradient : this.mRightToLeftGradient;
            this.mForegroundAlpha = Math.round((0.5f * r) * 255.0f);
            setBackgroundAlpha(Math.min(OUTLINE_ALPHA_MULTIPLIER + (0.39999998f * r), 1.0f));
            invalidate();
        }
    }

    public void onActive(boolean isActive) {
    }

    public boolean onUserInteraction(MotionEvent event) {
        return false;
    }

    public void onBouncerShowing(boolean showing) {
    }

    public void setWorkerHandler(Handler workerHandler) {
        this.mWorkerHandler = workerHandler;
    }

    public Handler getWorkerHandler() {
        return this.mWorkerHandler;
    }
}
