package com.cyngn.keyguard;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.View.BaseSavedState;
import android.view.View.MeasureSpec;
import android.view.View.OnLongClickListener;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewGroup.OnHierarchyChangeListener;
import android.view.ViewParent;
import android.view.ViewPropertyAnimator;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.widget.Scroller;
import java.util.ArrayList;

public abstract class PagedView extends ViewGroup implements OnHierarchyChangeListener {
    protected static final float ALPHA_QUANTIZE_LEVEL = 1.0E-4f;
    static final int APPLICATION_WIDGET_PAGE_NUMBER = 1;
    static final int AUTOMATIC_PAGE_SPACING = -1;
    private static final boolean DEBUG = false;
    private static final boolean DEBUG_WARP = false;
    private static final boolean DISABLE_FLING_TO_DELETE = false;
    private static final boolean DISABLE_TOUCH_INTERACTION = false;
    private static final boolean DISABLE_TOUCH_SIDE_PAGES = true;
    private static final int FLING_THRESHOLD_VELOCITY = 1500;
    protected static final int INVALID_PAGE = -1;
    protected static final int INVALID_POINTER = -1;
    private static final int MIN_FLING_VELOCITY = 500;
    private static final int MIN_LENGTH_FOR_FLING = 25;
    private static final int MIN_SNAP_VELOCITY = 1500;
    protected static final float NANOTIME_DIV = 1.0E9f;
    private static final float OVERSCROLL_ACCELERATE_FACTOR = 2.0f;
    private static final float OVERSCROLL_DAMP_FACTOR = 0.14f;
    protected static final int PAGE_SNAP_ANIMATION_DURATION = 750;
    private static final float RETURN_TO_ORIGINAL_PAGE_THRESHOLD = 0.33f;
    private static final float SIGNIFICANT_MOVE_THRESHOLD = 0.5f;
    protected static final int SLOW_PAGE_SNAP_ANIMATION_DURATION = 950;
    private static final String TAG = "WidgetPagedView";
    protected static final float TOUCH_SLOP_SCALE = 1.0f;
    protected static final int TOUCH_STATE_NEXT_PAGE = 3;
    protected static final int TOUCH_STATE_PREV_PAGE = 2;
    protected static final int TOUCH_STATE_READY = 5;
    protected static final int TOUCH_STATE_REORDERING = 4;
    protected static final int TOUCH_STATE_REST = 0;
    protected static final int TOUCH_STATE_SCROLLING = 1;
    private static final float WARP_ANIMATE_AMOUNT = -75.0f;
    private static final int WARP_PEEK_ANIMATION_DURATION = 150;
    private static final int WARP_SNAP_DURATION = 160;
    protected static final int sScrollIndicatorFadeInDuration = 150;
    protected static final int sScrollIndicatorFadeOutDuration = 650;
    protected static final int sScrollIndicatorFlashDuration = 650;
    private int DELETE_SLIDE_IN_SIDE_PAGE_DURATION;
    private int DRAG_TO_DELETE_FADE_OUT_DURATION;
    private int FLING_TO_DELETE_FADE_OUT_DURATION;
    private float FLING_TO_DELETE_FRICTION;
    private float FLING_TO_DELETE_MAX_FLING_DEGREES;
    private int NUM_ANIMATIONS_RUNNING_BEFORE_ZOOM_OUT;
    private long REORDERING_DELETE_DROP_TARGET_FADE_DURATION;
    private int REORDERING_DROP_REPOSITION_DURATION;
    protected int REORDERING_REORDER_REPOSITION_DURATION;
    private float REORDERING_SIDE_PAGE_BUFFER_PERCENTAGE;
    private int REORDERING_SIDE_PAGE_HOVER_TIMEOUT;
    protected int REORDERING_ZOOM_IN_OUT_DURATION;
    Runnable hideScrollingIndicatorRunnable;
    protected int mActivePointerId;
    protected boolean mAllowOverScroll;
    private Rect mAltTmpRect;
    protected int mCellCountX;
    protected int mCellCountY;
    protected int mChildCountOnLastMeasure;
    private int[] mChildOffsets;
    private int[] mChildOffsetsWithLayoutScale;
    private int[] mChildRelativeOffsets;
    protected boolean mContentIsRefreshable;
    protected int mCurrentPage;
    protected boolean mDeferScrollUpdate;
    private boolean mDeferringForDelete;
    private View mDeleteDropTarget;
    private String mDeleteString;
    protected float mDensity;
    protected ArrayList<Boolean> mDirtyPageContent;
    private boolean mDownEventOnEdge;
    private float mDownMotionX;
    private float mDownMotionY;
    private float mDownScrollX;
    protected View mDragView;
    private int mEdgeSwipeRegionSize;
    protected boolean mFadeInAdjacentScreens;
    protected boolean mFirstLayout;
    protected int mFlingThresholdVelocity;
    protected int mFlingToDeleteThresholdVelocity;
    protected boolean mForceDrawAllChildrenNextFrame;
    protected boolean mForceScreenScrolled;
    private boolean mIsApplicationWidgetEvent;
    private boolean mIsCameraEvent;
    protected boolean mIsDataReady;
    protected boolean mIsPageMoving;
    private boolean mIsReordering;
    protected float mLastMotionX;
    protected float mLastMotionXRemainder;
    protected float mLastMotionY;
    private int mLastScreenCenter;
    protected float mLayoutScale;
    protected OnLongClickListener mLongClickListener;
    protected int mMaxScrollX;
    private int mMaximumVelocity;
    protected int mMinFlingVelocity;
    private float mMinScale;
    protected int mMinSnapVelocity;
    private int mMinimumWidth;
    protected int mNextPage;
    AnimatorListenerAdapter mOffScreenAnimationListener;
    private boolean mOnPageBeginWarpCalled;
    private boolean mOnPageEndWarpCalled;
    AnimatorListenerAdapter mOnScreenAnimationListener;
    private boolean mOnlyAllowEdgeSwipes;
    protected int mOverScrollX;
    protected int mPageSpacing;
    private int mPageSwapIndex;
    private PageSwitchListener mPageSwitchListener;
    private int mPageWarpIndex;
    private int mPagingTouchSlop;
    private float mParentDownMotionX;
    private float mParentDownMotionY;
    private int mPostReorderingPreZoomInRemainingAnimationCount;
    private Runnable mPostReorderingPreZoomInRunnable;
    private boolean mReorderingStarted;
    private View mScrollIndicator;
    private ValueAnimator mScrollIndicatorAnimator;
    private int mScrollIndicatorPaddingLeft;
    private int mScrollIndicatorPaddingRight;
    protected Scroller mScroller;
    private boolean mShouldShowScrollIndicator;
    private boolean mShouldShowScrollIndicatorImmediately;
    private int mSidePageHoverIndex;
    private Runnable mSidePageHoverRunnable;
    protected float mSmoothingTime;
    protected int[] mTempVisiblePagesRange;
    private Matrix mTmpInvMatrix;
    private float[] mTmpPoint;
    private Rect mTmpRect;
    private boolean mTopAlignPageWhenShrinkingForBouncer;
    protected float mTotalMotionX;
    protected int mTouchSlop;
    protected int mTouchState;
    protected float mTouchX;
    protected int mUnboundedScrollX;
    protected boolean mUsePagingTouchSlop;
    private VelocityTracker mVelocityTracker;
    private Rect mViewport;
    private ViewPropertyAnimator mWarpAnimation;
    private boolean mWarpPageExposed;
    private float mWarpPeekAmount;
    protected AnimatorSet mZoomInOutAnim;

    public interface PageSwitchListener {
        void onPageSwitched(View view, int i);

        void onPageSwitching(View view, int i);
    }

    private static class FlingAlongVectorAnimatorUpdateListener implements AnimatorUpdateListener {
        private final TimeInterpolator mAlphaInterpolator = new DecelerateInterpolator(0.75f);
        private View mDragView;
        private float mFriction;
        private Rect mFrom;
        private long mPrevTime;
        private PointF mVelocity;

        public FlingAlongVectorAnimatorUpdateListener(View dragView, PointF vel, Rect from, long startTime, float friction) {
            this.mDragView = dragView;
            this.mVelocity = vel;
            this.mFrom = from;
            this.mPrevTime = startTime;
            this.mFriction = PagedView.TOUCH_SLOP_SCALE - (this.mDragView.getResources().getDisplayMetrics().density * friction);
        }

        public void onAnimationUpdate(ValueAnimator animation) {
            float t = ((Float) animation.getAnimatedValue()).floatValue();
            long curTime = AnimationUtils.currentAnimationTimeMillis();
            Rect rect = this.mFrom;
            rect.left = (int) (((float) rect.left) + ((this.mVelocity.x * ((float) (curTime - this.mPrevTime))) / 1000.0f));
            rect = this.mFrom;
            rect.top = (int) (((float) rect.top) + ((this.mVelocity.y * ((float) (curTime - this.mPrevTime))) / 1000.0f));
            this.mDragView.setTranslationX((float) this.mFrom.left);
            this.mDragView.setTranslationY((float) this.mFrom.top);
            this.mDragView.setAlpha(PagedView.TOUCH_SLOP_SCALE - this.mAlphaInterpolator.getInterpolation(t));
            PointF pointF = this.mVelocity;
            pointF.x *= this.mFriction;
            pointF = this.mVelocity;
            pointF.y *= this.mFriction;
            this.mPrevTime = curTime;
        }
    }

    public static class SavedState extends BaseSavedState {
        public static final Creator<SavedState> CREATOR = new Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
        int currentPage;

        SavedState(Parcelable superState) {
            super(superState);
            this.currentPage = PagedView.INVALID_POINTER;
        }

