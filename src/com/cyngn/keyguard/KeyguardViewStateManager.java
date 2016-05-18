package com.cyngn.keyguard;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import com.cyngn.keyguard.ChallengeLayout.OnBouncerStateChangedListener;
import com.cyngn.keyguard.SlidingChallengeLayout.OnChallengeScrolledListener;

public class KeyguardViewStateManager implements OnBouncerStateChangedListener, OnChallengeScrolledListener {
    private static final int SCREEN_ON_HINT_DURATION = 1000;
    private static final int SCREEN_ON_RING_HINT_DELAY = 300;
    private static final boolean SHOW_INITIAL_PAGE_HINTS = false;
    private static final String TAG = "KeyguardViewStateManager";
    private ChallengeLayout mChallengeLayout;
    int mChallengeTop;
    private int mCurrentPage;
    private Runnable mHideHintsRunnable;
    private KeyguardHostView mKeyguardHostView;
    private KeyguardHostViewMod mKeyguardHostViewMod;
    private KeyguardSecurityView mKeyguardSecurityContainer;
    private KeyguardWidgetPager mKeyguardWidgetPager;
    int mLastScrollState;
    Handler mMainQueue;
    private int mPageIndexOnPageBeginMoving;
    private int mPageListeningToSlider;
    private final AnimatorListener mPauseListener;
    private final AnimatorListener mResumeListener;
    private int[] mTmpLoc;
    private int[] mTmpPoint;

    public KeyguardViewStateManager(KeyguardHostView hostView) {
        this.mTmpPoint = new int[2];
        this.mTmpLoc = new int[2];
        this.mMainQueue = new Handler(Looper.myLooper());
        this.mLastScrollState = 0;
        this.mPageListeningToSlider = -1;
        this.mCurrentPage = -1;
        this.mPageIndexOnPageBeginMoving = -1;
        this.mChallengeTop = 0;
        this.mPauseListener = new AnimatorListenerAdapter() {
            public void onAnimationEnd(Animator animation) {
                KeyguardViewStateManager.this.mKeyguardSecurityContainer.onPause();
            }
        };
        this.mResumeListener = new AnimatorListenerAdapter() {
            public void onAnimationEnd(Animator animation) {
                if (((View) KeyguardViewStateManager.this.mKeyguardSecurityContainer).isShown()) {
                    KeyguardViewStateManager.this.mKeyguardSecurityContainer.onResume(0);
                }
            }
        };
        this.mHideHintsRunnable = new Runnable() {
            public void run() {
                if (KeyguardViewStateManager.this.mKeyguardWidgetPager != null) {
                    KeyguardViewStateManager.this.mKeyguardWidgetPager.hideOutlinesAndSidePages();
                }
            }
        };
        this.mKeyguardHostView = hostView;
    }

    public KeyguardViewStateManager(KeyguardHostViewMod hostViewMod) {
        this.mTmpPoint = new int[2];
        this.mTmpLoc = new int[2];
        this.mMainQueue = new Handler(Looper.myLooper());
        this.mLastScrollState = 0;
        this.mPageListeningToSlider = -1;
        this.mCurrentPage = -1;
        this.mPageIndexOnPageBeginMoving = -1;
        this.mChallengeTop = 0;
        this.mPauseListener = /* anonymous class already generated */;
        this.mResumeListener = /* anonymous class already generated */;
        this.mHideHintsRunnable = /* anonymous class already generated */;
        this.mKeyguardHostViewMod = hostViewMod;
    }

    public void setPagedView(KeyguardWidgetPager pagedView) {
        this.mKeyguardWidgetPager = pagedView;
        updateEdgeSwiping();
    }

    public void setChallengeLayout(ChallengeLayout layout) {
        this.mChallengeLayout = layout;
        updateEdgeSwiping();
    }

    private void updateEdgeSwiping() {
        if (this.mChallengeLayout != null && this.mKeyguardWidgetPager != null) {
            if (this.mChallengeLayout.isChallengeOverlapping()) {
                this.mKeyguardWidgetPager.setOnlyAllowEdgeSwipes(true);
            } else {
                this.mKeyguardWidgetPager.setOnlyAllowEdgeSwipes(SHOW_INITIAL_PAGE_HINTS);
            }
        }
    }

    public boolean isChallengeShowing() {
        if (this.mChallengeLayout != null) {
            return this.mChallengeLayout.isChallengeShowing();
        }
        return SHOW_INITIAL_PAGE_HINTS;
    }

    public boolean isChallengeOverlapping() {
        if (this.mChallengeLayout != null) {
            return this.mChallengeLayout.isChallengeOverlapping();
        }
        return SHOW_INITIAL_PAGE_HINTS;
    }

    public void setSecurityViewContainer(KeyguardSecurityView container) {
        this.mKeyguardSecurityContainer = container;
    }

