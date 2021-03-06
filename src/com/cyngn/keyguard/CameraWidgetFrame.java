package com.cyngn.keyguard;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.FrameLayout.LayoutParams;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import com.cyngn.keyguard.KeyguardActivityLauncher.CameraWidgetInfo;

public class CameraWidgetFrame extends KeyguardWidgetFrame implements OnClickListener {
    private static final boolean DEBUG = KeyguardHostView.DEBUG;
    private static final int RECOVERY_DELAY = 1000;
    private static final String TAG = CameraWidgetFrame.class.getSimpleName();
    public static final int WIDGET_ANIMATION_DURATION = 250;
    private static final int WIDGET_WAIT_DURATION = 400;
    private boolean mActive;
    private final KeyguardActivityLauncher mActivityLauncher;
    private final KeyguardUpdateMonitorCallback mCallback = new KeyguardUpdateMonitorCallback() {
        private boolean mShowing;

        void onKeyguardVisibilityChanged(boolean showing) {
            if (this.mShowing != showing) {
                this.mShowing = showing;
                CameraWidgetFrame.this.onKeyguardVisibilityChanged(this.mShowing);
            }
        }
    };
    private final Callbacks mCallbacks;
    private boolean mDown;
    private View mFakeNavBar;
    private View mFullscreenPreview;
    private final Handler mHandler = new Handler();
    private final Rect mInsets = new Rect();
    private long mLaunchCameraStart;
    private final Runnable mPostTransitionToCameraEndAction = new Runnable() {
        public void run() {
            CameraWidgetFrame.this.mHandler.post(CameraWidgetFrame.this.mTransitionToCameraEndAction);
        }
    };
    private FixedSizeFrameLayout mPreview;
    private final Runnable mRecoverRunnable = new Runnable() {
        public void run() {
            CameraWidgetFrame.this.recover();
        }
    };
    private final Runnable mRenderRunnable = new Runnable() {
        public void run() {
            CameraWidgetFrame.this.render();
        }
    };
    private final Point mRenderedSize = new Point();
    private final Runnable mSecureCameraActivityStartedRunnable = new Runnable() {
        public void run() {
            CameraWidgetFrame.this.onSecureCameraActivityStarted();
        }
    };
    private final int[] mTmpLoc = new int[2];
    private final Runnable mTransitionToCameraEndAction = new Runnable() {
        public void run() {
            if (CameraWidgetFrame.this.mTransitioning) {
                Handler worker = CameraWidgetFrame.this.getWorkerHandler() != null ? CameraWidgetFrame.this.getWorkerHandler() : CameraWidgetFrame.this.mHandler;
                CameraWidgetFrame.this.mLaunchCameraStart = SystemClock.uptimeMillis();
                if (CameraWidgetFrame.DEBUG) {
                    Log.d(CameraWidgetFrame.TAG, "Launching camera at " + CameraWidgetFrame.this.mLaunchCameraStart);
                }
                CameraWidgetFrame.this.mActivityLauncher.launchCamera(worker, CameraWidgetFrame.this.mSecureCameraActivityStartedRunnable);
            }
        }
    };
    private final Runnable mTransitionToCameraRunnable = new Runnable() {
        public void run() {
            CameraWidgetFrame.this.transitionToCamera();
        }
    };
    private boolean mTransitioning;
    private boolean mUseFastTransition;
    private final CameraWidgetInfo mWidgetInfo;
    private final WindowManager mWindowManager;

    interface Callbacks {
        void onCameraLaunchedSuccessfully();

        void onCameraLaunchedUnsuccessfully();

        void onLaunchingCamera();
    }

    private static final class FixedSizeFrameLayout extends FrameLayout {
        int height;
        int width;

