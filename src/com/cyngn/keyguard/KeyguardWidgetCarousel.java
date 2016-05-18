package com.cyngn.keyguard;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import java.util.ArrayList;

public class KeyguardWidgetCarousel extends KeyguardWidgetPager {
    private static float CAMERA_DISTANCE = 10000.0f;
    private static float MAX_SCROLL_PROGRESS = 1.3f;
    private float mAdjacentPagesAngle;
    protected AnimatorSet mChildrenTransformsAnimator;
    Interpolator mFastFadeInterpolator;
    Interpolator mSlowFadeInterpolator;
    float[] mTmpTransform;

    public KeyguardWidgetCarousel(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public KeyguardWidgetCarousel(Context context) {
        this(context, null, 0);
    }

    public KeyguardWidgetCarousel(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        this.mTmpTransform = new float[3];
        this.mFastFadeInterpolator = new Interpolator() {
            float mFactor = 2.5f;
            Interpolator mInternal = new DecelerateInterpolator(1.5f);

            public float getInterpolation(float input) {
                return this.mInternal.getInterpolation(Math.min(this.mFactor * input, 1.0f));
            }
        };
        this.mSlowFadeInterpolator = new Interpolator() {
            float mFactor = 1.3f;
            Interpolator mInternal = new AccelerateInterpolator(1.5f);

            public float getInterpolation(float input) {
                return this.mInternal.getInterpolation(this.mFactor * Math.max(input - (1.0f - (1.0f / this.mFactor)), 0.0f));
            }
        };
        this.mAdjacentPagesAngle = (float) context.getResources().getInteger(R.integer.kg_carousel_angle);
    }

    protected float getMaxScrollProgress() {
        return MAX_SCROLL_PROGRESS;
    }

    public float getAlphaForPage(int screenCenter, int index, boolean showSidePages) {
        View child = getChildAt(index);
        if (child == null) {
            return 0.0f;
        }
        boolean inVisibleRange = index >= getNextPage() + -1 && index <= getNextPage() + 1;
        if (isOverScrollChild(index, getScrollProgress(screenCenter, child, index))) {
            return 1.0f;
        }
        if ((showSidePages && inVisibleRange) || index == getNextPage()) {
            return 1.0f - (Math.abs(getBoundedScrollProgress(screenCenter, child, index) / MAX_SCROLL_PROGRESS) * 1.0f);
        }
        return 0.0f;
    }

    public float getOutlineAlphaForPage(int screenCenter, int index, boolean showSidePages) {
        boolean inVisibleRange = index >= getNextPage() + -1 && index <= getNextPage() + 1;
        if (inVisibleRange) {
            return super.getOutlineAlphaForPage(screenCenter, index, showSidePages);
        }
        return 0.0f;
    }

    private void updatePageAlphaValues(int screenCenter) {
        boolean showSidePages;
        if (this.mChildrenOutlineFadeAnimation != null) {
            this.mChildrenOutlineFadeAnimation.cancel();
            this.mChildrenOutlineFadeAnimation = null;
        }
        if (this.mShowingInitialHints || isPageMoving()) {
            showSidePages = true;
        } else {
            showSidePages = false;
        }
        if (!isReordering(false)) {
            for (int i = 0; i < getChildCount(); i++) {
                KeyguardWidgetFrame child = getWidgetPageAt(i);
                if (child != null) {
                    float outlineAlpha = getOutlineAlphaForPage(screenCenter, i, showSidePages);
                    float contentAlpha = getAlphaForPage(screenCenter, i, showSidePages);
                    child.setBackgroundAlpha(outlineAlpha);
                    child.setContentAlpha(contentAlpha);
                }
            }
        }
    }

    public void showInitialPageHints() {
        this.mShowingInitialHints = true;
        int count = getChildCount();
        int i = 0;
        while (i < count) {
            boolean inVisibleRange = i >= getNextPage() + -1 && i <= getNextPage() + 1;
            KeyguardWidgetFrame child = getWidgetPageAt(i);
            if (inVisibleRange) {
                child.setBackgroundAlpha(0.6f);
                child.setContentAlpha(1.0f);
            } else {
                child.setBackgroundAlpha(0.0f);
                child.setContentAlpha(0.0f);
            }
            i++;
        }
    }

    protected void screenScrolled(int screenCenter) {
        this.mScreenCenter = screenCenter;
        updatePageAlphaValues(screenCenter);
        if (!isReordering(false)) {
            for (int i = 0; i < getChildCount(); i++) {
                View v = getWidgetPageAt(i);
                float scrollProgress = getScrollProgress(screenCenter, v, i);
                float boundedProgress = getBoundedScrollProgress(screenCenter, v, i);
                if (!(v == this.mDragView || v == null)) {
                    v.setCameraDistance(CAMERA_DISTANCE);
                    if (isOverScrollChild(i, scrollProgress)) {
                        boolean z;
                        v.setRotationY((-OVERSCROLL_MAX_ROTATION) * scrollProgress);
                        float abs = Math.abs(scrollProgress);
                        if (scrollProgress < 0.0f) {
                            z = true;
                        } else {
                            z = false;
                        }
                        v.setOverScrollAmount(abs, z);
                    } else {
                        int width = v.getMeasuredWidth();
                        float pivotY = (float) (v.getMeasuredHeight() / 2);
                        float rotationY = (-this.mAdjacentPagesAngle) * boundedProgress;
                        v.setPivotX((((float) width) / 2.0f) + ((((float) width) / 2.0f) * boundedProgress));
                        v.setPivotY(pivotY);
                        v.setRotationY(rotationY);
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
    }

    void animatePagesToNeutral() {
        if (this.mChildrenTransformsAnimator != null) {
            this.mChildrenTransformsAnimator.cancel();
            this.mChildrenTransformsAnimator = null;
        }
        int count = getChildCount();
        ArrayList<Animator> anims = new ArrayList();
        int i = 0;
        while (i < count) {
            KeyguardWidgetFrame child = getWidgetPageAt(i);
            boolean inVisibleRange = i >= this.mCurrentPage + -1 && i <= this.mCurrentPage + 1;
            if (!inVisibleRange) {
                child.setRotationY(0.0f);
            }
            PropertyValuesHolder alpha = PropertyValuesHolder.ofFloat("contentAlpha", new float[]{1.0f});
            PropertyValuesHolder outlineAlpha = PropertyValuesHolder.ofFloat("backgroundAlpha", new float[]{0.6f});
            PropertyValuesHolder rotationY = PropertyValuesHolder.ofFloat("rotationY", new float[]{0.0f});
            ObjectAnimator a = ObjectAnimator.ofPropertyValuesHolder(child, new PropertyValuesHolder[]{alpha, outlineAlpha, rotationY});
            child.setVisibility(0);
            if (!inVisibleRange) {
                a.setInterpolator(this.mSlowFadeInterpolator);
            }
            anims.add(a);
            i++;
        }
        int duration = this.REORDERING_ZOOM_IN_OUT_DURATION;
        this.mChildrenTransformsAnimator = new AnimatorSet();
        this.mChildrenTransformsAnimator.playTogether(anims);
        this.mChildrenTransformsAnimator.setDuration((long) duration);
        this.mChildrenTransformsAnimator.start();
    }

    private void getTransformForPage(int screenCenter, int index, float[] transform) {
        View child = getChildAt(index);
        float boundedProgress = getBoundedScrollProgress(screenCenter, child, index);
        float rotationY = (-this.mAdjacentPagesAngle) * boundedProgress;
        int width = child.getMeasuredWidth();
        float pivotY = (float) (child.getMeasuredHeight() / 2);
        transform[0] = (((float) width) / 2.0f) + ((((float) width) / 2.0f) * boundedProgress);
        transform[1] = pivotY;
        transform[2] = rotationY;
    }

    void animatePagesToCarousel() {
        if (this.mChildrenTransformsAnimator != null) {
            this.mChildrenTransformsAnimator.cancel();
            this.mChildrenTransformsAnimator = null;
        }
        int count = getChildCount();
        ArrayList<Animator> anims = new ArrayList();
        int i = 0;
        while (i < count) {
            ObjectAnimator a;
            KeyguardWidgetFrame child = getWidgetPageAt(i);
            float finalAlpha = getAlphaForPage(this.mScreenCenter, i, true);
            float finalOutlineAlpha = getOutlineAlphaForPage(this.mScreenCenter, i, true);
            getTransformForPage(this.mScreenCenter, i, this.mTmpTransform);
            boolean inVisibleRange = i >= this.mCurrentPage + -1 && i <= this.mCurrentPage + 1;
            PropertyValuesHolder alpha = PropertyValuesHolder.ofFloat("contentAlpha", new float[]{finalAlpha});
            PropertyValuesHolder outlineAlpha = PropertyValuesHolder.ofFloat("backgroundAlpha", new float[]{finalOutlineAlpha});
            PropertyValuesHolder pivotX = PropertyValuesHolder.ofFloat("pivotX", new float[]{this.mTmpTransform[0]});
            PropertyValuesHolder pivotY = PropertyValuesHolder.ofFloat("pivotY", new float[]{this.mTmpTransform[1]});
            PropertyValuesHolder rotationY = PropertyValuesHolder.ofFloat("rotationY", new float[]{this.mTmpTransform[2]});
            if (inVisibleRange) {
                a = ObjectAnimator.ofPropertyValuesHolder(child, new PropertyValuesHolder[]{alpha, outlineAlpha, pivotX, pivotY, rotationY});
            } else {
                a = ObjectAnimator.ofPropertyValuesHolder(child, new PropertyValuesHolder[]{alpha, outlineAlpha});
                a.setInterpolator(this.mFastFadeInterpolator);
            }
            anims.add(a);
            i++;
        }
        int duration = this.REORDERING_ZOOM_IN_OUT_DURATION;
        this.mChildrenTransformsAnimator = new AnimatorSet();
        this.mChildrenTransformsAnimator.playTogether(anims);
        this.mChildrenTransformsAnimator.setDuration((long) duration);
        this.mChildrenTransformsAnimator.start();
    }

    protected void reorderStarting() {
        this.mViewStateManager.fadeOutSecurity(this.REORDERING_ZOOM_IN_OUT_DURATION);
        animatePagesToNeutral();
    }

    protected boolean zoomIn(Runnable onCompleteRunnable) {
        animatePagesToCarousel();
        return super.zoomIn(onCompleteRunnable);
    }

    protected void onEndReordering() {
        super.onEndReordering();
        this.mViewStateManager.fadeInSecurity(this.REORDERING_ZOOM_IN_OUT_DURATION);
    }
}
