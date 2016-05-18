package com.cyngn.keyguard;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

class KeyguardCircleFramedDrawable extends Drawable {
    private final Bitmap mBitmap = Bitmap.createBitmap(this.mSize, this.mSize, Config.ARGB_8888);
    private RectF mDstRect;
    private final int mFrameColor;
    private Path mFramePath;
    private RectF mFrameRect;
    private final int mFrameShadowColor;
    private final int mHighlightColor;
    private final Paint mPaint;
    private boolean mPressed;
    private float mScale;
    private final float mShadowRadius;
    private final int mSize;
    private Rect mSrcRect;
    private final float mStrokeWidth;

    public KeyguardCircleFramedDrawable(Bitmap bitmap, int size, int frameColor, float strokeWidth, int frameShadowColor, float shadowRadius, int highlightColor) {
        this.mSize = size;
        this.mShadowRadius = shadowRadius;
        this.mFrameColor = frameColor;
        this.mFrameShadowColor = frameShadowColor;
        this.mStrokeWidth = strokeWidth;
        this.mHighlightColor = highlightColor;
        Canvas canvas = new Canvas(this.mBitmap);
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int square = Math.min(width, height);
        Rect cropRect = new Rect((width - square) / 2, (height - square) / 2, square, square);
        RectF circleRect = new RectF(0.0f, 0.0f, (float) this.mSize, (float) this.mSize);
        circleRect.inset(this.mStrokeWidth / 2.0f, this.mStrokeWidth / 2.0f);
        circleRect.inset(this.mShadowRadius, this.mShadowRadius);
        Path fillPath = new Path();
        fillPath.addArc(circleRect, 0.0f, 360.0f);
        canvas.drawColor(0, Mode.CLEAR);
        this.mPaint = new Paint();
        this.mPaint.setAntiAlias(true);
        this.mPaint.setColor(-16777216);
        this.mPaint.setStyle(Style.FILL);
        canvas.drawPath(fillPath, this.mPaint);
        this.mPaint.setXfermode(new PorterDuffXfermode(Mode.SRC_ATOP));
        canvas.drawBitmap(bitmap, cropRect, circleRect, this.mPaint);
        this.mPaint.setXfermode(null);
        this.mScale = 1.0f;
        this.mSrcRect = new Rect(0, 0, this.mSize, this.mSize);
        this.mDstRect = new RectF(0.0f, 0.0f, (float) this.mSize, (float) this.mSize);
        this.mFrameRect = new RectF(this.mDstRect);
        this.mFramePath = new Path();
    }

    public void reset() {
        this.mScale = 1.0f;
        this.mPressed = false;
    }

    public void draw(Canvas canvas) {
        float outside = (float) Math.min(canvas.getWidth(), canvas.getHeight());
        float pad = (outside - (this.mScale * outside)) / 2.0f;
        this.mDstRect.set(pad, pad, outside - pad, outside - pad);
        canvas.drawBitmap(this.mBitmap, this.mSrcRect, this.mDstRect, null);
        this.mFrameRect.set(this.mDstRect);
        this.mFrameRect.inset(this.mStrokeWidth / 2.0f, this.mStrokeWidth / 2.0f);
        this.mFrameRect.inset(this.mShadowRadius, this.mShadowRadius);
        this.mFramePath.reset();
        this.mFramePath.addArc(this.mFrameRect, 0.0f, 360.0f);
        if (this.mPressed) {
            this.mPaint.setStyle(Style.FILL);
            this.mPaint.setColor(Color.argb(84, Color.red(this.mHighlightColor), Color.green(this.mHighlightColor), Color.blue(this.mHighlightColor)));
            canvas.drawPath(this.mFramePath, this.mPaint);
        }
        this.mPaint.setStrokeWidth(this.mStrokeWidth);
        this.mPaint.setStyle(Style.STROKE);
        this.mPaint.setColor(this.mPressed ? this.mHighlightColor : this.mFrameColor);
        this.mPaint.setShadowLayer(this.mShadowRadius, 0.0f, 0.0f, this.mFrameShadowColor);
        canvas.drawPath(this.mFramePath, this.mPaint);
    }

    public void setScale(float scale) {
        this.mScale = scale;
    }

    public float getScale() {
        return this.mScale;
    }

    public void setPressed(boolean pressed) {
        this.mPressed = pressed;
    }

    public int getOpacity() {
        return -3;
    }

    public void setAlpha(int alpha) {
    }

    public void setColorFilter(ColorFilter cf) {
    }

    public boolean verifyParams(float iconSize, int frameColor, float stroke, int frameShadowColor, float shadowRadius, int highlightColor) {
        return ((float) this.mSize) == iconSize && this.mFrameColor == frameColor && this.mStrokeWidth == stroke && this.mFrameShadowColor == frameShadowColor && this.mShadowRadius == shadowRadius && this.mHighlightColor == highlightColor;
    }
}
