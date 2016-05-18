package com.cyngn.keyguard;

import android.app.ActivityManager;
import android.app.ActivityManager.RecentTaskInfo;
import android.app.WallpaperManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.PorterDuff.Mode;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.AsyncTask.Status;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Parcelable;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.Vibrator;
import android.provider.Settings.System;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.support.v7.graphics.Palette;
import android.support.v7.graphics.Palette.PaletteAsyncListener;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Pair;
import android.util.Slog;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewManager;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import com.android.internal.policy.IKeyguardShowCallback;
import com.android.internal.widget.LockPatternUtils;
import com.cyngn.keyguard.KeyguardViewMediator.ViewMediatorCallback;

public class KeyguardViewManager {
    private static final boolean DEBUG = false;
    static final int DIGIT_PRESS_WAKE_MILLIS = 5000;
    private static final int HIDE_KEYGUARD_DELAY = 500;
    public static final String IS_SWITCHING_USER = "is_switching_user";
    private static final int RESET_THRESHOLD = 1000;
    private static final int SET_NULL_BACKGROUND_HANDLER_DELAY = 200;
    private static String TAG = "KeyguardViewManager";
    public static final int WALLPAPER_PAPER_OFFSET = 200;
    private BlurTask mBlurTask;
    private Runnable mCleanupKeyguardAction;
    private final Context mContext;
    private KeyguardSmartCoverView mCoverView;
    private boolean mFirstBlur = true;
    private Handler mHandler = new Handler();
    private boolean mIsModLockEnabled;
    private boolean mIsMusicPlaying = DEBUG;
    private Palette mKeyguardAlbumArtPalette = null;
    private ViewManagerHost mKeyguardHost;
    private Palette mKeyguardPalette = null;
    private KeyguardHostView mKeyguardView;
    private KeyguardHostViewMod mKeyguardViewMod;
    private int mLastXOffset;
    private int mLastYOffset;
    private int mLidState = -1;
    private LockPatternUtils mLockPatternUtils;
    private Runnable mMaybeBlurAgainRunnable = new Runnable() {
        public void run() {
            if (!KeyguardViewManager.this.mIsModLockEnabled) {
                return;
            }
            if ((KeyguardViewManager.this.mKeyguardHost == null || KeyguardViewManager.this.mKeyguardHost.mOrientation != 2) && !KeyguardViewManager.this.mIsMusicPlaying) {
                if (KeyguardViewManager.this.mModBlurBackground != null) {
                    int xOffset = WallpaperManager.getInstance(KeyguardViewManager.this.mContext).getLastWallpaperX();
                    int yOffset = WallpaperManager.getInstance(KeyguardViewManager.this.mContext).getLastWallpaperY();
                    if (xOffset == KeyguardViewManager.this.mLastXOffset && yOffset == KeyguardViewManager.this.mLastYOffset) {
                        return;
                    }
                }
                Drawable d = null;
                if (KeyguardViewManager.this.mUserBackground != null) {
                    d = KeyguardViewManager.this.mUserBackground;
                }
                KeyguardViewManager.this.requestBlurTask(d);
            }
        }
    };
    private Drawable mModBackground;
    private Drawable mModBlurBackground;
    private boolean mNeedsInput = DEBUG;
    private int mPhoneState;
    private boolean mScreenOn = DEBUG;
    private Runnable mSetNullBackground = new Runnable() {
        public void run() {
            KeyguardViewManager.this.setMusicPlaying(KeyguardViewManager.DEBUG);
            if (KeyguardViewManager.this.mKeyguardHost != null) {
                KeyguardViewManager.this.mKeyguardHost.setCustomBackground(null);
            }
        }
    };
    private int[] mSmartCoverCoords;
    private Runnable mSmartCoverTimeout = new Runnable() {
        public void run() {
            KeyguardViewManager.sendToSleep(KeyguardViewManager.this.mContext);
        }
    };
    SparseArray<Parcelable> mStateContainer = new SparseArray();
    private boolean mUnlockKeyDown = DEBUG;
    private KeyguardUpdateMonitorCallback mUpdateMonitorCallback = new KeyguardUpdateMonitorCallback() {
        public void onSetBackground(Bitmap bmp) {
            KeyguardViewManager.this.mModBackground = null;
            KeyguardViewManager.this.mModBlurBackground = null;
            KeyguardViewManager.this.mLastXOffset = -1;
            KeyguardViewManager.this.mLastYOffset = -1;
            if (bmp != null) {
                KeyguardViewManager.this.mHandler.removeCallbacks(KeyguardViewManager.this.mSetNullBackground);
                KeyguardViewManager.this.setMusicPlaying(true);
                KeyguardViewManager.this.mKeyguardHost.setCustomBackground(new BitmapDrawable(KeyguardViewManager.this.mContext.getResources(), bmp));
                Palette.generateAsync(bmp, new PaletteAsyncListener() {
                    public void onGenerated(Palette palette) {
                        KeyguardViewManager.this.mKeyguardAlbumArtPalette = palette;
                        KeyguardViewManager.this.updatePalette();
                    }
                });
                return;
            }
            KeyguardViewManager.this.setMusicPlaying(KeyguardViewManager.DEBUG);
            if (!KeyguardViewManager.this.mHandler.hasCallbacks(KeyguardViewManager.this.mSetNullBackground)) {
                KeyguardViewManager.this.mHandler.post(KeyguardViewManager.this.mSetNullBackground);
            }
            KeyguardViewManager.this.mKeyguardAlbumArtPalette = null;
            KeyguardViewManager.this.updatePalette();
        }

        public void onLidStateChanged(int state) {
            if (KeyguardViewManager.this.mSmartCoverCoords != null) {
                KeyguardViewManager.this.mLidState = state;
                KeyguardViewManager.this.resetSmartCoverState();
            }
        }

        void onPhoneStateChanged(int phoneState) {
            KeyguardViewManager.this.mPhoneState = phoneState;
            KeyguardViewManager.this.resetSmartCoverState();
        }
    };
    private Drawable mUserBackground;
    private final ViewManager mViewManager;
    private final ViewMediatorCallback mViewMediatorCallback;
    private LayoutParams mWindowCoverLayoutParams;
    private LayoutParams mWindowLayoutParams;

    public class BlurTask extends AsyncTask<Drawable, Void, Boolean> {
        long endTaskTime;
        private Drawable mScaledBlurWallpaper;
        private Drawable mScaledWallpaper;
        private Point mScreen = getDefaultDisplaySize();
        long startTaskTime;

        public Bitmap blurBitmap(Bitmap bitmap) {
            if (bitmap.getConfig() != Config.ARGB_8888) {
                bitmap = bitmap.copy(Config.ARGB_8888, KeyguardViewManager.DEBUG);
            }
            Bitmap outBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Config.ARGB_8888);
            RenderScript rs = RenderScript.create(KeyguardViewManager.this.mContext.getApplicationContext());
            ScriptIntrinsicBlur blurScript = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
            Allocation allIn = Allocation.createFromBitmap(rs, bitmap);
            Allocation allOut = Allocation.createFromBitmap(rs, outBitmap);
            blurScript.setRadius(20.0f);
            blurScript.setInput(allIn);
            blurScript.forEach(allOut);
            allOut.copyTo(outBitmap);
            rs.destroy();
            return outBitmap;
        }

