package com.pheelicks.visualizer.renderer;

import android.graphics.Canvas;
import android.graphics.Rect;
import com.pheelicks.visualizer.AudioData;
import com.pheelicks.visualizer.FFTData;

public abstract class Renderer {
    protected float[] mFFTPoints;
    protected float[] mPoints;

    public abstract void onRender(Canvas canvas, AudioData audioData, Rect rect);

    public abstract void onRender(Canvas canvas, FFTData fFTData, Rect rect);

    public final void render(Canvas canvas, AudioData data, Rect rect) {
        if (this.mPoints == null || this.mPoints.length < data.bytes.length * 4) {
            this.mPoints = new float[(data.bytes.length * 4)];
        }
        onRender(canvas, data, rect);
    }

    public final void render(Canvas canvas, FFTData data, Rect rect) {
        if (this.mFFTPoints == null || this.mFFTPoints.length < data.bytes.length * 4) {
            this.mFFTPoints = new float[(data.bytes.length * 4)];
        }
        onRender(canvas, data, rect);
    }
}
