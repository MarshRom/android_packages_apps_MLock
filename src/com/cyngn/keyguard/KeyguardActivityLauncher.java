package com.cyngn.keyguard;

import android.app.ActivityManagerNative;
import android.app.ActivityOptions;
import android.app.IActivityManager.WaitResult;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.util.Log;
import com.android.internal.widget.LockPatternUtils;
import java.util.List;

public abstract class KeyguardActivityLauncher {
    private static final boolean DEBUG = KeyguardHostView.DEBUG;
    private static final Intent INSECURE_CAMERA_INTENT = new Intent("android.media.action.STILL_IMAGE_CAMERA");
    private static final String META_DATA_KEYGUARD_LAYOUT = "com.android.keyguard.layout";
    private static final Intent SECURE_CAMERA_INTENT = new Intent("android.media.action.STILL_IMAGE_CAMERA_SECURE").addFlags(8388608);
    private static final String TAG = KeyguardActivityLauncher.class.getSimpleName();

    public static class CameraWidgetInfo {
        public String contextPackage;
        public int layoutId;
    }

    abstract KeyguardSecurityCallback getCallback();

    abstract Context getContext();

    abstract LockPatternUtils getLockPatternUtils();

    public CameraWidgetInfo getCameraWidgetInfo() {
        CameraWidgetInfo info = new CameraWidgetInfo();
        Intent intent = getCameraIntent();
        PackageManager packageManager = getContext().getPackageManager();
        List<ResolveInfo> appList = packageManager.queryIntentActivitiesAsUser(intent, 65536, getLockPatternUtils().getCurrentUser());
        if (appList.size() == 0) {
            if (DEBUG) {
                Log.d(TAG, "getCameraWidgetInfo(): Nothing found");
            }
            return null;
        }
        ResolveInfo resolved = packageManager.resolveActivityAsUser(intent, 65664, getLockPatternUtils().getCurrentUser());
        if (DEBUG) {
            Log.d(TAG, "getCameraWidgetInfo(): resolved: " + resolved);
        }
        if (wouldLaunchResolverActivity(resolved, appList)) {
            if (!DEBUG) {
                return info;
            }
            Log.d(TAG, "getCameraWidgetInfo(): Would launch resolver");
            return info;
        } else if (resolved == null || resolved.activityInfo == null) {
            return null;
        } else {
            if (resolved.activityInfo.metaData != null && !resolved.activityInfo.metaData.isEmpty()) {
                int layoutId = resolved.activityInfo.metaData.getInt(META_DATA_KEYGUARD_LAYOUT);
                if (layoutId != 0) {
                    info.contextPackage = resolved.activityInfo.packageName;
                    info.layoutId = layoutId;
                    return info;
                } else if (!DEBUG) {
                    return info;
                } else {
                    Log.d(TAG, "getCameraWidgetInfo(): no layout specified");
                    return info;
                }
            } else if (!DEBUG) {
                return info;
            } else {
                Log.d(TAG, "getCameraWidgetInfo(): no metadata found");
                return info;
            }
        }
    }

    public void launchCamera(Handler worker, Runnable onSecureCameraStarted) {
        LockPatternUtils lockPatternUtils = getLockPatternUtils();
        KeyguardUpdateMonitor.getInstance(getContext()).setAlternateUnlockEnabled(DEBUG);
        if (!lockPatternUtils.isSecure()) {
            launchActivity(INSECURE_CAMERA_INTENT, DEBUG, DEBUG, null, null);
        } else if (wouldLaunchResolverActivity(SECURE_CAMERA_INTENT)) {
            launchActivity(SECURE_CAMERA_INTENT, DEBUG, DEBUG, null, null);
        } else {
            launchActivity(SECURE_CAMERA_INTENT, true, DEBUG, worker, onSecureCameraStarted);
        }
    }

    public void launchApplicationWidget(Handler worker, Runnable onStarted, String packageName) {
        if (DEBUG) {
            Log.d(TAG, "Launch Application Widget Called for packageName: " + packageName);
        }
        try {
            ActivityManagerNative.getDefault().dismissKeyguardOnNextActivity();
        } catch (RemoteException e) {
        }
        Intent appLaunchIntent = getContext().getPackageManager().getLaunchIntentForPackage(packageName);
        appLaunchIntent.addFlags(335544320);
        Intent i = new Intent("android.intent.action.KEYGUARD_APPLICATION_WIDGET_LAUNCH_ACTION");
        i.putExtra("android.intent.extra.EXTRA_KEYGUARD_APPLICATION_WIDGET_PACKAGE_NAME", packageName);
        getContext().sendBroadcast(i, "android.permission.SET_KEYGUARD_APPLICATION_WIDGET");
        launchActivity(appLaunchIntent, DEBUG, DEBUG, null, null);
    }