    public void showBouncer(boolean show) {
        int i = R.string.keyguard_accessibility_show_bouncer;
        Resources resources;
        if (this.mKeyguardHostView != null) {
            resources = this.mKeyguardHostView.getContext().getResources();
            if (!show) {
                i = R.string.keyguard_accessibility_hide_bouncer;
            }
            this.mKeyguardHostView.announceForAccessibility(resources.getText(i));
            this.mKeyguardHostView.announceCurrentSecurityMethod();
        } else if (this.mKeyguardHostViewMod != null) {
            resources = this.mKeyguardHostViewMod.getContext().getResources();
            if (!show) {
                i = R.string.keyguard_accessibility_hide_bouncer;
            }
            this.mKeyguardHostViewMod.announceForAccessibility(resources.getText(i));
            this.mKeyguardHostViewMod.announceCurrentSecurityMethod();
        }
        this.mChallengeLayout.showBouncer();
    }

    public boolean isBouncing() {
        return this.mChallengeLayout.isBouncing();
    }

    public void fadeOutSecurity(int duration) {
        ((View) this.mKeyguardSecurityContainer).animate().alpha(0.0f).setDuration((long) duration).setListener(this.mPauseListener);
    }

    public void fadeInSecurity(int duration) {
        ((View) this.mKeyguardSecurityContainer).animate().alpha(1.0f).setDuration((long) duration).setListener(this.mResumeListener);
    }

    public void onPageBeginMoving() {
        if (this.mChallengeLayout.isChallengeOverlapping() && (this.mChallengeLayout instanceof SlidingChallengeLayout)) {
            this.mChallengeLayout.fadeOutChallenge();
            this.mPageIndexOnPageBeginMoving = this.mKeyguardWidgetPager.getCurrentPage();
        }
        if (this.mKeyguardHostView != null) {
            this.mKeyguardHostView.clearAppWidgetToShow();
            this.mKeyguardHostView.setOnDismissAction(null);
        } else if (this.mKeyguardHostViewMod != null) {
            this.mKeyguardHostViewMod.clearAppWidgetToShow();
            this.mKeyguardHostViewMod.setOnDismissAction(null);
        }
        if (this.mHideHintsRunnable != null) {
            this.mMainQueue.removeCallbacks(this.mHideHintsRunnable);
            this.mHideHintsRunnable = null;
        }
    }

    public void onPageEndMoving() {
        this.mPageIndexOnPageBeginMoving = -1;
    }

    public void onPageSwitching(View newPage, int newPageIndex) {
        if (this.mKeyguardWidgetPager != null && (this.mChallengeLayout instanceof SlidingChallengeLayout)) {
            boolean isCameraPage = newPage instanceof CameraWidgetFrame;
            if (isCameraPage) {
                ((CameraWidgetFrame) newPage).setUseFastTransition(this.mKeyguardWidgetPager.isWarping());
            }
            if (newPage instanceof ApplicationWidgetFrame) {
                ((ApplicationWidgetFrame) newPage).setUseFastTransition(this.mKeyguardWidgetPager.isWarping());
            }
            this.mChallengeLayout.setChallengeInteractive(!isCameraPage ? true : SHOW_INITIAL_PAGE_HINTS);
            int currentFlags = this.mKeyguardWidgetPager.getSystemUiVisibility();
            this.mKeyguardWidgetPager.setSystemUiVisibility(isCameraPage ? currentFlags | 33554432 : currentFlags & -33554433);
        }
        if (this.mPageIndexOnPageBeginMoving == this.mKeyguardWidgetPager.getNextPage() && (this.mChallengeLayout instanceof SlidingChallengeLayout)) {
            ((SlidingChallengeLayout) this.mChallengeLayout).fadeInChallenge();
            this.mKeyguardWidgetPager.setWidgetToResetOnPageFadeOut(-1);
        }
        this.mPageIndexOnPageBeginMoving = -1;
    }

    public void onPageSwitched(View newPage, int newPageIndex) {
        if (this.mCurrentPage != newPageIndex) {
            if (!(this.mKeyguardWidgetPager == null || this.mChallengeLayout == null)) {
                KeyguardWidgetFrame prevPage = this.mKeyguardWidgetPager.getWidgetPageAt(this.mCurrentPage);
                if (!(prevPage == null || this.mCurrentPage == this.mPageListeningToSlider || this.mCurrentPage == this.mKeyguardWidgetPager.getWidgetToResetOnPageFadeOut())) {
                    prevPage.resetSize();
                }
                KeyguardWidgetFrame newCurPage = this.mKeyguardWidgetPager.getWidgetPageAt(newPageIndex);
                if (!(!this.mChallengeLayout.isChallengeOverlapping() || newCurPage.isSmall() || this.mPageListeningToSlider == newPageIndex)) {
                    newCurPage.shrinkWidget(true);
                }
            }
            this.mCurrentPage = newPageIndex;
        }
    }

    public void onPageBeginWarp() {
        fadeOutSecurity(100);
        ((KeyguardWidgetFrame) this.mKeyguardWidgetPager.getPageAt(this.mKeyguardWidgetPager.getPageWarpIndex())).showFrame(this);
    }