        FixedSizeFrameLayout(Context context) {
            super(context);
        }

        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            measureChildren(MeasureSpec.makeMeasureSpec(this.width, 1073741824), MeasureSpec.makeMeasureSpec(this.height, 1073741824));
            setMeasuredDimension(this.width, this.height);
        }
    }

    private CameraWidgetFrame(Context context, Callbacks callbacks, KeyguardActivityLauncher activityLauncher, CameraWidgetInfo widgetInfo, View previewWidget) {
        super(context);
        this.mCallbacks = callbacks;
        this.mActivityLauncher = activityLauncher;
        this.mWidgetInfo = widgetInfo;
        this.mWindowManager = (WindowManager) context.getSystemService("window");
        KeyguardUpdateMonitor.getInstance(context).registerCallback(this.mCallback);
        this.mPreview = new FixedSizeFrameLayout(context);
        this.mPreview.addView(previewWidget);
        addView(this.mPreview);
        View clickBlocker = new View(context);
        clickBlocker.setBackgroundColor(0);
        clickBlocker.setOnClickListener(this);
        addView(clickBlocker);
        setContentDescription(context.getString(R.string.keyguard_accessibility_camera));
        if (DEBUG) {
            Log.d(TAG, "new CameraWidgetFrame instance " + instanceId());
        }
        this.mUseFastTransition = KeyguardViewManager.isModLockEnabled(this.mContext);
    }

    public static CameraWidgetFrame create(Context context, Callbacks callbacks, KeyguardActivityLauncher launcher) {
        if (context == null || callbacks == null || launcher == null) {
            return null;
        }
        CameraWidgetInfo widgetInfo = launcher.getCameraWidgetInfo();
        if (widgetInfo == null) {
            return null;
        }
        View previewWidget = getPreviewWidget(context, widgetInfo);
        if (previewWidget != null) {
            return new CameraWidgetFrame(context, callbacks, launcher, widgetInfo, previewWidget);
        }
        return null;
    }

    private static View getPreviewWidget(Context context, CameraWidgetInfo widgetInfo) {
        return widgetInfo.layoutId > 0 ? inflateWidgetView(context, widgetInfo) : inflateGenericWidgetView(context);
    }

    private static View inflateWidgetView(Context context, CameraWidgetInfo widgetInfo) {
        if (DEBUG) {
            Log.d(TAG, "inflateWidgetView: " + widgetInfo.contextPackage);
        }
        View widgetView = null;
        Exception exception = null;
        try {
            Context cameraContext = context.createPackageContext(widgetInfo.contextPackage, 4);
            widgetView = ((LayoutInflater) cameraContext.getSystemService("layout_inflater")).cloneInContext(cameraContext).inflate(widgetInfo.layoutId, null, DEBUG);
        } catch (Exception e) {
            exception = e;
        } catch (Exception e2) {
            exception = e2;
        }
        if (exception != null) {
            Log.w(TAG, "Error creating camera widget view", exception);
        }
        return widgetView;
    }

    private static View inflateGenericWidgetView(Context context) {
        if (DEBUG) {
            Log.d(TAG, "inflateGenericWidgetView");
        }
        ImageView iv = new ImageView(context);
        iv.setImageResource(R.drawable.ic_lockscreen_camera);
        iv.setScaleType(ScaleType.CENTER);
        iv.setBackgroundColor(Color.argb(127, 0, 0, 0));
        return iv;
    }

    private void render() {
        View root = getRootView();
        int width = root.getWidth() - this.mInsets.right;
        int height = root.getHeight() - this.mInsets.bottom;
        if (this.mRenderedSize.x == width && this.mRenderedSize.y == height) {
            if (DEBUG) {
                Log.d(TAG, String.format("Already rendered at size=%sx%s %d%%", new Object[]{Integer.valueOf(width), Integer.valueOf(height), Integer.valueOf((int) (100.0f * this.mPreview.getScaleX()))}));
            }
        } else if (width != 0 && height != 0) {
            this.mPreview.width = width;
            this.mPreview.height = height;
            this.mPreview.requestLayout();
            int thisWidth = (getWidth() - getPaddingLeft()) - getPaddingRight();
            int thisHeight = (getHeight() - getPaddingTop()) - getPaddingBottom();
            float pvScale = Math.min(((float) thisWidth) / ((float) width), ((float) thisHeight) / ((float) height));
            int pvWidth = (int) (((float) width) * pvScale);
            int pvHeight = (int) (((float) height) * pvScale);
            float pvTransX = pvWidth < thisWidth ? (float) ((thisWidth - pvWidth) / 2) : 0.0f;
            float pvTransY = pvHeight < thisHeight ? (float) ((thisHeight - pvHeight) / 2) : 0.0f;
            boolean isRtl = this.mPreview.getLayoutDirection() == 1 ? true : DEBUG;
            this.mPreview.setPivotX(isRtl ? (float) this.mPreview.width : 0.0f);
            this.mPreview.setPivotY(0.0f);
            this.mPreview.setScaleX(pvScale);
            this.mPreview.setScaleY(pvScale);
            this.mPreview.setTranslationX(((float) (isRtl ? -1 : 1)) * pvTransX);
            this.mPreview.setTranslationY(pvTransY);
            if (KeyguardViewManager.isModLockEnabled(this.mContext)) {
                this.mPreview.setVisibility(4);
            }
            this.mRenderedSize.set(width, height);
            if (DEBUG) {
                Log.d(TAG, String.format("Rendered camera widget size=%sx%s %d%% instance=%s", new Object[]{Integer.valueOf(width), Integer.valueOf(height), Integer.valueOf((int) (100.0f * this.mPreview.getScaleX())), instanceId()}));
            }
        }
    }

    private void transitionToCamera() {
        if (!this.mTransitioning && !this.mDown) {
            this.mTransitioning = true;
            enableWindowExitAnimation(DEBUG);
            int navHeight = this.mInsets.bottom;
            int navWidth = this.mInsets.right;
            this.mPreview.getLocationInWindow(this.mTmpLoc);
            float pvCenter = ((float) this.mTmpLoc[1]) + ((((float) this.mPreview.getHeight()) * this.mPreview.getScaleY()) / 2.0f);
            ViewGroup root = (ViewGroup) getRootView();
            if (DEBUG) {
                Log.d(TAG, "root = " + root.getLeft() + "," + root.getTop() + " " + root.getWidth() + "x" + root.getHeight());
            }
            if (this.mFullscreenPreview == null) {
                this.mFullscreenPreview = getPreviewWidget(this.mContext, this.mWidgetInfo);
                this.mFullscreenPreview.setClickable(DEBUG);
                root.addView(this.mFullscreenPreview, new LayoutParams(root.getWidth() - navWidth, root.getHeight() - navHeight));
            }
            float fsCenter = ((float) root.getTop()) + (((float) (root.getHeight() - navHeight)) / 2.0f);
            float fsScaleY = this.mPreview.getScaleY();
            float fsTransY = pvCenter - fsCenter;
            float fsScaleX = fsScaleY;
            this.mPreview.setVisibility(8);
            if (KeyguardViewManager.isModLockEnabled(this.mContext)) {
                this.mFullscreenPreview.setTranslationX((float) getWidth());
                this.mFullscreenPreview.setTranslationY(0.0f);
            } else {
                this.mFullscreenPreview.setTranslationY(fsTransY);
            }
            this.mFullscreenPreview.setVisibility(0);
            this.mFullscreenPreview.setScaleX(fsScaleX);
            this.mFullscreenPreview.setScaleY(fsScaleY);
            this.mFullscreenPreview.animate().scaleX(1.0f).scaleY(1.0f).translationX(0.0f).translationY(0.0f).setDuration(250).withEndAction(this.mPostTransitionToCameraEndAction).start();
            if (navHeight > 0 || navWidth > 0) {
                boolean atBottom = navHeight > 0 ? true : DEBUG;
                if (this.mFakeNavBar == null) {
                    int i;
                    int i2;
                    int i3;
                    this.mFakeNavBar = new View(this.mContext);
                    this.mFakeNavBar.setBackgroundColor(-16777216);
                    View view = this.mFakeNavBar;
                    if (atBottom) {
                        i = -1;
                    } else {
                        i = navWidth;
                    }
                    if (atBottom) {
                        i2 = navHeight;
                    } else {
                        i2 = -1;
                    }
                    if (atBottom) {
                        i3 = 87;
                    } else {
                        i3 = 117;
                    }
                    root.addView(view, new LayoutParams(i, i2, i3));
                    this.mFakeNavBar.setPivotY((float) navHeight);
                    this.mFakeNavBar.setPivotX((float) navWidth);
                }
                this.mFakeNavBar.setAlpha(0.0f);
                if (atBottom) {
                    this.mFakeNavBar.setScaleY(0.5f);
                } else {
                    this.mFakeNavBar.setScaleX(0.5f);
                }
                this.mFakeNavBar.setVisibility(0);
                this.mFakeNavBar.animate().alpha(1.0f).scaleY(1.0f).scaleY(1.0f).setDuration(250).start();
            }
            this.mCallbacks.onLaunchingCamera();
        }
    }

    private void recover() {
        if (DEBUG) {
            Log.d(TAG, "recovering at " + SystemClock.uptimeMillis());
        }
        this.mCallbacks.onCameraLaunchedUnsuccessfully();
        reset();
    }

    public void setOnLongClickListener(OnLongClickListener l) {
    }

    public void onClick(View v) {
        if (DEBUG) {
            Log.d(TAG, "clicked");
        }
        if (!this.mTransitioning && this.mActive) {
            cancelTransitionToCamera();
            transitionToCamera();
        }
    }

    protected void onDetachedFromWindow() {
        if (DEBUG) {
            Log.d(TAG, "onDetachedFromWindow: instance " + instanceId() + " at " + SystemClock.uptimeMillis());
        }
        super.onDetachedFromWindow();
        KeyguardUpdateMonitor.getInstance(this.mContext).removeCallback(this.mCallback);
        cancelTransitionToCamera();
        this.mHandler.removeCallbacks(this.mRecoverRunnable);
    }

    public void onActive(boolean isActive) {
        this.mActive = isActive;
        if (this.mActive) {
            rescheduleTransitionToCamera();
        } else {
            reset();
        }
    }

    public boolean onUserInteraction(MotionEvent event) {
        boolean z = true;
        if (!this.mTransitioning) {
            getLocationOnScreen(this.mTmpLoc);
            if (event.getRawY() <= ((float) (this.mTmpLoc[1] + getHeight()))) {
                int action = event.getAction();
                if (!(action == 0 || action == 2)) {
                    z = DEBUG;
                }
                this.mDown = z;
                if (this.mActive) {
                    rescheduleTransitionToCamera();
                }
                if (DEBUG) {
                    Log.d(TAG, "onUserInteraction observed, not eaten");
                }
                return DEBUG;
            } else if (!DEBUG) {
                return true;
            } else {
                Log.d(TAG, "onUserInteraction eaten: below widget");
                return true;
            }
        } else if (!DEBUG) {
            return true;
        } else {
            Log.d(TAG, "onUserInteraction eaten: mTransitioning");
            return true;
        }
    }

    protected void onFocusLost() {
        if (DEBUG) {
            Log.d(TAG, "onFocusLost at " + SystemClock.uptimeMillis());
        }
        cancelTransitionToCamera();
        super.onFocusLost();
    }

    public void onScreenTurnedOff() {
        if (DEBUG) {
            Log.d(TAG, "onScreenTurnedOff");
        }
        reset();
    }

    private void rescheduleTransitionToCamera() {
        if (DEBUG) {
            Log.d(TAG, "rescheduleTransitionToCamera at " + SystemClock.uptimeMillis());
        }
        this.mHandler.removeCallbacks(this.mTransitionToCameraRunnable);
        this.mHandler.postDelayed(this.mTransitionToCameraRunnable, this.mUseFastTransition ? 0 : 400);
    }

    private void cancelTransitionToCamera() {
        if (DEBUG) {
            Log.d(TAG, "cancelTransitionToCamera at " + SystemClock.uptimeMillis());
        }
        this.mHandler.removeCallbacks(this.mTransitionToCameraRunnable);
    }

    private void onCameraLaunched() {
        this.mCallbacks.onCameraLaunchedSuccessfully();
        reset();
    }

    private void reset() {
        if (DEBUG) {
            Log.d(TAG, "reset at " + SystemClock.uptimeMillis());
        }
        this.mLaunchCameraStart = 0;
        this.mTransitioning = DEBUG;
        this.mDown = DEBUG;
        cancelTransitionToCamera();
        this.mHandler.removeCallbacks(this.mRecoverRunnable);
        this.mPreview.setVisibility(0);
        if (this.mFullscreenPreview != null) {
            this.mFullscreenPreview.animate().cancel();
            this.mFullscreenPreview.setVisibility(8);
        }
        if (this.mFakeNavBar != null) {
            this.mFakeNavBar.animate().cancel();
            this.mFakeNavBar.setVisibility(8);
        }
        enableWindowExitAnimation(true);
    }

    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (DEBUG) {
            Log.d(TAG, String.format("onSizeChanged new=%sx%s old=%sx%s at %s", new Object[]{Integer.valueOf(w), Integer.valueOf(h), Integer.valueOf(oldw), Integer.valueOf(oldh), Long.valueOf(SystemClock.uptimeMillis())}));
        }
        if ((w != oldw && oldw > 0) || (h != oldh && oldh > 0)) {
            Point point = this.mRenderedSize;
            this.mRenderedSize.y = -1;
            point.x = -1;
        }
        this.mHandler.post(this.mRenderRunnable);
        super.onSizeChanged(w, h, oldw, oldh);
    }

    public void onBouncerShowing(boolean showing) {
        if (showing) {
            this.mTransitioning = DEBUG;
            this.mHandler.post(this.mRecoverRunnable);
        }
    }

    private void enableWindowExitAnimation(boolean isEnabled) {
        View root = getRootView();
        ViewGroup.LayoutParams lp = root.getLayoutParams();
        if (lp instanceof WindowManager.LayoutParams) {
            WindowManager.LayoutParams wlp = (WindowManager.LayoutParams) lp;
            int newWindowAnimations = isEnabled ? R.style.Animation_LockScreen : 0;
            if (newWindowAnimations != wlp.windowAnimations) {
                if (DEBUG) {
                    Log.d(TAG, "setting windowAnimations to: " + newWindowAnimations + " at " + SystemClock.uptimeMillis());
                }
                wlp.windowAnimations = newWindowAnimations;
                this.mWindowManager.updateViewLayout(root, wlp);
            }
        }
    }

    private void onKeyguardVisibilityChanged(boolean showing) {
        if (DEBUG) {
            Log.d(TAG, "onKeyguardVisibilityChanged " + showing + " at " + SystemClock.uptimeMillis());
        }
        if (this.mTransitioning && !showing) {
            this.mTransitioning = DEBUG;
            this.mHandler.removeCallbacks(this.mRecoverRunnable);
            if (this.mLaunchCameraStart > 0) {
                long launchTime = SystemClock.uptimeMillis() - this.mLaunchCameraStart;
                if (DEBUG) {
                    Log.d(TAG, String.format("Camera took %sms to launch", new Object[]{Long.valueOf(launchTime)}));
                }
                this.mLaunchCameraStart = 0;
                onCameraLaunched();
            }
        }
    }

    private void onSecureCameraActivityStarted() {
        if (DEBUG) {
            Log.d(TAG, "onSecureCameraActivityStarted at " + SystemClock.uptimeMillis());
        }
        this.mHandler.postDelayed(this.mRecoverRunnable, 1000);
    }

    private String instanceId() {
        return Integer.toHexString(hashCode());
    }

    public void setInsets(Rect insets) {
        if (DEBUG) {
            Log.d(TAG, "setInsets: " + insets);
        }
        this.mInsets.set(insets);
    }

    public void setUseFastTransition(boolean useFastTransition) {
        this.mUseFastTransition = useFastTransition;
    }
}