    public void launchWidgetPicker(int appWidgetId) {
        Intent pickIntent = new Intent("android.appwidget.action.KEYGUARD_APPWIDGET_PICK");
        pickIntent.putExtra("appWidgetId", appWidgetId);
        pickIntent.putExtra("customSort", DEBUG);
        pickIntent.putExtra("categoryFilter", 2);
        Bundle options = new Bundle();
        options.putInt("appWidgetCategory", 2);
        pickIntent.putExtra("appWidgetOptions", options);
        pickIntent.addFlags(880803840);
        launchActivity(pickIntent, DEBUG, DEBUG, null, null);
    }

    public void launchActivity(Intent intent, boolean showsWhileLocked, boolean useDefaultAnimations, Handler worker, Runnable onStarted) {
        launchActivityWithAnimation(intent, showsWhileLocked, useDefaultAnimations ? null : ActivityOptions.makeCustomAnimation(getContext(), 0, 0).toBundle(), worker, onStarted);
    }

    public void launchActivityWithAnimation(Intent intent, boolean showsWhileLocked, Bundle animation, Handler worker, Runnable onStarted) {
        LockPatternUtils lockPatternUtils = getLockPatternUtils();
        intent.addFlags(872415232);
        boolean isSecure = lockPatternUtils.isSecure();
        if (!isSecure || showsWhileLocked) {
            if (!isSecure) {
                dismissKeyguardOnNextActivity();
            }
            try {
                if (DEBUG) {
                    Log.d(TAG, String.format("Starting activity for intent %s at %s", new Object[]{intent, Long.valueOf(SystemClock.uptimeMillis())}));
                }
                startActivityForCurrentUser(intent, animation, worker, onStarted);
                return;
            } catch (ActivityNotFoundException e) {
                Log.w(TAG, "Activity not found for intent + " + intent.getAction());
                return;
            }
        }
        KeyguardSecurityCallback callback = getCallback();
        final Intent intent2 = intent;
        final Bundle bundle = animation;
        final Handler handler = worker;
        final Runnable runnable = onStarted;
        callback.setOnDismissAction(new OnDismissAction() {
            public boolean onDismiss() {
                KeyguardActivityLauncher.this.dismissKeyguardOnNextActivity();
                KeyguardActivityLauncher.this.startActivityForCurrentUser(intent2, bundle, handler, runnable);
                return true;
            }
        });
        callback.dismiss(DEBUG);
    }

    protected void dismissKeyguardOnNextActivity() {
        try {
            ActivityManagerNative.getDefault().dismissKeyguardOnNextActivity();
        } catch (RemoteException e) {
            Log.w(TAG, "can't dismiss keyguard on launch");
        }
    }

    private void startActivityForCurrentUser(Intent intent, Bundle options, Handler worker, Runnable onStarted) {
        final UserHandle user = new UserHandle(-2);
        if (worker == null || onStarted == null) {
            getContext().startActivityAsUser(intent, options, user);
            return;
        }
        final Intent intent2 = intent;
        final Bundle bundle = options;
        final Runnable runnable = onStarted;
        worker.post(new Runnable() {
            public void run() {
                try {
                    WaitResult result = ActivityManagerNative.getDefault().startActivityAndWait(null, null, intent2, intent2.resolveTypeIfNeeded(KeyguardActivityLauncher.this.getContext().getContentResolver()), null, null, 0, 268435456, null, null, bundle, user.getIdentifier());
                    if (KeyguardActivityLauncher.DEBUG) {
                        Log.d(KeyguardActivityLauncher.TAG, String.format("waitResult[%s,%s,%s,%s] at %s", new Object[]{Integer.valueOf(result.result), Long.valueOf(result.thisTime), Long.valueOf(result.totalTime), result.who, Long.valueOf(SystemClock.uptimeMillis())}));
                    }
                    try {
                        runnable.run();
                    } catch (Throwable t) {
                        Log.w(KeyguardActivityLauncher.TAG, "Error running onStarted callback", t);
                    }
                } catch (RemoteException e) {
                    Log.w(KeyguardActivityLauncher.TAG, "Error starting activity", e);
                }
            }
        });
    }

    private Intent getCameraIntent() {
        return getLockPatternUtils().isSecure() ? SECURE_CAMERA_INTENT : INSECURE_CAMERA_INTENT;
    }

    private boolean wouldLaunchResolverActivity(Intent intent) {
        PackageManager packageManager = getContext().getPackageManager();
        return wouldLaunchResolverActivity(packageManager.resolveActivityAsUser(intent, 65536, getLockPatternUtils().getCurrentUser()), packageManager.queryIntentActivitiesAsUser(intent, 65536, getLockPatternUtils().getCurrentUser()));
    }

    private boolean wouldLaunchResolverActivity(ResolveInfo resolved, List<ResolveInfo> appList) {
        for (int i = 0; i < appList.size(); i++) {
            ResolveInfo tmp = (ResolveInfo) appList.get(i);
            if (tmp.activityInfo.name.equals(resolved.activityInfo.name) && tmp.activityInfo.packageName.equals(resolved.activityInfo.packageName)) {
                return DEBUG;
            }
        }
        return true;
    }
}
