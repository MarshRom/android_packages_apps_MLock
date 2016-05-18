package com.pheelicks.visualizer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.media.audiofx.Visualizer;
import android.media.audiofx.Visualizer.OnDataCaptureListener;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import com.cyngn.keyguard.KeyguardViewManager;
import com.pheelicks.visualizer.renderer.BarGraphRenderer;
import com.pheelicks.visualizer.renderer.CircleBarRenderer;
import com.pheelicks.visualizer.renderer.CircleRenderer;
import com.pheelicks.visualizer.renderer.LineRenderer;
import com.pheelicks.visualizer.renderer.Renderer;
import java.util.HashSet;
import java.util.Set;

public class VisualizerView extends View {
    private static final String TAG = "VisualizerView";
    private int mAudioSessionId;
    private byte[] mBytes;
    Canvas mCanvas;
    Bitmap mCanvasBitmap;
    private byte[] mFFTBytes;
    private Paint mFadePaint;
    boolean mFlash;
    private Paint mFlashPaint;
    private Rect mRect;
    private Set<Renderer> mRenderers;
    private Visualizer mVisualizer;

    public VisualizerView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs);
        this.mRect = new Rect();
        this.mFlashPaint = new Paint();
        this.mFadePaint = new Paint();
        this.mFlash = false;
        init();
    }

    public VisualizerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public VisualizerView(Context context) {
        this(context, null, 0);
    }

    private void init() {
        this.mBytes = null;
        this.mFFTBytes = null;
        this.mFlashPaint.setColor(Color.argb(122, 255, 255, 255));
        this.mFadePaint.setColor(Color.argb(KeyguardViewManager.WALLPAPER_PAPER_OFFSET, 255, 255, 255));
        this.mFadePaint.setXfermode(new PorterDuffXfermode(Mode.MULTIPLY));
        this.mRenderers = new HashSet();
    }

    public void link(int audioSessionId) {
        if (!(this.mVisualizer == null || audioSessionId == this.mAudioSessionId)) {
            this.mVisualizer.setEnabled(false);
            this.mVisualizer.release();
            this.mVisualizer = null;
        }
        Log.i(TAG, "session=" + audioSessionId);
        this.mAudioSessionId = audioSessionId;
        if (this.mVisualizer == null) {
            try {
                this.mVisualizer = new Visualizer(audioSessionId);
                this.mVisualizer.setEnabled(false);
                this.mVisualizer.setCaptureSize(Visualizer.getCaptureSizeRange()[1]);
                this.mVisualizer.setDataCaptureListener(new OnDataCaptureListener() {
                    public void onWaveFormDataCapture(Visualizer visualizer, byte[] bytes, int samplingRate) {
                        VisualizerView.this.updateVisualizer(bytes);
                    }

                    public void onFftDataCapture(Visualizer visualizer, byte[] bytes, int samplingRate) {
                        VisualizerView.this.updateVisualizerFFT(bytes);
                    }
                }, (int) (((double) Visualizer.getMaxCaptureRate()) * 0.75d), true, true);
            } catch (Exception e) {
                Log.e(TAG, "Error enabling visualizer!", e);
                return;
            }
        }
        this.mVisualizer.setEnabled(true);
    }

    public void unlink() {
        if (this.mVisualizer != null) {
            this.mVisualizer.setEnabled(false);
            this.mVisualizer.release();
            this.mVisualizer = null;
        }
    }

    public void addRenderer(Renderer renderer) {
        if (renderer != null) {
            this.mRenderers.add(renderer);
        }
    }

    public void clearRenderers() {
        this.mRenderers.clear();
    }

    public void release() {
        this.mVisualizer.release();
    }

    public void updateVisualizer(byte[] bytes) {
        this.mBytes = bytes;
        invalidate();
    }

    public void updateVisualizerFFT(byte[] bytes) {
        this.mFFTBytes = bytes;
        invalidate();
    }

    public void flash() {
        this.mFlash = true;
        invalidate();
    }

    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        this.mRect.set(0, 0, getWidth(), getHeight());
        if (this.mCanvasBitmap == null) {
            this.mCanvasBitmap = Bitmap.createBitmap(canvas.getWidth(), canvas.getHeight(), Config.ARGB_8888);
        }
        if (this.mCanvas == null) {
            this.mCanvas = new Canvas(this.mCanvasBitmap);
        }
        if (this.mBytes != null) {
            AudioData audioData = new AudioData(this.mBytes);
            for (Renderer r : this.mRenderers) {
                r.render(this.mCanvas, audioData, this.mRect);
            }
        }
        if (this.mFFTBytes != null) {
            FFTData fftData = new FFTData(this.mFFTBytes);
            for (Renderer r2 : this.mRenderers) {
                r2.render(this.mCanvas, fftData, this.mRect);
            }
        }
        this.mCanvas.drawPaint(this.mFadePaint);
        if (this.mFlash) {
            this.mFlash = false;
            this.mCanvas.drawPaint(this.mFlashPaint);
        }
        canvas.drawBitmap(this.mCanvasBitmap, new Matrix(), null);
    }

    public void addBarGraphRendererBottom() {
        Paint paint = new Paint();
        paint.setStrokeWidth(50.0f);
        paint.setAntiAlias(true);
        paint.setColor(Color.argb(KeyguardViewManager.WALLPAPER_PAPER_OFFSET, 56, 138, 252));
        addRenderer(new BarGraphRenderer(16, paint, false));
    }

    public void addBarGraphRendererTop() {
        Paint paint2 = new Paint();
        paint2.setStrokeWidth(12.0f);
        paint2.setAntiAlias(true);
        paint2.setColor(Color.argb(KeyguardViewManager.WALLPAPER_PAPER_OFFSET, 181, 111, 233));
        addRenderer(new BarGraphRenderer(4, paint2, true));
    }

    public void addCircleBarRenderer() {
        Paint paint = new Paint();
        paint.setStrokeWidth(8.0f);
        paint.setAntiAlias(true);
        paint.setXfermode(new PorterDuffXfermode(Mode.LIGHTEN));
        paint.setColor(Color.argb(255, 222, 92, 143));
        addRenderer(new CircleBarRenderer(paint, 32, true));
    }

    public void addCircleRenderer() {
        Paint paint = new Paint();
        paint.setStrokeWidth(3.0f);
        paint.setAntiAlias(true);
        paint.setColor(Color.argb(255, 222, 92, 143));
        addRenderer(new CircleRenderer(paint, true));
    }

    public void addLineRenderer() {
        Paint linePaint = new Paint();
        linePaint.setStrokeWidth(1.0f);
        linePaint.setAntiAlias(true);
        linePaint.setColor(Color.argb(88, 0, 128, 255));
        Paint lineFlashPaint = new Paint();
        lineFlashPaint.setStrokeWidth(5.0f);
        lineFlashPaint.setAntiAlias(true);
        lineFlashPaint.setColor(Color.argb(188, 255, 255, 255));
        addRenderer(new LineRenderer(linePaint, lineFlashPaint, true));
    }
}
