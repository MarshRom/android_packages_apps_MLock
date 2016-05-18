package com.cyngn.keyguard;

import android.app.Presentation;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.graphics.Point;
import android.media.MediaRouter;
import android.media.MediaRouter.RouteInfo;
import android.media.MediaRouter.SimpleCallback;
import android.os.Bundle;
import android.util.Slog;
import android.view.Display;
import android.view.View;
import android.view.WindowManager.InvalidDisplayException;

public class KeyguardDisplayManager {
    private static boolean DEBUG = false;
    protected static final String TAG = "KeyguardDisplayManager";
    private Context mContext;
    private MediaRouter mMediaRouter;
    private final SimpleCallback mMediaRouterCallback = new SimpleCallback() {
        public void onRouteSelected(MediaRouter router, int type, RouteInfo info) {
            if (KeyguardDisplayManager.DEBUG) {
                Slog.d(KeyguardDisplayManager.TAG, "onRouteSelected: type=" + type + ", info=" + info);
            }
            KeyguardDisplayManager.this.updateDisplays(KeyguardDisplayManager.this.mShowing);
        }

        public void onRouteUnselected(MediaRouter router, int type, RouteInfo info) {
            if (KeyguardDisplayManager.DEBUG) {
                Slog.d(KeyguardDisplayManager.TAG, "onRouteUnselected: type=" + type + ", info=" + info);
            }
            KeyguardDisplayManager.this.updateDisplays(KeyguardDisplayManager.this.mShowing);
        }

        public void onRoutePresentationDisplayChanged(MediaRouter router, RouteInfo info) {
            if (KeyguardDisplayManager.DEBUG) {
                Slog.d(KeyguardDisplayManager.TAG, "onRoutePresentationDisplayChanged: info=" + info);
            }
            KeyguardDisplayManager.this.updateDisplays(KeyguardDisplayManager.this.mShowing);
        }
    };
    private OnDismissListener mOnDismissListener = new OnDismissListener() {
        public void onDismiss(DialogInterface dialog) {
            KeyguardDisplayManager.this.mPresentation = null;
        }
    };
    Presentation mPresentation;
    private boolean mShowing;

    private static final class KeyguardPresentation extends Presentation {
        private static final int MOVE_CLOCK_TIMEOUT = 10000;
        private static final int VIDEO_SAFE_REGION = 80;
        private View mClock;
        private int mMarginLeft;
        private int mMarginTop;
        Runnable mMoveTextRunnable = new Runnable() {
            public void run() {
                int y = KeyguardPresentation.this.mMarginTop + ((int) (Math.random() * ((double) (KeyguardPresentation.this.mUsableHeight - KeyguardPresentation.this.mClock.getHeight()))));
                KeyguardPresentation.this.mClock.setTranslationX((float) (KeyguardPresentation.this.mMarginLeft + ((int) (Math.random() * ((double) (KeyguardPresentation.this.mUsableWidth - KeyguardPresentation.this.mClock.getWidth()))))));
                KeyguardPresentation.this.mClock.setTranslationY((float) y);
                KeyguardPresentation.this.mClock.postDelayed(KeyguardPresentation.this.mMoveTextRunnable, 10000);
            }
        };
        private int mUsableHeight;
        private int mUsableWidth;

        public KeyguardPresentation(Context context, Display display) {
            super(context, display);
            getWindow().setType(2009);
        }

        public void onDetachedFromWindow() {
            this.mClock.removeCallbacks(this.mMoveTextRunnable);
        }

        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            Point p = new Point();
            getDisplay().getSize(p);
            this.mUsableWidth = (p.x * VIDEO_SAFE_REGION) / 100;
            this.mUsableHeight = (p.y * VIDEO_SAFE_REGION) / 100;
            this.mMarginLeft = (p.x * 20) / KeyguardViewManager.WALLPAPER_PAPER_OFFSET;
            this.mMarginTop = (p.y * 20) / KeyguardViewManager.WALLPAPER_PAPER_OFFSET;
            setContentView(R.layout.keyguard_presentation);
            this.mClock = findViewById(R.id.clock);
            this.mClock.post(this.mMoveTextRunnable);
        }
    }

    KeyguardDisplayManager(Context context) {
        this.mContext = context;
        this.mMediaRouter = (MediaRouter) this.mContext.getSystemService("media_router");
    }

    void show() {
        if (!this.mShowing) {
            if (DEBUG) {
                Slog.v(TAG, "show");
            }
            this.mMediaRouter.addCallback(4, this.mMediaRouterCallback, 8);
            updateDisplays(true);
        }
        this.mShowing = true;
    }

    void hide() {
        if (this.mShowing) {
            if (DEBUG) {
                Slog.v(TAG, "hide");
            }
            this.mMediaRouter.removeCallback(this.mMediaRouterCallback);
            updateDisplays(false);
        }
        this.mShowing = false;
    }

    protected void updateDisplays(boolean showing) {
        boolean useDisplay = true;
        if (showing) {
            Display presentationDisplay;
            RouteInfo route = this.mMediaRouter.getSelectedRoute(4);
            if (route == null || route.getPlaybackType() != 1) {
                useDisplay = false;
            }
            if (useDisplay) {
                presentationDisplay = route.getPresentationDisplay();
            } else {
                presentationDisplay = null;
            }
            if (!(this.mPresentation == null || this.mPresentation.getDisplay() == presentationDisplay)) {
                if (DEBUG) {
                    Slog.v(TAG, "Display gone: " + this.mPresentation.getDisplay());
                }
                this.mPresentation.dismiss();
                this.mPresentation = null;
            }
            if (this.mPresentation == null && presentationDisplay != null) {
                if (DEBUG) {
                    Slog.i(TAG, "Keyguard enabled on display: " + presentationDisplay);
                }
                this.mPresentation = new KeyguardPresentation(this.mContext, presentationDisplay);
                this.mPresentation.setOnDismissListener(this.mOnDismissListener);
                try {
                    this.mPresentation.show();
                } catch (InvalidDisplayException ex) {
                    Slog.w(TAG, "Invalid display:", ex);
                    this.mPresentation = null;
                }
            }
        } else if (this.mPresentation != null) {
            this.mPresentation.dismiss();
            this.mPresentation = null;
        }
    }
}
