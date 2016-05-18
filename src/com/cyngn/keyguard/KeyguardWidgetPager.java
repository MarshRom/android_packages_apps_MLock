package com.cyngn.keyguard;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.TimeInterpolator;
import android.appwidget.AppWidgetHostView;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.util.Slog;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout.LayoutParams;
import android.widget.TextClock;
import com.android.internal.widget.LockPatternUtils;
import com.cyngn.keyguard.ChallengeLayout.OnBouncerStateChangedListener;
import com.cyngn.keyguard.PagedView.PageSwitchListener;
import java.util.ArrayList;
import java.util.TimeZone;

public class KeyguardWidgetPager extends PagedView implements OnLongClickListener, OnBouncerStateChangedListener, PageSwitchListener {
    private static float CAMERA_DISTANCE = 10000.0f;
    public static final int CHILDREN_OUTLINE_FADE_IN_DURATION = 100;
    public static final int CHILDREN_OUTLINE_FADE_OUT_DURATION = 375;
    private static final long CUSTOM_WIDGET_USER_ACTIVITY_TIMEOUT = 30000;
    private static final int FLAG_HAS_LOCAL_HOUR = 1;
    private static final int FLAG_HAS_LOCAL_MINUTE = 2;
    protected static float OVERSCROLL_MAX_ROTATION = 30.0f;
    private static final boolean PERFORM_OVERSCROLL_ROTATION = true;
    private static final String TAG = "KeyguardWidgetPager";
    private float BOUNCER_SCALE_FACTOR;
    private View mAddWidgetView;
    private boolean mApplicationWidgetEventInProgress;
    private final Handler mBackgroundWorkerHandler;
    private final HandlerThread mBackgroundWorkerThread;
    private int mBouncerZoomInOutDuration;
    private Callbacks mCallbacks;
    private boolean mCameraEventInProgress;
    private boolean mCenterSmallWidgetsVertically;
    protected AnimatorSet mChildrenOutlineFadeAnimation;
    private boolean mDisableTouch;
    private boolean mHasMeasure;
    private int mLastHeightMeasureSpec;
    private int mLastWidthMeasureSpec;
    private LockPatternUtils mLockPatternUtils;
    private int mPage;
    protected int mScreenCenter;
    protected boolean mShowingInitialHints;
    protected KeyguardViewStateManager mViewStateManager;
    private int mWidgetToResetAfterFadeOut;
    ZInterpolator mZInterpolator;
    boolean showHintsAfterLayout;

    public interface Callbacks {
        void onAddView(View view);

        void onRemoveView(View view, boolean z);

        void onRemoveViewAnimationCompleted();

        void onUserActivityTimeoutChanged();

        void userActivity();
    }

    static class ZInterpolator implements TimeInterpolator {
        private float focalLength;

        public ZInterpolator(float foc) {
            this.focalLength = foc;
        }

        public float getInterpolation(float input) {
            return (1.0f - (this.focalLength / (this.focalLength + input))) / (1.0f - (this.focalLength / (this.focalLength + 1.0f)));
        }
    }

