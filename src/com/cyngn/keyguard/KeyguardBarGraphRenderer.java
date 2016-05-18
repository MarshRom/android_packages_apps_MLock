package com.cyngn.keyguard;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import com.pheelicks.visualizer.AudioData;
import com.pheelicks.visualizer.FFTData;
import com.pheelicks.visualizer.renderer.Renderer;

public class KeyguardBarGraphRenderer extends Renderer {
    private int mDivisions;
    private Paint mPaint;
    private boolean mUp;

    public KeyguardBarGraphRenderer(int divisions, Paint paint, boolean up) {
        this.mDivisions = divisions;
        this.mPaint = paint;
        this.mUp = up;
    }

    public void onRender(Canvas canvas, AudioData data, Rect rect) {
    }

    public void onRender(Canvas canvas, FFTData data, Rect rect) {
        for (int i = 0; i < data.bytes.length / this.mDivisions; i++) {
            this.mFFTPoints[i * 4] = (float) ((i * 4) * this.mDivisions);
            this.mFFTPoints[(i * 4) + 2] = (float) ((i * 4) * this.mDivisions);
            byte rfk = data.bytes[this.mDivisions * i];
            byte ifk = data.bytes[(this.mDivisions * i) + 1];
            int dbValue = (int) (10.0d * Math.log10((double) ((float) ((rfk * rfk) + (ifk * ifk)))));
            this.mFFTPoints[(i * 4) + 1] = (float) rect.height();
            this.mFFTPoints[(i * 4) + 3] = (float) (rect.height() - (dbValue * 35));
        }
        canvas.drawLines(this.mFFTPoints, this.mPaint);
    }
}
