package com.cyngn.keyguard;

public interface ChallengeLayout {

    public interface OnBouncerStateChangedListener {
        void onBouncerStateChanged(boolean z);
    }

    int getBouncerAnimationDuration();

    void hideBouncer();

    boolean isBouncing();

    boolean isChallengeOverlapping();

    boolean isChallengeShowing();

    void setOnBouncerStateChangedListener(OnBouncerStateChangedListener onBouncerStateChangedListener);

    void showBouncer();

    void showChallenge(boolean z);
}