    public void onPageEndWarp() {
        fadeInSecurity(SlidingChallengeLayout.CHALLENGE_FADE_IN_DURATION);
        ((KeyguardWidgetFrame) this.mKeyguardWidgetPager.getPageAt(this.mKeyguardWidgetPager.getPageWarpIndex())).hideFrame(this);
    }

    private int getChallengeTopRelativeToFrame(KeyguardWidgetFrame frame, int top) {
        this.mTmpPoint[0] = 0;
        this.mTmpPoint[1] = top;
        mapPoint((View) this.mChallengeLayout, frame, this.mTmpPoint);
        return this.mTmpPoint[1];
    }

    private void mapPoint(View fromView, View toView, int[] pt) {
        fromView.getLocationInWindow(this.mTmpLoc);
        int x = this.mTmpLoc[0];
        int y = this.mTmpLoc[1];
        toView.getLocationInWindow(this.mTmpLoc);
        int vX = this.mTmpLoc[0];
        int vY = this.mTmpLoc[1];
        pt[0] = pt[0] + (x - vX);
        pt[1] = pt[1] + (y - vY);
    }

    private void userActivity() {
        if (this.mKeyguardHostView != null) {
            this.mKeyguardHostView.onUserActivityTimeoutChanged();
            this.mKeyguardHostView.userActivity();
        } else if (this.mKeyguardHostViewMod != null) {
            this.mKeyguardHostViewMod.onUserActivityTimeoutChanged();
            this.mKeyguardHostViewMod.userActivity();
        }
    }

    public void onScrollStateChanged(int scrollState) {
        if (this.mKeyguardWidgetPager != null && this.mChallengeLayout != null) {
            boolean challengeOverlapping = this.mChallengeLayout.isChallengeOverlapping();
            KeyguardWidgetFrame frame;
            if (scrollState == 0) {
                frame = this.mKeyguardWidgetPager.getWidgetPageAt(this.mPageListeningToSlider);
                if (frame != null) {
                    if (!challengeOverlapping) {
                        if (this.mKeyguardWidgetPager.isPageMoving()) {
                            this.mKeyguardWidgetPager.setWidgetToResetOnPageFadeOut(this.mPageListeningToSlider);
                        } else {
                            frame.resetSize();
                            userActivity();
                        }
                    }
                    if (frame.isSmall()) {
                        frame.setFrameHeight(frame.getSmallFrameHeight());
                    }
                    if (scrollState != 3) {
                        frame.hideFrame(this);
                    }
                    updateEdgeSwiping();
                    if (this.mChallengeLayout.isChallengeShowing()) {
                        this.mKeyguardSecurityContainer.onResume(2);
                    } else {
                        this.mKeyguardSecurityContainer.onPause();
                    }
                    this.mPageListeningToSlider = -1;
                } else {
                    return;
                }
            } else if (this.mLastScrollState == 0) {
                this.mPageListeningToSlider = this.mKeyguardWidgetPager.getNextPage();
                frame = this.mKeyguardWidgetPager.getWidgetPageAt(this.mPageListeningToSlider);
                if (frame != null) {
                    if (!this.mChallengeLayout.isBouncing()) {
                        if (scrollState != 3) {
                            frame.showFrame(this);
                        }
                        if (!frame.isSmall()) {
                            this.mPageListeningToSlider = this.mKeyguardWidgetPager.getNextPage();
                            frame.shrinkWidget(SHOW_INITIAL_PAGE_HINTS);
                        }
                    } else if (!frame.isSmall()) {
                        this.mPageListeningToSlider = this.mKeyguardWidgetPager.getNextPage();
                    }
                    this.mKeyguardSecurityContainer.onPause();
                } else {
                    return;
                }
            }
            this.mLastScrollState = scrollState;
        }
    }

    public void onScrollPositionChanged(float scrollPosition, int challengeTop) {
        this.mChallengeTop = challengeTop;
        KeyguardWidgetFrame frame = this.mKeyguardWidgetPager.getWidgetPageAt(this.mPageListeningToSlider);
        if (frame != null && this.mLastScrollState != 3) {
            frame.adjustFrame(getChallengeTopRelativeToFrame(frame, this.mChallengeTop));
        }
    }

    public void showUsabilityHints() {
        this.mMainQueue.postDelayed(new Runnable() {
            public void run() {
                KeyguardViewStateManager.this.mKeyguardSecurityContainer.showUsabilityHint();
            }
        }, 300);
        if (this.mHideHintsRunnable != null) {
            this.mMainQueue.postDelayed(this.mHideHintsRunnable, 1000);
        }
    }

    public void onBouncerStateChanged(boolean bouncerActive) {
        if (bouncerActive) {
            if (this.mKeyguardHostView != null) {
                this.mKeyguardWidgetPager.zoomOutToBouncer();
            }
        } else if (this.mKeyguardHostView != null) {
            this.mKeyguardWidgetPager.zoomInFromBouncer();
            this.mKeyguardHostView.setOnDismissAction(null);
        } else if (this.mKeyguardHostViewMod != null) {
            this.mKeyguardHostViewMod.setOnDismissAction(null);
        }
    }
}