        private Bitmap drawableToBitmap(Context context, Drawable drawable) {
            if (context == null || drawable == null) {
                return null;
            }
            if (drawable instanceof BitmapDrawable) {
                return ((BitmapDrawable) drawable).getBitmap();
            }
            int width = drawable.getIntrinsicWidth();
            if (width <= 0) {
                width = 1;
            }
            int height = drawable.getIntrinsicHeight();
            if (height <= 0) {
                height = 1;
            }
            Bitmap bitmap = Bitmap.createBitmap(width, height, Config.ARGB_8888);
            drawable.draw(new Canvas(bitmap));
            return bitmap;
        }

        private Bitmap bitmapToScale(Bitmap bitmap, int width, int height, float scale) {
            int dwidth = bitmap.getWidth();
            int dheight = bitmap.getHeight();
            if (width > dwidth || height > dheight) {
                return bitmap;
            }
            Matrix matrix = new Matrix();
            matrix.postScale(scale, scale);
            return Bitmap.createBitmap(bitmap, (int) ((((float) (dwidth - width)) * 0.5f) + 0.5f), (int) ((((float) (dheight - height)) * 0.5f) + 0.5f), width, height, matrix, true);
        }

        protected void onPreExecute() {
            this.startTaskTime = System.currentTimeMillis();
        }

        private Point getDefaultDisplaySize() {
            Point p = new Point();
            ((WindowManager) KeyguardViewManager.this.mContext.getSystemService("window")).getDefaultDisplay().getRealSize(p);
            return p;
        }

        private Bitmap getRegularWallpaperBitmap() {
            int xOffset;
            int yOffset;
            WallpaperManager wm = WallpaperManager.getInstance(KeyguardViewManager.this.mContext);
            Bitmap wallpaper = wm.getBitmap();
            int dw = this.mScreen.x;
            int dh = this.mScreen.y;
            float scale = Math.max(((float) dw) / ((float) wallpaper.getWidth()), ((float) dh) / ((float) wallpaper.getHeight()));
            int scaledWidth = Math.round(((float) wallpaper.getWidth()) * scale);
            int scaledHeight = Math.round(((float) wallpaper.getHeight()) * scale);
            KeyguardViewManager keyguardViewManager;
            if (KeyguardViewManager.this.mFirstBlur) {
                KeyguardViewManager.this.mFirstBlur = KeyguardViewManager.DEBUG;
                keyguardViewManager = KeyguardViewManager.this;
                xOffset = KeyguardViewManager.this.mContext.getSharedPreferences("wallpaper", 0).getInt("lastX", -1);
                keyguardViewManager.mLastXOffset = xOffset;
                keyguardViewManager = KeyguardViewManager.this;
                yOffset = KeyguardViewManager.this.mContext.getSharedPreferences("wallpaper", 0).getInt("lastY", -1);
                keyguardViewManager.mLastYOffset = yOffset;
            } else {
                keyguardViewManager = KeyguardViewManager.this;
                xOffset = wm.getLastWallpaperX();
                keyguardViewManager.mLastXOffset = xOffset;
                KeyguardViewManager.this.mContext.getSharedPreferences("wallpaper", 0).edit().putInt("lastX", KeyguardViewManager.this.mLastXOffset).apply();
                keyguardViewManager = KeyguardViewManager.this;
                yOffset = wm.getLastWallpaperY();
                keyguardViewManager.mLastYOffset = yOffset;
                KeyguardViewManager.this.mContext.getSharedPreferences("wallpaper", 0).edit().putInt("lastY", KeyguardViewManager.this.mLastYOffset).apply();
            }
            if (xOffset == -1) {
                xOffset = 0;
            } else {
                xOffset *= -1;
            }
            if (yOffset == -1) {
                yOffset = 0;
            } else {
                yOffset *= -1;
            }
            if (scale < 1.0f) {
                yOffset -= KeyguardViewManager.this.mKeyguardViewMod.getParallaxDistance();
                if (wallpaper.getHeight() >= wallpaper.getWidth()) {
                    return Bitmap.createBitmap(wallpaper, xOffset, yOffset, dw, dh);
                }
                return Bitmap.createBitmap(wallpaper, xOffset, yOffset, dw, dh);
            }
            int xPixels = (dw - ((int) (((float) wallpaper.getWidth()) * scale))) / 2;
            int yPixels = (dh - ((int) (((float) wallpaper.getHeight()) * scale))) / 2;
            int availwUnscaled = dw - wallpaper.getWidth();
            int availhUnscaled = dh - wallpaper.getHeight();
            if (availwUnscaled < 0) {
                xPixels += (int) ((((float) availwUnscaled) * -0.5f) + 0.5f);
            }
            if (availhUnscaled < 0) {
                yPixels += (int) ((((float) availhUnscaled) * -0.5f) + 0.5f);
            }
            return Bitmap.createBitmap(wallpaper, (int) (((float) (Math.abs(xPixels) + xOffset)) / scale), yPixels, (int) (((float) dw) / scale), (int) (((float) dh) / scale));
        }

