package com.pheelicks.visualizer.renderer;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import com.pheelicks.visualizer.AudioData;
import com.pheelicks.visualizer.FFTData;

public class CircleRenderer extends Renderer {
    float aggresive;
    private float colorCounter;
    private boolean mCycleColor;
    private Paint mPaint;
    float modulation;

    public CircleRenderer(Paint paint) {
        this(paint, false);
    }

    public CircleRenderer(Paint paint, boolean cycleColor) {
        this.modulation = 0.0f;
        this.aggresive = 0.33f;
        this.colorCounter = 0.0f;
        this.mPaint = paint;
        this.mCycleColor = cycleColor;
    }

    public void onRender(Canvas canvas, AudioData data, Rect rect) {
        if (this.mCycleColor) {
            cycleColor();
        }
        for (int i = 0; i < data.bytes.length - 1; i++) {
            float[] polarPoint = toPolar(new float[]{((float) i) / ((float) (data.bytes.length - 1)), (float) ((rect.height() / 2) + ((((byte) (data.bytes[i] + 128)) * (rect.height() / 2)) / 128))}, rect);
            this.mPoints[i * 4] = polarPoint[0];
            this.mPoints[(i * 4) + 1] = polarPoint[1];
            float[] polarPoint2 = toPolar(new float[]{((float) (i + 1)) / ((float) (data.bytes.length - 1)), (float) ((rect.height() / 2) + ((((byte) (data.bytes[i + 1] + 128)) * (rect.height() / 2)) / 128))}, rect);
            this.mPoints[(i * 4) + 2] = polarPoint2[0];
            this.mPoints[(i * 4) + 3] = polarPoint2[1];
        }
        canvas.drawLines(this.mPoints, this.mPaint);
        this.modulation = (float) (((double) this.modulation) + 0.04d);
    }

    public void onRender(Canvas canvas, FFTData data, Rect rect) {
    }

    private float[] toPolar(float[] cartesian, Rect rect) {
        double cX = (double) (rect.width() / 2);
        double cY = (double) (rect.height() / 2);
        double angle = ((double) (cartesian[0] * 2.0f)) * 3.141592653589793d;
        double radius = (((double) ((((float) (rect.width() / 2)) * (1.0f - this.aggresive)) + ((this.aggresive * cartesian[1]) / 2.0f))) * (1.2d + Math.sin((double) this.modulation))) / 2.2d;
        return new float[]{(float) ((Math.sin(angle) * radius) + cX), (float) ((Math.cos(angle) * radius) + cY)};
    }

    private void cycleColor() {
        this.mPaint.setColor(Color.argb(128, (int) Math.floor((Math.sin((double) this.colorCounter) + 1.0d) * 128.0d), (int) Math.floor((Math.sin((double) (this.colorCounter + 2.0f)) + 1.0d) * 128.0d), (int) Math.floor((Math.sin((double) (this.colorCounter + 4.0f)) + 1.0d) * 128.0d)));
        this.colorCounter = (float) (((double) this.colorCounter) + 0.03d);
    }
}
