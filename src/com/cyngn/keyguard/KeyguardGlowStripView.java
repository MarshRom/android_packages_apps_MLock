package com.cyngn.keyguard;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.widget.LinearLayout;

public class KeyguardGlowStripView extends LinearLayout {
    private static final int DURATION = 500;
    private static final float SLIDING_WINDOW_SIZE = 0.4f;
    private float mAnimationProgress;
    private ValueAnimator mAnimator;
    private Interpolator mDotAlphaInterpolator;
    private Drawable mDotDrawable;
    private int mDotSize;
    private int mDotStripTop;
    private boolean mDrawDots;
    private int mHorizontalDotGap;
    private boolean mLeftToRight;
    private int mNumDots;

    public KeyguardGlowStripView(Context context) {
        this(context, null, 0);
    }

    public KeyguardGlowStripView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public KeyguardGlowStripView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        this.mLeftToRight = true;
        this.mAnimationProgress = 0.0f;
        this.mDrawDots = false;
        this.mDotAlphaInterpolator = new DecelerateInterpolator(0.5f);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.KeyguardGlowStripView);
        this.mDotSize = a.getDimensionPixelSize(0, this.mDotSize);
        this.mNumDots = a.getInt(1, this.mNumDots);
        this.mDotDrawable = a.getDrawable(2);
        this.mLeftToRight = a.getBoolean(3, this.mLeftToRight);
    }

    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        this.mHorizontalDotGap = (((w - getPaddingLeft()) - getPaddingRight()) - (this.mDotSize * this.mNumDots)) / (this.mNumDots - 1);
        this.mDotStripTop = getPaddingTop();
        invalidate();
    }

    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (this.mDrawDots) {
            int xOffset = getPaddingLeft();
            this.mDotDrawable.setBounds(0, 0, this.mDotSize, this.mDotSize);
            for (int i = 0; i < this.mNumDots; i++) {
                float alpha = this.mDotAlphaInterpolator.getInterpolation(Math.max(0.0f, 1.0f - (Math.abs((0.2f + (((((float) i) * 1.0f) / ((float) (this.mNumDots - 1))) * 0.6f)) - this.mAnimationProgress) / 0.2f)));
                canvas.save();
                canvas.translate((float) xOffset, (float) this.mDotStripTop);
                this.mDotDrawable.setAlpha((int) (255.0f * alpha));
                this.mDotDrawable.draw(canvas);
                canvas.restore();
                xOffset += this.mDotSize + this.mHorizontalDotGap;
            }
        }
    }

    public void makeEmGo() {
        float from;
        float to;
        if (this.mAnimator != null) {
            this.mAnimator.cancel();
        }
        if (this.mLeftToRight) {
            from = 0.0f;
        } else {
            from = 1.0f;
        }
        if (this.mLeftToRight) {
            to = 1.0f;
        } else {
            to = 0.0f;
        }
        this.mAnimator = ValueAnimator.ofFloat(new float[]{from, to});
        this.mAnimator.setDuration(500);
        this.mAnimator.setInterpolator(new LinearInterpolator());
        this.mAnimator.addListener(new AnimatorListenerAdapter() {
            public void onAnimationEnd(Animator animation) {
                KeyguardGlowStripView.this.mDrawDots = false;
                KeyguardGlowStripView.this.invalidate();
            }

            public void onAnimationStart(Animator animation) {
                KeyguardGlowStripView.this.mDrawDots = true;
            }
        });
        this.mAnimator.addUpdateListener(new AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator animation) {
                KeyguardGlowStripView.this.mAnimationProgress = ((Float) animation.getAnimatedValue()).floatValue();
                KeyguardGlowStripView.this.invalidate();
            }
        });
        this.mAnimator.start();
    }
}