        private Bitmap getFittedAlbumArt(Bitmap bitmap) {
            WindowManager service = (WindowManager) KeyguardViewManager.this.mContext.getSystemService("window");
            Point size = new Point();
            service.getDefaultDisplay().getSize(size);
            int dw = size.x;
            int dh = size.y;
            if (dw <= bitmap.getWidth() && dh <= bitmap.getHeight()) {
                return Bitmap.createBitmap(bitmap, 0, 0, dw, dh);
            }
            float scale = Math.max(1.0f, Math.max(((float) dw) / ((float) bitmap.getWidth()), ((float) dh) / ((float) bitmap.getHeight())));
            int scaledHeight = Math.round(((float) bitmap.getHeight()) * scale);
            int scaledWidth = Math.round(((float) bitmap.getWidth()) * scale);
            return Bitmap.createBitmap(Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true), Math.round(((float) (scaledWidth - dw)) / 2.0f), Math.round(((float) (scaledHeight - dh)) / 2.0f), dw, dh);
        }

        protected Boolean doInBackground(Drawable... images) {
            Context context = KeyguardViewManager.this.mContext;
            if (context == null || images == null) {
                return Boolean.valueOf(KeyguardViewManager.DEBUG);
            }
            Bitmap bitmapIn;
            int rotation = ((WindowManager) context.getSystemService("window")).getDefaultDisplay().getRotation();
            if (rotation == 3 || rotation == 1) {
            }
            if (images[0] != null) {
                bitmapIn = getFittedAlbumArt(drawableToBitmap(KeyguardViewManager.this.mContext, images[0]));
            } else if (KeyguardViewManager.this.mUserBackground != null) {
                try {
                    bitmapIn = drawableToBitmap(KeyguardViewManager.this.mContext, KeyguardViewManager.this.mUserBackground);
                } catch (Exception e) {
                    Log.e(KeyguardViewManager.TAG, "drawableToBitmap() threw exception", e);
                    bitmapIn = null;
                }
            } else if (WallpaperManager.getInstance(KeyguardViewManager.this.mContext).getWallpaperInfo() != null) {
                return Boolean.valueOf(KeyguardViewManager.DEBUG);
            } else {
                long timeBefore = System.currentTimeMillis();
                try {
                    bitmapIn = getRegularWallpaperBitmap();
                } catch (Exception e2) {
                    Log.e(KeyguardViewManager.TAG, "getRegularWallpaperBitmap() threw exception", e2);
                    bitmapIn = null;
                }
                System.currentTimeMillis();
            }
            if (bitmapIn == null) {
                return Boolean.valueOf(KeyguardViewManager.DEBUG);
            }
            if (isCancelled()) {
                return Boolean.valueOf(KeyguardViewManager.DEBUG);
            }
            long timeScale1 = System.currentTimeMillis();
            Bitmap scaledBitmap = bitmapToScale(bitmapIn, this.mScreen.x, this.mScreen.y, 0.5f);
            long timeScale2 = System.currentTimeMillis();
            this.mScaledWallpaper = new BitmapDrawable(KeyguardViewManager.this.mContext.getResources(), scaledBitmap);
            long timeBlur1 = System.currentTimeMillis();
            Bitmap blur = blurBitmap(scaledBitmap);
            long timeBlur2 = System.currentTimeMillis();
            this.mScaledBlurWallpaper = new BitmapDrawable(KeyguardViewManager.this.mContext.getResources(), blur);
            return Boolean.valueOf(true);
        }

        protected void onPostExecute(Boolean result) {
            if (isCancelled()) {
                this.mScaledWallpaper = null;
                this.mScaledBlurWallpaper = null;
                return;
            }
            if (result.booleanValue()) {
                KeyguardViewManager.this.mModBackground = this.mScaledWallpaper;
                KeyguardViewManager.this.mModBlurBackground = this.mScaledBlurWallpaper;
            } else {
                KeyguardViewManager.this.mLastXOffset = -1;
                KeyguardViewManager.this.mLastYOffset = -1;
                KeyguardViewManager.this.mModBackground = null;
                KeyguardViewManager.this.mModBlurBackground = null;
            }
            if (KeyguardViewManager.this.mKeyguardViewMod != null) {
                KeyguardViewManager.this.flushModBackgrounds();
            }
            this.endTaskTime = System.currentTimeMillis();
            this.mScaledWallpaper = null;
            this.mScaledBlurWallpaper = null;
            KeyguardViewManager.this.mBlurTask = null;
        }
    }

    public interface ShowListener {
        void onShown(IBinder iBinder);
    }

    class ViewManagerHost extends FrameLayout {
        private static final int BACKGROUND_COLOR = 1879048192;
        private static final int TRANSPARENT_COLOR = 0;
        private final Drawable mBackgroundDrawable = new Drawable() {
            public void draw(Canvas canvas) {
                ViewManagerHost.this.drawToCanvas(canvas, ViewManagerHost.this.mCustomBackground);
            }

            public void setAlpha(int alpha) {
            }

            public void setColorFilter(ColorFilter cf) {
            }

            public int getOpacity() {
                return -3;
            }
        };
        private Drawable mCustomBackground;
        int mOrientation;
        private TransitionDrawable mTransitionBackground = null;

        public ViewManagerHost(Context context) {
            super(context);
            if (KeyguardViewManager.this.mIsModLockEnabled) {
                setBackground(null);
            } else {
                setBackground(this.mBackgroundDrawable);
            }
        }

        public void drawToCanvas(Canvas canvas, Drawable drawable) {
            if (drawable != null) {
                Rect bounds = drawable.getBounds();
                int vWidth = getWidth();
                int vHeight = getHeight();
                int restore = canvas.save();
                canvas.translate((float) ((-(bounds.width() - vWidth)) / 2), (float) ((-(bounds.height() - vHeight)) / 2));
                drawable.draw(canvas);
                canvas.restoreToCount(restore);
            } else if (KeyguardViewManager.this.mKeyguardViewMod == null) {
                canvas.drawColor(BACKGROUND_COLOR, Mode.SRC);
            }
        }

        public void setCustomBackground(Drawable d) {
            if (!isLaidOut()) {
                return;
            }
            if (KeyguardViewManager.this.mIsModLockEnabled) {
                cacheBlurredBackground(d);
                return;
            }
            boolean newIsNull;
            Rect bounds;
            if (ActivityManager.isHighEndGfx() && KeyguardViewManager.this.mScreenOn && (KeyguardViewManager.this.mUserBackground == null || KeyguardViewManager.this.mIsMusicPlaying)) {
                Drawable old = this.mCustomBackground;
                if (old != null || d != null || KeyguardViewManager.this.mUserBackground != null) {
                    newIsNull = KeyguardViewManager.DEBUG;
                    if (old == null) {
                        old = new ColorDrawable(BACKGROUND_COLOR);
                        old.setColorFilter(BACKGROUND_COLOR, Mode.SRC_OVER);
                    }
                    if (d == null && KeyguardViewManager.this.mUserBackground != null) {
                        d = KeyguardViewManager.this.mUserBackground;
                        if (getWidth() > getHeight()) {
                            bounds = KeyguardViewManager.this.mUserBackground.getBounds();
                            if (bounds == null) {
                                bounds = new Rect(0, 0, getWidth(), getHeight());
                            }
                            KeyguardViewManager.this.mUserBackground.setBounds(bounds.left, bounds.top, bounds.bottom, bounds.right);
                        }
                    }
                    if (d == null) {
                        d = new ColorDrawable(BACKGROUND_COLOR);
                        newIsNull = true;
                    }
                    d.setColorFilter(BACKGROUND_COLOR, Mode.SRC_OVER);
                    computeCustomBackgroundBounds(d);
                    Bitmap b = Bitmap.createBitmap(getWidth(), getHeight(), Config.ARGB_8888);
                    drawToCanvas(new Canvas(b), d);
                    Drawable dd = new BitmapDrawable(this.mContext.getResources(), b);
                    this.mTransitionBackground = new TransitionDrawable(new Drawable[]{old, dd});
                    this.mTransitionBackground.setCrossFadeEnabled(true);
                    setBackground(this.mTransitionBackground);
                    this.mTransitionBackground.startTransition(KeyguardViewManager.WALLPAPER_PAPER_OFFSET);
                    if (newIsNull) {
                        dd = null;
                    }
                    this.mCustomBackground = dd;
                } else {
                    return;
                }
            } else if (d != null || KeyguardViewManager.this.mUserBackground == null) {
                newIsNull = KeyguardViewManager.DEBUG;
                if (d == null) {
                    d = new ColorDrawable(BACKGROUND_COLOR);
                    newIsNull = true;
                }
                d.setColorFilter(BACKGROUND_COLOR, Mode.SRC_OVER);
                computeCustomBackgroundBounds(d);
                this.mCustomBackground = newIsNull ? null : d;
                setBackground(this.mBackgroundDrawable);
            } else {
                if (getWidth() > getHeight()) {
                    bounds = KeyguardViewManager.this.mUserBackground.getBounds();
                    if (bounds == null) {
                        bounds = new Rect(0, 0, getWidth(), getHeight());
                    }
                    KeyguardViewManager.this.mUserBackground.setBounds(bounds.left, bounds.top, bounds.bottom, bounds.right);
                }
                KeyguardViewManager.this.mUserBackground.setColorFilter(BACKGROUND_COLOR, Mode.SRC_OVER);
                setBackground(KeyguardViewManager.this.mUserBackground);
                return;
            }
            invalidate();
        }

        private void computeCustomBackgroundBounds(Drawable background) {
            if (background != null && isLaidOut()) {
                int bgWidth = background.getIntrinsicWidth();
                int bgHeight = background.getIntrinsicHeight();
                int vWidth = getWidth();
                int vHeight = getHeight();
                float bgAspect = ((float) bgWidth) / ((float) bgHeight);
                if (bgAspect > ((float) vWidth) / ((float) vHeight)) {
                    background.setBounds(0, 0, (int) (((float) vHeight) * bgAspect), vHeight);
                } else {
                    background.setBounds(0, 0, vWidth, (int) (((float) vWidth) / bgAspect));
                }
            }
        }

        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            computeCustomBackgroundBounds(this.mCustomBackground);
        }

        protected void onConfigurationChanged(Configuration newConfig) {
            super.onConfigurationChanged(newConfig);
            this.mOrientation = newConfig.orientation;
            if (KeyguardViewManager.this.mKeyguardHost.getVisibility() == 0) {
                KeyguardViewManager.this.maybeCreateKeyguardLocked(KeyguardViewManager.this.shouldEnableScreenRotation(), true, null);
            }
        }

        public boolean dispatchKeyEvent(KeyEvent event) {
            if (!(KeyguardViewManager.this.mKeyguardView == null && KeyguardViewManager.this.mKeyguardViewMod == null)) {
                int keyCode = event.getKeyCode();
                int action = event.getAction();
                if (action == 0) {
                    if (KeyguardViewManager.this.handleKeyDown(keyCode, event)) {
                        return true;
                    }
                } else if (action == 1 && KeyguardViewManager.this.handleKeyUp(keyCode, event)) {
                    return true;
                }
                if (KeyguardViewManager.this.mKeyguardView != null && KeyguardViewManager.this.mKeyguardView.dispatchKeyEvent(event)) {
                    return true;
                }
                if (KeyguardViewManager.this.mKeyguardViewMod != null && KeyguardViewManager.this.mKeyguardViewMod.dispatchKeyEvent(event)) {
                    return true;
                }
            }
            return super.dispatchKeyEvent(event);
        }

        public boolean shouldShowWallpaper(boolean hiding) {
            if (!KeyguardViewManager.this.mIsModLockEnabled) {
                if (hiding) {
                    if (this.mCustomBackground != null) {
                        return KeyguardViewManager.DEBUG;
                    }
                    WallpaperManager wm = WallpaperManager.getInstance(this.mContext);
                    boolean liveWallpaperActive = (wm == null || wm.getWallpaperInfo() == null) ? KeyguardViewManager.DEBUG : true;
                    if (liveWallpaperActive) {
                        return KeyguardViewManager.DEBUG;
                    }
                }
                return this.mCustomBackground == null ? true : KeyguardViewManager.DEBUG;
            } else if (!hiding) {
                return shouldShowWallpaper();
            } else {
                ComponentName component1 = new Intent(((RecentTaskInfo) ((ActivityManager) this.mContext.getSystemService("activity")).getRecentTasksForUser(1, 2, UserHandle.CURRENT.getIdentifier()).get(0)).baseIntent).getComponent();
                if (KeyguardViewManager.this.mIsMusicPlaying || !isCurrentHomeActivity(component1, null)) {
                    KeyguardViewManager.this.mWindowLayoutParams.windowAnimations = R.style.Animation_LockScreenModSlide;
                    KeyguardViewManager.this.mViewManager.updateViewLayout(KeyguardViewManager.this.mKeyguardHost, KeyguardViewManager.this.mWindowLayoutParams);
                    return KeyguardViewManager.DEBUG;
                }
                KeyguardViewManager.this.mWindowLayoutParams.windowAnimations = R.style.Animation_LockScreenMod;
                KeyguardViewManager.this.mViewManager.updateViewLayout(KeyguardViewManager.this.mKeyguardHost, KeyguardViewManager.this.mWindowLayoutParams);
                return true;
            }
        }

        public boolean shouldShowWallpaper() {
            if (KeyguardViewManager.this.mIsMusicPlaying) {
                return KeyguardViewManager.DEBUG;
            }
            WallpaperManager wm = WallpaperManager.getInstance(this.mContext);
            if (wm != null) {
                if (wm.getWallpaperInfo() != null) {
                    return true;
                }
                if (KeyguardViewManager.this.mUserBackground != null) {
                    return KeyguardViewManager.DEBUG;
                }
            }
            return true;
        }

        private boolean isCurrentHomeActivity(ComponentName component, ActivityInfo homeInfo) {
            if (homeInfo == null) {
                homeInfo = new Intent("android.intent.action.MAIN").addCategory("android.intent.category.HOME").resolveActivityInfo(this.mContext.getPackageManager(), 0);
            }
            if (homeInfo != null && homeInfo.packageName.equals(component.getPackageName()) && homeInfo.name.equals(component.getClassName())) {
                return true;
            }
            return KeyguardViewManager.DEBUG;
        }

        private synchronized void cacheBlurredBackground(Drawable background) {
            if (KeyguardViewManager.this.mModBlurBackground == null) {
                KeyguardViewManager.this.requestBlurTask(background);
            }
        }

        private void cleanup() {
            this.mCustomBackground = null;
            this.mTransitionBackground = null;
        }
    }

    private void maybeBlurAgain() {
        this.mHandler.post(this.mMaybeBlurAgainRunnable);
    }

    public void setMusicPlaying(boolean playing) {
        if (playing != this.mIsMusicPlaying) {
            this.mIsMusicPlaying = playing;
            if (this.mIsModLockEnabled) {
                updateShowWallpaper(this.mKeyguardHost.shouldShowWallpaper());
                maybeBlurAgain();
            }
        }
    }

    private void updatePalette() {
        Palette palette = null;
        if (this.mKeyguardAlbumArtPalette != null) {
            palette = this.mKeyguardAlbumArtPalette;
        } else if (this.mKeyguardPalette != null) {
            palette = this.mKeyguardPalette;
        }
        if (this.mKeyguardViewMod != null) {
            this.mKeyguardViewMod.setKeyguardPalette(palette);
        }
    }

    public KeyguardViewManager(Context context, ViewManager viewManager, ViewMediatorCallback callback, LockPatternUtils lockPatternUtils) {
        this.mContext = context;
        this.mViewManager = viewManager;
        this.mViewMediatorCallback = callback;
        this.mLockPatternUtils = lockPatternUtils;
        this.mIsModLockEnabled = isModLockEnabled(this.mContext);
        this.mSmartCoverCoords = this.mContext.getResources().getIntArray(17235997);
        if (this.mSmartCoverCoords.length != 4) {
            this.mSmartCoverCoords = null;
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.intent.action.WALLPAPER_CHANGED");
        filter.addAction("android.intent.action.KEYGUARD_WALLPAPER_CHANGED");
        context.registerReceiver(new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                if ("android.intent.action.KEYGUARD_WALLPAPER_CHANGED".equals(intent.getAction()) || "android.intent.action.WALLPAPER_CHANGED".equals(intent.getAction())) {
                    KeyguardViewManager.this.cacheUserImage();
                }
            }
        }, filter, "android.permission.CONTROL_KEYGUARD", null);
        cacheUserImage();
    }

    public synchronized void show(Bundle options) {
        boolean enableScreenRotation = shouldEnableScreenRotation();
        if (this.mCleanupKeyguardAction != null) {
            this.mKeyguardHost.removeCallbacks(this.mCleanupKeyguardAction);
            this.mCleanupKeyguardAction.run();
        }
        this.mIsModLockEnabled = isModLockEnabled(this.mContext);
        maybeBlurAgain();
        maybeCreateKeyguardLocked(enableScreenRotation, DEBUG, options);
        maybeEnableScreenRotation(enableScreenRotation);
        updateShowWallpaper(this.mKeyguardHost.shouldShowWallpaper());
        if (shouldEnableTranslucentDecor()) {
            LayoutParams layoutParams = this.mWindowLayoutParams;
            layoutParams.flags |= 201326592;
        }
        this.mKeyguardHost.setSystemUiVisibility(2097408);
        this.mViewManager.updateViewLayout(this.mKeyguardHost, this.mWindowLayoutParams);
        this.mKeyguardHost.setVisibility(0);
        if (this.mKeyguardView != null) {
            this.mKeyguardView.show();
            this.mKeyguardView.requestFocus();
        } else if (this.mKeyguardViewMod != null) {
            if (this.mKeyguardPalette != null) {
                this.mKeyguardViewMod.setKeyguardPalette(this.mKeyguardPalette);
            }
            this.mKeyguardViewMod.show();
            this.mKeyguardViewMod.requestFocus();
        }
        if (!this.mIsModLockEnabled) {
            if (!(this.mKeyguardHost == null || this.mUserBackground == null)) {
                this.mKeyguardHost.setCustomBackground(this.mUserBackground);
            }
        }
    }

    private boolean shouldEnableScreenRotation() {
        Resources res = this.mContext.getResources();
        boolean enableLockScreenRotation = System.getInt(this.mContext.getContentResolver(), "lockscreen_rotation", 0) != 0 ? true : DEBUG;
        boolean enableAccelerometerRotation = System.getInt(this.mContext.getContentResolver(), "accelerometer_rotation", 1) != 0 ? true : DEBUG;
        if (SystemProperties.getBoolean("lockscreen.rot_override", DEBUG) || (enableLockScreenRotation && enableAccelerometerRotation)) {
            return true;
        }
        return DEBUG;
    }

    private boolean shouldEnableTranslucentDecor() {
        return this.mContext.getResources().getBoolean(R.bool.config_enableLockScreenTranslucentDecor);
    }

    public boolean handleKeyDown(int keyCode, KeyEvent event) {
        if (event.getRepeatCount() == 0) {
            this.mUnlockKeyDown = true;
            if (keyCode == 27) {
                if (this.mKeyguardView != null && this.mKeyguardView.handleCameraKey()) {
                    return true;
                }
                if (this.mKeyguardViewMod != null && this.mKeyguardViewMod.handleCameraKey()) {
                    return true;
                }
            }
        }
        if (event.isLongPress()) {
            String action = null;
            switch (keyCode) {
                case SlidingChallengeLayout.SCROLL_STATE_FADING /*3*/:
                    action = "lockscreen_long_home_action";
                    break;
                case SlidingChallengeLayout.LayoutParams.CHILD_TYPE_SCRIM /*4*/:
                    action = "lockscreen_long_back_action";
                    break;
                case 82:
                    action = "lockscreen_long_menu_action";
                    break;
            }
            if (action != null) {
                this.mUnlockKeyDown = DEBUG;
                String uri = System.getString(this.mContext.getContentResolver(), action);
                if (uri != null && runAction(this.mContext, uri)) {
                    long[] pattern = getLongPressVibePattern(this.mContext);
                    if (pattern == null) {
                        return true;
                    }
                    Context context = this.mContext;
                    Context context2 = this.mContext;
                    Vibrator v = (Vibrator) context.getSystemService("vibrator");
                    if (pattern.length == 1) {
                        v.vibrate(pattern[0]);
                        return true;
                    }
                    v.vibrate(pattern, -1);
                    return true;
                }
            }
        }
        return DEBUG;
    }

    public boolean handleKeyUp(int keyCode, KeyEvent event) {
        if (this.mUnlockKeyDown) {
            this.mUnlockKeyDown = DEBUG;
            switch (keyCode) {
                case SlidingChallengeLayout.SCROLL_STATE_FADING /*3*/:
                    if (this.mKeyguardView != null && this.mKeyguardView.handleHomeKey()) {
                        return true;
                    }
                    if (this.mKeyguardViewMod != null && this.mKeyguardViewMod.handleHomeKey()) {
                        return true;
                    }
                    break;
                case SlidingChallengeLayout.LayoutParams.CHILD_TYPE_SCRIM /*4*/:
                    if (this.mKeyguardView != null && this.mKeyguardView.handleBackKey()) {
                        return true;
                    }
                    if (this.mKeyguardViewMod != null && this.mKeyguardViewMod.handleBackKey()) {
                        return true;
                    }
                    break;
                case 82:
                    if (this.mKeyguardView != null && this.mKeyguardView.handleMenuKey()) {
                        return true;
                    }
                    if (this.mKeyguardViewMod != null && this.mKeyguardViewMod.handleMenuKey()) {
                        return true;
                    }
                    break;
            }
        }
        return DEBUG;
    }

    private static boolean runAction(Context context, String uri) {
        if ("FLASHLIGHT".equals(uri)) {
            context.sendBroadcast(new Intent("net.cactii.flash2.TOGGLE_FLASHLIGHT"));
            return true;
        } else if ("NEXT".equals(uri)) {
            sendMediaButtonEvent(context, 87);
            return true;
        } else if ("PREVIOUS".equals(uri)) {
            sendMediaButtonEvent(context, 88);
            return true;
        } else if ("PLAYPAUSE".equals(uri)) {
            sendMediaButtonEvent(context, 85);
            return true;
        } else if ("SOUND".equals(uri)) {
            toggleSilentMode(context);
            return true;
        } else if (!"SLEEP".equals(uri)) {
            return DEBUG;
        } else {
            sendToSleep(context);
            return true;
        }
    }

    private static void sendMediaButtonEvent(Context context, int code) {
        long eventtime = SystemClock.uptimeMillis();
        Intent downIntent = new Intent("android.intent.action.MEDIA_BUTTON", null);
        downIntent.putExtra("android.intent.extra.KEY_EVENT", new KeyEvent(eventtime, eventtime, 0, code, 0));
        context.sendOrderedBroadcast(downIntent, null);
        Intent upIntent = new Intent("android.intent.action.MEDIA_BUTTON", null);
        upIntent.putExtra("android.intent.extra.KEY_EVENT", new KeyEvent(eventtime, eventtime, 1, code, 0));
        context.sendOrderedBroadcast(upIntent, null);
    }

    private static void toggleSilentMode(Context context) {
        int i = 0;
        AudioManager am = (AudioManager) context.getSystemService("audio");
        Vibrator vib = (Vibrator) context.getSystemService("vibrator");
        boolean hasVib = vib == null ? DEBUG : vib.hasVibrator();
        if (am.getRingerMode() == 2) {
            if (hasVib) {
                i = 1;
            }
            am.setRingerMode(i);
            return;
        }
        am.setRingerMode(2);
    }

    private static long[] getLongPressVibePattern(Context context) {
        long[] jArr = null;
        if (System.getInt(context.getContentResolver(), "haptic_feedback_enabled", 0) != 0) {
            int[] defaultPattern = context.getResources().getIntArray(17236000);
            if (defaultPattern != null) {
                jArr = new long[defaultPattern.length];
                for (int i = 0; i < defaultPattern.length; i++) {
                    jArr[i] = (long) defaultPattern[i];
                }
            }
        }
        return jArr;
    }

    private static void sendToSleep(Context context) {
        ((PowerManager) context.getSystemService("power")).goToSleep(SystemClock.uptimeMillis());
    }

    private void maybeCreateKeyguardLocked(boolean enableScreenRotation, boolean force, Bundle options) {
        if (this.mKeyguardHost != null) {
            this.mKeyguardHost.saveHierarchyState(this.mStateContainer);
        }
        if (this.mKeyguardHost == null) {
            this.mKeyguardHost = new ViewManagerHost(this.mContext);
            int flags = 67840;
            if (!this.mNeedsInput) {
                flags = 67840 | 131072;
            }
            LayoutParams lp = new LayoutParams(-1, -1, 2004, flags, -3);
            lp.softInputMode = 16;
            lp.windowAnimations = R.style.Animation_LockScreen;
            lp.screenOrientation = enableScreenRotation ? 2 : 5;
            if (ActivityManager.isHighEndGfx()) {
                lp.flags |= 16777216;
                lp.privateFlags |= 2;
            }
            lp.privateFlags |= 8;
            lp.inputFeatures |= 4;
            lp.setTitle("Keyguard");
            this.mWindowLayoutParams = lp;
            this.mViewManager.addView(this.mKeyguardHost, lp);
            KeyguardUpdateMonitor.getInstance(this.mContext).registerCallback(this.mUpdateMonitorCallback);
        }
        if (force || (this.mKeyguardView == null && this.mKeyguardViewMod == null)) {
            this.mKeyguardHost.removeAllViews();
            inflateKeyguardView(options);
            if (!this.mHandler.hasCallbacks(this.mSetNullBackground)) {
                this.mHandler.postDelayed(this.mSetNullBackground, 200);
            }
            if (this.mKeyguardView != null) {
                this.mKeyguardView.requestFocus();
            } else if (this.mKeyguardViewMod != null) {
                if (this.mKeyguardPalette != null) {
                    this.mKeyguardViewMod.setKeyguardPalette(this.mKeyguardPalette);
                }
                this.mKeyguardViewMod.requestFocus();
            }
        }
        updateUserActivityTimeoutInWindowLayoutParams();
        this.mViewManager.updateViewLayout(this.mKeyguardHost, this.mWindowLayoutParams);
        this.mKeyguardHost.restoreHierarchyState(this.mStateContainer);
    }

    private void inflateKeyguardView(Bundle options) {
        View v = this.mKeyguardHost.findViewById(R.id.keyguard_host_view);
        if (v != null) {
            this.mKeyguardHost.removeView(v);
        }
        v = this.mKeyguardHost.findViewById(R.id.keyguard_host_view_mod);
        if (v != null) {
            this.mKeyguardHost.removeView(v);
        }
        View cover = this.mKeyguardHost.findViewById(R.id.keyguard_cover_layout);
        if (cover != null) {
            this.mKeyguardHost.removeView(cover);
        }
        LayoutInflater inflater = LayoutInflater.from(this.mContext);
        boolean z;
        if (this.mIsModLockEnabled) {
            this.mKeyguardViewMod = (KeyguardHostViewMod) inflater.inflate(R.layout.keyguard_host_view_mod, this.mKeyguardHost, true).findViewById(R.id.keyguard_host_view_mod);
            this.mKeyguardViewMod.setLockPatternUtils(this.mLockPatternUtils);
            this.mKeyguardViewMod.setViewMediatorCallback(this.mViewMediatorCallback);
            KeyguardHostViewMod keyguardHostViewMod = this.mKeyguardViewMod;
            if (options != null) {
                if (options.getBoolean(IS_SWITCHING_USER)) {
                    z = true;
                    keyguardHostViewMod.initializeSwitchingUserState(z);
                    this.mKeyguardView = null;
                    this.mWindowLayoutParams.windowAnimations = R.style.Animation_LockScreenMod;
                }
            }
            z = DEBUG;
            keyguardHostViewMod.initializeSwitchingUserState(z);
            this.mKeyguardView = null;
            this.mWindowLayoutParams.windowAnimations = R.style.Animation_LockScreenMod;
        } else {
            this.mKeyguardView = (KeyguardHostView) inflater.inflate(R.layout.keyguard_host_view, this.mKeyguardHost, true).findViewById(R.id.keyguard_host_view);
            this.mKeyguardView.setLockPatternUtils(this.mLockPatternUtils);
            this.mKeyguardView.setViewMediatorCallback(this.mViewMediatorCallback);
            KeyguardHostView keyguardHostView = this.mKeyguardView;
            if (options != null) {
                if (options.getBoolean(IS_SWITCHING_USER)) {
                    z = true;
                    keyguardHostView.initializeSwitchingUserState(z);
                    this.mKeyguardViewMod = null;
                    this.mWindowLayoutParams.windowAnimations = R.style.Animation_LockScreen;
                }
            }
            z = DEBUG;
            keyguardHostView.initializeSwitchingUserState(z);
            this.mKeyguardViewMod = null;
            this.mWindowLayoutParams.windowAnimations = R.style.Animation_LockScreen;
        }
        if (this.mViewMediatorCallback != null) {
            KeyguardPasswordView kpv;
            if (this.mKeyguardView != null) {
                kpv = (KeyguardPasswordView) this.mKeyguardView.findViewById(R.id.keyguard_password_view);
            } else {
                kpv = (KeyguardPasswordView) this.mKeyguardViewMod.findViewById(R.id.keyguard_password_view);
            }
            if (kpv != null) {
                this.mViewMediatorCallback.setNeedsInput(kpv.needsInput());
            }
        }
        if (options != null) {
            int widgetToShow = options.getInt("showappwidget", 0);
            if (widgetToShow != 0) {
                if (this.mKeyguardView != null) {
                    this.mKeyguardView.goToWidget(widgetToShow);
                } else {
                    this.mKeyguardViewMod.goToWidget(widgetToShow);
                }
            }
        }
        flushModBackgrounds();
        if (this.mSmartCoverCoords != null) {
            this.mCoverView = (KeyguardSmartCoverView) inflater.inflate(R.layout.keyguard_smart_cover, this.mKeyguardHost, true).findViewById(R.id.keyguard_cover_layout);
            int[] coverWindowCoords = this.mSmartCoverCoords;
            DisplayMetrics metrics = this.mContext.getResources().getDisplayMetrics();
            int windowHeight = coverWindowCoords[2] - coverWindowCoords[0];
            int windowWidth = (metrics.widthPixels - coverWindowCoords[1]) - (metrics.widthPixels - coverWindowCoords[3]);
            LayoutParams lp = new LayoutParams(-1, -1, 2004, 1296, -3);
            lp.gravity = 49;
            lp.screenOrientation = 5;
            lp.setTitle("SmartCover");
            this.mWindowCoverLayoutParams = lp;
            this.mCoverView.setAlpha(0.0f);
            this.mCoverView.setVisibility(0);
            LinearLayout.LayoutParams contentParams = (LinearLayout.LayoutParams) this.mCoverView.findViewById(R.id.content).getLayoutParams();
            contentParams.height = windowHeight;
            contentParams.width = windowWidth;
            contentParams.leftMargin = coverWindowCoords[1];
        }
    }

    public static boolean isModLockEnabled(Context context) {
        return System.getInt(context.getContentResolver(), "lockscreen_modlock_enabled", 1) != 0 ? true : DEBUG;
    }

    public void updateUserActivityTimeout() {
        updateUserActivityTimeoutInWindowLayoutParams();
        this.mViewManager.updateViewLayout(this.mKeyguardHost, this.mWindowLayoutParams);
    }

    private void updateUserActivityTimeoutInWindowLayoutParams() {
        long timeout;
        if (this.mKeyguardView != null) {
            timeout = this.mKeyguardView.getUserActivityTimeout();
            if (timeout >= 0) {
                this.mWindowLayoutParams.userActivityTimeout = timeout;
                return;
            }
        } else if (this.mKeyguardViewMod != null) {
            timeout = this.mKeyguardViewMod.getUserActivityTimeout();
            if (timeout >= 0) {
                this.mWindowLayoutParams.userActivityTimeout = timeout;
                return;
            }
        }
        this.mWindowLayoutParams.userActivityTimeout = 10000;
    }

    private void maybeEnableScreenRotation(boolean enableScreenRotation) {
        if (enableScreenRotation) {
            this.mWindowLayoutParams.screenOrientation = 2;
        } else {
            this.mWindowLayoutParams.screenOrientation = 5;
        }
        this.mViewManager.updateViewLayout(this.mKeyguardHost, this.mWindowLayoutParams);
    }

    void updateShowWallpaper(boolean show) {
        LayoutParams layoutParams;
        if (show) {
            layoutParams = this.mWindowLayoutParams;
            layoutParams.flags |= 1048576;
        } else {
            layoutParams = this.mWindowLayoutParams;
            layoutParams.flags &= -1048577;
        }
        this.mViewManager.updateViewLayout(this.mKeyguardHost, this.mWindowLayoutParams);
    }

    public void setNeedsInput(boolean needsInput) {
        this.mNeedsInput = needsInput;
        if (this.mWindowLayoutParams != null) {
            LayoutParams layoutParams;
            if (needsInput) {
                layoutParams = this.mWindowLayoutParams;
                layoutParams.flags &= -131073;
            } else {
                layoutParams = this.mWindowLayoutParams;
                layoutParams.flags |= 131072;
            }
            try {
                this.mViewManager.updateViewLayout(this.mKeyguardHost, this.mWindowLayoutParams);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Can't update input method on " + this.mKeyguardHost + " window not attached");
            }
        }
    }

    public synchronized void reset(Bundle options) {
        maybeCreateKeyguardLocked(shouldEnableScreenRotation(), true, options);
    }

    public synchronized void onScreenTurnedOff() {
        this.mScreenOn = DEBUG;
        if (this.mKeyguardView != null) {
            this.mKeyguardView.onScreenTurnedOff();
        } else if (this.mKeyguardViewMod != null) {
            this.mKeyguardViewMod.onScreenTurnedOff();
        }
        this.mHandler.removeCallbacks(this.mSmartCoverTimeout);
    }

    public synchronized void onScreenTurnedOn(final IKeyguardShowCallback callback) {
        this.mScreenOn = true;
        final IBinder token = isShowing() ? this.mKeyguardHost.getWindowToken() : null;
        if (this.mKeyguardView != null || this.mKeyguardViewMod != null) {
            if (this.mKeyguardView != null) {
                this.mKeyguardView.onScreenTurnedOn();
            } else if (this.mKeyguardViewMod != null) {
                this.mKeyguardViewMod.onScreenTurnedOn();
            }
            resetSmartCoverState();
            if (callback != null) {
                if (this.mKeyguardHost.getVisibility() == 0) {
                    this.mKeyguardHost.post(new Runnable() {
                        public void run() {
                            try {
                                callback.onShown(token);
                            } catch (RemoteException e) {
                                Slog.w(KeyguardViewManager.TAG, "Exception calling onShown():", e);
                            }
                        }
                    });
                } else {
                    try {
                        callback.onShown(token);
                    } catch (RemoteException e) {
                        Slog.w(TAG, "Exception calling onShown():", e);
                    }
                }
            }
        } else if (callback != null) {
            try {
                callback.onShown(token);
            } catch (RemoteException e2) {
                Slog.w(TAG, "Exception calling onShown():", e2);
            }
        }
    }

    public synchronized void verifyUnlock() {
        show(null);
        if (this.mKeyguardView != null) {
            this.mKeyguardView.verifyUnlock();
        } else {
            this.mKeyguardViewMod.verifyUnlock();
        }
    }

    public synchronized void hide() {
        if (this.mKeyguardHost != null) {
            this.mKeyguardHost.setVisibility(8);
            this.mStateContainer.clear();
            updateShowWallpaper(this.mKeyguardHost.shouldShowWallpaper(true));
            final KeyguardViewBase lastView = this.mKeyguardView != null ? this.mKeyguardView : this.mKeyguardViewMod;
            if (lastView != null) {
                this.mCleanupKeyguardAction = new Runnable() {
                    public void run() {
                        synchronized (KeyguardViewManager.this) {
                            lastView.cleanUp();
                            KeyguardViewManager.this.mKeyguardHost.cleanup();
                            KeyguardViewManager.this.mKeyguardHost.removeView(lastView);
                            KeyguardViewManager.this.mViewMediatorCallback.keyguardGone();
                            KeyguardViewManager.this.mKeyguardView = null;
                            KeyguardViewManager.this.mKeyguardViewMod = null;
                            KeyguardViewManager.this.mCleanupKeyguardAction = null;
                        }
                    }
                };
                this.mKeyguardHost.postDelayed(this.mCleanupKeyguardAction, 500);
            }
        }
    }

    public synchronized void dismiss() {
        if (this.mScreenOn) {
            if (this.mKeyguardView != null) {
                this.mKeyguardView.dismiss();
            } else if (this.mKeyguardViewMod != null) {
                this.mKeyguardViewMod.dismiss();
            }
        }
    }

    public synchronized boolean isShowing() {
        boolean z;
        z = (this.mKeyguardHost == null || this.mKeyguardHost.getVisibility() != 0) ? DEBUG : true;
        return z;
    }

    public void showAssistant() {
        if (this.mKeyguardView != null) {
            this.mKeyguardView.showAssistant();
        } else if (this.mKeyguardViewMod != null) {
            this.mKeyguardViewMod.showAssistant();
        }
    }

    public void dispatchCameraEvent(MotionEvent event) {
        if (this.mKeyguardView != null) {
            this.mKeyguardView.dispatchCameraEvent(event);
        } else if (this.mKeyguardViewMod != null) {
            this.mKeyguardViewMod.dispatchCameraEvent(event);
        }
    }

    public void dispatchApplicationWidgetEvent(MotionEvent event) {
        if (this.mKeyguardView != null) {
            this.mKeyguardView.dispatchApplicationWidgetEvent(event);
        } else if (this.mKeyguardViewMod != null) {
            this.mKeyguardViewMod.dispatchApplicationWidgetEvent(event);
        }
    }

    public void launchCamera() {
        if (this.mKeyguardView != null) {
            this.mKeyguardView.launchCamera();
        } else if (this.mKeyguardViewMod != null) {
            this.mKeyguardViewMod.launchCamera();
        }
    }

    public void launchApplicationWidget() {
        if (this.mKeyguardView != null) {
            Pair<String, byte[]> applicationWidget = KeyguardUpdateMonitor.getInstance(this.mContext).getApplicationWidgetDetails();
            if (applicationWidget.first != null) {
                String packageName = applicationWidget.first;
                if (this.mKeyguardView != null) {
                    this.mKeyguardView.launchApplicationWidget(packageName);
                } else if (this.mKeyguardViewMod == null) {
                }
            }
        }
    }

    public void showCover() {
        if (this.mSmartCoverCoords != null) {
            KeyguardUpdateMonitor updateMonitor = KeyguardUpdateMonitor.getInstance(this.mContext);
            if (updateMonitor.isDeviceProvisioned() && updateMonitor.hasBootCompleted()) {
                this.mCoverView.setAlpha(1.0f);
                this.mCoverView.setSystemUiVisibility(this.mCoverView.getSystemUiVisibility() | KeyguardSmartCoverView.SYSTEM_UI_FLAGS);
                this.mViewManager.updateViewLayout(this.mKeyguardHost, this.mWindowCoverLayoutParams);
                this.mCoverView.requestLayout();
                this.mCoverView.requestFocus();
            }
        }
    }

    public void hideCover(boolean force) {
        if (this.mSmartCoverCoords != null) {
            KeyguardUpdateMonitor updateMonitor = KeyguardUpdateMonitor.getInstance(this.mContext);
            if (updateMonitor.isDeviceProvisioned() && updateMonitor.hasBootCompleted()) {
                if (force) {
                    this.mCoverView.setAlpha(0.0f);
                } else {
                    this.mCoverView.animate().alpha(0.0f);
                }
                this.mCoverView.setSystemUiVisibility(this.mCoverView.getSystemUiVisibility() & -1287);
                this.mViewManager.updateViewLayout(this.mKeyguardHost, this.mWindowLayoutParams);
            }
        }
    }

    private void resetSmartCoverState() {
        if (this.mSmartCoverCoords != null) {
            this.mHandler.removeCallbacks(this.mSmartCoverTimeout);
            if (this.mPhoneState == 1 || this.mPhoneState == 2) {
                hideCover(true);
            } else if (this.mLidState == 1) {
                hideCover(this.mScreenOn);
            } else if (this.mLidState == 0 && this.mScreenOn) {
                showCover();
                this.mHandler.postDelayed(this.mSmartCoverTimeout, 8000);
            }
        }
    }

    private void flushModBackgrounds() {
        if (this.mKeyguardViewMod != null) {
            this.mKeyguardViewMod.setCustomBackground(this.mModBackground, this.mModBlurBackground);
        }
    }

    private void cacheUserImage() {
        this.mModBackground = null;
        this.mModBlurBackground = null;
        this.mKeyguardPalette = null;
        this.mLastXOffset = -1;
        this.mLastYOffset = -1;
        WallpaperManager wm = WallpaperManager.getInstance(this.mContext);
        Bitmap bitmap = wm.getKeyguardBitmap();
        if (bitmap != null) {
            WindowManager service = (WindowManager) this.mContext.getSystemService("window");
            Point size = new Point();
            service.getDefaultDisplay().getSize(size);
            int dw = size.x;
            int dh = size.y;
            if (dw > bitmap.getWidth() || dh > bitmap.getHeight()) {
                float scale = Math.max(1.0f, Math.max(((float) dw) / ((float) bitmap.getWidth()), ((float) dh) / ((float) bitmap.getHeight())));
                bitmap = Bitmap.createScaledBitmap(bitmap, Math.round(((float) bitmap.getWidth()) * scale), Math.round(((float) bitmap.getHeight()) * scale), true);
            }
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, dw, dh);
            this.mUserBackground = new BitmapDrawable(this.mContext.getResources(), bitmap);
        } else {
            this.mUserBackground = null;
            bitmap = wm.getBitmap();
        }
        Palette.generateAsync(bitmap, new PaletteAsyncListener() {
            public void onGenerated(Palette palette) {
                KeyguardViewManager.this.mKeyguardPalette = palette;
                KeyguardViewManager.this.updatePalette();
            }
        });
    }

    private void requestBlurTask(Drawable d) {
        if (this.mBlurTask != null) {
            if (this.mBlurTask.getStatus() == Status.RUNNING && !this.mIsMusicPlaying) {
                return;
            }
            if (this.mBlurTask.getStatus() != Status.PENDING || this.mIsMusicPlaying) {
                this.mBlurTask.cancel(true);
                this.mBlurTask = null;
            } else {
                return;
            }
        }
        this.mBlurTask = new BlurTask();
        this.mBlurTask.execute(new Drawable[]{d});
    }
}
