package com.cyngn.keyguard;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.graphics.drawable.Drawable;
import android.view.View;

public class KeyguardSecurityViewHelper {
    public static void showBouncer(SecurityMessageDisplay securityMessageDisplay, final View ecaView, Drawable bouncerFrame, int duration) {
        if (securityMessageDisplay != null) {
            securityMessageDisplay.showBouncer(duration);
        }
        if (ecaView != null) {
            if (duration > 0) {
                Animator anim = ObjectAnimator.ofFloat(ecaView, "alpha", new float[]{0.0f});
                anim.setDuration((long) duration);
                anim.addListener(new AnimatorListenerAdapter() {
                    private boolean mCanceled;

                    public void onAnimationCancel(Animator animation) {
                        this.mCanceled = true;
                        ecaView.setAlpha(1.0f);
                    }

                    public void onAnimationEnd(Animator animation) {
                        ecaView.setVisibility(this.mCanceled ? 0 : 4);
                    }
                });
                anim.start();
            } else {
                ecaView.setAlpha(0.0f);
                ecaView.setVisibility(4);
            }
        }
        if (bouncerFrame == null) {
            return;
        }
        if (duration > 0) {
            anim = ObjectAnimator.ofInt(bouncerFrame, "alpha", new int[]{0, 255});
            anim.setDuration((long) duration);
            anim.start();
            return;
        }
        bouncerFrame.setAlpha(255);
    }

    public static void hideBouncer(SecurityMessageDisplay securityMessageDisplay, View ecaView, Drawable bouncerFrame, int duration) {
        if (securityMessageDisplay != null) {
            securityMessageDisplay.hideBouncer(duration);
        }
        if (ecaView != null) {
            ecaView.setVisibility(0);
            if (duration > 0) {
                Animator anim = ObjectAnimator.ofFloat(ecaView, "alpha", new float[]{1.0f});
                anim.setDuration((long) duration);
                anim.start();
            } else {
                ecaView.setAlpha(1.0f);
            }
        }
        if (bouncerFrame == null) {
            return;
        }
        if (duration > 0) {
            anim = ObjectAnimator.ofInt(bouncerFrame, "alpha", new int[]{255, 0});
            anim.setDuration((long) duration);
            anim.start();
            return;
        }
        bouncerFrame.setAlpha(0);
    }
}
