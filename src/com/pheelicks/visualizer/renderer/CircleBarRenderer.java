package com.pheelicks.visualizer.renderer;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import com.pheelicks.visualizer.AudioData;
import com.pheelicks.visualizer.FFTData;

public class CircleBarRenderer extends Renderer {
    float aggresive;
    float angleModulation;
    private float colorCounter;
    private boolean mCycleColor;
    private int mDivisions;
    private Paint mPaint;
    float modulation;
    float modulationStrength;

    public CircleBarRenderer(Paint paint, int divisions) {
        this(paint, divisions, false);
    }

    public CircleBarRenderer(Paint paint, int divisions, boolean cycleColor) {
        this.modulation = 0.0f;
        this.modulationStrength = 0.4f;
        this.angleModulation = 0.0f;
        this.aggresive = 0.4f;
        this.colorCounter = 0.0f;
        this.mPaint = paint;
        this.mDivisions = divisions;
        this.mCycleColor = cycleColor;
    }

    public void onRender(Canvas canvas, AudioData data, Rect rect) {
    }

    public void onRender(Canvas canvas, FFTData data, Rect rect) {
        if (this.mCycleColor) {
            cycleColor();
        }
        for (int i = 0; i < data.bytes.length / this.mDivisions; i++) {
            byte rfk = data.bytes[this.mDivisions * i];
            byte ifk = data.bytes[(this.mDivisions * i) + 1];
            float dbValue = 75.0f * ((float) Math.log10((double) ((float) ((rfk * rfk) + (ifk * ifk)))));
            float[] polarPoint = toPolar(new float[]{((float) (this.mDivisions * i)) / ((float) (data.bytes.length - 1)), ((float) (rect.height() / 2)) - (dbValue / 4.0f)}, rect);
            this.mFFTPoints[i * 4] = polarPoint[0];
            this.mFFTPoints[(i * 4) + 1] = polarPoint[1];
            float[] polarPoint2 = toPolar(new float[]{((float) (this.mDivisions * i)) / ((float) (data.bytes.length - 1)), ((float) (rect.height() / 2)) + dbValue}, rect);
            this.mFFTPoints[(i * 4) + 2] = polarPoint2[0];
            this.mFFTPoints[(i * 4) + 3] = polarPoint2[1];
        }
        canvas.drawLines(this.mFFTPoints, this.mPaint);
        this.modulation = (float) (((double) this.modulation) + 0.13d);
        this.angleModulation = (float) (((double) this.angleModulation) + 0.28d);
    }

    private float[] toPolar(float[] cartesian, Rect rect) {
        double cX = (double) (rect.width() / 2);
        double cY = (double) (rect.height() / 2);
        double angle = ((double) (cartesian[0] * 2.0f)) * 3.141592653589793d;
        double radius = ((double) ((((float) (rect.width() / 2)) * (1.0f - this.aggresive)) + ((this.aggresive * cartesian[1]) / 2.0f))) * (((double) (1.0f - this.modulationStrength)) + ((((double) this.modulationStrength) * (1.0d + Math.sin((double) this.modulation))) / 2.0d));
        return new float[]{(float) ((Math.sin(((double) this.angleModulation) + angle) * radius) + cX), (float) ((Math.cos(((double) this.angleModulation) + angle) * radius) + cY)};
    }

    private void cycleColor() {
        this.mPaint.setColor(Color.argb(128, (int) Math.floor((Math.sin((double) this.colorCounter) + 1.0d) * 128.0d), (int) Math.floor((Math.sin((double) (this.colorCounter + 2.0f)) + 1.0d) * 128.0d), (int) Math.floor((Math.sin((double) (this.colorCounter + 4.0f)) + 1.0d) * 128.0d)));
        this.colorCounter = (float) (((double) this.colorCounter) + 0.03d);
    }
}