        private SavedState(Parcel in) {
            super(in);
            this.currentPage = PagedView.INVALID_POINTER;
            this.currentPage = in.readInt();
        }

        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(this.currentPage);
        }
    }

    private static class ScrollInterpolator implements Interpolator {
        public float getInterpolation(float t) {
            t -= PagedView.TOUCH_SLOP_SCALE;
            return ((((t * t) * t) * t) * t) + PagedView.TOUCH_SLOP_SCALE;
        }
    }

    public abstract void onAddView(View view, int i);

    public abstract void onRemoveView(View view, boolean z);

    public abstract void onRemoveViewAnimationCompleted();

    public PagedView(Context context) {
        this(context, null);
    }

    public PagedView(Context context, AttributeSet attrs) {
        this(context, attrs, TOUCH_STATE_REST);
    }

    public PagedView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        this.mFirstLayout = DISABLE_TOUCH_SIDE_PAGES;
        this.mNextPage = INVALID_POINTER;
        this.mLastScreenCenter = INVALID_POINTER;
        this.mTouchState = TOUCH_STATE_REST;
        this.mForceScreenScrolled = DISABLE_TOUCH_INTERACTION;
        this.mCellCountX = TOUCH_STATE_REST;
        this.mCellCountY = TOUCH_STATE_REST;
        this.mAllowOverScroll = DISABLE_TOUCH_SIDE_PAGES;
        this.mTempVisiblePagesRange = new int[TOUCH_STATE_PREV_PAGE];
        this.mLayoutScale = TOUCH_SLOP_SCALE;
        this.mActivePointerId = INVALID_POINTER;
        this.mContentIsRefreshable = DISABLE_TOUCH_SIDE_PAGES;
        this.mFadeInAdjacentScreens = DISABLE_TOUCH_INTERACTION;
        this.mUsePagingTouchSlop = DISABLE_TOUCH_SIDE_PAGES;
        this.mDeferScrollUpdate = DISABLE_TOUCH_INTERACTION;
        this.mIsPageMoving = DISABLE_TOUCH_INTERACTION;
        this.mIsDataReady = DISABLE_TOUCH_SIDE_PAGES;
        this.mShouldShowScrollIndicator = DISABLE_TOUCH_INTERACTION;
        this.mShouldShowScrollIndicatorImmediately = DISABLE_TOUCH_INTERACTION;
        this.mViewport = new Rect();
        this.REORDERING_DROP_REPOSITION_DURATION = KeyguardViewManager.WALLPAPER_PAPER_OFFSET;
        this.REORDERING_REORDER_REPOSITION_DURATION = 300;
        this.REORDERING_ZOOM_IN_OUT_DURATION = CameraWidgetFrame.WIDGET_ANIMATION_DURATION;
        this.REORDERING_SIDE_PAGE_HOVER_TIMEOUT = 300;
        this.REORDERING_SIDE_PAGE_BUFFER_PERCENTAGE = 0.1f;
        this.REORDERING_DELETE_DROP_TARGET_FADE_DURATION = 150;
        this.mMinScale = TOUCH_SLOP_SCALE;
        this.mSidePageHoverIndex = INVALID_POINTER;
        this.mReorderingStarted = DISABLE_TOUCH_INTERACTION;
        this.NUM_ANIMATIONS_RUNNING_BEFORE_ZOOM_OUT = TOUCH_STATE_PREV_PAGE;
        this.mOnlyAllowEdgeSwipes = DISABLE_TOUCH_INTERACTION;
        this.mDownEventOnEdge = DISABLE_TOUCH_INTERACTION;
        this.mEdgeSwipeRegionSize = TOUCH_STATE_REST;
        this.mTmpInvMatrix = new Matrix();
        this.mTmpPoint = new float[TOUCH_STATE_PREV_PAGE];
        this.mTmpRect = new Rect();
        this.mAltTmpRect = new Rect();
        this.FLING_TO_DELETE_FADE_OUT_DURATION = MultiPaneChallengeLayout.ANIMATE_BOUNCE_DURATION;
        this.FLING_TO_DELETE_FRICTION = 0.035f;
        this.FLING_TO_DELETE_MAX_FLING_DEGREES = 65.0f;
        this.mFlingToDeleteThresholdVelocity = -1400;
        this.mDeferringForDelete = DISABLE_TOUCH_INTERACTION;
        this.DELETE_SLIDE_IN_SIDE_PAGE_DURATION = CameraWidgetFrame.WIDGET_ANIMATION_DURATION;
        this.DRAG_TO_DELETE_FADE_OUT_DURATION = MultiPaneChallengeLayout.ANIMATE_BOUNCE_DURATION;
        this.mTopAlignPageWhenShrinkingForBouncer = DISABLE_TOUCH_INTERACTION;
        this.mPageSwapIndex = INVALID_POINTER;
        this.mPageWarpIndex = INVALID_POINTER;
        this.hideScrollingIndicatorRunnable = new Runnable() {
            public void run() {
                PagedView.this.hideScrollingIndicator(PagedView.DISABLE_TOUCH_INTERACTION);
            }
        };
        this.mOnScreenAnimationListener = new AnimatorListenerAdapter() {
            public void onAnimationEnd(Animator animation) {
                PagedView.this.mWarpAnimation = null;
                if (PagedView.this.mTouchState != PagedView.TOUCH_STATE_SCROLLING && PagedView.this.mTouchState != PagedView.TOUCH_STATE_READY) {
                    PagedView.this.animateWarpPageOffScreen("onScreen end", PagedView.DISABLE_TOUCH_SIDE_PAGES);
                }
            }
        };
        this.mOffScreenAnimationListener = new AnimatorListenerAdapter() {
            public void onAnimationEnd(Animator animation) {
                PagedView.this.mWarpAnimation = null;
                PagedView.this.mWarpPageExposed = PagedView.DISABLE_TOUCH_INTERACTION;
            }
        };
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.PagedView, defStyle, TOUCH_STATE_REST);
        setPageSpacing(a.getDimensionPixelSize(TOUCH_STATE_REST, TOUCH_STATE_REST));
        this.mScrollIndicatorPaddingLeft = a.getDimensionPixelSize(TOUCH_STATE_SCROLLING, TOUCH_STATE_REST);
        this.mScrollIndicatorPaddingRight = a.getDimensionPixelSize(TOUCH_STATE_PREV_PAGE, TOUCH_STATE_REST);
        a.recycle();
        Resources r = getResources();
        this.mEdgeSwipeRegionSize = r.getDimensionPixelSize(R.dimen.kg_edge_swipe_region_size);
        this.mTopAlignPageWhenShrinkingForBouncer = r.getBoolean(R.bool.kg_top_align_page_shrink_on_bouncer_visible);
        setHapticFeedbackEnabled(DISABLE_TOUCH_INTERACTION);
        init();
    }

    protected void init() {
        this.mDirtyPageContent = new ArrayList();
        this.mDirtyPageContent.ensureCapacity(32);
        this.mScroller = new Scroller(getContext(), new ScrollInterpolator());
        this.mCurrentPage = TOUCH_STATE_REST;
        ViewConfiguration configuration = ViewConfiguration.get(getContext());
        this.mTouchSlop = configuration.getScaledTouchSlop();
        this.mPagingTouchSlop = configuration.getScaledPagingTouchSlop();
        this.mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();
        this.mDensity = getResources().getDisplayMetrics().density;
        this.mWarpPeekAmount = this.mDensity * WARP_ANIMATE_AMOUNT;
        this.mFlingToDeleteThresholdVelocity = (int) (((float) this.mFlingToDeleteThresholdVelocity) * this.mDensity);
        this.mFlingThresholdVelocity = (int) (this.mDensity * 1500.0f);
        this.mMinFlingVelocity = (int) (500.0f * this.mDensity);
        this.mMinSnapVelocity = (int) (this.mDensity * 1500.0f);
        setOnHierarchyChangeListener(this);
    }

    void setDeleteDropTarget(View v) {
        this.mDeleteDropTarget = v;
    }

    float[] mapPointFromViewToParent(View v, float x, float y) {
        this.mTmpPoint[TOUCH_STATE_REST] = x;
        this.mTmpPoint[TOUCH_STATE_SCROLLING] = y;
        v.getMatrix().mapPoints(this.mTmpPoint);
        float[] fArr = this.mTmpPoint;
        fArr[TOUCH_STATE_REST] = fArr[TOUCH_STATE_REST] + ((float) v.getLeft());
        fArr = this.mTmpPoint;
        fArr[TOUCH_STATE_SCROLLING] = fArr[TOUCH_STATE_SCROLLING] + ((float) v.getTop());
        return this.mTmpPoint;
    }

    float[] mapPointFromParentToView(View v, float x, float y) {
        this.mTmpPoint[TOUCH_STATE_REST] = x - ((float) v.getLeft());
        this.mTmpPoint[TOUCH_STATE_SCROLLING] = y - ((float) v.getTop());
        v.getMatrix().invert(this.mTmpInvMatrix);
        this.mTmpInvMatrix.mapPoints(this.mTmpPoint);
        return this.mTmpPoint;
    }

    void updateDragViewTranslationDuringDrag() {
        float y = this.mLastMotionY - this.mDownMotionY;
        this.mDragView.setTranslationX(((this.mLastMotionX - this.mDownMotionX) + ((float) getScrollX())) - this.mDownScrollX);
        this.mDragView.setTranslationY(y);
    }

    public void setMinScale(float f) {
        this.mMinScale = f;
        requestLayout();
    }

    public void setScaleX(float scaleX) {
        super.setScaleX(scaleX);
        if (isReordering(DISABLE_TOUCH_SIDE_PAGES)) {
            float[] p = mapPointFromParentToView(this, this.mParentDownMotionX, this.mParentDownMotionY);
            this.mLastMotionX = p[TOUCH_STATE_REST];
            this.mLastMotionY = p[TOUCH_STATE_SCROLLING];
            updateDragViewTranslationDuringDrag();
        }
    }

    int getViewportWidth() {
        return this.mViewport.width();
    }

    int getViewportHeight() {
        return this.mViewport.height();
    }

    int getViewportOffsetX() {
        return (getMeasuredWidth() - getViewportWidth()) / TOUCH_STATE_PREV_PAGE;
    }

    int getViewportOffsetY() {
        return (getMeasuredHeight() - getViewportHeight()) / TOUCH_STATE_PREV_PAGE;
    }

    public void setPageSwitchListener(PageSwitchListener pageSwitchListener) {
        this.mPageSwitchListener = pageSwitchListener;
        if (this.mPageSwitchListener != null) {
            this.mPageSwitchListener.onPageSwitched(getPageAt(this.mCurrentPage), this.mCurrentPage);
        }
    }

    protected void setDataIsReady() {
        this.mIsDataReady = DISABLE_TOUCH_SIDE_PAGES;
    }

    protected boolean isDataReady() {
        return this.mIsDataReady;
    }

    int getCurrentPage() {
        return this.mCurrentPage;
    }

    int getNextPage() {
        return this.mNextPage != INVALID_POINTER ? this.mNextPage : this.mCurrentPage;
    }

    int getPageCount() {
        return getChildCount();
    }

    View getPageAt(int index) {
        return getChildAt(index);
    }

    protected int indexToPage(int index) {
        return index;
    }

    protected void updateCurrentPageScroll() {
        int newX = getChildOffset(this.mCurrentPage) - getRelativeChildOffset(this.mCurrentPage);
        scrollTo(newX, TOUCH_STATE_REST);
        this.mScroller.setFinalX(newX);
        this.mScroller.forceFinished(DISABLE_TOUCH_SIDE_PAGES);
    }

    void setCurrentPage(int currentPage) {
        notifyPageSwitching(currentPage);
        if (!this.mScroller.isFinished()) {
            this.mScroller.abortAnimation();
        }
        if (getChildCount() != 0) {
            this.mForceScreenScrolled = DISABLE_TOUCH_SIDE_PAGES;
            this.mCurrentPage = Math.max(TOUCH_STATE_REST, Math.min(currentPage, getPageCount() + INVALID_POINTER));
            updateCurrentPageScroll();
            updateScrollingIndicator();
            notifyPageSwitched();
            invalidate();
        }
    }

    public void setOnlyAllowEdgeSwipes(boolean enable) {
        this.mOnlyAllowEdgeSwipes = enable;
    }

    protected void notifyPageSwitching(int whichPage) {
        if (this.mPageSwitchListener != null) {
            this.mPageSwitchListener.onPageSwitching(getPageAt(whichPage), whichPage);
        }
    }

    protected void notifyPageSwitched() {
        if (this.mPageSwitchListener != null) {
            this.mPageSwitchListener.onPageSwitched(getPageAt(this.mCurrentPage), this.mCurrentPage);
        }
    }

    protected void pageBeginMoving() {
        if (!this.mIsPageMoving) {
            this.mIsPageMoving = DISABLE_TOUCH_SIDE_PAGES;
            if (isWarping()) {
                dispatchOnPageBeginWarp();
                if (this.mPageSwapIndex != INVALID_POINTER) {
                    swapPages(this.mPageSwapIndex, this.mPageWarpIndex);
                }
            }
            onPageBeginMoving();
        }
    }

    private void dispatchOnPageBeginWarp() {
        if (!this.mOnPageBeginWarpCalled) {
            onPageBeginWarp();
            this.mOnPageBeginWarpCalled = DISABLE_TOUCH_SIDE_PAGES;
        }
        this.mOnPageEndWarpCalled = DISABLE_TOUCH_INTERACTION;
    }

    private void dispatchOnPageEndWarp() {
        if (!this.mOnPageEndWarpCalled) {
            onPageEndWarp();
            this.mOnPageEndWarpCalled = DISABLE_TOUCH_SIDE_PAGES;
        }
        this.mOnPageBeginWarpCalled = DISABLE_TOUCH_INTERACTION;
    }

    protected void pageEndMoving() {
        if (this.mIsPageMoving) {
            this.mIsPageMoving = DISABLE_TOUCH_INTERACTION;
            if (isWarping()) {
                if (this.mPageSwapIndex != INVALID_POINTER) {
                    swapPages(this.mPageSwapIndex, this.mPageWarpIndex);
                }
                dispatchOnPageEndWarp();
                resetPageWarp();
            }
            onPageEndMoving();
        }
    }

    private void resetPageWarp() {
        this.mPageSwapIndex = INVALID_POINTER;
        this.mPageWarpIndex = INVALID_POINTER;
    }

    protected boolean isPageMoving() {
        return this.mIsPageMoving;
    }

    protected void onPageBeginMoving() {
    }

    protected void onPageEndMoving() {
    }

    public void setOnLongClickListener(OnLongClickListener l) {
        this.mLongClickListener = l;
        int count = getPageCount();
        for (int i = TOUCH_STATE_REST; i < count; i += TOUCH_STATE_SCROLLING) {
            getPageAt(i).setOnLongClickListener(l);
        }
    }

    public void scrollBy(int x, int y) {
        scrollTo(this.mUnboundedScrollX + x, getScrollY() + y);
    }

    public void scrollTo(int x, int y) {
        this.mUnboundedScrollX = x;
        if (x < 0) {
            super.scrollTo(TOUCH_STATE_REST, y);
            if (this.mAllowOverScroll) {
                overScroll((float) x);
            }
        } else if (x > this.mMaxScrollX) {
            super.scrollTo(this.mMaxScrollX, y);
            if (this.mAllowOverScroll) {
                overScroll((float) (x - this.mMaxScrollX));
            }
        } else {
            this.mOverScrollX = x;
            super.scrollTo(x, y);
        }
        this.mTouchX = (float) x;
        this.mSmoothingTime = ((float) System.nanoTime()) / NANOTIME_DIV;
        if (isReordering(DISABLE_TOUCH_SIDE_PAGES)) {
            float[] p = mapPointFromParentToView(this, this.mParentDownMotionX, this.mParentDownMotionY);
            this.mLastMotionX = p[TOUCH_STATE_REST];
            this.mLastMotionY = p[TOUCH_STATE_SCROLLING];
            updateDragViewTranslationDuringDrag();
        }
    }

    protected boolean computeScrollHelper() {
        if (this.mScroller.computeScrollOffset()) {
            if (!(getScrollX() == this.mScroller.getCurrX() && getScrollY() == this.mScroller.getCurrY() && this.mOverScrollX == this.mScroller.getCurrX())) {
                scrollTo(this.mScroller.getCurrX(), this.mScroller.getCurrY());
            }
            invalidate();
            return DISABLE_TOUCH_SIDE_PAGES;
        } else if (this.mNextPage == INVALID_POINTER) {
            return DISABLE_TOUCH_INTERACTION;
        } else {
            this.mCurrentPage = Math.max(TOUCH_STATE_REST, Math.min(this.mNextPage, getPageCount() + INVALID_POINTER));
            this.mNextPage = INVALID_POINTER;
            notifyPageSwitched();
            if (this.mTouchState == 0) {
                pageEndMoving();
            }
            onPostReorderingAnimationCompleted();
            return DISABLE_TOUCH_SIDE_PAGES;
        }
    }

    public void computeScroll() {
        computeScrollHelper();
    }

    protected boolean shouldSetTopAlignedPivotForWidget(int childIndex) {
        return this.mTopAlignPageWhenShrinkingForBouncer;
    }

    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (!this.mIsDataReady || getChildCount() == 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }
        View parent = (View) getParent();
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int maxSize = Math.max(dm.widthPixels, dm.heightPixels);
        int scaledWidthSize = (int) (((float) ((int) (1.5f * ((float) maxSize)))) / this.mMinScale);
        int scaledHeightSize = (int) (((float) maxSize) / this.mMinScale);
        this.mViewport.set(TOUCH_STATE_REST, TOUCH_STATE_REST, widthSize, heightSize);
        if (widthMode == 0 || heightMode == 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        } else if (widthSize <= 0 || heightSize <= 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        } else {
            int verticalPadding = getPaddingTop() + getPaddingBottom();
            int horizontalPadding = getPaddingLeft() + getPaddingRight();
            int childCount = getChildCount();
            for (int i = TOUCH_STATE_REST; i < childCount; i += TOUCH_STATE_SCROLLING) {
                int childWidthMode;
                int childHeightMode;
                View child = getPageAt(i);
                LayoutParams lp = child.getLayoutParams();
                if (lp.width == -2) {
                    childWidthMode = Integer.MIN_VALUE;
                } else {
                    childWidthMode = 1073741824;
                }
                if (lp.height == -2) {
                    childHeightMode = Integer.MIN_VALUE;
                } else {
                    childHeightMode = 1073741824;
                }
                child.measure(MeasureSpec.makeMeasureSpec(widthSize - horizontalPadding, childWidthMode), MeasureSpec.makeMeasureSpec(heightSize - verticalPadding, childHeightMode));
            }
            setMeasuredDimension(scaledWidthSize, scaledHeightSize);
            invalidateCachedOffsets();
            if (!(this.mChildCountOnLastMeasure == getChildCount() || this.mDeferringForDelete)) {
                setCurrentPage(this.mCurrentPage);
            }
            this.mChildCountOnLastMeasure = getChildCount();
            if (childCount > 0 && this.mPageSpacing == INVALID_POINTER) {
                int offset = getRelativeChildOffset(TOUCH_STATE_REST);
                setPageSpacing(Math.max(offset, (widthSize - offset) - getChildAt(TOUCH_STATE_REST).getMeasuredWidth()));
            }
            updateScrollingIndicatorPosition();
            if (childCount > 0) {
                this.mMaxScrollX = getChildOffset(childCount + INVALID_POINTER) - getRelativeChildOffset(childCount + INVALID_POINTER);
            } else {
                this.mMaxScrollX = TOUCH_STATE_REST;
            }
        }
    }

    public void setPageSpacing(int pageSpacing) {
        this.mPageSpacing = pageSpacing;
        invalidateCachedOffsets();
    }

    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (this.mIsDataReady && getChildCount() != 0) {
            int childCount = getChildCount();
            int offsetX = getViewportOffsetX();
            int offsetY = getViewportOffsetY();
            this.mViewport.offset(offsetX, offsetY);
            int childLeft = offsetX + getRelativeChildOffset(TOUCH_STATE_REST);
            for (int i = TOUCH_STATE_REST; i < childCount; i += TOUCH_STATE_SCROLLING) {
                View child = getPageAt(i);
                int childTop = offsetY + getPaddingTop();
                if (child.getVisibility() != 8) {
                    int childWidth = getScaledMeasuredWidth(child);
                    child.layout(childLeft, childTop, child.getMeasuredWidth() + childLeft, childTop + child.getMeasuredHeight());
                    childLeft += this.mPageSpacing + childWidth;
                }
            }
            if (this.mFirstLayout && this.mCurrentPage >= 0 && this.mCurrentPage < getChildCount()) {
                setHorizontalScrollBarEnabled(DISABLE_TOUCH_INTERACTION);
                updateCurrentPageScroll();
                setHorizontalScrollBarEnabled(DISABLE_TOUCH_SIDE_PAGES);
                this.mFirstLayout = DISABLE_TOUCH_INTERACTION;
            }
            if (this.mPageSwapIndex != INVALID_POINTER) {
                swapPages(this.mPageSwapIndex, this.mPageWarpIndex);
            }
        }
    }

    protected void screenScrolled(int screenCenter) {
    }

    public void onChildViewAdded(View parent, View child) {
        this.mForceScreenScrolled = DISABLE_TOUCH_SIDE_PAGES;
        invalidate();
        invalidateCachedOffsets();
    }

    public void onChildViewRemoved(View parent, View child) {
        this.mForceScreenScrolled = DISABLE_TOUCH_SIDE_PAGES;
        invalidate();
        invalidateCachedOffsets();
    }

    protected void invalidateCachedOffsets() {
        int count = getChildCount();
        if (count == 0) {
            this.mChildOffsets = null;
            this.mChildRelativeOffsets = null;
            this.mChildOffsetsWithLayoutScale = null;
            return;
        }
        this.mChildOffsets = new int[count];
        this.mChildRelativeOffsets = new int[count];
        this.mChildOffsetsWithLayoutScale = new int[count];
        for (int i = TOUCH_STATE_REST; i < count; i += TOUCH_STATE_SCROLLING) {
            this.mChildOffsets[i] = INVALID_POINTER;
            this.mChildRelativeOffsets[i] = INVALID_POINTER;
            this.mChildOffsetsWithLayoutScale[i] = INVALID_POINTER;
        }
    }

    protected int getChildOffset(int index) {
        if (index < 0 || index > getChildCount() + INVALID_POINTER) {
            return TOUCH_STATE_REST;
        }
        int[] childOffsets = Float.compare(this.mLayoutScale, TOUCH_SLOP_SCALE) == 0 ? this.mChildOffsets : this.mChildOffsetsWithLayoutScale;
        if (childOffsets != null && childOffsets[index] != INVALID_POINTER) {
            return childOffsets[index];
        }
        if (getChildCount() == 0) {
            return TOUCH_STATE_REST;
        }
        int offset = getRelativeChildOffset(TOUCH_STATE_REST);
        for (int i = TOUCH_STATE_REST; i < index; i += TOUCH_STATE_SCROLLING) {
            offset += getScaledMeasuredWidth(getPageAt(i)) + this.mPageSpacing;
        }
        if (childOffsets == null) {
            return offset;
        }
        childOffsets[index] = offset;
        return offset;
    }

    protected int getRelativeChildOffset(int index) {
        if (index < 0 || index > getChildCount() + INVALID_POINTER) {
            return TOUCH_STATE_REST;
        }
        if (this.mChildRelativeOffsets != null && this.mChildRelativeOffsets[index] != INVALID_POINTER) {
            return this.mChildRelativeOffsets[index];
        }
        int offset = getPaddingLeft() + (((getViewportWidth() - (getPaddingLeft() + getPaddingRight())) - getChildWidth(index)) / TOUCH_STATE_PREV_PAGE);
        if (this.mChildRelativeOffsets == null) {
            return offset;
        }
        this.mChildRelativeOffsets[index] = offset;
        return offset;
    }

    protected int getScaledMeasuredWidth(View child) {
        int maxWidth;
        int measuredWidth = child.getMeasuredWidth();
        int minWidth = this.mMinimumWidth;
        if (minWidth > measuredWidth) {
            maxWidth = minWidth;
        } else {
            maxWidth = measuredWidth;
        }
        return (int) ((((float) maxWidth) * this.mLayoutScale) + SIGNIFICANT_MOVE_THRESHOLD);
    }

    void boundByReorderablePages(boolean isReordering, int[] range) {
    }

    protected void getVisiblePages(int[] range) {
        range[TOUCH_STATE_REST] = TOUCH_STATE_REST;
        range[TOUCH_STATE_SCROLLING] = getPageCount() + INVALID_POINTER;
    }

    protected boolean shouldDrawChild(View child) {
        return child.getAlpha() > 0.0f ? DISABLE_TOUCH_SIDE_PAGES : DISABLE_TOUCH_INTERACTION;
    }

    protected void dispatchDraw(Canvas canvas) {
        int screenCenter = this.mOverScrollX + (getViewportWidth() / TOUCH_STATE_PREV_PAGE);
        if (screenCenter != this.mLastScreenCenter || this.mForceScreenScrolled) {
            this.mForceScreenScrolled = DISABLE_TOUCH_INTERACTION;
            screenScrolled(screenCenter);
            this.mLastScreenCenter = screenCenter;
        }
        int pageCount = getChildCount();
        if (pageCount > 0) {
            getVisiblePages(this.mTempVisiblePagesRange);
            int leftScreen = this.mTempVisiblePagesRange[TOUCH_STATE_REST];
            int rightScreen = this.mTempVisiblePagesRange[TOUCH_STATE_SCROLLING];
            if (leftScreen != INVALID_POINTER && rightScreen != INVALID_POINTER) {
                long drawingTime = getDrawingTime();
                canvas.save();
                canvas.clipRect(getScrollX(), getScrollY(), (getScrollX() + getRight()) - getLeft(), (getScrollY() + getBottom()) - getTop());
                int i = pageCount + INVALID_POINTER;
                while (i >= 0) {
                    View v = getPageAt(i);
                    if (v != this.mDragView && (this.mForceDrawAllChildrenNextFrame || (leftScreen <= i && i <= rightScreen && shouldDrawChild(v)))) {
                        drawChild(canvas, v, drawingTime);
                    }
                    i += INVALID_POINTER;
                }
                if (this.mDragView != null) {
                    drawChild(canvas, this.mDragView, drawingTime);
                }
                this.mForceDrawAllChildrenNextFrame = DISABLE_TOUCH_INTERACTION;
                canvas.restore();
            }
        }
    }

    public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) {
        int page = indexToPage(indexOfChild(child));
        if (page == this.mCurrentPage && this.mScroller.isFinished()) {
            return DISABLE_TOUCH_INTERACTION;
        }
        snapToPage(page);
        return DISABLE_TOUCH_SIDE_PAGES;
    }

    protected boolean onRequestFocusInDescendants(int direction, Rect previouslyFocusedRect) {
        int focusablePage;
        if (this.mNextPage != INVALID_POINTER) {
            focusablePage = this.mNextPage;
        } else {
            focusablePage = this.mCurrentPage;
        }
        View v = getPageAt(focusablePage);
        if (v != null) {
            return v.requestFocus(direction, previouslyFocusedRect);
        }
        return DISABLE_TOUCH_INTERACTION;
    }

    public boolean dispatchUnhandledMove(View focused, int direction) {
        if (direction == 17) {
            if (getCurrentPage() > 0) {
                snapToPage(getCurrentPage() + INVALID_POINTER);
                return DISABLE_TOUCH_SIDE_PAGES;
            }
        } else if (direction == 66 && getCurrentPage() < getPageCount() + INVALID_POINTER) {
            snapToPage(getCurrentPage() + TOUCH_STATE_SCROLLING);
            return DISABLE_TOUCH_SIDE_PAGES;
        }
        return super.dispatchUnhandledMove(focused, direction);
    }

    public void addFocusables(ArrayList<View> views, int direction, int focusableMode) {
        if (this.mCurrentPage >= 0 && this.mCurrentPage < getPageCount()) {
            getPageAt(this.mCurrentPage).addFocusables(views, direction, focusableMode);
        }
        if (direction == 17) {
            if (this.mCurrentPage > 0) {
                getPageAt(this.mCurrentPage + INVALID_POINTER).addFocusables(views, direction, focusableMode);
            }
        } else if (direction == 66 && this.mCurrentPage < getPageCount() + INVALID_POINTER) {
            getPageAt(this.mCurrentPage + TOUCH_STATE_SCROLLING).addFocusables(views, direction, focusableMode);
        }
    }

    public void focusableViewAvailable(View focused) {
        View current = getPageAt(this.mCurrentPage);
        View v = focused;
        while (v != current) {
            if (v != this && (v.getParent() instanceof View)) {
                v = (View) v.getParent();
            } else {
                return;
            }
        }
        super.focusableViewAvailable(focused);
    }

    protected boolean hitsPreviousPage(float x, float y) {
        return x < ((float) ((getViewportOffsetX() + getRelativeChildOffset(this.mCurrentPage)) - this.mPageSpacing)) ? DISABLE_TOUCH_SIDE_PAGES : DISABLE_TOUCH_INTERACTION;
    }

    protected boolean hitsNextPage(float x, float y) {
        return x > ((float) (((getViewportOffsetX() + getViewportWidth()) - getRelativeChildOffset(this.mCurrentPage)) + this.mPageSpacing)) ? DISABLE_TOUCH_SIDE_PAGES : DISABLE_TOUCH_INTERACTION;
    }

    private boolean isTouchPointInViewportWithBuffer(int x, int y) {
        this.mTmpRect.set(this.mViewport.left - (this.mViewport.width() / TOUCH_STATE_PREV_PAGE), this.mViewport.top, this.mViewport.right + (this.mViewport.width() / TOUCH_STATE_PREV_PAGE), this.mViewport.bottom);
        return this.mTmpRect.contains(x, y);
    }

    private boolean isTouchPointInCurrentPage(int x, int y) {
        View v = getPageAt(getCurrentPage());
        if (v == null) {
            return DISABLE_TOUCH_INTERACTION;
        }
        this.mTmpRect.set(v.getLeft() - getScrollX(), TOUCH_STATE_REST, v.getRight() - getScrollX(), v.getBottom());
        return this.mTmpRect.contains(x, y);
    }

    public boolean onInterceptTouchEvent(MotionEvent ev) {
        acquireVelocityTrackerAndAddMovement(ev);
        if (getChildCount() <= 0) {
            return super.onInterceptTouchEvent(ev);
        }
        int action = ev.getAction();
        if (action == TOUCH_STATE_PREV_PAGE && this.mTouchState == TOUCH_STATE_SCROLLING) {
            return DISABLE_TOUCH_SIDE_PAGES;
        }
        switch (action & 255) {
            case TOUCH_STATE_REST /*0*/:
                if (this.mIsCameraEvent || this.mIsApplicationWidgetEvent) {
                    animateWarpPageOnScreen("interceptTouch(): DOWN");
                }
                saveDownState(ev);
                boolean finishedScrolling = (this.mScroller.isFinished() || Math.abs(this.mScroller.getFinalX() - this.mScroller.getCurrX()) < this.mTouchSlop) ? DISABLE_TOUCH_SIDE_PAGES : DISABLE_TOUCH_INTERACTION;
                if (!finishedScrolling) {
                    if (!this.mIsCameraEvent && !this.mIsApplicationWidgetEvent && !isTouchPointInViewportWithBuffer((int) this.mDownMotionX, (int) this.mDownMotionY)) {
                        setTouchState(TOUCH_STATE_REST);
                        break;
                    }
                    setTouchState(TOUCH_STATE_SCROLLING);
                    break;
                }
                setTouchState(TOUCH_STATE_REST);
                this.mScroller.abortAnimation();
                break;
                break;
            case TOUCH_STATE_SCROLLING /*1*/:
            case TOUCH_STATE_NEXT_PAGE /*3*/:
                resetTouchState();
                if (!isTouchPointInCurrentPage((int) this.mLastMotionX, (int) this.mLastMotionY)) {
                    return DISABLE_TOUCH_SIDE_PAGES;
                }
                break;
            case TOUCH_STATE_PREV_PAGE /*2*/:
                if (this.mActivePointerId != INVALID_POINTER && (this.mIsCameraEvent || this.mIsApplicationWidgetEvent || determineScrollingStart(ev))) {
                    startScrolling(ev);
                    break;
                }
            case SlidingChallengeLayout.LayoutParams.CHILD_TYPE_EXPAND_CHALLENGE_HANDLE /*6*/:
                onSecondaryPointerUp(ev);
                releaseVelocityTracker();
                break;
        }
        return this.mTouchState == 0 ? DISABLE_TOUCH_INTERACTION : DISABLE_TOUCH_SIDE_PAGES;
    }

    private void setTouchState(int touchState) {
        if (this.mTouchState != touchState) {
            onTouchStateChanged(touchState);
            this.mTouchState = touchState;
        }
    }

    void onTouchStateChanged(int newTouchState) {
    }

    private void saveDownState(MotionEvent ev) {
        float x = ev.getX();
        this.mLastMotionX = x;
        this.mDownMotionX = x;
        x = ev.getY();
        this.mLastMotionY = x;
        this.mDownMotionY = x;
        this.mDownScrollX = (float) getScrollX();
        float[] p = mapPointFromViewToParent(this, this.mLastMotionX, this.mLastMotionY);
        this.mParentDownMotionX = p[TOUCH_STATE_REST];
        this.mParentDownMotionY = p[TOUCH_STATE_SCROLLING];
        this.mLastMotionXRemainder = 0.0f;
        this.mTotalMotionX = 0.0f;
        this.mActivePointerId = ev.getPointerId(TOUCH_STATE_REST);
        int rightEdgeBoundary = (getMeasuredWidth() - getViewportOffsetX()) - this.mEdgeSwipeRegionSize;
        if (this.mDownMotionX <= ((float) (getViewportOffsetX() + this.mEdgeSwipeRegionSize)) || this.mDownMotionX >= ((float) rightEdgeBoundary)) {
            this.mDownEventOnEdge = DISABLE_TOUCH_SIDE_PAGES;
        }
    }

    protected boolean determineScrollingStart(MotionEvent ev) {
        boolean z = DISABLE_TOUCH_SIDE_PAGES;
        int pointerIndex = ev.findPointerIndex(this.mActivePointerId);
        if (pointerIndex == INVALID_POINTER) {
            return DISABLE_TOUCH_INTERACTION;
        }
        float x = ev.getX(pointerIndex);
        float y = ev.getY(pointerIndex);
        if (!isTouchPointInViewportWithBuffer((int) x, (int) y)) {
            return DISABLE_TOUCH_INTERACTION;
        }
        if (this.mOnlyAllowEdgeSwipes && !this.mDownEventOnEdge) {
            return DISABLE_TOUCH_INTERACTION;
        }
        int xDiff = (int) Math.abs(x - this.mLastMotionX);
        int yDiff = (int) Math.abs(y - this.mLastMotionY);
        int touchSlop = Math.round(TOUCH_SLOP_SCALE * ((float) this.mTouchSlop));
        boolean xPaged = xDiff > this.mPagingTouchSlop ? DISABLE_TOUCH_SIDE_PAGES : DISABLE_TOUCH_INTERACTION;
        boolean xMoved = xDiff > touchSlop ? DISABLE_TOUCH_SIDE_PAGES : DISABLE_TOUCH_INTERACTION;
        boolean yMoved = yDiff > touchSlop ? DISABLE_TOUCH_SIDE_PAGES : DISABLE_TOUCH_INTERACTION;
        if (!(xMoved || xPaged || yMoved) || (this.mUsePagingTouchSlop ? xPaged : xMoved)) {
            z = DISABLE_TOUCH_INTERACTION;
        }
        return z;
    }

    private void startScrolling(MotionEvent ev) {
        int pointerIndex = ev.findPointerIndex(this.mActivePointerId);
        if (pointerIndex != INVALID_POINTER) {
            float x = ev.getX(pointerIndex);
            setTouchState(TOUCH_STATE_SCROLLING);
            this.mTotalMotionX += Math.abs(this.mLastMotionX - x);
            this.mLastMotionX = x;
            this.mLastMotionXRemainder = 0.0f;
            this.mTouchX = (float) (getViewportOffsetX() + getScrollX());
            this.mSmoothingTime = ((float) System.nanoTime()) / NANOTIME_DIV;
            pageBeginMoving();
        }
    }

    protected float getMaxScrollProgress() {
        return TOUCH_SLOP_SCALE;
    }

    protected float getBoundedScrollProgress(int screenCenter, View v, int page) {
        int halfScreenSize = getViewportWidth() / TOUCH_STATE_PREV_PAGE;
        return getScrollProgress(Math.max(halfScreenSize, Math.min(this.mScrollX + halfScreenSize, screenCenter)), v, page);
    }

    protected float getScrollProgress(int screenCenter, View v, int page) {
        return Math.max(Math.min(((float) (screenCenter - ((getChildOffset(page) - getRelativeChildOffset(page)) + (getViewportWidth() / TOUCH_STATE_PREV_PAGE)))) / (((float) (getScaledMeasuredWidth(v) + this.mPageSpacing)) * TOUCH_SLOP_SCALE), getMaxScrollProgress()), -getMaxScrollProgress());
    }

    private float overScrollInfluenceCurve(float f) {
        f -= TOUCH_SLOP_SCALE;
        return ((f * f) * f) + TOUCH_SLOP_SCALE;
    }

    protected void acceleratedOverScroll(float amount) {
        int screenSize = getViewportWidth();
        float f = OVERSCROLL_ACCELERATE_FACTOR * (amount / ((float) screenSize));
        if (f != 0.0f) {
            if (Math.abs(f) >= TOUCH_SLOP_SCALE) {
                f /= Math.abs(f);
            }
            int overScrollAmount = Math.round(((float) screenSize) * f);
            if (amount < 0.0f) {
                this.mOverScrollX = overScrollAmount;
                super.scrollTo(TOUCH_STATE_REST, getScrollY());
            } else {
                this.mOverScrollX = this.mMaxScrollX + overScrollAmount;
                super.scrollTo(this.mMaxScrollX, getScrollY());
            }
            invalidate();
        }
    }

    protected void dampedOverScroll(float amount) {
        int screenSize = getViewportWidth();
        float f = amount / ((float) screenSize);
        if (f != 0.0f) {
            f = (f / Math.abs(f)) * overScrollInfluenceCurve(Math.abs(f));
            if (Math.abs(f) >= TOUCH_SLOP_SCALE) {
                f /= Math.abs(f);
            }
            int overScrollAmount = Math.round((OVERSCROLL_DAMP_FACTOR * f) * ((float) screenSize));
            if (amount < 0.0f) {
                this.mOverScrollX = overScrollAmount;
                super.scrollTo(TOUCH_STATE_REST, getScrollY());
            } else {
                this.mOverScrollX = this.mMaxScrollX + overScrollAmount;
                super.scrollTo(this.mMaxScrollX, getScrollY());
            }
            invalidate();
        }
    }

    protected void overScroll(float amount) {
        dampedOverScroll(amount);
    }

    protected float maxOverScroll() {
        return OVERSCROLL_DAMP_FACTOR * ((TOUCH_SLOP_SCALE / Math.abs(TOUCH_SLOP_SCALE)) * overScrollInfluenceCurve(Math.abs(TOUCH_SLOP_SCALE)));
    }

    public boolean onTouchEvent(MotionEvent ev) {
        if (getChildCount() <= 0) {
            return super.onTouchEvent(ev);
        }
        acquireVelocityTrackerAndAddMovement(ev);
        int pointerIndex;
        float x;
        float[] pt;
        switch (ev.getAction() & 255) {
            case TOUCH_STATE_REST /*0*/:
                if (!this.mScroller.isFinished()) {
                    this.mScroller.abortAnimation();
                }
                saveDownState(ev);
                if (this.mTouchState == TOUCH_STATE_SCROLLING) {
                    pageBeginMoving();
                } else {
                    setTouchState(TOUCH_STATE_READY);
                }
                if (this.mIsCameraEvent || this.mIsApplicationWidgetEvent) {
                    animateWarpPageOnScreen("onTouch(): DOWN");
                    break;
                }
            case TOUCH_STATE_SCROLLING /*1*/:
                if (this.mTouchState == TOUCH_STATE_SCROLLING) {
                    int activePointerId = this.mActivePointerId;
                    pointerIndex = ev.findPointerIndex(activePointerId);
                    if (pointerIndex == INVALID_POINTER) {
                        return DISABLE_TOUCH_SIDE_PAGES;
                    }
                    x = ev.getX(pointerIndex);
                    VelocityTracker velocityTracker = this.mVelocityTracker;
                    velocityTracker.computeCurrentVelocity(1000, (float) this.mMaximumVelocity);
                    int velocityX = (int) velocityTracker.getXVelocity(activePointerId);
                    int deltaX = (int) (x - this.mDownMotionX);
                    int pageWidth = getScaledMeasuredWidth(getPageAt(this.mCurrentPage));
                    boolean isSignificantMove = ((float) Math.abs(deltaX)) > ((float) pageWidth) * SIGNIFICANT_MOVE_THRESHOLD ? DISABLE_TOUCH_SIDE_PAGES : DISABLE_TOUCH_INTERACTION;
                    this.mTotalMotionX += Math.abs((this.mLastMotionX + this.mLastMotionXRemainder) - x);
                    boolean isFling = (this.mTotalMotionX <= 25.0f || Math.abs(velocityX) <= this.mFlingThresholdVelocity) ? DISABLE_TOUCH_INTERACTION : DISABLE_TOUCH_SIDE_PAGES;
                    boolean returnToOriginalPage = DISABLE_TOUCH_INTERACTION;
                    if (((float) Math.abs(deltaX)) > ((float) pageWidth) * RETURN_TO_ORIGINAL_PAGE_THRESHOLD && Math.signum((float) velocityX) != Math.signum((float) deltaX) && isFling) {
                        returnToOriginalPage = DISABLE_TOUCH_SIDE_PAGES;
                    }
                    int moveToPage;
                    int finalPage;
                    if (((isSignificantMove && deltaX > 0 && !isFling) || (isFling && velocityX > 0)) && this.mCurrentPage > 0) {
                        moveToPage = this.mPageWarpIndex != INVALID_POINTER ? this.mPageWarpIndex : this.mCurrentPage + INVALID_POINTER;
                        if (returnToOriginalPage) {
                            finalPage = this.mCurrentPage;
                        } else {
                            finalPage = moveToPage;
                        }
                        snapToPageWithVelocity(finalPage, velocityX);
                    } else if (((!isSignificantMove || deltaX >= 0 || isFling) && (!isFling || velocityX >= 0)) || this.mCurrentPage >= getChildCount() + INVALID_POINTER) {
                        snapToDestination();
                    } else {
                        moveToPage = this.mPageWarpIndex != INVALID_POINTER ? this.mPageWarpIndex : this.mCurrentPage + TOUCH_STATE_SCROLLING;
                        if (returnToOriginalPage) {
                            finalPage = this.mCurrentPage;
                        } else {
                            finalPage = moveToPage;
                        }
                        snapToPageWithVelocity(finalPage, velocityX);
                    }
                } else if (this.mTouchState == TOUCH_STATE_PREV_PAGE) {
                    nextPage = Math.max(TOUCH_STATE_REST, this.mCurrentPage + INVALID_POINTER);
                    if (nextPage != this.mCurrentPage) {
                        snapToPage(nextPage);
                    } else {
                        snapToDestination();
                    }
                } else if (this.mTouchState == TOUCH_STATE_NEXT_PAGE) {
                    nextPage = Math.min(getChildCount() + INVALID_POINTER, this.mCurrentPage + TOUCH_STATE_SCROLLING);
                    if (nextPage != this.mCurrentPage) {
                        snapToPage(nextPage);
                    } else {
                        snapToDestination();
                    }
                } else if (this.mTouchState == TOUCH_STATE_REORDERING) {
                    this.mLastMotionX = ev.getX();
                    this.mLastMotionY = ev.getY();
                    pt = mapPointFromViewToParent(this, this.mLastMotionX, this.mLastMotionY);
                    this.mParentDownMotionX = pt[TOUCH_STATE_REST];
                    this.mParentDownMotionY = pt[TOUCH_STATE_SCROLLING];
                    updateDragViewTranslationDuringDrag();
                    boolean handledFling = DISABLE_TOUCH_INTERACTION;
                    PointF flingToDeleteVector = isFlingingToDelete();
                    if (flingToDeleteVector != null) {
                        onFlingToDelete(flingToDeleteVector);
                        handledFling = DISABLE_TOUCH_SIDE_PAGES;
                    }
                    if (!handledFling) {
                        if (isHoveringOverDeleteDropTarget((int) this.mParentDownMotionX, (int) this.mParentDownMotionY)) {
                            onDropToDelete();
                        }
                    }
                } else {
                    if (this.mWarpPageExposed && !isAnimatingWarpPage()) {
                        animateWarpPageOffScreen("unhandled tap", DISABLE_TOUCH_SIDE_PAGES);
                    }
                    onUnhandledTap(ev);
                }
                removeCallbacks(this.mSidePageHoverRunnable);
                resetTouchState();
                break;
            case TOUCH_STATE_PREV_PAGE /*2*/:
                if (this.mTouchState != TOUCH_STATE_SCROLLING) {
                    if (this.mTouchState != TOUCH_STATE_REORDERING) {
                        if (this.mIsCameraEvent || this.mIsApplicationWidgetEvent || determineScrollingStart(ev)) {
                            startScrolling(ev);
                            break;
                        }
                    }
                    this.mLastMotionX = ev.getX();
                    this.mLastMotionY = ev.getY();
                    pt = mapPointFromViewToParent(this, this.mLastMotionX, this.mLastMotionY);
                    this.mParentDownMotionX = pt[TOUCH_STATE_REST];
                    this.mParentDownMotionY = pt[TOUCH_STATE_SCROLLING];
                    updateDragViewTranslationDuringDrag();
                    final int dragViewIndex = indexOfChild(this.mDragView);
                    int bufferSize = (int) (this.REORDERING_SIDE_PAGE_BUFFER_PERCENTAGE * ((float) getViewportWidth()));
                    int leftBufferEdge = (int) (mapPointFromViewToParent(this, (float) this.mViewport.left, 0.0f)[TOUCH_STATE_REST] + ((float) bufferSize));
                    int rightBufferEdge = (int) (mapPointFromViewToParent(this, (float) this.mViewport.right, 0.0f)[TOUCH_STATE_REST] - ((float) bufferSize));
                    boolean isHoveringOverDelete = isHoveringOverDeleteDropTarget((int) this.mParentDownMotionX, (int) this.mParentDownMotionY);
                    setPageHoveringOverDeleteDropTarget(dragViewIndex, isHoveringOverDelete);
                    float parentX = this.mParentDownMotionX;
                    int pageIndexToSnapTo = INVALID_POINTER;
                    if (parentX < ((float) leftBufferEdge) && dragViewIndex > 0) {
                        pageIndexToSnapTo = dragViewIndex + INVALID_POINTER;
                    } else if (parentX > ((float) rightBufferEdge) && dragViewIndex < getChildCount() + INVALID_POINTER) {
                        pageIndexToSnapTo = dragViewIndex + TOUCH_STATE_SCROLLING;
                    }
                    int pageUnderPointIndex = pageIndexToSnapTo;
                    if (pageUnderPointIndex > INVALID_POINTER && !isHoveringOverDelete) {
                        this.mTempVisiblePagesRange[TOUCH_STATE_REST] = TOUCH_STATE_REST;
                        this.mTempVisiblePagesRange[TOUCH_STATE_SCROLLING] = getPageCount() + INVALID_POINTER;
                        boundByReorderablePages(DISABLE_TOUCH_SIDE_PAGES, this.mTempVisiblePagesRange);
                        if (this.mTempVisiblePagesRange[TOUCH_STATE_REST] <= pageUnderPointIndex && pageUnderPointIndex <= this.mTempVisiblePagesRange[TOUCH_STATE_SCROLLING] && pageUnderPointIndex != this.mSidePageHoverIndex && this.mScroller.isFinished()) {
                            this.mSidePageHoverIndex = pageUnderPointIndex;
                            final int i = pageUnderPointIndex;
                            this.mSidePageHoverRunnable = new Runnable() {
                                public void run() {
                                    PagedView.this.mDownScrollX = (float) (PagedView.this.getChildOffset(i) - PagedView.this.getRelativeChildOffset(i));
                                    PagedView.this.snapToPage(i);
                                    int shiftDelta = dragViewIndex < i ? PagedView.INVALID_POINTER : PagedView.TOUCH_STATE_SCROLLING;
                                    int lowerIndex = dragViewIndex < i ? dragViewIndex + PagedView.TOUCH_STATE_SCROLLING : i;
                                    int upperIndex = dragViewIndex > i ? dragViewIndex + PagedView.INVALID_POINTER : i;
                                    for (int i = lowerIndex; i <= upperIndex; i += PagedView.TOUCH_STATE_SCROLLING) {
                                        View v = PagedView.this.getChildAt(i);
                                        int oldX = PagedView.this.getViewportOffsetX() + PagedView.this.getChildOffset(i);
                                        int newX = PagedView.this.getViewportOffsetX() + PagedView.this.getChildOffset(i + shiftDelta);
                                        AnimatorSet anim = (AnimatorSet) v.getTag();
                                        if (anim != null) {
                                            anim.cancel();
                                        }
                                        v.setTranslationX((float) (oldX - newX));
                                        anim = new AnimatorSet();
                                        anim.setDuration((long) PagedView.this.REORDERING_REORDER_REPOSITION_DURATION);
                                        Animator[] animatorArr = new Animator[PagedView.TOUCH_STATE_SCROLLING];
                                        float[] fArr = new float[PagedView.TOUCH_STATE_SCROLLING];
                                        fArr[PagedView.TOUCH_STATE_REST] = 0.0f;
                                        animatorArr[PagedView.TOUCH_STATE_REST] = ObjectAnimator.ofFloat(v, "translationX", fArr);
                                        anim.playTogether(animatorArr);
                                        anim.start();
                                        v.setTag(anim);
                                    }
                                    PagedView.this.removeView(PagedView.this.mDragView);
                                    PagedView.this.onRemoveView(PagedView.this.mDragView, PagedView.DISABLE_TOUCH_INTERACTION);
                                    PagedView.this.addView(PagedView.this.mDragView, i);
                                    PagedView.this.onAddView(PagedView.this.mDragView, i);
                                    PagedView.this.mSidePageHoverIndex = PagedView.INVALID_POINTER;
                                }
                            };
                            postDelayed(this.mSidePageHoverRunnable, (long) this.REORDERING_SIDE_PAGE_HOVER_TIMEOUT);
                            break;
                        }
                    }
                    removeCallbacks(this.mSidePageHoverRunnable);
                    this.mSidePageHoverIndex = INVALID_POINTER;
                    break;
                }
                pointerIndex = ev.findPointerIndex(this.mActivePointerId);
                if (pointerIndex != INVALID_POINTER) {
                    x = ev.getX(pointerIndex);
                    float deltaX2 = (this.mLastMotionX + this.mLastMotionXRemainder) - x;
                    this.mTotalMotionX += Math.abs(deltaX2);
                    if (Math.abs(deltaX2) < TOUCH_SLOP_SCALE) {
                        awakenScrollBars();
                        break;
                    }
                    this.mTouchX += deltaX2;
                    this.mSmoothingTime = ((float) System.nanoTime()) / NANOTIME_DIV;
                    if (isWarping()) {
                        KeyguardWidgetFrame v = (KeyguardWidgetFrame) getPageAt(this.mPageWarpIndex);
                        v.setTranslationX(v.getTranslationX() - deltaX2);
                    } else if (this.mDeferScrollUpdate) {
                        invalidate();
                    } else {
                        scrollBy((int) deltaX2, TOUCH_STATE_REST);
                    }
                    this.mLastMotionX = x;
                    this.mLastMotionXRemainder = deltaX2 - ((float) ((int) deltaX2));
                    break;
                }
                return DISABLE_TOUCH_SIDE_PAGES;
                break;
            case TOUCH_STATE_NEXT_PAGE /*3*/:
                if (this.mTouchState == TOUCH_STATE_SCROLLING) {
                    snapToDestination();
                }
                resetTouchState();
                break;
            case SlidingChallengeLayout.LayoutParams.CHILD_TYPE_EXPAND_CHALLENGE_HANDLE /*6*/:
                onSecondaryPointerUp(ev);
                break;
        }
        return DISABLE_TOUCH_SIDE_PAGES;
    }

    private void resetTouchState() {
        releaseVelocityTracker();
        endReordering();
        setTouchState(TOUCH_STATE_REST);
        this.mActivePointerId = INVALID_POINTER;
        this.mDownEventOnEdge = DISABLE_TOUCH_INTERACTION;
    }

    protected void onUnhandledTap(MotionEvent ev) {
    }

    public boolean onGenericMotionEvent(MotionEvent event) {
        if ((event.getSource() & TOUCH_STATE_PREV_PAGE) != 0) {
            switch (event.getAction()) {
                case KeyguardViewDragHelper.EDGE_BOTTOM /*8*/:
                    float hscroll;
                    float vscroll;
                    if ((event.getMetaState() & TOUCH_STATE_SCROLLING) != 0) {
                        vscroll = 0.0f;
                        hscroll = event.getAxisValue(9);
                    } else {
                        vscroll = -event.getAxisValue(9);
                        hscroll = event.getAxisValue(10);
                    }
                    if (!(hscroll == 0.0f && vscroll == 0.0f)) {
                        if (hscroll > 0.0f || vscroll > 0.0f) {
                            scrollRight();
                        } else {
                            scrollLeft();
                        }
                        return DISABLE_TOUCH_SIDE_PAGES;
                    }
                    break;
            }
        }
        return super.onGenericMotionEvent(event);
    }

    private void acquireVelocityTrackerAndAddMovement(MotionEvent ev) {
        if (this.mVelocityTracker == null) {
            this.mVelocityTracker = VelocityTracker.obtain();
        }
        this.mVelocityTracker.addMovement(ev);
    }

    private void releaseVelocityTracker() {
        if (this.mVelocityTracker != null) {
            this.mVelocityTracker.recycle();
            this.mVelocityTracker = null;
        }
    }

    private void onSecondaryPointerUp(MotionEvent ev) {
        int pointerIndex = (ev.getAction() & 65280) >> 8;
        if (ev.getPointerId(pointerIndex) == this.mActivePointerId) {
            int newPointerIndex = pointerIndex == 0 ? TOUCH_STATE_SCROLLING : TOUCH_STATE_REST;
            float x = ev.getX(newPointerIndex);
            this.mDownMotionX = x;
            this.mLastMotionX = x;
            this.mLastMotionY = ev.getY(newPointerIndex);
            this.mLastMotionXRemainder = 0.0f;
            this.mActivePointerId = ev.getPointerId(newPointerIndex);
            if (this.mVelocityTracker != null) {
                this.mVelocityTracker.clear();
            }
        }
    }

    public void requestChildFocus(View child, View focused) {
        super.requestChildFocus(child, focused);
        int page = indexToPage(indexOfChild(child));
        if (page >= 0 && page != getCurrentPage() && !isInTouchMode()) {
            snapToPage(page);
        }
    }

    protected int getChildIndexForRelativeOffset(int relativeOffset) {
        int childCount = getChildCount();
        for (int i = TOUCH_STATE_REST; i < childCount; i += TOUCH_STATE_SCROLLING) {
            int left = getRelativeChildOffset(i);
            int right = left + getScaledMeasuredWidth(getPageAt(i));
            if (left <= relativeOffset && relativeOffset <= right) {
                return i;
            }
        }
        return INVALID_POINTER;
    }

    protected int getChildWidth(int index) {
        int measuredWidth = getPageAt(index).getMeasuredWidth();
        int minWidth = this.mMinimumWidth;
        return minWidth > measuredWidth ? minWidth : measuredWidth;
    }

    int getPageNearestToPoint(float x) {
        int index = TOUCH_STATE_REST;
        for (int i = TOUCH_STATE_REST; i < getChildCount(); i += TOUCH_STATE_SCROLLING) {
            if (x < ((float) (getChildAt(i).getRight() - getScrollX()))) {
                return index;
            }
            index += TOUCH_STATE_SCROLLING;
        }
        return Math.min(index, getChildCount() + INVALID_POINTER);
    }

    int getPageNearestToCenterOfScreen() {
        int minDistanceFromScreenCenter = Integer.MAX_VALUE;
        int minDistanceFromScreenCenterIndex = INVALID_POINTER;
        int screenCenter = (getViewportOffsetX() + getScrollX()) + (getViewportWidth() / TOUCH_STATE_PREV_PAGE);
        int childCount = getChildCount();
        for (int i = TOUCH_STATE_REST; i < childCount; i += TOUCH_STATE_SCROLLING) {
            int distanceFromScreenCenter = Math.abs(((getViewportOffsetX() + getChildOffset(i)) + (getScaledMeasuredWidth(getPageAt(i)) / TOUCH_STATE_PREV_PAGE)) - screenCenter);
            if (distanceFromScreenCenter < minDistanceFromScreenCenter) {
                minDistanceFromScreenCenter = distanceFromScreenCenter;
                minDistanceFromScreenCenterIndex = i;
            }
        }
        return minDistanceFromScreenCenterIndex;
    }

    protected void snapToDestination() {
        int newPage = getPageNearestToCenterOfScreen();
        if (isWarping()) {
            cancelWarpAnimation("snapToDestination", this.mCurrentPage != newPage ? DISABLE_TOUCH_SIDE_PAGES : DISABLE_TOUCH_INTERACTION);
        }
        snapToPage(newPage, getPageSnapDuration());
    }

    private int getPageSnapDuration() {
        return isWarping() ? WARP_SNAP_DURATION : PAGE_SNAP_ANIMATION_DURATION;
    }

    float distanceInfluenceForSnapDuration(float f) {
        return (float) Math.sin((double) ((float) (((double) (f - SIGNIFICANT_MOVE_THRESHOLD)) * 0.4712389167638204d)));
    }

    protected void snapToPageWithVelocity(int whichPage, int velocity) {
        boolean z = DISABLE_TOUCH_INTERACTION;
        whichPage = Math.max(TOUCH_STATE_REST, Math.min(whichPage, getChildCount() + INVALID_POINTER));
        int halfScreenSize = getViewportWidth() / TOUCH_STATE_PREV_PAGE;
        if (isWarping()) {
            String str = "snapToPageWithVelocity";
            if (this.mCurrentPage != whichPage) {
                z = DISABLE_TOUCH_SIDE_PAGES;
            }
            cancelWarpAnimation(str, z);
        }
        int delta = (getChildOffset(whichPage) - getRelativeChildOffset(whichPage)) - this.mUnboundedScrollX;
        if (Math.abs(velocity) < this.mMinFlingVelocity) {
            snapToPage(whichPage, getPageSnapDuration());
            return;
        }
        snapToPage(whichPage, delta, Math.round(1000.0f * Math.abs((((float) halfScreenSize) + (((float) halfScreenSize) * distanceInfluenceForSnapDuration(Math.min(TOUCH_SLOP_SCALE, (((float) Math.abs(delta)) * TOUCH_SLOP_SCALE) / ((float) (halfScreenSize * TOUCH_STATE_PREV_PAGE)))))) / ((float) Math.max(this.mMinSnapVelocity, Math.abs(velocity))))) * TOUCH_STATE_REORDERING);
    }

    protected void snapToPage(int whichPage) {
        snapToPage(whichPage, getPageSnapDuration());
    }

    protected void snapToPageImmediately(int whichPage) {
        snapToPage(whichPage, getPageSnapDuration(), (boolean) DISABLE_TOUCH_SIDE_PAGES);
    }

    protected void snapToPage(int whichPage, int duration) {
        snapToPage(whichPage, duration, (boolean) DISABLE_TOUCH_INTERACTION);
    }

    protected void snapToPage(int whichPage, int duration, boolean immediate) {
        whichPage = Math.max(TOUCH_STATE_REST, Math.min(whichPage, getPageCount() + INVALID_POINTER));
        snapToPage(whichPage, (getChildOffset(whichPage) - getRelativeChildOffset(whichPage)) - this.mUnboundedScrollX, duration, immediate);
    }

    protected void snapToPage(int whichPage, int delta, int duration) {
        snapToPage(whichPage, delta, duration, DISABLE_TOUCH_INTERACTION);
    }

    protected void snapToPage(int whichPage, int delta, int duration, boolean immediate) {
        if (this.mPageSwapIndex == INVALID_POINTER || whichPage != this.mPageSwapIndex) {
            this.mNextPage = whichPage;
        } else {
            this.mNextPage = this.mPageWarpIndex;
        }
        if (isWarping()) {
            dispatchOnPageEndWarp();
            notifyPageSwitching(whichPage);
            resetPageWarp();
        } else {
            notifyPageSwitching(whichPage);
        }
        View focusedChild = getFocusedChild();
        if (!(focusedChild == null || whichPage == this.mCurrentPage || focusedChild != getPageAt(this.mCurrentPage))) {
            focusedChild.clearFocus();
        }
        pageBeginMoving();
        awakenScrollBars(duration);
        if (immediate) {
            duration = TOUCH_STATE_REST;
        } else if (duration == 0) {
            duration = Math.abs(delta);
        }
        if (!this.mScroller.isFinished()) {
            this.mScroller.abortAnimation();
        }
        this.mScroller.startScroll(this.mUnboundedScrollX, TOUCH_STATE_REST, delta, TOUCH_STATE_REST, duration);
        notifyPageSwitched();
        if (immediate) {
            computeScroll();
        }
        this.mForceScreenScrolled = DISABLE_TOUCH_SIDE_PAGES;
        invalidate();
    }

    protected boolean isWarping() {
        return this.mPageWarpIndex != INVALID_POINTER ? DISABLE_TOUCH_SIDE_PAGES : DISABLE_TOUCH_INTERACTION;
    }

    public void scrollLeft() {
        if (this.mScroller.isFinished()) {
            if (this.mCurrentPage > 0) {
                snapToPage(this.mCurrentPage + INVALID_POINTER);
            }
        } else if (this.mNextPage > 0) {
            snapToPage(this.mNextPage + INVALID_POINTER);
        }
    }

    public void scrollRight() {
        if (this.mScroller.isFinished()) {
            if (this.mCurrentPage < getChildCount() + INVALID_POINTER) {
                snapToPage(this.mCurrentPage + TOUCH_STATE_SCROLLING);
            }
        } else if (this.mNextPage < getChildCount() + INVALID_POINTER) {
            snapToPage(this.mNextPage + TOUCH_STATE_SCROLLING);
        }
    }

    public int getPageForView(View v) {
        if (v != null) {
            ViewParent vp = v.getParent();
            int count = getChildCount();
            for (int i = TOUCH_STATE_REST; i < count; i += TOUCH_STATE_SCROLLING) {
                if (vp == getPageAt(i)) {
                    return i;
                }
            }
        }
        return INVALID_POINTER;
    }

    protected View getScrollingIndicator() {
        return null;
    }

    protected boolean isScrollingIndicatorEnabled() {
        return DISABLE_TOUCH_INTERACTION;
    }

    protected void flashScrollingIndicator(boolean animated) {
        removeCallbacks(this.hideScrollingIndicatorRunnable);
        showScrollingIndicator(!animated ? DISABLE_TOUCH_SIDE_PAGES : DISABLE_TOUCH_INTERACTION);
        postDelayed(this.hideScrollingIndicatorRunnable, 650);
    }

    protected void showScrollingIndicator(boolean immediately) {
        this.mShouldShowScrollIndicator = DISABLE_TOUCH_SIDE_PAGES;
        this.mShouldShowScrollIndicatorImmediately = DISABLE_TOUCH_SIDE_PAGES;
        if (getChildCount() > TOUCH_STATE_SCROLLING && isScrollingIndicatorEnabled()) {
            this.mShouldShowScrollIndicator = DISABLE_TOUCH_INTERACTION;
            getScrollingIndicator();
            if (this.mScrollIndicator != null) {
                updateScrollingIndicatorPosition();
                this.mScrollIndicator.setVisibility(TOUCH_STATE_REST);
                cancelScrollingIndicatorAnimations();
                if (immediately) {
                    this.mScrollIndicator.setAlpha(TOUCH_SLOP_SCALE);
                    return;
                }
                float[] fArr = new float[TOUCH_STATE_SCROLLING];
                fArr[TOUCH_STATE_REST] = TOUCH_SLOP_SCALE;
                this.mScrollIndicatorAnimator = ObjectAnimator.ofFloat(this.mScrollIndicator, "alpha", fArr);
                this.mScrollIndicatorAnimator.setDuration(150);
                this.mScrollIndicatorAnimator.start();
            }
        }
    }

    protected void cancelScrollingIndicatorAnimations() {
        if (this.mScrollIndicatorAnimator != null) {
            this.mScrollIndicatorAnimator.cancel();
        }
    }

    protected void hideScrollingIndicator(boolean immediately) {
        if (getChildCount() > TOUCH_STATE_SCROLLING && isScrollingIndicatorEnabled()) {
            getScrollingIndicator();
            if (this.mScrollIndicator != null) {
                updateScrollingIndicatorPosition();
                cancelScrollingIndicatorAnimations();
                if (immediately) {
                    this.mScrollIndicator.setVisibility(TOUCH_STATE_REORDERING);
                    this.mScrollIndicator.setAlpha(0.0f);
                    return;
                }
                float[] fArr = new float[TOUCH_STATE_SCROLLING];
                fArr[TOUCH_STATE_REST] = 0.0f;
                this.mScrollIndicatorAnimator = ObjectAnimator.ofFloat(this.mScrollIndicator, "alpha", fArr);
                this.mScrollIndicatorAnimator.setDuration(650);
                this.mScrollIndicatorAnimator.addListener(new AnimatorListenerAdapter() {
                    private boolean cancelled = PagedView.DISABLE_TOUCH_INTERACTION;

                    public void onAnimationCancel(Animator animation) {
                        this.cancelled = PagedView.DISABLE_TOUCH_SIDE_PAGES;
                    }

                    public void onAnimationEnd(Animator animation) {
                        if (!this.cancelled) {
                            PagedView.this.mScrollIndicator.setVisibility(PagedView.TOUCH_STATE_REORDERING);
                        }
                    }
                });
                this.mScrollIndicatorAnimator.start();
            }
        }
    }

    protected boolean hasElasticScrollIndicator() {
        return DISABLE_TOUCH_SIDE_PAGES;
    }

    private void updateScrollingIndicator() {
        if (getChildCount() > TOUCH_STATE_SCROLLING && isScrollingIndicatorEnabled()) {
            getScrollingIndicator();
            if (this.mScrollIndicator != null) {
                updateScrollingIndicatorPosition();
            }
            if (this.mShouldShowScrollIndicator) {
                showScrollingIndicator(this.mShouldShowScrollIndicatorImmediately);
            }
        }
    }

    private void updateScrollingIndicatorPosition() {
        if (isScrollingIndicatorEnabled() && this.mScrollIndicator != null) {
            int numPages = getChildCount();
            int pageWidth = getViewportWidth();
            int lastChildIndex = Math.max(TOUCH_STATE_REST, getChildCount() + INVALID_POINTER);
            int trackWidth = (pageWidth - this.mScrollIndicatorPaddingLeft) - this.mScrollIndicatorPaddingRight;
            int indicatorWidth = (this.mScrollIndicator.getMeasuredWidth() - this.mScrollIndicator.getPaddingLeft()) - this.mScrollIndicator.getPaddingRight();
            int indicatorSpace = trackWidth / numPages;
            float f = (float) (trackWidth - indicatorSpace);
            int indicatorPos = ((int) (f * Math.max(0.0f, Math.min(TOUCH_SLOP_SCALE, ((float) getScrollX()) / ((float) (getChildOffset(lastChildIndex) - getRelativeChildOffset(lastChildIndex))))))) + this.mScrollIndicatorPaddingLeft;
            if (!hasElasticScrollIndicator()) {
                indicatorPos += (indicatorSpace / TOUCH_STATE_PREV_PAGE) - (indicatorWidth / TOUCH_STATE_PREV_PAGE);
            } else if (this.mScrollIndicator.getMeasuredWidth() != indicatorSpace) {
                this.mScrollIndicator.getLayoutParams().width = indicatorSpace;
                this.mScrollIndicator.requestLayout();
            }
            this.mScrollIndicator.setTranslationX((float) indicatorPos);
        }
    }

    void animateDragViewToOriginalPosition() {
        if (this.mDragView != null) {
            AnimatorSet anim = new AnimatorSet();
            anim.setDuration((long) this.REORDERING_DROP_REPOSITION_DURATION);
            Animator[] animatorArr = new Animator[TOUCH_STATE_PREV_PAGE];
            float[] fArr = new float[TOUCH_STATE_SCROLLING];
            fArr[TOUCH_STATE_REST] = 0.0f;
            animatorArr[TOUCH_STATE_REST] = ObjectAnimator.ofFloat(this.mDragView, "translationX", fArr);
            fArr = new float[TOUCH_STATE_SCROLLING];
            fArr[TOUCH_STATE_REST] = 0.0f;
            animatorArr[TOUCH_STATE_SCROLLING] = ObjectAnimator.ofFloat(this.mDragView, "translationY", fArr);
            anim.playTogether(animatorArr);
            anim.addListener(new AnimatorListenerAdapter() {
                public void onAnimationEnd(Animator animation) {
                    PagedView.this.onPostReorderingAnimationCompleted();
                }
            });
            anim.start();
        }
    }

    protected boolean zoomOut() {
        if (this.mZoomInOutAnim != null && this.mZoomInOutAnim.isRunning()) {
            this.mZoomInOutAnim.cancel();
        }
        if (getScaleX() < TOUCH_SLOP_SCALE || getScaleY() < TOUCH_SLOP_SCALE) {
            return DISABLE_TOUCH_INTERACTION;
        }
        this.mZoomInOutAnim = new AnimatorSet();
        this.mZoomInOutAnim.setDuration((long) this.REORDERING_ZOOM_IN_OUT_DURATION);
        AnimatorSet animatorSet = this.mZoomInOutAnim;
        Animator[] animatorArr = new Animator[TOUCH_STATE_PREV_PAGE];
        float[] fArr = new float[TOUCH_STATE_SCROLLING];
        fArr[TOUCH_STATE_REST] = this.mMinScale;
        animatorArr[TOUCH_STATE_REST] = ObjectAnimator.ofFloat(this, "scaleX", fArr);
        fArr = new float[TOUCH_STATE_SCROLLING];
        fArr[TOUCH_STATE_REST] = this.mMinScale;
        animatorArr[TOUCH_STATE_SCROLLING] = ObjectAnimator.ofFloat(this, "scaleY", fArr);
        animatorSet.playTogether(animatorArr);
        this.mZoomInOutAnim.addListener(new AnimatorListenerAdapter() {
            public void onAnimationStart(Animator animation) {
                if (PagedView.this.mDeleteDropTarget != null) {
                    PagedView.this.mDeleteDropTarget.setVisibility(PagedView.TOUCH_STATE_REST);
                    PagedView.this.mDeleteDropTarget.animate().alpha(PagedView.TOUCH_SLOP_SCALE).setDuration(PagedView.this.REORDERING_DELETE_DROP_TARGET_FADE_DURATION).setListener(new AnimatorListenerAdapter() {
                        public void onAnimationStart(Animator animation) {
                            PagedView.this.mDeleteDropTarget.setAlpha(0.0f);
                        }
                    });
                }
            }
        });
        this.mZoomInOutAnim.start();
        return DISABLE_TOUCH_SIDE_PAGES;
    }

    protected void onStartReordering() {
        if (AccessibilityManager.getInstance(this.mContext).isEnabled()) {
            announceForAccessibility(this.mContext.getString(R.string.keyguard_accessibility_widget_reorder_start));
        }
        setTouchState(TOUCH_STATE_REORDERING);
        this.mIsReordering = DISABLE_TOUCH_SIDE_PAGES;
        getVisiblePages(this.mTempVisiblePagesRange);
        boundByReorderablePages(DISABLE_TOUCH_SIDE_PAGES, this.mTempVisiblePagesRange);
        int i = TOUCH_STATE_REST;
        while (i < getPageCount()) {
            if (i < this.mTempVisiblePagesRange[TOUCH_STATE_REST] || i > this.mTempVisiblePagesRange[TOUCH_STATE_SCROLLING]) {
                getPageAt(i).setAlpha(0.0f);
            }
            i += TOUCH_STATE_SCROLLING;
        }
        invalidate();
    }

    private void onPostReorderingAnimationCompleted() {
        this.mPostReorderingPreZoomInRemainingAnimationCount += INVALID_POINTER;
        if (this.mPostReorderingPreZoomInRunnable != null && this.mPostReorderingPreZoomInRemainingAnimationCount == 0) {
            this.mPostReorderingPreZoomInRunnable.run();
            this.mPostReorderingPreZoomInRunnable = null;
        }
    }

    protected void onEndReordering() {
        if (AccessibilityManager.getInstance(this.mContext).isEnabled()) {
            if (this.mDeleteString != null) {
                announceForAccessibility(this.mDeleteString);
                this.mDeleteString = null;
            } else {
                announceForAccessibility(this.mContext.getString(R.string.keyguard_accessibility_widget_reorder_end));
            }
        }
        this.mIsReordering = DISABLE_TOUCH_INTERACTION;
        getVisiblePages(this.mTempVisiblePagesRange);
        boundByReorderablePages(DISABLE_TOUCH_SIDE_PAGES, this.mTempVisiblePagesRange);
        int i = TOUCH_STATE_REST;
        while (i < getPageCount()) {
            if (i < this.mTempVisiblePagesRange[TOUCH_STATE_REST] || i > this.mTempVisiblePagesRange[TOUCH_STATE_SCROLLING]) {
                getPageAt(i).setAlpha(TOUCH_SLOP_SCALE);
            }
            i += TOUCH_STATE_SCROLLING;
        }
    }

    public boolean startReordering() {
        int dragViewIndex = getPageNearestToCenterOfScreen();
        this.mTempVisiblePagesRange[TOUCH_STATE_REST] = TOUCH_STATE_REST;
        this.mTempVisiblePagesRange[TOUCH_STATE_SCROLLING] = getPageCount() + INVALID_POINTER;
        boundByReorderablePages(DISABLE_TOUCH_SIDE_PAGES, this.mTempVisiblePagesRange);
        if (this.mTempVisiblePagesRange[TOUCH_STATE_REST] > dragViewIndex || dragViewIndex > this.mTempVisiblePagesRange[TOUCH_STATE_SCROLLING]) {
            return DISABLE_TOUCH_INTERACTION;
        }
        this.mReorderingStarted = DISABLE_TOUCH_SIDE_PAGES;
        if (!zoomOut()) {
            return DISABLE_TOUCH_SIDE_PAGES;
        }
        this.mDragView = getChildAt(dragViewIndex);
        onStartReordering();
        return DISABLE_TOUCH_SIDE_PAGES;
    }

    boolean isReordering(boolean testTouchState) {
        boolean state = this.mIsReordering;
        if (!testTouchState) {
            return state;
        }
        return state & (this.mTouchState == TOUCH_STATE_REORDERING ? TOUCH_STATE_SCROLLING : TOUCH_STATE_REST);
    }

    void endReordering() {
        if (this.mReorderingStarted) {
            this.mReorderingStarted = DISABLE_TOUCH_INTERACTION;
            final Runnable onCompleteRunnable = new Runnable() {
                public void run() {
                    PagedView.this.onEndReordering();
                }
            };
            if (!this.mDeferringForDelete) {
                this.mPostReorderingPreZoomInRunnable = new Runnable() {
                    public void run() {
                        PagedView.this.zoomIn(onCompleteRunnable);
                    }
                };
                this.mPostReorderingPreZoomInRemainingAnimationCount = this.NUM_ANIMATIONS_RUNNING_BEFORE_ZOOM_OUT;
                snapToPage(indexOfChild(this.mDragView), TOUCH_STATE_REST);
                animateDragViewToOriginalPosition();
            }
        }
    }

    protected boolean zoomIn(final Runnable onCompleteRunnable) {
        if (this.mZoomInOutAnim != null && this.mZoomInOutAnim.isRunning()) {
            this.mZoomInOutAnim.cancel();
        }
        if (getScaleX() < TOUCH_SLOP_SCALE || getScaleY() < TOUCH_SLOP_SCALE) {
            this.mZoomInOutAnim = new AnimatorSet();
            this.mZoomInOutAnim.setDuration((long) this.REORDERING_ZOOM_IN_OUT_DURATION);
            AnimatorSet animatorSet = this.mZoomInOutAnim;
            Animator[] animatorArr = new Animator[TOUCH_STATE_PREV_PAGE];
            float[] fArr = new float[TOUCH_STATE_SCROLLING];
            fArr[TOUCH_STATE_REST] = TOUCH_SLOP_SCALE;
            animatorArr[TOUCH_STATE_REST] = ObjectAnimator.ofFloat(this, "scaleX", fArr);
            fArr = new float[TOUCH_STATE_SCROLLING];
            fArr[TOUCH_STATE_REST] = TOUCH_SLOP_SCALE;
            animatorArr[TOUCH_STATE_SCROLLING] = ObjectAnimator.ofFloat(this, "scaleY", fArr);
            animatorSet.playTogether(animatorArr);
            this.mZoomInOutAnim.addListener(new AnimatorListenerAdapter() {
                public void onAnimationStart(Animator animation) {
                    if (PagedView.this.mDeleteDropTarget != null) {
                        PagedView.this.mDeleteDropTarget.animate().alpha(0.0f).setDuration(PagedView.this.REORDERING_DELETE_DROP_TARGET_FADE_DURATION).setListener(new AnimatorListenerAdapter() {
                            public void onAnimationEnd(Animator animation) {
                                PagedView.this.mDeleteDropTarget.setVisibility(8);
                            }
                        });
                    }
                }

                public void onAnimationCancel(Animator animation) {
                    PagedView.this.mDragView = null;
                }

                public void onAnimationEnd(Animator animation) {
                    PagedView.this.mDragView = null;
                    if (onCompleteRunnable != null) {
                        onCompleteRunnable.run();
                    }
                }
            });
            this.mZoomInOutAnim.start();
            return DISABLE_TOUCH_SIDE_PAGES;
        } else if (onCompleteRunnable == null) {
            return DISABLE_TOUCH_INTERACTION;
        } else {
            onCompleteRunnable.run();
            return DISABLE_TOUCH_INTERACTION;
        }
    }

    private PointF isFlingingToDelete() {
        this.mVelocityTracker.computeCurrentVelocity(1000, (float) ViewConfiguration.get(getContext()).getScaledMaximumFlingVelocity());
        if (this.mVelocityTracker.getYVelocity() < ((float) this.mFlingToDeleteThresholdVelocity)) {
            PointF vel = new PointF(this.mVelocityTracker.getXVelocity(), this.mVelocityTracker.getYVelocity());
            PointF upVec = new PointF(0.0f, -1.0f);
            if (((double) ((float) Math.acos((double) (((vel.x * upVec.x) + (vel.y * upVec.y)) / (vel.length() * upVec.length()))))) <= Math.toRadians((double) this.FLING_TO_DELETE_MAX_FLING_DEGREES)) {
                return vel;
            }
        }
        return null;
    }

    private Runnable createPostDeleteAnimationRunnable(final View dragView) {
        return new Runnable() {
            public void run() {
                int upperIndex;
                int dragViewIndex = PagedView.this.indexOfChild(dragView);
                PagedView.this.getVisiblePages(PagedView.this.mTempVisiblePagesRange);
                PagedView.this.boundByReorderablePages(PagedView.DISABLE_TOUCH_SIDE_PAGES, PagedView.this.mTempVisiblePagesRange);
                boolean isLastWidgetPage = PagedView.this.mTempVisiblePagesRange[PagedView.TOUCH_STATE_REST] == PagedView.this.mTempVisiblePagesRange[PagedView.TOUCH_STATE_SCROLLING] ? PagedView.DISABLE_TOUCH_SIDE_PAGES : PagedView.DISABLE_TOUCH_INTERACTION;
                boolean slideFromLeft = (isLastWidgetPage || dragViewIndex > PagedView.this.mTempVisiblePagesRange[PagedView.TOUCH_STATE_REST]) ? PagedView.DISABLE_TOUCH_SIDE_PAGES : PagedView.DISABLE_TOUCH_INTERACTION;
                if (slideFromLeft) {
                    PagedView.this.snapToPageImmediately(dragViewIndex + PagedView.INVALID_POINTER);
                }
                int firstIndex = isLastWidgetPage ? PagedView.TOUCH_STATE_REST : PagedView.this.mTempVisiblePagesRange[PagedView.TOUCH_STATE_REST];
                int lastIndex = Math.min(PagedView.this.mTempVisiblePagesRange[PagedView.TOUCH_STATE_SCROLLING], PagedView.this.getPageCount() + PagedView.INVALID_POINTER);
                int lowerIndex = slideFromLeft ? firstIndex : dragViewIndex + PagedView.TOUCH_STATE_SCROLLING;
                if (slideFromLeft) {
                    upperIndex = dragViewIndex + PagedView.INVALID_POINTER;
                } else {
                    upperIndex = lastIndex;
                }
                ArrayList<Animator> animations = new ArrayList();
                for (int i = lowerIndex; i <= upperIndex; i += PagedView.TOUCH_STATE_SCROLLING) {
                    int oldX;
                    int newX;
                    View v = PagedView.this.getChildAt(i);
                    if (slideFromLeft) {
                        if (i == 0) {
                            oldX = ((PagedView.this.getViewportOffsetX() + PagedView.this.getChildOffset(i)) - PagedView.this.getChildWidth(i)) - PagedView.this.mPageSpacing;
                        } else {
                            oldX = PagedView.this.getViewportOffsetX() + PagedView.this.getChildOffset(i + PagedView.INVALID_POINTER);
                        }
                        newX = PagedView.this.getViewportOffsetX() + PagedView.this.getChildOffset(i);
                    } else {
                        oldX = PagedView.this.getChildOffset(i) - PagedView.this.getChildOffset(i + PagedView.INVALID_POINTER);
                        newX = PagedView.TOUCH_STATE_REST;
                    }
                    AnimatorSet anim = (AnimatorSet) v.getTag();
                    if (anim != null) {
                        anim.cancel();
                    }
                    v.setAlpha(Math.max(v.getAlpha(), 0.01f));
                    v.setTranslationX((float) (oldX - newX));
                    anim = new AnimatorSet();
                    Animator[] animatorArr = new Animator[PagedView.TOUCH_STATE_PREV_PAGE];
                    float[] fArr = new float[PagedView.TOUCH_STATE_SCROLLING];
                    fArr[PagedView.TOUCH_STATE_REST] = PagedView.TOUCH_STATE_REST;
                    animatorArr[PagedView.TOUCH_STATE_REST] = ObjectAnimator.ofFloat(v, "translationX", fArr);
                    fArr = new float[PagedView.TOUCH_STATE_SCROLLING];
                    fArr[PagedView.TOUCH_STATE_REST] = 1.0f;
                    animatorArr[PagedView.TOUCH_STATE_SCROLLING] = ObjectAnimator.ofFloat(v, "alpha", fArr);
                    anim.playTogether(animatorArr);
                    animations.add(anim);
                    v.setTag(anim);
                }
                AnimatorSet slideAnimations = new AnimatorSet();
                slideAnimations.playTogether(animations);
                slideAnimations.setDuration((long) PagedView.this.DELETE_SLIDE_IN_SIDE_PAGE_DURATION);
                slideAnimations.addListener(new AnimatorListenerAdapter() {
                    public void onAnimationEnd(Animator animation) {
                        PagedView.this.zoomIn(new Runnable() {
                            public void run() {
                                PagedView.this.mDeferringForDelete = PagedView.DISABLE_TOUCH_INTERACTION;
                                PagedView.this.onEndReordering();
                                PagedView.this.onRemoveViewAnimationCompleted();
                            }
                        });
                    }
                });
                slideAnimations.start();
                PagedView.this.removeView(dragView);
                PagedView.this.onRemoveView(dragView, PagedView.DISABLE_TOUCH_SIDE_PAGES);
            }
        };
    }

    public void onFlingToDelete(PointF vel) {
        final long startTime = AnimationUtils.currentAnimationTimeMillis();
        TimeInterpolator tInterpolator = new TimeInterpolator() {
            private int mCount = PagedView.INVALID_POINTER;
            private float mOffset;
            private long mStartTime = startTime;

            public float getInterpolation(float t) {
                if (this.mCount < 0) {
                    this.mCount += PagedView.TOUCH_STATE_SCROLLING;
                } else if (this.mCount == 0) {
                    this.mOffset = Math.min(PagedView.SIGNIFICANT_MOVE_THRESHOLD, ((float) (AnimationUtils.currentAnimationTimeMillis() - this.mStartTime)) / ((float) PagedView.this.FLING_TO_DELETE_FADE_OUT_DURATION));
                    this.mCount += PagedView.TOUCH_STATE_SCROLLING;
                }
                return Math.min(PagedView.TOUCH_SLOP_SCALE, this.mOffset + t);
            }
        };
        Rect from = new Rect();
        View dragView = this.mDragView;
        from.left = (int) dragView.getTranslationX();
        from.top = (int) dragView.getTranslationY();
        AnimatorUpdateListener updateCb = new FlingAlongVectorAnimatorUpdateListener(dragView, vel, from, startTime, this.FLING_TO_DELETE_FRICTION);
        Resources resources = getContext().getResources();
        Object[] objArr = new Object[TOUCH_STATE_SCROLLING];
        objArr[TOUCH_STATE_REST] = this.mDragView.getContentDescription();
        this.mDeleteString = resources.getString(R.string.keyguard_accessibility_widget_deleted, objArr);
        final Runnable onAnimationEndRunnable = createPostDeleteAnimationRunnable(dragView);
        ValueAnimator mDropAnim = new ValueAnimator();
        mDropAnim.setInterpolator(tInterpolator);
        mDropAnim.setDuration((long) this.FLING_TO_DELETE_FADE_OUT_DURATION);
        mDropAnim.setFloatValues(new float[]{0.0f, TOUCH_SLOP_SCALE});
        mDropAnim.addUpdateListener(updateCb);
        mDropAnim.addListener(new AnimatorListenerAdapter() {
            public void onAnimationEnd(Animator animation) {
                onAnimationEndRunnable.run();
            }
        });
        mDropAnim.start();
        this.mDeferringForDelete = DISABLE_TOUCH_SIDE_PAGES;
    }

    private boolean isHoveringOverDeleteDropTarget(int x, int y) {
        if (this.mDeleteDropTarget == null) {
            return DISABLE_TOUCH_INTERACTION;
        }
        this.mAltTmpRect.set(TOUCH_STATE_REST, TOUCH_STATE_REST, TOUCH_STATE_REST, TOUCH_STATE_REST);
        View parent = (View) this.mDeleteDropTarget.getParent();
        if (parent != null) {
            parent.getGlobalVisibleRect(this.mAltTmpRect);
        }
        this.mDeleteDropTarget.getGlobalVisibleRect(this.mTmpRect);
        this.mTmpRect.offset(-this.mAltTmpRect.left, -this.mAltTmpRect.top);
        return this.mTmpRect.contains(x, y);
    }

    protected void setPageHoveringOverDeleteDropTarget(int viewIndex, boolean isHovering) {
    }

    private void onDropToDelete() {
        View dragView = this.mDragView;
        ArrayList<Animator> animations = new ArrayList();
        AnimatorSet motionAnim = new AnimatorSet();
        motionAnim.setInterpolator(new DecelerateInterpolator(OVERSCROLL_ACCELERATE_FACTOR));
        Animator[] animatorArr = new Animator[TOUCH_STATE_PREV_PAGE];
        float[] fArr = new float[TOUCH_STATE_SCROLLING];
        fArr[TOUCH_STATE_REST] = 0.0f;
        animatorArr[TOUCH_STATE_REST] = ObjectAnimator.ofFloat(dragView, "scaleX", fArr);
        fArr = new float[TOUCH_STATE_SCROLLING];
        fArr[TOUCH_STATE_REST] = 0.0f;
        animatorArr[TOUCH_STATE_SCROLLING] = ObjectAnimator.ofFloat(dragView, "scaleY", fArr);
        motionAnim.playTogether(animatorArr);
        animations.add(motionAnim);
        AnimatorSet alphaAnim = new AnimatorSet();
        alphaAnim.setInterpolator(new LinearInterpolator());
        animatorArr = new Animator[TOUCH_STATE_SCROLLING];
        fArr = new float[TOUCH_STATE_SCROLLING];
        fArr[TOUCH_STATE_REST] = 0.0f;
        animatorArr[TOUCH_STATE_REST] = ObjectAnimator.ofFloat(dragView, "alpha", fArr);
        alphaAnim.playTogether(animatorArr);
        animations.add(alphaAnim);
        Resources resources = getContext().getResources();
        Object[] objArr = new Object[TOUCH_STATE_SCROLLING];
        objArr[TOUCH_STATE_REST] = this.mDragView.getContentDescription();
        this.mDeleteString = resources.getString(R.string.keyguard_accessibility_widget_deleted, objArr);
        final Runnable onAnimationEndRunnable = createPostDeleteAnimationRunnable(dragView);
        AnimatorSet anim = new AnimatorSet();
        anim.playTogether(animations);
        anim.setDuration((long) this.DRAG_TO_DELETE_FADE_OUT_DURATION);
        anim.addListener(new AnimatorListenerAdapter() {
            public void onAnimationEnd(Animator animation) {
                onAnimationEndRunnable.run();
            }
        });
        anim.start();
        this.mDeferringForDelete = DISABLE_TOUCH_SIDE_PAGES;
    }

    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        boolean z = DISABLE_TOUCH_SIDE_PAGES;
        super.onInitializeAccessibilityNodeInfo(info);
        if (getPageCount() <= TOUCH_STATE_SCROLLING) {
            z = DISABLE_TOUCH_INTERACTION;
        }
        info.setScrollable(z);
        if (getCurrentPage() < getPageCount() + INVALID_POINTER) {
            info.addAction(4096);
        }
        if (getCurrentPage() > 0) {
            info.addAction(8192);
        }
    }

    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        super.onInitializeAccessibilityEvent(event);
        event.setScrollable(DISABLE_TOUCH_SIDE_PAGES);
        if (event.getEventType() == 4096) {
            event.setFromIndex(this.mCurrentPage);
            event.setToIndex(this.mCurrentPage);
            event.setItemCount(getChildCount());
        }
    }

    public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (super.performAccessibilityAction(action, arguments)) {
            return DISABLE_TOUCH_SIDE_PAGES;
        }
        switch (action) {
            case 4096:
                if (getCurrentPage() < getPageCount() + INVALID_POINTER) {
                    scrollRight();
                    return DISABLE_TOUCH_SIDE_PAGES;
                }
                break;
            case 8192:
                if (getCurrentPage() > 0) {
                    scrollLeft();
                    return DISABLE_TOUCH_SIDE_PAGES;
                }
                break;
        }
        return DISABLE_TOUCH_INTERACTION;
    }

    public boolean onHoverEvent(MotionEvent event) {
        return DISABLE_TOUCH_SIDE_PAGES;
    }

    void beginCameraEvent() {
        this.mIsCameraEvent = DISABLE_TOUCH_SIDE_PAGES;
    }

    void endCameraEvent() {
        this.mIsCameraEvent = DISABLE_TOUCH_INTERACTION;
    }

    void beginApplicationWidgetEvent() {
        this.mIsApplicationWidgetEvent = DISABLE_TOUCH_SIDE_PAGES;
    }

    void endApplicationWidgetEvent() {
        this.mIsApplicationWidgetEvent = DISABLE_TOUCH_INTERACTION;
    }

    private void cancelWarpAnimation(String msg, boolean abortAnimation) {
        if (abortAnimation) {
            KeyguardWidgetFrame v = (KeyguardWidgetFrame) getPageAt(this.mPageWarpIndex);
            v.animate().cancel();
            scrollBy(Math.round(-v.getTranslationX()), TOUCH_STATE_REST);
            v.setTranslationX(0.0f);
            return;
        }
        animateWarpPageOffScreen("canceled", DISABLE_TOUCH_SIDE_PAGES);
    }

    private boolean isAnimatingWarpPage() {
        return this.mWarpAnimation != null ? DISABLE_TOUCH_SIDE_PAGES : DISABLE_TOUCH_INTERACTION;
    }

    private void animateWarpPageOnScreen(String reason) {
        if (isWarping() && !this.mWarpPageExposed) {
            this.mWarpPageExposed = DISABLE_TOUCH_SIDE_PAGES;
            dispatchOnPageBeginWarp();
            KeyguardWidgetFrame v = (KeyguardWidgetFrame) getPageAt(this.mPageWarpIndex);
            DecelerateInterpolator interp = new DecelerateInterpolator(1.5f);
            this.mWarpAnimation = v.animate();
            float translationX = 0.0f;
            if (this.mCurrentPage < this.mPageWarpIndex) {
                translationX = this.mWarpPeekAmount;
            } else if (this.mCurrentPage > this.mPageWarpIndex) {
                translationX = ((float) (((this.mCurrentPage - this.mPageWarpIndex) + INVALID_POINTER) * v.getWidth())) - this.mWarpPeekAmount;
            }
            this.mWarpAnimation.translationX(translationX).setInterpolator(interp).setDuration(150).setListener(this.mOnScreenAnimationListener);
        }
    }

    private void animateWarpPageOffScreen(String reason, boolean animate) {
        if (isWarping()) {
            dispatchOnPageEndWarp();
            ((KeyguardWidgetFrame) getPageAt(this.mPageWarpIndex)).animate().translationX(0.0f).setInterpolator(new AccelerateInterpolator(1.5f)).setDuration(animate ? 150 : 0).setListener(this.mOffScreenAnimationListener);
        }
    }

    void swapPages(int indexA, int indexB) {
        View viewA = getPageAt(indexA);
        View viewB = getPageAt(indexB);
        if (viewA != viewB && viewA != null && viewB != null) {
            int deltaX = viewA.getLeft() - viewB.getLeft();
            viewA.offsetLeftAndRight(-deltaX);
            viewB.offsetLeftAndRight(deltaX);
        }
    }

    public void startPageWarp(int pageIndex) {
        int cameraPage = getPageCount() + INVALID_POINTER;
        int applicationWidgetPage;
        if (indexOfChild(findViewById(R.id.keyguard_add_widget)) < 0) {
            applicationWidgetPage = TOUCH_STATE_REST;
        } else {
            applicationWidgetPage = TOUCH_STATE_SCROLLING;
        }
        if (!(pageIndex == cameraPage || pageIndex == applicationWidgetPage)) {
            this.mPageSwapIndex = this.mCurrentPage + TOUCH_STATE_SCROLLING;
        }
        this.mPageWarpIndex = pageIndex;
    }

    protected int getPageWarpIndex() {
        return this.mPageWarpIndex;
    }

    public void stopPageWarp() {
    }

    public void onPageBeginWarp() {
    }

    public void onPageEndWarp() {
    }
}