    public KeyguardWidgetPager(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public KeyguardWidgetPager(Context context) {
        this(null, null, 0);
    }

    public KeyguardWidgetPager(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        this.mZInterpolator = new ZInterpolator(0.5f);
        this.mHasMeasure = false;
        this.showHintsAfterLayout = false;
        this.mPage = 0;
        this.mShowingInitialHints = false;
        this.mBouncerZoomInOutDuration = CameraWidgetFrame.WIDGET_ANIMATION_DURATION;
        this.BOUNCER_SCALE_FACTOR = 0.67f;
        if (getImportantForAccessibility() == 0) {
            setImportantForAccessibility(FLAG_HAS_LOCAL_HOUR);
        }
        setPageSwitchListener(this);
        this.mBackgroundWorkerThread = new HandlerThread("KeyguardWidgetPager Worker");
        this.mBackgroundWorkerThread.start();
        this.mBackgroundWorkerHandler = new Handler(this.mBackgroundWorkerThread.getLooper());
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.PagedView, defStyle, 0);
        this.mDisableTouch = a.getBoolean(3, false);
        a.recycle();
    }

    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        this.mBackgroundWorkerThread.quit();
    }

    public void setViewStateManager(KeyguardViewStateManager viewStateManager) {
        this.mViewStateManager = viewStateManager;
    }

    public void setLockPatternUtils(LockPatternUtils l) {
        this.mLockPatternUtils = l;
    }

    public void onPageSwitching(View newPage, int newPageIndex) {
        if (this.mViewStateManager != null) {
            this.mViewStateManager.onPageSwitching(newPage, newPageIndex);
        }
    }

    public void onPageSwitched(View newPage, int newPageIndex) {
        boolean showingClock = false;
        if ((newPage instanceof ViewGroup) && (((ViewGroup) newPage).getChildAt(0) instanceof KeyguardStatusView)) {
            showingClock = PERFORM_OVERSCROLL_ROTATION;
        }
        if (newPage != null && findClockInHierarchy(newPage) == 3) {
            showingClock = PERFORM_OVERSCROLL_ROTATION;
        }
        if (showingClock) {
            setSystemUiVisibility(getSystemUiVisibility() | 8388608);
        } else {
            setSystemUiVisibility(getSystemUiVisibility() & -8388609);
        }
        if (this.mPage != newPageIndex) {
            int oldPageIndex = this.mPage;
            this.mPage = newPageIndex;
            userActivity();
            KeyguardWidgetFrame oldWidgetPage = getWidgetPageAt(oldPageIndex);
            if (oldWidgetPage != null) {
                oldWidgetPage.onActive(false);
            }
            KeyguardWidgetFrame newWidgetPage = getWidgetPageAt(newPageIndex);
            if (newWidgetPage != null) {
                newWidgetPage.onActive(PERFORM_OVERSCROLL_ROTATION);
                newWidgetPage.setImportantForAccessibility(FLAG_HAS_LOCAL_HOUR);
                newWidgetPage.requestAccessibilityFocus();
            }
            if (this.mParent != null && AccessibilityManager.getInstance(this.mContext).isEnabled()) {
                AccessibilityEvent event = AccessibilityEvent.obtain(4096);
                onInitializeAccessibilityEvent(event);
                onPopulateAccessibilityEvent(event);
                this.mParent.requestSendAccessibilityEvent(this, event);
            }
        }
        if (this.mViewStateManager != null) {
            this.mViewStateManager.onPageSwitched(newPage, newPageIndex);
        }
    }

    public void onPageBeginWarp() {
        showOutlinesAndSidePages();
        this.mViewStateManager.onPageBeginWarp();
    }

    public void onPageEndWarp() {
        animateOutlinesAndSidePages(false, getPageWarpIndex() == getNextPage() ? 0 : -1);
        this.mViewStateManager.onPageEndWarp();
    }

    public void sendAccessibilityEvent(int eventType) {
        if (eventType != 4096 || isPageMoving()) {
            super.sendAccessibilityEvent(eventType);
        }
    }

    private void updateWidgetFramesImportantForAccessibility() {
        int pageCount = getPageCount();
        for (int i = 0; i < pageCount; i += FLAG_HAS_LOCAL_HOUR) {
            updateWidgetFrameImportantForAccessibility(getWidgetPageAt(i));
        }
    }

    private void updateWidgetFrameImportantForAccessibility(KeyguardWidgetFrame frame) {
        if (frame.getContentAlpha() <= 0.0f) {
            frame.setImportantForAccessibility(FLAG_HAS_LOCAL_MINUTE);
        } else {
            frame.setImportantForAccessibility(FLAG_HAS_LOCAL_HOUR);
        }
    }

    private void userActivity() {
        if (this.mCallbacks != null) {
            this.mCallbacks.onUserActivityTimeoutChanged();
            this.mCallbacks.userActivity();
        }
    }

    public boolean onTouchEvent(MotionEvent ev) {
        if (this.mDisableTouch) {
            return false;
        }
        if (captureUserInteraction(ev) || super.onTouchEvent(ev)) {
            return PERFORM_OVERSCROLL_ROTATION;
        }
        return false;
    }

    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (this.mDisableTouch) {
            return false;
        }
        if (captureUserInteraction(ev) || super.onInterceptTouchEvent(ev)) {
            return PERFORM_OVERSCROLL_ROTATION;
        }
        return false;
    }

    private boolean captureUserInteraction(MotionEvent ev) {
        KeyguardWidgetFrame currentWidgetPage = getWidgetPageAt(getCurrentPage());
        return (currentWidgetPage == null || !currentWidgetPage.onUserInteraction(ev)) ? false : PERFORM_OVERSCROLL_ROTATION;
    }

    public void showPagingFeedback() {
    }

    public long getUserActivityTimeout() {
        View page = getPageAt(this.mPage);
        if (page instanceof ViewGroup) {
            View view = ((ViewGroup) page).getChildAt(0);
            if (!((view instanceof KeyguardStatusView) || (view instanceof KeyguardMultiUserSelectorView))) {
                return CUSTOM_WIDGET_USER_ACTIVITY_TIMEOUT;
            }
        }
        return -1;
    }

    public void setCallbacks(Callbacks callbacks) {
        this.mCallbacks = callbacks;
    }

    public void addWidget(View widget) {
        addWidget(widget, -1);
    }

    public void onRemoveView(View v, boolean deletePermanently) {
        final int appWidgetId = ((KeyguardWidgetFrame) v).getContentAppWidgetId();
        if (this.mCallbacks != null) {
            this.mCallbacks.onRemoveView(v, deletePermanently);
        }
        this.mBackgroundWorkerHandler.post(new Runnable() {
            public void run() {
                KeyguardWidgetPager.this.mLockPatternUtils.removeAppWidget(appWidgetId);
            }
        });
    }

    public void onRemoveViewAnimationCompleted() {
        if (this.mCallbacks != null) {
            this.mCallbacks.onRemoveViewAnimationCompleted();
        }
    }

    public void onAddView(View v, final int index) {
        final int appWidgetId = ((KeyguardWidgetFrame) v).getContentAppWidgetId();
        final int[] pagesRange = new int[this.mTempVisiblePagesRange.length];
        getVisiblePages(pagesRange);
        boundByReorderablePages(PERFORM_OVERSCROLL_ROTATION, pagesRange);
        if (this.mCallbacks != null) {
            this.mCallbacks.onAddView(v);
        }
        this.mBackgroundWorkerHandler.post(new Runnable() {
            public void run() {
                KeyguardWidgetPager.this.mLockPatternUtils.addAppWidget(appWidgetId, index - pagesRange[0]);
            }
        });
    }

    public void addWidget(View widget, int pageIndex) {
        View frame;
        View content;
        if (widget instanceof KeyguardWidgetFrame) {
            KeyguardWidgetFrame frame2 = (KeyguardWidgetFrame) widget;
        } else {
            frame = new KeyguardWidgetFrame(getContext());
            LayoutParams lp = new LayoutParams(-1, -1);
            lp.gravity = 48;
            widget.setPadding(0, 0, 0, 0);
            frame.addView(widget, lp);
            if (widget instanceof AppWidgetHostView) {
                if ((((AppWidgetHostView) widget).getAppWidgetInfo().resizeMode & FLAG_HAS_LOCAL_MINUTE) != 0) {
                    frame.setWidgetLockedSmall(false);
                } else {
                    frame.setWidgetLockedSmall(PERFORM_OVERSCROLL_ROTATION);
                    if (this.mCenterSmallWidgetsVertically) {
                        lp.gravity = 17;
                    }
                }
            }
        }
        ViewGroup.LayoutParams pageLp = new ViewGroup.LayoutParams(-1, -1);
        frame.setOnLongClickListener(this);
        frame.setWorkerHandler(this.mBackgroundWorkerHandler);
        if (pageIndex == -1) {
            addView(frame, pageLp);
        } else {
            addView(frame, pageIndex, pageLp);
        }
        if (widget == frame) {
            content = frame.getContent();
        } else {
            content = widget;
        }
        if (content != null) {
            Context context = this.mContext;
            Object[] objArr = new Object[FLAG_HAS_LOCAL_HOUR];
            objArr[0] = content.getContentDescription();
            frame.setContentDescription(context.getString(R.string.keyguard_accessibility_widget, objArr));
        }
        updateWidgetFrameImportantForAccessibility(frame);
    }

    public void addView(View child, int index) {
        enforceKeyguardWidgetFrame(child);
        super.addView(child, index);
    }

    public void addView(View child, int width, int height) {
        enforceKeyguardWidgetFrame(child);
        super.addView(child, width, height);
    }

    public void addView(View child, ViewGroup.LayoutParams params) {
        enforceKeyguardWidgetFrame(child);
        super.addView(child, params);
    }

    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        enforceKeyguardWidgetFrame(child);
        super.addView(child, index, params);
    }

    private void enforceKeyguardWidgetFrame(View child) {
        if (!(child instanceof KeyguardWidgetFrame)) {
            throw new IllegalArgumentException("KeyguardWidgetPager children must be KeyguardWidgetFrames");
        }
    }

    public KeyguardWidgetFrame getWidgetPageAt(int index) {
        return (KeyguardWidgetFrame) getChildAt(index);
    }

    protected void onUnhandledTap(MotionEvent ev) {
        showPagingFeedback();
    }

    protected void onPageBeginMoving() {
        if (this.mViewStateManager != null) {
            this.mViewStateManager.onPageBeginMoving();
        }
        if (!isReordering(false)) {
            showOutlinesAndSidePages();
        }
        userActivity();
    }

    protected void onPageEndMoving() {
        if (this.mViewStateManager != null) {
            this.mViewStateManager.onPageEndMoving();
        }
        if (!isReordering(false)) {
            hideOutlinesAndSidePages();
        }
    }

    protected void enablePageContentLayers() {
        int children = getChildCount();
        for (int i = 0; i < children; i += FLAG_HAS_LOCAL_HOUR) {
            getWidgetPageAt(i).enableHardwareLayersForContent();
        }
    }

    protected void disablePageContentLayers() {
        int children = getChildCount();
        for (int i = 0; i < children; i += FLAG_HAS_LOCAL_HOUR) {
            getWidgetPageAt(i).disableHardwareLayersForContent();
        }
    }

    protected void overScroll(float amount) {
        acceleratedOverScroll(amount);
    }

    float backgroundAlphaInterpolator(float r) {
        return Math.min(1.0f, r);
    }

    private void updatePageAlphaValues(int screenCenter) {
    }

    public float getAlphaForPage(int screenCenter, int index, boolean showSidePages) {
        if (isWarping()) {
            if (index == getPageWarpIndex()) {
                return 1.0f;
            }
            return 0.0f;
        } else if (showSidePages || index == this.mCurrentPage) {
            return 1.0f;
        } else {
            return 0.0f;
        }
    }

    public float getOutlineAlphaForPage(int screenCenter, int index, boolean showSidePages) {
        if (showSidePages) {
            return getAlphaForPage(screenCenter, index, showSidePages) * 0.6f;
        }
        return 0.0f;
    }

    protected boolean isOverScrollChild(int index, float scrollProgress) {
        boolean isInOverscroll = (this.mOverScrollX < 0 || this.mOverScrollX > this.mMaxScrollX) ? PERFORM_OVERSCROLL_ROTATION : false;
        if (isInOverscroll) {
            if (index == 0 && scrollProgress < 0.0f) {
                return PERFORM_OVERSCROLL_ROTATION;
            }
            if (index == getChildCount() - 1 && scrollProgress > 0.0f) {
                return PERFORM_OVERSCROLL_ROTATION;
            }
        }
        return false;
    }

    protected void screenScrolled(int screenCenter) {
        this.mScreenCenter = screenCenter;
        updatePageAlphaValues(screenCenter);
        for (int i = 0; i < getChildCount(); i += FLAG_HAS_LOCAL_HOUR) {
            View v = getWidgetPageAt(i);
            if (!(v == this.mDragView || v == null)) {
                float scrollProgress = getScrollProgress(screenCenter, v, i);
                v.setCameraDistance(this.mDensity * CAMERA_DISTANCE);
                if (isOverScrollChild(i, scrollProgress)) {
                    boolean z;
                    float pivotY = (float) (v.getMeasuredHeight() / FLAG_HAS_LOCAL_MINUTE);
                    v.setPivotX((float) (v.getMeasuredWidth() / FLAG_HAS_LOCAL_MINUTE));
                    v.setPivotY(pivotY);
                    v.setRotationY((-OVERSCROLL_MAX_ROTATION) * scrollProgress);
                    float abs = Math.abs(scrollProgress);
                    if (scrollProgress < 0.0f) {
                        z = PERFORM_OVERSCROLL_ROTATION;
                    } else {
                        z = false;
                    }
                    v.setOverScrollAmount(abs, z);
                } else {
                    v.setRotationY(0.0f);
                    v.setOverScrollAmount(0.0f, false);
                }
                if (v.getAlpha() == 0.0f) {
                    v.setVisibility(4);
                } else if (v.getVisibility() != 0) {
                    v.setVisibility(0);
                }
            }
        }
    }

    public boolean isWidgetPage(int pageIndex) {
        if (pageIndex < 0 || pageIndex >= getChildCount()) {
            return false;
        }
        View v = getChildAt(pageIndex);
        if (v == null || !(v instanceof KeyguardWidgetFrame) || ((KeyguardWidgetFrame) v).getContentAppWidgetId() == 0) {
            return false;
        }
        return PERFORM_OVERSCROLL_ROTATION;
    }

    void boundByReorderablePages(boolean isReordering, int[] range) {
        if (isReordering) {
            while (range[FLAG_HAS_LOCAL_HOUR] >= range[0] && !isWidgetPage(range[FLAG_HAS_LOCAL_HOUR])) {
                range[FLAG_HAS_LOCAL_HOUR] = range[FLAG_HAS_LOCAL_HOUR] - 1;
            }
            while (range[0] <= range[FLAG_HAS_LOCAL_HOUR] && !isWidgetPage(range[0])) {
                range[0] = range[0] + FLAG_HAS_LOCAL_HOUR;
            }
        }
    }

    protected void reorderStarting() {
        showOutlinesAndSidePages();
    }

    protected void onStartReordering() {
        super.onStartReordering();
        enablePageContentLayers();
        reorderStarting();
    }

    protected void onEndReordering() {
        super.onEndReordering();
        hideOutlinesAndSidePages();
    }

    void showOutlinesAndSidePages() {
        animateOutlinesAndSidePages(PERFORM_OVERSCROLL_ROTATION);
    }

    void hideOutlinesAndSidePages() {
        animateOutlinesAndSidePages(false);
    }

    void updateChildrenContentAlpha(float sidePageAlpha) {
        int count = getChildCount();
        for (int i = 0; i < count; i += FLAG_HAS_LOCAL_HOUR) {
            KeyguardWidgetFrame child = getWidgetPageAt(i);
            if (i != this.mCurrentPage) {
                child.setBackgroundAlpha(sidePageAlpha);
                child.setContentAlpha(0.0f);
            } else {
                child.setBackgroundAlpha(0.0f);
                child.setContentAlpha(1.0f);
            }
        }
    }

    public void showInitialPageHints() {
        this.mShowingInitialHints = PERFORM_OVERSCROLL_ROTATION;
        updateChildrenContentAlpha(0.6f);
    }

    void setCurrentPage(int currentPage) {
        super.setCurrentPage(currentPage);
        updateChildrenContentAlpha(0.0f);
        updateWidgetFramesImportantForAccessibility();
    }

    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        this.mHasMeasure = false;
    }

    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        this.mLastWidthMeasureSpec = widthMeasureSpec;
        this.mLastHeightMeasureSpec = heightMeasureSpec;
        View parent = (View) getParent();
        if (parent.getParent() instanceof SlidingChallengeLayout) {
            SlidingChallengeLayout scl = (SlidingChallengeLayout) parent.getParent();
            int maxChallengeTop = scl.getMaxChallengeTop() - getPaddingTop();
            boolean challengeShowing = scl.isChallengeShowing();
            int count = getChildCount();
            int i = 0;
            while (i < count) {
                KeyguardWidgetFrame frame = getWidgetPageAt(i);
                frame.setMaxChallengeTop(maxChallengeTop);
                if (challengeShowing && i == this.mCurrentPage && !this.mHasMeasure) {
                    frame.shrinkWidget(PERFORM_OVERSCROLL_ROTATION);
                }
                i += FLAG_HAS_LOCAL_HOUR;
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        this.mHasMeasure = PERFORM_OVERSCROLL_ROTATION;
    }

    void animateOutlinesAndSidePages(boolean show) {
        animateOutlinesAndSidePages(show, -1);
    }

    public void setWidgetToResetOnPageFadeOut(int widget) {
        this.mWidgetToResetAfterFadeOut = widget;
    }

    public int getWidgetToResetOnPageFadeOut() {
        return this.mWidgetToResetAfterFadeOut;
    }

    void animateOutlinesAndSidePages(final boolean show, int duration) {
        if (this.mChildrenOutlineFadeAnimation != null) {
            this.mChildrenOutlineFadeAnimation.cancel();
            this.mChildrenOutlineFadeAnimation = null;
        }
        int count = getChildCount();
        ArrayList<Animator> anims = new ArrayList();
        if (duration == -1) {
            duration = show ? CHILDREN_OUTLINE_FADE_IN_DURATION : CHILDREN_OUTLINE_FADE_OUT_DURATION;
        }
        int curPage = getNextPage();
        int i = 0;
        while (i < count) {
            float finalContentAlpha;
            if (show) {
                finalContentAlpha = getAlphaForPage(this.mScreenCenter, i, PERFORM_OVERSCROLL_ROTATION);
            } else if (show || i != curPage) {
                finalContentAlpha = 0.0f;
            } else {
                finalContentAlpha = 1.0f;
            }
            KeyguardWidgetFrame child = getWidgetPageAt(i);
            float[] fArr = new float[FLAG_HAS_LOCAL_HOUR];
            fArr[0] = finalContentAlpha;
            PropertyValuesHolder[] propertyValuesHolderArr = new PropertyValuesHolder[FLAG_HAS_LOCAL_HOUR];
            propertyValuesHolderArr[0] = PropertyValuesHolder.ofFloat("contentAlpha", fArr);
            anims.add(ObjectAnimator.ofPropertyValuesHolder(child, propertyValuesHolderArr));
            child.fadeFrame(this, show, show ? getOutlineAlphaForPage(this.mScreenCenter, i, PERFORM_OVERSCROLL_ROTATION) : 0.0f, duration);
            i += FLAG_HAS_LOCAL_HOUR;
        }
        this.mChildrenOutlineFadeAnimation = new AnimatorSet();
        this.mChildrenOutlineFadeAnimation.playTogether(anims);
        this.mChildrenOutlineFadeAnimation.setDuration((long) duration);
        this.mChildrenOutlineFadeAnimation.addListener(new AnimatorListenerAdapter() {
            public void onAnimationStart(Animator animation) {
                if (show) {
                    KeyguardWidgetPager.this.enablePageContentLayers();
                }
            }

            public void onAnimationEnd(Animator animation) {
                if (!show) {
                    KeyguardWidgetPager.this.disablePageContentLayers();
                    KeyguardWidgetFrame frame = KeyguardWidgetPager.this.getWidgetPageAt(KeyguardWidgetPager.this.mWidgetToResetAfterFadeOut);
                    if (!(frame == null || (frame == KeyguardWidgetPager.this.getWidgetPageAt(KeyguardWidgetPager.this.mCurrentPage) && KeyguardWidgetPager.this.mViewStateManager.isChallengeOverlapping()))) {
                        frame.resetSize();
                    }
                    KeyguardWidgetPager.this.mWidgetToResetAfterFadeOut = -1;
                    KeyguardWidgetPager.this.mShowingInitialHints = false;
                }
                KeyguardWidgetPager.this.updateWidgetFramesImportantForAccessibility();
            }
        });
        this.mChildrenOutlineFadeAnimation.start();
    }

    public boolean onLongClick(View v) {
        boolean isChallengeOverlapping = (this.mViewStateManager.isChallengeShowing() && this.mViewStateManager.isChallengeOverlapping()) ? PERFORM_OVERSCROLL_ROTATION : false;
        return (isChallengeOverlapping || !startReordering()) ? false : PERFORM_OVERSCROLL_ROTATION;
    }

    public void removeWidget(View view) {
        if (view instanceof KeyguardWidgetFrame) {
            removeView(view);
            return;
        }
        int pos = getWidgetPageIndex(view);
        if (pos != -1) {
            KeyguardWidgetFrame frame = (KeyguardWidgetFrame) getChildAt(pos);
            frame.removeView(view);
            removeView(frame);
            return;
        }
        Slog.w(TAG, "removeWidget() can't find:" + view);
    }

    public int getWidgetPageIndex(View view) {
        if (view instanceof KeyguardWidgetFrame) {
            return indexOfChild(view);
        }
        return indexOfChild((KeyguardWidgetFrame) view.getParent());
    }

    protected void setPageHoveringOverDeleteDropTarget(int viewIndex, boolean isHovering) {
        getWidgetPageAt(viewIndex).setIsHoveringOverDeleteDropTarget(isHovering);
    }

    public void onBouncerStateChanged(boolean bouncerActive) {
        if (bouncerActive) {
            zoomOutToBouncer();
        } else {
            zoomInFromBouncer();
        }
    }

    void setBouncerAnimationDuration(int duration) {
        this.mBouncerZoomInOutDuration = duration;
    }

    void zoomInFromBouncer() {
        if (this.mZoomInOutAnim != null && this.mZoomInOutAnim.isRunning()) {
            this.mZoomInOutAnim.cancel();
        }
        View currentPage = getPageAt(getCurrentPage());
        if (currentPage != null) {
            if (currentPage.getScaleX() < 1.0f || currentPage.getScaleY() < 1.0f) {
                this.mZoomInOutAnim = new AnimatorSet();
                AnimatorSet animatorSet = this.mZoomInOutAnim;
                Animator[] animatorArr = new Animator[FLAG_HAS_LOCAL_MINUTE];
                float[] fArr = new float[FLAG_HAS_LOCAL_HOUR];
                fArr[0] = 1.0f;
                animatorArr[0] = ObjectAnimator.ofFloat(currentPage, "scaleX", fArr);
                fArr = new float[FLAG_HAS_LOCAL_HOUR];
                fArr[0] = 1.0f;
                animatorArr[FLAG_HAS_LOCAL_HOUR] = ObjectAnimator.ofFloat(currentPage, "scaleY", fArr);
                animatorSet.playTogether(animatorArr);
                this.mZoomInOutAnim.setDuration((long) this.mBouncerZoomInOutDuration);
                this.mZoomInOutAnim.setInterpolator(new DecelerateInterpolator(1.5f));
                this.mZoomInOutAnim.start();
            }
            if (currentPage instanceof KeyguardWidgetFrame) {
                ((KeyguardWidgetFrame) currentPage).onBouncerShowing(false);
            }
        }
    }

    void zoomOutToBouncer() {
        if (this.mZoomInOutAnim != null && this.mZoomInOutAnim.isRunning()) {
            this.mZoomInOutAnim.cancel();
        }
        int curPage = getCurrentPage();
        View currentPage = getPageAt(curPage);
        if (currentPage != null) {
            if (shouldSetTopAlignedPivotForWidget(curPage)) {
                currentPage.setPivotY(0.0f);
                currentPage.setPivotX(0.0f);
                currentPage.setPivotX((float) (currentPage.getMeasuredWidth() / FLAG_HAS_LOCAL_MINUTE));
            }
            if (currentPage.getScaleX() >= 1.0f && currentPage.getScaleY() >= 1.0f) {
                this.mZoomInOutAnim = new AnimatorSet();
                AnimatorSet animatorSet = this.mZoomInOutAnim;
                Animator[] animatorArr = new Animator[FLAG_HAS_LOCAL_MINUTE];
                float[] fArr = new float[FLAG_HAS_LOCAL_HOUR];
                fArr[0] = this.BOUNCER_SCALE_FACTOR;
                animatorArr[0] = ObjectAnimator.ofFloat(currentPage, "scaleX", fArr);
                fArr = new float[FLAG_HAS_LOCAL_HOUR];
                fArr[0] = this.BOUNCER_SCALE_FACTOR;
                animatorArr[FLAG_HAS_LOCAL_HOUR] = ObjectAnimator.ofFloat(currentPage, "scaleY", fArr);
                animatorSet.playTogether(animatorArr);
                this.mZoomInOutAnim.setDuration((long) this.mBouncerZoomInOutDuration);
                this.mZoomInOutAnim.setInterpolator(new DecelerateInterpolator(1.5f));
                this.mZoomInOutAnim.start();
            }
            if (currentPage instanceof KeyguardWidgetFrame) {
                ((KeyguardWidgetFrame) currentPage).onBouncerShowing(PERFORM_OVERSCROLL_ROTATION);
            }
        }
    }

    void setAddWidgetEnabled(boolean enabled) {
        if (this.mAddWidgetView != null && enabled) {
            addView(this.mAddWidgetView, 0);
            measure(this.mLastWidthMeasureSpec, this.mLastHeightMeasureSpec);
            setCurrentPage(this.mCurrentPage + FLAG_HAS_LOCAL_HOUR);
            this.mAddWidgetView = null;
        } else if (this.mAddWidgetView == null && !enabled) {
            View addWidget = findViewById(R.id.keyguard_add_widget);
            if (addWidget != null) {
                this.mAddWidgetView = addWidget;
                removeView(addWidget);
            }
        }
    }

    boolean isAddPage(int pageIndex) {
        View v = getChildAt(pageIndex);
        return (v == null || v.getId() != R.id.keyguard_add_widget) ? false : PERFORM_OVERSCROLL_ROTATION;
    }

    boolean isCameraPage(int pageIndex) {
        View v = getChildAt(pageIndex);
        return (v == null || !(v instanceof CameraWidgetFrame)) ? false : PERFORM_OVERSCROLL_ROTATION;
    }

    boolean isApplicationWidgetPage(int pageIndex) {
        View v = getChildAt(pageIndex);
        return (v == null || !(v instanceof ApplicationWidgetFrame)) ? false : PERFORM_OVERSCROLL_ROTATION;
    }

    protected boolean shouldSetTopAlignedPivotForWidget(int childIndex) {
        return (isCameraPage(childIndex) || !super.shouldSetTopAlignedPivotForWidget(childIndex)) ? false : PERFORM_OVERSCROLL_ROTATION;
    }

    private static int findClockInHierarchy(View view) {
        if (view instanceof TextClock) {
            return getClockFlags((TextClock) view);
        }
        if (!(view instanceof ViewGroup)) {
            return 0;
        }
        int flags = 0;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i += FLAG_HAS_LOCAL_HOUR) {
            flags |= findClockInHierarchy(group.getChildAt(i));
        }
        return flags;
    }

    private static int getClockFlags(TextClock clock) {
        int flags = 0;
        String timeZone = clock.getTimeZone();
        if (timeZone != null && !TimeZone.getDefault().equals(TimeZone.getTimeZone(timeZone))) {
            return 0;
        }
        CharSequence format = clock.getFormat();
        if (DateFormat.hasDesignator(format, clock.is24HourModeEnabled() ? 'k' : 'h')) {
            flags = 0 | FLAG_HAS_LOCAL_HOUR;
        }
        if (DateFormat.hasDesignator(format, 'm')) {
            flags |= FLAG_HAS_LOCAL_MINUTE;
        }
        return flags;
    }

    public void handleExternalApplicationWidgetEvent(MotionEvent event) {
        int applicationWidgetPage;
        beginApplicationWidgetEvent();
        boolean endWarp = false;
        if (indexOfChild(findViewById(R.id.keyguard_add_widget)) < 0) {
            applicationWidgetPage = 0;
        } else {
            applicationWidgetPage = FLAG_HAS_LOCAL_HOUR;
        }
        if (isApplicationWidgetPage(applicationWidgetPage) || this.mApplicationWidgetEventInProgress) {
            switch (event.getAction()) {
                case SlidingChallengeLayout.SCROLL_STATE_IDLE /*0*/:
                    this.mApplicationWidgetEventInProgress = PERFORM_OVERSCROLL_ROTATION;
                    userActivity();
                    startPageWarp(applicationWidgetPage);
                    break;
                case FLAG_HAS_LOCAL_HOUR /*1*/:
                case SlidingChallengeLayout.SCROLL_STATE_FADING /*3*/:
                    this.mApplicationWidgetEventInProgress = false;
                    endWarp = isWarping();
                    break;
            }
            dispatchTouchEvent(event);
            if (endWarp) {
                stopPageWarp();
            }
        }
        endApplicationWidgetEvent();
    }

    public void handleExternalCameraEvent(MotionEvent event) {
        beginCameraEvent();
        int cameraPage = getPageCount() - 1;
        boolean endWarp = false;
        if (isCameraPage(cameraPage) || this.mCameraEventInProgress) {
            switch (event.getAction()) {
                case SlidingChallengeLayout.SCROLL_STATE_IDLE /*0*/:
                    this.mCameraEventInProgress = PERFORM_OVERSCROLL_ROTATION;
                    userActivity();
                    startPageWarp(cameraPage);
                    break;
                case FLAG_HAS_LOCAL_HOUR /*1*/:
                case SlidingChallengeLayout.SCROLL_STATE_FADING /*3*/:
                    this.mCameraEventInProgress = false;
                    endWarp = isWarping();
                    break;
            }
            dispatchTouchEvent(event);
            if (endWarp) {
                stopPageWarp();
            }
        }
        endCameraEvent();
    }
}
