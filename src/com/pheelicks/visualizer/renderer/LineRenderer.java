package com.pheelicks.visualizer.renderer;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import com.pheelicks.visualizer.AudioData;
import com.pheelicks.visualizer.FFTData;

public class LineRenderer extends Renderer {
    private float amplitude;
    private float colorCounter;
    private boolean mCycleColor;
    private Paint mFlashPaint;
    private Paint mPaint;

    public LineRenderer(Paint paint, Paint flashPaint) {
        this(paint, flashPaint, false);
    }

    public LineRenderer(Paint paint, Paint flashPaint, boolean cycleColor) {
        this.amplitude = 0.0f;
        this.colorCounter = 0.0f;
        this.mPaint = paint;
        this.mFlashPaint = flashPaint;
        this.mCycleColor = cycleColor;
    }

    public void onRender(Canvas canvas, AudioData data, Rect rect) {
        int i;
        if (this.mCycleColor) {
            cycleColor();
        }
        for (i = 0; i < data.bytes.length - 1; i++) {
            this.mPoints[i * 4] = (float) ((rect.width() * i) / (data.bytes.length - 1));
            this.mPoints[(i * 4) + 1] = (float) ((rect.height() / 2) + ((((byte) (data.bytes[i] + 128)) * (rect.height() / 3)) / 128));
            this.mPoints[(i * 4) + 2] = (float) ((rect.width() * (i + 1)) / (data.bytes.length - 1));
            this.mPoints[(i * 4) + 3] = (float) ((rect.height() / 2) + ((((byte) (data.bytes[i + 1] + 128)) * (rect.height() / 3)) / 128));
        }
        float accumulator = 0.0f;
        for (i = 0; i < data.bytes.length - 1; i++) {
            accumulator += (float) Math.abs(data.bytes[i]);
        }
        float amp = accumulator / ((float) (data.bytes.length * 128));
        if (amp > this.amplitude) {
            this.amplitude = amp;
            canvas.drawLines(this.mPoints, this.mFlashPaint);
            return;
        }
        this.amplitude = (float) (((double) this.amplitude) * 0.99d);
        canvas.drawLines(this.mPoints, this.mPaint);
    }

    public void onRender(Canvas canvas, FFTData data, Rect rect) {
    }

    private void cycleColor() {
        this.mPaint.setColor(Color.argb(128, (int) Math.floor((Math.sin((double) this.colorCounter) + 3.0d) * 128.0d), (int) Math.floor((Math.sin((double) (this.colorCounter + 1.0f)) + 1.0d) * 128.0d), (int) Math.floor((Math.sin((double) (this.colorCounter + 7.0f)) + 1.0d) * 128.0d)));
        this.colorCounter = (float) (((double) this.colorCounter) + 0.03d);
    }
}
